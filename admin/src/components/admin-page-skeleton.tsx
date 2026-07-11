type SkeletonKind = "overview" | "requests" | "catalog" | "scans" | "detail" | "settings";

function Bar({ className = "" }: { className?: string }) {
  return <div className={`pulse-soft rounded-lg bg-[#e9e5f0] ${className}`} />;
}

function Workspace({ kind }: { kind: SkeletonKind }) {
  if (kind === "overview") {
    return <><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{Array.from({ length: 4 }, (_, index) => <div className="surface h-28 p-5" key={index}><Bar className="h-3 w-24" /><Bar className="mt-3 h-8 w-16" /></div>)}</div><div className="mt-5 grid gap-5 xl:grid-cols-2"><div className="surface h-72 p-5"><Bar className="h-5 w-40" /><div className="mt-6 grid gap-5">{Array.from({ length: 4 }, (_, index) => <Bar className="h-7 w-full" key={index} />)}</div></div><div className="surface h-72 p-5"><Bar className="h-5 w-44" /><div className="mt-6 grid gap-3">{Array.from({ length: 5 }, (_, index) => <Bar className="h-10 w-full" key={index} />)}</div></div></div></>;
  }
  if (kind === "detail") {
    return <div className="mt-6 grid gap-5 xl:grid-cols-[1.45fr_.8fr]"><div className="surface aspect-[4/3]" /><div className="grid gap-5"><div className="surface h-60 p-5"><Bar className="h-7 w-2/3" /><div className="mt-6 grid gap-4">{Array.from({ length: 4 }, (_, index) => <Bar className="h-5 w-full" key={index} />)}</div></div><div className="surface h-52 p-5"><Bar className="h-6 w-36" /><div className="mt-5 grid gap-3">{Array.from({ length: 3 }, (_, index) => <Bar className="h-11 w-full" key={index} />)}</div></div></div></div>;
  }
  if (kind === "settings") {
    return <div className="mt-6 grid gap-5"><div className="surface h-52 p-6"><Bar className="h-6 w-48" /><Bar className="mt-4 h-4 w-full" /><Bar className="mt-3 h-4 w-4/5" /><Bar className="mt-6 h-11 w-44" /></div><div className="surface h-56 p-6"><Bar className="h-6 w-40" />{Array.from({ length: 5 }, (_, index) => <Bar className="mt-5 h-7 w-full" key={index} />)}</div></div>;
  }
  const cards = kind === "catalog" ? 6 : kind === "scans" ? 6 : 5;
  return <div className={`mt-6 grid gap-4 ${kind === "catalog" ? "lg:grid-cols-2" : kind === "scans" ? "sm:grid-cols-2 xl:grid-cols-3" : ""}`}>{Array.from({ length: cards }, (_, index) => <div className={`surface overflow-hidden ${kind === "scans" ? "" : "p-5"}`} key={index}>{kind === "scans" && <Bar className="aspect-[16/10] w-full rounded-none" />}{kind === "requests" && <div className="flex gap-4"><Bar className="h-20 w-24 shrink-0" /><div className="min-w-0 flex-1"><Bar className="h-5 w-2/3" /><Bar className="mt-3 h-4 w-full" /><Bar className="mt-2 h-4 w-4/5" /></div></div>}{kind === "catalog" && <><Bar className="h-4 w-24" /><Bar className="mt-3 h-6 w-1/2" /><Bar className="mt-5 h-4 w-full" /><Bar className="mt-2 h-4 w-4/5" /></>}{kind === "scans" && <div className="p-4"><Bar className="h-5 w-3/5" /><Bar className="mt-4 h-4 w-full" /></div>}</div>)}</div>;
}

/** Route loaders mirror the actual shell instead of flashing a mobile-only layout on desktop. */
export function AdminPageSkeleton({ kind = "overview" }: { kind?: SkeletonKind }) {
  return <div aria-busy="true" aria-label="Loading admin workspace" className="min-h-screen lg:grid lg:grid-cols-[268px_1fr]">
    <aside className="border-b border-[#e4e1eb] bg-white px-5 py-5 lg:h-screen lg:border-b-0 lg:border-r"><div className="flex items-center gap-3"><Bar className="h-11 w-11 rounded-2xl" /><div><Bar className="h-5 w-24" /><Bar className="mt-2 h-3 w-20" /></div></div><div className="mt-8 grid grid-cols-2 gap-2 sm:grid-cols-5 lg:grid-cols-1">{Array.from({ length: 5 }, (_, index) => <Bar className="h-11 w-full" key={index} />)}</div><div className="mt-6 hidden rounded-2xl border border-[#e4e1eb] p-4 lg:block"><Bar className="h-4 w-28" /><Bar className="mt-3 h-3 w-full" /></div></aside>
    <main className="min-w-0 p-5 sm:p-7 lg:p-9"><div className="mx-auto max-w-[1240px]"><Bar className="h-9 w-56" /><Bar className="mt-3 h-4 w-80 max-w-full" /><Workspace kind={kind} /></div><span className="sr-only">Loading live production data…</span></main>
  </div>;
}
