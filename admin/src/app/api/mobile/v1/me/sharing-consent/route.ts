import { NextResponse } from "next/server";
import { apiError, authorizeMobile, parseJson } from "@/lib/mobile-api";
import { validateSharingConsent } from "@/lib/mobile-validation";
import { getAdminClient } from "@/lib/supabase/admin";
import {
  attemptImmediateStorageCleanup,
  cleanupQueuedFromPersistentCount,
} from "@/lib/storage-cleanup";

export async function POST(request: Request) {
  const validation = validateSharingConsent(await parseJson<unknown>(request));
  if (!validation.ok) {
    return apiError("Invalid anonymous-sharing consent.", 400, "invalid_consent");
  }
  // Disabling sharing is a privacy control and remains available while the
  // ordinary production write switch is paused.
  const auth = await authorizeMobile(request, validation.value.enabled);
  if ("response" in auth) return auth.response;

  const supabase = getAdminClient();
  const { data, error } = await supabase.rpc("set_sharing_consent", {
    p_owner_id: auth.user.id,
    p_enabled: validation.value.enabled,
    p_consent_version: validation.value.consentVersion,
  });
  const result = data?.[0];
  if (error || !result) {
    return apiError(
      "Anonymous-sharing consent could not be saved.",
      500,
      "consent_update_failed",
    );
  }

  const rawCancelledPaths: unknown = result.cancelled_paths;
  const cancelledPaths = Array.isArray(rawCancelledPaths)
    ? rawCancelledPaths.filter((path: unknown): path is string => typeof path === "string")
    : [];
  await attemptImmediateStorageCleanup(
    cancelledPaths,
    {
      remove: async (paths) => {
        const { error: cleanupError } = await supabase.storage
          .from("eggplant-scans")
          .remove(paths);
        return !cleanupError;
      },
      acknowledge: async (paths) => {
        const { error: acknowledgementError } = await supabase.rpc(
          "acknowledge_storage_cleanup_paths",
          {
            p_owner_id: auth.user.id,
            p_bucket_id: "eggplant-scans",
            p_paths: paths,
          },
        );
        return !acknowledgementError;
      },
    },
  );
  const { count: queuedCleanupCount, error: queuedCleanupError } = await supabase
    .from("storage_cleanup_outbox")
    .select("id", { count: "exact", head: true })
    .eq("owner_id", auth.user.id);
  // Fail closed if queue state cannot be confirmed. A retry may have reached
  // the server after the first disable response was lost.
  const cleanupQueued = cleanupQueuedFromPersistentCount(
    queuedCleanupCount,
    Boolean(queuedCleanupError),
  );
  return NextResponse.json({
    enabled: result.sharing_enabled === true,
    cancelledPendingShares: cancelledPaths.length,
    cleanupQueued,
  });
}
