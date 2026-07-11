export const DEFAULT_JPEG_LIMITS = {
  minimumDimension: 64,
  maximumDimension: 8_192,
  maximumPixels: 16_777_216,
} as const;

export type JpegLimits = {
  minimumDimension: number;
  maximumDimension: number;
  maximumPixels: number;
};

export type JpegDimensions = {
  width: number;
  height: number;
};

type Frame = JpegDimensions & {
  marker: number;
  componentIds: Set<number>;
};

const MARKER_PREFIX = 0xff;
const SOI = 0xd8;
const EOI = 0xd9;
const SOS = 0xda;
const DHT = 0xc4;
const DQT = 0xdb;
const DRI = 0xdd;
const COM = 0xfe;
const BASELINE_SOF = 0xc0;
const PROGRESSIVE_SOF = 0xc2;
const MAX_SEGMENTS = 1_024;
const MAX_SCANS = 64;

/**
 * Validates the bounded structure used by ordinary baseline/progressive JPEGs.
 * The parser never allocates from image dimensions and rejects malformed
 * segment lengths, unsupported frame types, oversized frames, incomplete
 * scans, trailing payloads, and files without a final EOI marker.
 */
export function validateJpegBytes(
  bytes: Uint8Array,
  limits: JpegLimits = DEFAULT_JPEG_LIMITS,
): JpegDimensions | null {
  if (!validLimits(limits) || bytes.byteLength < 4) return null;
  if (bytes[0] !== MARKER_PREFIX || bytes[1] !== SOI) return null;

  let offset = 2;
  let segmentCount = 0;
  let scanCount = 0;
  let frame: Frame | null = null;
  let hasQuantizationTable = false;
  let hasHuffmanTable = false;

  while (offset < bytes.length) {
    if (bytes[offset] !== MARKER_PREFIX) return null;
    while (offset < bytes.length && bytes[offset] === MARKER_PREFIX) offset += 1;
    if (offset >= bytes.length) return null;

    const marker = bytes[offset++];
    if (marker === 0x00) return null;
    if (marker === EOI) {
      return offset === bytes.length &&
        frame != null &&
        scanCount > 0 &&
        hasQuantizationTable &&
        hasHuffmanTable
        ? { width: frame.width, height: frame.height }
        : null;
    }
    if (marker === SOI || marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) {
      return null;
    }
    if (++segmentCount > MAX_SEGMENTS || offset + 2 > bytes.length) return null;

    const segmentLength = readUint16(bytes, offset);
    if (segmentLength < 2) return null;
    const payloadStart = offset + 2;
    const segmentEnd = offset + segmentLength;
    if (segmentEnd > bytes.length) return null;

    if (marker === DQT) {
      if (!validQuantizationTables(bytes, payloadStart, segmentEnd)) return null;
      hasQuantizationTable = true;
      offset = segmentEnd;
      continue;
    }
    if (marker === DHT) {
      if (!validHuffmanTables(bytes, payloadStart, segmentEnd)) return null;
      hasHuffmanTable = true;
      offset = segmentEnd;
      continue;
    }
    if (marker === BASELINE_SOF || marker === PROGRESSIVE_SOF) {
      if (frame != null) return null;
      frame = parseFrame(bytes, marker, segmentLength, payloadStart, limits);
      if (frame == null) return null;
      offset = segmentEnd;
      continue;
    }
    if (marker === SOS) {
      if (frame == null || !validScanHeader(bytes, frame, segmentLength, payloadStart)) {
        return null;
      }
      if (++scanCount > MAX_SCANS) return null;
      offset = segmentEnd;
      let entropyBytes = 0;
      while (offset < bytes.length) {
        if (bytes[offset] !== MARKER_PREFIX) {
          entropyBytes += 1;
          offset += 1;
          continue;
        }

        const prefixOffset = offset;
        let codeOffset = offset + 1;
        while (codeOffset < bytes.length && bytes[codeOffset] === MARKER_PREFIX) {
          codeOffset += 1;
        }
        if (codeOffset >= bytes.length) return null;
        const scanMarker = bytes[codeOffset];
        if (scanMarker === 0x00) {
          entropyBytes += 1;
          offset = codeOffset + 1;
          continue;
        }
        if (scanMarker >= 0xd0 && scanMarker <= 0xd7) {
          offset = codeOffset + 1;
          continue;
        }
        offset = prefixOffset;
        break;
      }
      if (entropyBytes === 0) return null;
      continue;
    }
    if (marker === DRI) {
      if (segmentLength !== 4) return null;
      offset = segmentEnd;
      continue;
    }
    if ((marker >= 0xe0 && marker <= 0xef) || marker === COM) {
      offset = segmentEnd;
      continue;
    }

    // Reject arithmetic, hierarchical, lossless, JPG-extension, DNL and other
    // uncommon marker families that the mobile ingestion contract does not use.
    return null;
  }

  return null;
}

