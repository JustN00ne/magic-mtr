package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class ScheduleStopTimeSchema implements SerializedDataBase {

	protected final long arrivalTime;

	protected final long departureTime;

	protected final String serviceId;

	protected final String tripId;

	protected ScheduleStopTimeSchema(final long arrivalTime, final long departureTime, final String serviceId, final String tripId) {
		this.arrivalTime = arrivalTime;
		this.departureTime = departureTime;
		this.serviceId = serviceId;
		this.tripId = tripId;
	}

	protected ScheduleStopTimeSchema(final ReaderBase readerBase) {
		arrivalTime = readerBase.getLong("arrivalTime", 0);
		departureTime = readerBase.getLong("departureTime", 0);
		serviceId = readerBase.getString("serviceId", "");
		tripId = readerBase.getString("tripId", "");
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("arrivalTime", arrivalTime);
		writerBase.writeLong("departureTime", departureTime);
		writerBase.writeString("serviceId", serviceId);
		writerBase.writeString("tripId", tripId);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "arrivalTime: " + arrivalTime + "\n"
			+ "departureTime: " + departureTime + "\n"
			+ "serviceId: " + serviceId + "\n"
			+ "tripId: " + tripId + "\n"
		;
	}
}