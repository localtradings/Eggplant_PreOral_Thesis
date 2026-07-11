import { AdminShell } from "@/components/admin-shell";
import { requireAdmin } from "@/lib/auth";
import { getAdminClient } from "@/lib/supabase/admin";
import { ArrowLeft, CheckCircle2 } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import Image from "next/image";
import { FormSubmitButton } from "@/components/form-submit-button";
import { updateDiseaseRequest } from "../actions";
import { REQUEST_REVIEW_STATUSES } from "../constants";
import { randomUUID } from "node:crypto";

export const dynamic = "force-dynamic";
type RequestPhoto = { position: number; object_path: string };

export default async function RequestReview({params,searchParams}:{params:Promise<{id:string}>;searchParams:Promise<{reviewed?:string;outcome?:string}>}){
  const admin=await requireAdmin(["owner", "admin", "reviewer"]); const {id}=await params; const supabase=getAdminClient();
  const reviewed=(await searchParams).reviewed;
  const outcome=(await searchParams).outcome;
  const {data:request,error:requestError}=await supabase.from("disease_requests").select("*,disease_request_photos(position,object_path)").eq("id",id).maybeSingle();
  if(requestError) throw new Error("The disease request could not be loaded.");
  if(!request) notFound();
  const photos=([...(request.disease_request_photos??[])] as RequestPhoto[]).sort((a,b)=>a.position-b.position);
  const signedResult=photos.length?await supabase.storage.from("eggplant-scans").createSignedUrls(photos.map((photo)=>photo.object_path),300):{data:[],error:null};
  if(signedResult.error) throw new Error("Private request photos could not be loaded.");
  const signed=signedResult.data??[];
  const {data:timeline=[],error:timelineError}=await supabase.from("moderation_actions").select("id,action,reason,created_at").eq("request_id",id).order("created_at",{ascending:false});
  if(timelineError) throw new Error("The request audit timeline could not be loaded.");
  const reviewSucceeded = reviewed && REQUEST_REVIEW_STATUSES.includes(reviewed as (typeof REQUEST_REVIEW_STATUSES)[number]);
  return <AdminShell active="/disease-requests" role={admin.role}><div className="fade-up mx-auto max-w-6xl"><Link href="/disease-requests" className="inline-flex items-center gap-2 text-sm font-semibold text-[#512b91]"><ArrowLeft size={17}/>Back to requests</Link>{reviewSucceeded && <p role="status" className="status-banner mt-4 flex items-center gap-2 rounded-xl border border-[#bfe4c5] bg-[#f1fbf2] p-3 text-sm font-semibold text-[#247936]"><CheckCircle2 size={17}/>{outcome === "unchanged" ? "No request changes were needed." : `Request marked ${reviewed.replaceAll("_"," ")}.`}</p>}<div className="mt-5 grid gap-5 lg:grid-cols-[minmax(0,1.4fr)_minmax(19rem,.8fr)]"><section className="min-w-0"><div className="grid gap-4 sm:grid-cols-2">{signed?.map((photo,index)=><div className="surface relative aspect-[4/3] overflow-hidden" key={photos[index]?.object_path}>{photo.signedUrl?<Image src={photo.signedUrl} alt={`Private plant photo ${index+1}`} fill sizes="(min-width: 640px) 50vw, 100vw" unoptimized className="object-contain"/>:<div className="grid h-full place-items-center text-sm text-[#777286]">Photo unavailable</div>}</div>)}</div><section className="surface mt-5 p-5"><p className="text-xs font-semibold uppercase tracking-[.12em] text-[#278b3d]">Private disease request</p><h1 className="mt-1 text-2xl font-bold">{request.requested_name || "Name not provided"}</h1><p className="safe-long-content mt-3 whitespace-pre-wrap text-sm leading-6 text-[#625e72]">{request.notes||"No notes supplied."}</p><dl className="mt-4 text-sm"><div className="flex flex-wrap justify-between gap-3 border-t py-3"><dt>Status</dt><dd className="font-semibold">{request.status.replaceAll("_"," ")}</dd></div><div className="flex flex-wrap justify-between gap-3 border-t py-3"><dt>Model version</dt><dd className="break-all font-mono text-xs">{request.model_version}</dd></div></dl></section></section><aside className="min-w-0 space-y-5"><form action={updateDiseaseRequest} className="surface p-5"><input type="hidden" name="id" value={id}/><input type="hidden" name="idempotency_key" value={randomUUID()}/><h2 className="font-bold">Review decision</h2><p className="mt-1 text-xs leading-5 text-[#777286]">Changes are recorded after the server confirms them.</p><label className="mt-4 grid gap-1.5 text-sm font-semibold">Status<select name="status" defaultValue={request.status==="submitted"?"under_review":request.status} className="focus-ring h-11 rounded-xl border border-[#dcd8e4] px-3">{REQUEST_REVIEW_STATUSES.map((status)=><option value={status} key={status}>{status.replaceAll("_"," ")}</option>)}</select></label><label className="mt-4 grid gap-1.5 text-sm font-semibold">Admin note<textarea name="note" maxLength={2_000} defaultValue={request.admin_note??""} rows={4} className="focus-ring safe-long-content rounded-xl border border-[#dcd8e4] p-3 font-normal"/></label><input type="hidden" name="return_to" value="detail"/><FormSubmitButton label="Save review" pendingLabel="Saving review" className="mt-4 w-full bg-[#512b91] px-4 text-white"/></form><section className="surface p-5"><h2 className="font-bold">Timeline</h2><div className="mt-3 grid gap-3">{(timeline??[]).length===0?<p className="mt-3 text-sm text-[#777286]">No review actions yet.</p>:(timeline??[]).map((event)=><div className="safe-long-content border-l-2 border-[#bda8dd] pl-3 text-sm" key={event.id}><p className="font-semibold">{event.action.replaceAll("_"," ")}</p><p className="text-xs text-[#777286]">{new Date(event.created_at).toLocaleString()}</p>{event.reason&&<p className="mt-1 whitespace-pre-wrap">{event.reason}</p>}</div>)}</div></section></aside></div></div></AdminShell>;
}
