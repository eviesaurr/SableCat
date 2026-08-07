package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockPlaceContext.class)
public abstract class CommandBlockPlaceMixin {

    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void sablecat$preventCommandBlockOnSubLevel(CallbackInfoReturnable<Boolean> cir) {
        if (!FixRegistry.isEnabled("command-block-sublevel-fix")) return;

        BlockPlaceContext self = (BlockPlaceContext) (Object) this;

        var heldItem = self.getItemInHand();
        if (!(heldItem.getItem() instanceof BlockItem blockItem)) return;

        var block = blockItem.getBlock();
        if (block != Blocks.COMMAND_BLOCK
                && block != Blocks.REPEATING_COMMAND_BLOCK
                && block != Blocks.CHAIN_COMMAND_BLOCK) {
            return;
        }

        SubLevel subLevel = Sable.HELPER.getContaining(self.getLevel(), self.getClickedPos());
        if (subLevel != null) {
            SableCat.LOGGER.debug("Prevented command block placement on sub-level at {}", self.getClickedPos());
            cir.setReturnValue(false);
        }
    }
}
