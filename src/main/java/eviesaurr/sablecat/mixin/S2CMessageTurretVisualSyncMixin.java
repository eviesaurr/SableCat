package eviesaurr.sablecat.mixin;

//import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.blockentity.TurretBlockEntity;
import com.mrcrayfish.framework.api.network.MessageContext;
import top.ribs.scguns.network.message.S2CMessageTurretVisualSync;

@Mixin(S2CMessageTurretVisualSync.class)
public abstract class S2CMessageTurretVisualSyncMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sablecat$handleWithoutChunkLoadedGate(S2CMessageTurretVisualSync message,
                                                              MessageContext context, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("turret-visual-sync-fix")) {
            return;
        }
        ci.cancel();

        S2CMessageTurretVisualSyncAccessor accessor = (S2CMessageTurretVisualSyncAccessor) message;

        context.execute(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            BlockEntity blockEntity = level.getBlockEntity(accessor.sablecat$getPos());

//            SableCat.LOGGER.info(
//                    "SableCat DEBUG: turret visual sync client handle - pos={} blockEntity={}",
//                    accessor.sablecat$getPos(), blockEntity
//            );

            if (blockEntity instanceof TurretBlockEntity turret) {
                turret.applyRemoteVisualState(
                        accessor.sablecat$getYaw(),
                        accessor.sablecat$getPitch(),
                        accessor.sablecat$getRecoilPitchOffset()
                );
            }
        });
        context.setHandled(true);
    }
}