function validLimits(limits: JpegLimits) {
  return (
    Number.isSafeInteger(limits.minimumDimension) &&
    Number.isSafeInteger(limits.maximumDimension) &&
    Number.isSafeInteger(limits.maximumPixels) &&
    limits.minimumDimension > 0 &&
    limits.maximumDimension >= limits.minimumDimension &&
    limits.maximumPixels > 0
  );
}

function readUint16(bytes: Uint8Array, offset: number) {
  return bytes[offset] * 256 + bytes[offset + 1];
}

function validQuantizationTables(bytes: Uint8Array, start: number, end: number) {
  let offset = start;
  let tableCount = 0;
  while (offset < end) {
    const descriptor = bytes[offset++];
    const precision = descriptor >> 4;
    const tableId = descriptor & 0x0f;
    if (precision > 1 || tableId > 3) return false;
    const tableBytes = precision === 0 ? 64 : 128;
    if (offset + tableBytes > end) return false;
    offset += tableBytes;
    tableCount += 1;
  }
  return tableCount > 0 && offset === end;
}

function validHuffmanTables(bytes: Uint8Array, start: number, end: number) {
  let offset = start;
  let tableCount = 0;
  while (offset < end) {
    if (offset + 17 > end) return false;
    const descriptor = bytes[offset++];
    if ((descriptor >> 4) > 1 || (descriptor & 0x0f) > 3) return false;
    let symbolCount = 0;
    for (let index = 0; index < 16; index += 1) symbolCount += bytes[offset + index];
    offset += 16;
    if (symbolCount < 1 || symbolCount > 256 || offset + symbolCount > end) return false;
    offset += symbolCount;
    tableCount += 1;
  }
  return tableCount > 0 && offset === end;
}

function parseFrame(
  bytes: Uint8Array,
  marker: number,
  segmentLength: number,
  start: number,
  limits: JpegLimits,
): Frame | null {
  if (segmentLength < 11 || bytes[start] !== 8) return null;
  const height = readUint16(bytes, start + 1);
  const width = readUint16(bytes, start + 3);
  const componentCount = bytes[start + 5];
  if (componentCount < 1 || componentCount > 4 || segmentLength !== 8 + 3 * componentCount) {
    return null;
  }
  if (
    width < limits.minimumDimension ||
    height < limits.minimumDimension ||
    width > limits.maximumDimension ||
    height > limits.maximumDimension ||
    width * height > limits.maximumPixels
  ) {
    return null;
  }

  const componentIds = new Set<number>();
  for (let index = 0; index < componentCount; index += 1) {
    const componentOffset = start + 6 + index * 3;
    const componentId = bytes[componentOffset];
    const sampling = bytes[componentOffset + 1];
    const horizontalSampling = sampling >> 4;
    const verticalSampling = sampling & 0x0f;
    const quantizationTable = bytes[componentOffset + 2];
    if (
      componentIds.has(componentId) ||
      horizontalSampling < 1 ||
      horizontalSampling > 4 ||
      verticalSampling < 1 ||
      verticalSampling > 4 ||
      quantizationTable > 3
    ) {
      return null;
    }
    componentIds.add(componentId);
  }
  return { width, height, marker, componentIds };
}

function validScanHeader(
  bytes: Uint8Array,
  frame: Frame,
  segmentLength: number,
  start: number,
) {
  const componentCount = bytes[start];
  if (
    componentCount < 1 ||
    componentCount > frame.componentIds.size ||
    segmentLength !== 6 + 2 * componentCount
  ) {
    return false;
  }

  const seenComponents = new Set<number>();
  for (let index = 0; index < componentCount; index += 1) {
    const componentOffset = start + 1 + index * 2;
    const componentId = bytes[componentOffset];
    const tableSelectors = bytes[componentOffset + 1];
    if (
      !frame.componentIds.has(componentId) ||
      seenComponents.has(componentId) ||
      (tableSelectors >> 4) > 3 ||
      (tableSelectors & 0x0f) > 3
    ) {
      return false;
    }
    seenComponents.add(componentId);
  }

  const spectralOffset = start + 1 + componentCount * 2;
  const spectralStart = bytes[spectralOffset];
  const spectralEnd = bytes[spectralOffset + 1];
  const approximation = bytes[spectralOffset + 2];
  if (frame.marker === BASELINE_SOF) {
    return spectralStart === 0 && spectralEnd === 63 && approximation === 0;
  }
  return (
    spectralStart <= spectralEnd &&
    spectralEnd <= 63 &&
    (approximation >> 4) <= 13 &&
    (approximation & 0x0f) <= 13
  );
}
