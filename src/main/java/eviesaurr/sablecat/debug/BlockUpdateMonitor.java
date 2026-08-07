package eviesaurr.sablecat.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class BlockUpdateMonitor {

    private static final Map<BlockPos, MonitorEntry> monitors = new ConcurrentHashMap<>();

    public static boolean isMonitoring(BlockPos pos) {
        return monitors.containsKey(pos);
    }

    public static void startMonitoring(BlockPos pos, ServerPlayer player) {
        monitors.put(pos.immutable(), new MonitorEntry(player));
    }

    public static void stopMonitoring(BlockPos pos) {
        monitors.remove(pos);
    }

    public static void stopAll() {
        monitors.clear();
    }

    public static MonitorEntry getMonitor(BlockPos pos) {
        return monitors.get(pos);
    }

    public static Set<Map.Entry<BlockPos, MonitorEntry>> getAllMonitors() {
        return monitors.entrySet();
    }

    public static class MonitorEntry {
        private final ServerPlayer player;
        private final long startTime;

        public MonitorEntry(ServerPlayer player) {
            this.player = player;
            this.startTime = System.currentTimeMillis();
        }

        public ServerPlayer getPlayer() {
            return player;
        }

        public long getStartTime() {
            return startTime;
        }
    }
}
