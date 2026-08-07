package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityTeleportMixin {

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void sablecat$projectSubLevelPosition(double x, double y, double z, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("carryon-compat")) return;

        Entity self = (Entity) (Object) this;
        if (!(self instanceof ServerPlayer player)) return;

        Level level = self.level();
        if (level == null) return;

        Vec3 targetPos = new Vec3(x, y, z);

        try {
            Vec3 projected = Sable.HELPER.projectOutOfSubLevel(level, targetPos);
            if (!projected.equals(targetPos)) {
                SableCat.LOGGER.debug("CarryOn compat: projected teleport via getContaining from ({}, {}, {}) to ({}, {}, {})",
                    x, y, z, projected.x, projected.y, projected.z);
                self.setPos(projected.x, projected.y, projected.z);
                ci.cancel();
                return;
            }

            if (!(level instanceof SubLevelContainerHolder holder)) return;

            SubLevelContainer container = holder.sable$getPlotContainer();
            if (container == null) return;

            List<? extends SubLevel> subLevels = container.getAllSubLevels();
            for (SubLevel subLevel : subLevels) {
                LevelPlot plot = subLevel.getPlot();
                var plotBounds = plot.getBoundingBox();

                if (x >= plotBounds.minX() - 1 && x <= plotBounds.maxX() + 2 &&
                    y >= plotBounds.minY() - 1 && y <= plotBounds.maxY() + 2 &&
                    z >= plotBounds.minZ() - 1 && z <= plotBounds.maxZ() + 2) {

                    Vec3 globalPos = JOMLConversion.toMojang(subLevel.logicalPose().transformPosition(JOMLConversion.toJOML(targetPos)));
                    SableCat.LOGGER.debug("CarryOn compat: projected teleport via plot scan from ({}, {}, {}) to ({}, {}, {})",
                        x, y, z, globalPos.x, globalPos.y, globalPos.z);
                    self.setPos(globalPos.x, globalPos.y, globalPos.z);
                    ci.cancel();
                    return;
                }
            }
        } catch (Exception e) {
            SableCat.LOGGER.error("CarryOn compat: error projecting teleport position, using original", e);
        }
    }
}
