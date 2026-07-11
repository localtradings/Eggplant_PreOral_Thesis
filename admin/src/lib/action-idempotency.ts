import { createHash } from "node:crypto";

import { UUID_PATTERN } from "@/lib/mobile-validation";

/**
 * Server actions receive the same key when a browser retries a submitted form.
 * The database binds that key to the resource and canonical payload hash.
 */
export function requireIdempotencyKey(formData: FormData) {
  const key = String(formData.get("idempotency_key") ?? "");
  if (!UUID_PATTERN.test(key)) {
    throw new Error("This action is missing its retry-protection key. Refresh and try again.");
  }
  return key;
}

/** The input shape is constructed in a fixed order at each call site. */
export function hashActionPayload(payload: unknown) {
  return createHash("sha256")
    .update(JSON.stringify(payload), "utf8")
    .digest("hex");
}
