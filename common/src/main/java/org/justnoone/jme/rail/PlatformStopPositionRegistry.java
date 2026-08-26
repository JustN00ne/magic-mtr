package org.justnoone.jme.rail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.justnoone.jme.config.MagicConfigPaths;
import org.mtr.core.data.PathData;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PlatformStopPositionRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MagicConfigPaths.resolveConfigFile("platform_stop_positions.json", "jme_platform_stop_positions.json");
    private static final Map<Long, StopPosition> STOP_POSITION_BY_PLATFORM = new ConcurrentHashMap<>();
    private static boolean loaded;

    private PlatformStopPositionRegistry() {
    }

    public static synchronized StopPosition get(long platformId) {
        ensureLoaded();
        return STOP_POSITION_BY_PLATFORM.getOrDefault(platformId, StopPosition.END);
    }

    public static synchronized void set(long platformId, StopPosition stopPosition) {
        ensureLoaded();
        if (platformId == 0) {
            return;
        }

        final StopPosition sanitized = stopPosition == null ? StopPosition.END : stopPosition;
        if (sanitized == StopPosition.END) {
            STOP_POSITION_BY_PLATFORM.remove(platformId);
        } else {
            STOP_POSITION_BY_PLATFORM.put(platformId, sanitized);
        }
        save();
    }

    public static synchronized StopPosition cycle(long platformId) {
        final StopPosition next = get(platformId).next();
        set(platformId, next);
        return next;
    }

    public static double adjustStoppingPoint(PathData pathData, double defaultStoppingPoint, double trainLengthBlocks) {
        if (pathData == null) {
            return defaultStoppingPoint;
        }

        final StopPosition stopPosition = get(pathData.getSavedRailBaseId());
        if (stopPosition == StopPosition.END) {
            return defaultStoppingPoint;
        }

        final double start = pathData.getStartDistance();
        final double end = pathData.getEndDistance();
        final double platformLength = Math.max(0D, end - start);
        final double trainLength = Math.max(0D, trainLengthBlocks);
        final double wanted;
        if (stopPosition == StopPosition.START) {
            wanted = start + trainLength;
        } else {
            wanted = start + (platformLength + trainLength) * 0.5D;
        }

        return Math.max(start, Math.min(end, wanted));
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
        STOP_POSITION_BY_PLATFORM.clear();
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }

        try {
            final JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(CONFIG_PATH), StandardCharsets.UTF_8)).getAsJsonObject();
            for (final Map.Entry<String, JsonElement> entry : root.entrySet()) {
                try {
                    final long platformId = Long.parseUnsignedLong(entry.getKey(), 16);
                    final StopPosition stopPosition = StopPosition.fromString(entry.getValue().getAsString());
                    if (platformId != 0 && stopPosition != StopPosition.END) {
                        STOP_POSITION_BY_PLATFORM.put(platformId, stopPosition);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        try {
            final JsonObject root = new JsonObject();
            STOP_POSITION_BY_PLATFORM.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> root.addProperty(String.format("%016X", entry.getKey()), entry.getValue().serializedId));
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public enum StopPosition {
        END("end", "End"),
        MIDDLE("middle", "Middle"),
        START("start", "Start");

        private final String serializedId;
        private final String displayName;

        StopPosition(String serializedId, String displayName) {
            this.serializedId = serializedId;
            this.displayName = displayName;
        }

        public String getSerializedId() {
            return serializedId;
        }

        public String getDisplayName() {
            return displayName;
        }

        private StopPosition next() {
            switch (this) {
                case END:
                    return MIDDLE;
                case MIDDLE:
                    return START;
                case START:
                default:
                    return END;
            }
        }

        public static StopPosition fromString(String value) {
            final String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
            for (final StopPosition stopPosition : values()) {
                if (stopPosition.serializedId.equals(normalized) || stopPosition.name().toLowerCase(Locale.ENGLISH).equals(normalized)) {
                    return stopPosition;
                }
            }
            return END;
        }
    }
}
