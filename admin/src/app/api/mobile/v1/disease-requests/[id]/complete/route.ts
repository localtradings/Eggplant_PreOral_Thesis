import { NextResponse } from "next/server";
import {
  apiError,
  authorizeMobile,
  verifyStoredJpeg,
} from "@/lib/mobile-api";
import {
  sha256FromDiseaseRequestPath,
  UUID_PATTERN,
} from "@/lib/mobile-validation";
import { getAdminClient } from "@/lib/supabase/admin";

export async function POST(
  request: Request,
  context: { params: Promise<{ id: string }> },
) {
  const auth = await authorizeMobile(request, true);
  if ("response" in auth) return auth.response;
  const { id } = await context.params;
  if (!UUID_PATTERN.test(id)) {
    return apiError("Invalid disease request.", 400, "invalid_request");
  }

  const supabase = getAdminClient();
  const { data: diseaseRequest, error: requestError } = await supabase
    .from("disease_requests")
    .select("id,status")
    .eq("id", id)
    .eq("owner_id", auth.user.id)
    .maybeSingle();
  if (requestError) {
    return apiError(
      "The disease request is temporarily unavailable.",
      503,
      "request_unavailable",
    );
  }
  if (!diseaseRequest) {
    return apiError(
      "Disease request not found.",
      404,
      "request_not_found",
    );
  }
  const { data: photos = [], error: photoReadError } = await supabase
    .from("disease_request_photos")
    .select("position,object_path")
    .eq("request_id", id)
    .eq("owner_id", auth.user.id)
    .order("position");
  if (photoReadError) {
    return apiError(
      "Private request photos are temporarily unavailable.",
      503,
      "request_photos_unavailable",
    );
  }
  if (!photos || photos.length < 1 || photos.length > 3) {
    return apiError(
      "One to three private photos are required.",
      422,
      "photos_incomplete",
    );
  }

  // Validate sequentially so three maximum-size images cannot expand in memory
  // at the same time inside one serverless invocation.
  for (const [index, { object_path }] of photos.entries()) {
    const expectedPrefix = `requests/${auth.user.id}/${id}/${index}-`;
    const sha256 = sha256FromDiseaseRequestPath(object_path);
    const valid = object_path.startsWith(expectedPrefix) && sha256
      ? await verifyStoredJpeg(object_path, sha256)
      : false;
    if (!valid) {
      return apiError(
        "A private JPEG upload is incomplete or invalid.",
        409,
        "photos_incomplete",
      );
    }
  }
  if (diseaseRequest.status === "submitted") {
    return NextResponse.json({ id, status: "submitted" });
  }
  if (diseaseRequest.status !== "upload_pending") {
    return apiError(
      "This disease request can no longer accept uploads.",
      409,
      "request_already_reviewed",
    );
  }

  const { data, error } = await supabase
    .from("disease_requests")
    .update({ status: "submitted" })
    .eq("id", id)
    .eq("owner_id", auth.user.id)
    .eq("status", "upload_pending")
    .select("id,status,updated_at")
    .single();
  if (error) {
    return apiError(
      "Could not submit the disease request.",
      500,
      "request_complete_failed",
    );
  }
  return NextResponse.json(data);
}
