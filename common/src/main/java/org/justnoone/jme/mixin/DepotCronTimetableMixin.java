package org.justnoone.jme.mixin;

import org.justnoone.jme.rail.CronTimetableRegistry;
import org.justnoone.jme.util.CronTimetables;
import org.mtr.core.data.Data;
import org.mtr.core.data.Depot;
import org.mtr.core.simulation.Simulator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

/**
 * Feeds a depot's cron timetable into MTR's departure generation.
 * <p>
 * {@code generatePlatformDirectionsAndWriteDeparturesToSidings} first builds the depot's daily departure list (from the
 * real-time list or from the hourly frequencies), sorts it, then hands the departures out to the sidings. The list is
 * replaced right at that sort, so everything after it (siding assignment, repeats, trips, arrivals) is MTR's own code
 * working on cron departures. Depots without a cron timetable are not touched.
 */
@Mixin(value = Depot.class, remap = false)
public abstract class DepotCronTimetableMixin {

    @ModifyArg(
            method = "generatePlatformDirectionsAndWriteDeparturesToSidings",
            at = @At(value = "INVOKE", target = "Ljava/util/Collections;sort(Ljava/util/List;)V", ordinal = 1),
            index = 0,
            remap = false
    )
    private List<Long> jme$applyCronTimetable(List<Long> departures) {
        final Depot depot = (Depot) (Object) this;
        final CronTimetableRegistry.Entry entry = CronTimetableRegistry.getActive(depot.getId());
        if (entry == null || depot.getTransportMode().continuousMovement) {
            return departures;
        }

        final Data data = ((NameColorDataAccessor) this).jme$getData();
        final long[] cronDepartures;
        if (depot.getUseRealTime()) {
            cronDepartures = CronTimetables.realTimeDepartures(entry.cron, entry.zone, data == null ? System.currentTimeMillis() : data.getCurrentMillis());
        } else if (data instanceof Simulator) {
            cronDepartures = jme$toGameMillis((Simulator) data, CronTimetables.gameTimeDepartures(entry.cron));
        } else {
            return departures;
        }

        departures.clear();
        for (final long departure : cronDepartures) {
            departures.add(departure);
        }
        return departures;
    }

    /**
     * Same conversion MTR applies to frequency-based departures: a time of the 24-hour Minecraft day becomes an offset
     * from the simulator's Minecraft midnight, scaled to the real length of a Minecraft day.
     */
    @Unique
    private static long[] jme$toGameMillis(Simulator simulator, long[] timesOfDay) {
        final long millisOfGameMidnight = simulator.getMillisOfGameMidnight();
        final long gameMillisPerDay = simulator.getGameMillisPerDay();
        final long[] result = new long[timesOfDay.length];
        for (int i = 0; i < timesOfDay.length; i++) {
            result[i] = millisOfGameMidnight + timesOfDay[i] * gameMillisPerDay / CronTimetables.MILLIS_PER_DAY;
        }
        return result;
    }
}
