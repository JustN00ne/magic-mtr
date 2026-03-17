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

public final class MagicRailTiltRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MagicConfigPaths.resolveConfigFile("rail_tilt.json", "jme_rail_tilt.json");
    private static final ConcurrentHashMap<String, TiltSettings> TILT_BY_RAIL_ID = new ConcurrentHashMap<>();
    private static boolean loaded;

    private MagicRailTiltRegistry() {
    }

    /**
     * Stores tilt settings in a canonical (direction-independent) key so the physical cant does not depend on
     * which endpoint was chosen as the rail "start" when placing the rail.
     */
    public static synchronized void setTiltAbsolute(String railId, int startDegrees, int middleDegrees, int endDegrees) {
        final String canonicalRailId = normalizeRailId(railId);
        if (canonicalRailId.isEmpty()) {
            return;
        }
        ensureLoaded();
        final TiltSettings settings = new TiltSettings(
                MagicRailConstants.clampTiltDegrees(startDegrees),
                MagicRailConstants.clampTiltDegrees(middleDegrees),
                MagicRailConstants.clampTiltDegrees(endDegrees)
        );
        if (settings.startDegrees == 0 && settings.middleDegrees == 0 && settings.endDegrees == 0) {
            removeTilt(canonicalRailId);
            return;
        }
        TILT_BY_RAIL_ID.put(canonicalRailId, settings);
        save();
    }

    /**
     * Backwards-compatible alias for older call sites.
     * <p>
     * Note: this now uses absolute/canonical tilt storage.
     */
    public static synchronized void setTilt(String railId, int startDegrees, int middleDegrees, int endDegrees) {
        setTiltAbsolute(railId, startDegrees, middleDegrees, endDegrees);
    }

    public static synchronized void removeTilt(String railId) {
        final String canonicalRailId = normalizeRailId(railId);
        if (canonicalRailId.isEmpty()) {
            return;
        }
        ensureLoaded();
        TILT_BY_RAIL_ID.remove(canonicalRailId);
        save();
    }

    /**
     * Returns the stored absolute tilt settings for a rail, independent of placement direction.
     */
    public static TiltSettings getTiltAbsolute(String railId) {
        final String canonicalRailId = normalizeRailId(railId);
        if (canonicalRailId.isEmpty()) {
            return null;
        }
        ensureLoaded();
        return TILT_BY_RAIL_ID.get(canonicalRailId);
    }

    /**
     * Returns tilt settings transformed for the given rail id's direction. This is what rendering should use,
     * because left/right orientation flips when the rail direction flips.
     */
    public static TiltSettings getTiltForRail(String railId) {
        if (railId == null || railId.isEmpty()) {
            return null;
        }

        final String canonicalRailId = normalizeRailId(railId);
        if (canonicalRailId.isEmpty()) {
            return null;
        }

        final TiltSettings canonical = getTiltAbsolute(canonicalRailId);
        if (canonical == null) {
            return null;
        }

        if (canonicalRailId.equals(railId)) {
            return canonical;
        }

        // Convert canonical tilt to the reversed rail's local orientation.
        return new TiltSettings(-canonical.endDegrees, -canonical.middleDegrees, -canonical.startDegrees);
    }

    /**
     * Backwards-compatible alias for older call sites: returns per-rail-direction settings.
     */
    public static TiltSettings getTilt(String railId) {
        return getTiltForRail(railId);
    }

    public static Map<String, TiltSettings> getAll() {
        ensureLoaded();
        return TILT_BY_RAIL_ID;
    }

    public static double interpolateDegrees(TiltSettings settings, double progress) {
        if (settings == null) {
            return 0;
        }
        final double clampedProgress = Math.max(0, Math.min(1, progress));
        if (clampedProgress <= 0.5) {
            return lerp(settings.startDegrees, settings.middleDegrees, clampedProgress * 2);
        } else {
            return lerp(settings.middleDegrees, settings.endDegrees, (clampedProgress - 0.5) * 2);
        }
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
        TILT_BY_RAIL_ID.clear();
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }

        try {
            final JsonElement element = new JsonParser().parse(readText(CONFIG_PATH));
            if (!element.isJsonObject()) {
                return;
            }
            final JsonObject root = element.getAsJsonObject();
            final ConcurrentHashMap<String, LoadedTiltEntry> loadedByCanonicalId = new ConcurrentHashMap<>();
            root.entrySet().forEach(entry -> {
                final String rawRailId = entry.getKey();
                final JsonElement value = entry.getValue();
                if (rawRailId == null || rawRailId.isEmpty() || !value.isJsonObject()) {
                    return;
                }

                final JsonObject settingsObject = value.getAsJsonObject();
                try {
                    final int start = MagicRailConstants.clampTiltDegrees(settingsObject.get("start").getAsInt());
                    final int middle = MagicRailConstants.clampTiltDegrees(settingsObject.get("middle").getAsInt());
                    final int end = MagicRailConstants.clampTiltDegrees(settingsObject.get("end").getAsInt());
                    final TiltSettings parsedSettings = new TiltSettings(start, middle, end);

                    final String canonicalRailId = normalizeRailId(rawRailId);
                    if (canonicalRailId.isEmpty()) {
                        return;
                    }

                    final boolean isCanonical = canonicalRailId.equals(rawRailId);
                    final TiltSettings canonicalSettings = isCanonical ? parsedSettings : toCanonicalFromReversed(parsedSettings);

                    loadedByCanonicalId.compute(canonicalRailId, (unused, existing) -> {
                        if (existing == null) {
                            return new LoadedTiltEntry(canonicalSettings, isCanonical);
                        }
                        // Prefer canonical entries over reversed-derived entries when both exist.
                        if (!existing.isCanonicalSource && isCanonical) {
                            return new LoadedTiltEntry(canonicalSettings, true);
                        }
                        return existing;
                    });
                } catch (Exception ignored) {
                }
            });

            loadedByCanonicalId.forEach((canonicalRailId, entry) -> {
                if (entry != null && entry.settings != null) {
                    TILT_BY_RAIL_ID.put(canonicalRailId, entry.settings);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        final JsonObject root = new JsonObject();
        TILT_BY_RAIL_ID.forEach((key, settings) -> {
            final JsonObject settingsObject = new JsonObject();
            settingsObject.addProperty("start", settings.startDegrees);
            settingsObject.addProperty("middle", settings.middleDegrees);
            settingsObject.addProperty("end", settings.endDegrees);
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
            final boolean alreadyCanonical = x1 < x2
                    || (x1 == x2 && (y1 < y2 || (y1 == y2 && z1 <= z2)));
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

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static TiltSettings toCanonicalFromReversed(TiltSettings reversedSettings) {
        if (reversedSettings == null) {
            return null;
        }
        return new TiltSettings(-reversedSettings.endDegrees, -reversedSettings.middleDegrees, -reversedSettings.startDegrees);
    }

    private static final class LoadedTiltEntry {
        private final TiltSettings settings;
        private final boolean isCanonicalSource;

        private LoadedTiltEntry(TiltSettings settings, boolean isCanonicalSource) {
            this.settings = settings;
            this.isCanonicalSource = isCanonicalSource;
        }
    }

    public static final class TiltSettings {
        public final int startDegrees;
        public final int middleDegrees;
        public final int endDegrees;

        public TiltSettings(int startDegrees, int middleDegrees, int endDegrees) {
            this.startDegrees = startDegrees;
            this.middleDegrees = middleDegrees;
            this.endDegrees = endDegrees;
        }
    }
}
