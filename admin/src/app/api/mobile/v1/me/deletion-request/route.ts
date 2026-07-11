import { NextResponse } from "next/server";
import { apiError, authorizeMobile } from "@/lib/mobile-api";
import { getAdminClient } from "@/lib/supabase/admin";

export async function POST(request: Request) {
  // Privacy deletion remains available even while ordinary cloud writes are paused.
  const auth = await authorizeMobile(request);
  if ("response" in auth) return auth.response;
  const supabase = getAdminClient();
  const { error: unpublishError } = await supabase.from("scan_contributions").update({ status: "removed" }).eq("owner_id", auth.user.id).in("status", ["published", "quarantined"]);
  if (unpublishError) return apiError("Could not unpublish shared scans.", 500, "unpublish_failed");
  const { data: active, error: activeError } = await supabase.from("deletion_requests").select("id,status,created_at").eq("owner_id", auth.user.id).in("status", ["queued", "processing", "failed"]).order("created_at", { ascending: false }).limit(1).maybeSingle();
  if (activeError) {
    return apiError("Shared scans were unpublished, but deletion status could not be checked.", 500, "deletion_status_failed");
  }
  if (active) {
    if (active.status === "failed") {
      const { error: retryError } = await supabase.from("deletion_requests").update({ status: "queued" }).eq("id", active.id).eq("status", "failed");
      if (retryError) {
        return apiError("Shared scans were unpublished, but cloud deletion could not be re-queued.", 500, "deletion_retry_failed");
      }
    }
    return NextResponse.json({ ...active, status: active.status === "failed" ? "queued" : active.status }, { status: 202 });
  }
  const { data, error } = await supabase.from("deletion_requests").insert({ owner_id: auth.user.id }).select("id,status,created_at").single();
  if (error) return apiError("Shared scans were unpublished, but cloud deletion could not be queued.", 500, "deletion_queue_failed");
  return NextResponse.json(data, { status: 202 });
}
