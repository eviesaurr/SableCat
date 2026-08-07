package eviesaurr.sablecat.mixin;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.UUID;

/**
 * Exposes SubLevelHoldingChunkMap's private getOrLoadHoldingChunk/setDirty,
 * used by SubLevelPurge to locate a holding chunk and mark it dirty for
 * re-save after removing a dead pointer from it.
 * <p>
 * Also exposes moveAndSaveSubLevel(ServerSubLevel, ChunkPos, List) - needed
 * so SubLevelHoldingChunkMapMixin's chain-save guard can re-invoke the real
 * implementation from inside its own try/catch wrapper, since a mixin can't
 * directly call another class's private method even when targeting the same
 * class (the compiler doesn't know about the eventual bytecode merge).
 */
@Mixin(SubLevelHoldingChunkMap.class)
public interface SubLevelHoldingChunkMapInvoker {

    @Invoker("getOrLoadHoldingChunk")
    @Nullable SubLevelHoldingChunk sablecat$getOrLoadHoldingChunk(ChunkPos chunkPos, boolean create);

    @Invoker("setDirty")
    void sablecat$setDirty(ChunkPos chunkPos);

    @Invoker("moveAndSaveSubLevel")
    void sablecat$moveAndSaveSubLevel(ServerSubLevel subLevel, ChunkPos moveToChunk, List<UUID> uuids);
}