package org.justnoone.jme.mixin;

import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.Station;
import org.mtr.core.generated.operation.ArrivalResponseSchema;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.core.operation.ArrivalsRequest;
import org.mtr.core.operation.ArrivalsResponse;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Collections;

@Mixin(value = ArrivalsRequest.class, remap = false)
public abstract class ArrivalsRequestAlternativePlatformMixin {

    @Unique
    private static final ThreadLocal<LongAVLTreeSet> jme$initialRequestedPlatformIds = new ThreadLocal<>();

    @Unique
    private static volatile Field jme$carsField;

    @Unique
    private static volatile Field jme$platformNameField;

    @Inject(method = "getArrivals", at = @At("HEAD"), remap = false)
    private void jme$expandRequestedPlatforms(Simulator simulator, CallbackInfoReturnable<ArrivalsResponse> cir) {
        final LongAVLTreeSet requestedPlatformIds = jme$getRequestedPlatformIds(simulator);
        jme$initialRequestedPlatformIds.set(requestedPlatformIds);
        if (requestedPlatformIds.isEmpty()) {
            return;
        }
        jme$addAlternativePlatformIds(simulator, requestedPlatformIds);
    }

    @Inject(method = "getArrivals", at = @At("RETURN"), cancellable = true, remap = false)
    private void jme$mirrorDivertedPlatforms(Simulator simulator, CallbackInfoReturnable<ArrivalsResponse> cir) {
        try {
            final ArrivalsResponse originalResponse = cir.getReturnValue();
            if (originalResponse == null) {
                return;
            }

            LongAVLTreeSet requestedPlatformIds = jme$initialRequestedPlatformIds.get();
            if (requestedPlatformIds == null || requestedPlatformIds.isEmpty()) {
                requestedPlatformIds = jme$getRequestedPlatformIds(simulator);
            }
            if (requestedPlatformIds.isEmpty()) {
                return;
            }

            final ObjectImmutableList<ArrivalResponse> originalArrivals = originalResponse.getArrivals();
            if (originalArrivals.isEmpty()) {
                return;
            }

            final ObjectArrayList<ArrivalResponse> patchedArrivals = new ObjectArrayList<>();
            final ObjectArraySet<String> seenKeys = new ObjectArraySet<>();

            for (int i = 0; i < originalArrivals.size(); i++) {
                final ArrivalResponse arrivalResponse = originalArrivals.get(i);
                if (arrivalResponse == null) {
                    continue;
                }

                final long routeId = arrivalResponse.getRouteId();
                final long selectedPlatformId = arrivalResponse.getPlatformId();
                final Route route = simulator.routeIdMap.get(routeId);
                final Platform selectedPlatform = simulator.platformIdMap.get(selectedPlatformId);
                if (route == null || selectedPlatform == null) {
                    jme$addUnique(patchedArrivals, seenKeys, arrivalResponse);
                    continue;
                }

                long primaryPlatformId = AlternativePlatformRegistry.getPrimaryForAlternative(routeId, selectedPlatformId);
                if (primaryPlatformId == 0 && !AlternativePlatformRegistry.getAlternatives(routeId, selectedPlatformId).isEmpty()) {
                    primaryPlatformId = selectedPlatformId;
                }
                if (primaryPlatformId == 0) {
                    jme$addUnique(patchedArrivals, seenKeys, arrivalResponse);
                    continue;
                }

                final Platform primaryPlatform = simulator.platformIdMap.get(primaryPlatformId);
                if (primaryPlatform == null) {
                    jme$addUnique(patchedArrivals, seenKeys, arrivalResponse);
                    continue;
                }

                final java.util.List<Long> candidatePlatformIds = AlternativePlatformRegistry.getCandidatePlatformIds(route, primaryPlatform);
                if (candidatePlatformIds.size() <= 1) {
                    jme$addUnique(patchedArrivals, seenKeys, arrivalResponse);
                    continue;
                }

                // If this platform is one of the candidates, we only show arrivals for the platform it is currently at.
                // The Simulator naturally only has the arrival for the currently selected platform.
                // We just need to make sure we only show it for the platform requested if that platform is the selected one.
                if (!requestedPlatformIds.contains(selectedPlatformId)) {
                    continue;
                }

                final int stopIndex = jme$getStopIndex(route, primaryPlatformId, selectedPlatformId);
                final boolean selectedIsAlternative = selectedPlatformId != primaryPlatformId;
                final String divertedLabel = selectedIsAlternative ? jme$getDivertedPlatformLabel(primaryPlatform, selectedPlatform) : null;

                if (divertedLabel == null) {
                    jme$addUnique(patchedArrivals, seenKeys, arrivalResponse);
                } else {
                    final ArrivalResponse platformCopy = jme$copyArrival(arrivalResponse, route, selectedPlatform, stopIndex, divertedLabel);
                    if (platformCopy != null) {
                        jme$addUnique(patchedArrivals, seenKeys, platformCopy);
                    }
                }
            }

            Collections.sort(patchedArrivals);
            final ArrivalsResponse patchedResponse = new ArrivalsResponse(originalResponse.getCurrentTime());
            final long maxCountTotal = ((ArrivalsRequestSchemaAccessor) this).jme$getMaxCountTotal();
            final int limit = maxCountTotal <= 0 ? patchedArrivals.size() : (int) Math.min(maxCountTotal, patchedArrivals.size());
            for (int i = 0; i < limit; i++) {
                patchedResponse.add(patchedArrivals.get(i));
            }

            cir.setReturnValue(patchedResponse);
        } finally {
            jme$initialRequestedPlatformIds.remove();
        }
    }

