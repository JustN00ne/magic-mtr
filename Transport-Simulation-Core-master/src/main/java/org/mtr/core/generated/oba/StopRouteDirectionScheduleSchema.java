package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class StopRouteDirectionScheduleSchema implements SerializedDataBase {

	protected final String tripHeadsign;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<ScheduleStopTime> scheduleStopTimes = new it.unimi.dsi.fastutil.objects.ObjectArrayList<ScheduleStopTime>();

	protected StopRouteDirectionScheduleSchema(final String tripHeadsign) {
		this.tripHeadsign = tripHeadsign;
	}

	protected StopRouteDirectionScheduleSchema(final ReaderBase readerBase) {
		tripHeadsign = readerBase.getString("tripHeadsign", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("scheduleStopTimes", scheduleStopTimes::clear, readerBaseChild -> scheduleStopTimes.add(new ScheduleStopTime(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("tripHeadsign", tripHeadsign);
		serializeScheduleStopTimes(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "tripHeadsign: " + tripHeadsign + "\n"
			+ "scheduleStopTimes: " + scheduleStopTimes + "\n"
		;
	}

	protected void serializeScheduleStopTimes(final WriterBase writerBase) {
		writerBase.writeDataset(scheduleStopTimes, "scheduleStopTimes");
	}
}