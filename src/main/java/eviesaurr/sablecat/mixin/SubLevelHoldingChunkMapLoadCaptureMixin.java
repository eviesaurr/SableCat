package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.CorruptedHoldingRegistry;
import eviesaurr.sablecat.fix.FixRegistry;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import eviesaurr.sablecat.fix.SubLevelManifest;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SubLevelHoldingChunkMap.class)
public abstract class SubLevelHoldingChunkMapLoadCaptureMixin {

    @Redirect(
            method = "loadHoldingSubLevel",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelSerializer;fullyLoad(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)Ldev/ryanhcode/sable/sublevel/ServerSubLevel;",
                    remap = false),
            remap = false
    )
    private ServerSubLevel sablecat$captureLoadFailure(ServerLevel level, SubLevelData data,
                                                        HoldingSubLevel holdingSubLevel) {
        ServerSubLevel result = SubLevelSerializer.fullyLoad(level, data);

        SubLevelManifest.initIfNeeded(level.getServer());

        if (result != null && FixRegistry.isEnabled("rescue-capture")) {
            SubLevelManifest.recordLive(result, "load");
        }

        if (result == null && FixRegistry.isEnabled("rescue-capture")) {
            CorruptedHoldingRegistry.capture(holdingSubLevel);
            SableCat.LOGGER.warn(
                    "Captured failed holding sub-level {} for rescue inspection - run /sablecat rescue list",
                    data.uuid());
        }

        return result;
    }
}