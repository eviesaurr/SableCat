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
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(targets = "tschipp.carryon.common.carry.PlacementHandler", remap = false)
public class PlacementHandlerMixin {

    @ModifyVariable(
        method = "tryPlaceEntity",
        at = @At(value = "STORE", ordinal = 0),
        ordinal = 0,
        remap = false
    )
    private static Vec3 sablecat$projectPlacementPos(Vec3 placementPos, ServerPlayer player) {
        if (!FixRegistry.isEnabled("carryon-compat")) return placementPos;

        try {
            var level = player.serverLevel();

            // 方法1：使用 Sable 的 projectOutOfSubLevel
            Vec3 projected = Sable.HELPER.projectOutOfSubLevel(level, placementPos);
            if (!projected.equals(placementPos)) {
                SableCat.LOGGER.info("CarryOn compat: projected placement pos via getContaining from ({}, {}, {}) to ({}, {}, {})",
                    placementPos.x, placementPos.y, placementPos.z, projected.x, projected.y, projected.z);
                return projected;
            }

            // 方法2：遍历所有 sub-level，检查目标坐标是否在 plot 的局部坐标范围内
            if (!(level instanceof SubLevelContainerHolder holder)) return placementPos;

            SubLevelContainer container = holder.sable$getPlotContainer();
            if (container == null) return placementPos;

            List<? extends SubLevel> subLevels = container.getAllSubLevels();
            for (SubLevel subLevel : subLevels) {
                LevelPlot plot = subLevel.getPlot();
                var plotBounds = plot.getBoundingBox();

                double x = placementPos.x;
                double y = placementPos.y;
                double z = placementPos.z;

                if (x >= plotBounds.minX() - 1 && x <= plotBounds.maxX() + 2 &&
                    y >= plotBounds.minY() - 1 && y <= plotBounds.maxY() + 2 &&
                    z >= plotBounds.minZ() - 1 && z <= plotBounds.maxZ() + 2) {

                    Vec3 globalPos = JOMLConversion.toMojang(subLevel.logicalPose().transformPosition(JOMLConversion.toJOML(placementPos)));
                    SableCat.LOGGER.info("CarryOn compat: projected placement pos via plot scan from ({}, {}, {}) to ({}, {}, {})",
                        x, y, z, globalPos.x, globalPos.y, globalPos.z);
                    return globalPos;
                }
            }
        } catch (Exception e) {
            SableCat.LOGGER.error("CarryOn compat: error projecting placement position, using original", e);
        }

        return placementPos;
    }
}
