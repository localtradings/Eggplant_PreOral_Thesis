import { AdminShell } from "@/components/admin-shell";
import { requireAdmin } from "@/lib/auth";
import { getAdminClient } from "@/lib/supabase/admin";
import { ArrowLeft, LockKeyhole } from "lucide-react";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";

export const dynamic = "force-dynamic";

async function saveContent(formData: FormData) {
  "use server";
  const admin = await requireAdmin(["owner", "admin"]);
  const id = String(formData.get("id") ?? "");
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(id)) throw new Error("Invalid disease ID.");
  const payloads = (["en", "fil"] as const).map((language) => {
    const fields = ["name", "description", "symptom_preview", "causes", "prevention", "guidance", "when_to_act", "disclaimer"] as const;
    const content = Object.fromEntries(fields.map((field) => [field, String(formData.get(`${language}_${field}`) ?? "").trim()]));
    if (Object.values(content).some((value) => value.length < 2 || value.length > 4_000)) throw new Error(`Complete all ${language} content fields within the allowed length.`);
    const reference = {
      publisher: String(formData.get(`${language}_reference_publisher`) ?? "").trim(),
      title: String(formData.get(`${language}_reference_title`) ?? "").trim(),
      url: String(formData.get(`${language}_reference_url`) ?? "").trim(),
    };
    if (!isSafeReference(reference)) throw new Error("Each reference requires a publisher, title, and public HTTPS URL.");
    const signs = String(formData.get(`${language}_signs`) ?? "")
      .split("\n")
      .map((sign) => sign.trim())
      .filter(Boolean);
    if (signs.length < 1 || signs.length > 20 || signs.some((sign) => sign.length < 2 || sign.length > 500)) {
      throw new Error(`Provide 1 to 20 ${language} symptoms, one per line.`);
    }
    return { language, content, reference, signs };
  });
  const [english, filipino] = payloads;
  const { error } = await getAdminClient().rpc("update_disease_catalog_content", {
    p_disease_id: id,
    p_en_content: english.content,
    p_fil_content: filipino.content,
    p_en_signs: english.signs,
    p_fil_signs: filipino.signs,
    p_en_reference: english.reference,
    p_fil_reference: filipino.reference,
    p_admin_id: admin.user.id,
  });
  if (error) throw new Error("The bilingual disease content could not be published.");
  redirect("/disease-catalog");
}

export default async function DiseaseContentEditor({ params }: { params: Promise<{ id: string }> }) {
  const admin = await requireAdmin(["owner", "admin"]); const { id } = await params; const supabase = getAdminClient();
  const { data: disease, error } = await supabase.from("disease_catalog").select("id,model_class_index,model_label,category,content_version,disease_localizations(*),disease_signs(*),disease_references(*)").eq("id", id).maybeSingle();
  if (error) throw new Error("The disease content could not be loaded.");
  if (!disease) notFound();
  return <AdminShell active="/disease-catalog" role={admin.role}><div className="fade-up mx-auto max-w-5xl"><Link href="/disease-catalog" className="inline-flex items-center gap-2 text-sm font-semibold text-[#512b91]"><ArrowLeft size={17}/>Back to catalog</Link><header className="mt-4 flex flex-wrap items-start justify-between gap-4"><div><h1 className="text-3xl font-bold capitalize">{disease.id.replaceAll("-", " ")}</h1><p className="mt-1 text-sm text-[#6f6b80]">Edit bilingual educational content, symptoms, and citations.</p></div><div className="rounded-xl bg-[#f0eafa] px-3 py-2 font-mono text-xs text-[#512b91]"><LockKeyhole className="mr-1 inline" size={14}/>Class {disease.model_class_index}: {disease.model_label} (read-only)</div></header><form action={saveContent} className="mt-6 grid gap-5"><input type="hidden" name="id" value={disease.id}/>{(["en","fil"] as const).map((language)=>{const content=disease.disease_localizations?.find((row)=>row.language_tag===language); const signs=(disease.disease_signs??[]).filter((row)=>row.language_tag===language).sort((a,b)=>a.position-b.position).map((row)=>row.text); const reference=disease.disease_references?.find((row)=>row.language_tag===language && row.position===0); return <section className="surface p-5" key={language}><h2 className="text-xl font-bold">{language === "en" ? "English" : "Filipino"}</h2><div className="mt-4 grid gap-4 sm:grid-cols-2"><Field name={`${language}_name`} label="Name" value={content?.name}/><Field name={`${language}_symptom_preview`} label="Symptom preview" value={content?.symptom_preview}/></div><SignsArea name={`${language}_signs`} value={signs.join("\n")}/>{(["description","causes","prevention","guidance","when_to_act","disclaimer"] as const).map((field)=><Area key={field} name={`${language}_${field}`} label={field.replaceAll("_"," ")} value={content?.[field]}/>) }<h3 className="mt-5 font-bold">Primary authoritative reference</h3><div className="mt-3 grid gap-4 sm:grid-cols-2"><Field name={`${language}_reference_publisher`} label="Publisher" value={reference?.publisher}/><Field name={`${language}_reference_title`} label="Title" value={reference?.title}/></div><Field name={`${language}_reference_url`} label="HTTPS URL" value={reference?.url}/></section>})}<button className="focus-ring h-12 rounded-xl bg-[#512b91] px-5 font-semibold text-white transition-transform hover:-translate-y-0.5">Publish content update</button></form></div></AdminShell>;
}

function Field({name,label,value}:{name:string;label:string;value?:string}){return <label className="mt-3 grid gap-1.5 text-sm font-semibold">{label}<input required maxLength={label === "HTTPS URL" ? 2_048 : 500} name={name} defaultValue={value ?? ""} className="focus-ring min-h-11 rounded-xl border border-[#dcd8e4] px-3 font-normal"/></label>}
function Area({name,label,value}:{name:string;label:string;value?:string}){return <label className="mt-4 grid gap-1.5 text-sm font-semibold capitalize">{label}<textarea required maxLength={4_000} name={name} defaultValue={value ?? ""} rows={3} className="focus-ring rounded-xl border border-[#dcd8e4] p-3 font-normal leading-6"/></label>}
function SignsArea({name,value}:{name:string;value:string}){return <label className="mt-4 grid gap-1.5 text-sm font-semibold">Symptoms (one per line)<textarea required maxLength={10_019} name={name} defaultValue={value} rows={5} className="focus-ring rounded-xl border border-[#dcd8e4] p-3 font-normal leading-6"/></label>}

function isSafeReference(reference: {publisher:string;title:string;url:string}) {
  if (!reference.publisher || reference.publisher.length > 500 || !reference.title || reference.title.length > 500 || reference.url.length > 2_048) return false;
  try {
    const url = new URL(reference.url);
    return url.protocol === "https:" && !url.username && !url.password && Boolean(url.hostname);
  } catch {
    return false;
  }
}
