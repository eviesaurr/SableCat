package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.fix.FixRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Mixin(targets = "dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage", remap = false)
public class SubLevelStorageLogSpamMixin {

    private static final long LOG_INTERVAL_NS = TimeUnit.SECONDS.toNanos(60);
    private static final int MAX_TRACKED_KEYS = 1024;
    private static final ConcurrentHashMap<String, Long> sablecat$logTimestamps = new ConcurrentHashMap<>();

    @Redirect(
        method = "attemptLoadSubLevel",
        at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"),
        remap = false
    )
    private void sablecat$throttleLog(Logger logger, String format, Object arg1, Object arg2) {
        if (!FixRegistry.isEnabled("sublevel-load-log-spam-fix")) {
            logger.error(format, arg1, arg2);
            return;
        }

        // arg1 = subLevelIndex (Short), arg2 = chunkPos (ChunkPos)
        String key = String.valueOf(arg2) + ":" + arg1;
        long now = System.nanoTime();
        Long last = sablecat$logTimestamps.get(key);
        if (last != null && (now - last) < LOG_INTERVAL_NS) {
            return;
        }
        sablecat$logTimestamps.put(key, now);

        if (sablecat$logTimestamps.size() > MAX_TRACKED_KEYS) {
            sablecat$logTimestamps.clear();
            sablecat$logTimestamps.put(key, now);
        }

        logger.error(format, arg1, arg2);
    }
}
