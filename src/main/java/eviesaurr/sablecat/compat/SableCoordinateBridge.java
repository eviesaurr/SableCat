package eviesaurr.sablecat.compat;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SableCoordinateBridge {

    private SableCoordinateBridge() {
    }

    public static Vec3 projectToWorldSpace(Level level, Vec3 position) {
        try {
            return Sable.HELPER.projectOutOfSubLevel(level, position);
        } catch (RuntimeException e) {
            return position;
        }
    }

    public static Vec3 worldToLocalDirection(Level level, BlockPos pos, Vec3 worldDirection) {
        try {
            SubLevelAccess access = SableCompanion.INSTANCE.getContaining(level, pos);
            return access != null ? access.logicalPose().transformNormalInverse(worldDirection) : worldDirection;
        } catch (RuntimeException e) {
            return worldDirection;
        }
    }
}