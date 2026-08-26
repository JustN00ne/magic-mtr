package org.justnoone.jme.mixin;

import org.mtr.mapping.holder.Item;
import org.mtr.mapping.mapper.BlockItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(value = org.mtr.mapping.registry.Registry.class, remap = false)
public class MtrRegistryHideRailsMixin {

    @Inject(
            method = "lambda$registerItem$7(Lorg/mtr/mapping/holder/Item;Lnet/fabricmc/fabric/api/itemgroup/v1/FabricItemGroupEntries;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void jme$hideMtrRailItems(Item item, @Coerce Object entries, CallbackInfo ci) {
        // Setting removed; keep MTR items visible.
    }

    @Inject(
            method = "lambda$registerBlockWithBlockItem$5(Lorg/mtr/mapping/mapper/BlockItemExtension;Lnet/fabricmc/fabric/api/itemgroup/v1/FabricItemGroupEntries;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void jme$hideMtrRailBlockItems(BlockItemExtension item, @Coerce Object entries, CallbackInfo ci) {
        // Setting removed; keep MTR items visible.
    }

    private static boolean jme$shouldHide(String id) {
        if (id == null) {
            return false;
        }

        final int colon = id.indexOf(':');
        final String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        if (!"mtr".equals(namespace)) {
            return false;
        }

        final String path = colon < 0 ? id : id.substring(colon + 1);
        if ("rail_connector_300".equals(path)) {
            return false;
        }

        // Hide all MTR rails (items + block items) while keeping the universal connector.
        return path.startsWith("rail") || path.contains("_rail") || path.contains("rail_");
    }

    private static String jme$getItemIdString(Object itemConvertible) {
        if (itemConvertible == null) {
            return null;
        }

        Object rawItem = itemConvertible;
        try {
            final Method asItem = itemConvertible.getClass().getMethod("asItem");
            rawItem = asItem.invoke(itemConvertible);
        } catch (Exception ignored) {
        }

        if (rawItem == null) {
            return null;
        }

        // 1.19+ path
        try {
            final Class<?> registriesClass = Class.forName("net.minecraft.registry.Registries");
            final Object itemRegistry = registriesClass.getField("ITEM").get(null);
            final Object id = jme$invokeGetId(itemRegistry, rawItem);
            return id == null ? null : id.toString();
        } catch (Exception ignored) {
        }

        // 1.16.5 path
        try {
            final Class<?> registryClass = Class.forName("net.minecraft.util.registry.Registry");
            final Object itemRegistry = registryClass.getField("ITEM").get(null);
            final Object id = jme$invokeGetId(itemRegistry, rawItem);
            return id == null ? null : id.toString();
        } catch (Exception ignored) {
        }

        return null;
    }

    private static Object jme$invokeGetId(Object registry, Object rawItem) throws Exception {
        if (registry == null || rawItem == null) {
            return null;
        }

        try {
            return registry.getClass().getMethod("getId", Object.class).invoke(registry, rawItem);
        } catch (NoSuchMethodException ignored) {
        }

        try {
            final Class<?> itemClass = Class.forName("net.minecraft.item.Item");
            return registry.getClass().getMethod("getId", itemClass).invoke(registry, rawItem);
        } catch (Exception ignored) {
        }

        for (final Method method : registry.getClass().getMethods()) {
            if (!"getId".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            try {
                return method.invoke(registry, rawItem);
            } catch (IllegalArgumentException ignored) {
            }
        }

        return null;
    }
}
