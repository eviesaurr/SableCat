package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.mehvahdjukaar.vista.common.chunk_tracking.ServerCameraChunkManager", remap = false)
public class VistaCameraChunkFixMixin {

    @Redirect(
            method = "onServerPlayerTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/mehvahdjukaar/vista/common/chunk_tracking/ServerCameraChunkManager;setChunksForceLoaded(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;IZ)V"),
            remap = false
    )
    private static void sablecat$projectAndForceLoadOnAdd(ServerLevel level, BlockPos pos, int radius, boolean force) {
        sablecat$setChunksForceLoadedProjected(level, pos, radius, force);
    }

    @Redirect(
            method = "updateVfReferences",
            at = @At(value = "INVOKE",
                    target = "Lnet/mehvahdjukaar/vista/common/chunk_tracking/ServerCameraChunkManager;setChunksForceLoaded(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;IZ)V"),
            remap = false
    )
    private static void sablecat$projectAndForceLoadOnRemove(ServerLevel level, BlockPos pos, int radius, boolean force) {
        sablecat$setChunksForceLoadedProjected(level, pos, radius, force);
    }

    private static void sablecat$setChunksForceLoadedProjected(ServerLevel level, BlockPos pos, int radius, boolean force) {
        if (FixRegistry.isEnabled("vista-camera-chunk-fix")) {
            try {
                Vec3 projected = SableCompanion.INSTANCE.projectOutOfSubLevel(level, Vec3.atLowerCornerOf(pos));
                BlockPos newPos = BlockPos.containing(projected);
                if (!newPos.equals(pos)) {
                    SableCat.LOGGER.debug("Vista camera chunk fix: projected ViewFinder {} -> {}", pos, newPos);
                    pos = newPos;
                }
            } catch (Exception e) {
                SableCat.LOGGER.debug("Vista camera chunk fix: failed to project position", e);
            }
        }

        ChunkPos cp = new ChunkPos(pos);
        ChunkPos.rangeClosed(cp, radius)
                .filter(p -> p.distanceSquared(cp) <= radius * radius)
                .forEach(p -> level.setChunkForced(p.x, p.z, force));
    }
}
