package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ParticleEngine.class)
public abstract class EffortlessParticleFixMixin {

    @Shadow
    private ClientLevel level;

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    private void sablecat$checkChunkLoadedDestroy(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("effortless-particle-fix")) {
            return;
        }

        if (this.level == null) {
            return;
        }

        if (!this.level.hasChunkAt(pos)) {
            SableCat.LOGGER.debug("Effortless particle fix: skipping destroy particle at {} (chunk not loaded)", pos);
            ci.cancel();
        }
    }

    @Inject(method = "crack", at = @At("HEAD"), cancellable = true)
    private void sablecat$checkChunkLoadedCrack(BlockPos pos, net.minecraft.core.Direction direction, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("effortless-particle-fix")) {
            return;
        }

        if (this.level == null) {
            return;
        }

        if (!this.level.hasChunkAt(pos)) {
            SableCat.LOGGER.debug("Effortless particle fix: skipping crack particle at {} (chunk not loaded)", pos);
            ci.cancel();
        }
    }
}
