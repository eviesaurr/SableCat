package eviesaurr.sablecat.fix;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.mixin.SubLevelHoldingChunkMapInvoker;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports a sub-level from an EXTERNAL, offline copy of Sable's storage
 * folder into the current live world, as a brand new sub-level.
 * <p>
 * Two discovery paths:
 * - findInExternalStorage: normal path, walks the region file's own header
 *   table via Sable's real SubLevelStorage API. Works for any sub-level
 *   whose pointer is still properly indexed.
 * - importFromExactOffset: bypasses the header entirely and reads a
 *   CompoundTag directly from a known raw byte offset within a storage
 *   file. For sub-levels whose data is still physically present and
 *   well-formed, but no longer referenced by any valid header entry
 *   (e.g. orphaned after being moved/reassigned) - confirmed as the real
 *   situation for one specific recovery case via a byte-offset scan that
 *   found complete, correctly sector-aligned, valid NBT data that Sable's
 *   own header-driven scan could not discover. Uses the exact same
 *   NbtIo.readCompressed(InputStream, NbtAccounter) call Sable's own
 *   SubLevelStorageFile.read(int) uses internally, confirmed directly from
 *   decompiled bytecode of the actual jar in use, not guessed.
 */
public final class SubLevelImport {

    public record ImportResult(boolean success, String message) {}

    private static final Pattern REGION_FILE_PATTERN = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.slvlr$");

