import { NextResponse } from "next/server";
import {
  apiError,
  authorizeMobile,
  parseJson,
  verifyStoredJpeg,
} from "@/lib/mobile-api";
import { validateShareCompletion } from "@/lib/mobile-validation";
import { getAdminClient } from "@/lib/supabase/admin";

export async function POST(request: Request) {
  const auth = await authorizeMobile(request, true);
  if ("response" in auth) return auth.response;

  const validation = validateShareCompletion(
    await parseJson<unknown>(request),
    auth.user.id,
  );
  if (!validation.ok) {
    return apiError("Invalid completed share.", 400, "invalid_share");
  }
  const body = validation.value;
  const supabase = getAdminClient();
  const [{ data: disease, error: diseaseError }, validPhoto] = await Promise.all([
    supabase
      .from("disease_catalog")
      .select("id")
      .eq("id", body.diseaseId)
      .maybeSingle(),
    verifyStoredJpeg(body.path, body.expectedSha256),
  ]);
  if (diseaseError) {
    return apiError(
      "The disease catalog is temporarily unavailable.",
      503,
      "catalog_unavailable",
    );
  }
  if (!disease) {
    return apiError(
      "Disease is not supported by this model.",
      422,
      "unsupported_disease",
    );
  }
  if (!validPhoto) {
    return apiError(
      "The uploaded JPEG is missing, invalid, or does not match the share intent.",
      422,
      "invalid_uploaded_photo",
    );
  }
  const { data, error } = await supabase.rpc("create_scan_contribution_with_quota", {
    p_owner_id: auth.user.id,
    p_client_scan_id: body.clientScanId,
    p_disease_id: body.diseaseId,
    p_confidence: body.confidence,
    p_source: body.source,
    p_model_version: body.modelVersion,
    p_photo_path: body.path,
  });
  const result = data?.[0];
  if (error || !result) {
    return apiError("Could not publish this scan.", 422, "publish_failed");
  }
  if (result.outcome === "conflict") {
    return apiError(
      "This local scan was already shared with different metadata.",
      409,
      "share_conflict",
    );
  }
  if (result.outcome === "invalid_intent") {
    return apiError("This share intent is missing or expired.", 409, "share_intent_invalid");
  }
  if (result.outcome === "consent_required") {
    return apiError("Anonymous sharing was disabled before publication.", 409, "sharing_disabled");
  }
  return NextResponse.json({
    id: result.contribution_id,
    status: result.contribution_status,
    published_at: result.contribution_published_at,
  }, { status: result.outcome === "created" ? 201 : 200 });
}
