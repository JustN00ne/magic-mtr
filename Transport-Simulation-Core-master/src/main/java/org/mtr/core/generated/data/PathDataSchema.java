package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class PathDataSchema implements SerializedDataBase {

	protected final long savedRailBaseId;

	protected final long dwellTime;

	protected final long stopIndex;

	protected final double startDistance;

	protected final double endDistance;

	protected final Position startPosition;

	protected final Angle startAngle;

	protected final Position endPosition;

	protected final Angle endAngle;

	protected long speedLimit;

	protected PathDataSchema(final long savedRailBaseId, final long dwellTime, final long stopIndex, final double startDistance, final double endDistance, final Position startPosition, final Angle startAngle, final Position endPosition, final Angle endAngle) {
		this.savedRailBaseId = savedRailBaseId;
		this.dwellTime = dwellTime;
		this.stopIndex = stopIndex;
		this.startDistance = startDistance;
		this.endDistance = endDistance;
		this.startPosition = startPosition;
		this.startAngle = startAngle;
		this.endPosition = endPosition;
		this.endAngle = endAngle;
	}

	protected PathDataSchema(final ReaderBase readerBase) {
		savedRailBaseId = readerBase.getLong("savedRailBaseId", 0);
		dwellTime = readerBase.getLong("dwellTime", 0);
		stopIndex = readerBase.getLong("stopIndex", 0);
		startDistance = readerBase.getDouble("startDistance", 0);
		endDistance = readerBase.getDouble("endDistance", 0);
		startPosition = new Position(readerBase.getChild("startPosition"));
		startAngle = EnumHelper.valueOf(Angle.values()[0], readerBase.getString("startAngle", ""));
		endPosition = new Position(readerBase.getChild("endPosition"));
		endAngle = EnumHelper.valueOf(Angle.values()[0], readerBase.getString("endAngle", ""));
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackLong("speedLimit", value -> speedLimit = value);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("savedRailBaseId", savedRailBaseId);
		writerBase.writeLong("dwellTime", dwellTime);
		writerBase.writeLong("stopIndex", stopIndex);
		writerBase.writeDouble("startDistance", startDistance);
		writerBase.writeDouble("endDistance", endDistance);
		if (startPosition != null) startPosition.serializeData(writerBase.writeChild("startPosition"));
		writerBase.writeString("startAngle", startAngle.toString());
		if (endPosition != null) endPosition.serializeData(writerBase.writeChild("endPosition"));
		writerBase.writeString("endAngle", endAngle.toString());
		serializeSpeedLimit(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "savedRailBaseId: " + savedRailBaseId + "\n"
			+ "dwellTime: " + dwellTime + "\n"
			+ "stopIndex: " + stopIndex + "\n"
			+ "startDistance: " + startDistance + "\n"
			+ "endDistance: " + endDistance + "\n"
			+ "startPosition: " + startPosition + "\n"
			+ "startAngle: " + startAngle + "\n"
			+ "endPosition: " + endPosition + "\n"
			+ "endAngle: " + endAngle + "\n"
			+ "speedLimit: " + speedLimit + "\n"
		;
	}

	protected void serializeSpeedLimit(final WriterBase writerBase) {
		writerBase.writeLong("speedLimit", speedLimit);
	}
}