package eviesaurr.sablecat.fix;

import eviesaurr.sablecat.SableCat;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public final class SubLevelRescue {

    public record RescueResult(boolean success, String message) {}

    public static RescueResult rescue(ServerLevel level, UUID uuid, double x, double y, double z) {
        CorruptedHoldingRegistry.CapturedFailure failure = CorruptedHoldingRegistry.get(uuid);
        if (failure == null) {
            return new RescueResult(false, "No captured failure with UUID " + uuid);
        }
        if (failure.kind() == CorruptedHoldingRegistry.FailureKind.CONTENT_EMPTY) {
            return new RescueResult(false,
                    "Sub-level " + uuid + " has NO plot block content (CONTENT_EMPTY) - its blocks are gone from this record. "
                            + "Moving it cannot restore them. Not rescuing.");
        }

        HoldingSubLevel holding = failure.holding();
        CompoundTag tag = holding.data().fullTag();
        CompoundTag oldPose = tag.getCompound("pose");
        CompoundTag rotPoint = oldPose.getCompound("rotation_point");
        double rpx = rotPoint.getDouble("x");
        double rpy = rotPoint.getDouble("y");
        double rpz = rotPoint.getDouble("z");

        if (Double.isNaN(rpx) || Double.isNaN(rpy) || Double.isNaN(rpz)) {
            return new RescueResult(false,
                    "rotation_point is NaN - the plot anchor itself is corrupted. Auto-rescue can't safely "
                            + "reconstruct it yet (needs plot-grid math from plot_x/plot_z). Not rescuing.");
        }

        CompoundTag poseTag = new CompoundTag();
        poseTag.put("position", writeVec(x, y, z));
        poseTag.put("rotation_point", writeVec(rpx, rpy, rpz)); // preserved - plot space
        CompoundTag orientation = new CompoundTag();
        orientation.putDouble("x", 0.0);
        orientation.putDouble("y", 0.0);
        orientation.putDouble("z", 0.0);
        orientation.putDouble("w", 1.0);
        poseTag.put("orientation", orientation);
        tag.put("pose", poseTag);

        // Zero any stored velocity so it doesn't shoot off on arrival.
        if (tag.contains("linear_velocity")) tag.put("linear_velocity", writeVec(0, 0, 0));
        if (tag.contains("angular_velocity")) tag.put("angular_velocity", writeVec(0, 0, 0));

        SableCat.LOGGER.info("Rescue: rewrote pose of {} to ({}, {}, {}), re-attempting load", uuid, x, y, z);

        try {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) {
                return new RescueResult(false, "No sub-level container for this dimension.");
            }
            container.getHoldingChunkMap().loadHoldingSubLevel(holding);
        } catch (Exception e) {
            SableCat.LOGGER.error("Rescue: re-load attempt threw for {}", uuid, e);
            return new RescueResult(false, "Re-load threw an exception (see log): " + e.getMessage());
        }

        CorruptedHoldingRegistry.CapturedFailure after = CorruptedHoldingRegistry.get(uuid);
        if (after != null && after.capturedAtMs() > failure.capturedAtMs()) {
            return new RescueResult(false,
                    "Pose rewritten, but the load STILL failed (re-captured). Failure kind now: " + after.kind()
                            + ". The corruption goes deeper than the pose.");
        }

        CorruptedHoldingRegistry.remove(uuid);
        return new RescueResult(true,
                "Sub-level " + uuid + " rescued to (" + x + ", " + y + ", " + z + ") and loaded successfully.");
    }

    private static CompoundTag writeVec(double x, double y, double z) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        return tag;
    }
}