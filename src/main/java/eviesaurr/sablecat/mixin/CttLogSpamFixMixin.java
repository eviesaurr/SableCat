package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.fix.FixRegistry;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "de.mrjulsen.ctt.CreateThreadedTrains", remap = false)
public class CttLogSpamFixMixin {

    private static final ConcurrentHashMap<String, Boolean> LOGGED_ERRORS = new ConcurrentHashMap<>();

    @Redirect(
        method = "postTick",
        at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Throwable;)V", remap = false),
        remap = false
    )
    private static void sablecat$suppressRepeatedWarn(Logger instance, String message, Throwable arg) {
        if (!FixRegistry.isEnabled("ctt-log-spam-fix")) {
            instance.warn(message, arg);
            return;
        }

        String key = "warn:" + (arg != null ? arg.getClass().getName() : "unknown");
        if (LOGGED_ERRORS.putIfAbsent(key, Boolean.TRUE) == null) {
            instance.warn(message + " (subsequent errors of this type will be suppressed)", arg);
        }
    }
}
