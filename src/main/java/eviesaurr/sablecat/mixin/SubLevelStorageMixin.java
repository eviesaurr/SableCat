package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.CorruptedPointerCache;
import eviesaurr.sablecat.fix.FixRegistry;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SubLevelStorage.class)
public abstract class SubLevelStorageMixin {

    @Inject(method = "attemptLoadSubLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private void sablecat$skipCorruptedPointer(ChunkPos chunkPos, SavedSubLevelPointer pointer, CallbackInfoReturnable<SubLevelData> cir) {
        if (!FixRegistry.isEnabled("corrupted-cleanup")) return;

        long chunkKey = chunkPos.toLong();
        if (CorruptedPointerCache.isCorrupted(chunkKey, pointer)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "attemptLoadSubLevel", at = @At("RETURN"), remap = false)
    private void sablecat$cacheFailedPointer(ChunkPos chunkPos, SavedSubLevelPointer pointer, CallbackInfoReturnable<SubLevelData> cir) {
        if (!FixRegistry.isEnabled("corrupted-cleanup")) return;

        long chunkKey = chunkPos.toLong();

        if (cir.getReturnValue() == null) {
            if (!CorruptedPointerCache.isCorrupted(chunkKey, pointer)) {
                CorruptedPointerCache.markCorrupted(chunkKey, pointer);
                SableCat.LOGGER.warn("Corrupted sub-level pointer cached (attempt #{}): {} in chunk {}",
                        CorruptedPointerCache.getFailCount(chunkKey, pointer), pointer, chunkPos);
            }
        } else {
            // Loaded successfully - if this pointer had previously failed, forget that
            // history entirely rather than leaving a stale fail count lying around.
            if (CorruptedPointerCache.clearOne(chunkKey, pointer)) {
                SableCat.LOGGER.info("Sub-level pointer recovered: {} in chunk {}", pointer, chunkPos);
            }
        }
    }
}