package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class StopTimeSchema implements SerializedDataBase {

	protected final String stopId;

	protected final long arrivalTime;

	protected final long departureTime;

	protected final long pickupType;

	protected final long dropOffType;

	protected final String stopHeadsign;

	protected StopTimeSchema(final String stopId, final long arrivalTime, final long departureTime, final long pickupType, final long dropOffType, final String stopHeadsign) {
		this.stopId = stopId;
		this.arrivalTime = arrivalTime;
		this.departureTime = departureTime;
		this.pickupType = pickupType;
		this.dropOffType = dropOffType;
		this.stopHeadsign = stopHeadsign;
	}

	protected StopTimeSchema(final ReaderBase readerBase) {
		stopId = readerBase.getString("stopId", "");
		arrivalTime = readerBase.getLong("arrivalTime", 0);
		departureTime = readerBase.getLong("departureTime", 0);
		pickupType = readerBase.getLong("pickupType", 0);
		dropOffType = readerBase.getLong("dropOffType", 0);
		stopHeadsign = readerBase.getString("stopHeadsign", "");
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("stopId", stopId);
		writerBase.writeLong("arrivalTime", arrivalTime);
		writerBase.writeLong("departureTime", departureTime);
		writerBase.writeLong("pickupType", pickupType);
		writerBase.writeLong("dropOffType", dropOffType);
		writerBase.writeString("stopHeadsign", stopHeadsign);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "stopId: " + stopId + "\n"
			+ "arrivalTime: " + arrivalTime + "\n"
			+ "departureTime: " + departureTime + "\n"
			+ "pickupType: " + pickupType + "\n"
			+ "dropOffType: " + dropOffType + "\n"
			+ "stopHeadsign: " + stopHeadsign + "\n"
		;
	}
}