package org.justnoone.jme.mixin;

import org.justnoone.jme.rail.CronTimetableRegistry;
import org.justnoone.jme.util.CronTimetables;
import org.mtr.core.data.Data;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Siding;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * Applies the parts of a cron timetable that MTR's daily departure list cannot express (the day, month and weekday
 * fields, and daylight-saving changes; see {@link CronTimetables}): a candidate departure the cron expression does not
 * schedule at its actual local date and time neither spawns a train nor appears on departure boards.
 */
@Mixin(value = Siding.class, remap = false)
public abstract class SidingCronTimetableMixin {

    @Unique
    private static final ThreadLocal<int[]> JME_ARRIVALS_START = new ThreadLocal<>();

    @Shadow
    @Final
    private LongArrayList departures;

    @Inject(method = "matchDeparture", at = @At("RETURN"), cancellable = true, remap = false)
    private void jme$skipUnscheduledCronDeparture(CallbackInfoReturnable<Integer> cir) {
        final int departureIndex = cir.getReturnValueI();
        if (departureIndex < 0 || departureIndex >= departures.size()) {
            return;
        }
        final CronTimetableRegistry.Entry entry = jme$getRealTimeCronEntry();
        if (entry == null) {
            return;
        }
        final Data data = ((NameColorDataAccessor) this).jme$getData();
        if (data == null) {
            return;
        }
        // MTR has just matched this departure against the clock, so it is the most recent occurrence of its time.
        final long departureMillis = CronTimetables.latestOccurrence(data.getCurrentMillis(), departures.getLong(departureIndex));
        if (!CronTimetables.isScheduled(entry.cron, entry.zone, departureMillis)) {
            cir.setReturnValue(-1);
        }
    }

    @Inject(method = "getArrivals", at = @At("HEAD"), remap = false)
    private void jme$rememberArrivalsStart(long currentMillis, Platform platform, long count, ObjectArrayList<ArrivalResponse> arrivals, CallbackInfo ci) {
        JME_ARRIVALS_START.set(arrivals == null ? null : new int[]{arrivals.size()});
    }

    @Inject(method = "getArrivals", at = @At("RETURN"), remap = false)
    private void jme$removeUnscheduledCronArrivals(long currentMillis, Platform platform, long count, ObjectArrayList<ArrivalResponse> arrivals, CallbackInfo ci) {
        final int[] start = JME_ARRIVALS_START.get();
        JME_ARRIVALS_START.remove();
        if (start == null || arrivals == null || arrivals.size() <= start[0]) {
            return;
        }
        final CronTimetableRegistry.Entry entry = jme$getRealTimeCronEntry();
        if (entry == null) {
            return;
        }

        for (int i = arrivals.size() - 1; i >= start[0]; i--) {
            final ArrivalResponse arrival = arrivals.get(i);
            // A realtime arrival belongs to a train that is already running, so it was scheduled.
            if (arrival == null || arrival.getRealtime()) {
                continue;
            }
            final long departureIndex = arrival.getDepartureIndex();
            if (departureIndex < 0 || departureIndex >= departures.size()) {
                continue;
            }
            // The trip left the depot at the latest occurrence of its departure time before its scheduled arrival here
            // (trips shorter than a day, as MTR's daily repeat already assumes).
            final long departureMillis = CronTimetables.latestOccurrence(arrival.getArrival() - arrival.getDeviation(), departures.getLong((int) departureIndex));
            if (!CronTimetables.isScheduled(entry.cron, entry.zone, departureMillis)) {
                arrivals.remove(i);
            }
        }
    }

    /**
     * Only real-time depots need this: in Minecraft time, cron departures are exactly the minute and hour fields.
     */
    @Unique
    @Nullable
    private CronTimetableRegistry.Entry jme$getRealTimeCronEntry() {
        if (CronTimetableRegistry.isEmpty()) {
            return null;
        }
        final Siding siding = (Siding) (Object) this;
        if (!(siding.area instanceof Depot)) {
            return null;
        }
        final Depot depot = (Depot) siding.area;
        if (!depot.getUseRealTime() || depot.getTransportMode().continuousMovement) {
            return null;
        }
        return CronTimetableRegistry.getActive(depot.getId());
    }
}