    @Unique
    private LongAVLTreeSet jme$getRequestedPlatformIds(Simulator simulator) {
        final ArrivalsRequestSchemaAccessor accessor = (ArrivalsRequestSchemaAccessor) this;
        final LongArrayList platformIds = accessor.jme$getPlatformIds();
        final ObjectArrayList<String> platformIdsHex = accessor.jme$getPlatformIdsHex();
        final LongArrayList stationIds = accessor.jme$getStationIds();
        final ObjectArrayList<String> stationIdsHex = accessor.jme$getStationIdsHex();
        final LongAVLTreeSet requestedPlatformIds = new LongAVLTreeSet();

        platformIds.forEach((long platformId) -> {
            if (platformId != 0) {
                requestedPlatformIds.add(platformId);
            }
        });

        platformIdsHex.forEach(platformIdHex -> {
            final long parsed = jme$parseHexId(platformIdHex);
            if (parsed != 0) {
                requestedPlatformIds.add(parsed);
            }
        });

        stationIds.forEach((long stationId) -> jme$addStationPlatforms(simulator, stationId, requestedPlatformIds));
        stationIdsHex.forEach(stationIdHex -> jme$addStationPlatforms(simulator, jme$parseHexId(stationIdHex), requestedPlatformIds));
        return requestedPlatformIds;
    }

    @Unique
    private void jme$addAlternativePlatformIds(Simulator simulator, LongAVLTreeSet requestedPlatformIds) {
        final LongArrayList platformIds = ((ArrivalsRequestSchemaAccessor) this).jme$getPlatformIds();
        requestedPlatformIds.forEach((long primaryPlatformId) -> {
            final Platform platform = simulator.platformIdMap.get(primaryPlatformId);
            if (platform == null) {
                return;
            }

            platform.routes.forEach(route -> AlternativePlatformRegistry.getAlternatives(route.getId(), primaryPlatformId).forEach(alternativePlatformId -> {
                if (alternativePlatformId != 0 && !platformIds.contains(alternativePlatformId)) {
                    platformIds.add(alternativePlatformId);
                }
            }));
        });
    }

    @Unique
    private static void jme$addStationPlatforms(Simulator simulator, long stationId, LongAVLTreeSet requestedPlatformIds) {
        if (stationId == 0) {
            return;
        }

        final Station station = simulator.stationIdMap.get(stationId);
        if (station == null) {
            return;
        }

        station.savedRails.forEach(platform -> {
            if (platform != null && platform.getId() != 0) {
                requestedPlatformIds.add(platform.getId());
            }
        });
    }

