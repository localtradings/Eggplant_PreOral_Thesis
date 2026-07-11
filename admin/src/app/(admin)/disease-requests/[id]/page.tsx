import { AdminShell } from "@/components/admin-shell";
import { requireAdmin } from "@/lib/auth";
import { getAdminClient } from "@/lib/supabase/admin";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { UUID_PATTERN } from "@/lib/mobile-validation";
import Image from "next/image";

export const dynamic = "force-dynamic";
const STATUSES = ["under_review","planned","needs_information","not_supported","closed"];
type RequestPhoto = { position: number; object_path: string };

async function updateRequest(formData: FormData) {
  "use server";
  const admin = await requireAdmin(["owner", "admin", "reviewer"]); const id=String(formData.get("id")??""); const status=String(formData.get("status")??""); const note=String(formData.get("note")??"").trim();
  if(!UUID_PATTERN.test(id) || !STATUSES.includes(status) || note.length > 2_000) throw new Error("Invalid request review.");
  const supabase=getAdminClient();
  const {data:updated,error}=await supabase.rpc("review_disease_request",{p_request_id:id,p_status:status,p_admin_note:note,p_admin_id:admin.user.id});
  if(error || !updated) throw new Error("The request could not be reviewed. Refresh and try again.");
  redirect(`/disease-requests/${id}`);
}

export default async function RequestReview({params}:{params:Promise<{id:string}>}){
  const admin=await requireAdmin(["owner", "admin", "reviewer"]); const {id}=await params; const supabase=getAdminClient();
  const {data:request,error:requestError}=await supabase.from("disease_requests").select("*,disease_request_photos(position,object_path)").eq("id",id).maybeSingle();
  if(requestError) throw new Error("The disease request could not be loaded.");
  if(!request) notFound();
  const photos=([...(request.disease_request_photos??[])] as RequestPhoto[]).sort((a,b)=>a.position-b.position);
  const signedResult=photos.length?await supabase.storage.from("eggplant-scans").createSignedUrls(photos.map((photo)=>photo.object_path),300):{data:[],error:null};
  if(signedResult.error) throw new Error("Private request photos could not be loaded.");
  const signed=signedResult.data??[];
  const {data:timeline=[],error:timelineError}=await supabase.from("moderation_actions").select("id,action,reason,created_at").eq("request_id",id).order("created_at",{ascending:false});
  if(timelineError) throw new Error("The request audit timeline could not be loaded.");
  return <AdminShell active="/disease-requests" role={admin.role}><div className="fade-up mx-auto max-w-6xl"><Link href="/disease-requests" className="inline-flex items-center gap-2 text-sm font-semibold text-[#512b91]"><ArrowLeft size={17}/>Back to requests</Link><div className="mt-5 grid gap-5 lg:grid-cols-[1.4fr_.8fr]"><section><div className="grid gap-4 sm:grid-cols-2">{signed?.map((photo,index)=><div className="surface relative aspect-[4/3] overflow-hidden" key={photos[index]?.object_path}>{photo.signedUrl?<Image src={photo.signedUrl} alt={`Private plant photo ${index+1}`} fill sizes="(min-width: 640px) 50vw, 100vw" unoptimized className="object-contain"/>:<div className="grid h-full place-items-center">Photo unavailable</div>}</div>)}</div><section className="surface mt-5 p-5"><h1 className="text-2xl font-bold">{request.requested_name}</h1><p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-[#625e72]">{request.notes||"No notes supplied."}</p><dl className="mt-4 text-sm"><div className="flex justify-between border-t py-3"><dt>Status</dt><dd className="font-semibold">{request.status.replaceAll("_"," ")}</dd></div><div className="flex justify-between border-t py-3"><dt>Model version</dt><dd className="font-mono text-xs">{request.model_version}</dd></div></dl></section></section><aside className="space-y-5"><form action={updateRequest} className="surface p-5"><input type="hidden" name="id" value={id}/><h2 className="font-bold">Review decision</h2><label className="mt-4 grid gap-1.5 text-sm font-semibold">Status<select name="status" defaultValue={request.status==="submitted"?"under_review":request.status} className="focus-ring h-11 rounded-xl border px-3">{STATUSES.map((status)=><option value={status} key={status}>{status.replaceAll("_"," ")}</option>)}</select></label><label className="mt-4 grid gap-1.5 text-sm font-semibold">Admin note<textarea name="note" maxLength={2_000} defaultValue={request.admin_note??""} rows={4} className="focus-ring rounded-xl border p-3 font-normal"/></label><button className="focus-ring mt-4 h-11 w-full rounded-xl bg-[#512b91] font-semibold text-white">Save review</button></form><section className="surface p-5"><h2 className="font-bold">Timeline</h2><div className="mt-3 grid gap-3">{(timeline??[]).length===0?<p className="mt-3 text-sm text-[#777286]">No review actions yet.</p>:(timeline??[]).map((event)=><div className="border-l-2 border-[#bda8dd] pl-3 text-sm" key={event.id}><p className="font-semibold">{event.action.replaceAll("_"," ")}</p><p className="text-xs text-[#777286]">{new Date(event.created_at).toLocaleString()}</p>{event.reason&&<p className="mt-1">{event.reason}</p>}</div>)}</div></section></aside></div></div></AdminShell>;
}
