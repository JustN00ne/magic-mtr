package org.justnoone.jme.mixin;

import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.justnoone.jme.util.PathCache;
import org.justnoone.jme.util.SavedRailPathFinder;
import org.mtr.core.Main;
import org.mtr.core.data.Data;
import org.mtr.core.data.Depot;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Route;
import org.mtr.core.data.SavedRailBase;
import org.mtr.core.data.Siding;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.data.VehiclePosition;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(value = Siding.class, remap = false)
public abstract class SidingDynamicPlatformRerouteMixin {

    @Shadow
    @Final
    private ObjectArrayList<PathData> pathMainRoute;

    @Shadow
    @Final
    private ObjectArrayList<PathData> pathSidingToMainRoute;

    @Shadow
    @Final
    private ObjectArrayList<PathData> pathMainRouteToSiding;

    @Shadow
    private PathData defaultPathData;

    @Unique
    private static final Object CONCURRENCY_LOCK = new Object();

    @Unique
    private static final Map<Long, Long> DEPLOYING_RESERVATIONS = new HashMap<>();

    @Unique
    private static final long PATHFIND_TIME_BUDGET_MILLIS = 120;

    @Unique
    private static final int MAX_PATHFIND_TICK_CALLS = 48;

    @Unique
    private static volatile Field jme$dataField;

