package eviesaurr.sablecat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Block.class)
public abstract class BlockUseItemMixin {

    @WrapMethod(method = "useItemOn")
    private ItemInteractionResult sablecat$catchClientClassError(
            ItemStack stack, BlockState state, Level level, Player player,
            InteractionHand hand, BlockHitResult hitResult,
            Operation<ItemInteractionResult> original) {
        if (!FixRegistry.isEnabled("typewriter-server-fix")) {
            return original.call(stack, state, level, player, hand, hitResult);
        }

        try {
            return original.call(stack, state, level, player, hand, hitResult);
        } catch (NoClassDefFoundError e) {
            SableCat.LOGGER.warn("Caught NoClassDefFoundError during block interaction at {} - " +
                    "this is likely a mod bug referencing client-only classes in common code", hitResult.getBlockPos(), e);
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
    }
}
