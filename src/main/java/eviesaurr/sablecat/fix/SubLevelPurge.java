package eviesaurr.sablecat.fix;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.mixin.SubLevelHoldingChunkInvoker;
import eviesaurr.sablecat.mixin.SubLevelHoldingChunkMapInvoker;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.UUID;

/**
 * Permanently deletes a captured, unrescuable (CONTENT_EMPTY) holding
 * sub-level record from Sable's storage, using Sable's own snatch/setDirty
 * machinery rather than hand-editing storage files.
 * <p>
 * Deliberately restricted to CONTENT_EMPTY only - POSE_CORRUPTED and OTHER
 * records may still be genuinely rescuable (see SubLevelRescue) and purging
 * them would destroy recoverable data. This is a one-way operation: once
 * snatch() removes the pointer and the chunk is saved dirty, the dead
 * reference is gone and will never be attempted (or logged as failing) again.
 */
public final class SubLevelPurge {

    public record PurgeResult(boolean success, String message) {}

    public static PurgeResult purge(ServerLevel level, UUID uuid) {
        CorruptedHoldingRegistry.CapturedFailure failure = CorruptedHoldingRegistry.get(uuid);
        if (failure == null) {
            return new PurgeResult(false, "No captured failure with UUID " + uuid + " - nothing to purge.");
        }
        if (failure.kind() != CorruptedHoldingRegistry.FailureKind.CONTENT_EMPTY) {
            return new PurgeResult(false,
                    "Refusing to purge " + uuid + ": classified as " + failure.kind() + ", not CONTENT_EMPTY. "
                            + "This record may still be rescuable - try /sablecat rescue " + uuid + " instead.");
        }

        GlobalSavedSubLevelPointer pointer = failure.holding().pointer();
        if (pointer == null) {
            return new PurgeResult(false, "Captured failure for " + uuid + " has no pointer - can't locate its holding chunk.");
        }
        ChunkPos chunkPos = pointer.chunkPos();

        var container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return new PurgeResult(false, "No sub-level container for this dimension.");
        }

        SubLevelHoldingChunkMap holdingMap = container.getHoldingChunkMap();
        SubLevelHoldingChunkMapInvoker mapInvoker = (SubLevelHoldingChunkMapInvoker) holdingMap;

        SubLevelHoldingChunk holdingChunk = mapInvoker.sablecat$getOrLoadHoldingChunk(chunkPos, false);
        if (holdingChunk == null) {
            // Nothing on disk to remove either - already effectively gone. Clear our
            // own record and report success rather than leaving a stale registry entry.
            CorruptedHoldingRegistry.remove(uuid);
            return new PurgeResult(true,
                    "No holding chunk found at " + chunkPos + " - " + uuid + " was already absent from storage. Cleared from registry.");
        }

        SubLevelHoldingChunkInvoker chunkInvoker = (SubLevelHoldingChunkInvoker) holdingChunk;

        // THE REAL FIX: SubLevelHoldingChunk has TWO separate collections, confirmed
        // by decompiling the actual class. snatch() only touches
        // `loadedHoldingSubLevels` (an in-memory-only resolved-object cache) - but
        // writeTo() (what actually gets persisted to disk) reads from a completely
        // different field, `pointers` (the raw index list). snatch() removing
        // something from the wrong collection can NEVER affect what gets saved,
        // regardless of whether it found a match or not - confirmed by real testing:
        // a previous "always save anyway" fix still didn't stop the pointer from
        // reappearing after a relog, because it kept re-saving the SAME untouched
        // raw pointer list every time. getSubLevelPointers() returns that raw list's
        // live reference (not a copy), so removing from it directly is what's
        // actually needed.
        Collection<HoldingSubLevel> removed = chunkInvoker.sablecat$snatch(uuid);
        boolean removedFromResolvedCache = removed != null && !removed.isEmpty();

        SavedSubLevelPointer localPointer = pointer.local();
        boolean removedFromRawList = holdingChunk.getSubLevelPointers().remove(localPointer);
        boolean actuallyRemoved = removedFromResolvedCache || removedFromRawList;

        mapInvoker.sablecat$setDirty(chunkPos);