    public static SubLevelData findInExternalStorage(Path externalFolder, UUID uuid) throws IOException {
        if (!Files.isDirectory(externalFolder)) {
            throw new IOException("Not a directory: " + externalFolder);
        }

        try (SubLevelStorage externalStorage = new SubLevelStorage(externalFolder)) {
            List<Path> regionFiles;
            try (var stream = Files.list(externalFolder)) {
                regionFiles = stream.filter(p -> REGION_FILE_PATTERN.matcher(p.getFileName().toString()).find()).toList();
            }

            SableCat.LOGGER.info("Import scan: found {} region file(s) in {}", regionFiles.size(), externalFolder);

            for (Path regionFile : regionFiles) {
                Matcher m = REGION_FILE_PATTERN.matcher(regionFile.getFileName().toString());
                if (!m.find()) continue;
                int regionX = Integer.parseInt(m.group(1));
                int regionZ = Integer.parseInt(m.group(2));

                for (int cx = 0; cx < 32; cx++) {
                    for (int cz = 0; cz < 32; cz++) {
                        ChunkPos chunkPos = new ChunkPos(regionX * 32 + cx, regionZ * 32 + cz);
                        SubLevelHoldingChunk holdingChunk = externalStorage.attemptLoadHoldingChunk(chunkPos);
                        if (holdingChunk == null) continue;

                        for (SavedSubLevelPointer pointer : holdingChunk.getSubLevelPointers()) {
                            SubLevelData data = externalStorage.attemptLoadSubLevel(chunkPos, pointer);
                            if (data != null && uuid.equals(data.uuid())) {
                                SableCat.LOGGER.info("Import scan: found {} at external chunk {} pointer {}", uuid, chunkPos, pointer);
                                return data;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public static ImportResult importSubLevel(ServerLevel level, Path externalFolder, UUID uuid, double x, double y, double z) {
        SubLevelData data;
        try {
            data = findInExternalStorage(externalFolder, uuid);
        } catch (IOException e) {
            SableCat.LOGGER.error("Import scan failed for {}", uuid, e);
            return new ImportResult(false, "Failed to scan external storage: " + e);
        }

        if (data == null) {
            return new ImportResult(false,
                    "No sub-level with UUID " + uuid + " found anywhere in " + externalFolder
                            + " (scanned every region file present). If you know its exact raw byte offset within a "
                            + "specific storage file, try importFromExactOffset instead - the header index may not "
                            + "reference it even though the data itself is still present.");
        }

        return finishImport(level, data, uuid, x, y, z);
    }

    public static ImportResult importFromExactOffset(ServerLevel level, Path storageFile, long byteOffset, double x, double y, double z) {
        CompoundTag tag;
        try (RandomAccessFile raf = new RandomAccessFile(storageFile.toFile(), "r")) {
            raf.seek(byteOffset);
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Channels.newInputStream(raf.getChannel())))) {
                tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            }
        } catch (IOException e) {
            SableCat.LOGGER.error("Direct-offset read failed at {} offset {}", storageFile, byteOffset, e);
            return new ImportResult(false, "Failed to read NBT at offset " + byteOffset + " in " + storageFile + ": " + e);
        }

        SubLevelData data;
        try {
            data = SubLevelSerializer.fromData(tag);
        } catch (Exception e) {
            SableCat.LOGGER.error("Failed to construct SubLevelData from tag read at offset {}", byteOffset, e);
            return new ImportResult(false, "Read valid NBT at that offset, but it doesn't parse as a sub-level: " + e);
        }

        if (data == null) {
            return new ImportResult(false, "SubLevelSerializer.fromData returned null for the tag at offset " + byteOffset);
        }

        return finishImport(level, data, data.uuid(), x, y, z);
    }

    private static ImportResult finishImport(ServerLevel level, SubLevelData data, UUID uuid, double x, double y, double z) {
        CompoundTag tag = data.fullTag();
        String name = tag.contains("display_name") ? tag.getString("display_name") : "(unnamed)";
        int chunkCount = tag.getCompound("plot").getCompound("chunks").getAllKeys().size();

        if (chunkCount == 0) {
            return new ImportResult(false,
                    "Found " + uuid + " (" + name + "), but it has NO plot block content either "
                            + "- there's nothing to recover. Not importing an empty record.");
        }

        CompoundTag oldPose = tag.getCompound("pose");
        CompoundTag rotPoint = oldPose.getCompound("rotation_point");
        CompoundTag newPose = new CompoundTag();
        newPose.put("position", writeVec(x, y, z));
        newPose.put("rotation_point", rotPoint.copy());
        newPose.put("orientation", oldPose.getCompound("orientation").copy());
        tag.put("pose", newPose);
        if (tag.contains("linear_velocity")) tag.put("linear_velocity", writeVec(0, 0, 0));
        if (tag.contains("angular_velocity")) tag.put("angular_velocity", writeVec(0, 0, 0));

        SableCat.LOGGER.info("Importing {} ({}) to live world at ({}, {}, {})", uuid, name, x, y, z);

        ServerSubLevel imported;
        try {
            imported = SubLevelSerializer.fullyLoad(level, data);
        } catch (Exception e) {
            SableCat.LOGGER.error("Import of {} failed during fullyLoad", uuid, e);
            return new ImportResult(false, "Failed to load imported data into the live world: " + e);
        }

        if (imported == null) {
            return new ImportResult(false,
                    "fullyLoad returned null for " + uuid + " even after repositioning - the recovered data "
                            + "may itself have an issue (check server log for the specific error).");
        }

        try {
            var container = SubLevelContainer.getContainer(level);
            if (container != null) {
                SubLevelHoldingChunkMap holdingMap = container.getHoldingChunkMap();
                var pointer = imported.getLastSerializationPointer();
                if (pointer != null) {
                    holdingMap.getStorage().attemptSaveSubLevel(pointer, data);
                    var mapInvoker = (SubLevelHoldingChunkMapInvoker) holdingMap;
                    SubLevelHoldingChunk liveChunk = mapInvoker.sablecat$getOrLoadHoldingChunk(pointer.chunkPos(), false);
                    if (liveChunk != null) {
                        holdingMap.getStorage().attemptSaveHoldingChunk(pointer.chunkPos(), liveChunk);
                    }
                }
                holdingMap.getStorage().flush();
            }
        } catch (Exception e) {
            SableCat.LOGGER.error("Import of {} loaded successfully but immediate persist failed", uuid, e);
            return new ImportResult(false,
                    "Imported " + uuid + " (" + name + ") and it's live in-world now, but failed to persist to disk "
                            + "immediately: " + e + ". Run /save-all before restarting or it may be lost again.");
        }

        return new ImportResult(true,
                "Imported " + name + " (" + uuid + ") with " + chunkCount + " chunk(s) of block content, "
                        + "placed at (" + x + ", " + y + ", " + z + "), and saved to disk immediately.");
    }

    private static CompoundTag writeVec(double x, double y, double z) {
        CompoundTag t = new CompoundTag();
        t.putDouble("x", x);
        t.putDouble("y", y);
        t.putDouble("z", z);
        return t;
    }
}