package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class PositionSchema implements SerializedDataBase {

	protected final double lat;

	protected final double lon;

	protected PositionSchema(final double lat, final double lon) {
		this.lat = lat;
		this.lon = lon;
	}

	protected PositionSchema(final ReaderBase readerBase) {
		lat = readerBase.getDouble("lat", 0);
		lon = readerBase.getDouble("lon", 0);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeDouble("lat", lat);
		writerBase.writeDouble("lon", lon);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "lat: " + lat + "\n"
			+ "lon: " + lon + "\n"
		;
	}
}