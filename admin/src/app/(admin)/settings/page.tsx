import { AdminShell } from "@/components/admin-shell";
import { requireAdmin } from "@/lib/auth";
import { getAdminClient } from "@/lib/supabase/admin";
import { revalidatePath } from "next/cache";
import { Cloud, LockKeyhole } from "lucide-react";

export const dynamic = "force-dynamic";

async function setCloudWrites(formData: FormData) {
  "use server";
  const admin = await requireAdmin(["owner"]);
  const enabled = formData.get("enabled") === "true";
  const { data, error } = await getAdminClient().rpc("set_cloud_writes_enabled", {
    p_enabled: enabled,
    p_admin_id: admin.user.id,
  });
  if (error || data !== true) {
    throw new Error("The cloud-write safety control could not be changed.");
  }
  revalidatePath("/settings");
}

export default async function SettingsPage() {
  const admin = await requireAdmin();
  const supabase = getAdminClient();
  const [configResult, auditResult] = await Promise.all([
    supabase
      .from("app_config")
      .select("cloud_writes_enabled,catalog_version,updated_at")
      .eq("id", true)
      .single(),
    supabase
      .from("moderation_actions")
      .select("id,action,reason,created_at,contribution_id,request_id,resource_type,resource_key")
      .order("created_at", { ascending: false })
      .limit(50),
  ]);
  if (configResult.error || auditResult.error) {
    throw new Error("Production settings and audit data could not be loaded.");
  }
  const data = configResult.data;
  const audit = auditResult.data ?? [];
  const enabled = data.cloud_writes_enabled === true;
  return <AdminShell active="/settings" role={admin.role}><div className="fade-up mx-auto max-w-3xl"><h1 className="text-3xl font-bold tracking-[-.03em]">Audit & settings</h1><p className="mt-1 text-sm text-[#6f6b80]">Production safety controls for cloud writes and content delivery.</p><section className="surface mt-6 p-6"><div className="flex items-start gap-4"><span className={`rounded-full p-3 ${enabled ? "bg-[#e9f6eb] text-[#278b3d]" : "bg-[#fff0dd] text-[#995a06]"}`}><Cloud/></span><div className="flex-1"><h2 className="text-lg font-bold">Cloud writes {enabled ? "enabled" : "paused"}</h2><p className="mt-1 text-sm leading-6 text-[#686376]">When paused, Android keeps events in its offline outbox and reads remain available. Enable only after production smoke tests pass.</p>{admin.role === "owner" ? <form action={setCloudWrites} className="mt-4"><input type="hidden" name="enabled" value={String(!enabled)}/><button className={`focus-ring h-11 rounded-xl px-4 font-semibold text-white ${enabled ? "bg-[#b33143]" : "bg-[#512b91]"}`}>{enabled ? "Pause cloud writes" : "Enable cloud writes"}</button></form> : <p className="mt-4 text-xs font-semibold text-[#777286]">Only the owner can change this production kill switch.</p>}</div></div></section><section className="surface mt-5 p-6"><div className="flex items-start gap-4"><span className="rounded-full bg-[#f0eafa] p-3 text-[#512b91]"><LockKeyhole/></span><div><h2 className="text-lg font-bold">Security posture</h2><p className="mt-1 text-sm leading-6 text-[#686376]">Private Storage, anonymous owner-scoped data, server-only secret credentials, audited moderation, and explicit RLS are required. Catalog version: {data?.catalog_version ?? 1}.</p></div></div></section><section className="surface mt-5 p-6"><h2 className="text-lg font-bold">Moderation audit log</h2>{(audit??[]).length===0?<p className="mt-3 text-sm text-[#777286]">No moderation actions yet.</p>:<div className="mt-4 divide-y divide-[#ece9f1]">{(audit??[]).map((event)=><div className="py-3 text-sm" key={event.id}><div className="flex justify-between gap-4"><span className="font-semibold">{event.action.replaceAll("_"," ")}</span><span className="font-mono text-xs text-[#777286]">{new Date(event.created_at).toLocaleString()}</span></div><p className="mt-1 text-xs text-[#777286]">Target: {event.contribution_id??event.request_id??[event.resource_type,event.resource_key].filter(Boolean).join(":")}</p>{event.reason&&<p className="mt-1">{event.reason}</p>}</div>)}</div>}</section></div></AdminShell>;
}