    @Unique
    private static long jme$parseHexId(String id) {
        if (id == null || id.isEmpty()) {
            return 0;
        }

        try {
            return Long.parseUnsignedLong(id, 16);
        } catch (Exception ignored) {
            return 0;
        }
    }

    @Unique
    private static int jme$getStopIndex(Route route, long primaryPlatformId, long selectedPlatformId) {
        int fallback = 0;
        for (int i = 0; i < route.getRoutePlatforms().size(); i++) {
            final Platform platform = route.getRoutePlatforms().get(i).platform;
            if (platform == null) {
                continue;
            }
            if (platform.getId() == primaryPlatformId) {
                return i;
            }
            if (platform.getId() == selectedPlatformId) {
                fallback = i;
            }
        }
        return fallback;
    }

    @Unique
    private static String jme$getDivertedPlatformLabel(Platform primaryPlatform, Platform selectedPlatform) {
        final String selectedName = jme$getPlatformDisplayName(selectedPlatform);
        if (!"?".equals(selectedName)) {
            return selectedName;
        }
        return jme$getPlatformDisplayName(primaryPlatform);
    }

    @Unique
    private static String jme$getPlatformDisplayName(Platform platform) {
        final String name = platform == null ? "" : platform.getName();
        return name == null || name.isEmpty() ? "?" : name;
    }

    @Unique
    private static ArrivalResponse jme$copyArrival(ArrivalResponse source, Route route, Platform platform, int stopIndex, String platformNameOverride) {
        try {
            final ArrivalResponse copy = new ArrivalResponse(
                    source.getDestination(),
                    source.getArrival(),
                    source.getDeparture(),
                    source.getDeviation(),
                    source.getRealtime(),
                    source.getDepartureIndex(),
                    stopIndex,
                    route,
                    platform
            );

            jme$copyCars(source, copy);
            if (platformNameOverride != null && !platformNameOverride.isEmpty()) {
                jme$setPlatformName(copy, platformNameOverride);
            }
            return copy;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Unique
    private static void jme$copyCars(ArrivalResponse source, ArrivalResponse target) {
        try {
            Field carsField = jme$carsField;
            if (carsField == null) {
                carsField = ArrivalResponseSchema.class.getDeclaredField("cars");
                carsField.setAccessible(true);
                jme$carsField = carsField;
            }

            final Object sourceCarsObject = carsField.get(source);
            final Object targetCarsObject = carsField.get(target);
            if (!(sourceCarsObject instanceof ObjectArrayList) || !(targetCarsObject instanceof ObjectArrayList)) {
                return;
            }

            final ObjectArrayList<Object> sourceCars = (ObjectArrayList<Object>) sourceCarsObject;
            final ObjectArrayList<Object> targetCars = (ObjectArrayList<Object>) targetCarsObject;
            targetCars.addAll(sourceCars);
        } catch (Exception ignored) {
        }
    }

    @Unique
    private static void jme$setPlatformName(ArrivalResponse arrivalResponse, String platformName) {
        try {
            Field platformNameField = jme$platformNameField;
            if (platformNameField == null) {
                platformNameField = ArrivalResponseSchema.class.getDeclaredField("platformName");
                platformNameField.setAccessible(true);
                jme$platformNameField = platformNameField;
            }
            platformNameField.set(arrivalResponse, platformName);
        } catch (Exception ignored) {
        }
    }

    @Unique
    private static void jme$addUnique(ObjectArrayList<ArrivalResponse> arrivals, ObjectArraySet<String> seenKeys, ArrivalResponse arrivalResponse) {
        final String key = arrivalResponse.getRouteId() + ":" + arrivalResponse.getDepartureIndex() + ":" + arrivalResponse.getArrival() + ":" + arrivalResponse.getDeparture() + ":" + arrivalResponse.getPlatformId();
        if (seenKeys.add(key)) {
            arrivals.add(arrivalResponse);
        }
    }
}
