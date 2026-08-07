package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import dev.simulated_team.simulated.service.SimAssemblyService;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.simulated_team.simulated.util.assembly.SimAssemblyContraption", remap = false)
public abstract class SlimeBearingFixMixin {
    @Redirect(
        method = "moveBlock",
        at = @At(value = "INVOKE", target = "Ldev/simulated_team/simulated/service/SimAssemblyService;canStickTo(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z"),
        remap = false
    )
    private boolean sablecat$preventSlimeStickToBearing(SimAssemblyService instance, BlockState stateA, BlockState stateB) {
        boolean original = instance.canStickTo(stateA, stateB);

        if (!FixRegistry.isEnabled("aeronautics-slime-bearfix")) {
            return original;
        }

        if (!original) {
            return false;
        }

        if (isSwivelBearing(stateA) || isSwivelBearing(stateB)) {
            SableCat.LOGGER.debug("Slime-bearing fix: preventing stick between {} and {}", stateA, stateB);
            return false;
        }

        return true;
    }

    private static boolean isSwivelBearing(BlockState state) {
        String className = state.getBlock().getClass().getName();
        return className.contains("SwivelBearingBlock");
    }
}
