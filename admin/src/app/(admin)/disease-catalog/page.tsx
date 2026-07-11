import { AdminShell } from "@/components/admin-shell";
import { requireAdmin } from "@/lib/auth";
import { getAdminClient } from "@/lib/supabase/admin";
import { BookOpen, ExternalLink } from "lucide-react";
import Link from "next/link";

export const dynamic = "force-dynamic";

export default async function CatalogPage() {
  const admin = await requireAdmin(); const supabase = getAdminClient();
  const { data = [], error } = await supabase.from("disease_catalog").select("id,category,model_class_index,model_label,content_version,disease_localizations(language_tag,name,symptom_preview,causes,guidance),disease_references(language_tag,publisher,title,url)").order("model_class_index");
  const diseases = data ?? [];
  return <AdminShell active="/disease-catalog" role={admin.role}><div className="fade-up mx-auto max-w-[1240px]"><header><h1 className="text-3xl font-bold tracking-[-.03em]">Disease catalog</h1><p className="mt-1 text-sm text-[#6f6b80]">Bilingual educational content. Detector model mappings are read-only.</p></header>{error ? <p role="alert" className="mt-6 rounded-xl bg-[#fff0f2] p-4 text-sm text-[#a92f40]">The disease catalog is temporarily unavailable.</p> : <section className="mt-6 grid gap-4 lg:grid-cols-2">{diseases.map((disease)=>{const en=disease.disease_localizations?.find((x)=>x.language_tag==="en"); return <Link href={`/disease-catalog/${disease.id}`} key={disease.id} className="surface motion-card p-5"><div className="flex items-start justify-between gap-4"><div><p className="text-xs font-semibold uppercase tracking-[.12em] text-[#278b3d]">{disease.category.replaceAll("_"," ")}</p><h2 className="mt-1 text-xl font-bold">{en?.name ?? disease.id}</h2></div><span className="rounded-lg bg-[#f0eafa] px-2 py-1 font-mono text-xs text-[#512b91]">Class {disease.model_class_index}</span></div><p className="mt-3 text-sm leading-6 text-[#625e72]">{en?.symptom_preview || "Content will be published from the bundled offline catalog."}</p><div className="mt-4 flex flex-wrap gap-2">{disease.disease_references?.filter((r)=>r.language_tag==="en").map((ref)=><span className="inline-flex items-center gap-1 text-xs font-semibold text-[#512b91]" key={ref.url}>{ref.publisher}<ExternalLink size={12}/></span>)}</div></Link>})}</section>}<div className="mt-5 rounded-xl border border-[#d9d3e4] bg-[#f9f7fc] p-4 text-sm text-[#655f74]"><BookOpen className="mr-2 inline text-[#512b91]" size={18}/>Content publishing increments the catalog version while preserving stable disease IDs and model-class mappings.</div></div></AdminShell>;
}
