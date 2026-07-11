import { AdminShell } from "@/components/admin-shell";
import { requireAdmin } from "@/lib/auth";
import { getAdminClient } from "@/lib/supabase/admin";
import { ClipboardList } from "lucide-react";
import Link from "next/link";

export const dynamic = "force-dynamic";

export default async function DiseaseRequestsPage() {
  const admin = await requireAdmin(); const supabase = getAdminClient();
  const { data = [], error } = await supabase.from("disease_requests").select("id,requested_name,notes,status,created_at,model_version,disease_request_photos(position,object_path)").order("created_at", { ascending: false }).limit(100);
  const requests = data ?? [];
  return <AdminShell active="/disease-requests" role={admin.role}><div className="fade-up mx-auto max-w-[1240px]"><header><h1 className="text-3xl font-bold tracking-[-.03em]">Disease requests</h1><p className="mt-1 text-sm text-[#6f6b80]">Private user requests for diseases not supported by the current model.</p></header>{error ? <p role="alert" className="mt-6 rounded-xl bg-[#fff0f2] p-4 text-sm text-[#a92f40]">Disease requests are temporarily unavailable.</p> : requests.length === 0 ? <div className="surface mt-6 grid place-items-center p-16 text-center"><ClipboardList size={34} className="text-[#512b91]"/><h2 className="mt-3 text-lg font-bold">No open requests</h2><p className="mt-1 text-sm text-[#6f6b80]">Private no-match reports will appear here.</p></div> : <section className="surface mt-6 overflow-x-auto p-5"><table className="w-full min-w-[760px] text-left text-sm"><thead className="text-xs uppercase tracking-wide text-[#797487]"><tr><th className="pb-3">Requested disease</th><th className="pb-3">Photos</th><th className="pb-3">Notes</th><th className="pb-3">Status</th><th className="pb-3">Submitted</th></tr></thead><tbody className="divide-y divide-[#ece9f1]">{requests.map((request)=><tr key={request.id}><td className="py-4 font-semibold"><Link className="text-[#512b91] hover:underline" href={`/disease-requests/${request.id}`}>{request.requested_name}</Link></td><td className="py-4 font-mono">{request.disease_request_photos?.length ?? 0}</td><td className="max-w-sm truncate py-4 text-[#666174]">{request.notes || "—"}</td><td className="py-4"><span className="rounded-full bg-[#f0eafa] px-2 py-1 text-xs font-semibold text-[#512b91]">{request.status.replaceAll("_"," ")}</span></td><td className="py-4 font-mono text-xs">{new Date(request.created_at).toLocaleString()}</td></tr>)}</tbody></table></section>}</div></AdminShell>;
}
