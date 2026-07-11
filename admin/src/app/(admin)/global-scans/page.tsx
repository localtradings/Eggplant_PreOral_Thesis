import Image from "next/image";
import Link from "next/link";
import { AlertTriangle, ChevronLeft, ChevronRight, Filter, Globe2, Search, X } from "lucide-react";
import { AdminShell } from "@/components/admin-shell";
import { requireAdmin } from "@/lib/auth";
import { getAdminClient } from "@/lib/supabase/admin";

export const dynamic = "force-dynamic";

const PAGE_SIZE = 12;
const STATUS_FILTERS = ["published", "all", "quarantined", "removed"] as const;
type StatusFilter = (typeof STATUS_FILTERS)[number];

function makeHref(page: number, status: StatusFilter, search: string) {
  const params = new URLSearchParams({ page: String(page) });
  if (status !== "published") params.set("status", status);
  if (search) params.set("q", search);
  return `/global-scans?${params.toString()}`;
}

function ScanStatus({ status }: { status: string }) {
  const style = status === "published" ? "bg-[#e9f6eb] text-[#247936]" : status === "removed" ? "bg-[#fff0f2] text-[#a92f40]" : "bg-[#fff0dd] text-[#995a06]";
  return <span className={`rounded-full px-2 py-1 text-xs font-semibold ${style}`}>{status === "quarantined" && <AlertTriangle className="mr-1 inline" size={12}/>} {status}</span>;
}

