package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PersistentEntitySectionManager.class)
public class PersistentEntitySectionManagerStopTrackingGuardMixin {

    @Redirect(
            method = "stopTracking",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityLookup;remove(Lnet/minecraft/world/level/entity/EntityAccess;)V"),
            remap = false
    )
    private void sablecat$safeEntityLookupRemove(EntityLookup<EntityAccess> instance, EntityAccess entity) {
        if (!FixRegistry.isEnabled("entity-lookup-remove-guard")) {
            if (entity != null) instance.remove(entity);
            return;
        }

        try {
            if (entity != null) instance.remove(entity);
        } catch (ArrayIndexOutOfBoundsException e) {
            SableCat.LOGGER.warn(
                    "EntityLookup.remove hit AIOOBE for entity {} (Int2ObjectLinkedOpenHashMap state corrupted by Sable, see Sable issue tracker). Attempting in-place repair...",
                    entity, e);
            sablecat$repairAndRetry(instance, entity);
        } catch (Throwable t) {
            SableCat.LOGGER.warn("EntityLookup.remove skipped unexpected error for entity {}", entity, t);
        }
    }

    /**
     * Rebuilds the corrupted byId map from the healthy byUuid map, then retries
     * the removal that originally failed.
     * <p>
     * byUuid (plain HashMap) holds the same entity set as byId but has no
     * linked-pointer state to corrupt, so it can serve as the source of truth.
     * A brand-new Int2ObjectLinkedOpenHashMap is populated from it and swapped
     * in, replacing the broken map - clearing the corruption entirely instead
     * of leaving it resident.
     */
    @SuppressWarnings("unchecked")
    private static void sablecat$repairAndRetry(EntityLookup<EntityAccess> instance, EntityAccess entity) {
        try {
            EntityLookupAccessor<EntityAccess> accessor = (EntityLookupAccessor<EntityAccess>) instance;

            int before = accessor.sablecat$getById().size();

            Int2ObjectMap<EntityAccess> rebuilt = new Int2ObjectLinkedOpenHashMap<>();
            for (EntityAccess e : accessor.sablecat$getByUuid().values()) {
                if (e != null) {
                    rebuilt.put(e.getId(), e);
                }
            }
            accessor.sablecat$setById(rebuilt);

            SableCat.LOGGER.info(
                    "EntityLookup byId map rebuilt from byUuid ({} -> {} entries). Retrying removal of entity {}.",
                    before, rebuilt.size(), entity);

            // Retry the original removal against the now-healthy map.
            if (entity != null) instance.remove(entity);
        } catch (Throwable repairError) {
            // Repair itself failed - fall back to the old behavior (swallow, keep server alive).
            SableCat.LOGGER.error(
                    "EntityLookup in-place repair failed; falling back to skipping removal of entity {}. Corruption remains resident - a server restart is recommended.",
                    entity, repairError);
        }
    }
}