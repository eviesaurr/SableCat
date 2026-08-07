package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixEntry;
import eviesaurr.sablecat.fix.FixRegistry;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(SubLevelPhysicsSystem.class)
public class PhysicsStaffDragClipFixMixin {

    @Shadow(remap = false)
    private ServerLevel level;

    private static final Set<UUID> CLAMPED_SUBLEVELS = ConcurrentHashMap.newKeySet();

    @Inject(method = "updatePose", at = @At("TAIL"), remap = false)
    private void sablecat$clampSubLevelToWorldBounds(ServerSubLevel serverSubLevel, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("physics-staff-drag-clipfix")) return;

        Vector3d position = serverSubLevel.logicalPose().position();
        WorldBorder border = this.level.getWorldBorder();

        double minX = border.getMinX() + 1.0;
        double maxX = border.getMaxX() - 1.0;
        double minZ = border.getMinZ() + 1.0;
        double maxZ = border.getMaxZ() - 1.0;

        double minY = this.level.getMinBuildHeight() + 1.0;
        double yMaxMargin = sablecat$getDoubleOption("yMaxMargin", 1000.0);
        double maxY = this.level.getMaxBuildHeight() + yMaxMargin;

        boolean outOfBounds = false;
        double clampedX = position.x;
        double clampedY = position.y;
        double clampedZ = position.z;

        if (clampedX < minX) { clampedX = minX; outOfBounds = true; }
        if (clampedX > maxX) { clampedX = maxX; outOfBounds = true; }
        if (clampedZ < minZ) { clampedZ = minZ; outOfBounds = true; }
        if (clampedZ > maxZ) { clampedZ = maxZ; outOfBounds = true; }
        if (clampedY < minY) { clampedY = minY; outOfBounds = true; }
        if (clampedY > maxY) { clampedY = maxY; outOfBounds = true; }

        UUID subLevelId = serverSubLevel.getUniqueId();

        if (outOfBounds) {
            position.set(clampedX, clampedY, clampedZ);

            serverSubLevel.latestLinearVelocity.zero();
            serverSubLevel.latestAngularVelocity.zero();

            if (CLAMPED_SUBLEVELS.add(subLevelId)) {
                SableCat.LOGGER.warn("SubLevel {} is out of world bounds, clamping position. Subsequent clamps will be silent.",
                    subLevelId);
            }
        } else {
            CLAMPED_SUBLEVELS.remove(subLevelId);
        }
    }

    @Unique
    private static double sablecat$getDoubleOption(String key, double defaultValue) {
        FixEntry entry = FixRegistry.getFix("physics-staff-drag-clipfix");
        if (entry == null) return defaultValue;
        Object val = entry.getOption(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }
}
