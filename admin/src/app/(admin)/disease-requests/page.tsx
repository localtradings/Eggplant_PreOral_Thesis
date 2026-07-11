import Image from "next/image";
import Link from "next/link";
import { CheckCircle2, ClipboardList, ImageOff } from "lucide-react";
import { AdminShell } from "@/components/admin-shell";
import { RequestReviewActions } from "@/components/request-review-actions";
import { requireAdmin } from "@/lib/auth";
import { getAdminClient } from "@/lib/supabase/admin";
import { randomUUID } from "node:crypto";
import { updateDiseaseRequest } from "./actions";
import { REQUEST_REVIEW_STATUSES } from "./constants";

export const dynamic = "force-dynamic";

type RequestPhoto = { position: number; object_path: string };

function StatusPill({ status }: { status: string }) {
  const tone = status === "planned"
    ? "bg-[#e9f6eb] text-[#247936]"
    : status === "not_supported" || status === "closed"
      ? "bg-[#fff0f2] text-[#a92f40]"
      : "bg-[#f0eafa] text-[#512b91]";
  return <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${tone}`}>{status.replaceAll("_", " ")}</span>;
}

function RequestThumbnail({ url, name }: { url?: string; name: string }) {
  return <div className="relative h-20 w-24 shrink-0 overflow-hidden rounded-xl bg-[#eeeaf4] sm:h-16 sm:w-20">
    {url ? <Image src={url} alt={`Private request photo for ${name}`} fill sizes="96px" unoptimized className="object-cover" /> : <div className="grid h-full place-items-center text-[#777286]"><ImageOff size={18} aria-label="Photo unavailable" /></div>}
  </div>;
}

export default async function DiseaseRequestsPage({ searchParams }: { searchParams: Promise<{ reviewed?: string; outcome?: string }> }) {
  const admin = await requireAdmin();
  const supabase = getAdminClient();
  const { data = [], error } = await supabase
    .from("disease_requests")
    .select("id,requested_name,notes,status,created_at,model_version,disease_request_photos(position,object_path)")
    .order("created_at", { ascending: false })
    .limit(100);
  const requests = data ?? [];
  const primaryPhotoPaths = requests
    .map((request) => ([...(request.disease_request_photos ?? [])] as RequestPhoto[]).sort((a, b) => a.position - b.position)[0]?.object_path)
    .filter((path): path is string => Boolean(path));
  const signedResult = primaryPhotoPaths.length
    ? await supabase.storage.from("eggplant-scans").createSignedUrls(primaryPhotoPaths, 300)
    : { data: [], error: null };
  const signedByPath = new Map(primaryPhotoPaths.map((path, index) => [path, signedResult.data?.[index]?.signedUrl ?? ""]));
  const reviewed = (await searchParams).reviewed;
  const outcome = (await searchParams).outcome;
  const reviewSucceeded = reviewed && REQUEST_REVIEW_STATUSES.includes(reviewed as (typeof REQUEST_REVIEW_STATUSES)[number]);

  return <AdminShell active="/disease-requests" role={admin.role}>
    <div className="fade-up mx-auto max-w-[1240px]">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div><h1 className="text-3xl font-bold tracking-[-.03em]">Disease requests</h1><p className="mt-1 text-sm text-[#6f6b80]">Private no-match requests. Review the real submitted photo before changing status.</p></div>
        {!error && <p className="rounded-xl border border-[#ded9e8] bg-white px-3 py-2 text-sm font-semibold text-[#5e596e]">{requests.length} recent request{requests.length === 1 ? "" : "s"}</p>}
      </header>
      {reviewSucceeded && <p role="status" className="status-banner mt-5 flex items-center gap-2 rounded-xl border border-[#bfe4c5] bg-[#f1fbf2] p-3 text-sm font-semibold text-[#247936]"><CheckCircle2 size={17}/>{outcome === "unchanged" ? "No request changes were needed." : `Request marked ${reviewed.replaceAll("_", " ")}.`}</p>}
      {error ? <p role="alert" className="mt-6 rounded-xl bg-[#fff0f2] p-4 text-sm text-[#a92f40]">Disease requests are temporarily unavailable.</p> : requests.length === 0 ? <div className="surface mt-6 grid place-items-center p-16 text-center"><ClipboardList size={34} className="text-[#512b91]"/><h2 className="mt-3 text-lg font-bold">No open requests</h2><p className="mt-1 text-sm text-[#6f6b80]">Private no-match reports will appear here.</p></div> : <>
        <section className="mt-6 grid gap-3 md:hidden" aria-label="Disease request cards">
          {requests.map((request) => {
            const photos = ([...(request.disease_request_photos ?? [])] as RequestPhoto[]).sort((a, b) => a.position - b.position);
            const name = request.requested_name || "Name not provided";
            return <article className="surface request-item p-4" key={request.id}>
              <div className="flex items-start gap-3"><RequestThumbnail url={photos[0] ? signedByPath.get(photos[0].object_path) : undefined} name={name}/><div className="min-w-0 flex-1"><div className="flex flex-wrap items-start justify-between gap-2"><Link className="focus-ring safe-long-content font-semibold text-[#512b91] hover:underline" href={`/disease-requests/${request.id}`}>{name}</Link><StatusPill status={request.status}/></div><p className="safe-long-content mt-2 line-clamp-3 text-sm leading-5 text-[#625e72]">{request.notes || "No notes supplied."}</p><p className="mt-2 font-mono text-xs text-[#777286]">{photos.length} photo{photos.length === 1 ? "" : "s"} · {new Date(request.created_at).toLocaleString()}</p></div></div>
              <div className="mt-4 flex items-center justify-between gap-3 border-t border-[#ece9f1] pt-3"><Link href={`/disease-requests/${request.id}`} className="focus-ring text-sm font-semibold text-[#512b91]">Open review</Link><RequestReviewActions action={updateDiseaseRequest} requestId={request.id} currentStatus={request.status} plannedIdempotencyKey={randomUUID()} unsupportedIdempotencyKey={randomUUID()} compact/></div>
            </article>;
          })}
        </section>
        <section className="surface mt-6 hidden overflow-x-auto p-5 md:block">
          <table className="w-full min-w-[980px] text-left text-sm"><thead className="text-xs uppercase tracking-wide text-[#797487]"><tr><th className="pb-3">Photo</th><th className="pb-3">Requested disease</th><th className="pb-3">Notes</th><th className="pb-3">Status</th><th className="pb-3">Submitted</th><th className="pb-3 text-right">Quick review</th></tr></thead><tbody className="divide-y divide-[#ece9f1]">{requests.map((request) => {
            const photos = ([...(request.disease_request_photos ?? [])] as RequestPhoto[]).sort((a, b) => a.position - b.position);
            const name = request.requested_name || "Name not provided";
            return <tr className="request-item" key={request.id}><td className="py-3 pr-4"><RequestThumbnail url={photos[0] ? signedByPath.get(photos[0].object_path) : undefined} name={name}/></td><td className="max-w-[13rem] py-3 pr-4 align-top"><Link className="focus-ring safe-long-content font-semibold text-[#512b91] hover:underline" href={`/disease-requests/${request.id}`}>{name}</Link><p className="mt-1 font-mono text-xs text-[#777286]">{photos.length} photo{photos.length === 1 ? "" : "s"}</p></td><td className="max-w-[20rem] py-3 pr-4 align-top text-[#666174]"><p className="safe-long-content line-clamp-3 leading-5">{request.notes || "—"}</p></td><td className="py-3 pr-4 align-top"><StatusPill status={request.status}/></td><td className="whitespace-nowrap py-3 pr-4 align-top font-mono text-xs text-[#777286]">{new Date(request.created_at).toLocaleString()}</td><td className="py-3 text-right align-top"><RequestReviewActions action={updateDiseaseRequest} requestId={request.id} currentStatus={request.status} plannedIdempotencyKey={randomUUID()} unsupportedIdempotencyKey={randomUUID()}/></td></tr>;
          })}</tbody></table>
        </section>
      </>}
    </div>
  </AdminShell>;
}
