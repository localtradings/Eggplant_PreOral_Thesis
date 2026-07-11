import Image from "next/image";
import Link from "next/link";
import { BookOpen, ClipboardList, Globe2, LayoutDashboard, LogOut, Settings, ShieldCheck } from "lucide-react";

const items = [
  ["Overview", "/overview", LayoutDashboard],
  ["Global scans", "/global-scans", Globe2],
  ["Disease requests", "/disease-requests", ClipboardList],
  ["Disease catalog", "/disease-catalog", BookOpen],
  ["Audit & settings", "/settings", Settings],
] as const;

export function AdminShell({ children, active, role }: { children: React.ReactNode; active: string; role: string }) {
  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[268px_1fr]">
      <aside className="border-b border-[#e4e1eb] bg-white px-5 py-5 lg:relative lg:sticky lg:top-0 lg:h-screen lg:border-b-0 lg:border-r">
        <div className="flex items-center gap-3 px-2">
          <Image src="/eggplant-logo.svg" alt="" width={46} height={46} priority />
          <div><p className="text-xl font-bold tracking-tight">Eggplant</p><p className="text-sm font-semibold text-[#24863c]">Disease Detector</p></div>
        </div>
        <nav className="mt-8 grid grid-cols-2 gap-2 sm:grid-cols-5 lg:grid-cols-1" aria-label="Admin navigation">
          {items.map(([label, href, Icon]) => {
            const selected = active === href;
            return <Link key={href} href={href} aria-current={selected ? "page" : undefined} className={`focus-ring flex min-h-11 items-center gap-3 rounded-xl px-3.5 text-sm font-semibold transition-all ${selected ? "bg-[#512b91] text-white shadow-[0_8px_22px_rgba(81,43,145,.2)]" : "text-[#514e65] hover:bg-[#f1eff7] hover:text-[#2b2341]"}`}><Icon size={19} strokeWidth={1.9}/><span>{label}</span></Link>;
          })}
        </nav>
        <div className="mt-6 rounded-2xl border border-[#e4e1eb] bg-[#fbfbfd] p-4 lg:absolute lg:bottom-5 lg:left-5 lg:right-5">
          <div className="flex items-center gap-2 text-sm font-semibold"><ShieldCheck size={17} className="text-[#2f963f]"/>Private admin</div>
          <p className="mt-1 text-xs text-[#777389]">Role: {role}. All moderation actions are audited.</p>
          <form action="/auth/signout" method="post" className="mt-3"><button className="focus-ring flex items-center gap-2 text-xs font-semibold text-[#512b91]"><LogOut size={14}/>Sign out</button></form>
        </div>
      </aside>
      <main className="min-w-0 p-5 sm:p-7 lg:p-9">{children}</main>
    </div>
  );
}
