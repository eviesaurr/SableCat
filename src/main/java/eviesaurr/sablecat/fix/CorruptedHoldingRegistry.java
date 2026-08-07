package eviesaurr.sablecat.fix;

import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of holding sub-levels that failed SubLevelSerializer.fullyLoad.
 * Populated by SubLevelHoldingChunkMapLoadCaptureMixin at the exact failure site,
 * consumed by /sablecat rescue.
 * <p>
 * Each captured entry is classified so admins can tell rescuable failures apart
 * from unrescuable ones:
 * - POSE_CORRUPTED: pose NBT contains NaN or absurdly out-of-range coordinates,
 *   but the plot still has block content. Rescuable by rewriting the pose.
 * - CONTENT_EMPTY: the plot NBT has no chunk content - the ship's blocks are
 *   gone from this record. NOT rescuable by moving it; nothing to move.
 * - OTHER: failed for a reason we couldn't classify (e.g. allocation failure).
 */
public final class CorruptedHoldingRegistry {

    public enum FailureKind { POSE_CORRUPTED, CONTENT_EMPTY, OTHER }

    public record CapturedFailure(UUID uuid, HoldingSubLevel holding, FailureKind kind,
                                  String detail, long capturedAtMs) {}

    /**
     * Coordinates beyond this are treated as corrupt. Deliberately far beyond the
     * plot grid: rotation_point legitimately holds plot-space coordinates ~20M+
     * blocks out, and position holds a world-minus-plot offset of similar scale -
     * huge values are NORMAL here. Only NaN or truly absurd magnitudes (like the
     * 5.7E9 bounds observed in genuinely corrupted entries) indicate corruption.
     */
    public static final double SANE_COORD_LIMIT = 100_000_000.0;

    private static final Map<UUID, CapturedFailure> captured = new ConcurrentHashMap<>();

    public static void capture(HoldingSubLevel holding) {
        UUID uuid = holding.data().uuid();
        FailureKind kind = classify(holding);
        String detail = describe(holding, kind);
        captured.put(uuid, new CapturedFailure(uuid, holding, kind, detail, System.currentTimeMillis()));
    }

    private static FailureKind classify(HoldingSubLevel holding) {
        CompoundTag tag = holding.data().fullTag();

        // Content check first: an empty plot is unrescuable regardless of pose.
        CompoundTag plotTag = tag.getCompound("plot");
        CompoundTag chunks = plotTag.getCompound("chunks");
        if (chunks.getAllKeys().isEmpty()) {
            return FailureKind.CONTENT_EMPTY;
        }

        CompoundTag poseTag = tag.getCompound("pose");
        if (isPoseInsane(poseTag)) {
            return FailureKind.POSE_CORRUPTED;
        }

        return FailureKind.OTHER;
    }

    /** True if any pose coordinate is NaN or beyond the sane world limit. */
    public static boolean isPoseInsane(CompoundTag poseTag) {
        return isVecInsane(poseTag.getCompound("position"))
                || isVecInsane(poseTag.getCompound("rotation_point"));
    }

    private static boolean isVecInsane(CompoundTag vec) {
        double x = vec.getDouble("x");
        double y = vec.getDouble("y");
        double z = vec.getDouble("z");
        return Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)
                || Math.abs(x) > SANE_COORD_LIMIT
                || Math.abs(y) > SANE_COORD_LIMIT
                || Math.abs(z) > SANE_COORD_LIMIT;
    }

    private static String describe(HoldingSubLevel holding, FailureKind kind) {
        CompoundTag tag = holding.data().fullTag();
        CompoundTag pos = tag.getCompound("pose").getCompound("position");
        int chunkCount = tag.getCompound("plot").getCompound("chunks").getAllKeys().size();
        String name = tag.contains("display_name") ? tag.getString("display_name") : "(unnamed)";
        return String.format("%s | pos=(%.1f, %.1f, %.1f) | %d plot chunk(s) | %s",
                name, pos.getDouble("x"), pos.getDouble("y"), pos.getDouble("z"), chunkCount, kind);
    }

    /** Insertion-ordered snapshot for listing. */
    public static Map<UUID, CapturedFailure> getAll() {
        return new LinkedHashMap<>(captured);
    }

    public static CapturedFailure get(UUID uuid) {
        return captured.get(uuid);
    }

    public static boolean remove(UUID uuid) {
        return captured.remove(uuid) != null;
    }

    public static void clear() {
        captured.clear();
    }
}