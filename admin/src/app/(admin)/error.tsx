"use client";

import { AlertTriangle, RotateCcw } from "lucide-react";
import { useEffect } from "react";

export default function AdminError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("Admin route failed", error.digest ?? "no-digest");
  }, [error]);

  return (
    <main className="grid min-h-[70vh] place-items-center p-5">
      <section role="alert" className="surface max-w-lg p-7 text-center">
        <AlertTriangle className="mx-auto text-[#b33143]" size={34} />
        <h1 className="mt-3 text-xl font-bold">Production data is unavailable</h1>
        <p className="mt-2 text-sm leading-6 text-[#686376]">
          No placeholder values were substituted. Retry after checking the cloud connection.
        </p>
        <button
          type="button"
          onClick={reset}
          className="focus-ring mt-5 inline-flex h-11 items-center gap-2 rounded-xl bg-[#512b91] px-4 font-semibold text-white"
        >
          <RotateCcw size={17} />
          Retry
        </button>
      </section>
    </main>
  );
}
