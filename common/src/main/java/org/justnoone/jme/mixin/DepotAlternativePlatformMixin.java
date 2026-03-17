package org.justnoone.jme.mixin;

import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = Depot.class, remap = false)
public abstract class DepotAlternativePlatformMixin {

    @Shadow
    @Final
    public ObjectArrayList<Route> routes;

    @Inject(method = "writeRouteCache", at = @At("TAIL"), remap = false)
    private void jme$applyAlternativePlatforms(Long2ObjectOpenHashMap<Route> routeIdMap, CallbackInfo ci) {
        final Map<Long, Platform> platformLookup = new HashMap<>();
        routes.forEach(route -> route.getRoutePlatforms().forEach(routePlatformData -> {
            final Platform platform = routePlatformData.platform;
            if (platform == null) {
                return;
            }
            platformLookup.put(platform.getId(), platform);
            if (platform.area != null) {
                platform.area.savedRails.forEach(savedRail -> {
                    if (savedRail instanceof Platform) {
                        final Platform candidate = (Platform) savedRail;
                        platformLookup.put(candidate.getId(), candidate);
                    }
                });
            }
        }));

        for (final Route route : routes) {
            for (int i = 0; i < route.getRoutePlatforms().size(); i++) {
                final Platform primaryPlatform = route.getRoutePlatforms().get(i).platform;
                if (primaryPlatform == null) {
                    continue;
                }

                AlternativePlatformRegistry.getAlternatives(route.getId(), primaryPlatform.getId()).forEach(alternativeId -> {
                    final Platform alternativePlatform = platformLookup.get(alternativeId);
                    jme$addRouteReference(alternativePlatform, route);
                });
            }
        }
    }

    @Unique
    private static void jme$addRouteReference(Platform platform, Route route) {
        if (platform == null || route == null) {
            return;
        }

        if (!platform.routes.contains(route)) {
            platform.routes.add(route);
            platform.routeColors.add(route.getColor());
        }
    }
}
