package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixEntry;
import eviesaurr.sablecat.fix.FixRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelDestroyBlockGuardMixin {

    @Unique
    private static long sablecat$lastCoordWarnTime = 0;

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void sablecat$guardSetBlockBounds(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (!FixRegistry.isEnabled("block-destroy-coordinate-guard")) return;
        if (sablecat$isCoordinateExtreme(pos)) {
            sablecat$warnExtremeCoordinate("setBlock", pos);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
    private void sablecat$guardDestroyBlockBounds(BlockPos pos, boolean dropBlock, net.minecraft.world.entity.Entity entity, int recursionLevel, CallbackInfoReturnable<Boolean> cir) {
        if (!FixRegistry.isEnabled("block-destroy-coordinate-guard")) return;
        if (sablecat$isCoordinateExtreme(pos)) {
            sablecat$warnExtremeCoordinate("destroyBlock", pos);
            cir.setReturnValue(false);
        }
    }

    @Unique
    private static int sablecat$getIntOption(String key, int defaultValue) {
        FixEntry entry = FixRegistry.getFix("block-destroy-coordinate-guard");
        if (entry == null) return defaultValue;
        Object val = entry.getOption(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    @Unique
    private static boolean sablecat$isCoordinateExtreme(BlockPos pos) {
        int xLimit = sablecat$getIntOption("xLimit", 30_000_000);
        int yMin = sablecat$getIntOption("yMin", -512);
        int yMax = sablecat$getIntOption("yMax", 1024);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return Math.abs(x) > xLimit || Math.abs(z) > xLimit || y < yMin || y > yMax;
    }

    @Unique
    private static void sablecat$warnExtremeCoordinate(String operation, BlockPos pos) {
        long now = System.currentTimeMillis();
        if (now - sablecat$lastCoordWarnTime > 60_000) {
            sablecat$lastCoordWarnTime = now;
            int xLimit = sablecat$getIntOption("xLimit", 30_000_000);
            int yMin = sablecat$getIntOption("yMin", -512);
            int yMax = sablecat$getIntOption("yMax", 1024);
            SableCat.LOGGER.warn(
                "Blocked {} at extreme coordinate {} (likely integer overflow from a modded item). " +
                "Current limits: x/z limit={}, y range=[{}, {}]. " +
                "This warning is throttled to once per 60s.",
                operation, pos, xLimit, yMin, yMax
            );
        }
    }
}
