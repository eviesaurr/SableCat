package eviesaurr.sablecat.mixin;

import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackEdge;
import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.Map;

@Mixin(value = TrackGraph.class, remap = false)
public class TrackGraphNullConnectionsMixin {

    @Inject(method = "getConnectionsFrom", at = @At("HEAD"), cancellable = true, remap = false)
    private void sablecat$returnEmptyForNullNode(TrackNode node,
                                                  CallbackInfoReturnable<Map<TrackNode, TrackEdge>> cir) {
        if (!FixRegistry.isEnabled("create-trackgraph-null-guard")) {
            return;
        }
        if (node == null) {
            SableCat.LOGGER.debug("TrackGraph.getConnectionsFrom called with null node, returning empty map");
            cir.setReturnValue(Collections.emptyMap());
        }
    }
}
