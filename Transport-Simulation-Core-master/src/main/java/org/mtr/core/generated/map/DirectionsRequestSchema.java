package org.mtr.core.generated.map;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.map.*;

public abstract class DirectionsRequestSchema implements SerializedDataBase {

	protected long startPositionX;

	protected long startPositionY;

	protected long startPositionZ;

	protected String startStationName = "";

	protected String startClientId = "";

	protected long endPositionX;

	protected long endPositionY;

	protected long endPositionZ;

	protected String endStationName = "";

	protected String endClientId = "";

	protected final long startTime;

	protected DirectionsRequestSchema(final long startTime) {
		this.startTime = startTime;
	}

	protected DirectionsRequestSchema(final ReaderBase readerBase) {
		startTime = readerBase.getLong("startTime", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackLong("startPositionX", value -> startPositionX = value);
		readerBase.unpackLong("startPositionY", value -> startPositionY = value);
		readerBase.unpackLong("startPositionZ", value -> startPositionZ = value);
		readerBase.unpackString("startStationName", value -> startStationName = value);
		readerBase.unpackString("startClientId", value -> startClientId = value);
		readerBase.unpackLong("endPositionX", value -> endPositionX = value);
		readerBase.unpackLong("endPositionY", value -> endPositionY = value);
		readerBase.unpackLong("endPositionZ", value -> endPositionZ = value);
		readerBase.unpackString("endStationName", value -> endStationName = value);
		readerBase.unpackString("endClientId", value -> endClientId = value);
	}

	public void serializeData(final WriterBase writerBase) {
		serializeStartPositionX(writerBase);
		serializeStartPositionY(writerBase);
		serializeStartPositionZ(writerBase);
		serializeStartStationName(writerBase);
		serializeStartClientId(writerBase);
		serializeEndPositionX(writerBase);
		serializeEndPositionY(writerBase);
		serializeEndPositionZ(writerBase);
		serializeEndStationName(writerBase);
		serializeEndClientId(writerBase);
		writerBase.writeLong("startTime", startTime);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "startPositionX: " + startPositionX + "\n"
			+ "startPositionY: " + startPositionY + "\n"
			+ "startPositionZ: " + startPositionZ + "\n"
			+ "startStationName: " + startStationName + "\n"
			+ "startClientId: " + startClientId + "\n"
			+ "endPositionX: " + endPositionX + "\n"
			+ "endPositionY: " + endPositionY + "\n"
			+ "endPositionZ: " + endPositionZ + "\n"
			+ "endStationName: " + endStationName + "\n"
			+ "endClientId: " + endClientId + "\n"
			+ "startTime: " + startTime + "\n"
		;
	}

	protected void serializeStartPositionX(final WriterBase writerBase) {
		writerBase.writeLong("startPositionX", startPositionX);
	}

	protected void serializeStartPositionY(final WriterBase writerBase) {
		writerBase.writeLong("startPositionY", startPositionY);
	}

	protected void serializeStartPositionZ(final WriterBase writerBase) {
		writerBase.writeLong("startPositionZ", startPositionZ);
	}

	protected void serializeStartStationName(final WriterBase writerBase) {
		writerBase.writeString("startStationName", startStationName);
	}

	protected void serializeStartClientId(final WriterBase writerBase) {
		writerBase.writeString("startClientId", startClientId);
	}

	protected void serializeEndPositionX(final WriterBase writerBase) {
		writerBase.writeLong("endPositionX", endPositionX);
	}

	protected void serializeEndPositionY(final WriterBase writerBase) {
		writerBase.writeLong("endPositionY", endPositionY);
	}

	protected void serializeEndPositionZ(final WriterBase writerBase) {
		writerBase.writeLong("endPositionZ", endPositionZ);
	}

	protected void serializeEndStationName(final WriterBase writerBase) {
		writerBase.writeString("endStationName", endStationName);
	}

	protected void serializeEndClientId(final WriterBase writerBase) {
		writerBase.writeString("endClientId", endClientId);
	}
}