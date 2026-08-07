package eviesaurr.sablecat.fix;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks sub-level/holding-chunk save failures that async-save's catch blocks
 * would otherwise only log once and discard.
 * <p>
 * This exists because of a structural gap in how async-save interacts with
 * Sable's own SubLevelHoldingChunkMap.saveAll(): that method removes a
 * sub-level/chunk from ALL in-memory tracking (allHoldingSubLevels,
 * loadedHoldingChunks) synchronously, immediately after merely SUBMITTING
 * the async save - not after it completes. If the save then fails (throws),
 * there is no in-memory record left to retry from, and nothing on disk
 * either. The failure becomes permanently silent and unrecoverable unless
 * something outside Sable's own bookkeeping remembers it happened - which is
 * what this registry is for.
 */

public final class SaveFailureRegistry {

    public record Failure(String description, String errorMessage, long timestampMs, int occurrenceCount) {}

    private static final Map<String, Failure> failures = new ConcurrentHashMap<>();

    /** Key is a caller-chosen identifier - typically a UUID string or chunk pos, kept stable across attempts. */
    public static void recordFailure(String key, String description, Throwable error) {
        failures.compute(key, (k, existing) -> new Failure(
                description,
                error.getClass().getSimpleName() + ": " + error.getMessage(),
                System.currentTimeMillis(),
                (existing != null ? existing.occurrenceCount() : 0) + 1
        ));
    }

    /** Call when a subsequent save for the same key succeeds, so it stops being flagged. */
    public static void recordSuccess(String key) {
        failures.remove(key);
    }

    public static Map<String, Failure> getAll() {
        return new LinkedHashMap<>(failures);
    }

    public static void clear() {
        failures.clear();
    }
}