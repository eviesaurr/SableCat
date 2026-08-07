package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLightEngine.class)
public class LightEngineBoundsGuardMixin {

    @Inject(method = "queueSectionData", at = @At("HEAD"), cancellable = true)
    private void sablecat$guardQueueSectionData(LightLayer layer, SectionPos pos, DataLayer data, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("light-engine-bounds-guard")) return;

        LevelLightEngine self = (LevelLightEngine) (Object) this;
        int sectionY = pos.getY();
        if (sectionY < self.getMinLightSection() || sectionY >= self.getMaxLightSection()) {
            SableCat.LOGGER.debug("Light engine bounds guard: skipping queueSectionData for out-of-bounds section Y={}", sectionY);
            ci.cancel();
        }
    }

    @Inject(method = "updateSectionStatus", at = @At("HEAD"), cancellable = true)
    private void sablecat$guardUpdateSectionStatus(SectionPos pos, boolean notEmpty, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("light-engine-bounds-guard")) return;

        LevelLightEngine self = (LevelLightEngine) (Object) this;
        int sectionY = pos.getY();
        if (sectionY < self.getMinLightSection() || sectionY >= self.getMaxLightSection()) {
            SableCat.LOGGER.debug("Light engine bounds guard: skipping updateSectionStatus for out-of-bounds section Y={}", sectionY);
            ci.cancel();
        }
    }
}
