package eviesaurr.sablecat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.debug.BlockUpdateMonitor;
import eviesaurr.sablecat.fix.CorruptedHoldingRegistry;
import eviesaurr.sablecat.fix.CorruptedPointerCache;
import eviesaurr.sablecat.fix.SubLevelRescue;
import eviesaurr.sablecat.fix.FixEntry;
import eviesaurr.sablecat.fix.FixRegistry;
import eviesaurr.sablecat.fix.*;
import eviesaurr.sablecat.i18n.LanguageManager;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

@SuppressWarnings("null")
public class SableCatCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sablecat")
                .requires(source -> source.hasPermission(2))
                .executes(SableCatCommand::listFixes)
                .then(Commands.argument("fix", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            String input = builder.getRemaining().toLowerCase();
                            if ("all".startsWith(input)) {
                                builder.suggest("all");
                            }
                            for (FixEntry entry : FixRegistry.getAllFixes()) {
                                if (entry.isHidden()) continue;
                                if (entry.getId().toLowerCase().startsWith(input)) {
                                    builder.suggest(entry.getId());
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(SableCatCommand::showFix)
                        .then(Commands.literal("on")
                                .executes(ctx -> toggleFix(ctx, true)))
                        .then(Commands.literal("off")
                                .executes(ctx -> toggleFix(ctx, false)))
                        .then(Commands.literal("reset")
                                .executes(SableCatCommand::resetFixOptions))
                )
                .then(Commands.literal("default")
                        .executes(SableCatCommand::resetToDefaults)
                )
                .then(Commands.literal("list-corrupted")
                        .executes(SableCatCommand::listCorrupted)
                        .then(Commands.literal("clear")
                                .executes(SableCatCommand::clearAllCorrupted)
                        )
                )
                .then(Commands.literal("rescue")
                        .then(Commands.literal("list")
                                .executes(SableCatCommand::rescueList)
                        )
                        .then(Commands.literal("setlocation")
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(SableCatCommand::rescueSetLocation)
                                )
                        )
                        .then(Commands.argument("uuid", StringArgumentType.word())
                                .executes(SableCatCommand::rescueDryRun)
                                .then(Commands.literal("confirm")
                                        .executes(SableCatCommand::rescueConfirm)
                                )
                                .then(Commands.literal("purge")
                                        .executes(SableCatCommand::purgeDryRun)
                                        .then(Commands.literal("confirm")
                                                .executes(SableCatCommand::purgeConfirm)
                                        )
                                )
                        )
                )
                .then(Commands.literal("purge")
                        .then(Commands.argument("uuid", StringArgumentType.word())
                                .executes(SableCatCommand::purgeLiveDryRun)
                                .then(Commands.literal("confirm")
                                        .executes(SableCatCommand::purgeLiveConfirm)
                                )
                        )
                )
                .then(Commands.literal("forcepurge")
                        .then(Commands.argument("uuid", StringArgumentType.word())
                                .executes(SableCatCommand::forcePurgeDryRun)
                                .then(Commands.literal("confirm")
                                        .executes(SableCatCommand::forcePurgeConfirm)
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(SableCatCommand::forcePurgeConfirmWithReason)
                                        )
                                )
                        )
                )
                .then(Commands.literal("backup")
                        .then(Commands.literal("now")
                                .executes(SableCatCommand::backupNow)
                        )
                        .then(Commands.literal("list")
                                .executes(SableCatCommand::backupList)
                        )
                        .then(Commands.literal("config")
                                .then(Commands.argument("intervalHours", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.1))
                                        .then(Commands.argument("maxBackupsToKeep", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                                .executes(SableCatCommand::backupConfig)
                                        )
                                )
                        )
                )
                .then(Commands.literal("import")
                        .then(Commands.literal("scan")
                                .then(Commands.argument("folder", StringArgumentType.greedyString())
                                        .executes(SableCatCommand::importScan)
                                )
                        )
                        .then(Commands.argument("uuid", StringArgumentType.word())
                                .then(Commands.argument("folder", StringArgumentType.greedyString())
                                        .executes(SableCatCommand::importDryRun)
                                )
                                .then(Commands.literal("confirm")
                                        .then(Commands.argument("folder", StringArgumentType.greedyString())
                                                .executes(SableCatCommand::importConfirm)
                                        )
                                )
                        )
                )
                .then(Commands.literal("manifest")
                        .then(Commands.literal("list")
                                .executes(SableCatCommand::manifestList)
                        )
                        .then(Commands.literal("missing")
                                .executes(SableCatCommand::manifestMissing)
                        )
                )
                .then(Commands.literal("tphere")
                        .then(Commands.argument("target", StringArgumentType.greedyString())
                                .executes(SableCatCommand::tpHere)
                        )
                )
                .then(Commands.literal("save-failures")
                        .then(Commands.literal("list")
                                .executes(SableCatCommand::saveFailuresList)
                        )
                        .then(Commands.literal("clear")
                                .executes(SableCatCommand::saveFailuresClear)
                        )
                )
                .then(Commands.literal("fstemp")
                        .then(Commands.argument("block", StringArgumentType.word())
                                .executes(SableCatCommand::findBlocks)
                        )
                )
                .then(Commands.literal("fs2temp")
                        .then(Commands.literal("on")
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(SableCatCommand::startMonitor)
                                )
                        )
                        .then(Commands.literal("off")
                                .executes(SableCatCommand::stopAllMonitors)
                        )
                )
        );
    }

    private static int listFixes(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(LanguageManager.get("command.list-header")), false);

        for (FixEntry entry : FixRegistry.getAllFixes()) {
            if (entry.isHidden()) continue;
            String status;
            if (!entry.isEnvironmentMet()) {
                status = LanguageManager.get("command.env-unmet");
            } else {
                String statusKey = entry.isEnabled() ? "command.enabled" : "command.disabled";
                status = LanguageManager.get(statusKey);
            }
            String line = LanguageManager.get("command.fix-status", entry.getId(), status);
            source.sendSuccess(() -> Component.literal("  " + line), false);
        }
        return FixRegistry.getAllFixes().size();
    }

    private static int showFix(CommandContext<CommandSourceStack> context) {
        String fixId = StringArgumentType.getString(context, "fix");
        FixEntry entry = FixRegistry.getFix(fixId);

        if (entry == null) {
            context.getSource().sendFailure(Component.literal(LanguageManager.get("command.fix-unknown", fixId)));
            return 0;
        }

        String desc = entry.getDescription();
        String status;
        if (!entry.isEnvironmentMet()) {
            status = LanguageManager.get("command.env-unmet");
        } else {
            String statusKey = entry.isEnabled() ? "command.enabled" : "command.disabled";
            status = LanguageManager.get(statusKey);
        }

        StringBuilder info = new StringBuilder();
        info.append(LanguageManager.get("command.fix-status", entry.getId(), status)).append("\n");
        info.append(LanguageManager.get("command.fix-desc", desc));
        if (!entry.getRequiredMods().isEmpty()) {
            info.append("\n").append(LanguageManager.get("command.fix-requires", String.join(", ", entry.getRequiredMods())));
        }

        String finalInfo = info.toString();
        context.getSource().sendSuccess(() -> Component.literal(finalInfo), false);
        return 1;
    }

    private static int toggleFix(CommandContext<CommandSourceStack> context, boolean enabled) {
        String fixId = StringArgumentType.getString(context, "fix");

        if (fixId.equalsIgnoreCase("all")) {
            return toggleAllFixes(context, enabled);
        }

        FixEntry entry = FixRegistry.getFix(fixId);

        if (entry == null) {
            context.getSource().sendFailure(Component.literal(LanguageManager.get("command.fix-unknown", fixId)));
            return 0;
        }

        if (!entry.isEnvironmentMet()) {
            context.getSource().sendFailure(Component.literal(LanguageManager.get("command.fix-env-blocked", fixId)));
            return 0;
        }

        entry.setEnabled(enabled);
        SableCat.saveConfig();
        String key = enabled ? "command.fix-enabled" : "command.fix-disabled";
        context.getSource().sendSuccess(() -> Component.literal(LanguageManager.get(key, fixId)), true);
        return 1;
    }

    private static int resetFixOptions(CommandContext<CommandSourceStack> context) {
        String fixId = StringArgumentType.getString(context, "fix");

        if (fixId.equalsIgnoreCase("all")) {
            int changed = 0;
            for (FixEntry entry : FixRegistry.getAllFixes()) {
                entry.resetOptions();
                changed++;
            }
            SableCat.saveConfig();
            final int count = changed;
            context.getSource().sendSuccess(() -> Component.literal("Reset options for all fixes to defaults (" + count + " fixes)"), true);
            return changed;
        }

        FixEntry entry = FixRegistry.getFix(fixId);

        if (entry == null) {
            context.getSource().sendFailure(Component.literal(LanguageManager.get("command.fix-unknown", fixId)));
            return 0;
        }

        entry.resetOptions();
        SableCat.saveConfig();
        context.getSource().sendSuccess(() -> Component.literal("Reset options for " + fixId + " to defaults"), true);
        return 1;
    }

    private static int toggleAllFixes(CommandContext<CommandSourceStack> context, boolean enabled) {
        int changed = 0;
        for (FixEntry entry : FixRegistry.getAllFixes()) {
            if (!entry.isEnvironmentMet()) continue;
            entry.setEnabled(enabled);
            changed++;
        }
        SableCat.saveConfig();
        final int count = changed;
        String key = enabled ? "command.all-enabled" : "command.all-disabled";
        context.getSource().sendSuccess(() -> Component.literal(LanguageManager.get(key, count)), true);
        return changed;
    }

    private static int resetToDefaults(CommandContext<CommandSourceStack> context) {
        int changed = 0;
        for (FixEntry entry : FixRegistry.getAllFixes()) {
            if (entry.isExplicitlyEnabled() != entry.isDefaultEnabled()) {
                entry.setEnabled(entry.isDefaultEnabled());
                changed++;
            }
        }
        SableCat.saveConfig();
        final int count = changed;
        context.getSource().sendSuccess(() -> Component.literal(LanguageManager.get("command.reset-defaults", count)), true);
        return changed;
    }

    /**
     * Lists every sub-level pointer currently blacklisted by corrupted-cleanup,
     * grouped by chunk, along with how many consecutive times each has failed to load.
     */
    private static int listCorrupted(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Map<Long, Set<SavedSubLevelPointer>> all = CorruptedPointerCache.getAll();

        int total = 0;
        for (Set<SavedSubLevelPointer> pointers : all.values()) {
            total += pointers.size();
        }

        if (total == 0) {
            source.sendSuccess(() -> Component.literal("No corrupted sub-level pointers currently cached."), false);
            return 0;
        }

        final int finalTotal = total;
        source.sendSuccess(() -> Component.literal("Found " + finalTotal + " corrupted sub-level pointer(s) currently blacklisted:"), false);

        for (Map.Entry<Long, Set<SavedSubLevelPointer>> chunkEntry : all.entrySet()) {
            long chunkKey = chunkEntry.getKey();
            ChunkPos chunkPos = new ChunkPos(chunkKey);

            for (SavedSubLevelPointer pointer : chunkEntry.getValue()) {
                int failCount = CorruptedPointerCache.getFailCount(chunkKey, pointer);
                String line = String.format("  [chunk %s] %s (failed %d time%s)",
                        chunkPos, pointer, failCount, failCount == 1 ? "" : "s");
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }

        return total;
    }

    /**
     * Force-clears the entire corrupted-pointer cache, so every blacklisted
     * sub-level gets a genuine retry on its next load attempt instead of
     * waiting out the cooldown.
     */
    private static int clearAllCorrupted(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int totalBefore = 0;
        for (Set<SavedSubLevelPointer> pointers : CorruptedPointerCache.getAll().values()) {
            totalBefore += pointers.size();
        }

        CorruptedPointerCache.clear();

        final int count = totalBefore;
        source.sendSuccess(() -> Component.literal("Cleared " + count + " corrupted sub-level pointer(s). They will be retried on next load."), true);
        return count;
    }

    private static int findBlocks(CommandContext<CommandSourceStack> context) {
        String blockId = StringArgumentType.getString(context, "block");
        CommandSourceStack source = context.getSource();

        ResourceLocation targetId = ResourceLocation.tryParse(blockId);
        if (targetId == null) {
            source.sendFailure(Component.literal(LanguageManager.get("command.invalid-block-id", blockId)));
            return 0;
        }

        var registry = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        var holder = registry.getHolder(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, targetId));
        if (holder.isEmpty()) {
            source.sendFailure(Component.literal(LanguageManager.get("command.block-not-found", targetId)));
            return 0;
        }

        var block = holder.get().value();
        List<String> results = new ArrayList<>();
        int[] totalChunks = {0};

        for (ServerLevel level : source.getServer().getAllLevels()) {
            java.util.Set<Long> scannedChunks = new java.util.HashSet<>();

            // 扫描玩家视距内的区块
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                if (player.serverLevel() != level) continue;
                int chunkRadius = level.getServer().getPlayerList().getViewDistance();
                int playerChunkX = player.blockPosition().getX() >> 4;
                int playerChunkZ = player.blockPosition().getZ() >> 4;

                for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
                    for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                        long chunkKey = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
                        if (!scannedChunks.add(chunkKey)) continue;

                        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                        if (chunk == null) continue;
                        totalChunks[0]++;
                        scanChunkForBlock(chunk, block, blockId, level, results);
                    }
                }
            }

            // 扫描 sub-level 的 plot 区块
            if (level instanceof dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder holder2) {
                var container = holder2.sable$getPlotContainer();
                if (container != null) {
                    for (var subLevel : container.getAllSubLevels()) {
                        var plot = subLevel.getPlot();
                        var plotBounds = plot.getBoundingBox();
                        int minCX = plotBounds.minX() >> 4;
                        int minCZ = plotBounds.minZ() >> 4;
                        int maxCX = plotBounds.maxX() >> 4;
                        int maxCZ = plotBounds.maxZ() >> 4;

                        for (int cx = minCX; cx <= maxCX; cx++) {
                            for (int cz = minCZ; cz <= maxCZ; cz++) {
                                long chunkKey = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
                                if (!scannedChunks.add(chunkKey)) continue;

                                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                                if (chunk == null) continue;
                                totalChunks[0]++;
                                scanChunkForBlockInSubLevel(chunk, block, blockId, level, results, subLevel, plotBounds);
                            }
                        }
                    }
                }
            }
        }

        int chunks = totalChunks[0];
        source.sendSuccess(() -> Component.literal(LanguageManager.get("command.scan-complete", chunks)), false);
        if (results.isEmpty()) {
            source.sendSuccess(() -> Component.literal(LanguageManager.get("command.scan-no-result", blockId)), false);
        } else {
            int count = results.size();
            source.sendSuccess(() -> Component.literal(LanguageManager.get("command.scan-found", count, blockId)), false);
            for (String line : results) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        return results.size();
    }

    private static void scanChunkForBlock(LevelChunk chunk, net.minecraft.world.level.block.Block block, String blockId, ServerLevel level, List<String> results) {
        int minSection = chunk.getMinSection();
        int maxSection = chunk.getMaxSection();
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;

        for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
            var sectionData = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
            if (sectionData.hasOnlyAir()) continue;

            int baseY = sectionY << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        var state = sectionData.getBlockState(x, y, z);
                        if (state.is(block)) {
                            BlockPos pos = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                            results.add(String.format("  [%s] %s 维度=%s",
                                    blockId, pos, level.dimension().location()));
                        }
                    }
                }
            }
        }
    }

    private static void scanChunkForBlockInSubLevel(LevelChunk chunk, net.minecraft.world.level.block.Block block, String blockId, ServerLevel level, List<String> results, dev.ryanhcode.sable.sublevel.SubLevel subLevel, dev.ryanhcode.sable.companion.math.BoundingBox3ic plotBounds) {
        int minSection = chunk.getMinSection();
        int maxSection = chunk.getMaxSection();
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;

        for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
            var sectionData = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
            if (sectionData.hasOnlyAir()) continue;

            int baseY = sectionY << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        var state = sectionData.getBlockState(x, y, z);
                        if (state.is(block)) {
                            int worldX = (cx << 4) + x;
                            int worldY = baseY + y;
                            int worldZ = (cz << 4) + z;

                            // 仅处理 plot 范围内的方块
                            if (worldX < plotBounds.minX() || worldX > plotBounds.maxX() ||
                                    worldY < plotBounds.minY() || worldY > plotBounds.maxY() ||
                                    worldZ < plotBounds.minZ() || worldZ > plotBounds.maxZ()) continue;

                            BlockPos localPos = new BlockPos(worldX, worldY, worldZ);

                            // 将局部坐标转换为全局坐标
                            net.minecraft.world.phys.Vec3 globalPos = dev.ryanhcode.sable.companion.math.JOMLConversion.toMojang(
                                    subLevel.logicalPose().transformPosition(
                                            dev.ryanhcode.sable.companion.math.JOMLConversion.toJOML(
                                                    new net.minecraft.world.phys.Vec3(localPos.getX() + 0.5, localPos.getY() + 0.5, localPos.getZ() + 0.5)
                                            )
                                    )
                            );

                            results.add(String.format("  [%s] 局部=%s 全局=(%d, %d, %d) 维度=%s",
                                    blockId, localPos,
                                    (int) globalPos.x, (int) globalPos.y, (int) globalPos.z,
                                    level.dimension().location()));
                        }
                    }
                }
            }
        }
    }

    private static int startMonitor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(LanguageManager.get("command.player-only")));
            return 0;
        }

        Coordinates coords = Vec3Argument.getCoordinates(context, "pos");
        BlockPos pos = coords.getBlockPos(source);
        BlockUpdateMonitor.startMonitoring(pos, player);

        source.sendSuccess(() -> Component.literal(LanguageManager.get("command.monitor-started", pos.toShortString())), false);
        return 1;
    }

    private static int stopAllMonitors(CommandContext<CommandSourceStack> context) {
        int count = BlockUpdateMonitor.getAllMonitors().size();
        BlockUpdateMonitor.stopAll();
        context.getSource().sendSuccess(() -> Component.literal(LanguageManager.get("command.monitor-stopped", count)), false);
        return count;
    }

    // --- /sablecat rescue ---

    private static double[] sablecat$getRescueLocation() {
        FixEntry entry = FixRegistry.getFix("rescue-capture");
        double x = 0, y = 200, z = 0;
        if (entry != null) {
            x = ((Number) entry.getOption("rescueX", 0.0)).doubleValue();
            y = ((Number) entry.getOption("rescueY", 200.0)).doubleValue();
            z = ((Number) entry.getOption("rescueZ", 0.0)).doubleValue();
        }
        return new double[]{x, y, z};
    }

    private static int rescueList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Map<UUID, CorruptedHoldingRegistry.CapturedFailure> all = CorruptedHoldingRegistry.getAll();

        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No captured sub-level load failures this session."), false);
            return 0;
        }

        double[] loc = sablecat$getRescueLocation();
        source.sendSuccess(() -> Component.literal(
                "Captured " + all.size() + " failed sub-level(s). Rescue location: ("
                        + loc[0] + ", " + loc[1] + ", " + loc[2] + ")"), false);

        for (CorruptedHoldingRegistry.CapturedFailure f : all.values()) {
            String rescuable = switch (f.kind()) {
                case POSE_CORRUPTED -> "RESCUABLE";
                case CONTENT_EMPTY -> "NOT RESCUABLE (blocks gone)";
                case OTHER -> "UNKNOWN (try dry-run)";
            };
            String line = "  " + f.uuid() + " | " + f.detail() + " | " + rescuable;
            source.sendSuccess(() -> Component.literal(line), false);
        }
        source.sendSuccess(() -> Component.literal(
                "Run /sablecat rescue <uuid> for a dry-run, then add 'confirm' to execute."), false);
        return all.size();
    }

    private static int rescueSetLocation(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        net.minecraft.world.phys.Vec3 pos = Vec3Argument.getVec3(context, "pos");

        FixEntry entry = FixRegistry.getFix("rescue-capture");
        if (entry == null) {
            source.sendFailure(Component.literal("rescue-capture fix not registered"));
            return 0;
        }
        entry.setOption("rescueX", pos.x);
        entry.setOption("rescueY", pos.y);
        entry.setOption("rescueZ", pos.z);
        SableCat.saveConfig();
        source.sendSuccess(() -> Component.literal(
                "Rescue location set to (" + pos.x + ", " + pos.y + ", " + pos.z + ") and saved to config."), true);
        return 1;
    }

    private static int rescueDryRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID uuid = sablecat$parseUuid(context, source);
        if (uuid == null) return 0;

        CorruptedHoldingRegistry.CapturedFailure f = CorruptedHoldingRegistry.get(uuid);
        if (f == null) {
            source.sendFailure(Component.literal("No captured failure with UUID " + uuid));
            return 0;
        }

        double[] loc = sablecat$getRescueLocation();
        source.sendSuccess(() -> Component.literal("DRY RUN for " + uuid + ":"), false);
        source.sendSuccess(() -> Component.literal("  " + f.detail()), false);
        switch (f.kind()) {
            case CONTENT_EMPTY -> source.sendSuccess(() -> Component.literal(
                    "  Would NOT rescue: plot has no block content - the ship's blocks are gone from this record."), false);
            case POSE_CORRUPTED, OTHER -> source.sendSuccess(() -> Component.literal(
                    "  Would rewrite pose to (" + loc[0] + ", " + loc[1] + ", " + loc[2]
                            + "), zero velocity, and re-attempt load. Run with 'confirm' to execute."), false);
        }
        return 1;
    }

    private static int rescueConfirm(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID uuid = sablecat$parseUuid(context, source);
        if (uuid == null) return 0;

        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            source.sendFailure(Component.literal("Must be run in a server level context"));
            return 0;
        }

        double[] loc = sablecat$getRescueLocation();
        SubLevelRescue.RescueResult result = SubLevelRescue.rescue(level, uuid, loc[0], loc[1], loc[2]);

        if (result.success()) {
            source.sendSuccess(() -> Component.literal(result.message()), true);
            return 1;
        } else {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
    }

    private static UUID sablecat$parseUuid(CommandContext<CommandSourceStack> context, CommandSourceStack source) {
        String raw = StringArgumentType.getString(context, "uuid");
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Invalid UUID: " + raw));
            return null;
        }
    }

    // --- /sablecat manifest ---

    private static int manifestList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Map<UUID, SubLevelManifest.Entry> all = SubLevelManifest.getAll();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "Manifest is empty - it fills in as sub-levels load/save. Fly around a bit and retry."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Manifest: " + all.size() + " known sub-level(s):"), false);
        all.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().lastSeenMs, a.getValue().lastSeenMs))
                .forEach(e -> {
                    var v = e.getValue();
                    long ageMin = (System.currentTimeMillis() - v.lastSeenMs) / 60000L;
                    String line = String.format("  %s | %s | (%.0f, %.0f, %.0f) %s | %d chunk(s) | last %s %dm ago",
                            e.getKey(), v.name, v.worldX, v.worldY, v.worldZ, v.dimension, v.plotChunks, v.lastEvent, ageMin);
                    source.sendSuccess(() -> Component.literal(line), false);
                });
        return all.size();
    }

    private static int manifestMissing(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Map<UUID, SubLevelManifest.Entry> all = SubLevelManifest.getAll();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Manifest is empty - nothing to diff yet."), false);
            return 0;
        }

        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(source.getLevel());
        int flagged = 0;
        for (var e : all.entrySet()) {
            UUID uuid = e.getKey();
            boolean loaded = container != null && container.getSubLevel(uuid) != null;
            if (loaded) continue;

            var capturedFailure = CorruptedHoldingRegistry.get(uuid);
            var v = e.getValue();
            long ageMin = (System.currentTimeMillis() - v.lastSeenMs) / 60000L;
            String status = capturedFailure != null
                    ? "CAPTURED AS FAILED (" + capturedFailure.kind() + ") - see /sablecat rescue list"
                    : "not currently loaded (may simply be in unloaded chunks)";
            String line = String.format("  %s | %s | last seen (%.0f, %.0f, %.0f) %dm ago | %s",
                    uuid, v.name, v.worldX, v.worldY, v.worldZ, ageMin, status);
            source.sendSuccess(() -> Component.literal(line), false);
            flagged++;
        }
        if (flagged == 0) {
            source.sendSuccess(() -> Component.literal(
                    "Every manifest-known sub-level is currently loaded. Nothing missing."), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "Note: 'not currently loaded' is not proof of loss - visit the last-seen coords to force a load attempt; "
                            + "genuinely broken ones will then appear in /sablecat rescue list."), false);
        }
        return flagged;
    }

    // --- /sablecat tphere ---

    /**
     * Teleports a sub-level to the command source's position. pose.position()
     * is world-space (rotation_point is the plot anchor in plot space; the
     * pose transform maps plot coords to world around it). What this adds over
     * /sable teleport is the full verified sequence from
     * SubLevelSerializer.fullyLoad: resetVelocity -> set pose ->
     * pipeline.teleport -> updateLastPose -> updateBoundingBox, where
     * updateLastPose snaps client render interpolation so the ship appears
     * instantly instead of visibly flying, and updateBoundingBox forces a
     * fresh world-bounds recompute (doubles as a bounds repair nudge).
     */
    private static int tpHere(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String raw = StringArgumentType.getString(context, "target").trim();

        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(source.getLevel());
        if (container == null) {
            source.sendFailure(Component.literal("No sub-level container for this dimension."));
            return 0;
        }

        // Resolve by exact name (case-insensitive) first, then by UUID / UUID prefix.
        dev.ryanhcode.sable.sublevel.ServerSubLevel target = null;
        for (var sl : container.getAllSubLevels()) {
            String name = sl.getName();
            if (name != null && name.equalsIgnoreCase(raw)) {
                target = (dev.ryanhcode.sable.sublevel.ServerSubLevel) sl;
                break;
            }
        }
        if (target == null) {
            String lower = raw.toLowerCase();
            for (var sl : container.getAllSubLevels()) {
                if (sl.getUniqueId().toString().toLowerCase().startsWith(lower)) {
                    target = (dev.ryanhcode.sable.sublevel.ServerSubLevel) sl;
                    break;
                }
            }
        }
        if (target == null) {
            source.sendFailure(Component.literal(
                    "No loaded sub-level matches '" + raw + "' (by name or UUID prefix). "
                            + "Note: only currently-loaded sub-levels can be teleported."));
            return 0;
        }

        net.minecraft.world.phys.Vec3 dest = source.getPosition();
        var pose = target.logicalPose();
        var rp = pose.rotationPoint();

        if (Double.isNaN(rp.x()) || Double.isNaN(rp.y()) || Double.isNaN(rp.z())) {
            source.sendFailure(Component.literal(
                    "This sub-level's rotation point (plot anchor) is NaN - refusing to teleport a corrupted pose."));
            return 0;
        }

        // pose.position() is WORLD-space (confirmed empirically: healthy ships report
        // small world coords in Sable's own dumps, and fullyLoad passes pose.position()
        // straight to pipeline.teleport). No space conversion - destination goes in as-is.
        var physics = container.physicsSystem();
        var pipeline = physics.getPipeline();

        pipeline.resetVelocity(target);
        pose.position().set(dest.x, dest.y, dest.z);
        pipeline.teleport(target, new org.joml.Vector3d(dest.x, dest.y, dest.z), pose.orientation());
        target.updateLastPose();
        target.updateBoundingBox();

        // Record the sighting so the manifest reflects the move immediately.
        SubLevelManifest.recordLive(target, "tphere");

        String name = target.getName();
        String label = (name != null && !name.isEmpty() ? name : target.getUniqueId().toString());
        final String msg = String.format("Teleported %s to (%.1f, %.1f, %.1f).", label, dest.x, dest.y, dest.z);
        source.sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    // --- /sablecat save-failures ---

    private static int saveFailuresList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var all = SaveFailureRegistry.getAll();

        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No save failures recorded."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal(all.size() + " save failure(s) recorded:"), false);
        for (var entry : all.values()) {
            long ageSec = (System.currentTimeMillis() - entry.timestampMs()) / 1000;
            String line = String.format("  %s | %s | failed %dx | last %ds ago",
                    entry.description(), entry.errorMessage(), entry.occurrenceCount(), ageSec);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        source.sendSuccess(() -> Component.literal(
                "These items may be lost if the server restarts before they save successfully."), false);
        return all.size();
    }

    private static int saveFailuresClear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int count = SaveFailureRegistry.getAll().size();
        SaveFailureRegistry.clear();
        source.sendSuccess(() -> Component.literal("Cleared " + count + " save failure record(s)."), true);
        return count;
    }

    // --- /sablecat rescue <uuid> purge ---

    private static int purgeDryRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID uuid = sablecat$parseUuid(context, source);
        if (uuid == null) return 0;

        try {
            CorruptedHoldingRegistry.CapturedFailure f = CorruptedHoldingRegistry.get(uuid);
            if (f == null) {
                source.sendFailure(Component.literal("No captured failure with UUID " + uuid));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("DRY RUN PURGE for " + uuid + ":"), false);
            source.sendSuccess(() -> Component.literal("  " + f.detail()), false);
            if (f.kind() != CorruptedHoldingRegistry.FailureKind.CONTENT_EMPTY) {
                source.sendSuccess(() -> Component.literal(
                        "  Would NOT purge: classified as " + f.kind() + ", not CONTENT_EMPTY. "
                                + "This may still be rescuable - try /sablecat rescue " + uuid + " first."), false);
            } else {
                source.sendSuccess(() -> Component.literal(
                        "  Would PERMANENTLY remove this pointer from storage. This cannot be undone. "
                                + "Run with 'confirm' to execute."), false);
            }
            return 1;
        } catch (Exception e) {
            SableCat.LOGGER.error("purge dry-run failed for uuid {}", uuid, e);
            source.sendFailure(Component.literal("Unexpected error during dry-run: " + e));
            return 0;
        }
    }

    private static int purgeConfirm(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID uuid = sablecat$parseUuid(context, source);
        if (uuid == null) return 0;

        try {
            if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
                source.sendFailure(Component.literal("Must be run in a server level context"));
                return 0;
            }

            SubLevelPurge.PurgeResult result = SubLevelPurge.purge(level, uuid);

            if (result.success()) {
                source.sendSuccess(() -> Component.literal(result.message()), true);
                return 1;
            } else {
                source.sendFailure(Component.literal(result.message()));
                return 0;
            }
        } catch (Exception e) {
            SableCat.LOGGER.error("purge confirm failed for uuid {}", uuid, e);
            source.sendFailure(Component.literal("Unexpected error during purge: " + e));
            return 0;
        }
    }

    // --- /sablecat purge <uuid> (general - any currently-loaded, confirmed-empty sub-level) ---

    private static int purgeLiveDryRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID uuid = sablecat$parseUuid(context, source);
        if (uuid == null) return 0;

        try {
            if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
                source.sendFailure(Component.literal("Must be run in a server level context"));
                return 0;
            }

            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            var subLevel = container != null ? container.getSubLevel(uuid) : null;

            if (subLevel == null) {
                source.sendFailure(Component.literal(
                        uuid + " is not currently loaded. Visit its last-known location (check /sablecat manifest list) "
                                + "to force a load attempt, or use /sablecat rescue " + uuid + " purge if it's a captured load failure."));
                return 0;
            }

            var bounds = subLevel.boundingBox();
            double volume = Math.max(0, bounds.maxX() - bounds.minX())
                    * Math.max(0, bounds.maxY() - bounds.minY())
                    * Math.max(0, bounds.maxZ() - bounds.minZ());

            source.sendSuccess(() -> Component.literal("DRY RUN for " + uuid + ":"), false);
            source.sendSuccess(() -> Component.literal(String.format("  Bounding box volume: ~%.1f", volume)), false);
            if (volume > 1.0) {
                source.sendSuccess(() -> Component.literal(
                        "  Would NOT purge: has real volume, likely still has block content."), false);
            } else {
                source.sendSuccess(() -> Component.literal(
                        "  Would PERMANENTLY delete this sub-level. This cannot be undone. Run with 'confirm' to execute."), false);
            }
            return 1;
        } catch (Exception e) {
            SableCat.LOGGER.error("purge (live) dry-run failed for uuid {}", uuid, e);
            source.sendFailure(Component.literal("Unexpected error during dry-run: " + e));
            return 0;
        }
    }

    private static int purgeLiveConfirm(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID uuid = sablecat$parseUuid(context, source);
        if (uuid == null) return 0;

        try {
            if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
                source.sendFailure(Component.literal("Must be run in a server level context"));
                return 0;
            }

            SubLevelPurge.PurgeResult result = SubLevelPurge.purgeLive(level, uuid);

            if (result.success()) {
                source.sendSuccess(() -> Component.literal(result.message()), true);
                return 1;
            } else {
                source.sendFailure(Component.literal(result.message()));
                return 0;
            }
        } catch (Exception e) {
            SableCat.LOGGER.error("purge (live) confirm failed for uuid {}", uuid, e);
            source.sendFailure(Component.literal("Unexpected error during purge: " + e));
            return 0;
        }
    }

    // --- /sablecat forcepurge <uuid> (unrestricted - deletes ANY loaded sub-level, real content or not) ---

    private static int forcePurgeDryRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID uuid = sablecat$parseUuid(context, source);
        if (uuid == null) return 0;

        try {
            if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
                source.sendFailure(Component.literal("Must be run in a server level context"));
                return 0;
            }

            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            var subLevel = container != null ? container.getSubLevel(uuid) : null;

            if (subLevel == null) {
                source.sendFailure(Component.literal(
                        uuid + " is not currently loaded. It must be loaded (visit its last-known location) before it can be force-purged."));
                return 0;
            }

            String name = subLevel.getName();
            String label = (name != null && !name.isEmpty()) ? name : uuid.toString();
            var bounds = subLevel.boundingBox();
            double volume = Math.max(0, bounds.maxX() - bounds.minX())
                    * Math.max(0, bounds.maxY() - bounds.minY())
                    * Math.max(0, bounds.maxZ() - bounds.minZ());

            source.sendSuccess(() -> Component.literal("DRY RUN FORCE-PURGE for '" + label + "' (" + uuid + "):"), false);
            source.sendSuccess(() -> Component.literal(String.format("  Bounding box volume: ~%.1f (this may be REAL block content - forcepurge does NOT check)", volume)), false);
            source.sendSuccess(() -> Component.literal(
                    "  Would PERMANENTLY delete this sub-level regardless of content, and broadcast to all ops. "
                            + "This cannot be undone. Run with 'confirm', optionally followed by a reason, to execute."), false);
            return 1;
        } catch (Exception e) {
            SableCat.LOGGER.error("forcepurge dry-run failed for uuid {}", uuid, e);
            source.sendFailure(Component.literal("Unexpected error during dry-run: " + e));
            return 0;
        }
    }

    private static int forcePurgeConfirm(CommandContext<CommandSourceStack> context) {
        return sablecat$forcePurgeExecute(context, null);
    }

    private static int forcePurgeConfirmWithReason(CommandContext<CommandSourceStack> context) {
        return sablecat$forcePurgeExecute(context, StringArgumentType.getString(context, "reason"));
    }

    private static int sablecat$forcePurgeExecute(CommandContext<CommandSourceStack> context, String reason) {
        CommandSourceStack source = context.getSource();
        UUID uuid = sablecat$parseUuid(context, source);
        if (uuid == null) return 0;

        try {
            if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
                source.sendFailure(Component.literal("Must be run in a server level context"));
                return 0;
            }

            SubLevelPurge.PurgeResult result = SubLevelPurge.forcePurgeLive(level, uuid, reason);

            if (result.success()) {
                source.sendSuccess(() -> Component.literal(result.message()), true);
                return 1;
            } else {
                source.sendFailure(Component.literal(result.message()));
                return 0;
            }
        } catch (Exception e) {
            SableCat.LOGGER.error("forcepurge confirm failed for uuid {}", uuid, e);
            source.sendFailure(Component.literal("Unexpected error during force-purge: " + e));
            return 0;
        }
    }

    // --- /sablecat backup (periodic archival to an independent library) ---

    private static int backupNow(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
                source.sendFailure(Component.literal("Must be run in a server level context"));
                return 0;
            }

            FixEntry entry = FixRegistry.getFix("sublevel-backup");
            java.nio.file.Path libraryPath = SubLevelBackup.getLibraryPath(level, entry);

            source.sendSuccess(() -> Component.literal("Backing up sub-levels to " + libraryPath + " (this may take a moment)..."), false);

            // Run off the main thread - this walks and zips the whole sub-level
            // storage folder, which shouldn't block the server for a manual,
            // deliberately-triggered admin action.
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                String result = SubLevelBackup.runBackupNow(level, libraryPath, entry);
                level.getServer().execute(() ->
                        source.sendSuccess(() -> Component.literal(result), true));
            });
            return 1;
        } catch (Exception e) {
            SableCat.LOGGER.error("backup now failed", e);
            source.sendFailure(Component.literal("Unexpected error starting backup: " + e));
            return 0;
        }
    }

    private static int backupList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
                source.sendFailure(Component.literal("Must be run in a server level context"));
                return 0;
            }

            FixEntry entry = FixRegistry.getFix("sublevel-backup");
            java.nio.file.Path libraryPath = SubLevelBackup.getLibraryPath(level, entry);
            var backups = SubLevelBackup.listBackups(libraryPath);

            if (backups.isEmpty()) {
                source.sendSuccess(() -> Component.literal("No backups found in " + libraryPath), false);
                return 0;
            }

            source.sendSuccess(() -> Component.literal(backups.size() + " backup(s) in " + libraryPath + ":"), false);
            for (var path : backups) {
                source.sendSuccess(() -> Component.literal("  " + path.getFileName()), false);
            }
            return backups.size();
        } catch (Exception e) {
            SableCat.LOGGER.error("backup list failed", e);
            source.sendFailure(Component.literal("Unexpected error listing backups: " + e));
            return 0;
        }
    }

    private static int backupConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            double intervalHours = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "intervalHours");
            int maxBackupsToKeep = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "maxBackupsToKeep");

            FixEntry entry = FixRegistry.getFix("sublevel-backup");
            if (entry == null) {
                source.sendFailure(Component.literal("sublevel-backup fix not registered"));
                return 0;
            }
            entry.setOption("intervalHours", intervalHours);
            entry.setOption("maxBackupsToKeep", (double) maxBackupsToKeep);
            SableCat.saveConfig();

            source.sendSuccess(() -> Component.literal(
                    "Backup config updated: every " + intervalHours + " hour(s), keeping the newest " + maxBackupsToKeep + " backup(s)."), true);
            return 1;
        } catch (Exception e) {
            SableCat.LOGGER.error("backup config failed", e);
            source.sendFailure(Component.literal("Unexpected error updating backup config: " + e));
            return 0;
        }
    }

    // --- /sablecat import (recover a sub-level from an external offline storage folder) ---

    /**
     * Strips accidental surrounding quote characters. Minecraft's greedyString
     * argument type (used for the folder path here) captures everything typed
     * literally, INCLUDING quote marks - unlike a shell, it does not strip
     * them. A path typed with quotes (a very natural habit) would otherwise
     * contain literal " characters, which are invalid in Windows paths and
     * make Path.of() throw with zero visible error.
     */
    private static String sablecat$stripQuotes(String s) {
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * Parses a folder path argument, reporting a clear, specific error
     * instead of letting Path.of() throw uncaught. This matters more than it
     * looks like it should: in this project's setup, an uncaught exception
     * from inside a command handler produces ONLY the generic client-side
     * "An unexpected error occurred trying to execute that command" message
     * - confirmed by grepping BOTH latest.log and debug.log for a real
     * incident and finding no stack trace in either, at any log level. There
     * is no server-side fallback logging it. Every risky operation in these
     * import commands needs its own explicit handling, or a failure is
     * completely undiagnosable.
     */
    private static java.nio.file.Path sablecat$parseFolder(CommandSourceStack source, String rawArg) {
        String cleaned = sablecat$stripQuotes(rawArg);
        try {
            return java.nio.file.Path.of(cleaned);
        } catch (java.nio.file.InvalidPathException e) {
            source.sendFailure(Component.literal("Invalid path: '" + cleaned + "' - " + e.getReason()));
            return null;
        }
    }

    private static int importScan(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String folderArg = StringArgumentType.getString(context, "folder");
        try {
            java.nio.file.Path folder = sablecat$parseFolder(source, folderArg);
            if (folder == null) return 0;

            source.sendSuccess(() -> Component.literal("Scanning " + folder + " for sub-levels (this may take a moment)..."), false);

            if (!java.nio.file.Files.isDirectory(folder)) {
                source.sendFailure(Component.literal("Not a directory: " + folder));
                return 0;
            }

            java.util.List<java.nio.file.Path> regionFiles;
            try (var stream = java.nio.file.Files.list(folder)) {
                regionFiles = stream.filter(p -> p.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.slvlr")).toList();
            } catch (Exception e) {
                source.sendFailure(Component.literal("Failed to list folder: " + e));
                return 0;
            }

            String cleanedArg = sablecat$stripQuotes(folderArg);
            source.sendSuccess(() -> Component.literal(
                    "Found " + regionFiles.size() + " region file(s). Use /sablecat import <uuid> " + cleanedArg
                            + " to search for a specific UUID (full scan runs per-command, not cached)."), false);
            return regionFiles.size();
        } catch (Exception e) {
            SableCat.LOGGER.error("import scan failed for arg '{}'", folderArg, e);
            source.sendFailure(Component.literal("Unexpected error during scan: " + e));
            return 0;
        }
    }

    private static int importDryRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID uuid = sablecat$parseUuid(context, source);
        if (uuid == null) return 0;
        String folderArg = StringArgumentType.getString(context, "folder");
        try {
            java.nio.file.Path folder = sablecat$parseFolder(source, folderArg);
            if (folder == null) return 0;

            source.sendSuccess(() -> Component.literal("Scanning " + folder + " for " + uuid + " (this may take a moment)..."), false);

            dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData data;
            try {
                data = SubLevelImport.findInExternalStorage(folder, uuid);
            } catch (Exception e) {
                source.sendFailure(Component.literal("Scan failed: " + e));
                return 0;
            }

            if (data == null) {
                source.sendFailure(Component.literal("Not found anywhere in " + folder));
                return 0;
            }

            var tag = data.fullTag();
            String name = tag.contains("display_name") ? tag.getString("display_name") : "(unnamed)";
            int chunkCount = tag.getCompound("plot").getCompound("chunks").getAllKeys().size();
            double[] loc = sablecat$getRescueLocation();

            source.sendSuccess(() -> Component.literal("DRY RUN IMPORT for " + uuid + ":"), false);
            source.sendSuccess(() -> Component.literal("  Name: " + name + " | " + chunkCount + " chunk(s) of block content"), false);
            if (chunkCount == 0) {
                source.sendSuccess(() -> Component.literal("  Would NOT import: found in external storage but has no block content either."), false);
            } else {
                source.sendSuccess(() -> Component.literal(
                        "  Would import to (" + loc[0] + ", " + loc[1] + ", " + loc[2]
                                + ") and save immediately. Run with 'confirm' to execute."), false);
            }
            return 1;
        } catch (Exception e) {
            SableCat.LOGGER.error("import dry-run failed for uuid {} arg '{}'", uuid, folderArg, e);
            source.sendFailure(Component.literal("Unexpected error during scan: " + e));
            return 0;
        }
    }

    private static int importConfirm(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID uuid = sablecat$parseUuid(context, source);
        if (uuid == null) return 0;
        String folderArg = StringArgumentType.getString(context, "folder");
        try {
            java.nio.file.Path folder = sablecat$parseFolder(source, folderArg);
            if (folder == null) return 0;

            if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
                source.sendFailure(Component.literal("Must be run in a server level context"));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("Importing " + uuid + " from " + folder + " (this may take a moment)..."), false);

            double[] loc = sablecat$getRescueLocation();
            SubLevelImport.ImportResult result =
                    SubLevelImport.importSubLevel(level, folder, uuid, loc[0], loc[1], loc[2]);

            if (result.success()) {
                source.sendSuccess(() -> Component.literal(result.message()), true);
                return 1;
            } else {
                source.sendFailure(Component.literal(result.message()));
                return 0;
            }
        } catch (Exception e) {
            SableCat.LOGGER.error("import confirm failed for uuid {} arg '{}'", uuid, folderArg, e);
            source.sendFailure(Component.literal("Unexpected error during import: " + e));
            return 0;
        }
    }
}