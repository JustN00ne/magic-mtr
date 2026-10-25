package org.mtr.core.generated.map;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.map.*;

public abstract class DirectionsConnectionSchema implements SerializedDataBase {

	protected final String routeId;

	protected final String startStationId;

	protected final String endStationId;

	protected final String startPlatformName;

	protected final String endPlatformName;

	protected final long startTime;

	protected final long endTime;

	protected final long walkingDistance;

	protected DirectionsConnectionSchema(final String routeId, final String startStationId, final String endStationId, final String startPlatformName, final String endPlatformName, final long startTime, final long endTime, final long walkingDistance) {
		this.routeId = routeId;
		this.startStationId = startStationId;
		this.endStationId = endStationId;
		this.startPlatformName = startPlatformName;
		this.endPlatformName = endPlatformName;
		this.startTime = startTime;
		this.endTime = endTime;
		this.walkingDistance = walkingDistance;
	}

	protected DirectionsConnectionSchema(final ReaderBase readerBase) {
		routeId = readerBase.getString("routeId", "");
		startStationId = readerBase.getString("startStationId", "");
		endStationId = readerBase.getString("endStationId", "");
		startPlatformName = readerBase.getString("startPlatformName", "");
		endPlatformName = readerBase.getString("endPlatformName", "");
		startTime = readerBase.getLong("startTime", 0);
		endTime = readerBase.getLong("endTime", 0);
		walkingDistance = readerBase.getLong("walkingDistance", 0);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("routeId", routeId);
		writerBase.writeString("startStationId", startStationId);
		writerBase.writeString("endStationId", endStationId);
		writerBase.writeString("startPlatformName", startPlatformName);
		writerBase.writeString("endPlatformName", endPlatformName);
		writerBase.writeLong("startTime", startTime);
		writerBase.writeLong("endTime", endTime);
		writerBase.writeLong("walkingDistance", walkingDistance);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "routeId: " + routeId + "\n"
			+ "startStationId: " + startStationId + "\n"
			+ "endStationId: " + endStationId + "\n"
			+ "startPlatformName: " + startPlatformName + "\n"
			+ "endPlatformName: " + endPlatformName + "\n"
			+ "startTime: " + startTime + "\n"
			+ "endTime: " + endTime + "\n"
			+ "walkingDistance: " + walkingDistance + "\n"
		;
	}
}