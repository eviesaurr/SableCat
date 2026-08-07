package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheBlockChangedMixin {

    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    private ChunkHolder getVisibleChunkIfPresent(long pos) {
        throw new AssertionError();
    }

    /**
     * @author SableCat
     * @reason Fix crash when plot holder doesn't exist, and fix ghost blocks from previous @Overwrite
     */
    @Overwrite
    public void blockChanged(BlockPos blockPos) {
        SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container != null) {
            ChunkPos pos = new ChunkPos(blockPos);
            if (container.inBounds(pos)) {
                PlotChunkHolder holder = container.getChunkHolder(pos);

                if (holder == null) {
                    SableCat.LOGGER.debug("Plot holder not found for block change at {}, skipping", blockPos);
                    return;
                }

                holder.blockChanged(blockPos);
                return;
            }
        }

        int cx = SectionPos.blockToSectionCoord(blockPos.getX());
        int cz = SectionPos.blockToSectionCoord(blockPos.getZ());
        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(cx, cz));
        if (holder != null) {
            holder.blockChanged(blockPos);
        }
    }
}
