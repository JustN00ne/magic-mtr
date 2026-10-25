package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class TripWithStopTimesSchema implements SerializedDataBase {

	protected final String tripId;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<ScheduledStopTime> scheduledStopTimes = new it.unimi.dsi.fastutil.objects.ObjectArrayList<ScheduledStopTime>();

	protected TripWithStopTimesSchema(final String tripId) {
		this.tripId = tripId;
	}

	protected TripWithStopTimesSchema(final ReaderBase readerBase) {
		tripId = readerBase.getString("tripId", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("scheduledStopTimes", scheduledStopTimes::clear, readerBaseChild -> scheduledStopTimes.add(new ScheduledStopTime(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("tripId", tripId);
		serializeScheduledStopTimes(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "tripId: " + tripId + "\n"
			+ "scheduledStopTimes: " + scheduledStopTimes + "\n"
		;
	}

	protected void serializeScheduledStopTimes(final WriterBase writerBase) {
		writerBase.writeDataset(scheduledStopTimes, "scheduledStopTimes");
	}
}