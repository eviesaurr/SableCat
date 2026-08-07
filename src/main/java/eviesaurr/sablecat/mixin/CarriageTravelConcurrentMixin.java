package eviesaurr.sablecat.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(value = Carriage.class, remap = false)
public abstract class CarriageTravelConcurrentMixin {

    @Shadow(remap = false)
    protected abstract void updateContraptionAnchors();

    @Inject(method = "manageEntities", at = @At("HEAD"), remap = false)
    private void sablecat$updateAnchorsBeforeManageEntities(Level level, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("ctt-concurrent-fix")) {
            return;
        }

        if (level.getServer() != null && !level.getServer().isSameThread()) {
            SableCat.LOGGER.warn("CTT concurrent fix: manageEntities called on non-main thread, skipping anchor update");
            return;
        }

        try {
            this.updateContraptionAnchors();
        } catch (Exception e) {
            SableCat.LOGGER.warn("CTT concurrent fix: error updating anchors before manageEntities", e);
        }
    }
}
