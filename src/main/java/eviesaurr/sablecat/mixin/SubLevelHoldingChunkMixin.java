package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(SubLevelHoldingChunk.class)
public abstract class SubLevelHoldingChunkMixin {

    @Shadow(remap = false)
    private final Object2ObjectMap<UUID, HoldingSubLevel> loadedHoldingSubLevels = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>();

    @Inject(method = "collectReadySubLevels", at = @At("HEAD"), remap = false)
    private void sablecat$pruneOrphanedDependencies(ServerLevel level, Object2ObjectMap<UUID, HoldingSubLevel> readySubLevels, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("corrupted-cleanup")) return;

        Set<UUID> orphans = new HashSet<>();
        for (Map.Entry<UUID, HoldingSubLevel> entry : this.loadedHoldingSubLevels.object2ObjectEntrySet()) {
            List<UUID> deps = entry.getValue().data().dependencies();
            for (UUID dep : deps) {
                if (!this.loadedHoldingSubLevels.containsKey(dep)) {
                    orphans.add(entry.getKey());
                    break;
                }
            }
        }

        if (!orphans.isEmpty()) {
            SableCat.LOGGER.debug("Pruning {} orphaned sub-level(s) with missing dependencies", orphans.size());
            orphans.forEach(this.loadedHoldingSubLevels::remove);
        }

    }
}