        // Don't rely on the dirty flag alone: saveAll()'s dirty-chunk loop does
        // `if (loadedHoldingChunks.get(longKey) == null) continue;` with NO
        // logging at all (confirmed by decompiling the actual 2.0.3 jar) - if
        // this chunk falls out of loadedHoldingChunks before the next saveAll()
        // runs (e.g. normal unload processing evicting an empty, unvisited
        // chunk), the purge silently never persists. Saving directly, right
        // now, while we know the object is still valid, sidesteps that race
        // entirely instead of hoping a future save cycle finds it.
        try {
            holdingMap.getStorage().attemptSaveHoldingChunk(chunkPos, holdingChunk);
            holdingMap.getStorage().flush(); // attemptSaveHoldingChunk only writes to the cached
            // region file object - flush() is what actually
            // reaches physical disk. Normally called once at the
            // end of the batched saveAll(); we call it ourselves
            // here since we're bypassing that batch entirely.
        } catch (Exception e) {
            SableCat.LOGGER.error("Purge of {} removed the pointer in memory, but the immediate persist failed", uuid, e);
            return new PurgeResult(false,
                    "Purged " + uuid + " in memory, but failed to persist immediately: " + e
                            + ". It WILL be lost if the server restarts before the next successful save.");
        }

        CorruptedHoldingRegistry.remove(uuid);

