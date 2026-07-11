"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { hashActionPayload, requireIdempotencyKey } from "@/lib/action-idempotency";
import { requireAdmin } from "@/lib/auth";
import { UUID_PATTERN } from "@/lib/mobile-validation";
import { getAdminClient } from "@/lib/supabase/admin";
import { REQUEST_REVIEW_STATUSES, type RequestReviewStatus } from "./constants";

/**
 * Shared by the row actions and the full review form so both use the same
 * authorization and RPC path. The database remains the final idempotency
 * authority; the UI blocks repeat presses while this action is pending.
 */
export async function updateDiseaseRequest(formData: FormData) {
  const admin = await requireAdmin(["owner", "admin", "reviewer"]);
  const id = String(formData.get("id") ?? "");
  const status = String(formData.get("status") ?? "");
  const note = String(formData.get("note") ?? "").trim();
  const idempotencyKey = requireIdempotencyKey(formData);
  const returnTo = formData.get("return_to") === "list" ? "/disease-requests" : `/disease-requests/${id}`;

  if (!UUID_PATTERN.test(id) || !REQUEST_REVIEW_STATUSES.includes(status as RequestReviewStatus) || note.length > 2_000) {
    throw new Error("Invalid request review.");
  }

  const { data: outcome, error } = await getAdminClient().rpc("review_disease_request_v2", {
    p_request_id: id,
    p_status: status,
    p_admin_note: note,
    p_admin_id: admin.user.id,
    p_idempotency_key: idempotencyKey,
    p_payload_hash: hashActionPayload({ requestId: id, status, note }),
  });
  if (error || !["applied", "unchanged"].includes(outcome ?? "")) {
    throw new Error("The request could not be reviewed. Refresh and try again.");
  }

  revalidatePath("/disease-requests");
  revalidatePath(`/disease-requests/${id}`);
  redirect(`${returnTo}?reviewed=${encodeURIComponent(status)}&outcome=${encodeURIComponent(outcome)}`);
}
