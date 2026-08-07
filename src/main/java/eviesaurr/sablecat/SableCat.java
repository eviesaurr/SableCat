package eviesaurr.sablecat;

import eviesaurr.sablecat.command.SableCatCommand;
import eviesaurr.sablecat.command.SableCatLangCommand;
import eviesaurr.sablecat.fix.FixEntry;
import eviesaurr.sablecat.fix.FixRegistry;
import eviesaurr.sablecat.i18n.LanguageManager;
import eviesaurr.sablecat.update.UpdateChecker;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod(SableCat.MOD_ID)
public class SableCat {
    public static final String MOD_ID = "sablecat";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static SableCatConfig config;
    private static ModContainer modContainer;

    public SableCat(IEventBus bus, ModContainer container) {
        modContainer = container;

        Path configDir = FMLPaths.CONFIGDIR.get();
        LanguageManager.init(configDir);

        config = SableCatConfig.load(configDir);

        LanguageManager.setLanguage(config.getLanguage());

        printBanner(configDir);

        FixRegistry.register("async-save",
                "Redirects SubLevel save operations to an async I/O thread to prevent server freezes during saves",
                true, FixEntry.Side.BOTH);
        FixRegistry.register("panic-guard",
                "Adds safety checks before Rust native calls to prevent server crashes from panics in native code",
                true, FixEntry.Side.BOTH);
        FixRegistry.register("write-flush",
                "Ensures data is flushed to disk before updating storage file headers, preventing data corruption on crash",
                true, FixEntry.Side.BOTH);
        FixRegistry.register("corrupted-cleanup",
                "Removes corrupted sub-level pointers from holding chunks to prevent repeated load errors",
                true, FixEntry.Side.BOTH);

        FixRegistry.register("carryon-compat",
                "Fixes CarryOn placing players on physics sub-levels causing teleportation to wrong dimensions",
                true, Set.of("carryon"), FixEntry.Side.BOTH);
        FixRegistry.register("typewriter-server-fix",
                "Fixes Simulated mod typewriter crashing dedicated servers due to client-only class references in common code",
                true, Set.of("simulated"), FixEntry.Side.BOTH);
        FixRegistry.register("command-block-sublevel-fix",
                "Prevents command blocks (and variants) from being placed on physics sub-levels, which bypasses vanilla restrictions",
                true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("aeronautics-server-fix",
                "Fixes Aeronautics SteamVentBlockEntity crashing dedicated servers due to client-only class references in common code",
                true, Set.of("aeronautics"), FixEntry.Side.BOTH);
        FixRegistry.register("aeronautics-slime-bearfix",
                "Fixes slime blocks sticking to bearing structures causing them to separate and clip through blocks",
                false, Set.of("aeronautics"), FixEntry.Side.BOTH);
        FixRegistry.register("physics-staff-drag-clipfix",
                "Prevents physics structures from clipping through physics blocks when dragged at high speed with the physics staff",
                true, Set.of("simulated"), FixEntry.Side.BOTH);
        FixRegistry.register("plot-holder-guard",
                "Prevents server crash when block changes occur in plot chunks without a holder (e.g. bamboo growing near unloaded physics structures)",
                true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("copycats-lift-compat",
                "Prevents server crash when Copycats blocks with missing facing property trigger sable$getNormal in onBlockChange",
                true, Set.of("sable", "copycats"), FixEntry.Side.BOTH);
        FixEntry playerPosGuard = FixRegistry.register("player-position-guard",
                "Clamps player position to world border when coordinates exceed boundaries, preventing server crashes from SubLevel physics",
                true, Set.of("sable"), FixEntry.Side.BOTH);
        playerPosGuard.setDefaultOption("yMaxMargin", 1000.0);
        FixRegistry.register("light-engine-bounds-guard",
                "Prevents light engine crashes when SubLevel sections exceed world height limits during light propagation",
                true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("physics-ticket-guard",
                "Prevents server crash when PhysicsChunkTicketManager triggers DistanceManager internal state corruption (ArrayIndexOutOfBoundsException in LeveledPriorityQueue)",
                true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("sublevel-entity-guard",
                "Prevents server freeze when SubLevelInclusiveLevelEntityGetter iterates over abnormally large AABBs caused by corrupted entity section storage",
                true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("sublevel-volume-limit",
                "Limits the maximum block count of a single physics structure to prevent server lag and Rapier native crashes from oversized collision bodies",
                true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("ctt-concurrent-fix",
                "Fixes ConcurrentModificationException when CreateThreadedTrains ticks trains on worker threads while main thread modifies passenger data",
                true, Set.of("createthreadedtrains"), FixEntry.Side.BOTH);
        FixRegistry.register("ctt-log-spam-fix",
                "Suppresses repeated warning logs from CreateThreadedTrains when train calculation fails, only logs once per error type",
                true, Set.of("createthreadedtrains"), FixEntry.Side.BOTH);
        FixRegistry.register("create-trackgraph-null-guard",
                "Prevents server crash when Create train navigation searches with a null TrackNode (corrupted train state from CTT concurrent issues): TrackGraph.getConnectionsFrom returns empty Map instead of null to avoid NullPointerException in Navigation.search",
                true, Set.of("create"), FixEntry.Side.BOTH);
        FixRegistry.register("create-train-detach-nulledge-guard",
                "Prevents server crash when TrackGraph.removeNode triggers Train.detachFromTracks on a train with corrupted state (null TravellingPoint.edge): skips TrainMigration creation for points with null edge instead of throwing NullPointerException in TrainMigration constructor",
                true, Set.of("create"), FixEntry.Side.BOTH);
        FixRegistry.register("sublevel-load-log-spam-fix",
                "Throttles repeated 'Couldn't find sub-level' ERROR log spam from SubLevelStorage.attemptLoadSubLevel when a sub-level storage entry is corrupted/missing: logs once per chunk+index per 60s window instead of every retry",
                true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("udp-invalid-packet-guard",
                "Silently drops UDP packets with invalid packet IDs (e.g. legacy server list ping packet ID 254) instead of letting SableUDPPacketDecoder throw IOException and spam 'Server UDP channel caught exception' ERROR logs",
                true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("entity-lookup-remove-guard",
                "Catches ArrayIndexOutOfBoundsException thrown by Int2ObjectLinkedOpenHashMap inside EntityLookup.remove during PersistentEntitySectionManager.stopTracking, preventing single-entity removal failures from crashing the server tick loop when Sable corrupts the EntityLookup internal linked-map state",
                true, Set.of("sable"), FixEntry.Side.BOTH);
        FixEntry rescueCapture = FixRegistry.register("rescue-capture",
                "Captures holding sub-levels that fail SubLevelSerializer.fullyLoad (corrupted pose, empty plot bounds) into an in-memory registry for inspection and rescue via /sablecat rescue: list captured failures, dry-run a rescue, or rewrite a pose-corrupted sub-level's position to a configurable safe location and re-attempt loading",
                true, Set.of("sable"), FixEntry.Side.BOTH);
        rescueCapture.setDefaultOption("rescueX", 0.0);
        rescueCapture.setDefaultOption("rescueY", 200.0);
        rescueCapture.setDefaultOption("rescueZ", 0.0);
        FixRegistry.register("chain-save-guard",
                "Prevents one corrupted sub-level in a getLoadingDependencyChain group (sub-levels grouped purely by bounding-box intersection, e.g. two physically-touching vehicles) from silently aborting the rest of saveAll() when moveAndSaveSubLevel throws for that one member - without this, every sub-level scheduled to save afterward in the same cycle is skipped too, not just the failing one",
                true, Set.of("sable"), FixEntry.Side.BOTH);
        FixEntry sublevelBackup = FixRegistry.register("sublevel-backup",
                "Periodically archives the live sub-level storage folder into a separate, timestamped, never-overwritten .zip library, independent of the live world's own save state - checked once per save cycle, only actually runs when the configured interval has elapsed",
                true, Set.of("sable"), FixEntry.Side.SERVER);
        sublevelBackup.setDefaultOption("intervalHours", 24.0);
        sublevelBackup.setDefaultOption("maxBackupsToKeep", 14.0);
        sublevelBackup.setDefaultOption("libraryPath", "");
        FixEntry blockGuard = FixRegistry.register("block-destroy-coordinate-guard",
                "Prevents server crash and lag when modded items cause block destruction at integer-limit coordinates (Integer.MIN_VALUE/MAX_VALUE): blocks setBlock/destroyBlock calls when coordinates exceed world border limits (±30M) or Y bounds (-512~1024), preventing mass chunk loading and light propagation cascading",
                true, Set.of("sable"), FixEntry.Side.BOTH);
        blockGuard.setDefaultOption("xLimit", 30_000_000);
        blockGuard.setDefaultOption("yMin", -512);
        blockGuard.setDefaultOption("yMax", 1024);
        FixRegistry.register("frogport-extract-limit",
                "Prevents server freeze when FrogportBlockEntity.lazyTick pulls items from oversized adjacent inventories (hopper chains, Create warehouses): skips ItemHelper.extract when IItemHandler slot count exceeds 256, logs once per 60s",
                true, Set.of("create"), FixEntry.Side.BOTH);
        FixRegistry.register("ctt-posttick-timeout-guard",
                "Prevents Watchdog server crash when CreateThreadedTrains.postTick blocks the main thread waiting for a stuck async train worker: replaces Future.get() with a 10s timeout, cancels and skips on timeout to keep the server alive",
                true, Set.of("createthreadedtrains"), FixEntry.Side.BOTH);

        FixRegistry.register("effortless-particle-fix",
                "Fixes Effortless client crash when interacting with Sable physics structures by skipping particle generation for unloaded chunks (Plot storage area coordinates)",
                true, Set.of("effortless", "sable"), FixEntry.Side.CLIENT);

        FixRegistry.register("vista-camera-chunk-fix",
                "Fixes Vista camera chunk loading incompatibility with Sable physics structures: projects ViewFinder SubLevel coordinates to world coordinates before force-loading chunks, preventing TPS drop and infinite loading loops",
                true, Set.of("vista", "sable"), FixEntry.Side.BOTH);

        FixRegistry.register("sable-scalablelux-incompat-bypass",
                "Bypasses Sable's hardcoded incompatible-with-ScalableLux declaration via NeoForge's dependency override mechanism: writes 'dependencyOverrides.sable = [\"-scalablelux\"]' to config/fml.toml on startup. This is a prerequisite for 'scalablelux-compat'. Enable this first, then restart, before enabling scalablelux-compat.",
                false, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("scalablelux-compat",
                "Fixes Sable SubLevel lighting being completely disabled when ScalableLux is installed: ScalableLux clears the vanilla blockEngine/skyEngine fields of the main world light engine, causing Sable to misjudge SubLevel as having no block light and no sky light. Requires 'sable-scalablelux-incompat-bypass' to be enabled first.",
                true, Set.of("scalablelux", "sable"), FixEntry.Side.BOTH);

        FixRegistry.register("constraint-self-fix",
                "Suppresses self-constraint errors in Sable physics pipeline: when a constraint is added between a SubLevel and itself, returns null instead of throwing IllegalArgumentException, preventing log spam",
                true, Set.of("sable"), FixEntry.Side.BOTH);

        FixRegistry.checkEnvironment(modId -> {
            boolean loaded = net.neoforged.fml.loading.FMLLoader.getLoadingModList().getModFileById(modId) != null;
            if (!loaded) {
                LOGGER.info("Mod '{}' not found, related fixes will be disabled", modId);
            }
            return loaded;
        });

        for (FixEntry entry : FixRegistry.getAllFixes()) {
            Boolean state = config.getFixStates().get(entry.getId());
            if (state != null) {
                entry.setEnabled(state);
            }
        }

        for (Map.Entry<String, Map<String, Object>> fixParamEntry : config.getFixParams().entrySet()) {
            FixEntry fixEntry = FixRegistry.getFix(fixParamEntry.getKey());
            if (fixEntry != null) {
                for (Map.Entry<String, Object> param : fixParamEntry.getValue().entrySet()) {
                    fixEntry.setOption(param.getKey(), param.getValue());
                }
            }
        }

        config.save(configDir);

        if (FixRegistry.isEnabled("sable-scalablelux-incompat-bypass")) {
            ensureScalableLuxDependencyOverride(configDir);
        }

        if (config.isAutoUpdate()) {
            UpdateChecker.checkAsync();
        }

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);

        LOGGER.info("sablecat v{} loaded - {} fixes registered", VERSION, FixRegistry.getAllFixes().size());
    }

    private void printBanner(Path configDir) {
        ConsoleAnsiArtist.printAnsiText("SABLECAT", "255,80,80", "");
        System.out.println();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        SableCatCommand.register(event.getDispatcher());
        SableCatLangCommand.register(event.getDispatcher());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        config.save(FMLPaths.CONFIGDIR.get());
    }

    private static void ensureScalableLuxDependencyOverride(Path configDir) {
        try {
            Path fmlTomlPath = configDir.resolve("fml.toml");
            if (!Files.exists(fmlTomlPath)) {
                return;
            }

            List<String> lines = new ArrayList<>(Files.readAllLines(fmlTomlPath, StandardCharsets.UTF_8));

            for (String line : lines) {
                if (line.contains("scalablelux")) {
                    return;
                }
            }

            lines.removeIf(line -> line.trim().equals("dependencyOverrides = {}"));

            int sectionIndex = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().equals("[dependencyOverrides]")) {
                    sectionIndex = i;
                    break;
                }
            }

            if (sectionIndex >= 0) {
                lines.add(sectionIndex + 1, "sable = [\"-scalablelux\"]");
            } else {
                if (!lines.isEmpty() && !lines.get(lines.size() - 1).trim().isEmpty()) {
                    lines.add("");
                }
                lines.add("[dependencyOverrides]");
                lines.add("sable = [\"-scalablelux\"]");
            }

            Files.write(fmlTomlPath, lines, StandardCharsets.UTF_8);
            LOGGER.info("Added ScalableLux dependency override to fml.toml (sable-scalablelux-incompat-bypass). Restart the game for the change to take effect.");
        } catch (Exception e) {
            LOGGER.warn("Failed to add ScalableLux dependency override to fml.toml", e);
        }
    }

    public static void saveConfig() {
        if (config != null) {
            config.save(FMLPaths.CONFIGDIR.get());
        }
    }

    public static ModContainer getModContainer() { return modContainer; }
}