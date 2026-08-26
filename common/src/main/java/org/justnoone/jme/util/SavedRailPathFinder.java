package org.justnoone.jme.util;

import org.mtr.core.data.Data;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.SavedRailBase;
import org.mtr.core.data.TransportMode;
import org.mtr.core.path.PathFinder;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Pathfinding helper for SavedRailBase-to-SavedRailBase paths.
 *
 * <p>MTR's {@code SidingPathFinder} always starts pathfinding from {@code position1} of the start/end rails.
 * That can fail for some directed / one-way cases depending on how rails were placed (node order).
 *
 * <p>This helper tries both endpoints (position1/position2) for the start and end saved rails, and returns
 * the first/best path that works.
 */
public final class SavedRailPathFinder {

    private SavedRailPathFinder() {
    }

    public static ObjectArrayList<PathData> findPath(
            Data data,
            SavedRailBase<?, ?> startSavedRail,
            SavedRailBase<?, ?> endSavedRail,
            int stopIndex,
            long cruisingAltitude,
            long timeBudgetMillis,
            int maxStepCalls
    ) {
        if (data == null || startSavedRail == null || endSavedRail == null) {
            return new ObjectArrayList<>();
        }

        final long startId = startSavedRail.getId();
        final long endId = endSavedRail.getId();
        if (startId == 0 || endId == 0 || startId == endId) {
            return new ObjectArrayList<>();
        }

        final Position startPos1 = startSavedRail.getRandomPosition();
        final Position startPos2 = startSavedRail.getOtherPosition(startPos1);
        final Position endPos1 = endSavedRail.getRandomPosition();
        final Position endPos2 = endSavedRail.getOtherPosition(endPos1);

        final List<PositionPair> pairs = new ArrayList<>(4);
        addPair(pairs, startPos1, endPos1);
        addPair(pairs, startPos1, endPos2);
        addPair(pairs, startPos2, endPos1);
        addPair(pairs, startPos2, endPos2);

        final long startMillis = System.currentTimeMillis();
        final long deadlineMillis = startMillis + Math.max(1, timeBudgetMillis);

        PathCandidate best = null;

        for (int i = 0; i < pairs.size() && System.currentTimeMillis() < deadlineMillis; i++) {
            final PositionPair pair = pairs.get(i);
            final long remainingMillis = deadlineMillis - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                break;
            }

            final long perAttemptBudget = Math.max(10, remainingMillis / Math.max(1, pairs.size() - i));
            final PathCandidate candidate = tryFindPath(
                    data,
                    startSavedRail,
                    endSavedRail,
                    stopIndex,
                    cruisingAltitude,
                    pair.start,
                    pair.end,
                    System.currentTimeMillis() + perAttemptBudget,
                    maxStepCalls
            );
            if (candidate == null) {
                continue;
            }

            if (best == null) {
                best = candidate;
            } else if (candidate.totalDuration < best.totalDuration) {
                best = candidate;
            } else if (candidate.totalDuration == best.totalDuration && candidate.path.size() < best.path.size()) {
                best = candidate;
            }

            // Fast path: if the "default" endpoints worked, prefer it to avoid unnecessary exploration.
            if (i == 0 && best != null) {
                break;
            }
        }

