package org.justnoone.jme.network;

import org.mtr.mapping.holder.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class MagicPacketIdHelper {

    private MagicPacketIdHelper() {
    }

    public static net.minecraft.util.Identifier toMinecraftId(Identifier mappingIdentifier) {
        return toMinecraftId(mappingIdentifier == null ? "" : mappingIdentifier.data.toString());
    }

    public static net.minecraft.util.Identifier toMinecraftId(String rawIdentifier) {
        final String normalized = rawIdentifier == null ? "" : rawIdentifier;
        final int separatorIndex = normalized.indexOf(':');
        final String namespace;
        final String path;
        if (separatorIndex >= 0) {
            namespace = normalized.substring(0, separatorIndex);
            path = normalized.substring(separatorIndex + 1);
        } else {
            namespace = "minecraft";
            path = normalized;
        }

        try {
            final Method method = net.minecraft.util.Identifier.class.getMethod("of", String.class, String.class);
            final Object value = method.invoke(null, namespace, path);
            if (value instanceof net.minecraft.util.Identifier) {
                return (net.minecraft.util.Identifier) value;
            }
        } catch (Exception ignored) {
        }

        final String combined = namespace + ":" + path;

        try {
            final Method method = net.minecraft.util.Identifier.class.getMethod("of", String.class);
            final Object value = method.invoke(null, combined);
            if (value instanceof net.minecraft.util.Identifier) {
                return (net.minecraft.util.Identifier) value;
            }
        } catch (Exception ignored) {
        }

        try {
            final Method method = net.minecraft.util.Identifier.class.getMethod("tryParse", String.class);
            final Object value = method.invoke(null, combined);
            if (value instanceof net.minecraft.util.Identifier) {
                return (net.minecraft.util.Identifier) value;
            }
        } catch (Exception ignored) {
        }

        try {
            final Constructor<net.minecraft.util.Identifier> constructor = net.minecraft.util.Identifier.class.getDeclaredConstructor(String.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(namespace, path);
        } catch (Exception ignored) {
        }

        try {
            final Constructor<net.minecraft.util.Identifier> constructor = net.minecraft.util.Identifier.class.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(combined);
        } catch (Exception ignored) {
        }

        throw new IllegalStateException("Unable to construct Minecraft Identifier for " + combined);
    }
}
