package eviesaurr.sablecat.mixin;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Train.class, remap = false)
public class TrainDetachNullEdgeMixin {

    @Inject(method = "lambda$detachFromTracks$22", at = @At("HEAD"), cancellable = true, remap = false)
    private void sablecat$skipNullEdgePoint(TravellingPoint tp, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("create-train-detach-nulledge-guard")) {
            return;
        }
        if (tp == null || tp.edge == null) {
            SableCat.LOGGER.warn("Train.detachFromTracks: skipping TravellingPoint with null edge (corrupted train state), migration skipped for this point");
            ci.cancel();
        }
    }
}
