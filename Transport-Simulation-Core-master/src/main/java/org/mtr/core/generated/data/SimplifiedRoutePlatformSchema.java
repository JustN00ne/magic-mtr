package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class SimplifiedRoutePlatformSchema extends InterchangeColorsForStationName {

	protected final long platformId;

	protected final long stationId;

	protected final String destination;

	protected SimplifiedRoutePlatformSchema(final long platformId, final long stationId, final String destination, final String stationName) {
		super(stationName);
		this.platformId = platformId;
		this.stationId = stationId;
		this.destination = destination;
	}

	protected SimplifiedRoutePlatformSchema(final ReaderBase readerBase) {
		super(readerBase);
		platformId = readerBase.getLong("platformId", 0);
		stationId = readerBase.getLong("stationId", 0);
		destination = readerBase.getString("destination", "");
	}

	public void updateData(final ReaderBase readerBase) {
		super.updateData(readerBase);
	}

	public void serializeData(final WriterBase writerBase) {
		super.serializeData(writerBase);
		writerBase.writeLong("platformId", platformId);
		writerBase.writeLong("stationId", stationId);
		writerBase.writeString("destination", destination);
	}

	@Nonnull
	public String toString() {
		return super.toString()
			+ "platformId: " + platformId + "\n"
			+ "stationId: " + stationId + "\n"
			+ "destination: " + destination + "\n"
		;
	}
}