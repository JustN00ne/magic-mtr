package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class StopWithDistanceSchema implements SerializedDataBase {

	protected final String stopId;

	protected final double distanceFromQuery;

	protected StopWithDistanceSchema(final String stopId, final double distanceFromQuery) {
		this.stopId = stopId;
		this.distanceFromQuery = distanceFromQuery;
	}

	protected StopWithDistanceSchema(final ReaderBase readerBase) {
		stopId = readerBase.getString("stopId", "");
		distanceFromQuery = readerBase.getDouble("distanceFromQuery", 0);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("stopId", stopId);
		writerBase.writeDouble("distanceFromQuery", distanceFromQuery);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "stopId: " + stopId + "\n"
			+ "distanceFromQuery: " + distanceFromQuery + "\n"
		;
	}
}