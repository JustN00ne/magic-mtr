package org.justnoone.jme.client;

import org.mtr.core.data.Position;
import org.mtr.core.tool.Angle;

import java.util.Objects;

/**
 * Lightweight key for direction-aware pathfinding on the in-game dashboard map.
 * Angle can be null to represent "no required direction yet" (start state).
 */
public final class PositionAngleKey {

    public final Position position;
    public final Angle angle;

    public PositionAngleKey(Position position, Angle angle) {
        this.position = position;
        this.angle = angle;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PositionAngleKey)) {
            return false;
        }
        final PositionAngleKey other = (PositionAngleKey) obj;
        return Objects.equals(position, other.position) && angle == other.angle;
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, angle);
    }
}

