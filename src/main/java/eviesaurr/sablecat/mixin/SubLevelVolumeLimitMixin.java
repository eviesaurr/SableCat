package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

@Mixin(SubLevelAssemblyHelper.class)
public class SubLevelVolumeLimitMixin {

    private static final int SABLECAT$MAX_BLOCK_COUNT = 8192; // 16*16*16 = 4096, doubled for headroom

    @Inject(method = "assembleBlocks", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sablecat$checkVolumeLimit(ServerLevel level, BlockPos anchor,
                                                    Iterable<BlockPos> blocks, BoundingBox3ic bounds,
                                                    CallbackInfoReturnable<ServerSubLevel> cir) {
        if (!FixRegistry.isEnabled("sublevel-volume-limit")) return;

        int count = 0;
        for (BlockPos ignored : blocks) {
            count++;
            if (count > SABLECAT$MAX_BLOCK_COUNT) {
                SableCat.LOGGER.warn("SubLevel assembly rejected: block count {} exceeds limit {}. Anchor: {}",
                    count, SABLECAT$MAX_BLOCK_COUNT, anchor);
                cir.setReturnValue(null);
                return;
            }
        }
    }
}
