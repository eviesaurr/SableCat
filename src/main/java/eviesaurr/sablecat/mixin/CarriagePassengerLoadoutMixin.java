package eviesaurr.sablecat.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import eviesaurr.sablecat.fix.FixRegistry;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.Set;

@Mixin(value = Carriage.DimensionalCarriageEntity.class, remap = false)
public abstract class CarriagePassengerLoadoutMixin {

    @Shadow(remap = false)
    Carriage this$0;

    @Redirect(
        method = "updatePassengerLoadout",
        remap = false,
        at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;", remap = false)
    )
    private Set<Map.Entry<Integer, CompoundTag>> sablecat$useSnapshotEntrySet(Map<Integer, CompoundTag> instance) {
        if (!FixRegistry.isEnabled("ctt-concurrent-fix")) {
            return instance.entrySet();
        }

        return new java.util.HashMap<>(instance).entrySet();
    }
}
