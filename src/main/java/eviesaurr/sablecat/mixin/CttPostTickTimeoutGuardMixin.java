package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


@Mixin(targets = "de.mrjulsen.ctt.CreateThreadedTrains", remap = false)
public class CttPostTickTimeoutGuardMixin {

    private static final long TIMEOUT_SECONDS = 10;

    @Redirect(
        method = "postTick",
        at = @At(value = "INVOKE", target = "Ljava/util/concurrent/Future;get()Ljava/lang/Object;", remap = false),
        remap = false
    )
    private static Object sablecat$getWithTimeout(Future<?> future) throws InterruptedException, ExecutionException {
        if (!FixRegistry.isEnabled("ctt-posttick-timeout-guard")) {
            return future.get();
        }
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            SableCat.LOGGER.warn("CTT train worker task timed out after {}s, cancelled to prevent server freeze", TIMEOUT_SECONDS);
            return null;
        }
    }
}