export default async function GlobalScansPage({ searchParams }: { searchParams: Promise<{ page?: string; status?: string; q?: string }> }) {
  const admin = await requireAdmin();
  const supabase = getAdminClient();
  const query = await searchParams;
  const status = STATUS_FILTERS.includes(query.status as StatusFilter) ? query.status as StatusFilter : "published";
  const search = (query.q ?? "").replace(/[^a-z0-9\s-]/gi, "").trim().slice(0, 80);

  let countQuery = supabase.from("scan_contributions").select("id", { count: "exact", head: true });
  if (status !== "all") countQuery = countQuery.eq("status", status);
  if (search) countQuery = countQuery.ilike("disease_id", `%${search}%`);
  const { count, error: countError } = await countQuery;
  const pageCount = Math.max(1, Math.ceil((count ?? 0) / PAGE_SIZE));
  const requestedPage = Number.parseInt(query.page ?? "1", 10);
  const page = Number.isFinite(requestedPage) ? Math.min(Math.max(requestedPage, 1), pageCount) : 1;
  const from = (page - 1) * PAGE_SIZE;

  let scansQuery = supabase
    .from("scan_contributions")
    .select("id,disease_id,confidence,status,published_at,photo_path")
    .order("published_at", { ascending: false })
    .range(from, from + PAGE_SIZE - 1);
  if (status !== "all") scansQuery = scansQuery.eq("status", status);
  if (search) scansQuery = scansQuery.ilike("disease_id", `%${search}%`);
  const { data = [], error: scansError } = await scansQuery;
  const error = countError ?? scansError;
  const scans = data ?? [];
  const paths = scans.map((scan) => scan.photo_path);
  const signedResult = paths.length ? await supabase.storage.from("eggplant-scans").createSignedUrls(paths, 300) : { data: [], error: null };
  const signedByPath = new Map(paths.map((path, index) => [path, signedResult.data?.[index]?.signedUrl ?? ""]));

  return <AdminShell active="/global-scans" role={admin.role}>
    <div className="fade-up mx-auto max-w-[1240px]">
      <header className="flex flex-wrap items-end justify-between gap-4"><div><h1 className="text-3xl font-bold tracking-[-.03em]">Global scans</h1><p className="mt-1 text-sm text-[#6f6b80]">Real community photos and post-publication moderation. Removed items are hidden by default.</p></div><p className="rounded-xl border border-[#ded9e8] bg-white px-3 py-2 text-sm font-semibold text-[#5e596e]">{count ?? 0} matching scan{count === 1 ? "" : "s"}</p></header>
      <form method="get" className="surface mt-6 grid gap-3 p-4 sm:grid-cols-[minmax(12rem,.55fr)_minmax(14rem,1fr)_auto] sm:items-end"><label className="grid gap-1.5 text-sm font-semibold">Status<select name="status" defaultValue={status} className="focus-ring h-11 rounded-xl border border-[#dcd8e4] bg-white px-3"><option value="published">Published</option><option value="all">All statuses</option><option value="quarantined">Quarantined</option><option value="removed">Removed</option></select></label><label className="grid gap-1.5 text-sm font-semibold">Disease search<div className="relative"><Search className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#777286]" size={17}/><input name="q" defaultValue={search} maxLength={80} placeholder="e.g. melon thrips" className="focus-ring h-11 w-full rounded-xl border border-[#dcd8e4] bg-white py-2 pl-10 pr-3 font-normal"/></div></label><div className="flex items-center gap-2"><button className="focus-ring action-button inline-flex h-11 items-center gap-2 rounded-xl bg-[#512b91] px-4 font-semibold text-white"><Filter size={16}/>Apply</button>{(status !== "published" || search) && <Link href="/global-scans" className="focus-ring inline-flex h-11 items-center gap-2 rounded-xl border border-[#dcd8e4] px-3 text-sm font-semibold text-[#5f596d]"><X size={16}/>Clear</Link>}</div></form>
      {error ? <p role="alert" className="mt-6 rounded-xl bg-[#fff0f2] p-4 text-sm text-[#a92f40]">Global scans are temporarily unavailable. Refresh to try again.</p> : scans.length === 0 ? <div className="surface mt-6 grid place-items-center p-16 text-center"><Globe2 size={34} className="text-[#512b91]"/><h2 className="mt-3 text-lg font-bold">No scans match these controls</h2><p className="mt-1 max-w-md text-sm text-[#6f6b80]">Try another disease name or status. Eligible app-camera scans appear after users explicitly share them.</p></div> : <>
        <section className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-label="Global scan results">{scans.map((scan) => <Link href={`/global-scans/${scan.id}`} key={scan.id} className="surface motion-card group overflow-hidden"><div className="relative aspect-[16/10] bg-[#eeeaf4]">{signedByPath.get(scan.photo_path) ? <Image src={signedByPath.get(scan.photo_path)!} alt={`Shared ${scan.disease_id.replaceAll("-", " ")} scan`} fill sizes="(min-width: 1280px) 30vw, (min-width: 640px) 50vw, 100vw" unoptimized className="object-cover transition-transform duration-300 group-hover:scale-[1.02]"/> : <div className="grid h-full place-items-center text-sm text-[#777286]">Photo unavailable</div>}</div><div className="p-4"><div className="flex items-start justify-between gap-3"><h2 className="safe-long-content font-semibold capitalize">{scan.disease_id.replaceAll("-", " ")}</h2><ChevronRight size={18} className="shrink-0 text-[#512b91]"/></div><div className="mt-3 flex items-center justify-between gap-3 text-sm"><span className="font-mono font-semibold text-[#278b3d]">{Math.round(Number(scan.confidence) * 100)}%</span><ScanStatus status={scan.status}/></div><p className="mt-3 font-mono text-xs text-[#777286]">{scan.published_at ? new Date(scan.published_at).toLocaleString() : "Publication pending"}</p></div></Link>)}</section>
        <nav aria-label="Global scan pages" className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-[#e4e1eb] pt-5"><p className="text-sm text-[#716c80]">Page <span className="font-semibold text-[#2b2341]">{page}</span> of {pageCount}</p><div className="flex gap-2"><Link aria-disabled={page <= 1} tabIndex={page <= 1 ? -1 : undefined} href={makeHref(Math.max(1, page - 1), status, search)} className={`focus-ring action-button inline-flex h-11 items-center gap-2 rounded-xl border border-[#dcd8e4] px-3 text-sm font-semibold ${page <= 1 ? "pointer-events-none opacity-45" : ""}`}><ChevronLeft size={17}/>Previous</Link><Link aria-disabled={page >= pageCount} tabIndex={page >= pageCount ? -1 : undefined} href={makeHref(Math.min(pageCount, page + 1), status, search)} className={`focus-ring action-button inline-flex h-11 items-center gap-2 rounded-xl border border-[#dcd8e4] px-3 text-sm font-semibold ${page >= pageCount ? "pointer-events-none opacity-45" : ""}`}>Next<ChevronRight size={17}/></Link></div></nav>
      </>}
    </div>
  </AdminShell>;
}
