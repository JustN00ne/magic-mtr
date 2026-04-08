package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class VehicleStatusSchema implements SerializedDataBase {

	protected final String vehicleId;

	protected final long lastUpdateTime;

	protected final long lastLocationUpdateTime;

	protected final Position location;

	protected final String tripId;

	protected final TripStatus tripStatus;

	protected VehicleStatusSchema(final String vehicleId, final long lastUpdateTime, final long lastLocationUpdateTime, final Position location, final String tripId, final TripStatus tripStatus) {
		this.vehicleId = vehicleId;
		this.lastUpdateTime = lastUpdateTime;
		this.lastLocationUpdateTime = lastLocationUpdateTime;
		this.location = location;
		this.tripId = tripId;
		this.tripStatus = tripStatus;
	}

	protected VehicleStatusSchema(final ReaderBase readerBase) {
		vehicleId = readerBase.getString("vehicleId", "");
		lastUpdateTime = readerBase.getLong("lastUpdateTime", 0);
		lastLocationUpdateTime = readerBase.getLong("lastLocationUpdateTime", 0);
		location = new Position(readerBase.getChild("location"));
		tripId = readerBase.getString("tripId", "");
		tripStatus = new TripStatus(readerBase.getChild("tripStatus"));
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("vehicleId", vehicleId);
		writerBase.writeLong("lastUpdateTime", lastUpdateTime);
		writerBase.writeLong("lastLocationUpdateTime", lastLocationUpdateTime);
		if (location != null) location.serializeData(writerBase.writeChild("location"));
		writerBase.writeString("tripId", tripId);
		if (tripStatus != null) tripStatus.serializeData(writerBase.writeChild("tripStatus"));
	}

	@Nonnull
	public String toString() {
		return ""
			+ "vehicleId: " + vehicleId + "\n"
			+ "lastUpdateTime: " + lastUpdateTime + "\n"
			+ "lastLocationUpdateTime: " + lastLocationUpdateTime + "\n"
			+ "location: " + location + "\n"
			+ "tripId: " + tripId + "\n"
			+ "tripStatus: " + tripStatus + "\n"
		;
	}
}