package eviesaurr.sablecat.mixin;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import top.ribs.scguns.network.message.S2CMessageTurretVisualSync;

@Mixin(value = S2CMessageTurretVisualSync.class, remap = false)
public interface S2CMessageTurretVisualSyncAccessor {

    @Accessor("pos")
    BlockPos sablecat$getPos();

    @Accessor("yaw")
    float sablecat$getYaw();

    @Accessor("pitch")
    float sablecat$getPitch();

    @Accessor("recoilPitchOffset")
    float sablecat$getRecoilPitchOffset();
}