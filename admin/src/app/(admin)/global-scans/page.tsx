import { AdminShell } from "@/components/admin-shell";
import { requireAdmin } from "@/lib/auth";
import { getAdminClient } from "@/lib/supabase/admin";
import { AlertTriangle, ArrowUpRight, Globe2 } from "lucide-react";
import Link from "next/link";
import Image from "next/image";

export const dynamic = "force-dynamic";

export default async function GlobalScansPage() {
  const admin = await requireAdmin();
  const supabase = getAdminClient();
  const { data = [], error } = await supabase.from("scan_contributions").select("id,disease_id,confidence,status,published_at,photo_path").order("published_at", { ascending: false }).limit(100);
  if (error) throw new Error("Global scans are temporarily unavailable.");
  const scans = data ?? [];
  const paths = scans.map((x) => x.photo_path);
  const signedResult = paths.length
    ? await supabase.storage.from("eggplant-scans").createSignedUrls(paths, 300)
    : { data: [], error: null };
  if (signedResult.error) {
    throw new Error("Global scan photos are temporarily unavailable.");
  }
  const signed = signedResult.data ?? [];
  return <AdminShell active="/global-scans" role={admin.role}><div className="fade-up mx-auto max-w-[1240px]"><header><h1 className="text-3xl font-bold tracking-[-.03em]">Global scans</h1><p className="mt-1 text-sm text-[#6f6b80]">Published community photos, reports, and post-publication moderation.</p></header>
    {scans.length === 0 ? <div className="surface mt-6 grid place-items-center p-16 text-center"><Globe2 size={34} className="text-[#512b91]"/><h2 className="mt-3 text-lg font-bold">No shared scans yet</h2><p className="mt-1 max-w-md text-sm text-[#6f6b80]">Eligible app-camera scans will appear here after users explicitly share them.</p></div> : <section className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{scans.map((scan, index) => <Link href={`/global-scans/${scan.id}`} key={scan.id} className="surface motion-card overflow-hidden"><div className="relative aspect-[16/10] bg-[#eeeaf4]">{signed?.[index]?.signedUrl ? <Image src={signed[index].signedUrl!} alt="Shared eggplant scan" fill sizes="(min-width: 1280px) 30vw, (min-width: 640px) 50vw, 100vw" unoptimized className="object-cover"/> : <div className="grid h-full place-items-center text-sm text-[#777286]">Photo unavailable</div>}</div><div className="p-4"><div className="flex items-center justify-between gap-3"><h2 className="font-semibold capitalize">{scan.disease_id.replaceAll("-", " ")}</h2><ArrowUpRight size={17} className="text-[#512b91]"/></div><div className="mt-3 flex items-center justify-between text-sm"><span className="font-mono font-semibold text-[#278b3d]">{Math.round(Number(scan.confidence) * 100)}%</span><span className={`rounded-full px-2 py-1 text-xs font-semibold ${scan.status === "published" ? "bg-[#e9f6eb] text-[#247936]" : "bg-[#fff0dd] text-[#995a06]"}`}>{scan.status === "quarantined" && <AlertTriangle className="mr-1 inline" size={12}/>} {scan.status}</span></div></div></Link>)}</section>}
  </div></AdminShell>;
}
