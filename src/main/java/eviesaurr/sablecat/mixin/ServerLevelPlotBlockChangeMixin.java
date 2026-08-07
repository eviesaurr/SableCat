package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot", remap = false)
public class ServerLevelPlotBlockChangeMixin {

    @Redirect(
        method = "onBlockChange",
        at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/block/BlockSubLevelLiftProvider;sable$getNormal(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/core/Direction;"),
        remap = false
    )
    private Direction sablecat$safeGetNormal(BlockSubLevelLiftProvider instance, BlockState state) {
        if (!FixRegistry.isEnabled("copycats-lift-compat")) {
            return instance.sable$getNormal(state);
        }
        try {
            return instance.sable$getNormal(state);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist in")) {
                SableCat.LOGGER.debug("Copycats compatibility: sable$getNormal failed for {} - returning UP as fallback", state, e);
                return Direction.UP;
            }
            throw e;
        }
    }
}
