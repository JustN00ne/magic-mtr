package org.justnoone.jme.client.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.justnoone.jme.client.screen.JmeSettingsScreen;

import java.lang.reflect.Method;

/**
 * Central config-screen entrypoint that works across all supported Minecraft versions.
 *
 * <p>Prefers YACL when an implementation is available, otherwise falls back to MAGIC's legacy UI.
 */
public final class JmeConfigScreens {

    private static final String CLOTH_SCREEN_CLASS = "org.justnoone.jme.client.cloth.JmeClothConfigScreen";
    private static final String YACL_SCREEN_CLASS = "org.justnoone.jme.client.yacl.JmeYaclConfigScreen";

    private JmeConfigScreens() {
    }

    public static Screen create(Screen parent) {
        final Screen safeParent = parent != null ? parent : MinecraftClient.getInstance().currentScreen;

        // Cloth Config is available for most Minecraft versions and provides a nice config UI,
        // but it is still an optional dependency.
        final Screen cloth = tryCreateClothScreen(safeParent);
        if (cloth != null) {
            return cloth;
        }

        // YACL is optional and version-dependent. We load it reflectively so older Minecraft versions
        // (and installs without YACL) can still compile and run.
        final Screen yacl = tryCreateYaclScreen(safeParent);
        if (yacl != null) {
            return yacl;
        }

        // Legacy UI fallback.
        return new JmeSettingsScreen(new org.mtr.mapping.holder.Screen(safeParent));
    }

    private static Screen tryCreateClothScreen(Screen parent) {
        try {
            final Class<?> clazz = Class.forName(CLOTH_SCREEN_CLASS);
            final Method method = clazz.getMethod("create", Screen.class);
            final Object out = method.invoke(null, parent);
            return out instanceof Screen ? (Screen) out : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Screen tryCreateYaclScreen(Screen parent) {
        try {
            final Class<?> clazz = Class.forName(YACL_SCREEN_CLASS);
            final Method method = clazz.getMethod("create", Screen.class);
            final Object out = method.invoke(null, parent);
            return out instanceof Screen ? (Screen) out : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
