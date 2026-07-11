import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { validateJpegBytes } from "./jpeg-validation";

function segment(marker: number, payload: number[]) {
  const length = payload.length + 2;
  return [0xff, marker, length >> 8, length & 0xff, ...payload];
}

function jpegFixture(width = 128, height = 96, entropy = [0x2a, 0xff, 0x00, 0x17]) {
  const dqt = segment(0xdb, [0x00, ...Array<number>(64).fill(1)]);
  const dht = segment(0xc4, [0x00, 1, ...Array<number>(15).fill(0), 0x00]);
  const sof = segment(0xc0, [
    8,
    height >> 8,
    height & 0xff,
    width >> 8,
    width & 0xff,
    3,
    1,
    0x11,
    0,
    2,
    0x11,
    0,
    3,
    0x11,
    0,
  ]);
  const sos = segment(0xda, [3, 1, 0, 2, 0, 3, 0, 0, 63, 0]);
  return Uint8Array.from([0xff, 0xd8, ...dqt, ...dht, ...sof, ...sos, ...entropy, 0xff, 0xd9]);
}

describe("validateJpegBytes", () => {
  it("accepts a real JPEG already shipped by the Android application", () => {
    const bytes = readFileSync(
      new URL("../../../app/src/main/res/drawable-nodpi/hero_leaf.jpg", import.meta.url),
    );
    expect(validateJpegBytes(bytes)).not.toBeNull();
  });

  it("accepts a bounded baseline JPEG structure", () => {
    expect(validateJpegBytes(jpegFixture())).toEqual({ width: 128, height: 96 });
  });

  it("rejects a truncated JPEG that retains its header and scan", () => {
    const valid = jpegFixture();
    expect(validateJpegBytes(valid.subarray(0, valid.length - 1))).toBeNull();
  });

  it("rejects images whose declared pixel count exceeds the bound", () => {
    expect(
      validateJpegBytes(jpegFixture(128, 128), {
        minimumDimension: 1,
        maximumDimension: 1_024,
        maximumPixels: 10_000,
      }),
    ).toBeNull();
  });

  it("rejects a segment length that runs past the upload", () => {
    const malformed = jpegFixture();
    malformed[4] = 0xff;
    malformed[5] = 0xff;
    expect(validateJpegBytes(malformed)).toBeNull();
  });

  it("rejects scans with no entropy-coded payload", () => {
    expect(validateJpegBytes(jpegFixture(128, 96, []))).toBeNull();
  });

  it("rejects data appended after the final EOI marker", () => {
    expect(validateJpegBytes(Uint8Array.from([...jpegFixture(), 0x00]))).toBeNull();
  });

  it("rejects marker-only data that is not a complete JPEG", () => {
    expect(validateJpegBytes(Uint8Array.from([0xff, 0xd8, 0xff, 0xd9]))).toBeNull();
  });
});
