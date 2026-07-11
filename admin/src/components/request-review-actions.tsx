"use client";

import { Check, X } from "lucide-react";
import { FormSubmitButton } from "@/components/form-submit-button";

type ServerAction = (formData: FormData) => void | Promise<void>;

type RequestReviewActionsProps = {
  action: ServerAction;
  requestId: string;
  currentStatus: string;
  plannedIdempotencyKey: string;
  unsupportedIdempotencyKey: string;
  compact?: boolean;
};

/** Quick moderation controls share the same server action as the detail form. */
export function RequestReviewActions({ action, requestId, currentStatus, plannedIdempotencyKey, unsupportedIdempotencyKey, compact = false }: RequestReviewActionsProps) {
  const handled = currentStatus === "planned" || currentStatus === "not_supported" || currentStatus === "closed";

  return (
    <div className={`flex items-center ${compact ? "gap-1" : "gap-2"}`} aria-label="Quick review actions">
      <form action={action}>
        <input type="hidden" name="id" value={requestId} />
        <input type="hidden" name="status" value="planned" />
        <input type="hidden" name="note" value="" />
        <input type="hidden" name="idempotency_key" value={plannedIdempotencyKey} />
        <input type="hidden" name="return_to" value="list" />
        <FormSubmitButton
          label="Mark planned"
          pendingLabel="Updating"
          iconOnly={compact}
          icon={<Check size={16} aria-hidden="true" />}
          className={`min-w-11 border border-[#70b77b] px-3 text-[#247936] ${handled && currentStatus !== "planned" ? "opacity-50" : ""}`}
        />
      </form>
      <form action={action}>
        <input type="hidden" name="id" value={requestId} />
        <input type="hidden" name="status" value="not_supported" />
        <input type="hidden" name="note" value="" />
        <input type="hidden" name="idempotency_key" value={unsupportedIdempotencyKey} />
        <input type="hidden" name="return_to" value="list" />
        <FormSubmitButton
          label="Mark not supported"
          pendingLabel="Updating"
          iconOnly={compact}
          icon={<X size={16} aria-hidden="true" />}
          className={`min-w-11 border border-[#e38b96] px-3 text-[#b12d40] ${handled && currentStatus !== "not_supported" ? "opacity-50" : ""}`}
        />
      </form>
    </div>
  );
}
