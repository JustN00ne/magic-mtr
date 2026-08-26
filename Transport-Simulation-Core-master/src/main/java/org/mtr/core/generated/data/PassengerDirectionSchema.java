package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class PassengerDirectionSchema implements SerializedDataBase {

	protected final long routeId;

	protected final long startPlatformId;

	protected final long endPlatformId;

	protected final long startTime;

	protected final long endTime;

	protected PassengerDirectionSchema(final long routeId, final long startPlatformId, final long endPlatformId, final long startTime, final long endTime) {
		this.routeId = routeId;
		this.startPlatformId = startPlatformId;
		this.endPlatformId = endPlatformId;
		this.startTime = startTime;
		this.endTime = endTime;
	}

	protected PassengerDirectionSchema(final ReaderBase readerBase) {
		routeId = readerBase.getLong("routeId", 0);
		startPlatformId = readerBase.getLong("startPlatformId", 0);
		endPlatformId = readerBase.getLong("endPlatformId", 0);
		startTime = readerBase.getLong("startTime", 0);
		endTime = readerBase.getLong("endTime", 0);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("routeId", routeId);
		writerBase.writeLong("startPlatformId", startPlatformId);
		writerBase.writeLong("endPlatformId", endPlatformId);
		writerBase.writeLong("startTime", startTime);
		writerBase.writeLong("endTime", endTime);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "routeId: " + routeId + "\n"
			+ "startPlatformId: " + startPlatformId + "\n"
			+ "endPlatformId: " + endPlatformId + "\n"
			+ "startTime: " + startTime + "\n"
			+ "endTime: " + endTime + "\n"
		;
	}
}