package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.lang.reflect.Field;

@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent.SteamVentBlockEntity$SteamVentValueBoxTransform", remap = false)
public abstract class SteamVentValueBoxTransformMixin extends com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform.Sided {

    private void sablecat$setDirection(Direction dir) {
        try {
            Class<?> clazz = this.getClass().getSuperclass();
            while (clazz != null) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.getType() == Direction.class) {
                        f.setAccessible(true);
                        f.set(this, dir);
                        return;
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception e) {
            SableCat.LOGGER.debug("SteamVent: failed to set direction field via reflection", e);
        }
    }

    /**
     * @author sablecat
     * @reason Minecraft NoClassDefFoundError
     */
    @Overwrite(remap = false)
    public com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform.Sided fromSide(final Direction dir) {
        this.sablecat$setDirection(dir);

        if (!FixRegistry.isEnabled("aeronautics-server-fix")) {
            return this;
        }

        if (FMLEnvironment.dist != Dist.CLIENT) {
            return this;
        }

        if (dir == Direction.UP) {
            try {
                Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
                var getInstance = minecraftClass.getDeclaredMethod("getInstance");
                Object mc = getInstance.invoke(null);
                var hitResultField = minecraftClass.getDeclaredField("hitResult");
                hitResultField.setAccessible(true);
                Object hitResult = hitResultField.get(mc);

                if (hitResult != null && hitResult.getClass().getName().contains("BlockHitResult")) {
                    var getLocation = hitResult.getClass().getMethod("getLocation");
                    Object hitVec = getLocation.invoke(hitResult);
                    double hx = (double) hitVec.getClass().getMethod("x").invoke(hitVec);
                    double hy = (double) hitVec.getClass().getMethod("y").invoke(hitVec);
                    double hz = (double) hitVec.getClass().getMethod("z").invoke(hitResult);

                    var beField = this.getClass().getDeclaredField("be");
                    beField.setAccessible(true);
                    Object be = beField.get(this);
                    var getBlockPos = be.getClass().getMethod("getBlockPos");
                    Object blockPos = getBlockPos.invoke(be);
                    int bx = (int) blockPos.getClass().getMethod("getX").invoke(blockPos);
                    int by = (int) blockPos.getClass().getMethod("getY").invoke(blockPos);
                    int bz = (int) blockPos.getClass().getMethod("getZ").invoke(blockPos);

                    double localY = hy - (by + 0.5);
                    if (localY < 0.4) {
                        double localX = hx - (bx + 0.5);
                        double localZ = hz - (bz + 0.5);
                        this.sablecat$setDirection(Direction.getNearest(localX, 0, localZ));
                    }
                }
            } catch (Exception e) {
                SableCat.LOGGER.debug("SteamVent fromSide: failed to get client hit result", e);
            }
        }

        return this;
    }

    /**
     * @author sablecat
     * @reason isSideActive Minecraft NoClassDefFoundError
     */
    @Overwrite(remap = false)
    protected boolean isSideActive(final BlockState state, final Direction dir) {
        if (!FixRegistry.isEnabled("aeronautics-server-fix")) {
            return true;
        }

        if (FMLEnvironment.dist != Dist.CLIENT) {
            return true;
        }

        if (dir != Direction.UP) {
            return true;
        }

        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            var getInstance = minecraftClass.getDeclaredMethod("getInstance");
            Object mc = getInstance.invoke(null);
            var hitResultField = minecraftClass.getDeclaredField("hitResult");
            hitResultField.setAccessible(true);
            Object hitResult = hitResultField.get(mc);

            if (hitResult != null && hitResult.getClass().getName().contains("BlockHitResult")) {
                var getLocation = hitResult.getClass().getMethod("getLocation");
                Object hitVec = getLocation.invoke(hitResult);
                double y = (double) hitVec.getClass().getMethod("y").invoke(hitVec);

                var beField = this.getClass().getDeclaredField("be");
                beField.setAccessible(true);
                Object be = beField.get(this);
                var getBlockPos = be.getClass().getMethod("getBlockPos");
                Object blockPos = getBlockPos.invoke(be);
                int by = (int) blockPos.getClass().getMethod("getY").invoke(blockPos);

                double localY = y - (by + 0.5);
                return localY < 0.4;
            }
        } catch (Exception e) {
            SableCat.LOGGER.debug("SteamVent isSideActive: failed to get client hit result", e);
        }

        return true;
    }
}
