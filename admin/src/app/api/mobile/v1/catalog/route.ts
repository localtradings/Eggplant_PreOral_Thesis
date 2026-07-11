import { NextResponse } from "next/server";
import { apiError, authorizeMobile } from "@/lib/mobile-api";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET(request: Request) {
  const auth = await authorizeMobile(request);
  if ("response" in auth) return auth.response;
  const languageTag = new URL(request.url).searchParams.get("lang") === "fil" ? "fil" : "en";
  const supabase = getAdminClient();
  const [
    { data: config, error: configError },
    { data: diseases, error: diseaseError },
    { data: content, error: contentError },
    { data: signs, error: signsError },
    { data: references, error: referencesError },
  ] = await Promise.all([
    supabase.from("app_config").select("catalog_version").eq("id", true).single(),
    supabase.from("disease_catalog").select("id,model_class_index,model_label,category,artwork_key,content_version,updated_at").order("model_class_index"),
    supabase.from("disease_localizations").select("disease_id,name,description,symptom_preview,causes,recommended_action,prevention,guidance,when_to_act,disclaimer,updated_at").eq("language_tag", languageTag),
    supabase.from("disease_signs").select("disease_id,position,text").eq("language_tag", languageTag).order("position"),
    supabase.from("disease_references").select("disease_id,position,publisher,title,url").eq("language_tag", languageTag).order("position"),
  ]);
  if (configError || diseaseError || contentError || signsError || referencesError) {
    return apiError("Could not load the disease catalog.", 500, "catalog_failed");
  }
  const version = config?.catalog_version ?? 1;
  const etag = `\"catalog-${version}-${languageTag}\"`;
  if (request.headers.get("if-none-match") === etag) {
    return new NextResponse(null, {
      status: 304,
      headers: { ETag: etag, "Cache-Control": "private, no-cache" },
    });
  }
  return NextResponse.json({
    version,
    languageTag,
    diseases: (diseases ?? []).map((disease) => ({
      ...disease,
      content: content?.find((entry) => entry.disease_id === disease.id) ?? null,
      signs: signs?.filter((entry) => entry.disease_id === disease.id).map((entry) => entry.text) ?? [],
      references: references?.filter((entry) => entry.disease_id === disease.id) ?? [],
    })),
  }, {
    headers: { ETag: etag, "Cache-Control": "private, no-cache" },
  });
}
