import "server-only";
import { getAdminClient } from "@/lib/supabase/admin";

export type DashboardData = {
  pendingReports: number;
  sharedScans: number;
  installations: number;
  openRequests: number;
  storageBytes: number;
  cloudWritesEnabled: boolean;
  lastInstallationSeenAt: string | null;
  rankings: Array<{ diseaseId: string; name: string; count: number }>;
  recent: Array<{ id: string; disease: string; confidence: number; publishedAt: string; status: string }>;
};

export async function getDashboardData(): Promise<DashboardData> {
  const supabase = getAdminClient();
  const [reports, scans, installs, requests, rankings, recent, storage, config, lastSeen] = await Promise.all([
    supabase.from("scan_contributions").select("id", { count: "exact", head: true }).eq("status", "quarantined"),
    supabase.from("scan_contributions").select("id", { count: "exact", head: true }).in("status", ["published", "expired"]),
    supabase.from("installations").select("owner_id", { count: "exact", head: true }),
    supabase.from("disease_requests").select("id", { count: "exact", head: true }).in("status", ["submitted", "under_review", "needs_information"]),
    supabase.from("global_disease_rankings").select("disease_id,scan_count").order("scan_count", { ascending: false }).limit(5),
    supabase.from("scan_contributions").select("id,disease_id,confidence,published_at,status").order("published_at", { ascending: false }).limit(6),
    supabase.rpc("admin_storage_usage"),
    supabase.from("app_config").select("cloud_writes_enabled").eq("id", true).single(),
    supabase.from("installations").select("last_seen_at").order("last_seen_at", { ascending: false }).limit(1).maybeSingle(),
  ]);
  const failures = [
    reports.error,
    scans.error,
    installs.error,
    requests.error,
    rankings.error,
    recent.error,
    storage.error,
    config.error,
    lastSeen.error,
  ].filter(Boolean);
  if (failures.length > 0) {
    throw new Error("The production dashboard metrics could not be loaded.");
  }
  const diseaseIds = [...new Set([...(rankings.data ?? []).map((x) => x.disease_id), ...(recent.data ?? []).map((x) => x.disease_id)])];
  const namesResult = diseaseIds.length
    ? await supabase.from("disease_localizations").select("disease_id,name").eq("language_tag", "en").in("disease_id", diseaseIds)
    : { data: [] as Array<{ disease_id: string; name: string }>, error: null };
  if (namesResult.error) {
    throw new Error("The production disease names could not be loaded.");
  }
  const names = new Map((namesResult.data ?? []).map((x) => [x.disease_id, x.name]));
  return {
    pendingReports: reports.count ?? 0,
    sharedScans: scans.count ?? 0,
    installations: installs.count ?? 0,
    openRequests: requests.count ?? 0,
    storageBytes: Number(storage.data ?? 0),
    cloudWritesEnabled: config.data?.cloud_writes_enabled === true,
    lastInstallationSeenAt: lastSeen.data?.last_seen_at ?? null,
    rankings: (rankings.data ?? []).map((x) => ({ diseaseId: x.disease_id, name: names.get(x.disease_id) ?? x.disease_id, count: Number(x.scan_count) })),
    recent: (recent.data ?? []).map((x) => ({
      id: x.id,
      disease: names.get(x.disease_id) ?? x.disease_id,
      confidence: Math.round(Number(x.confidence) * 100),
      publishedAt: x.published_at,
      status: x.status,
    })),
  };
}
