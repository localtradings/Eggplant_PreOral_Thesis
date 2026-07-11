export const MAX_UPLOAD_BYTES = 8_388_608;
export const MAX_JSON_BYTES = 65_536;
export const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
export const SHA256_PATTERN = /^[a-f0-9]{64}$/i;

export type ShareSource = "live" | "capture";

export type ShareIntent = {
  clientScanId: string;
  diseaseId: string;
  confidence: number;
  source: ShareSource;
  modelVersion: string;
  contentLength: number;
  sha256: string;
};

export type ShareCompletion = {
  clientScanId: string;
  diseaseId: string;
  confidence: number;
  source: ShareSource;
  modelVersion: string;
  path: string;
};

export type DiseaseRequestInput = {
  clientRequestId: string;
  requestedName?: string;
  notes?: string;
  modelVersion: string;
  rightsConsent: boolean;
  trainingConsent: false;
  photos: Array<{
    contentLength: number;
    sha256: string;
    source: "live" | "capture";
  }>;
};

export type ContentReportInput = {
  reason: string;
  details?: string;
};

export type SharingConsentInput = {
  enabled: boolean;
  consentVersion: 1 | null;
};

export type FeedCursor = {
  publishedAt: string;
  id: string;
};

type ValidationResult<T> =
  | { ok: true; value: T }
  | { ok: false };

const REPORT_REASONS = new Set([
  "incorrect_result",
  "not_eggplant",
  "inappropriate",
  "duplicate",
  "other",
]);

export function boundedText(
  value: unknown,
  minimum: number,
  maximum: number,
) {
  if (typeof value !== "string") return null;
  const normalized = value.trim();
  return normalized.length >= minimum && normalized.length <= maximum
    ? normalized
    : null;
}

export function validateShareIntent(
  value: unknown,
  maximumBytes = MAX_UPLOAD_BYTES,
): ValidationResult<ShareIntent> {
  if (!isRecord(value)) return { ok: false };
  const modelVersion = boundedText(value.modelVersion, 1, 100);
  const diseaseId = boundedText(value.diseaseId, 1, 100);
  const confidence = value.confidence;
  const contentLength = value.contentLength;
  const source = value.source;
  const sha256 = value.sha256;
  if (
    typeof value.clientScanId !== "string" ||
    !UUID_PATTERN.test(value.clientScanId) ||
    !diseaseId ||
    !modelVersion ||
    (source !== "live" && source !== "capture") ||
    typeof confidence !== "number" ||
    !Number.isFinite(confidence) ||
    confidence < 0.5 ||
    confidence > 1 ||
    typeof contentLength !== "number" ||
    !Number.isSafeInteger(contentLength) ||
    contentLength < 1 ||
    contentLength > maximumBytes ||
    typeof sha256 !== "string" ||
    !SHA256_PATTERN.test(sha256)
  ) {
    return { ok: false };
  }
  return {
    ok: true,
    value: {
      clientScanId: value.clientScanId,
      diseaseId,
      confidence,
      source,
      modelVersion,
      contentLength,
      sha256: sha256.toLowerCase(),
    },
  };
}

export function validateShareCompletion(
  value: unknown,
  ownerId: string,
): ValidationResult<ShareCompletion & { expectedSha256: string }> {
  if (!isRecord(value)) return { ok: false };
  const modelVersion = boundedText(value.modelVersion, 1, 100);
  const diseaseId = boundedText(value.diseaseId, 1, 100);
  const confidence = value.confidence;
  const source = value.source;
  if (
    typeof value.clientScanId !== "string" ||
    !UUID_PATTERN.test(value.clientScanId) ||
    !diseaseId ||
    !modelVersion ||
    (source !== "live" && source !== "capture") ||
    typeof confidence !== "number" ||
    !Number.isFinite(confidence) ||
    confidence < 0.5 ||
    confidence > 1 ||
    typeof value.path !== "string"
  ) {
    return { ok: false };
  }
  const prefix = `global/${ownerId}/${value.clientScanId}/`;
  if (!value.path.startsWith(prefix)) return { ok: false };
  const filename = value.path.slice(prefix.length);
  const expectedSha256 = filename.endsWith(".jpg")
    ? filename.slice(0, -4)
    : "";
  if (!SHA256_PATTERN.test(expectedSha256)) return { ok: false };
  return {
    ok: true,
    value: {
      clientScanId: value.clientScanId,
      diseaseId,
      confidence,
      source,
      modelVersion,
      path: value.path,
      expectedSha256: expectedSha256.toLowerCase(),
    },
  };
}

export function globalSharePath(
  ownerId: string,
  clientScanId: string,
  sha256: string,
) {
  return `global/${ownerId}/${clientScanId}/${sha256.toLowerCase()}.jpg`;
}

