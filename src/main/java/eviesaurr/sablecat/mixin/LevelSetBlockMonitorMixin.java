package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.debug.BlockUpdateMonitor;
import eviesaurr.sablecat.i18n.LanguageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings({"null", "resource"})
@Mixin(Level.class)
public class LevelSetBlockMonitorMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void sablecat$onSetBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        BlockUpdateMonitor.MonitorEntry monitor = BlockUpdateMonitor.getMonitor(pos);
        if (monitor == null) return;

        Level self = (Level) (Object) this;
        if (self.isClientSide()) return;

        ServerPlayer player = monitor.getPlayer();
        if (player == null || !player.isAlive()) {
            BlockUpdateMonitor.stopMonitoring(pos);
            return;
        }

        BlockState oldState = self.getBlockState(pos);

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StringBuilder traceBuilder = new StringBuilder();
        traceBuilder.append(LanguageManager.get("monitor.setblock-header")).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.position", pos.toShortString())).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.old-state", oldState)).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.new-state", state)).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.flags", flags)).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.max-depth", maxUpdateDepth)).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.dimension", self.dimension().location())).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.call-chain")).append("\n");

        for (int i = 3; i < Math.min(stackTrace.length, 30); i++) {
            traceBuilder.append("  at ").append(stackTrace[i].toString()).append("\n");
        }
        if (stackTrace.length > 30) {
            traceBuilder.append(LanguageManager.get("monitor.more-frames", stackTrace.length - 30)).append("\n");
        }

        String traceText = traceBuilder.toString();

        Component message = Component.literal(LanguageManager.get("monitor.setblock-title", pos.toShortString()))
            .withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, traceText))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(LanguageManager.get("monitor.click-to-copy"))))
            );

        player.sendSystemMessage(message);
        SableCat.LOGGER.info("[fs2temp] setBlock at {}: {} -> {}, flags={}", pos, oldState, state, flags);
    }
}
