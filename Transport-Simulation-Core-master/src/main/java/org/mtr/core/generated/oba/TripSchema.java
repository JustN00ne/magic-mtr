package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class TripSchema implements SerializedDataBase {

	protected final String routeId;

	protected final String serviceId;

	protected final String id;

	protected final String tripHeadsign;

	protected final String tripShortName;

	protected final long directionId;

	protected final String blockId;

	protected final String shapeId;

	protected final String routeShortName;

	protected final String timeZone;

	protected TripSchema(final String routeId, final String serviceId, final String id, final String tripHeadsign, final String tripShortName, final long directionId, final String blockId, final String shapeId, final String routeShortName, final String timeZone) {
		this.routeId = routeId;
		this.serviceId = serviceId;
		this.id = id;
		this.tripHeadsign = tripHeadsign;
		this.tripShortName = tripShortName;
		this.directionId = directionId;
		this.blockId = blockId;
		this.shapeId = shapeId;
		this.routeShortName = routeShortName;
		this.timeZone = timeZone;
	}

	protected TripSchema(final ReaderBase readerBase) {
		routeId = readerBase.getString("routeId", "");
		serviceId = readerBase.getString("serviceId", "");
		id = readerBase.getString("id", "");
		tripHeadsign = readerBase.getString("tripHeadsign", "");
		tripShortName = readerBase.getString("tripShortName", "");
		directionId = readerBase.getLong("directionId", 0);
		blockId = readerBase.getString("blockId", "");
		shapeId = readerBase.getString("shapeId", "");
		routeShortName = readerBase.getString("routeShortName", "");
		timeZone = readerBase.getString("timeZone", "");
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("routeId", routeId);
		writerBase.writeString("serviceId", serviceId);
		writerBase.writeString("id", id);
		writerBase.writeString("tripHeadsign", tripHeadsign);
		writerBase.writeString("tripShortName", tripShortName);
		writerBase.writeLong("directionId", directionId);
		writerBase.writeString("blockId", blockId);
		writerBase.writeString("shapeId", shapeId);
		writerBase.writeString("routeShortName", routeShortName);
		writerBase.writeString("timeZone", timeZone);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "routeId: " + routeId + "\n"
			+ "serviceId: " + serviceId + "\n"
			+ "id: " + id + "\n"
			+ "tripHeadsign: " + tripHeadsign + "\n"
			+ "tripShortName: " + tripShortName + "\n"
			+ "directionId: " + directionId + "\n"
			+ "blockId: " + blockId + "\n"
			+ "shapeId: " + shapeId + "\n"
			+ "routeShortName: " + routeShortName + "\n"
			+ "timeZone: " + timeZone + "\n"
		;
	}
}