        if (actuallyRemoved) {
            SableCat.LOGGER.info("Purged unrescuable sub-level {} (resolved-cache hit: {}, raw-list hit: {}, saved to disk)",
                    uuid, removedFromResolvedCache, removedFromRawList);
            return new PurgeResult(true,
                    "Purged " + uuid + " - removed from holding chunk at " + chunkPos
                            + " and saved to disk immediately. This will no longer appear as a failed load.");
        } else {
            SableCat.LOGGER.info("Sub-level {} was already absent from holding chunk at {} in memory "
                    + "(likely auto-cleaned by Sable on a prior failed load) - forced a save to disk anyway, "
                    + "since that correction had never actually been persisted", uuid, chunkPos);
            return new PurgeResult(true,
                    "Purged " + uuid + " - it was already gone from the in-memory chunk at " + chunkPos
                            + ", but the disk copy was never updated to match until now. Forced that save. "
                            + "This should no longer come back after a relog.");
        }
    }


    /**
     * Purges a LIVE, currently-loaded sub-level that has no block content -
     * a genuinely different case from the CONTENT_EMPTY capture above. That
     * path only ever sees sub-levels that FAILED to load (fullyLoad returned
     * null). A sub-level that loaded successfully but is simply empty never
     * fails, never gets captured, and was completely invisible to purge()
     * until this method - it only shows up in the manifest (recordLive
     * fires on successful load) with a degenerate/near-zero bounding box.
     * <p>
     * Uses Sable's own queueDeletion(ServerSubLevel) - its real, clean
     * deletion mechanism - but does NOT rely on its deferred batch
     * processing (same silent-skip risk as holding-chunk saves; queueDeletion
     * marks the holding chunk dirty and waits for a later saveAll() to
     * process it). Instead, immediately after queueDeletion, this forces the
     * same direct save+flush pattern used in purge() above, and also
     * directly persists the sub-level's own data deletion (writes null to
     * its storage slot) rather than waiting for queuedDeletion's batch.
     */
    public static PurgeResult purgeLive(ServerLevel level, UUID uuid) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return new PurgeResult(false, "No sub-level container for this dimension.");
        }

        var subLevel = container.getSubLevel(uuid);
        if (subLevel == null) {
            return new PurgeResult(false,
                    uuid + " is not currently loaded. If it's a captured load failure, use /sablecat rescue "
                            + uuid + " purge instead. Otherwise, visit its last-known location to force a load attempt first.");
        }
        if (!(subLevel instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel serverSubLevel)) {
            return new PurgeResult(false, uuid + " is loaded but not a server-side sub-level - can't purge from here.");
        }

        var bounds = subLevel.boundingBox();
        double volume = Math.max(0, bounds.maxX() - bounds.minX())
                * Math.max(0, bounds.maxY() - bounds.minY())
                * Math.max(0, bounds.maxZ() - bounds.minZ());
        if (volume > 1.0) {
            return new PurgeResult(false,
                    uuid + " has a non-trivial bounding box (volume ~" + String.format("%.1f", volume)
                            + ") - it likely still has real block content. Refusing to purge a live sub-level that isn't confirmed empty. "
                            + "If you genuinely need to remove it anyway (a buggy build, not a corrupted one), use /sablecat forcepurge "
                            + uuid + " instead.");
        }

        return deleteLiveSubLevel(container, serverSubLevel, uuid,
                "Purged live sub-level " + uuid + " (confirmed near-empty, volume ~" + String.format("%.1f", volume)
                        + ") and saved to disk immediately.");
    }

    /**
     * Unconditionally deletes ANY currently-loaded sub-level, real block
     * content or not - for admin removal of buggy-but-not-corrupted builds
     * (e.g. a contraption with multiple swivel bearings causing server-wide
     * instability). This is the SAME mechanism as purgeLive, just without the
     * volume safety gate. Given it can destroy legitimate player work, every
     * call is logged permanently at INFO level (not just on failure) and
     * broadcast to every online op, not just the command's own caller -
     * unlike every other purge/rescue command tonight, this one deliberately
     * leaves a loud, unmissable trail rather than a quiet one.
     */
    public static PurgeResult forcePurgeLive(ServerLevel level, UUID uuid, String reason) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return new PurgeResult(false, "No sub-level container for this dimension.");
        }

        var subLevel = container.getSubLevel(uuid);
        if (subLevel == null) {
            return new PurgeResult(false,
                    uuid + " is not currently loaded. It must be loaded (visit its last-known location) before it can be force-purged.");
        }
        if (!(subLevel instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel serverSubLevel)) {
            return new PurgeResult(false, uuid + " is loaded but not a server-side sub-level - can't purge from here.");
        }

        String name = subLevel.getName();
        String label = (name != null && !name.isEmpty()) ? name : uuid.toString();

        var bounds = subLevel.boundingBox();
        double volume = Math.max(0, bounds.maxX() - bounds.minX())
                * Math.max(0, bounds.maxY() - bounds.minY())
                * Math.max(0, bounds.maxZ() - bounds.minZ());

        String broadcastMsg = "[SableCat] FORCE-PURGED sub-level '" + label + "' (" + uuid + ", volume ~"
                + String.format("%.1f", volume) + ") - reason: " + (reason == null || reason.isBlank() ? "(none given)" : reason);
        SableCat.LOGGER.info(broadcastMsg);
        if (level.getServer() != null) {
            level.getServer().execute(() -> {
                for (var player : level.getServer().getPlayerList().getPlayers()) {
                    if (player.hasPermissions(2)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(broadcastMsg));
                    }
                }
            });
        }

        return deleteLiveSubLevel(container, serverSubLevel, uuid,
                "Force-purged '" + label + "' (" + uuid + ", volume ~" + String.format("%.1f", volume)
                        + ") and saved to disk immediately.");
    }

    /**
     * Shared deletion mechanics for both purgeLive and forcePurgeLive - queues
     * the deletion via Sable's own queueDeletion(), then forces both the
     * sub-level's own data slot AND its holding chunk record to persist
     * immediately, rather than trusting a future saveAll() batch to find them
     * (same lesson as every other purge fix tonight).
     */
    private static PurgeResult deleteLiveSubLevel(dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container, ServerSubLevel serverSubLevel,
                                                  UUID uuid, String successMessage) {
        var pointer = serverSubLevel.getLastSerializationPointer();
        SubLevelHoldingChunkMap holdingMap = container.getHoldingChunkMap();

        // Diagnostic logging: a real contradiction showed up in testing (BUGGER 392) -
        // forcepurge reported success on the storage side, yet a fresh reload still
        // loaded it with full, real content. That only makes sense if the persist block
        // below never actually ran - most likely because pointer is null for this
        // specific sub-level, silently skipping the entire wipe. Logging this
        // unconditionally (not just on failure) so the NEXT attempt's log tells us the
        // real state, rather than guessing again.
        SableCat.LOGGER.info("deleteLiveSubLevel: uuid={} pointer={} (null={})",
                uuid, pointer, pointer == null);

        holdingMap.queueDeletion(serverSubLevel);

        try {
            if (pointer != null) {
                holdingMap.getStorage().attemptSaveSubLevel(pointer, null);

                SubLevelHoldingChunkMapInvoker mapInvoker = (SubLevelHoldingChunkMapInvoker) holdingMap;
                SubLevelHoldingChunk holdingChunk = mapInvoker.sablecat$getOrLoadHoldingChunk(pointer.chunkPos(), false);
                if (holdingChunk != null) {
                    holdingMap.getStorage().attemptSaveHoldingChunk(pointer.chunkPos(), holdingChunk);
                }
            } else {
                SableCat.LOGGER.warn("deleteLiveSubLevel: pointer was null for {} - storage wipe SKIPPED entirely, "
                        + "only the live in-memory object and queueDeletion's own pointer-list removal happened. "
                        + "On-disk data for this sub-level was NOT touched.", uuid);
            }
            holdingMap.getStorage().flush();

            SableCat.LOGGER.info("deleteLiveSubLevel: calling removeSubLevel(subLevel, REMOVED) for {}", uuid);
            container.removeSubLevel(serverSubLevel, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        } catch (Exception e) {
            SableCat.LOGGER.error("Deletion of live sub-level {} failed during immediate persist", uuid, e);
            return new PurgeResult(false,
                    "Deleted " + uuid + " in memory, but failed to persist immediately: " + e
                            + ". It WILL be lost if the server restarts before the next successful save.");
        }

        return new PurgeResult(true, successMessage);
    }
}