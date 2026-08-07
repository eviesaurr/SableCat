package eviesaurr.sablecat.mixin;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot", remap = false)
public abstract class ScalableLuxCompatMixin {

    @Redirect(
        method = "<init>",
        at = @At(
            value = "NEW",
            target = "net/minecraft/world/level/lighting/LevelLightEngine",
            remap = false
        ),
        remap = false
    )
    private LevelLightEngine sablecat$fixLightEngineInit(
        LightChunkGetter chunkGetter, boolean hasBlockLight, boolean hasSkyLight
    ) {
        if (FixRegistry.isEnabled("scalablelux-compat")) {
            if (!FixRegistry.isEnabled("sable-scalablelux-incompat-bypass")) {
                SableCat.LOGGER.warn(
                    "scalablelux-compat is enabled but its prerequisite 'sable-scalablelux-incompat-bypass' is disabled. " +
                    "Enable 'sable-scalablelux-incompat-bypass' and restart the server for ScalableLux compatibility to work."
                );
                return new LevelLightEngine(chunkGetter, hasBlockLight, hasSkyLight);
            }
            if (chunkGetter.getLevel() instanceof Level level) {
                LevelLightEngine worldEngine = level.getLightEngine();
                if (worldEngine instanceof StarLightLightingProvider provider) {
                    StarLightInterface starLight = provider.scalablelux$getLightEngine();
                    if (starLight != null) {
                        boolean realHasBlock = starLight.hasBlockLight();
                        boolean realHasSky = starLight.hasSkyLight();
                        if (realHasBlock != hasBlockLight || realHasSky != hasSkyLight) {
                            SableCat.LOGGER.info(
                                "ScalableLux compat: corrected SubLevel light engine flags (block: {}->{}, sky: {}->{})",
                                hasBlockLight, realHasBlock, hasSkyLight, realHasSky
                            );
                        }
                        hasBlockLight = realHasBlock;
                        hasSkyLight = realHasSky;
                    }
                }
            }
        }
        return new LevelLightEngine(chunkGetter, hasBlockLight, hasSkyLight);
    }
}
