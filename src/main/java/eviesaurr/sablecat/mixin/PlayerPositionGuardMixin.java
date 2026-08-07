package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixEntry;
import eviesaurr.sablecat.fix.FixRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class PlayerPositionGuardMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void sablecat$clampToWorldBorder(CallbackInfo ci) {
        if (!FixRegistry.isEnabled("player-position-guard")) return;

        ServerPlayer self = (ServerPlayer) (Object) this;
        Vec3 pos = self.position();

        WorldBorder border = self.level().getWorldBorder();
        double minX = border.getMinX() + 5.0;
        double maxX = border.getMaxX() - 5.0;
        double minZ = border.getMinZ() + 5.0;
        double maxZ = border.getMaxZ() - 5.0;

        double minY = self.level().getMinBuildHeight() + 5.0;
        double yMaxMargin = sablecat$getDoubleOption("yMaxMargin", 1000.0);
        double maxY = self.level().getMaxBuildHeight() + yMaxMargin;
        boolean isCreative = self.isCreative();

        boolean outOfBounds = false;
        double clampedX = pos.x;
        double clampedY = pos.y;
        double clampedZ = pos.z;

        if (clampedX < minX) { clampedX = minX; outOfBounds = true; }
        if (clampedX > maxX) { clampedX = maxX; outOfBounds = true; }
        if (clampedZ < minZ) { clampedZ = minZ; outOfBounds = true; }
        if (clampedZ > maxZ) { clampedZ = maxZ; outOfBounds = true; }

        if (isCreative && clampedY < minY && !self.onGround()) { clampedY = minY; outOfBounds = true; }
        if (clampedY > maxY) { clampedY = maxY; outOfBounds = true; }

        if (outOfBounds) {
            SableCat.LOGGER.warn("Player {} was out of world bounds at ({}, {}, {}), clamping to ({}, {}, {})",
                    self.getName().getString(), pos.x, pos.y, pos.z, clampedX, clampedY, clampedZ);
            self.setPos(clampedX, clampedY, clampedZ);
            self.setDeltaMovement(new Vec3(0.0, 0.0, 0.0));
        }
    }

    @Unique
    private static double sablecat$getDoubleOption(String key, double defaultValue) {
        FixEntry entry = FixRegistry.getFix("player-position-guard");
        if (entry == null) return defaultValue;
        Object val = entry.getOption(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }
}