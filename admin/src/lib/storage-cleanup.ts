export const STORAGE_CLEANUP_BATCH_LIMIT = 25;

export type StorageCleanupEntry = {
  cleanupId: string;
  bucketId: string;
  objectPath: string;
  attemptCount: number;
};

type StorageCleanupDependencies = {
  claim: (limit: number) => Promise<StorageCleanupEntry[]>;
  remove: (bucketId: string, objectPath: string) => Promise<boolean>;
  acknowledge: (
    cleanupId: string,
    succeeded: boolean,
    errorCode: string | null,
  ) => Promise<boolean>;
};

type ImmediateCleanupDependencies = {
  remove: (paths: string[]) => Promise<boolean>;
  acknowledge: (paths: string[]) => Promise<boolean>;
};

export type StorageCleanupBatchResult = {
  claimed: number;
  completed: number;
  retriesQueued: number;
  acknowledgementFailures: number;
  claimFailed: boolean;
};

export function cleanupQueuedFromPersistentCount(
  count: number | null,
  queryFailed: boolean,
) {
  // Unknown queue state must never be reported as completed to a privacy flow.
  return queryFailed || (count ?? 0) > 0;
}

export async function attemptImmediateStorageCleanup(
  paths: string[],
  dependencies: ImmediateCleanupDependencies,
) {
  const uniquePaths = [...new Set(paths)];
  if (uniquePaths.length === 0) return { cleanupQueued: false };

  let removed = false;
  try {
    removed = await dependencies.remove(uniquePaths);
  } catch {
    return { cleanupQueued: true };
  }
  if (!removed) return { cleanupQueued: true };

  try {
    return {
      cleanupQueued: !(await dependencies.acknowledge(uniquePaths)),
    };
  } catch {
    // The durable row remains safe to retry even though object removal succeeded.
    return { cleanupQueued: true };
  }
}

export async function processStorageCleanupBatch(
  dependencies: StorageCleanupDependencies,
  limit = STORAGE_CLEANUP_BATCH_LIMIT,
): Promise<StorageCleanupBatchResult> {
  const boundedLimit = Number.isFinite(limit)
    ? Math.min(100, Math.max(1, Math.trunc(limit)))
    : STORAGE_CLEANUP_BATCH_LIMIT;
  let entries: StorageCleanupEntry[];
  try {
    entries = await dependencies.claim(boundedLimit);
  } catch {
    return {
      claimed: 0,
      completed: 0,
      retriesQueued: 0,
      acknowledgementFailures: 0,
      claimFailed: true,
    };
  }

  const result: StorageCleanupBatchResult = {
    claimed: entries.length,
    completed: 0,
    retriesQueued: 0,
    acknowledgementFailures: 0,
    claimFailed: false,
  };

  for (const entry of entries) {
    let removed = false;
    try {
      removed = await dependencies.remove(entry.bucketId, entry.objectPath);
    } catch {
      removed = false;
    }

    let acknowledged = false;
    try {
      acknowledged = await dependencies.acknowledge(
        entry.cleanupId,
        removed,
        removed ? null : "storage_remove_failed",
      );
    } catch {
      acknowledged = false;
    }

    if (removed && acknowledged) {
      result.completed += 1;
    } else if (acknowledged) {
      result.retriesQueued += 1;
    }
    if (!acknowledged) result.acknowledgementFailures += 1;
  }

  return result;
}
