package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class ScheduledStopTimeSchema implements SerializedDataBase {

	protected final boolean arrivalEnabled;

	protected final long arrivalTime;

	protected final boolean departureEnabled;

	protected final long departureTime;

	protected final String tripId;

	protected ScheduledStopTimeSchema(final boolean arrivalEnabled, final long arrivalTime, final boolean departureEnabled, final long departureTime, final String tripId) {
		this.arrivalEnabled = arrivalEnabled;
		this.arrivalTime = arrivalTime;
		this.departureEnabled = departureEnabled;
		this.departureTime = departureTime;
		this.tripId = tripId;
	}

	protected ScheduledStopTimeSchema(final ReaderBase readerBase) {
		arrivalEnabled = readerBase.getBoolean("arrivalEnabled", false);
		arrivalTime = readerBase.getLong("arrivalTime", 0);
		departureEnabled = readerBase.getBoolean("departureEnabled", false);
		departureTime = readerBase.getLong("departureTime", 0);
		tripId = readerBase.getString("tripId", "");
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeBoolean("arrivalEnabled", arrivalEnabled);
		writerBase.writeLong("arrivalTime", arrivalTime);
		writerBase.writeBoolean("departureEnabled", departureEnabled);
		writerBase.writeLong("departureTime", departureTime);
		writerBase.writeString("tripId", tripId);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "arrivalEnabled: " + arrivalEnabled + "\n"
			+ "arrivalTime: " + arrivalTime + "\n"
			+ "departureEnabled: " + departureEnabled + "\n"
			+ "departureTime: " + departureTime + "\n"
			+ "tripId: " + tripId + "\n"
		;
	}
}