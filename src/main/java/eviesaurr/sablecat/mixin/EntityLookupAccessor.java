package eviesaurr.sablecat.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/**
 * Accessor for EntityLookup's two internal maps, used by
 * PersistentEntitySectionManagerStopTrackingGuardMixin to rebuild the
 * corrupted byId map from the healthy byUuid map.
 * <p>
 * EntityLookup keeps the same entities in two parallel maps:
 * - byId: Int2ObjectLinkedOpenHashMap (the one Sable corrupts - its internal
 *   prev/next linked-list pointers get broken by concurrent modification)
 * - byUuid: a plain HashMap (no linked-pointer state, not affected by the
 *   same corruption)
 * <p>
 * That redundancy is what makes in-place repair possible: a fresh byId can be
 * reconstructed entirely from byUuid's contents.
 */
@Mixin(EntityLookup.class)
public interface EntityLookupAccessor<T extends EntityAccess> {

    @Accessor("byId")
    Int2ObjectMap<T> sablecat$getById();

    @Mutable
    @Accessor("byId")
    void sablecat$setById(Int2ObjectMap<T> byId);

    @Accessor("byUuid")
    Map<UUID, T> sablecat$getByUuid();
}