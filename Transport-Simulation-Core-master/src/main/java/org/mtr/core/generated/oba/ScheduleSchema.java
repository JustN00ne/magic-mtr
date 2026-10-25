package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class ScheduleSchema implements SerializedDataBase {

	protected final String timeZone;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<StopTime> stopTimes = new it.unimi.dsi.fastutil.objects.ObjectArrayList<StopTime>();

	protected final String previousTripId;

	protected final String nextTripId;

	protected Frequency frequency = getDefaultFrequency();

	protected ScheduleSchema(final String timeZone, final String previousTripId, final String nextTripId) {
		this.timeZone = timeZone;
		this.previousTripId = previousTripId;
		this.nextTripId = nextTripId;
	}

	protected ScheduleSchema(final ReaderBase readerBase) {
		timeZone = readerBase.getString("timeZone", "");
		previousTripId = readerBase.getString("previousTripId", "");
		nextTripId = readerBase.getString("nextTripId", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("stopTimes", stopTimes::clear, readerBaseChild -> stopTimes.add(new StopTime(readerBaseChild)));
		readerBase.unpackChild("frequency", readerBaseChild -> frequency = new Frequency(readerBaseChild));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("timeZone", timeZone);
		serializeStopTimes(writerBase);
		writerBase.writeString("previousTripId", previousTripId);
		writerBase.writeString("nextTripId", nextTripId);
		serializeFrequency(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "timeZone: " + timeZone + "\n"
			+ "stopTimes: " + stopTimes + "\n"
			+ "previousTripId: " + previousTripId + "\n"
			+ "nextTripId: " + nextTripId + "\n"
			+ "frequency: " + frequency + "\n"
		;
	}

	protected void serializeStopTimes(final WriterBase writerBase) {
		writerBase.writeDataset(stopTimes, "stopTimes");
	}

	protected abstract Frequency getDefaultFrequency();

	protected void serializeFrequency(final WriterBase writerBase) {
		if (frequency != null) frequency.serializeData(writerBase.writeChild("frequency"));
	}
}