package eviesaurr.sablecat.fix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import eviesaurr.sablecat.SableCat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SubLevelManifest {

    public static final class Entry {
        public String name;
        public String dimension;
        public double worldX, worldY, worldZ; // last known effective world position
        public int plotChunks;                 // plot chunk count at last sighting
        public long lastSeenMs;
        public String lastEvent;               // "load" or "save"
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private static volatile Path manifestPath = null;
    private static volatile long lastFlushMs = 0;
    private static final long FLUSH_INTERVAL_MS = 5_000L;

    public static void initIfNeeded(MinecraftServer server) {
        Path expected = server.getWorldPath(LevelResource.ROOT).resolve("sablecat_sublevel_manifest.json");
        if (expected.equals(manifestPath)) return;
        init(server);
    }

    public static void init(MinecraftServer server) {
        manifestPath = server.getWorldPath(LevelResource.ROOT).resolve("sablecat_sublevel_manifest.json");
        entries.clear();
        if (Files.exists(manifestPath)) {
            try (Reader reader = Files.newBufferedReader(manifestPath)) {
                Map<UUID, Entry> loaded = GSON.fromJson(reader, new TypeToken<Map<UUID, Entry>>(){}.getType());
                if (loaded != null) entries.putAll(loaded);
                SableCat.LOGGER.info("Sub-level manifest loaded: {} known sub-level(s)", entries.size());
            } catch (Exception e) {
                SableCat.LOGGER.error("Failed to read sub-level manifest, starting fresh", e);
            }
        }
    }

    public static void recordLive(dev.ryanhcode.sable.sublevel.ServerSubLevel subLevel, String event) {
        Entry e = entries.computeIfAbsent(subLevel.getUniqueId(), k -> new Entry());
        var bounds = subLevel.boundingBox();

        String name = subLevel.getName();
        e.name = name != null && !name.isEmpty() ? name : "(unnamed)";
        e.dimension = subLevel.getLevel().dimension().location().toString();
        e.worldX = (bounds.minX() + bounds.maxX()) / 2.0;
        e.worldY = (bounds.minY() + bounds.maxY()) / 2.0;
        e.worldZ = (bounds.minZ() + bounds.maxZ()) / 2.0;
        e.plotChunks = -1; // unknown from live object; filled by save-path sightings
        e.lastSeenMs = System.currentTimeMillis();
        e.lastEvent = event;

        maybeFlush(false);
    }

    public static void record(UUID uuid, String dimension, CompoundTag fullTag, String event) {
        Entry e = entries.computeIfAbsent(uuid, k -> new Entry());

        e.name = fullTag.contains("display_name") ? fullTag.getString("display_name") : e.name != null ? e.name : "(unnamed)";
        if (!"unknown".equals(dimension) || e.dimension == null) e.dimension = dimension;
        e.plotChunks = fullTag.getCompound("plot").getCompound("chunks").getAllKeys().size();
        e.lastSeenMs = System.currentTimeMillis();
        e.lastEvent = event;

        maybeFlush(false);
    }

    public static Map<UUID, Entry> getAll() {
        return new LinkedHashMap<>(entries);
    }

    public static void maybeFlush(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastFlushMs < FLUSH_INTERVAL_MS) return;
        Path path = manifestPath;
        if (path == null) return;
        lastFlushMs = now;
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(entries, writer);
        } catch (IOException e) {
            SableCat.LOGGER.error("Failed to write sub-level manifest", e);
        }
    }
}