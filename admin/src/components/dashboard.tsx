import Link from "next/link";
import { ArrowUpRight, ClipboardCheck, Globe2, Leaf, RefreshCw, UsersRound } from "lucide-react";
import type { DashboardData } from "@/lib/dashboard-data";

function Metric({ label, value, icon: Icon }: { label: string; value: number; icon: typeof Globe2 }) {
  return <div className="min-w-0 border-b border-[#ebe8f0] p-5 last:border-0 sm:border-b-0 sm:border-r"><div className="flex items-start justify-between"><div><p className="text-sm font-medium text-[#6f6b80]">{label}</p><p className="mt-1 text-3xl font-bold tracking-tight text-[#512b91]">{value.toLocaleString()}</p></div><span className="rounded-full bg-[#f0eafb] p-2.5 text-[#512b91]"><Icon size={21}/></span></div></div>;
}

export function Dashboard({ data }: { data: DashboardData }) {
  const maxRank = Math.max(1, ...data.rankings.map((r) => r.count));
  return <div className="fade-up mx-auto max-w-[1240px]">
    <header className="flex flex-wrap items-end justify-between gap-4"><div><h1 className="text-3xl font-bold tracking-[-.03em]">Overview</h1><p className="mt-1 text-sm text-[#6f6b80]">Live operational data from the Eggplant detector.</p></div><p className="flex items-center gap-2 rounded-xl border border-[#ded9e8] bg-white px-3 py-2 text-sm text-[#5e596e]"><RefreshCw size={16}/>Refreshes on navigation</p></header>
    <section className="surface mt-6 grid sm:grid-cols-2 xl:grid-cols-4">
      <Metric label="Quarantined reports" value={data.pendingReports} icon={ClipboardCheck}/><Metric label="Shared scans" value={data.sharedScans} icon={Globe2}/><Metric label="Contributing installs" value={data.installations} icon={UsersRound}/><Metric label="Open requests" value={data.openRequests} icon={Leaf}/>
    </section>
    <section className="mt-5 grid gap-3 sm:grid-cols-3"><Ops label="Cloud writes" value={data.cloudWritesEnabled ? "Enabled" : "Paused"}/><Ops label="Private photo storage" value={formatBytes(data.storageBytes)}/><Ops label="Last mobile sync" value={data.lastInstallationSeenAt ? new Date(data.lastInstallationSeenAt).toLocaleString() : "No sync yet"}/></section>
    <div className="mt-5 grid gap-5 xl:grid-cols-[1fr_1.15fr]">
      <section className="surface p-5"><div className="flex items-center justify-between"><h2 className="text-lg font-bold">Disease rankings</h2><Link href="/global-scans" className="text-sm font-semibold text-[#512b91]">View all</Link></div>
        {data.rankings.length === 0 ? <Empty text="Rankings will appear after users share confirmed scans."/> : <ol className="mt-4 divide-y divide-[#ece9f1]">{data.rankings.map((rank, index) => <li key={rank.diseaseId} className="grid grid-cols-[28px_1fr_auto] items-center gap-3 py-3"><span className="font-mono text-sm font-bold text-[#512b91]">{index + 1}</span><div><p className="text-sm font-semibold">{rank.name}</p><div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-[#eceaf0]"><div className="h-full rounded-full bg-[#319548]" style={{width:`${rank.count / maxRank * 100}%`}}/></div></div><span className="font-mono text-sm text-[#625e72]">{rank.count}</span></li>)}</ol>}
      </section>
      <section className="surface p-5"><div className="flex items-center justify-between"><h2 className="text-lg font-bold">Recent shared scans</h2><Link href="/global-scans" className="text-sm font-semibold text-[#512b91]">Manage</Link></div>
        {data.recent.length === 0 ? <Empty text="No community photos have been shared yet."/> : <div className="mt-4 overflow-x-auto"><table className="w-full text-left text-sm"><thead className="text-xs uppercase tracking-wide text-[#797487]"><tr><th className="pb-3">Disease</th><th className="pb-3">Confidence</th><th className="pb-3">Status</th><th className="pb-3"><span className="sr-only">Open</span></th></tr></thead><tbody className="divide-y divide-[#ece9f1]">{data.recent.map((scan)=><tr key={scan.id}><td className="py-3 font-semibold">{scan.disease}</td><td className="py-3 font-mono text-[#27883d]">{scan.confidence}%</td><td className="py-3"><span className={`rounded-full px-2 py-1 text-xs font-semibold ${scan.status === "published" ? "bg-[#e9f6eb] text-[#247936]" : "bg-[#fff0dd] text-[#995a06]"}`}>{scan.status}</span></td><td><Link href={`/global-scans/${scan.id}`} aria-label={`Review ${scan.disease}`} className="text-[#512b91]"><ArrowUpRight size={17}/></Link></td></tr>)}</tbody></table></div>}
      </section>
    </div>
  </div>;
}

function Empty({ text }: { text: string }) { return <div className="mt-4 rounded-xl border border-dashed border-[#d9d3e4] bg-[#faf9fc] p-8 text-center text-sm text-[#716c80]">{text}</div>; }
function Ops({label,value}:{label:string;value:string}){return <div className="surface p-4"><p className="text-xs font-semibold uppercase tracking-wide text-[#777286]">{label}</p><p className="mt-1 font-mono text-sm font-semibold text-[#512b91]">{value}</p></div>}
function formatBytes(bytes:number){if(bytes<1024)return `${bytes} B`;if(bytes<1024*1024)return `${(bytes/1024).toFixed(1)} KB`;return `${(bytes/1024/1024).toFixed(1)} MB`}
