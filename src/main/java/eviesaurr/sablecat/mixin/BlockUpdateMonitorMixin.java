package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.debug.BlockUpdateMonitor;
import eviesaurr.sablecat.i18n.LanguageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("null")
@Mixin(ServerLevel.class)
public class BlockUpdateMonitorMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void sablecat$onBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        BlockUpdateMonitor.MonitorEntry monitor = BlockUpdateMonitor.getMonitor(pos);
        if (monitor == null) return;

        ServerPlayer player = monitor.getPlayer();
        if (player == null || !player.isAlive()) {
            BlockUpdateMonitor.stopMonitoring(pos);
            return;
        }

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StringBuilder traceBuilder = new StringBuilder();
        traceBuilder.append(LanguageManager.get("monitor.blockupdate-header")).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.position", pos.toShortString())).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.old-state", oldState)).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.new-state", newState)).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.flags", flags)).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.dimension", ((ServerLevel) (Object) this).dimension().location())).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.call-chain")).append("\n");

        for (int i = 3; i < Math.min(stackTrace.length, 30); i++) {
            traceBuilder.append("  at ").append(stackTrace[i].toString()).append("\n");
        }
        if (stackTrace.length > 30) {
            traceBuilder.append(LanguageManager.get("monitor.more-frames", stackTrace.length - 30)).append("\n");
        }

        String traceText = traceBuilder.toString();

        Component message = Component.literal(LanguageManager.get("monitor.blockupdate-title", pos.toShortString()))
            .withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, traceText))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(LanguageManager.get("monitor.click-to-copy"))))
            );

        player.sendSystemMessage(message);

        SableCat.LOGGER.info("[fs2temp] Block update at {}: {} -> {}, flags={}", pos, oldState, newState, flags);
    }
}
