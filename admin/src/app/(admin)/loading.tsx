export default function AdminLoading() {
  return (
    <main aria-busy="true" aria-label="Loading admin dashboard" className="mx-auto max-w-[1240px] p-5 sm:p-7 lg:p-9">
      <div className="pulse-soft h-9 w-56 rounded-xl bg-[#e8e4ef]" />
      <div className="mt-3 h-4 w-80 max-w-full rounded bg-[#eeebf3]" />
      <div className="mt-7 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }, (_, index) => (
          <div className="surface h-28 pulse-soft" key={index} />
        ))}
      </div>
      <div className="mt-5 grid gap-5 xl:grid-cols-2">
        <div className="surface h-72 pulse-soft" />
        <div className="surface h-72 pulse-soft" />
      </div>
      <span className="sr-only">Loading live production data…</span>
    </main>
  );
}
