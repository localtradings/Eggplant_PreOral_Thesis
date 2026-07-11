import { describe, expect, it, vi } from "vitest";
import {
  attemptImmediateStorageCleanup,
  cleanupQueuedFromPersistentCount,
  processStorageCleanupBatch,
  type StorageCleanupEntry,
} from "./storage-cleanup";

const entry = (id: string, path: string): StorageCleanupEntry => ({
  cleanupId: id,
  bucketId: "eggplant-scans",
  objectPath: path,
  attemptCount: 1,
});

describe("attemptImmediateStorageCleanup", () => {
  it("leaves durable cleanup queued when Storage removal fails", async () => {
    const acknowledge = vi.fn(async () => true);
    const result = await attemptImmediateStorageCleanup(
      ["global/owner/scan/photo.jpg"],
      {
        remove: async () => false,
        acknowledge,
      },
    );

    expect(result).toEqual({ cleanupQueued: true });
    expect(acknowledge).not.toHaveBeenCalled();
  });

  it("preserves cleanupQueued when acknowledgement fails after removal", async () => {
    const result = await attemptImmediateStorageCleanup(
      ["global/owner/scan/photo.jpg"],
      {
        remove: async () => true,
        acknowledge: async () => false,
      },
    );

    expect(result).toEqual({ cleanupQueued: true });
  });

  it("clears cleanupQueued only after removal and acknowledgement", async () => {
    const remove = vi.fn(async () => true);
    const acknowledge = vi.fn(async () => true);
    const result = await attemptImmediateStorageCleanup(
      ["global/owner/scan/photo.jpg", "global/owner/scan/photo.jpg"],
      { remove, acknowledge },
    );

    expect(result).toEqual({ cleanupQueued: false });
    expect(remove).toHaveBeenCalledWith(["global/owner/scan/photo.jpg"]);
    expect(acknowledge).toHaveBeenCalledWith([
      "global/owner/scan/photo.jpg",
    ]);
  });

  it("retries an outstanding path returned by a repeated disable", async () => {
    const path = "global/owner/scan/retry.jpg";
    const remove = vi
      .fn<(paths: string[]) => Promise<boolean>>()
      .mockResolvedValueOnce(false)
      .mockResolvedValueOnce(true);
    const acknowledge = vi.fn(async () => true);

    const firstDisable = await attemptImmediateStorageCleanup([path], {
      remove,
      acknowledge,
    });
    const repeatedDisable = await attemptImmediateStorageCleanup([path], {
      remove,
      acknowledge,
    });

    expect(firstDisable).toEqual({ cleanupQueued: true });
    expect(repeatedDisable).toEqual({ cleanupQueued: false });
    expect(remove).toHaveBeenCalledTimes(2);
    expect(acknowledge).toHaveBeenCalledOnce();
    expect(acknowledge).toHaveBeenCalledWith([path]);
  });
});

describe("cleanupQueuedFromPersistentCount", () => {
  it("preserves cleanupQueued across retries and fails closed", () => {
    expect(cleanupQueuedFromPersistentCount(1, false)).toBe(true);
    expect(cleanupQueuedFromPersistentCount(0, false)).toBe(false);
    expect(cleanupQueuedFromPersistentCount(null, true)).toBe(true);
  });
});

describe("processStorageCleanupBatch", () => {
  it("acknowledges success and durably schedules failed removals", async () => {
    const rows = [
      entry("01890f3d-00d8-7b65-9a77-a79bfe3f8481", "global/a/one.jpg"),
      entry("01890f3d-00d8-7b65-9a77-a79bfe3f8482", "global/a/two.jpg"),
    ];
    const acknowledge = vi.fn(async () => true);
    const result = await processStorageCleanupBatch({
      claim: async () => rows,
      remove: async (_bucket, path) => path.endsWith("one.jpg"),
      acknowledge,
    });

    expect(result).toEqual({
      claimed: 2,
      completed: 1,
      retriesQueued: 1,
      acknowledgementFailures: 0,
      claimFailed: false,
    });
    expect(acknowledge).toHaveBeenNthCalledWith(
      1,
      rows[0].cleanupId,
      true,
      null,
    );
    expect(acknowledge).toHaveBeenNthCalledWith(
      2,
      rows[1].cleanupId,
      false,
      "storage_remove_failed",
    );
  });

  it("reports claim and acknowledgement failures without dropping work", async () => {
    const claimFailure = await processStorageCleanupBatch({
      claim: async () => {
        throw new Error("unavailable");
      },
      remove: async () => true,
      acknowledge: async () => true,
    });
    expect(claimFailure.claimFailed).toBe(true);

    const acknowledgementFailure = await processStorageCleanupBatch({
      claim: async () => [
        entry("01890f3d-00d8-7b65-9a77-a79bfe3f8483", "global/a/three.jpg"),
      ],
      remove: async () => true,
      acknowledge: async () => false,
    });
    expect(acknowledgementFailure).toMatchObject({
      claimed: 1,
      completed: 0,
      retriesQueued: 0,
      acknowledgementFailures: 1,
      claimFailed: false,
    });
  });
});
