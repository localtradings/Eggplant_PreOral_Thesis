import { NextResponse } from "next/server";
import { apiError, authorizeMobile } from "@/lib/mobile-api";
import {
  decodeFeedCursor,
  encodeFeedCursor,
  normalizePageLimit,
} from "@/lib/mobile-validation";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET(request: Request) {
  const auth = await authorizeMobile(request);
  if ("response" in auth) return auth.response;

  const url = new URL(request.url);
  const limit = normalizePageLimit(url.searchParams.get("limit"));
  const cursorValue = url.searchParams.get("cursor");
  const cursor = decodeFeedCursor(cursorValue);
  if (cursorValue && !cursor) {
    return apiError("Invalid feed cursor.", 400, "invalid_cursor");
  }
  const languageTag = url.searchParams.get("lang") === "fil" ? "fil" : "en";
  const supabase = getAdminClient();
  let query = supabase
    .from("scan_contributions")
    .select(
      "id,disease_id,confidence,source,model_version,photo_path,published_at",
    )
    .eq("status", "published")
    .gt("expires_at", new Date().toISOString())
    .order("published_at", { ascending: false })
    .order("id", { ascending: false })
    .limit(limit + 1);
  if (cursor) {
    query = query.or(
      `published_at.lt.${cursor.publishedAt},and(published_at.eq.${cursor.publishedAt},id.lt.${cursor.id})`,
    );
  }

  const { data, error } = await query;
  if (error) {
    return apiError(
      "Could not load Global Scans.",
      500,
      "feed_failed",
    );
  }
  const rows = data ?? [];
  const page = rows.slice(0, limit);
  const diseaseIds = [...new Set(page.map((entry) => entry.disease_id))];
  const filterIds = diseaseIds.length ? diseaseIds : ["__none__"];
  const [
    contentResult,
    signsResult,
    referencesResult,
    rankingsResult,
    signedResult,
  ] = await Promise.all([
    supabase
      .from("disease_localizations")
      .select(
        "disease_id,name,description,symptom_preview,causes,prevention,guidance,when_to_act,disclaimer",
      )
      .eq("language_tag", languageTag)
      .in("disease_id", filterIds),
    supabase
      .from("disease_signs")
      .select("disease_id,position,text")
      .eq("language_tag", languageTag)
      .in("disease_id", filterIds)
      .order("position"),
    supabase
      .from("disease_references")
      .select("disease_id,position,publisher,title,url")
      .eq("language_tag", languageTag)
      .in("disease_id", filterIds)
      .order("position"),
    supabase
      .from("global_disease_rankings")
      .select("disease_id,scan_count")
      .order("scan_count", { ascending: false }),
    page.length
      ? supabase.storage
          .from("eggplant-scans")
          .createSignedUrls(
            page.map((entry) => entry.photo_path),
            300,
          )
      : Promise.resolve({ data: [], error: null }),
  ]);
  if (
    contentResult.error ||
    signsResult.error ||
    referencesResult.error ||
    rankingsResult.error ||
    signedResult.error
  ) {
    return apiError(
      "Could not load complete Global Scan content.",
      500,
      "feed_content_failed",
    );
  }
  const localizedContent = contentResult.data ?? [];
  const diseaseSigns = signsResult.data ?? [];
  const diseaseReferences = referencesResult.data ?? [];
  const signedPhotos = signedResult.data ?? [];
  const rankingRows = rankingsResult.data ?? [];
  const contentMap = new Map(
    localizedContent.map((entry) => [entry.disease_id, entry]),
  );
  const last = page.at(-1);

  return NextResponse.json({
    items: page.map((entry, index) => ({
      id: entry.id,
      disease_id: entry.disease_id,
      confidence: entry.confidence,
      source: entry.source,
      model_version: entry.model_version,
      published_at: entry.published_at,
      photoUrl: signedPhotos[index]?.signedUrl ?? null,
      content: contentMap.get(entry.disease_id) ?? null,
      signs: diseaseSigns
        .filter((sign) => sign.disease_id === entry.disease_id)
        .map((sign) => sign.text),
      references: diseaseReferences.filter(
        (reference) => reference.disease_id === entry.disease_id,
      ),
    })),
    rankings: rankingRows.map((ranking) => ({
      diseaseId: ranking.disease_id,
      count: Number(ranking.scan_count),
    })),
    nextCursor:
      rows.length > limit && last
        ? encodeFeedCursor({
            publishedAt: new Date(last.published_at).toISOString(),
            id: last.id,
          })
        : null,
    generatedAt: new Date().toISOString(),
  });
}
