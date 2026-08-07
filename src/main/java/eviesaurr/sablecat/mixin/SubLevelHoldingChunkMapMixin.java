package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.*;
import eviesaurr.sablecat.fix.FixRegistry;
import eviesaurr.sablecat.fix.PendingSave;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import eviesaurr.sablecat.fix.CorruptedPointerCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mixin(SubLevelHoldingChunkMap.class)
public abstract class SubLevelHoldingChunkMapMixin {

    @Shadow(remap = false)
    @org.spongepowered.asm.mixin.Final
    private ServerLevel level;

    @Unique
    private ExecutorService sablecat$ioExecutor;

    // PendingSave is now a top-level record in this same package (see PendingSave.java) -
    // Mixin's processor doesn't allow plain nested classes/records inside a @Mixin class.
    @Unique
    private List<PendingSave> sablecat$pendingSaves;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void sablecat$init(CallbackInfo ci) {
        this.sablecat$ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sablecat Sub-Level I/O");
            t.setDaemon(true);
            return t;
        });
        this.sablecat$pendingSaves = new ArrayList<>();
    }

    /**
     * Records a save failure and warns any online ops immediately, rather than
     * relying on someone to notice a single ERROR log line. Runs the actual
     * broadcast on the main thread via level.getServer().execute(...), since
     * this may be called from the async IO thread.
     */
    @Unique
    private void sablecat$reportSaveFailure(String key, String description, Throwable error) {
        SableCat.LOGGER.error("Save failed - {}: {}", description, error.toString(), error);
        SaveFailureRegistry.recordFailure(key, description, error);

        if (this.level == null) return;
        this.level.getServer().execute(() -> {
            // Action bar, not chat - reads as a system notice rather than a
            // conversational message, doesn't pollute chat history/scrollback,
            // and auto-fades. Sable's own toast system (SableToastableServer)
            // is the "correct" vanilla answer here, but it's only implemented
            // for IntegratedServer (singleplayer) - there's no dedicated-server
            // wiring for it, so it silently does nothing on a real server.
            // Replicating it properly would need a custom network packet;
            // action bar gets most of the same low-intrusion feel for one line.
            Component msg = Component.literal(
                    "[sablecat] Save failed: " + description + " - check /sablecat save-failures list");
            for (ServerPlayer player : this.level.getServer().getPlayerList().getPlayers()) {
                if (player.hasPermissions(2)) {
                    player.displayClientMessage(msg, true);
                }
            }
        });
    }

    @Unique
    private void sablecat$reportSaveSuccess(String key) {
        SaveFailureRegistry.recordSuccess(key);
    }

    // Note: PalettedContainer is not thread-safe (has ThreadingDetector), ServerLevelPlot.save cannot run on async thread
    // So serialization (including PalettedContainer.pack) runs on main thread, only disk IO goes async

    @Redirect(
            method = "saveAll",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelStorage;attemptSaveSubLevel(Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)V", remap = false),
            remap = false
    )
    private void sablecat$wrapSaveSubLevel(SubLevelStorage storage, GlobalSavedSubLevelPointer pointer, SubLevelData data) {
        // data == null is a LEGITIMATE, intentional value here, not an error case -
        // it's Sable's own documented mechanism for deleting a sub-level's storage
        // slot (used by Sable's own queuedDeletion processing, and by our own
        // SubLevelPurge.purgeLive()). This crashed the entire server (confirmed via
        // a real crash report) because data.uuid() was called unconditionally below
        // without checking for this case first - any genuine deletion, ours or
        // Sable's own, would crash the very next save cycle after being queued.
        if (data == null) {
            String key = "delete:" + pointer;
            String desc = "deletion of sub-level at pointer " + pointer;
            if (!FixRegistry.isEnabled("async-save")) {
                try {
                    storage.attemptSaveSubLevel(pointer, null);
                    sablecat$reportSaveSuccess(key);
                } catch (Exception e) {
                    sablecat$reportSaveFailure(key, desc, e);
                }
                return;
            }
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    storage.attemptSaveSubLevel(pointer, null);
                    sablecat$reportSaveSuccess(key);
                } catch (Exception e) {
                    sablecat$reportSaveFailure(key, desc, e);
                }
            }, this.sablecat$ioExecutor);
            this.sablecat$pendingSaves.add(new PendingSave(key, desc, future));
            return;
        }

        if (FixRegistry.isEnabled("rescue-capture")) {
            SubLevelManifest.record(
                    data.uuid(), "unknown", data.fullTag(), "save");
        }

        String key = data.uuid().toString();
        String desc = "sub-level " + key + " (pointer " + pointer + ")";

        if (!FixRegistry.isEnabled("async-save")) {
            try {
                storage.attemptSaveSubLevel(pointer, data);
                sablecat$reportSaveSuccess(key);
            } catch (Exception e) {
                sablecat$reportSaveFailure(key, desc, e);
            }
            return;
        }
        // Submit disk IO to async thread to avoid blocking main thread
        // Exceptions are caught here (so one bad save can't take down the
        // whole batch or crash the IO thread) but ALSO reported immediately,
        // rather than only being swallowed - this is the actual fix for the
        // silent-loss issue, not just a log-line change.
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                storage.attemptSaveSubLevel(pointer, data);
                sablecat$reportSaveSuccess(key);
            } catch (Exception e) {
                sablecat$reportSaveFailure(key, desc, e);
            }
        }, this.sablecat$ioExecutor);
        this.sablecat$pendingSaves.add(new PendingSave(key, desc, future));
    }

    @Redirect(
            method = "saveAll",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelStorage;attemptSaveHoldingChunk(Lnet/minecraft/world/level/ChunkPos;Ldev/ryanhcode/sable/sublevel/storage/holding/SubLevelHoldingChunk;)V", remap = false),
            remap = false
    )
    private void sablecat$wrapSaveHoldingChunk(SubLevelStorage storage, ChunkPos chunkPos, SubLevelHoldingChunk holdingChunk) {
        String key = "chunk:" + chunkPos.toLong();
        String desc = "holding chunk at " + chunkPos;

        if (!FixRegistry.isEnabled("async-save")) {
            try {
                storage.attemptSaveHoldingChunk(chunkPos, holdingChunk);
                sablecat$reportSaveSuccess(key);
            } catch (Exception e) {
                sablecat$reportSaveFailure(key, desc, e);
            }
            return;
        }
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                storage.attemptSaveHoldingChunk(chunkPos, holdingChunk);
                sablecat$reportSaveSuccess(key);
            } catch (Exception e) {
                sablecat$reportSaveFailure(key, desc, e);
            }
        }, this.sablecat$ioExecutor);
        this.sablecat$pendingSaves.add(new PendingSave(key, desc, future));
    }

    /**
     * Prevents one corrupted chain member (e.g. a sub-level with NaN pose data
     * from a violent depenetration event) from silently aborting the rest of
     * saveAll(). Sable's own chain-processing loop has NO exception handling
     * at all - an uncaught throw here propagates out of the entire per-chain
     * loop, the outer per-sub-level loop, and potentially saveAll() itself,
     * meaning every sub-level scheduled to be processed afterward in that
     * same save cycle never gets saved either, not just the failing one.
     * <p>
     * This is the leading theory for how physically-touching sub-levels
     * (grouped together via getLoadingDependencyChain purely by bounding-box
     * intersection) can end up destroyed together - real incidents observed:
     * a portable radiator resting on a car, and a car resting on a lift
     * platform, both pairs later found with the "car" half's holding record
     * emptied out.
     * <p>
     * IMPORTANT: a naive catch-and-swallow here is NOT safe by itself. A
     * successful call ends by creating the destination holding chunk
     * (getOrLoadHoldingChunk(moveToChunk, true)); if we swallow an exception
     * that happened BEFORE that point, saveAll()'s very next line
     * (holdingChunk.markKeepLoaded()) would NPE on the chunk that was never
     * created. So on failure, we defensively create it ourselves - the
     * failed member's data isn't recovered, but the surrounding loop's
     * assumption is preserved and the batch keeps going.
     */
    @Redirect(
            method = "saveAll",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/storage/holding/SubLevelHoldingChunkMap;moveAndSaveSubLevel(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/world/level/ChunkPos;Ljava/util/List;)V", remap = false),
            remap = false
    )
    private void sablecat$wrapChainMemberSave(SubLevelHoldingChunkMap instance, ServerSubLevel subLevel, ChunkPos moveToChunk, List<UUID> uuids) {
        if (!FixRegistry.isEnabled("chain-save-guard")) {
            ((SubLevelHoldingChunkMapInvoker) instance).sablecat$moveAndSaveSubLevel(subLevel, moveToChunk, uuids);
            return;
        }

        String key = "chain-member:" + subLevel.getUniqueId();
        String desc = "chain-grouped save of sub-level " + subLevel.getUniqueId() + " (moving to " + moveToChunk + ")";

        try {
            ((SubLevelHoldingChunkMapInvoker) instance).sablecat$moveAndSaveSubLevel(subLevel, moveToChunk, uuids);
            sablecat$reportSaveSuccess(key);
        } catch (Exception e) {
            sablecat$reportSaveFailure(key, desc, e);
            // Ensure the destination chunk exists regardless, so saveAll()'s
            // following markKeepLoaded() call doesn't NPE on our behalf.
            ((SubLevelHoldingChunkMapInvoker) instance).sablecat$getOrLoadHoldingChunk(moveToChunk, true);
        }
    }

    /**
     * Waits for this batch's async saves before saveAll() returns. Individual
     * failures are already caught and reported inside each task (see
     * sablecat$wrapSaveSubLevel/wrapSaveHoldingChunk), so this join is just
     * about not returning from saveAll() with writes still in flight -
     * it deliberately does NOT re-throw on a per-task failure, since that
     * failure was already handled and reported at the point it happened.
     */
    @Inject(method = "saveAll", at = @At("RETURN"), remap = false)
    private void sablecat$awaitPendingIO(CallbackInfo ci) {
        // Independent of async-save's toggle below - this is a separate concern
        // (periodic archival), not disk-IO threading, so it runs regardless.
        if (this.level != null) {
            SubLevelBackup.maybeRunScheduledBackup(this.level, this.sablecat$ioExecutor);
        }

        if (!FixRegistry.isEnabled("async-save")) return;
        if (this.sablecat$pendingSaves.isEmpty()) return;

        List<PendingSave> batch = new ArrayList<>(this.sablecat$pendingSaves);
        this.sablecat$pendingSaves.clear();

        CompletableFuture<Void> all = CompletableFuture.allOf(
                batch.stream().map(PendingSave::future).toArray(CompletableFuture[]::new)
        );
        try {
            all.join();
        } catch (Exception e) {
            // Should be rare - individual tasks already catch their own exceptions -
            // but log it in case something outside our own try/catch went wrong.
            SableCat.LOGGER.error("Unexpected error waiting for async sub-level disk IO batch", e);
        }
    }

    // --- corrupted-cleanup ---

    @Inject(method = "getOrLoadHoldingChunk", at = @At("RETURN"), remap = false)
    private void sablecat$cleanupCorruptedPointers(ChunkPos chunkPos, boolean create, CallbackInfoReturnable<SubLevelHoldingChunk> cir) {
        if (!FixRegistry.isEnabled("corrupted-cleanup")) return;

        SubLevelHoldingChunk loadedChunk = cir.getReturnValue();
        if (loadedChunk == null) return;

        Set<SavedSubLevelPointer> knownCorrupted = CorruptedPointerCache.getCorrupted(chunkPos.toLong());
        if (knownCorrupted != null && !knownCorrupted.isEmpty()) {
            List<SavedSubLevelPointer> pointers = loadedChunk.getSubLevelPointers();
            int before = pointers.size();
            pointers.removeAll(knownCorrupted);
            int removed = before - pointers.size();
            if (removed > 0) {
                SableCat.LOGGER.info("Removed {} corrupted pointer(s) from holding chunk at {}", removed, chunkPos);
            }
        }
    }

    @Inject(method = "close", at = @At("HEAD"), remap = false)
    private void sablecat$awaitBeforeClose(CallbackInfo ci) {
        SubLevelManifest.maybeFlush(true);
        if (this.sablecat$ioExecutor != null) {
            this.sablecat$ioExecutor.shutdown();
        }
    }
}