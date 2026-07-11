"use client";

import { Check, LoaderCircle } from "lucide-react";
import { useFormStatus } from "react-dom";
import type { ReactNode } from "react";

type FormSubmitButtonProps = {
  label: string;
  pendingLabel: string;
  className?: string;
  completeLabel?: string;
  iconOnly?: boolean;
  icon?: ReactNode;
};

/**
 * Keeps server-action controls honest while a request is pending. A completed
 * mutation redirects/revalidates the route, so success is announced by the
 * destination page instead of optimistically claiming success here.
 */
export function FormSubmitButton({
  label,
  pendingLabel,
  className = "",
  completeLabel,
  iconOnly = false,
  icon,
}: FormSubmitButtonProps) {
  const { pending } = useFormStatus();

  return (
    <button
      type="submit"
      disabled={pending}
      aria-disabled={pending}
      aria-busy={pending}
      className={`focus-ring action-button inline-flex min-h-11 items-center justify-center gap-2 rounded-xl font-semibold disabled:cursor-wait disabled:opacity-70 ${className}`}
    >
      {pending ? <LoaderCircle className="animate-spin" size={17} aria-hidden="true" /> : icon ?? (completeLabel ? <Check size={17} aria-hidden="true" /> : null)}
      <span className={iconOnly ? "sr-only" : undefined}>{pending ? pendingLabel : completeLabel ?? label}</span>
      <span className="sr-only" aria-live="polite">{pending ? `${pendingLabel}. Please wait.` : ""}</span>
    </button>
  );
}
