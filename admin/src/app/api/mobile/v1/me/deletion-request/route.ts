import { NextResponse } from "next/server";
import { apiError, authorizeMobile } from "@/lib/mobile-api";
import { getAdminClient } from "@/lib/supabase/admin";
import {
  attemptImmediateStorageCleanup,
  cleanupQueuedFromPersistentCount,
} from "@/lib/storage-cleanup";

export async function POST(request: Request) {
  // Privacy deletion remains available even while ordinary cloud writes are paused.
  const auth = await authorizeMobile(request);
  if ("response" in auth) return auth.response;
  const supabase = getAdminClient();
  const { data, error } = await supabase.rpc("request_shared_cloud_deletion_v2", {
    p_owner_id: auth.user.id,
  });
  const deletion = data?.[0];
  if (error || !deletion) {
    return apiError("Shared cloud data could not be safely queued for deletion.", 500, "deletion_queue_failed");
  }
  const rawCancelledPaths: unknown[] = Array.isArray(deletion.cancelled_paths)
    ? deletion.cancelled_paths
    : [];
  const cancelledPaths = rawCancelledPaths
    .filter((path: unknown): path is string => typeof path === "string")
  await attemptImmediateStorageCleanup(cancelledPaths, {
    remove: async (paths) => {
      const { error: cleanupError } = await supabase.storage.from("eggplant-scans").remove(paths);
      return !cleanupError;
    },
    acknowledge: async (paths) => {
      const { error: acknowledgementError } = await supabase.rpc("acknowledge_storage_cleanup_paths", {
        p_owner_id: auth.user.id,
        p_bucket_id: "eggplant-scans",
        p_paths: paths,
      });
      return !acknowledgementError;
    },
  });
  const { count: queuedCleanupCount, error: queuedCleanupError } = await supabase
    .from("storage_cleanup_outbox")
    .select("id", { count: "exact", head: true })
    .eq("owner_id", auth.user.id);
  const cleanupQueued = cleanupQueuedFromPersistentCount(
    queuedCleanupCount,
    Boolean(queuedCleanupError),
  );
  const rawAffectedContributionIds: unknown[] = Array.isArray(deletion.affected_contribution_ids)
    ? deletion.affected_contribution_ids
    : [];
  const affectedContributionIds = rawAffectedContributionIds
    .filter((id: unknown): id is string => typeof id === "string");
  let completedAt: string | null = null;
  if (deletion.deletion_status === "completed") {
    const { data: completedRequest, error: completionReadError } = await supabase
      .from("deletion_requests")
      .select("completed_at")
      .eq("id", deletion.deletion_id)
      .maybeSingle();
    if (completionReadError) {
      return apiError("Cloud deletion completion could not be verified.", 503, "deletion_status_failed");
    }
    completedAt = completedRequest?.completed_at ?? null;
  }
  return NextResponse.json({
    id: deletion.deletion_id,
    status: deletion.deletion_status,
    scope: "shared",
    affectedContributionIds,
    cleanupQueued,
    lastErrorCode: null,
    completedAt,
  }, { status: deletion.deletion_status === "completed" ? 200 : 202 });
}

export async function GET(request: Request) {
  const auth = await authorizeMobile(request);
  if ("response" in auth) return auth.response;
  const { data, error } = await getAdminClient()
    .from("deletion_requests")
    .select("id,status,scope,created_at,completed_at,last_error_code")
    .eq("owner_id", auth.user.id)
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (error) return apiError("Cloud deletion status could not be loaded.", 503, "deletion_status_failed");
  if (!data) return NextResponse.json({ status: "idle", scope: "shared", affectedContributionIds: [] });
  return NextResponse.json({
    id: data.id,
    status: data.status,
    scope: data.scope,
    affectedContributionIds: [],
    lastErrorCode: data.last_error_code,
    completedAt: data.completed_at,
  });
}
