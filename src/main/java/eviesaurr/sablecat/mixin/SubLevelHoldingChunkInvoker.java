package eviesaurr.sablecat.mixin;

import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;
import java.util.UUID;

/**
 * Exposes SubLevelHoldingChunk's protected snatch(UUID), which removes a
 * sub-level from the chunk's tracked list and returns what was removed.
 * This is the same method Sable's own snatchAndLoad uses internally - reusing
 * it means a purge behaves identically to Sable's own removal path rather
 * than us inventing a second, possibly-inconsistent way to strip an entry.
 */
@Mixin(SubLevelHoldingChunk.class)
public interface SubLevelHoldingChunkInvoker {

    @Invoker("snatch")
    Collection<HoldingSubLevel> sablecat$snatch(UUID subLevelId);
}