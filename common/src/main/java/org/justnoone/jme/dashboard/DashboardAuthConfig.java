package org.justnoone.jme.dashboard;

import org.justnoone.jme.Jme;
import org.justnoone.jme.config.MagicConfigPaths;
import org.mtr.core.Main;
import org.mtr.libraries.com.google.gson.Gson;
import org.mtr.libraries.com.google.gson.GsonBuilder;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Whitelist config for dashboard edit access.
 * Stored at {@code config/MAGIC/configs/dashboard_auth.json}.
 *
 * <pre>{
 *   "editors": [
 *     {"uuid": "550e8400-...", "username": "JustNoone"}
 *   ]
 * }</pre>
 *
 * OP level 2 players are always authorized (no need to be in the list).
 */
public final class DashboardAuthConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE_PATH = MagicConfigPaths.resolveConfigFile("dashboard_auth.json");

    private final Set<UUID> editorUUIDs = new HashSet<>();

    private DashboardAuthConfig() {
    }

    /**
     * Check if the given UUID is in the editors whitelist.
     */
    public boolean isEditor(UUID uuid) {
        return editorUUIDs.contains(uuid);
    }

    /**
     * Get the set of editor UUIDs (unmodifiable view).
     */
    public Set<UUID> getEditorUUIDs() {
        return Collections.unmodifiableSet(new HashSet<>(editorUUIDs));
    }

    /**
     * Load the config from disk. Returns a default config if the file doesn't exist.
     */
    public static DashboardAuthConfig load() {
        final DashboardAuthConfig config = new DashboardAuthConfig();
        if (!Files.isRegularFile(FILE_PATH)) {
            config.save(); // Write default file
            return config;
        }

        try {
            final String json = new String(Files.readAllBytes(FILE_PATH), StandardCharsets.UTF_8);
            final JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (root.has("editors") && root.get("editors").isJsonArray()) {
                final JsonArray editors = root.getAsJsonArray("editors");
                for (final JsonElement el : editors) {
                    if (el.isJsonObject()) {
                        final JsonObject editorObj = el.getAsJsonObject();
                        if (editorObj.has("uuid")) {
                            try {
                                config.editorUUIDs.add(UUID.fromString(editorObj.get("uuid").getAsString()));
                            } catch (IllegalArgumentException ignored) {
                                Main.LOGGER.warn("[{}] Invalid UUID in dashboard_auth.json editors: {}",
                                        Jme.MOD_ID, editorObj.get("uuid").getAsString());
                            }
                        }
                    }
                }
            }

            Main.LOGGER.info("[{}] Loaded dashboard auth config: {} editor(s)", Jme.MOD_ID, config.editorUUIDs.size());
        } catch (Exception e) {
            Main.LOGGER.error("[{}] Failed to load dashboard_auth.json", Jme.MOD_ID, e);
        }

        return config;
    }

    /**
     * Save the current config to disk.
     */
    public void save() {
        final JsonObject root = new JsonObject();
        final JsonArray editors = new JsonArray();
        for (final UUID uuid : editorUUIDs) {
            final JsonObject editor = new JsonObject();
            editor.addProperty("uuid", uuid.toString());
            editors.add(editor);
        }
        root.add("editors", editors);

        try {
            Files.createDirectories(FILE_PATH.getParent());
            Files.write(FILE_PATH, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Main.LOGGER.error("[{}] Failed to save dashboard_auth.json", Jme.MOD_ID, e);
        }
    }
}
