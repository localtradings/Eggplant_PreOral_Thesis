import { NextResponse } from "next/server";
import { apiError, authorizeMobile, mobileRateSubject, parseJson } from "@/lib/mobile-api";
import {
  diseaseRequestPhotoPath,
  validateDiseaseRequest,
} from "@/lib/mobile-validation";
import { getAdminClient } from "@/lib/supabase/admin";

export async function POST(request: Request) {
  const auth = await authorizeMobile(request, true);
  if ("response" in auth) return auth.response;

  const validation = validateDiseaseRequest(
    await parseJson<unknown>(request),
  );
  if (!validation.ok) {
    return apiError(
      "Rights consent and one to three valid in-app camera plant photos are required.",
      400,
      "invalid_disease_request",
    );
  }
  const body = validation.value;
  const supabase = getAdminClient();
  let rateSubject: string;
  try {
    rateSubject = mobileRateSubject(request);
  } catch {
    return apiError("Request protection is temporarily unavailable.", 503, "rate_limit_unavailable");
  }
  const { data, error } = await supabase.rpc("create_disease_request_with_quota_v2", {
    p_owner_id: auth.user.id,
    p_client_request_id: body.clientRequestId,
    p_requested_name: body.requestedName ?? null,
    p_notes: body.notes ?? null,
    p_model_version: body.modelVersion,
    p_rights_consent: body.rightsConsent,
    p_training_consent: body.trainingConsent,
    p_photo_hashes: body.photos.map((photo) => photo.sha256),
    p_photo_sources: body.photos.map((photo) => photo.source),
    p_rate_subject: rateSubject,
  });
  const result = data?.[0];
  if (error || !result) {
    return apiError(
      "Could not create the disease request.",
      500,
      "request_create_failed",
    );
  }
  if (result.outcome === "conflict") {
    return apiError(
      "This local request was already submitted with different metadata.",
      409,
      "request_conflict",
    );
  }
  if (result.outcome === "quota") {
    return apiError(
      "Daily disease-request limit reached.",
      429,
      "request_limit",
    );
  }
  const requestId = result.request_id as string;
  const existing = result.outcome === "existing";

  if (result.request_status !== "upload_pending") {
    return NextResponse.json(
      { id: requestId, status: result.request_status, uploads: [] },
      { status: 200 },
    );
  }

  const paths = body.photos.map((photo, index) =>
    diseaseRequestPhotoPath(
      auth.user.id,
      requestId,
      index,
      photo.sha256,
    ),
  );
  const { data: recordedPhotos = [], error: recordedPhotosError } = await supabase
    .from("disease_request_photos")
    .select("position,object_path")
    .eq("request_id", requestId)
    .eq("owner_id", auth.user.id)
    .order("position");
  if (recordedPhotosError) {
    return apiError(
      "Could not verify the private photo request.",
      503,
      "request_photo_unavailable",
    );
  }
  const photoRows = recordedPhotos ?? [];
  if (
    photoRows.length !== paths.length ||
    photoRows.some(
      (photo, index) =>
        photo.position !== index || photo.object_path !== paths[index],
    )
  ) {
    return apiError(
      "This local request has conflicting photos.",
      409,
      "request_photo_conflict",
    );
  }

  const bucket = supabase.storage.from("eggplant-scans");
  const { error: cleanupError } = await bucket.remove(paths);
  if (cleanupError) {
    return apiError(
      "Could not safely renew the private photo uploads.",
      503,
      "request_upload_cleanup_failed",
    );
  }
  const signedResults = await Promise.all(
    paths.map((path) =>
      bucket.createSignedUploadUrl(path, { upsert: false }),
    ),
  );
  const signed = signedResults.map((result) => result.data);
  if (signedResults.some((result) => result.error || !result.data)) {
    return apiError(
      "Could not prepare private photo uploads.",
      500,
      "request_upload_failed",
    );
  }

  return NextResponse.json(
    {
      id: requestId,
      status: result.request_status,
      uploads: signed.map((entry, index) => ({
        position: index,
        path: paths[index],
        token: entry!.token,
        signedUrl: entry!.signedUrl,
        expiresInSeconds: 7_200,
      })),
    },
    { status: existing ? 200 : 201 },
  );
}
