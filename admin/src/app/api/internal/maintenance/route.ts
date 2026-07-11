import { timingSafeEqual } from "node:crypto";
import { NextResponse } from "next/server";
import { getAdminClient } from "@/lib/supabase/admin";
import {
  processStorageCleanupBatch,
  type StorageCleanupEntry,
} from "@/lib/storage-cleanup";

export async function GET(request: Request) {
  const expected = process.env.CRON_SECRET;
  const authorization = request.headers.get("authorization");
  const provided = authorization?.startsWith("Bearer ")
    ? authorization.slice(7)
    : null;
  if (!expected || !provided || !secureEqual(expected, provided)) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const supabase = getAdminClient();
  const storageCleanup = await processStorageCleanupBatch({
    claim: async (limit) => {
      const { data, error } = await supabase.rpc(
        "claim_storage_cleanup_batch",
        { p_limit: limit },
      );
      if (error || !Array.isArray(data)) {
        throw new Error("Storage cleanup could not claim its queue.");
      }
      return data.map(parseStorageCleanupEntry);
    },
    remove: async (bucketId, objectPath) => {
      if (bucketId !== "eggplant-scans") return false;
      const { error } = await supabase.storage
        .from(bucketId)
        .remove([objectPath]);
      return !error;
    },
    acknowledge: async (cleanupId, succeeded, errorCode) => {
      const { data, error } = await supabase.rpc(
        "acknowledge_storage_cleanup",
        {
          p_cleanup_id: cleanupId,
          p_succeeded: succeeded,
          p_error_code: errorCode,
        },
      );
      return !error && data === true;
    },
  });
  const now = new Date().toISOString();
  const { data: expiring, error: expirationReadError } = await supabase
    .from("scan_contributions")
    .select("id,photo_path")
    .eq("status", "published")
    .lte("expires_at", now)
    .order("expires_at")
    .limit(100);
  if (expirationReadError) {
    return NextResponse.json(
      { error: "Expiration maintenance could not read its queue." },
      { status: 500 },
    );
  }
  const expired = expiring ?? [];
  let expiredPhotoCleanupFailed = false;
  let expirationStatusUpdateFailed = false;
  let expiredScansUnpublished = 0;
  if (expired.length > 0) {
    const { error } = await supabase.storage
      .from("eggplant-scans")
      .remove(expired.map((row) => row.photo_path));
    expiredPhotoCleanupFailed = Boolean(error);
    if (!error) {
      const expiredIds = expired.map((row) => row.id);
      const { error: updateError } = await supabase
        .from("scan_contributions")
        .update({ status: "expired" })
        .in("id", expiredIds)
        .eq("status", "published");
      expirationStatusUpdateFailed = Boolean(updateError);
      if (!updateError) expiredScansUnpublished = expiredIds.length;
    }
  }

  const pendingIntentCutoff = new Date(Date.now() - 2 * 60 * 60 * 1_000).toISOString();
  const completedIntentCutoff = new Date(Date.now() - 181 * 24 * 60 * 60 * 1_000).toISOString();
  const { data: staleIntents = [], error: intentReadError } = await supabase
    .from("global_share_intents")
    .select("id,photo_path,status")
    .or(`and(status.eq.pending,created_at.lt.${pendingIntentCutoff}),and(status.eq.completed,created_at.lt.${completedIntentCutoff})`)
    .limit(200);
  if (intentReadError) {
    return NextResponse.json(
      { error: "Expired upload intents could not be read." },
      { status: 500 },
    );
  }
  const pendingIntents = (staleIntents ?? []).filter((intent) => intent.status !== "completed");
  let intentCleanupFailed = false;
  if (pendingIntents.length > 0) {
    const { error } = await supabase.storage
      .from("eggplant-scans")
      .remove(pendingIntents.map((intent) => intent.photo_path));
    intentCleanupFailed = Boolean(error);
  }
  const removableIntentIds = (staleIntents ?? [])
    .filter((intent) => intent.status === "completed" || !intentCleanupFailed)
    .map((intent) => intent.id);
  if (removableIntentIds.length > 0) {
    const { error } = await supabase
      .from("global_share_intents")
      .delete()
      .in("id", removableIntentIds);
    intentCleanupFailed = intentCleanupFailed || Boolean(error);
  }
  const rateLimitCutoff = new Date();
  rateLimitCutoff.setUTCDate(rateLimitCutoff.getUTCDate() - 2);
  const { error: rateLimitCleanupError } = await supabase
    .from("api_rate_limits")
    .delete()
    .lt("window_start", rateLimitCutoff.toISOString().slice(0, 10));

  const { data: deletions, error: deletionReadError } = await supabase
    .from("deletion_requests")
    .select("id,owner_id")
    .in("status", ["queued", "processing", "failed"])
    .order("created_at")
    .limit(20);
  if (deletionReadError) {
    return NextResponse.json(
      { error: "Deletion maintenance could not read its queue." },
      { status: 500 },
    );
  }

  let completed = 0;
  let failed = 0;
  for (const deletion of deletions ?? []) {
    const { error: processingError } = await supabase
      .from("deletion_requests")
      .update({ status: "processing" })
      .eq("id", deletion.id);
    if (processingError) {
      failed += 1;
      continue;
    }

    const [contributions, requestPhotos, shareIntents] = await Promise.all([
      supabase
        .from("scan_contributions")
        .select("photo_path")
        .eq("owner_id", deletion.owner_id),
      supabase
        .from("disease_request_photos")
        .select("object_path")
        .eq("owner_id", deletion.owner_id),
      supabase
        .from("global_share_intents")
        .select("photo_path")
        .eq("owner_id", deletion.owner_id),
    ]);
    if (contributions.error || requestPhotos.error || shareIntents.error) {
      await markDeletionFailed(deletion.id);
      failed += 1;
      continue;
    }
    const paths = [...new Set([
      ...(contributions.data ?? []).map((row) => row.photo_path),
      ...(requestPhotos.data ?? []).map((row) => row.object_path),
      ...(shareIntents.data ?? []).map((row) => row.photo_path),
    ])];
    if (paths.length > 0) {
      const { error } = await supabase.storage
        .from("eggplant-scans")
        .remove(paths);
      if (error) {
        await markDeletionFailed(deletion.id);
        failed += 1;
        continue;
      }
    }

    // Preserve the moderation signal while removing the reporter identity and
    // optional free-text content supplied by this owner.
    const { error: reportAnonymizeError } = await supabase
      .from("content_reports")
      .update({
        reporter_id: null,
        details: null,
        reporter_anonymized_at: new Date().toISOString(),
      })
      .eq("reporter_id", deletion.owner_id);
    if (reportAnonymizeError) {
      await markDeletionFailed(deletion.id);
      failed += 1;
      continue;
    }

    const [scanDelete, requestDelete, intentDelete, installationUpdate] = await Promise.all([
      supabase
        .from("scan_contributions")
        .delete()
        .eq("owner_id", deletion.owner_id),
      supabase
        .from("disease_requests")
        .delete()
        .eq("owner_id", deletion.owner_id),
      supabase
        .from("global_share_intents")
        .delete()
        .eq("owner_id", deletion.owner_id),
      supabase
        .from("installations")
        .update({ sharing_enabled: false, consent_version: null, consented_at: null })
        .eq("owner_id", deletion.owner_id),
    ]);
    if (scanDelete.error || requestDelete.error || intentDelete.error || installationUpdate.error) {
      await markDeletionFailed(deletion.id);
      failed += 1;
      continue;
    }
    const { error: completionError } = await supabase
      .from("deletion_requests")
      .update({ status: "completed", completed_at: new Date().toISOString() })
      .eq("id", deletion.id);
    if (completionError) {
      await markDeletionFailed(deletion.id);
      failed += 1;
      continue;
    }
    completed += 1;
  }

  const storageCleanupFailed =
    storageCleanup.claimFailed ||
    storageCleanup.retriesQueued > 0 ||
    storageCleanup.acknowledgementFailures > 0;
  const status = expiredPhotoCleanupFailed || expirationStatusUpdateFailed || intentCleanupFailed || rateLimitCleanupError || storageCleanupFailed || failed > 0 ? 500 : 200;
  return NextResponse.json(
    {
      storageCleanupClaimed: storageCleanup.claimed,
      storageCleanupCompleted: storageCleanup.completed,
      storageCleanupRetriesQueued: storageCleanup.retriesQueued,
      storageCleanupAcknowledgementFailures:
        storageCleanup.acknowledgementFailures,
      storageCleanupClaimFailed: storageCleanup.claimFailed,
      expiredScansUnpublished,
      expiredPhotosPendingCleanup: expiredPhotoCleanupFailed,
      expirationStatusUpdateFailed,
      uploadIntentsRemoved: removableIntentIds.length,
      uploadIntentCleanupFailed: intentCleanupFailed,
      rateLimitCleanupFailed: Boolean(rateLimitCleanupError),
      deletionRequestsCompleted: completed,
      deletionRequestsFailed: failed,
    },
    { status },
  );

  async function markDeletionFailed(id: string) {
    await supabase
      .from("deletion_requests")
      .update({ status: "failed" })
      .eq("id", id);
  }
}

function secureEqual(expected: string, provided: string) {
  const expectedBytes = Buffer.from(expected);
  const providedBytes = Buffer.from(provided);
  return (
    expectedBytes.length === providedBytes.length &&
    timingSafeEqual(expectedBytes, providedBytes)
  );
}

function parseStorageCleanupEntry(value: unknown): StorageCleanupEntry {
  if (!isRecord(value)) {
    throw new Error("Storage cleanup returned an invalid queue row.");
  }
  const cleanupId = value.cleanup_id;
  const bucketId = value.cleanup_bucket_id;
  const objectPath = value.cleanup_object_path;
  const attemptCount = value.cleanup_attempt_count;
  if (
    typeof cleanupId !== "string" ||
    !UUID_PATTERN.test(cleanupId) ||
    bucketId !== "eggplant-scans" ||
    typeof objectPath !== "string" ||
    objectPath.length < 1 ||
    objectPath.length > 1_024 ||
    typeof attemptCount !== "number" ||
    !Number.isSafeInteger(attemptCount) ||
    attemptCount < 1
  ) {
    throw new Error("Storage cleanup returned an invalid queue row.");
  }
  return { cleanupId, bucketId, objectPath, attemptCount };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
