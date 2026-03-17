package org.justnoone.jme.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class MagicConfigPaths {

    private static final Path CONFIG_ROOT = resolveMagicRoot();
    private static final Path CONFIGS_DIR = CONFIG_ROOT.resolve("configs");
    private static final Path MAP_DIR = CONFIG_ROOT.resolve("map");
    private static final Path EXPORT_DIR = CONFIG_ROOT.resolve("exports");

    private MagicConfigPaths() {
    }

    public static Path resolveConfigFile(String fileName, String... legacyFileNames) {
        final Path target = CONFIGS_DIR.resolve(fileName);
        migrateLegacyFile(target, legacyFileNames);
        return target;
    }

    public static Path resolveMapFile(String fileName, String... legacyFileNames) {
        final Path target = MAP_DIR.resolve(fileName);
        migrateLegacyFile(target, legacyFileNames);
        return target;
    }

    public static Path resolveExportFile(String fileName) {
        final Path target = EXPORT_DIR.resolve(fileName);
        try {
            Files.createDirectories(target.getParent());
        } catch (Exception ignored) {
        }
        return target;
    }

    private static Path resolveMagicRoot() {
        final Path configDir = FabricLoader.getInstance().getConfigDir();
        final Path uppercase = configDir.resolve("MAGIC");
        final Path lowercase = configDir.resolve("magic");

        try {
            if (Files.exists(lowercase)) {
                if (!Files.exists(uppercase)) {
                    Files.move(lowercase, uppercase, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    migrateDirectoryContents(lowercase, uppercase);
                }
            }
            Files.createDirectories(uppercase);
        } catch (Exception ignored) {
        }

        return uppercase;
    }

    private static void migrateLegacyFile(Path target, String... legacyFileNames) {
        try {
            if (Files.exists(target)) {
                return;
            }

            final Path configDir = FabricLoader.getInstance().getConfigDir();
            for (final String legacyFileName : legacyFileNames) {
                if (legacyFileName == null || legacyFileName.isEmpty()) {
                    continue;
                }

                for (final Path legacyPath : getLegacyCandidates(configDir, legacyFileName)) {
                    if (!Files.exists(legacyPath)) {
                        continue;
                    }

                    Files.createDirectories(target.getParent());
                    Files.move(legacyPath, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static List<Path> getLegacyCandidates(Path configDir, String legacyFileName) {
        final List<Path> candidates = new ArrayList<>();
        final String normalized = legacyFileName.replace('\\', '/');
        candidates.add(configDir.resolve(normalized));
        candidates.add(configDir.resolve("magic").resolve(normalized));
        candidates.add(configDir.resolve("MAGIC").resolve(normalized));

        String fileNameOnly;
        try {
            final Path normalizedPath = Paths.get(normalized);
            final Path fileName = normalizedPath.getFileName();
            fileNameOnly = fileName == null ? normalized : fileName.toString();
        } catch (Exception ignored) {
            final int lastSlash = normalized.lastIndexOf('/');
            fileNameOnly = lastSlash < 0 ? normalized : normalized.substring(lastSlash + 1);
        }
        candidates.add(configDir.resolve("magic").resolve("configs").resolve(fileNameOnly));
        candidates.add(configDir.resolve("magic").resolve("map").resolve(fileNameOnly));
        candidates.add(configDir.resolve("MAGIC").resolve("configs").resolve(fileNameOnly));
        candidates.add(configDir.resolve("MAGIC").resolve("map").resolve(fileNameOnly));
        return candidates;
    }

    private static void migrateDirectoryContents(Path sourceRoot, Path targetRoot) throws IOException {
        if (!Files.exists(sourceRoot)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.forEach(sourcePath -> {
                try {
                    final Path relative = sourceRoot.relativize(sourcePath);
                    if (relative.toString().isEmpty()) {
                        return;
                    }

                    final Path targetPath = targetRoot.resolve(relative.toString());
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                        return;
                    }

                    if (!Files.exists(targetPath)) {
                        Files.createDirectories(targetPath.getParent());
                        Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception ignored) {
                }
            });
        }
    }
}
