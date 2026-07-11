import Link from "next/link";

export default function NotFound() {
  return (
    <main className="grid min-h-screen place-items-center p-5">
      <section className="surface max-w-md p-8 text-center">
        <p className="font-mono text-sm font-bold text-[#512b91]">404</p>
        <h1 className="mt-2 text-2xl font-bold">Admin record not found</h1>
        <p className="mt-2 text-sm leading-6 text-[#686376]">
          The record may have been removed or the link may be outdated.
        </p>
        <Link href="/overview" className="focus-ring mt-5 inline-flex h-11 items-center rounded-xl bg-[#512b91] px-4 font-semibold text-white">
          Return to overview
        </Link>
      </section>
    </main>
  );
}
