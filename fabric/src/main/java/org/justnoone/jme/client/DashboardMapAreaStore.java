package org.justnoone.jme.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.justnoone.jme.config.MagicConfigPaths;
import org.justnoone.jme.mixin.WidgetMapAccessor;
import org.mtr.mod.screen.WorldMap;
import org.mtr.mod.screen.WidgetMap;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DashboardMapAreaStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MagicConfigPaths.resolveConfigFile("dashboard_areas.lzma2", "dashboard_areas.json", "dashboard_areas.json.lzma2");
    private static final int MAX_AREAS = 128;
    private static final double MIN_SCALE = 1D / 128D;
    private static final double MAX_SCALE = 64D;
    private static final double MIN_CENTER_DELTA = 2D;
    private static final double MIN_SCALE_DELTA = 0.01D;

    private static final List<MapArea> mapAreas = new ArrayList<>();
    private static boolean loaded;
    private static int currentIndex = -1;

    private DashboardMapAreaStore() {
    }

    public static synchronized boolean restoreLatest(WidgetMap widgetMap) {
        if (widgetMap == null) {
            return false;
        }

        ensureLoaded();
        if (mapAreas.isEmpty()) {
            return false;
        }

        currentIndex = mapAreas.size() - 1;
        applyToMap(widgetMap, mapAreas.get(currentIndex));
        return true;
    }

    public static synchronized boolean autoSaveCurrent(WidgetMap widgetMap) {
        if (widgetMap == null) {
            return false;
        }

        ensureLoaded();
        final MapArea capturedArea = captureCurrent(widgetMap);
        if (capturedArea == null) {
            return false;
        }

        if (!mapAreas.isEmpty() && isSameArea(mapAreas.get(mapAreas.size() - 1), capturedArea)) {
            return false;
        }

        addArea(capturedArea);
        save();
        return true;
    }

    public static synchronized boolean saveCurrent(WidgetMap widgetMap) {
        if (widgetMap == null) {
            return false;
        }

        ensureLoaded();
        final MapArea capturedArea = captureCurrent(widgetMap);
        if (capturedArea == null) {
            return false;
        }
        addArea(capturedArea);
        save();
        return true;
    }

    public static synchronized boolean loadNext(WidgetMap widgetMap) {
        if (widgetMap == null) {
            return false;
        }

        ensureLoaded();
        if (mapAreas.isEmpty()) {
            return false;
        }

        currentIndex = (currentIndex + 1) % mapAreas.size();
        applyToMap(widgetMap, mapAreas.get(currentIndex));
        return true;
    }

    public static synchronized int getCount() {
        ensureLoaded();
        return mapAreas.size();
    }

    private static void applyToMap(WidgetMap widgetMap, MapArea mapArea) {
        final WidgetMapAccessor accessor = (WidgetMapAccessor) (Object) widgetMap;
        accessor.jme$setCenterX(mapArea.centerX);
        accessor.jme$setCenterY(mapArea.centerY);
        accessor.jme$setScale(clampScale(mapArea.scale));
        widgetMap.setMapOverlayMode(mapArea.topView ? WorldMap.MapOverlayMode.TOP_VIEW : WorldMap.MapOverlayMode.CURRENT_Y);
    }

    private static MapArea captureCurrent(WidgetMap widgetMap) {
        final WidgetMapAccessor accessor = (WidgetMapAccessor) (Object) widgetMap;
        final double centerX = accessor.jme$getCenterX();
        final double centerY = accessor.jme$getCenterY();
        final double scale = clampScale(accessor.jme$getScale());
        final boolean topView = widgetMap.isMapOverlayMode(WorldMap.MapOverlayMode.TOP_VIEW);
        final String name = "Area " + (mapAreas.size() + 1);
        return new MapArea(name, centerX, centerY, scale, topView);
    }

    private static void addArea(MapArea mapArea) {
        mapAreas.add(mapArea);
        while (mapAreas.size() > MAX_AREAS) {
            mapAreas.remove(0);
        }
        currentIndex = mapAreas.size() - 1;
    }

    private static boolean isSameArea(MapArea previous, MapArea current) {
        if (previous == null || current == null) {
            return false;
        }
        if (previous.topView != current.topView) {
            return false;
        }
        final double centerDelta = Math.hypot(previous.centerX - current.centerX, previous.centerY - current.centerY);
        final double scaleDelta = Math.abs(previous.scale - current.scale);
        return centerDelta < MIN_CENTER_DELTA && scaleDelta < MIN_SCALE_DELTA;
    }

    private static double clampScale(double scale) {
        if (!Double.isFinite(scale)) {
            return 1D;
        }
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    private static void load() {
        mapAreas.clear();
        currentIndex = -1;

        if (!Files.exists(CONFIG_PATH)) {
            return;
        }

        try {
            final byte[] compressedBytes = Files.readAllBytes(CONFIG_PATH);
            final String json = readLzma2Text(compressedBytes);
            if (json == null || json.isEmpty()) {
                return;
            }

            final JsonElement parsed = new JsonParser().parse(json);
            if (!parsed.isJsonObject()) {
                return;
            }

            final JsonObject root = parsed.getAsJsonObject();
            if (!root.has("areas") || !root.get("areas").isJsonArray()) {
                return;
            }

            final JsonArray areas = root.getAsJsonArray("areas");
            areas.forEach(areaElement -> {
                if (!areaElement.isJsonObject()) {
                    return;
                }

                final JsonObject areaObject = areaElement.getAsJsonObject();
                final String name = areaObject.has("name") ? areaObject.get("name").getAsString() : "Area";
                final double centerX = areaObject.has("center_x") ? areaObject.get("center_x").getAsDouble() : 0D;
                final double centerY = areaObject.has("center_y") ? areaObject.get("center_y").getAsDouble() : 0D;
                final double scale = areaObject.has("scale") ? areaObject.get("scale").getAsDouble() : 1D;
                final boolean topView = !areaObject.has("top_view") || areaObject.get("top_view").getAsBoolean();
                mapAreas.add(new MapArea(name, centerX, centerY, clampScale(scale), topView));
            });
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        final JsonObject root = new JsonObject();
        final JsonArray areas = new JsonArray();

        mapAreas.forEach(mapArea -> {
            final JsonObject areaObject = new JsonObject();
            areaObject.addProperty("name", mapArea.name);
            areaObject.addProperty("center_x", mapArea.centerX);
            areaObject.addProperty("center_y", mapArea.centerY);
            areaObject.addProperty("scale", mapArea.scale);
            areaObject.addProperty("top_view", mapArea.topView);
            areas.add(areaObject);
        });

        root.add("areas", areas);

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            final byte[] rawJson = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
            Files.write(CONFIG_PATH, writeLzma2Bytes(rawJson));
        } catch (Exception ignored) {
        }
    }

    private static String readLzma2Text(byte[] compressedBytes) {
        if (compressedBytes == null || compressedBytes.length == 0) {
            return "";
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedBytes);
             XZInputStream xzInputStream = new XZInputStream(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[4096];
            int read;
            while ((read = xzInputStream.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            // Backward-compatibility fallback for uncompressed legacy data.
            return new String(compressedBytes, StandardCharsets.UTF_8);
        }
    }

    private static byte[] writeLzma2Bytes(byte[] rawBytes) throws IOException {
        final LZMA2Options options = new LZMA2Options();
        options.setPreset(6);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             XZOutputStream xzOutputStream = new XZOutputStream(outputStream, options)) {
            xzOutputStream.write(rawBytes);
            xzOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

    private static final class MapArea {
        private final String name;
        private final double centerX;
        private final double centerY;
        private final double scale;
        private final boolean topView;

        private MapArea(String name, double centerX, double centerY, double scale, boolean topView) {
            this.name = name;
            this.centerX = centerX;
            this.centerY = centerY;
            this.scale = scale;
            this.topView = topView;
        }
    }
}
