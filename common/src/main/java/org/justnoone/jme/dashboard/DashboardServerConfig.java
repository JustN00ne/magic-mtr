package org.justnoone.jme.dashboard;

import org.justnoone.jme.Jme;
import org.justnoone.jme.config.MagicConfigPaths;
import org.mtr.core.Main;
import org.mtr.libraries.com.google.gson.Gson;
import org.mtr.libraries.com.google.gson.GsonBuilder;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Network settings for the MAGIC web dashboard / Livemap.
 * Stored at {@code config/MAGIC/configs/dashboard_server.json}.
 *
 * <pre>{
 *   "bindAddress": "auto",
 *   "port": 8088
 * }</pre>
 *
 * {@code "auto"} listens on every interface on a dedicated server (so players can open the map) and on
 * loopback only in singleplayer / LAN hosts. Any other value is used as the literal bind address
 * (e.g. {@code "0.0.0.0"} or {@code "127.0.0.1"}). The JVM properties {@code -Dmagic.dashboard.bind}
 * and {@code -Dmagic.dashboard.port} override the file.
 */
public final class DashboardServerConfig {

    public static final String AUTO = "auto";
    private static final int DEFAULT_PORT = 8088;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE_PATH = MagicConfigPaths.resolveConfigFile("dashboard_server.json");

    public final String bindAddress;
    public final int port;

    private DashboardServerConfig(String bindAddress, int port) {
        this.bindAddress = bindAddress;
        this.port = port;
    }

    public static DashboardServerConfig load() {
        String bindAddress = AUTO;
        int port = DEFAULT_PORT;

        if (Files.isRegularFile(FILE_PATH)) {
            try {
                final JsonObject root = JsonParser.parseString(new String(Files.readAllBytes(FILE_PATH), StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("bindAddress")) {
                    bindAddress = root.get("bindAddress").getAsString().trim();
                }
                if (root.has("port")) {
                    port = root.get("port").getAsInt();
                }
            } catch (Exception e) {
                Main.LOGGER.error("[{}] Failed to read dashboard_server.json - using defaults", Jme.MOD_ID, e);
            }
        } else {
            final JsonObject root = new JsonObject();
            root.addProperty("bindAddress", AUTO);
            root.addProperty("port", DEFAULT_PORT);
            try {
                Files.createDirectories(FILE_PATH.getParent());
                Files.write(FILE_PATH, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                Main.LOGGER.warn("[{}] Failed to write default dashboard_server.json", Jme.MOD_ID, e);
            }
        }

        final String bindProperty = System.getProperty("magic.dashboard.bind");
        if (bindProperty != null && !bindProperty.trim().isEmpty()) {
            bindAddress = bindProperty.trim();
        }
        final String portProperty = System.getProperty("magic.dashboard.port");
        if (portProperty != null && !portProperty.trim().isEmpty()) {
            try {
                port = Integer.parseInt(portProperty.trim());
            } catch (NumberFormatException e) {
                Main.LOGGER.warn("[{}] Invalid -Dmagic.dashboard.port '{}' - using {}", Jme.MOD_ID, portProperty, port);
            }
        }
        if (port < 1 || port > 65535) {
            Main.LOGGER.warn("[{}] Dashboard port {} out of range - using {}", Jme.MOD_ID, port, DEFAULT_PORT);
            port = DEFAULT_PORT;
        }
        if (bindAddress.isEmpty()) {
            bindAddress = AUTO;
        }
        return new DashboardServerConfig(bindAddress, port);
    }
}
