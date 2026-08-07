package eviesaurr.sablecat.fix;

import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class CorruptedPointerCache {

    private static final long COOLDOWN_MS = 60_000L;

    private record Entry(long markedAtMs, int failCount) {}

    private static final ConcurrentHashMap<Long, ConcurrentHashMap<SavedSubLevelPointer, Entry>> cache =
            new ConcurrentHashMap<>();

    private static boolean isExpired(Entry entry) {
        return System.currentTimeMillis() - entry.markedAtMs() >= COOLDOWN_MS;
    }

    /** True only while a pointer is actively within its cooldown window. */
    public static boolean isCorrupted(long chunkKey, SavedSubLevelPointer pointer) {
        Map<SavedSubLevelPointer, Entry> chunkEntries = cache.get(chunkKey);
        if (chunkEntries == null) return false;

        Entry entry = chunkEntries.get(pointer);
        if (entry == null) return false;

        // Cooldown elapsed - stop blocking, but keep the entry (and its fail count)
        // around so a repeat failure continues the count instead of restarting it.
        return !isExpired(entry);
    }

    public static void markCorrupted(long chunkKey, SavedSubLevelPointer pointer) {
        cache.computeIfAbsent(chunkKey, k -> new ConcurrentHashMap<>())
                .compute(pointer, (p, existing) -> {
                    int nextFailCount = (existing != null ? existing.failCount() : 0) + 1;
                    return new Entry(System.currentTimeMillis(), nextFailCount);
                });
    }

    /** How many times this pointer has failed to load, total, across the whole session (0 if never failed). */
    public static int getFailCount(long chunkKey, SavedSubLevelPointer pointer) {
        Map<SavedSubLevelPointer, Entry> chunkEntries = cache.get(chunkKey);
        if (chunkEntries == null) return 0;
        Entry entry = chunkEntries.get(pointer);
        return entry != null ? entry.failCount() : 0;
    }

    /** Pointers in this chunk that are currently, actively blocked (i.e. still within their cooldown window). */
    public static Set<SavedSubLevelPointer> getCorrupted(long chunkKey) {
        Map<SavedSubLevelPointer, Entry> chunkEntries = cache.get(chunkKey);
        if (chunkEntries == null) return Set.of();
        return chunkEntries.entrySet().stream()
                .filter(e -> !isExpired(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Snapshot of every chunk -> currently-blocked-pointers pairing, for listing/debugging. */
    public static Map<Long, Set<SavedSubLevelPointer>> getAll() {
        Map<Long, Set<SavedSubLevelPointer>> result = new HashMap<>();
        for (long chunkKey : cache.keySet()) {
            Set<SavedSubLevelPointer> active = getCorrupted(chunkKey);
            if (!active.isEmpty()) {
                result.put(chunkKey, active);
            }
        }
        return result;
    }

    public static boolean clearOne(long chunkKey, SavedSubLevelPointer pointer) {
        Map<SavedSubLevelPointer, Entry> chunkEntries = cache.get(chunkKey);
        if (chunkEntries == null) return false;
        return chunkEntries.remove(pointer) != null;
    }

    public static boolean clearChunk(long chunkKey) {
        return cache.remove(chunkKey) != null;
    }

    public static void clear() {
        cache.clear();
    }
}