        return best == null ? new ObjectArrayList<>() : best.path;
    }

    private static void addPair(List<PositionPair> pairs, Position start, Position end) {
        if (start == null || end == null) {
            return;
        }
        for (int i = 0; i < pairs.size(); i++) {
            final PositionPair existing = pairs.get(i);
            if (existing.start.equals(start) && existing.end.equals(end)) {
                return;
            }
        }
        pairs.add(new PositionPair(start, end));
    }

    @Nullable
    private static PathCandidate tryFindPath(
            Data data,
            SavedRailBase<?, ?> startSavedRail,
            SavedRailBase<?, ?> endSavedRail,
            int stopIndex,
            long cruisingAltitude,
            Position startPos,
            Position endPos,
            long attemptDeadlineMillis,
            int maxStepCalls
    ) {
        if (data == null || startSavedRail == null || endSavedRail == null || startPos == null || endPos == null) {
            return null;
        }

        final TransportMode transportMode = startSavedRail.getTransportMode();
        final FlexiblePathFinder pathFinder = new FlexiblePathFinder(data, transportMode, startPos, endPos);

        // PathFinder#findPath() is incremental; the number of calls required can be high on real networks.
        // Bound by time first, and keep a generous hard cap to avoid "fail early" behavior.
        final int maxCalls = Math.max(256, Math.max(8, maxStepCalls) * 64);
        for (int i = 0; i < maxCalls && System.currentTimeMillis() < attemptDeadlineMillis; i++) {
            final PathCandidate candidate = pathFinder.tickToCandidate(startSavedRail, endSavedRail, stopIndex, cruisingAltitude);
            if (candidate == null) {
                continue;
            }

            if (candidate.path.isEmpty() || candidate.path.size() < 2) {
                return null;
            }
            return candidate;
        }

        return null;
    }

    private static void padPositions(
            ObjectArrayList<Position> positions,
            SavedRailBase<?, ?> savedRail,
            boolean isEnd,
            Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> positionsToRail
    ) {
        if (positions == null || positions.isEmpty() || savedRail == null || positionsToRail == null) {
            return;
        }

        final Position edgePosition = isEnd ? positions.get(positions.size() - 1) : positions.get(0);
        if (edgePosition == null) {
            return;
        }

        if (!savedRail.containsPos(edgePosition)) {
            final Object2ObjectOpenHashMap<Position, Rail> railConnections = positionsToRail.get(edgePosition);
            if (railConnections == null) {
                return;
            }

            Position newPosition = null;
            for (final Position connected : railConnections.keySet()) {
                if (savedRail.containsPos(connected)) {
                    newPosition = connected;
                    break;
                }
            }
            if (newPosition == null) {
                return;
            }

            final Position other = savedRail.getOtherPosition(newPosition);
            if (isEnd) {
                positions.add(newPosition);
                positions.add(other);
            } else {
                // Insert in the same order as MTR: other -> newPosition -> existing...
                positions.add(0, newPosition);
                positions.add(0, other);
            }
        } else if (positions.size() > 1) {
            final Position adjacent = isEnd ? positions.get(positions.size() - 2) : positions.get(1);
            if (adjacent != null && !savedRail.containsPos(adjacent)) {
                final Position other = savedRail.getOtherPosition(edgePosition);
                if (isEnd) {
                    positions.add(other);
                } else {
                    positions.add(0, other);
                }
            }
        }
    }

    private static ObjectArrayList<PathData> buildPathData(
            Data data,
            TransportMode transportMode,
            SavedRailBase<?, ?> endSavedRail,
            int stopIndex,
            long cruisingAltitude,
            ObjectArrayList<Position> positions
    ) {
        final ObjectArrayList<PathData> path = new ObjectArrayList<>();
        if (data == null || transportMode == null || endSavedRail == null || positions == null || positions.size() < 2) {
            return path;
        }

        for (int i = 1; i < positions.size(); i++) {
            final Position position1 = positions.get(i - 1);
            final Position position2 = positions.get(i);
            if (position1 == null || position2 == null) {
                return new ObjectArrayList<>();
            }

            final Rail rail = Data.tryGet(data.positionsToRail, position1, position2);
            if (rail == null) {
                // Airplane special case: unreachable for normal rail graphs, but keep behavior close to MTR.
                if (transportMode == TransportMode.AIRPLANE) {
                    return new ObjectArrayList<>();
                }
                return new ObjectArrayList<>();
            }

            if (i == positions.size() - 1) {
                final long dwellTime = endSavedRail instanceof Platform ? ((Platform) endSavedRail).getDwellTime() : 1;
                path.add(new PathData(rail, endSavedRail.getId(), dwellTime, stopIndex + 1, position1, position2));
            } else if (rail.canTurnBack() && i < positions.size() - 1 && positions.get(i + 1).equals(position1)) {
                path.add(new PathData(rail, 0, 1, stopIndex, position1, position2));
            } else {
                path.add(new PathData(rail, 0, 0, stopIndex, position1, position2));
            }
        }

        return path;
    }

    private static final class PositionPair {
        private final Position start;
        private final Position end;

        private PositionPair(Position start, Position end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class PathCandidate {
        private final ObjectArrayList<PathData> path;
        private final long totalDuration;

        private PathCandidate(ObjectArrayList<PathData> path, long totalDuration) {
            this.path = path;
            this.totalDuration = totalDuration;
        }
    }

    private static final class FlexiblePathFinder extends PathFinder<PositionAndAngle> {

        private final Data data;
        private final TransportMode transportMode;
        private final Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> positionsToRail;
        private final Object2ObjectOpenHashMap<Position, Rail> runwaysInbound;
        private final ObjectOpenHashSet<Position> runwaysOutbound;

        private static final class Connection extends ConnectionDetails<PositionAndAngle> {

            private Connection(PositionAndAngle node, long duration, long waitingTime, long routeId) {
                super(node, duration, waitingTime, routeId);
            }
        }

        private FlexiblePathFinder(Data data, TransportMode transportMode, Position start, Position end) {
            super(new PositionAndAngle(start, null), new PositionAndAngle(end, null));
            this.data = data;
            this.transportMode = transportMode;
            this.positionsToRail = data.positionsToRail;
            this.runwaysInbound = data.runwaysInbound;
            this.runwaysOutbound = data.runwaysOutbound;
        }

        @Nullable
        private PathCandidate tickToCandidate(
                SavedRailBase<?, ?> startSavedRail,
                SavedRailBase<?, ?> endSavedRail,
                int stopIndex,
                long cruisingAltitude
        ) {
            final ObjectArrayList<ConnectionDetails<PositionAndAngle>> connectionDetailsList = findPath();
            if (connectionDetailsList == null) {
                return null;
            }

            if (connectionDetailsList.isEmpty()) {
                return new PathCandidate(new ObjectArrayList<>(), Long.MAX_VALUE);
            }

            long totalDuration = 0;
            final ObjectArrayList<Position> positions = new ObjectArrayList<>();
            for (int i = 0; i < connectionDetailsList.size(); i++) {
                final ConnectionDetails<PositionAndAngle> details = connectionDetailsList.get(i);
                if (details == null || details.node == null || details.node.position == null) {
                    continue;
                }

                totalDuration += details.duration + details.waitingTime;
                positions.add(details.node.position);
            }

            if (positions.size() < 2) {
                return new PathCandidate(new ObjectArrayList<>(), totalDuration);
            }

            // Ensure the path includes the "full" start/end saved rails, matching MTR behavior.
            padPositions(positions, startSavedRail, false, positionsToRail);
            padPositions(positions, endSavedRail, true, positionsToRail);

            final ObjectArrayList<PathData> path = buildPathData(data, transportMode, endSavedRail, stopIndex, cruisingAltitude, positions);
            return new PathCandidate(path, totalDuration);
        }

        @Override
        protected ObjectArrayList<ConnectionDetails<PositionAndAngle>> getConnections(long elapsedTime, PositionAndAngle node, @Nullable Long previousRouteId) {
            final ObjectArrayList<ConnectionDetails<PositionAndAngle>> connections = new ObjectArrayList<>();
            final Object2ObjectOpenHashMap<Position, Rail> railConnections = positionsToRail.get(node.position);

            if (railConnections != null) {
                railConnections.forEach((position, rail) -> {
                    final double speedLimit = rail.getSpeedLimitMetersPerMillisecond(node.position);
                    if (speedLimit > 0 && (node.angle == null || node.angle == rail.getStartAngle(node.position) || rail.canTurnBack())) {
                        connections.add(new Connection(
                                new PositionAndAngle(position, rail.getStartAngle(position).getOpposite()),
                                Math.round(rail.railMath.getLength() / speedLimit),
                                0,
                                0
                        ));
                    }
                });
            }

            if (transportMode == TransportMode.AIRPLANE && runwaysOutbound.contains(node.position)) {
                runwaysInbound.forEach((position, rail) -> connections.add(new Connection(new PositionAndAngle(position, rail.getStartAngle(position)), 1, 0, 0)));
            }

            return connections;
        }

        @Override
        protected long getWeightFromEndNode(PositionAndAngle node) {
            return node.position.manhattanDistance(endNode.position);
        }
    }

    private static final class PositionAndAngle {

        private final Position position;
        @Nullable
        private final Angle angle;

        private PositionAndAngle(Position position, @Nullable Angle angle) {
            this.position = position;
            this.angle = angle;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof PositionAndAngle)) {
                return false;
            }
            final PositionAndAngle other = (PositionAndAngle) obj;
            return position.equals(other.position) && (angle == null || other.angle == null || angle == other.angle);
        }

        @Override
        public int hashCode() {
            return position.hashCode() ^ ((angle == null ? 0 : angle.ordinal() + 1) << 8);
        }
    }
}
