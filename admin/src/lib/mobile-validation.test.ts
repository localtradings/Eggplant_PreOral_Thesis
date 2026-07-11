import { describe, expect, it } from "vitest";
import {
  decodeFeedCursor,
  diseaseRequestPhotoPath,
  encodeFeedCursor,
  globalSharePath,
  isJpeg,
  MAX_UPLOAD_BYTES,
  normalizePageLimit,
  sha256FromDiseaseRequestPath,
  validateContentReport,
  validateDiseaseRequest,
  validateShareCompletion,
  validateShareIntent,
  validateSharingConsent,
} from "./mobile-validation";

const USER_ID = "01890f3d-00d8-7b65-9a77-a79bfe3f8482";
const SCAN_ID = "01890f3d-00d8-7b65-9a77-a79bfe3f8483";
const REQUEST_ID = "01890f3d-00d8-7b65-9a77-a79bfe3f8484";
const SHA256 = "a".repeat(64);

describe("global-share API validation", () => {
  const validIntent = {
    clientScanId: SCAN_ID,
    diseaseId: "leaf-spot",
    confidence: 0.5,
    source: "capture",
    modelVersion: "eggplant-ncnn-v2",
    contentLength: 1_024,
    sha256: SHA256,
  };

  it("accepts the documented lower confidence boundary and canonical metadata", () => {
    expect(validateShareIntent(validIntent)).toEqual({
      ok: true,
      value: validIntent,
    });
  });

  it.each([
    ["gallery sources", { ...validIntent, source: "gallery" }],
    ["sub-threshold confidence", { ...validIntent, confidence: 0.4999 }],
    ["non-finite confidence", { ...validIntent, confidence: Number.NaN }],
    ["oversized uploads", { ...validIntent, contentLength: MAX_UPLOAD_BYTES + 1 }],
    ["fractional byte lengths", { ...validIntent, contentLength: 10.5 }],
    ["malformed hashes", { ...validIntent, sha256: "not-a-hash" }],
  ])("rejects %s", (_, candidate) => {
    expect(validateShareIntent(candidate)).toEqual({ ok: false });
  });

  it("binds completion to the authenticated owner, local scan, and intent hash", () => {
    const path = globalSharePath(USER_ID, SCAN_ID, SHA256);
    const result = validateShareCompletion(
      { ...validIntent, path },
      USER_ID,
    );
    expect(result).toEqual({
      ok: true,
      value: {
        clientScanId: SCAN_ID,
        diseaseId: "leaf-spot",
        confidence: 0.5,
        source: "capture",
        modelVersion: "eggplant-ncnn-v2",
        path,
        expectedSha256: SHA256,
      },
    });
    expect(
      validateShareCompletion(
        { ...validIntent, path },
        "01890f3d-00d8-7b65-9a77-a79bfe3f8499",
      ),
    ).toEqual({ ok: false });
  });

  it("rejects a completion path that does not contain a complete SHA-256 name", () => {
    expect(
      validateShareCompletion(
        {
          ...validIntent,
          path: `global/${USER_ID}/${SCAN_ID}/photo.jpg`,
        },
        USER_ID,
      ),
    ).toEqual({ ok: false });
  });
});

