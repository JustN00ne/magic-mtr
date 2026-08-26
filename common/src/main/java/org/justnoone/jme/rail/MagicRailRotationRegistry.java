package org.justnoone.jme.rail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.justnoone.jme.config.MagicConfigPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MagicRailRotationRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MagicConfigPaths.resolveConfigFile("jme_rail_rotation.json", "rail_rotation.json");
    private static final ConcurrentHashMap<String, RotationSettings> ROTATION_BY_RAIL_ID = new ConcurrentHashMap<>();
    private static boolean loaded;

    private MagicRailRotationRegistry() {
    }

    public static synchronized void setRotation(String railId, double offset1Degrees, double offset2Degrees) {
        final String canonicalRailId = normalizeRailId(railId);
        if (canonicalRailId.isEmpty()) {
            return;
        }
        ensureLoaded();

        final double clampedStart = clampRotationDegrees(offset1Degrees);
        final double clampedEnd = clampRotationDegrees(offset2Degrees);

        if (clampedStart == 0.0 && clampedEnd == 0.0) {
            removeRotation(canonicalRailId);
            return;
        }

        final RotationSettings settings;
        if (canonicalRailId.equals(railId)) {
            settings = new RotationSettings(clampedStart, clampedEnd);
        } else {
            // When setting via reversed ID, we swap endpoints
            settings = new RotationSettings(clampedEnd, clampedStart);
        }

        ROTATION_BY_RAIL_ID.put(canonicalRailId, settings);
        save();
    }

    public static synchronized void removeRotation(String railId) {
        final String canonicalRailId = normalizeRailId(railId);
        if (canonicalRailId.isEmpty()) {
            return;
        }
        ensureLoaded();
        ROTATION_BY_RAIL_ID.remove(canonicalRailId);
        save();
    }

    public static RotationSettings getRotation(String railId) {
        if (railId == null || railId.isEmpty()) {
            return null;
        }

        final String canonicalRailId = normalizeRailId(railId);
        if (canonicalRailId.isEmpty()) {
            return null;
        }

        ensureLoaded();
        final RotationSettings canonical = ROTATION_BY_RAIL_ID.get(canonicalRailId);
        if (canonical == null) {
            return null;
        }

        if (canonicalRailId.equals(railId)) {
            return canonical;
        }

        // Convert canonical rotation to the reversed rail's local orientation.
        return new RotationSettings(canonical.angleOffset2Degrees, canonical.angleOffset1Degrees);
    }

    public static Map<String, RotationSettings> getAll() {
        ensureLoaded();
        return ROTATION_BY_RAIL_ID;
    }

    public static double interpolateDegrees(RotationSettings settings, double progress) {
        if (settings == null) {
            return 0;
        }
        final double clampedProgress = Math.max(0, Math.min(1, progress));
        return lerp(settings.angleOffset1Degrees, settings.angleOffset2Degrees, clampedProgress);
    }

    public static synchronized void reloadFromDisk() {
        loaded = true;
        load();
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    private static void load() {
        ROTATION_BY_RAIL_ID.clear();
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }

        try {
            final JsonElement element = new JsonParser().parse(readText(CONFIG_PATH));
            if (!element.isJsonObject()) {
                return;
            }
            final JsonObject root = element.getAsJsonObject();
            final ConcurrentHashMap<String, LoadedRotationEntry> loadedByCanonicalId = new ConcurrentHashMap<>();
            root.entrySet().forEach(entry -> {
                final String rawRailId = entry.getKey();
                final JsonElement value = entry.getValue();
                if (rawRailId == null || rawRailId.isEmpty() || !value.isJsonObject()) {
                    return;
                }

                final JsonObject settingsObject = value.getAsJsonObject();
                try {
                    final double start = clampRotationDegrees(settingsObject.get("start").getAsDouble());
                    final double end = clampRotationDegrees(settingsObject.get("end").getAsDouble());
                    final RotationSettings parsedSettings = new RotationSettings(start, end);

                    final String canonicalRailId = normalizeRailId(rawRailId);
                    if (canonicalRailId.isEmpty()) {
                        return;
                    }

                    final boolean isCanonical = canonicalRailId.equals(rawRailId);
                    final RotationSettings canonicalSettings = isCanonical ? parsedSettings : new RotationSettings(parsedSettings.angleOffset2Degrees, parsedSettings.angleOffset1Degrees);

                    loadedByCanonicalId.compute(canonicalRailId, (unused, existing) -> {
                        if (existing == null) {
                            return new LoadedRotationEntry(canonicalSettings, isCanonical);
                        }
                        // Prefer canonical entries over reversed-derived entries when both exist.
                        if (!existing.isCanonicalSource && isCanonical) {
                            return new LoadedRotationEntry(canonicalSettings, true);
                        }
                        return existing;
                    });
                } catch (Exception ignored) {
                }
            });

            loadedByCanonicalId.forEach((canonicalRailId, entry) -> {
                if (entry != null && entry.settings != null) {
                    ROTATION_BY_RAIL_ID.put(canonicalRailId, entry.settings);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        final JsonObject root = new JsonObject();
        ROTATION_BY_RAIL_ID.forEach((key, settings) -> {
            final JsonObject settingsObject = new JsonObject();
            settingsObject.addProperty("start", settings.angleOffset1Degrees);
            settingsObject.addProperty("end", settings.angleOffset2Degrees);
            root.add(key, settingsObject);
        });

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private static String readText(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    public static String normalizeRailId(String railId) {
        if (railId == null || railId.isEmpty()) {
            return "";
        }
        final String[] split = railId.split("-");
        if (split.length != 6) {
            return railId;
        }

        try {
            final long x1 = Long.parseUnsignedLong(split[0], 16);
            final long y1 = Long.parseUnsignedLong(split[1], 16);
            final long z1 = Long.parseUnsignedLong(split[2], 16);
            final long x2 = Long.parseUnsignedLong(split[3], 16);
            final long y2 = Long.parseUnsignedLong(split[4], 16);
            final long z2 = Long.parseUnsignedLong(split[5], 16);

            // Match MTR's TwoPositionsBase.getHexId ordering: Position.compareTo compares x, then y, then z as signed longs.
            final boolean alreadyCanonical = x1 < x2 || (x1 == x2 && (y1 < y2 || (y1 == y2 && z1 <= z2)));
            return alreadyCanonical ? railId : reverseRailId(railId);
        } catch (Exception ignored) {
            return railId;
        }
    }

    private static String reverseRailId(String railId) {
        if (railId == null || railId.isEmpty()) {
            return "";
        }
        final String[] split = railId.split("-");
        if (split.length != 6) {
            return railId;
        }
        return split[3] + "-" + split[4] + "-" + split[5] + "-" + split[0] + "-" + split[1] + "-" + split[2];
    }

    private static double clampRotationDegrees(double degrees) {
        return Math.max(MagicRailConstants.MIN_ROTATION_DEGREES, Math.min(MagicRailConstants.MAX_ROTATION_DEGREES, degrees));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static final class LoadedRotationEntry {
        private final RotationSettings settings;
        private final boolean isCanonicalSource;

        private LoadedRotationEntry(RotationSettings settings, boolean isCanonicalSource) {
            this.settings = settings;
            this.isCanonicalSource = isCanonicalSource;
        }
    }

    public static final class RotationSettings {
        public final double angleOffset1Degrees;
        public final double angleOffset2Degrees;

        public RotationSettings(double angleOffset1Degrees, double angleOffset2Degrees) {
            this.angleOffset1Degrees = angleOffset1Degrees;
            this.angleOffset2Degrees = angleOffset2Degrees;
        }
    }
}
