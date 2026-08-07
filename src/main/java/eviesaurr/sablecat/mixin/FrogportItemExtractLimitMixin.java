package eviesaurr.sablecat.mixin;

import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.simibubi.create.foundation.item.ItemHelper;
import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;

@Mixin(FrogportBlockEntity.class)
public class FrogportItemExtractLimitMixin {

    private static final int MAX_SLOTS = 256;
    private static final long SLOT_WARN_INTERVAL_NS = 60_000_000_000L; // 60s
    private static volatile long sablecat$lastWarnTime = 0L;

    @Redirect(
        method = "tryPullingFrom",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/item/ItemHelper;extract(Lnet/neoforged/neoforge/items/IItemHandler;Ljava/util/function/Predicate;Z)Lnet/minecraft/world/item/ItemStack;")
    )
    private ItemStack sablecat$limitExtractSlots(IItemHandler handler, Predicate<ItemStack> predicate, boolean copy) {
        if (!FixRegistry.isEnabled("frogport-extract-limit")) {
            return ItemHelper.extract(handler, predicate, copy);
        }

        int slots;
        try {
            slots = handler.getSlots();
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }

        if (slots <= MAX_SLOTS) {
            return ItemHelper.extract(handler, predicate, copy);
        }

        long now = System.nanoTime();
        long last = sablecat$lastWarnTime;
        if (now - last > SLOT_WARN_INTERVAL_NS) {
            sablecat$lastWarnTime = now;
            SableCat.LOGGER.warn(
                "Frogport adjacent inventory has {} slots (limit {}), skipping extract to prevent server freeze. Reduce inventory size or move frogport.",
                slots, MAX_SLOTS
            );
        }
        return ItemStack.EMPTY;
    }
}