describe("missing-disease API validation", () => {
  const validRequest = {
    clientRequestId: REQUEST_ID,
    requestedName: "Unknown stem lesion",
    notes: "Observed after several rainy days.",
    modelVersion: "eggplant-ncnn-v2",
    rightsConsent: true,
    trainingConsent: false,
    photos: [{ contentLength: 2_048, sha256: SHA256, source: "capture" }],
  };

  it("accepts one to three private photos with explicit rights and no training consent", () => {
    expect(validateDiseaseRequest(validRequest).ok).toBe(true);
    expect(
      validateDiseaseRequest({
        ...validRequest,
        photos: [
          ...validRequest.photos,
          { contentLength: 4_096, sha256: "b".repeat(64), source: "live" },
          { contentLength: 8_192, sha256: "c".repeat(64), source: "capture" },
        ],
      }).ok,
    ).toBe(true);
  });

  it.each([
    ["no photo", { ...validRequest, photos: [] }],
    ["four photos", { ...validRequest, photos: Array(4).fill(validRequest.photos[0]) }],
    ["missing rights", { ...validRequest, rightsConsent: false }],
    ["training consent", { ...validRequest, trainingConsent: true }],
    ["oversized notes", { ...validRequest, notes: "x".repeat(201) }],
    ["gallery photo source", { ...validRequest, photos: [{ contentLength: 2_048, sha256: SHA256, source: "gallery" }] }],
  ])("rejects %s", (_, candidate) => {
    expect(validateDiseaseRequest(candidate)).toEqual({ ok: false });
  });

  it("accepts an omitted disease name and exactly 200 optional note characters", () => {
    expect(validateDiseaseRequest({
      ...validRequest,
      requestedName: undefined,
      notes: "x".repeat(200),
    })).toEqual({
      ok: true,
      value: {
        clientRequestId: REQUEST_ID,
        notes: "x".repeat(200),
        modelVersion: "eggplant-ncnn-v2",
        rightsConsent: true,
        trainingConsent: false,
        photos: [{ contentLength: 2_048, sha256: SHA256, source: "capture" }],
      },
    });
  });

  it("derives and recovers the expected digest from owner-partitioned paths", () => {
    const path = diseaseRequestPhotoPath(
      USER_ID,
      REQUEST_ID,
      0,
      SHA256.toUpperCase(),
    );
    expect(path).toBe(`requests/${USER_ID}/${REQUEST_ID}/0-${SHA256}.jpg`);
    expect(sha256FromDiseaseRequestPath(path)).toBe(SHA256);
    expect(sha256FromDiseaseRequestPath(`${path}.exe`)).toBeNull();
  });
});

describe("report and cursor API validation", () => {
  it("requires the current consent version only when anonymous sharing is enabled", () => {
    expect(validateSharingConsent({ enabled: true, consentVersion: 1 })).toEqual({
      ok: true,
      value: { enabled: true, consentVersion: 1 },
    });
    expect(validateSharingConsent({ enabled: false })).toEqual({
      ok: true,
      value: { enabled: false, consentVersion: null },
    });
    expect(validateSharingConsent({ enabled: true, consentVersion: 2 })).toEqual({ ok: false });
    expect(validateSharingConsent({ enabled: false, consentVersion: 1 })).toEqual({ ok: false });
  });

  it("accepts only the documented report reasons and bounded details", () => {
    expect(
      validateContentReport({ reason: "incorrect_result", details: "Wrong disease" }),
    ).toEqual({
      ok: true,
      value: { reason: "incorrect_result", details: "Wrong disease" },
    });
    expect(validateContentReport({ reason: "arbitrary" })).toEqual({ ok: false });
    expect(
      validateContentReport({ reason: "other", details: "x".repeat(1_001) }),
    ).toEqual({ ok: false });
  });

  it("round-trips a stable timestamp-plus-ID cursor", () => {
    const cursor = {
      publishedAt: "2026-07-10T08:30:15.123Z",
      id: SCAN_ID,
    };
    expect(decodeFeedCursor(encodeFeedCursor(cursor))).toEqual(cursor);
    expect(decodeFeedCursor("not-base64-json")).toBeNull();
    expect(
      decodeFeedCursor(
        Buffer.from(JSON.stringify({ ...cursor, id: "id.lt.anything" })).toString(
          "base64url",
        ),
      ),
    ).toBeNull();
  });

  it("bounds page sizes without allowing NaN or fractional values", () => {
    expect(normalizePageLimit(null)).toBe(20);
    expect(normalizePageLimit("0")).toBe(1);
    expect(normalizePageLimit("300")).toBe(30);
    expect(normalizePageLimit("3.5")).toBe(20);
    expect(normalizePageLimit("NaN")).toBe(20);
  });
});

describe("stored JPEG validation primitives", () => {
  it("requires JPEG start and end markers", () => {
    expect(isJpeg(Uint8Array.from([0xff, 0xd8, 0xff, 0xe0, 0, 0xff, 0xd9]))).toBe(true);
    expect(isJpeg(Uint8Array.from([0x89, 0x50, 0x4e, 0x47]))).toBe(false);
    expect(isJpeg(Uint8Array.from([0xff, 0xd8, 0xff, 0xe0]))).toBe(false);
  });
});
