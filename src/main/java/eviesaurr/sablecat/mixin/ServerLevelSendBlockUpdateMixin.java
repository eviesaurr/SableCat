package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerLevelSendBlockUpdateMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), cancellable = true)
    private void sablecat$skipIfPlotHolderMissing(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("plot-holder-guard")) return;

        ServerLevel level = (ServerLevel) (Object) this;
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        ChunkPos chunkPos = new ChunkPos(pos);
        if (!container.inBounds(chunkPos)) return;

        PlotChunkHolder holder = container.getChunkHolder(chunkPos);
        if (holder == null) {
            SableCat.LOGGER.debug("Skipping sendBlockUpdated at {}: plot holder missing", pos);
            ci.cancel();
        }
    }
}
