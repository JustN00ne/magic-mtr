package org.justnoone.jme.client.data;

import org.justnoone.jme.config.MagicConfigPaths;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class SidingSpeedSliderFileStore {

    private static final Path FILE_PATH = MagicConfigPaths.resolveConfigFile("siding_speed_slider.properties", "jme_siding_speed_slider.properties");
    private static final int JME_MAX_MANUAL_SPEED_SLIDER_KMH = 300;
    private static final Properties PROPERTIES = new Properties();
    private static boolean loaded;

    private SidingSpeedSliderFileStore() {
    }

    public static synchronized Integer get(String sidingId) {
        loadIfNeeded();
        final String value = PROPERTIES.getProperty(sidingId);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static synchronized void set(String sidingId, double kmh) {
        loadIfNeeded();
        final int clamped = Math.max(1, Math.min(JME_MAX_MANUAL_SPEED_SLIDER_KMH, (int) Math.round(kmh)));
        final String key = sidingId;
        final String next = String.valueOf(clamped);
        if (next.equals(PROPERTIES.getProperty(key))) {
            return;
        }
        PROPERTIES.setProperty(key, next);
        save();
    }

    public static synchronized void reloadFromDisk() {
        loaded = false;
        PROPERTIES.clear();
        loadIfNeeded();
    }

    private static void loadIfNeeded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (!Files.exists(FILE_PATH)) {
                return;
            }
            try (InputStream inputStream = Files.newInputStream(FILE_PATH)) {
                PROPERTIES.load(inputStream);
            }
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE_PATH.getParent());
            try (OutputStream outputStream = Files.newOutputStream(FILE_PATH)) {
                PROPERTIES.store(outputStream, "JME siding manual speed cache (km/h)");
            }
        } catch (Exception ignored) {
        }
    }
}
