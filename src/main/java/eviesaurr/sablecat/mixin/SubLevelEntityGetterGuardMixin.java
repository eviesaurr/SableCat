package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.util.SubLevelInclusiveLevelEntityGetter", remap = false)
public abstract class SubLevelEntityGetterGuardMixin {

    private static final int MAX_SUBLEVEL_ITERATIONS = 10;

    @Inject(method = "get(Lnet/minecraft/world/phys/AABB;Ljava/util/function/Consumer;)V", at = @At("HEAD"), remap = false, cancellable = true)
    private void sablecat$guardGet(AABB aABB, Consumer consumer, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("sublevel-entity-guard")) {
            return;
        }

        double size = aABB.getSize();
        if (size > 10000) {
            SableCat.LOGGER.warn("SubLevelInclusiveLevelEntityGetter: skipping abnormally large AABB (size={})", size);
            ci.cancel();
        }
    }

    @Inject(method = "get(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V", at = @At("HEAD"), remap = false, cancellable = true)
    private void sablecat$guardGetTyped(EntityTypeTest entityTypeTest, AABB aABB, AbortableIterationConsumer abortableIterationConsumer, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("sublevel-entity-guard")) {
            return;
        }

        double size = aABB.getSize();
        if (size > 10000) {
            SableCat.LOGGER.warn("SubLevelInclusiveLevelEntityGetter: skipping abnormally large AABB (size={})", size);
            ci.cancel();
        }
    }
}
