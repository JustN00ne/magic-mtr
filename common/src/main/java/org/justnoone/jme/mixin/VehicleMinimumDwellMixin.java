package org.justnoone.jme.mixin;

import org.justnoone.jme.rail.RouteTimetableRegistry;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.data.VehiclePosition;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Keeps the minimum dwell time of a route's flexible timetable ({@link RouteTimetableRegistry}) for late trains.
 * <p>
 * A late train stopped at a platform skips part of its dwell time to catch up (the siding's "reduce dwell time"
 * percentage): MTR adds {@code min(delay, remaining dwell * percentage)} to the elapsed dwell time once. That jump is
 * capped here so the train still stays at least the route's minimum dwell time.
 */
@Mixin(value = Vehicle.class, remap = false)
public abstract class VehicleMinimumDwellMixin {

    @Unique
    private int jme$stoppedIndex;

    @Inject(method = "simulateStopped", at = @At("HEAD"), remap = false)
    private void jme$rememberStoppedIndex(long millisElapsed, @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, int currentIndex, CallbackInfo ci) {
        jme$stoppedIndex = currentIndex;
    }

    @Redirect(
            method = "simulateStopped",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(JJ)J", ordinal = 0),
            remap = false
    )
    private long jme$keepMinimumDwellTime(long delay, long dwellTimeReduction) {
        final long skipped = Math.min(delay, dwellTimeReduction);
        if (RouteTimetableRegistry.isEmpty()) {
            return skipped;
        }
        final VehicleExtraData vehicleExtraData = ((Vehicle) (Object) this).vehicleExtraData;
        if (vehicleExtraData == null || vehicleExtraData.immutablePath == null) {
            return skipped;
        }
        final RouteTimetableRegistry.Entry entry = RouteTimetableRegistry.get(vehicleExtraData.getThisRouteId());
        final PathData stop = Utilities.getElement(vehicleExtraData.immutablePath, jme$stoppedIndex);
        if (entry == null || entry.minimumDwellMillis <= 0 || stop == null) {
            return skipped;
        }
        return Math.min(skipped, Math.max(0, stop.getDwellTime() - entry.minimumDwellMillis));
    }
}
