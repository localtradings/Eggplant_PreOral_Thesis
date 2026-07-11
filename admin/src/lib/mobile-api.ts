import "server-only";
import { createHash } from "node:crypto";
import { NextResponse } from "next/server";
import { cloudWritesEnabled, verifyMobileUser } from "@/lib/auth";
import { getAdminClient } from "@/lib/supabase/admin";
import {
  isJpeg,
  MAX_JSON_BYTES,
  MAX_UPLOAD_BYTES,
} from "@/lib/mobile-validation";
import { requestRateSubject } from "@/lib/request-security";
import { validateJpegBytes } from "@/lib/jpeg-validation";

export {
  boundedText,
  MAX_UPLOAD_BYTES,
  SHA256_PATTERN,
  UUID_PATTERN,
} from "@/lib/mobile-validation";

export function apiError(message: string, status: number, code: string) {
  return NextResponse.json({ error: { code, message } }, { status });
}

export async function authorizeMobile(request: Request, write = false) {
  const user = await verifyMobileUser(request);
  if (!user) return { response: apiError("Authentication required.", 401, "unauthorized") } as const;
  if (write && !(await cloudWritesEnabled())) return { response: apiError("Cloud writes are temporarily paused.", 503, "writes_paused") } as const;
  const { error } = await getAdminClient()
    .from("installations")
    .upsert(
      { owner_id: user.id, last_seen_at: new Date().toISOString() },
      { onConflict: "owner_id" },
    );
  if (error) {
    return {
      response: apiError(
        "Cloud identity is temporarily unavailable.",
        503,
        "installation_unavailable",
      ),
    } as const;
  }
  return { user } as const;
}

export async function parseJson<T>(request: Request): Promise<T | null> {
  const contentType = request.headers.get("content-type")?.toLowerCase() ?? "";
  const contentLengthHeader = request.headers.get("content-length");
  const contentLength = contentLengthHeader && /^\d+$/.test(contentLengthHeader)
    ? Number(contentLengthHeader)
    : Number.NaN;
  if (
    !contentType.startsWith("application/json") ||
    !Number.isFinite(contentLength) ||
    contentLength < 0 ||
    contentLength > MAX_JSON_BYTES
  ) {
    return null;
  }
  try {
    const reader = request.body?.getReader();
    if (!reader) return null;
    const chunks: Uint8Array[] = [];
    let total = 0;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > MAX_JSON_BYTES) {
        await reader.cancel();
        return null;
      }
      chunks.push(value);
    }
    if (total !== contentLength) return null;
    const bytes = new Uint8Array(total);
    let offset = 0;
    for (const chunk of chunks) {
      bytes.set(chunk, offset);
      offset += chunk.byteLength;
    }
    return JSON.parse(new TextDecoder().decode(bytes)) as T;
  } catch {
    return null;
  }
}

export function mobileRateSubject(request: Request) {
  return requestRateSubject(request, process.env.CRON_SECRET);
}

export async function verifyStoredJpeg(path: string, expectedSha256: string) {
  const bucket = getAdminClient().storage.from("eggplant-scans");
  const { data: info, error: infoError } = await bucket.info(path);
  if (
    infoError ||
    !info ||
    typeof info.size !== "number" ||
    info.size < 1 ||
    info.size > MAX_UPLOAD_BYTES ||
    info.contentType?.toLowerCase() !== "image/jpeg"
  ) {
    return false;
  }
  const { data: blob, error: downloadError } = await bucket.download(path);
  if (downloadError || !blob || blob.size !== info.size) return false;
  const bytes = new Uint8Array(await blob.arrayBuffer());
  if (!isJpeg(bytes)) return false;
  if (createHash("sha256").update(bytes).digest("hex") !== expectedSha256) {
    return false;
  }
  return (await validateJpegBytes(bytes)) != null;
}