export function validateDiseaseRequest(
  value: unknown,
  maximumBytes = MAX_UPLOAD_BYTES,
): ValidationResult<DiseaseRequestInput> {
  if (!isRecord(value)) return { ok: false };
  const requestedName =
    value.requestedName == null || value.requestedName === ""
      ? undefined
      : boundedText(value.requestedName, 2, 120);
  const modelVersion = boundedText(value.modelVersion, 1, 100);
  const normalizedNotes =
    value.notes == null || value.notes === ""
      ? undefined
      : boundedText(value.notes, 1, 200);
  const photos = Array.isArray(value.photos) ? value.photos : [];
  const validPhotos =
    photos.length >= 1 &&
    photos.length <= 3 &&
    photos.every(
      (photo) =>
        isRecord(photo) &&
        typeof photo.contentLength === "number" &&
        Number.isSafeInteger(photo.contentLength) &&
        photo.contentLength >= 1 &&
        photo.contentLength <= maximumBytes &&
        typeof photo.sha256 === "string" &&
        SHA256_PATTERN.test(photo.sha256) &&
        (photo.source === "live" || photo.source === "capture"),
    );
  if (
    typeof value.clientRequestId !== "string" ||
    !UUID_PATTERN.test(value.clientRequestId) ||
    !modelVersion ||
    value.rightsConsent !== true ||
    value.trainingConsent !== false ||
    !validPhotos ||
    (value.notes != null && value.notes !== "" && !normalizedNotes)
  ) {
    return { ok: false };
  }
  return {
    ok: true,
    value: {
      clientRequestId: value.clientRequestId,
      ...(requestedName ? { requestedName } : {}),
      notes: normalizedNotes ?? undefined,
      modelVersion,
      rightsConsent: true,
      trainingConsent: false,
      photos: photos.map((photo) => ({
        contentLength: Number((photo as Record<string, unknown>).contentLength),
        sha256: String((photo as Record<string, unknown>).sha256).toLowerCase(),
        source: (photo as Record<string, unknown>).source as "live" | "capture",
      })),
    },
  };
}

export function diseaseRequestPhotoPath(
  ownerId: string,
  requestId: string,
  position: number,
  sha256: string,
) {
  return `requests/${ownerId}/${requestId}/${position}-${sha256.toLowerCase()}.jpg`;
}

export function sha256FromDiseaseRequestPath(path: string) {
  const filename = path.split("/").at(-1) ?? "";
  const separator = filename.indexOf("-");
  const sha256 = filename.endsWith(".jpg")
    ? filename.slice(separator + 1, -4)
    : "";
  return separator > 0 && SHA256_PATTERN.test(sha256)
    ? sha256.toLowerCase()
    : null;
}

export function validateContentReport(
  value: unknown,
): ValidationResult<{ reason: string; details: string | null }> {
  if (!isRecord(value) || typeof value.reason !== "string") {
    return { ok: false };
  }
  const details =
    value.details == null || value.details === ""
      ? null
      : boundedText(value.details, 1, 1_000);
  if (!REPORT_REASONS.has(value.reason) || (value.details != null && value.details !== "" && !details)) {
    return { ok: false };
  }
  return { ok: true, value: { reason: value.reason, details } };
}

export function validateSharingConsent(
  value: unknown,
): ValidationResult<SharingConsentInput> {
  if (!isRecord(value) || typeof value.enabled !== "boolean") {
    return { ok: false };
  }
  if (value.enabled && value.consentVersion !== 1) return { ok: false };
  if (!value.enabled && value.consentVersion != null) return { ok: false };
  return {
    ok: true,
    value: {
      enabled: value.enabled,
      consentVersion: value.enabled ? 1 : null,
    },
  };
}

export function normalizePageLimit(value: string | null, fallback = 20) {
  if (value == null || value.trim() === "") return fallback;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) ? Math.min(30, Math.max(1, parsed)) : fallback;
}

export function encodeFeedCursor(cursor: FeedCursor) {
  return Buffer.from(JSON.stringify(cursor), "utf8").toString("base64url");
}

export function decodeFeedCursor(value: string | null): FeedCursor | null {
  if (!value || value.length > 512) return null;
  try {
    const parsed = JSON.parse(
      Buffer.from(value, "base64url").toString("utf8"),
    ) as unknown;
    if (!isRecord(parsed) || typeof parsed.publishedAt !== "string" || typeof parsed.id !== "string") {
      return null;
    }
    const canonicalDate = new Date(parsed.publishedAt).toISOString();
    if (!UUID_PATTERN.test(parsed.id)) return null;
    return { publishedAt: canonicalDate, id: parsed.id };
  } catch {
    return null;
  }
}

export function isJpeg(bytes: Uint8Array) {
  return (
    bytes.length >= 4 &&
    bytes[0] === 0xff &&
    bytes[1] === 0xd8 &&
    bytes[2] === 0xff &&
    bytes.at(-2) === 0xff &&
    bytes.at(-1) === 0xd9
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
