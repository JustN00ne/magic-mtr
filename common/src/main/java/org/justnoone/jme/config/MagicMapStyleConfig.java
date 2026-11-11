package org.justnoone.jme.config;

import org.justnoone.jme.Jme;
import org.mtr.core.Main;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * map-style.yaml: the colours and map styles (infrastructure, speed, electrification, gauge and any custom ones) shared
 * by the web dashboard and the Livemap. The browser evaluates the rules; the server only checks the file parses.
 */
public final class MagicMapStyleConfig {

    private static final Path CONFIG_PATH = MagicConfigPaths.resolveMapConfigFile("map-style.yaml");
    private static final String DEFAULT_RESOURCE = "/assets/jme/dashboard/map-style.default.yaml";

    private static Map<String, Object> style = Collections.emptyMap();
    private static String error = "";
    private static long lastModified = -1;

    private MagicMapStyleConfig() {
    }

    /** The parsed style for the /api/map-style endpoint; re-read whenever the file changes. */
    public static synchronized Map<String, Object> toJsonMap() {
        reloadIfChanged();
        final Map<String, Object> response = new LinkedHashMap<>(style);
        if (!error.isEmpty()) {
            response.put("error", error);
        }
        return response;
    }

    private static void reloadIfChanged() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                writeDefault();
            }
            final long modified = Files.exists(CONFIG_PATH) ? Files.getLastModifiedTime(CONFIG_PATH).toMillis() : 0;
            if (modified == lastModified) {
                return;
            }
            lastModified = modified;
            final String content = Files.exists(CONFIG_PATH) ? new String(Files.readAllBytes(CONFIG_PATH), StandardCharsets.UTF_8) : readDefault();
            final Object root = new Yaml().load(content);
            if (!(root instanceof Map) || !(((Map<?, ?>) root).get("styles") instanceof List)) {
                throw new IllegalArgumentException("the file needs a \"styles:\" list");
            }
            @SuppressWarnings("unchecked") final Map<String, Object> parsed = (Map<String, Object>) root;
            style = parsed;
            error = "";
            Main.LOGGER.info("[{}] Loaded map styles from {}", Jme.MOD_ID, CONFIG_PATH);
        } catch (Exception e) {
            // Keep the last good style (or the defaults) so a typo doesn't blank the map; the page shows the error.
            error = "map-style.yaml: " + e.getMessage();
            Main.LOGGER.warn("[{}] Failed to load {}: {}", Jme.MOD_ID, CONFIG_PATH, e.getMessage());
            if (style.isEmpty()) {
                try {
                    @SuppressWarnings("unchecked") final Map<String, Object> defaults = (Map<String, Object>) new Yaml().load(readDefault());
                    style = defaults;
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void writeDefault() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, readDefault().getBytes(StandardCharsets.UTF_8));
            Main.LOGGER.info("[{}] Created default map-style.yaml at {}", Jme.MOD_ID, CONFIG_PATH);
        } catch (Exception e) {
            Main.LOGGER.warn("[{}] Failed to create default map-style.yaml: {}", Jme.MOD_ID, e.getMessage());
        }
    }

    private static String readDefault() throws Exception {
        try (InputStream inputStream = MagicMapStyleConfig.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException(DEFAULT_RESOURCE + " missing from the jar");
            }
            final java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            final byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, read);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
