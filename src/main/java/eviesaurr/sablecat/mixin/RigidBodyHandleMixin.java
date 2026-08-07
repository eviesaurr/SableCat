package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RigidBodyHandle.class)
public abstract class RigidBodyHandleMixin {

    @Redirect(
        method = "getLinearVelocity",
        at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;getLinearVelocity(Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;",
            remap = false),
        remap = false
    )
    private Vector3d sablecat$safeGetLinearVelocity(PhysicsPipeline pipeline, PhysicsPipelineBody body, Vector3d dest) {
        if (!FixRegistry.isEnabled("panic-guard")) {
            return pipeline.getLinearVelocity(body, dest);
        }
        try {
            return pipeline.getLinearVelocity(body, dest);
        } catch (RuntimeException e) {
            SableCat.LOGGER.warn("Body has been removed, returning zero linear velocity");
            return dest.zero();
        }
    }

    @Redirect(
        method = "getAngularVelocity",
        at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;getAngularVelocity(Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;",
            remap = false),
        remap = false
    )
    private Vector3d sablecat$safeGetAngularVelocity(PhysicsPipeline pipeline, PhysicsPipelineBody body, Vector3d dest) {
        if (!FixRegistry.isEnabled("panic-guard")) {
            return pipeline.getAngularVelocity(body, dest);
        }
        try {
            return pipeline.getAngularVelocity(body, dest);
        } catch (RuntimeException e) {
            SableCat.LOGGER.warn("Body has been removed, returning zero angular velocity");
            return dest.zero();
        }
    }
}
