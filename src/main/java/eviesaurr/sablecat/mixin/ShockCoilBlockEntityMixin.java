package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.compat.SableCoordinateBridge;
import eviesaurr.sablecat.fix.FixRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.config.ShockCoilConfig;
import top.ribs.scguns.blockentity.ShockCoilBlockEntity;

@Mixin(value = ShockCoilBlockEntity.class, remap = false)
public abstract class ShockCoilBlockEntityMixin {

    @ModifyVariable(method = "serverTick", at = @At("STORE"), ordinal = 0)
    private static Vec3 sablecat$transformCoilCenterToWorldSpace(Vec3 coilCenter, Level level, BlockPos pos,
                                                                 BlockState state, ShockCoilBlockEntity blockEntity) {
        if (!FixRegistry.isEnabled("shock-coil-coordinate-fix")) return coilCenter;
        return SableCoordinateBridge.projectToWorldSpace(level, coilCenter);
    }

    @Inject(method = "zapTarget", at = @At("HEAD"), cancellable = true)
    private void sablecat$rejectAbsurdZapDistance(LivingEntity target, Vec3 start, Vec3 end, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("shock-coil-coordinate-fix")) return;

        double actualDistSqr = start.distanceToSqr(end);
        double maxAllowedRange = ShockCoilConfig.getZapRange() * 4.0D; // +- some tolerance because I'm nice
        double maxAllowedDistSqr = maxAllowedRange * maxAllowedRange;
        if (actualDistSqr > maxAllowedDistSqr) {
            SableCat.LOGGER.warn(
                    "SableCat: blocked a shock coil zap with an absurd distance ({} blocks, likely a Sable coordinate-space mismatch) - cancelling to prevent a crash",
                    Math.sqrt(actualDistSqr)
            );
            ci.cancel();
        }
    }
}