    @Redirect(
            method = "simulateTrain",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/core/data/VehicleExtraData;create(JJDLorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;Lorg/mtr/core/data/PathData;ZDDZDJ)Lorg/mtr/core/data/VehicleExtraData;"
            ),
            remap = false,
            require = 0
    )
    private VehicleExtraData jme$createVehicleExtraDataWithDynamicPlatform(
            long depotId,
            long sidingId,
            double railLength,
            ObjectArrayList<VehicleCar> vehicleCars,
            ObjectArrayList<PathData> pathSidingToMainRoute,
            ObjectArrayList<PathData> pathMainRoute,
            ObjectArrayList<PathData> pathMainRouteToSiding,
            PathData defaultPathData,
            boolean repeatInfinitely,
            double acceleration,
            double deceleration,
            boolean isManualAllowed,
            double maxManualSpeed,
            long manualToAutomaticTime
    ) {
        // Keep spawn logic vanilla. Dynamic platform choice is recalculated right before
        // departure (see the Vehicle#startUp redirect) to reduce tick-time pathfinding and
        // avoid odd "instant deploy" placements during initial spawn.
        return VehicleExtraData.create(
                depotId,
                sidingId,
                railLength,
                vehicleCars,
                pathSidingToMainRoute,
                pathMainRoute,
                pathMainRouteToSiding,
                defaultPathData,
                repeatInfinitely,
                acceleration,
                deceleration,
                isManualAllowed,
                maxManualSpeed,
                manualToAutomaticTime
        );
    }

    @Redirect(
            method = "simulateTrain",
            at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/Vehicle;startUp(JJ)V"),
            remap = false,
            require = 0
    )
    private void jme$refreshPlatformChoiceBeforeStartUp(
            Vehicle vehicle,
            long departureIndex,
            long sidingDepartureTime
    ) {
        if (vehicle == null || vehicle.getIsOnRoute()) {
            if (vehicle != null) {
                vehicle.startUp(departureIndex, sidingDepartureTime);
            }
            return;
        }

        final Siding siding = (Siding) (Object) this;
        final Depot depot = siding.area;
        if (depot == null || defaultPathData == null || pathMainRoute == null || pathMainRoute.isEmpty()) {
            vehicle.startUp(departureIndex, sidingDepartureTime);
            return;
        }

        try {
            final long selectionSeed = jme$mix64(siding.getId() ^ vehicle.getId() ^ departureIndex ^ (sidingDepartureTime << 1) ^ System.currentTimeMillis());
            final VehicleExtraData dynamicVehicleExtraData = jme$buildDynamicVehicleExtraData(
                    siding,
                    depot.getId(),
                    siding.getId(),
                    siding.getRailLength(),
                    siding.getVehicleCars(),
                    pathSidingToMainRoute,
                    pathMainRoute,
                    pathMainRouteToSiding,
                    defaultPathData,
                    depot.getRepeatInfinitely(),
                    siding.getAcceleration(),
                    siding.getDeceleration(),
                    siding.getIsManual(),
                    siding.getMaxManualSpeed(),
                    siding.getManualToAutomaticTime(),
                    vehicle.getId(),
                    selectionSeed
            );

            if (dynamicVehicleExtraData != null) {
                ((VehicleMutableExtraDataAccessor) (Object) vehicle).jme$setVehicleExtraData(dynamicVehicleExtraData);
            }
        } catch (Throwable ignored) {
            // Never break train deployment due to reroute logic.
        }

        vehicle.startUp(departureIndex, sidingDepartureTime);
    }

    @Unique
    private VehicleExtraData jme$buildDynamicVehicleExtraData(
            Siding siding,
            long depotId,
            long sidingId,
            double railLength,
            ObjectArrayList<VehicleCar> vehicleCars,
            ObjectArrayList<PathData> pathSidingToMainRoute,
            ObjectArrayList<PathData> pathMainRoute,
            ObjectArrayList<PathData> pathMainRouteToSiding,
            PathData defaultPathData,
            boolean repeatInfinitely,
            double acceleration,
            double deceleration,
            boolean isManualAllowed,
            double maxManualSpeed,
            long manualToAutomaticTime,
            long vehicleId,
            long selectionSeed
    ) {
        final Depot depot = siding == null ? null : siding.area;
        final Data data = jme$getDataFromSiding(siding);
        if (depot == null || data == null || pathMainRoute == null || pathMainRoute.isEmpty()) {
            return null;
        }

        final long cruisingAltitude = depot.getCruisingAltitude();
        final long[] firstStopSelection = new long[3];
        final int[] stopIndices = new int[2];
        final List<Long> candidatePlatformIds = new ArrayList<>();
        if (!jme$fillFirstStopSelection(data, siding, vehicleCars, depot, cruisingAltitude, pathSidingToMainRoute, pathMainRoute, firstStopSelection, stopIndices, candidatePlatformIds)) {
            return null;
        }

        final Platform secondStopPlatform = data.platformIdMap.get(firstStopSelection[2]);
        if (secondStopPlatform == null) {
            return null;
        }

        final ObjectArrayList<PathData>[] selectedFirstStopPaths = jme$newPathSelectionArray();
        final Platform selectedFirstStop = jme$chooseFirstStopPlatform(
                siding,
                data,
                firstStopSelection[0],
                secondStopPlatform,
                candidatePlatformIds,
                stopIndices[0],
                stopIndices[1],
                cruisingAltitude,
                vehicleId,
                selectionSeed,
                selectedFirstStopPaths
        );

        if (selectedFirstStop == null || selectedFirstStop.getId() == firstStopSelection[1]) {
            return null;
        }

        Main.LOGGER.info("[MAGIC] Rerouting siding {} from platform {} to {}", siding.getId(), firstStopSelection[0], selectedFirstStop.getId());

        final ObjectArrayList<PathData> dynamicSidingToMainRoute = selectedFirstStopPaths[0];
        final ObjectArrayList<PathData> dynamicFirstLeg = selectedFirstStopPaths[1];
        if (dynamicSidingToMainRoute == null || dynamicFirstLeg == null) {
            return null;
        }

        final ObjectArrayList<PathData> dynamicMainRoute = new ObjectArrayList<>(dynamicFirstLeg);
        // The first segment of the main route leg often duplicates the last segment of the
        // siding->platform leg (the platform rail itself). Remove the overlap to keep transitions
        // sane and distances consistent.
        jme$removeOverlappingFirstSegment(dynamicSidingToMainRoute, dynamicMainRoute);
        jme$appendMainRouteTail(pathMainRoute, dynamicMainRoute, firstStopSelection[2]);
        if (dynamicMainRoute.isEmpty() || jme$hasInvalidImmediateOppositeRailTransition(dynamicMainRoute)) {
            return null;
        }

        final PathData lastPathToMainRoute = dynamicSidingToMainRoute.get(dynamicSidingToMainRoute.size() - 1);
        final PathData firstMainRoutePath = dynamicMainRoute.get(0);
        if (jme$isInvalidOppositeTransition(lastPathToMainRoute, firstMainRoutePath)) {
            return null;
        }

        final ObjectArrayList<PathData> dynamicMainRouteToSiding = new ObjectArrayList<>(pathMainRouteToSiding);
        jme$removeOverlappingFirstSegment(dynamicMainRoute, dynamicMainRouteToSiding);
        jme$regeneratePathDistances(dynamicSidingToMainRoute, dynamicMainRoute, dynamicMainRouteToSiding);

        // Important: our dynamic path segments come from runtime pathfinding and can have speedLimit=0.
        // If so, MTR clamps to 1 km/h and the train crawls out of the station. Cache rails + speeds now.
        TransportMode transportMode = null;
        try {
            // Siding doesn't always expose a transport mode. Platforms do, and it's the same mode for this route.
            transportMode = secondStopPlatform.getTransportMode();
        } catch (Throwable ignored) {
        }
        if (transportMode != null) {
            try {
                PathData.writePathCache(dynamicSidingToMainRoute, data, transportMode);
            } catch (Throwable ignored) {
            }
            try {
                PathData.writePathCache(dynamicMainRoute, data, transportMode);
            } catch (Throwable ignored) {
            }
            try {
                if (!dynamicMainRouteToSiding.isEmpty()) {
                    PathData.writePathCache(dynamicMainRouteToSiding, data, transportMode);
                }
            } catch (Throwable ignored) {
            }
        }

        return VehicleExtraData.create(
                depotId,
                sidingId,
                railLength,
                vehicleCars,
                dynamicSidingToMainRoute,
                dynamicMainRoute,
                dynamicMainRouteToSiding,
                defaultPathData,
                repeatInfinitely,
                acceleration,
                deceleration,
                isManualAllowed,
                maxManualSpeed,
                manualToAutomaticTime
        );
    }

    @Unique
    private static boolean jme$fillFirstStopSelection(
            Data data,
            Siding siding,
            ObjectArrayList<VehicleCar> vehicleCars,
            Depot depot,
            long cruisingAltitude,
            ObjectArrayList<PathData> pathSidingToMainRoute,
            ObjectArrayList<PathData> pathMainRoute,
            long[] firstStopSelection,
            int[] stopIndicesOut,
            List<Long> candidatePlatformIds
    ) {
        if (data == null || siding == null || depot == null || pathSidingToMainRoute == null || pathSidingToMainRoute.isEmpty() || pathMainRoute == null || pathMainRoute.isEmpty() || firstStopSelection == null || firstStopSelection.length < 3 || stopIndicesOut == null || stopIndicesOut.length < 2 || candidatePlatformIds == null) {
            return false;
        }

        firstStopSelection[0] = 0;
        firstStopSelection[1] = 0;
        firstStopSelection[2] = 0;
        stopIndicesOut[0] = -1;
        stopIndicesOut[1] = -1;
        candidatePlatformIds.clear();

        final long[] extractedStops = jme$extractFirstTwoStops(pathSidingToMainRoute, pathMainRoute);
        final long firstStopId = extractedStops[0];
        final long secondStopId = extractedStops[1];
        final int firstStopIndex = (int) extractedStops[2];
        final int secondStopIndex = (int) extractedStops[3];
        final int resolvedSecondStopIndex = secondStopIndex >= 0 ? secondStopIndex : firstStopIndex + 1;

        if (firstStopId == 0 || secondStopId == 0 || firstStopIndex < 0 || resolvedSecondStopIndex < 0) {
            return false;
        }

        stopIndicesOut[0] = firstStopIndex;
        stopIndicesOut[1] = resolvedSecondStopIndex;

        final Platform firstStopPlatform = data.platformIdMap.get(firstStopId);
        if (firstStopPlatform == null) {
            return false;
        }

        final VehicleExtraData.VehiclePlatformRouteInfo info = depot.getVehiclePlatformRouteInfo(firstStopIndex);
        final VehiclePlatformRouteInfoAccessor infoAccessor = (VehiclePlatformRouteInfoAccessor) (Object) info;
        Route route = infoAccessor.jme$getThisRoute();
        if (route == null) {
            route = infoAccessor.jme$getNextRoute();
        }
        if (route == null) {
            route = infoAccessor.jme$getPreviousRoute();
        }

        if (route == null) {
            return false;
        }

        final List<Long> configuredCandidates = AlternativePlatformRegistry.getCandidatePlatformIds(route, firstStopPlatform);
        if (configuredCandidates.size() <= 1) {
            return false;
        }

        // Only keep candidates that exist in the current data.
        final LinkedHashSet<Long> candidates = new LinkedHashSet<>();
        configuredCandidates.forEach(candidateId -> {
            if (candidateId != 0 && data.platformIdMap.containsKey(candidateId)) {
                candidates.add(candidateId);
            }
        });

        if (candidates.size() <= 1) {
            return false;
        }

        firstStopSelection[0] = firstStopId;
        firstStopSelection[1] = firstStopId;
        firstStopSelection[2] = secondStopId;
        candidatePlatformIds.addAll(candidates);
        return true;
    }

    @Unique
    private Platform jme$chooseFirstStopPlatform(
            Siding siding,
            Data data,
            long primaryPlatformId,
            Platform secondStopPlatform,
            List<Long> candidatePlatformIds,
            int firstStopIndex,
            int secondStopIndex,
            long cruisingAltitude,
            long vehicleId,
            long selectionSeed,
            ObjectArrayList<PathData>[] selectedPaths
    ) {
        if (siding == null || data == null || candidatePlatformIds == null || candidatePlatformIds.isEmpty() || selectedPaths == null || selectedPaths.length < 2) {
            return null;
        }

        selectedPaths[0] = null;
        selectedPaths[1] = null;

        final long secondStopId = secondStopPlatform == null ? 0 : secondStopPlatform.getId();
        if (primaryPlatformId == 0 || secondStopId == 0) {
            return null;
        }

        // Candidate set: use the provided list, but keep it unique and sane.
        final LinkedHashSet<Long> uniqueCandidateIds = new LinkedHashSet<>();
        candidatePlatformIds.forEach(candidateId -> {
            if (candidateId != 0 && candidateId != secondStopId) {
                uniqueCandidateIds.add(candidateId);
            }
        });
        if (uniqueCandidateIds.size() <= 1 || !uniqueCandidateIds.contains(primaryPlatformId)) {
            return null;
        }

        final long now = System.currentTimeMillis();
        final Set<Long> deployingReserved;
        synchronized (CONCURRENCY_LOCK) {
            // Expire old deploying reservations (more than 5 seconds old)
            DEPLOYING_RESERVATIONS.entrySet().removeIf(entry -> now - entry.getValue() > 5000);
            deployingReserved = new LinkedHashSet<>(DEPLOYING_RESERVATIONS.keySet());
        }

        final Iterable<Siding> sidingsToCheck = jme$getSidingsToCheck(siding, data);
        final Map<Long, Integer> reservationCounts = jme$getReservationCounts(sidingsToCheck, uniqueCandidateIds);
        final Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> vehiclePositions = jme$getVehiclePositions(sidingsToCheck);

        final double totalVehicleLength = jme$getTotalVehicleLength(siding.getVehicleCars());
        final TransportMode transportMode = secondStopPlatform.getTransportMode();
        final double requiredClearLength = totalVehicleLength + (transportMode == null ? 0 : transportMode.stoppingSpace) + 0.5;

        final Map<Long, Integer> candidateScores = new HashMap<>();
        for (final long candidateId : uniqueCandidateIds) {
            int score = reservationCounts.getOrDefault(candidateId, 0);
            if (deployingReserved.contains(candidateId)) {
                score += 1_000_000;
            }
            if (jme$isPlatformTaken(sidingsToCheck, null, candidateId)) {
                score += 10_000;
            }
            candidateScores.put(candidateId, score);
        }

        final int primaryScore = candidateScores.getOrDefault(primaryPlatformId, 0);
        final long probeVehicleId = vehicleId;
        // Check signal blocks on the *actual* primary approach path. Using only the cached
        // siding->main-route leg misses blocks on the station throat / platform entry.
        boolean primarySignalBlocked = false;
        boolean primaryApproachOccupied = false;
        boolean primarySignalClearanceBlocked = false;
        try {
            final Platform primaryPlatform = data.platformIdMap.get(primaryPlatformId);
            final ObjectArrayList<PathData>[] primaryPaths = jme$newPathSelectionArray();
            if (primaryPlatform != null && jme$populateCandidatePaths(data, siding, primaryPlatform, secondStopPlatform, firstStopIndex, secondStopIndex, cruisingAltitude, primaryPaths)) {
                primarySignalBlocked = jme$isPathSignalBlocked(primaryPaths[0], data, transportMode, probeVehicleId);
                primaryApproachOccupied = jme$isApproachOccupied(primaryPaths[0], vehiclePositions, requiredClearLength);
                primarySignalClearanceBlocked = jme$isSignalClearanceBlocked(primaryPaths[0], vehiclePositions, requiredClearLength);
            } else {
                primarySignalBlocked = jme$isPathSignalBlocked(pathSidingToMainRoute, data, transportMode, probeVehicleId);
            }
        } catch (Throwable ignored) {
            primarySignalBlocked = jme$isPathSignalBlocked(pathSidingToMainRoute, data, transportMode, probeVehicleId);
        }
        final boolean primaryBlocked = primarySignalBlocked || primaryApproachOccupied || primarySignalClearanceBlocked;

        final List<Long> orderedByPreference = new ArrayList<>(uniqueCandidateIds);
        orderedByPreference.sort(Comparator
                .comparingInt((Long candidateId) -> candidateScores.getOrDefault(candidateId, 0))
                .thenComparingLong(candidateId -> jme$mix64(selectionSeed ^ candidateId))
        );

        // Keep the primary platform when it is the top choice. This also enables load-balancing:
        // if multiple candidates tie (score == primaryScore), the tie-breaker can make an alternative
        // the top choice for this departure.
        if (!primaryBlocked && !orderedByPreference.isEmpty() && orderedByPreference.get(0) == primaryPlatformId) {
            return null;
        }

        int skippedDeployingReserved = 0;
        int skippedWorseThanPrimary = 0;
        int missingCandidatePlatform = 0;
        int pathfindingFailed = 0;
        int candidateSignalBlocked = 0;
        int candidateClearanceBlocked = 0;
        int candidateApproachOccupied = 0;

        int orderedIndex = -1;
        for (final long candidateId : orderedByPreference) {
            orderedIndex++;
            if (candidateId == primaryPlatformId) {
                continue;
            }
            if (deployingReserved.contains(candidateId)) {
                skippedDeployingReserved++;
                continue;
            }
            if (!primaryBlocked && candidateScores.getOrDefault(candidateId, 0) > primaryScore) {
                // Remaining candidates are worse than the primary platform.
                skippedWorseThanPrimary = Math.max(0, orderedByPreference.size() - orderedIndex);
                break;
            }

            final Platform candidatePlatform = data.platformIdMap.get(candidateId);
            if (candidatePlatform == null) {
                missingCandidatePlatform++;
                continue;
            }

            if (!jme$populateCandidatePaths(data, siding, candidatePlatform, secondStopPlatform, firstStopIndex, secondStopIndex, cruisingAltitude, selectedPaths)) {
                pathfindingFailed++;
                continue;
            }

            // If the candidate approach is blocked, keep searching.
            if (jme$isPathSignalBlocked(selectedPaths[0], data, transportMode, probeVehicleId)) {
                candidateSignalBlocked++;
                selectedPaths[0] = null;
                selectedPaths[1] = null;
                continue;
            }

            // Mirror MTR's "green signal but can't clear the block" behavior.
            if (jme$isSignalClearanceBlocked(selectedPaths[0], vehiclePositions, requiredClearLength)) {
                candidateClearanceBlocked++;
                selectedPaths[0] = null;
                selectedPaths[1] = null;
                continue;
            }

            // If another train is already occupying the platform approach (even if the signal is "green"),
            // we won't be able to enter. Treat this as blocked and try other platforms.
            if (jme$isApproachOccupied(selectedPaths[0], vehiclePositions, requiredClearLength)) {
                candidateApproachOccupied++;
                selectedPaths[0] = null;
                selectedPaths[1] = null;
                continue;
            }

            synchronized (CONCURRENCY_LOCK) {
                // Might have been reserved by another deployment while we were pathfinding.
                if (DEPLOYING_RESERVATIONS.containsKey(candidateId)) {
                    continue;
                }
                DEPLOYING_RESERVATIONS.put(candidateId, now);
            }

            return candidatePlatform;
        }

        // If we reached this point we wanted to divert, but couldn't find a usable alternative.
        if (primaryBlocked || primaryScore > 0) {
            Main.LOGGER.info(
                    "[MAGIC] No alternative platform found for siding {} (primary {}, primaryScore {}, primarySignalBlocked {}, primaryClearanceBlocked {}, candidates {}, stats {deployingReserved {}, worseThanPrimary {}, missingPlatform {}, pathFail {}, signalBlocked {}, clearanceBlocked {}, approachOccupied {}})",
                    siding.getId(),
                    primaryPlatformId,
                    primaryScore,
                    primarySignalBlocked,
                    primarySignalClearanceBlocked,
                    orderedByPreference,
                    skippedDeployingReserved,
                    skippedWorseThanPrimary,
                    missingCandidatePlatform,
                    pathfindingFailed,
                    candidateSignalBlocked,
                    candidateClearanceBlocked,
                    candidateApproachOccupied
            );
        }

        return null;
    }

    @Unique
    private static boolean jme$isPathSignalBlocked(ObjectArrayList<PathData> path, Data data, TransportMode transportMode, long vehicleId) {
        if (path == null || path.isEmpty() || data == null || transportMode == null) {
            return false;
        }

        try {
            PathData.writePathCache(path, data, transportMode);
        } catch (Throwable ignored) {
        }

        for (int i = 0; i < path.size(); i++) {
            final PathData pathData = path.get(i);
            if (pathData == null) {
                continue;
            }
            try {
                if (pathData.isSignalBlocked(vehicleId, Rail.BlockReservation.DO_NOT_RESERVE)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    @Unique
    private static boolean jme$isPlatformTaken(Iterable<Siding> sidingsToCheck, Vehicle currentVehicle, long platformId) {
        if (platformId == 0) {
            return false;
        }

        for (final Siding depotSiding : sidingsToCheck) {
            if (depotSiding == null) {
                continue;
            }

            final ObjectArraySet<Vehicle> sidingVehicles = ((SidingVehiclesAccessor) (Object) depotSiding).jme$getVehicles();
            if (sidingVehicles == null || sidingVehicles.isEmpty()) {
                continue;
            }

            for (final Vehicle otherVehicle : sidingVehicles) {
                if (otherVehicle == null || otherVehicle == currentVehicle || otherVehicle.vehicleExtraData == null) {
                    continue;
                }

                // Reserve platforms for active trains and for trains laying over at a terminal,
                // but ignore trains that are still parked in their depot siding.
                if (!otherVehicle.getIsOnRoute() && otherVehicle.closeToDepot()) {
                    continue;
                }

                if (otherVehicle.vehicleExtraData.getThisPlatformId() == platformId) {
                    return true;
                }
                if (otherVehicle.vehicleExtraData.getNextPlatformId() == platformId) {
                    return true;
                }

                // Check upcoming stops in the vehicle's future path (up to 5 stops)
                int stopsFound = 0;
                for (final PathData pathData : otherVehicle.vehicleExtraData.immutablePath) {
                    if (pathData != null && pathData.getDwellTime() > 0) {
                        final long pathPlatformId = pathData.getSavedRailBaseId();
                        if (pathPlatformId != 0) {
                            if (pathPlatformId == platformId) {
                                return true;
                            }
                            stopsFound++;
                        }
                    }
                    if (stopsFound >= 5) {
                        break;
                    }
                }
            }
        }

        return false;
    }

    @Unique
    private static Platform jme$getNextRoutePlatform(Route route, int stopIndex) {
        if (route == null) {
            return null;
        }

        final int nextIndex = stopIndex + 1;
        if (nextIndex < 0 || nextIndex >= route.getRoutePlatforms().size()) {
            return null;
        }

        final org.mtr.core.data.RoutePlatformData routePlatformData = route.getRoutePlatforms().get(nextIndex);
        return routePlatformData == null ? null : routePlatformData.platform;
    }

    @Unique
    private static long[] jme$extractFirstTwoStops(ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute) {
        long firstStopId = 0;
        long secondStopId = 0;
        int firstStopIndex = -1;
        int secondStopIndex = -1;

        for (int pass = 0; pass < 2; pass++) {
            final ObjectArrayList<PathData> path = pass == 0 ? pathSidingToMainRoute : pathMainRoute;
            if (path == null || path.isEmpty()) {
                continue;
            }

            for (int i = 0; i < path.size(); i++) {
                final PathData pathData = path.get(i);
                if (pathData == null || pathData.getDwellTime() <= 0) {
                    continue;
                }

                final long platformId = pathData.getSavedRailBaseId();
                if (platformId == 0) {
                    continue;
                }

                if (firstStopId == 0) {
                    firstStopId = platformId;
                    firstStopIndex = pathData.getStopIndex();
                } else if (platformId != firstStopId) {
                    secondStopId = platformId;
                    secondStopIndex = pathData.getStopIndex();
                    return new long[]{firstStopId, secondStopId, firstStopIndex, secondStopIndex};
                }
            }
        }

        return new long[]{firstStopId, secondStopId, firstStopIndex, secondStopIndex};
    }

    @Unique
    private static double jme$getPathLength(ObjectArrayList<PathData> path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }

        double length = 0;
        for (int i = 0; i < path.size(); i++) {
            final PathData pathData = path.get(i);
            if (pathData != null) {
                length += pathData.getRailLength();
            }
        }
        return length;
    }

    @Unique
    private static double jme$getTotalVehicleLength(ObjectArrayList<VehicleCar> vehicleCars) {
        return vehicleCars == null ? 0 : Siding.getTotalVehicleLength(vehicleCars);
    }

    @Unique
    private static Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> jme$getVehiclePositions(Iterable<Siding> sidingsToCheck) {
        final Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> vehiclePositions = new Object2ObjectAVLTreeMap<>();
        if (sidingsToCheck == null) {
            return vehiclePositions;
        }

        for (final Siding depotSiding : sidingsToCheck) {
            if (depotSiding == null) {
                continue;
            }
            try {
                depotSiding.initVehiclePositions(vehiclePositions);
            } catch (Throwable ignored) {
            }
        }

        return vehiclePositions;
    }

    @Unique
    private static boolean jme$isSignalClearanceBlocked(
            ObjectArrayList<PathData> pathToPlatform,
            Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> vehiclePositions,
            double requiredClearLength
    ) {
        if (pathToPlatform == null || pathToPlatform.isEmpty() || vehiclePositions == null || vehiclePositions.isEmpty() || requiredClearLength <= 0) {
            return false;
        }

        // Mirror Vehicle#checkAndBlockSignal:
        // If a signal block is encountered, ensure the path *after* the entire block is clear for at least
        // (totalVehicleLength + stoppingSpace). Otherwise the vehicle will stop even if the signal appears green.
        //
        // For platform selection we care most about the *last* signal block boundary on the approach
        // (the one right before the station throat / platform entry). Earlier blocks tend to be shared
        // by all candidate platforms and rejecting on them prevents rerouting even when a different platform
        // would work.
        int lastExitIndex = -1;
        for (int i = 0; i < pathToPlatform.size(); i++) {
            final PathData firstPathData = pathToPlatform.get(i);
            if (firstPathData == null) {
                continue;
            }

            final IntAVLTreeSet signalColors;
            try {
                signalColors = firstPathData.getSignalColors();
            } catch (Throwable ignored) {
                continue;
            }

            if (signalColors == null || signalColors.isEmpty()) {
                continue;
            }

            int index = i + 1;
            while (!signalColors.isEmpty() && index < pathToPlatform.size()) {
                final PathData pathData = pathToPlatform.get(index);
                if (pathData == null) {
                    index++;
                    continue;
                }

                final boolean blockEnded;
                try {
                    blockEnded = pathData.getSignalColors().intStream().noneMatch(signalColors::contains);
                } catch (Throwable ignored) {
                    break;
                }

                if (blockEnded) {
                    lastExitIndex = index;
                    // Skip to the first segment after this block; it might start another block.
                    i = index - 1;
                    break;
                }

                index++;
            }
        }

        return lastExitIndex >= 0 && jme$isPrefixOccupied(pathToPlatform, lastExitIndex, vehiclePositions, requiredClearLength);
    }

    @Unique
    private static boolean jme$isPrefixOccupied(
            ObjectArrayList<PathData> path,
            int startIndex,
            Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> vehiclePositions,
            double requiredClearLength
    ) {
        if (path == null || startIndex < 0 || startIndex >= path.size() || vehiclePositions == null || vehiclePositions.isEmpty() || requiredClearLength <= 0) {
            return false;
        }

        double remaining = requiredClearLength;
        for (int i = startIndex; i < path.size() && remaining > 0; i++) {
            final PathData pathData = path.get(i);
            if (pathData == null) {
                continue;
            }

            final double segmentLength = Math.max(0.1, pathData.getRailLength());
            final double segmentWindow = Math.min(segmentLength, remaining);

            // VehiclePosition offsets are stored in the ordered-position coordinate system.
            // We need the *prefix window* (closest to the segment start in travel direction).
            final double windowStart;
            final double windowEnd;
            if (pathData.reversePositions) {
                // Travel direction is opposite of ordered direction, so the prefix is near ordered offset segmentLength.
                windowStart = segmentLength - segmentWindow;
                windowEnd = segmentLength;
            } else {
                windowStart = 0;
                windowEnd = segmentWindow;
            }

            final VehiclePosition vehiclePosition = Data.tryGet(vehiclePositions, pathData.getOrderedPosition1(), pathData.getOrderedPosition2());
            if (vehiclePosition != null) {
                try {
                    if (vehiclePosition.getClosestOverlap(windowStart, windowEnd, pathData.reversePositions, 0) >= 0) {
                        return true;
                    }
                } catch (Throwable ignored) {
                    return true;
                }
            }

            remaining -= segmentLength;
        }

        return false;
    }

    @Unique
    private static boolean jme$isApproachOccupied(
            ObjectArrayList<PathData> pathToPlatform,
            Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> vehiclePositions,
            double requiredClearLength
    ) {
        if (pathToPlatform == null || pathToPlatform.isEmpty() || vehiclePositions == null || vehiclePositions.isEmpty() || requiredClearLength <= 0) {
            return false;
        }

        double remaining = requiredClearLength;
        for (int i = pathToPlatform.size() - 1; i >= 0 && remaining > 0; i--) {
            final PathData pathData = pathToPlatform.get(i);
            if (pathData == null) {
                continue;
            }

            final double segmentLength = Math.max(0.1, pathData.getRailLength());
            final double segmentWindow = Math.min(segmentLength, remaining);

            // VehiclePosition offsets are stored in the ordered-position coordinate system.
            // We only care if the *tail window* (closest to the platform end) is occupied.
            final double windowStart;
            final double windowEnd;
            if (pathData.reversePositions) {
                // Travel direction is opposite of ordered direction, so the tail is near ordered offset 0.
                windowStart = 0;
                windowEnd = segmentWindow;
            } else {
                windowStart = segmentLength - segmentWindow;
                windowEnd = segmentLength;
            }

            final VehiclePosition vehiclePosition = Data.tryGet(vehiclePositions, pathData.getOrderedPosition1(), pathData.getOrderedPosition2());
            if (vehiclePosition != null) {
                try {
                    // Any overlap in the window means we can't fit the full consist into the approach.
                    if (vehiclePosition.getClosestOverlap(windowStart, windowEnd, pathData.reversePositions, 0) >= 0) {
                        return true;
                    }
                } catch (Throwable ignored) {
                    return true;
                }
            }

            remaining -= segmentLength;
        }

        return false;
    }

    @Unique
    private static Map<Long, Integer> jme$getReservationCounts(Iterable<Siding> sidingsToCheck, Set<Long> candidatePlatformIds) {
        if (sidingsToCheck == null || candidatePlatformIds == null || candidatePlatformIds.isEmpty()) {
            return Collections.emptyMap();
        }

        final Map<Long, Integer> reservationCounts = new HashMap<>();

        for (final Siding depotSiding : sidingsToCheck) {
            if (depotSiding == null) {
                continue;
            }

            final ObjectArraySet<Vehicle> sidingVehicles = ((SidingVehiclesAccessor) (Object) depotSiding).jme$getVehicles();
            if (sidingVehicles == null || sidingVehicles.isEmpty()) {
                continue;
            }

            for (final Vehicle otherVehicle : sidingVehicles) {
                if (otherVehicle == null || otherVehicle.vehicleExtraData == null) {
                    continue;
                }

                // Ignore trains that are still parked in their depot siding.
                if (!otherVehicle.getIsOnRoute() && otherVehicle.closeToDepot()) {
                    continue;
                }

                final long thisPlatformId = otherVehicle.vehicleExtraData.getThisPlatformId();
                if (thisPlatformId != 0 && candidatePlatformIds.contains(thisPlatformId)) {
                    reservationCounts.put(thisPlatformId, reservationCounts.getOrDefault(thisPlatformId, 0) + 1);
                }

                final long nextPlatformId = otherVehicle.vehicleExtraData.getNextPlatformId();
                if (nextPlatformId != 0 && nextPlatformId != thisPlatformId && candidatePlatformIds.contains(nextPlatformId)) {
                    reservationCounts.put(nextPlatformId, reservationCounts.getOrDefault(nextPlatformId, 0) + 1);
                }
            }
        }

        return reservationCounts;
    }

    @Unique
    private static long jme$mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    @Unique
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ObjectArrayList<PathData> jme$findPath(Data data, SavedRailBase<?, ?> startSavedRail, SavedRailBase<?, ?> endSavedRail, int stopIndex, long cruisingAltitude) {
        if (data == null || startSavedRail == null || endSavedRail == null) {
            return new ObjectArrayList<>();
        }

        final long startId = startSavedRail.getId();
        final long endId = endSavedRail.getId();
        if (startId == 0 || endId == 0 || startId == endId) {
            return new ObjectArrayList<>();
        }

        final long startMillis = System.currentTimeMillis();
        final ObjectArrayList<PathData> cached = PathCache.getCopy(startId, endId, stopIndex, cruisingAltitude, startMillis);
        if (cached != null) {
            return cached;
        }

        final TransportMode transportMode = startSavedRail.getTransportMode();
        if (transportMode != TransportMode.AIRPLANE) {
            final ObjectArrayList<PathData> path = SavedRailPathFinder.findPath(
                    data,
                    startSavedRail,
                    endSavedRail,
                    stopIndex,
                    cruisingAltitude,
                    PATHFIND_TIME_BUDGET_MILLIS,
                    MAX_PATHFIND_TICK_CALLS
            );
            if (path.size() >= 2) {
                PathCache.putCopy(startId, endId, stopIndex, cruisingAltitude, path, startMillis);
                return path;
            }
        }

        // Fallback to vanilla MTR pathfinder for airplane mode or if the flexible endpoint probing fails.
        final ObjectArrayList<PathData> path = new ObjectArrayList<>();
        final ObjectArrayList pathFinders = new ObjectArrayList();
        pathFinders.add(new SidingPathFinder(data, startSavedRail, endSavedRail, stopIndex));

        final boolean[] failed = {false};
        final long deadlineMillis = startMillis + PATHFIND_TIME_BUDGET_MILLIS;
        for (int i = 0; i < MAX_PATHFIND_TICK_CALLS && !failed[0] && !pathFinders.isEmpty() && System.currentTimeMillis() < deadlineMillis; i++) {
            SidingPathFinder.findPathTick(path, pathFinders, cruisingAltitude, () -> {
            }, (startSavedRailFail, endSavedRailFail) -> failed[0] = true);
        }

        if (failed[0] || !pathFinders.isEmpty() || path.size() < 2) {
            return new ObjectArrayList<>();
        }

        PathCache.putCopy(startId, endId, stopIndex, cruisingAltitude, path, startMillis);

        return path;
    }

    @Unique
    private static void jme$removeOverlappingFirstSegment(ObjectArrayList<PathData> path, ObjectArrayList<PathData> newPath) {
        if (path == null || path.isEmpty() || newPath == null || newPath.isEmpty()) {
            return;
        }

        final PathData last = path.get(path.size() - 1);
        final PathData first = newPath.get(0);
        if (last != null && first != null && last.isSameRail(first)) {
            newPath.remove(0);
        }
    }

    @Unique
    private static void jme$appendMainRouteTail(ObjectArrayList<PathData> originalMainRoute, ObjectArrayList<PathData> newMainRoute, long secondStopId) {
        if (originalMainRoute == null || originalMainRoute.isEmpty() || newMainRoute == null || secondStopId == 0) {
            return;
        }

        int tailStartIndex = -1;
        for (int i = 0; i < originalMainRoute.size(); i++) {
            final PathData pathData = originalMainRoute.get(i);
            if (pathData != null && pathData.getDwellTime() > 0 && pathData.getSavedRailBaseId() == secondStopId) {
                tailStartIndex = i + 1;
                break;
            }
        }

        if (tailStartIndex < 0 || tailStartIndex >= originalMainRoute.size()) {
            return;
        }

        for (int i = tailStartIndex; i < originalMainRoute.size(); i++) {
            final PathData pathData = originalMainRoute.get(i);
            if (pathData == null) {
                continue;
            }

            final PathData lastPathData = newMainRoute.isEmpty() ? null : newMainRoute.get(newMainRoute.size() - 1);
            if (lastPathData != null && lastPathData.isSameRail(pathData)) {
                continue;
            }

            newMainRoute.add(pathData);
        }
    }

    @Unique
    private static void jme$regeneratePathDistances(ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding) {
        if (pathSidingToMainRoute == null || pathMainRoute == null || pathMainRouteToSiding == null) {
            return;
        }

        SidingPathFinder.generatePathDataDistances(pathSidingToMainRoute, 0);

        final double mainRouteStartDistance;
        if (pathSidingToMainRoute.isEmpty()) {
            mainRouteStartDistance = 0;
        } else {
            mainRouteStartDistance = pathSidingToMainRoute.get(pathSidingToMainRoute.size() - 1).getEndDistance();
        }

        SidingPathFinder.generatePathDataDistances(pathMainRoute, mainRouteStartDistance);

        if (!pathMainRouteToSiding.isEmpty()) {
            final double mainToSidingStartDistance = pathMainRoute.isEmpty() ? mainRouteStartDistance : pathMainRoute.get(pathMainRoute.size() - 1).getEndDistance();
            SidingPathFinder.generatePathDataDistances(pathMainRouteToSiding, mainToSidingStartDistance);
        }
    }

    @Unique
    private static boolean jme$hasInvalidImmediateOppositeRailTransition(ObjectArrayList<PathData> path) {
        if (path == null || path.size() < 2) {
            return false;
        }

        for (int i = 1; i < path.size(); i++) {
            final PathData previous = path.get(i - 1);
            final PathData current = path.get(i);
            if (jme$isInvalidOppositeTransition(previous, current)) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private static boolean jme$isInvalidOppositeTransition(PathData previous, PathData current) {
        return previous != null
                && current != null
                && previous.isOppositeRail(current)
                && previous.getDwellTime() <= 0
                && current.getDwellTime() <= 0;
    }

    @Unique
    private static boolean jme$populateCandidatePaths(
            Data data,
            Siding siding,
            Platform candidatePlatform,
            Platform secondStopPlatform,
            int firstStopIndex,
            int secondStopIndex,
            long cruisingAltitude,
            ObjectArrayList<PathData>[] selectedPaths
    ) {
        if (data == null || siding == null || candidatePlatform == null || secondStopPlatform == null || selectedPaths == null || selectedPaths.length < 2) {
            return false;
        }

        // MTR's SidingPathFinder writes the *end* platform with stopIndex + 1.
        // When generating a leg that ends at stop N, the path finder should use (N - 1) as input.
        // Examples:
        // - Siding -> first stop (N=0): -1
        // - Stop 0 -> stop 1 (N=1): 0
        final int sidingToFirstStopIndex = firstStopIndex - 1;
        final int firstToSecondStopIndex = secondStopIndex - 1;

        final ObjectArrayList<PathData> pathFromSiding = jme$findPath(data, siding, candidatePlatform, sidingToFirstStopIndex, cruisingAltitude);
        final ObjectArrayList<PathData> pathToSecondStop = jme$findPath(data, candidatePlatform, secondStopPlatform, firstToSecondStopIndex, cruisingAltitude);
        if (pathFromSiding.isEmpty() || pathToSecondStop.isEmpty()) {
            return false;
        }

        if (jme$hasInvalidImmediateOppositeRailTransition(pathFromSiding) || jme$hasInvalidImmediateOppositeRailTransition(pathToSecondStop)) {
            return false;
        }

        final PathData lastPathToPlatform = pathFromSiding.get(pathFromSiding.size() - 1);
        final PathData firstPathFromPlatform = pathToSecondStop.get(0);
        if (jme$isInvalidOppositeTransition(lastPathToPlatform, firstPathFromPlatform)) {
            return false;
        }

        selectedPaths[0] = pathFromSiding;
        selectedPaths[1] = pathToSecondStop;
        return true;
    }

    @Unique
    private static Iterable<Siding> jme$getSidingsToCheck(Siding siding, Data data) {
        if (data != null && data.sidings != null && !data.sidings.isEmpty()) {
            return data.sidings;
        }
        if (siding != null && siding.area != null && siding.area.savedRails != null && !siding.area.savedRails.isEmpty()) {
            return siding.area.savedRails;
        }
        if (siding != null) {
            return ObjectArrayList.of(siding);
        }
        return ObjectArrayList.of();
    }

    @Unique
    private static Data jme$getDataFromSiding(Siding siding) {
        if (siding == null) {
            return null;
        }

        try {
            Field dataField = jme$dataField;
            if (dataField == null) {
                Class<?> check = siding.getClass();
                while (check != null && dataField == null) {
                    try {
                        dataField = check.getDeclaredField("data");
                    } catch (NoSuchFieldException ignored) {
                        check = check.getSuperclass();
                    }
                }

                if (dataField == null) {
                    return null;
                }

                dataField.setAccessible(true);
                jme$dataField = dataField;
            }

            final Object dataObject = dataField.get(siding);
            return dataObject instanceof Data ? (Data) dataObject : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static ObjectArrayList<PathData>[] jme$newPathSelectionArray() {
        return (ObjectArrayList<PathData>[]) new ObjectArrayList[2];
    }
}
