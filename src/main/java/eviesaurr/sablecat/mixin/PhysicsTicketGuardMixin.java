package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SubLevelPhysicsSystem.class)
public class PhysicsTicketGuardMixin {

    @Redirect(
        method = "tick",
        at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/system/ticket/PhysicsChunkTicketManager;update(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;D)V"),
        remap = false
    )
    private void sablecat$safeTicketUpdate(PhysicsChunkTicketManager instance, net.minecraft.server.level.ServerLevel level,
                                            dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container,
                                            SubLevelPhysicsSystem system,
                                            dev.ryanhcode.sable.api.physics.PhysicsPipeline pipeline, double timeStep) {
        if (!FixRegistry.isEnabled("physics-ticket-guard")) {
            instance.update(level, container, system, pipeline, timeStep);
            return;
        }

        try {
            instance.update(level, container, system, pipeline, timeStep);
        } catch (ArrayIndexOutOfBoundsException e) {
            SableCat.LOGGER.error("PhysicsChunkTicketManager update failed due to DistanceManager internal state corruption, skipping this tick", e);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && (e.getMessage().contains("Full") || e.getMessage().contains("ticket") || e.getMessage().contains("chunk"))) {
                SableCat.LOGGER.error("PhysicsChunkTicketManager update failed with state error, skipping this tick", e);
            } else {
                throw e;
            }
        }
    }
}
