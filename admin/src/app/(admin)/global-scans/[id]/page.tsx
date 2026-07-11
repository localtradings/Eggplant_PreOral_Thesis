import { AdminShell } from "@/components/admin-shell";
import { hashActionPayload, requireIdempotencyKey } from "@/lib/action-idempotency";
import { requireAdmin } from "@/lib/auth";
import { getAdminClient } from "@/lib/supabase/admin";
import { revalidatePath } from "next/cache";
import { notFound, redirect } from "next/navigation";
import { ArrowLeft, CheckCircle2, ShieldAlert, Trash2 } from "lucide-react";
import Link from "next/link";
import { UUID_PATTERN } from "@/lib/mobile-validation";
import Image from "next/image";
import { FormSubmitButton } from "@/components/form-submit-button";
import { randomUUID } from "node:crypto";
import type { ReactNode } from "react";

export const dynamic = "force-dynamic";

async function moderate(formData: FormData) {
  "use server";
  const admin = await requireAdmin(["owner", "admin", "reviewer"]);
  const id = String(formData.get("id") ?? "");
  const status = String(formData.get("status") ?? "");
  const idempotencyKey = requireIdempotencyKey(formData);
  if (!UUID_PATTERN.test(id) || !["published", "quarantined", "removed"].includes(status)) throw new Error("Invalid moderation action.");
  const reason = String(formData.get("reason") ?? "Manual admin action").slice(0, 500);
  const supabase = getAdminClient();
  const { data: outcome, error } = await supabase.rpc("moderate_scan_contribution_v2", {
    p_contribution_id: id,
    p_status: status,
    p_reason: reason,
    p_admin_id: admin.user.id,
    p_idempotency_key: idempotencyKey,
    p_payload_hash: hashActionPayload({ contributionId: id, status, reason }),
  });
  if (error || !["applied", "unchanged"].includes(outcome ?? "")) throw new Error("The scan could not be moderated. Refresh and try again.");
  revalidatePath("/global-scans");
  redirect(`/global-scans/${id}?moderated=${encodeURIComponent(status)}&outcome=${encodeURIComponent(outcome)}`);
}

export default async function GlobalScanDetail({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ moderated?: string; outcome?: string }> }) {
  const admin = await requireAdmin(["owner", "admin", "reviewer"]); const { id } = await params; const supabase = getAdminClient();
  const moderated = (await searchParams).moderated;
  const outcome = (await searchParams).outcome;
  const { data: scan, error: scanError } = await supabase.from("scan_contributions").select("*").eq("id", id).maybeSingle();
  if (scanError) throw new Error("The Global Scan could not be loaded.");
  if (!scan) notFound();
  const [{ data: signed, error: signedError }, { count: reports, error: reportError }] = await Promise.all([
    supabase.storage.from("eggplant-scans").createSignedUrl(scan.photo_path, 300),
    supabase.from("content_reports").select("id", { count: "exact", head: true }).eq("contribution_id", id),
  ]);
  if (signedError || reportError) throw new Error("The Global Scan review data could not be loaded.");
  const moderationSucceeded = ["published", "quarantined", "removed"].includes(moderated ?? "");
  return <AdminShell active="/global-scans" role={admin.role}><div className="fade-up mx-auto max-w-[1240px]"><Link href="/global-scans" className="inline-flex items-center gap-2 text-sm font-semibold text-[#512b91]"><ArrowLeft size={17}/>Back to scans</Link>{moderationSucceeded && <p role="status" className="status-banner mt-4 flex items-center gap-2 rounded-xl border border-[#bfe4c5] bg-[#f1fbf2] p-3 text-sm font-semibold text-[#247936]"><CheckCircle2 size={17}/>{outcome === "unchanged" ? "No scan status change was needed." : `Scan status updated to ${moderated}.`}</p>}<div className="mt-4 grid gap-5 xl:grid-cols-[minmax(0,1.45fr)_minmax(19rem,.8fr)]"><section className="surface overflow-hidden"><div className="relative aspect-[4/3] bg-[#ece8f1]">{signed?.signedUrl ? <Image src={signed.signedUrl} alt="Shared eggplant disease photo" fill sizes="(min-width: 1280px) 60vw, 100vw" unoptimized className="object-contain"/> : <div className="grid h-full place-items-center text-sm text-[#777286]">Photo unavailable</div>}</div></section><aside className="min-w-0 space-y-5"><section className="surface p-5"><h1 className="safe-long-content text-2xl font-bold capitalize">{scan.disease_id.replaceAll("-", " ")}</h1><dl className="mt-4 divide-y divide-[#ece9f1] text-sm"><Row label="Confidence" value={`${Math.round(Number(scan.confidence)*100)}%`}/><Row label="Source" value={scan.source}/><Row label="Status" value={scan.status}/><Row label="Reports" value={String(reports ?? 0)}/><Row label="Model" value={scan.model_version}/></dl></section><section className="surface p-5"><h2 className="font-bold">Moderation actions</h2><p className="mt-1 text-xs text-[#777286]">Buttons lock while the server applies the change. Every completed action is recorded in the audit log.</p><div className="mt-4 grid gap-2"><Action id={id} status="published" label="Publish / restore" icon={<CheckCircle2 size={17}/>} style="border-[#70b77b] text-[#247936]"/><Action id={id} status="quarantined" label="Quarantine" icon={<ShieldAlert size={17}/>} style="border-[#e0a44e] text-[#915707]"/><Action id={id} status="removed" label="Remove from public" icon={<Trash2 size={17}/>} style="border-[#e38b96] text-[#b12d40]"/></div></section></aside></div></div></AdminShell>;
}
function Row({label,value}:{label:string;value:string}){return <div className="flex flex-wrap justify-between gap-4 py-3"><dt className="text-[#716c80]">{label}</dt><dd className="safe-long-content max-w-[65%] text-right font-mono font-semibold">{value}</dd></div>}
function Action({id,status,label,icon,style}:{id:string;status:string;label:string;icon:ReactNode;style:string}){return <form action={moderate}><input type="hidden" name="id" value={id}/><input type="hidden" name="status" value={status}/><input type="hidden" name="idempotency_key" value={randomUUID()}/><FormSubmitButton label={label} pendingLabel="Applying change" icon={icon} className={`w-full border px-4 ${style}`}/></form>}
