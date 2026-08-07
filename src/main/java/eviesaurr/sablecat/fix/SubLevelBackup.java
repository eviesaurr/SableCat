package eviesaurr.sablecat.fix;

import eviesaurr.sablecat.SableCat;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Periodically archives the live sub-level storage folder into a separate,
 * dedicated library - timestamped, never-overwritten .zip files, completely
 * independent of the live world's own save state.
 * <p>
 * This exists because the recovery work done this session (importing
 * possum2.0, A CAB, Lift-o, HOTSTUFF) only worked because a manual snapshot
 * happened to be taken and sent over before a rollback. Without that, none
 * of it would have been recoverable. This automates taking that snapshot
 * regularly, so a future incident doesn't depend on someone remembering to
 * manually zip and share a folder before disaster strikes.
 * <p>
 * Uses plain java.util.zip (no external dependencies) rather than tar.gz -
 * functionally equivalent for this purpose (compressed, timestamped,
 * append-only archive of a folder) and avoids needing to hand-roll TAR
 * format or pull in a new library dependency.
 */
public final class SubLevelBackup {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final Pattern BACKUP_FILE_PATTERN = Pattern.compile("sublevels_backup_.*\\.zip$");

    /**
     * Checks whether enough real time has passed since the newest existing
     * backup, and if so, runs a new one on the IO executor (never blocks the
     * caller). Intended to be called from an already-firing hook (e.g. at the
     * end of saveAll()) rather than requiring new scheduling infrastructure.
     * Reads the interval/library-path/retention config fresh each call, so
     * changes via /sablecat backup config take effect without a restart.
     */
    public static void maybeRunScheduledBackup(ServerLevel level, java.util.concurrent.ExecutorService ioExecutor) {
        if (!FixRegistry.isEnabled("sublevel-backup")) return;

        FixEntry entry = FixRegistry.getFix("sublevel-backup");
        double intervalHours = entry != null ? ((Number) entry.getOption("intervalHours", 24.0)).doubleValue() : 24.0;

        Path libraryPath = getLibraryPath(level, entry);
        try {
            Files.createDirectories(libraryPath);
        } catch (IOException e) {
            SableCat.LOGGER.error("Could not create sub-level backup library directory at {}", libraryPath, e);
            return;
        }

        long newestBackupMillis = findNewestBackupTimestamp(libraryPath);
        long intervalMillis = (long) (intervalHours * 3_600_000L);
        if (newestBackupMillis > 0 && System.currentTimeMillis() - newestBackupMillis < intervalMillis) {
            return; // not due yet
        }

        ioExecutor.execute(() -> runBackupNow(level, libraryPath, entry));
    }

    /** Runs a backup immediately, regardless of schedule. Safe to call from a command (runs synchronously on the calling thread - callers on the main thread should dispatch to an executor themselves if they don't want to block). */
    public static String runBackupNow(ServerLevel level, Path libraryPath, FixEntry entry) {
        try {
            Files.createDirectories(libraryPath);
        } catch (IOException e) {
            SableCat.LOGGER.error("Could not create sub-level backup library directory at {}", libraryPath, e);
            return "Could not create library directory at " + libraryPath + ": " + e;
        }

        var container = SubLevelContainer.getContainer(level);
        if (container == null) {
            SableCat.LOGGER.error("Sub-level backup skipped - no container for this dimension");
            return "No sub-level container for this dimension - backup skipped.";
        }

        Path sourceFolder = container.getHoldingChunkMap().getStorage().getFolder();
        if (!Files.isDirectory(sourceFolder)) {
            SableCat.LOGGER.error("Sub-level backup skipped - source folder {} doesn't exist", sourceFolder);
            return "Source sub-level folder " + sourceFolder + " doesn't exist - backup skipped.";
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        Path target = libraryPath.resolve("sublevels_backup_" + timestamp + ".zip");

        try {
            zipFolder(sourceFolder, target);
        } catch (IOException e) {
            SableCat.LOGGER.error("Sub-level backup to {} failed", target, e);
            return "Backup failed: " + e;
        }

        SableCat.LOGGER.info("Sub-level backup written to {}", target);

        int maxToKeep = entry != null ? ((Number) entry.getOption("maxBackupsToKeep", 14.0)).intValue() : 14;
        pruneOldBackups(libraryPath, maxToKeep);

        return "Backup written to " + target;
    }

    private static void zipFolder(Path sourceFolder, Path zipTarget) throws IOException {
        // Write to a .tmp file first, then atomically move into place - so a backup
        // that fails or gets interrupted partway through never leaves a corrupt,
        // half-written .zip sitting in the library looking like a real backup.
        Path tmpTarget = zipTarget.resolveSibling(zipTarget.getFileName() + ".tmp");
        try (OutputStream fos = Files.newOutputStream(tmpTarget);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            try (var stream = Files.walk(sourceFolder)) {
                for (Path path : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                    String entryName = sourceFolder.relativize(path).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        }
        Files.move(tmpTarget, zipTarget, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static long findNewestBackupTimestamp(Path libraryPath) {
        long newest = 0;
        try (var stream = Files.list(libraryPath)) {
            for (Path p : (Iterable<Path>) stream.filter(f -> BACKUP_FILE_PATTERN.matcher(f.getFileName().toString()).find())::iterator) {
                BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                long modified = attrs.lastModifiedTime().toMillis();
                if (modified > newest) newest = modified;
            }
        } catch (IOException ignored) {
            // No existing backups (or unreadable directory) - treat as "none exist yet"
        }
        return newest;
    }

    private static void pruneOldBackups(Path libraryPath, int maxToKeep) {
        if (maxToKeep <= 0) return; // 0 or negative = keep everything, never prune
        try (var stream = Files.list(libraryPath)) {
            List<Path> backups = new ArrayList<>();
            stream.filter(f -> BACKUP_FILE_PATTERN.matcher(f.getFileName().toString()).find()).forEach(backups::add);
            backups.sort(Comparator.comparing(p -> p.getFileName().toString())); // timestamp-named, so lexical sort = chronological
            while (backups.size() > maxToKeep) {
                Path oldest = backups.remove(0);
                try {
                    Files.delete(oldest);
                    SableCat.LOGGER.info("Pruned old sub-level backup {} (keeping newest {})", oldest, maxToKeep);
                } catch (IOException e) {
                    SableCat.LOGGER.error("Failed to prune old backup {}", oldest, e);
                }
            }
        } catch (IOException e) {
            SableCat.LOGGER.error("Failed to list backups for pruning in {}", libraryPath, e);
        }
    }

    /** Library defaults to a folder sibling to the live "sublevels" folder, named "sablecat_sublevel_library". */
    public static Path getLibraryPath(ServerLevel level, FixEntry entry) {
        String configured = entry != null ? (String) entry.getOption("libraryPath", "") : "";
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        var container = SubLevelContainer.getContainer(level);
        if (container != null) {
            Path sourceFolder = container.getHoldingChunkMap().getStorage().getFolder();
            return sourceFolder.resolveSibling("sablecat_sublevel_library");
        }
        return Path.of("sablecat_sublevel_library");
    }

    public static List<Path> listBackups(Path libraryPath) {
        List<Path> result = new ArrayList<>();
        try (var stream = Files.list(libraryPath)) {
            stream.filter(f -> BACKUP_FILE_PATTERN.matcher(f.getFileName().toString()).find())
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(result::add);
        } catch (IOException ignored) {
        }
        return result;
    }
}