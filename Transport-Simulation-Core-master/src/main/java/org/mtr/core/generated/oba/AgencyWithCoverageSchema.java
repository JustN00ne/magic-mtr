package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class AgencyWithCoverageSchema implements SerializedDataBase {

	protected final String agencyId;

	protected final double lat;

	protected final double lon;

	protected final double latSpan;

	protected final double lonSpan;

	protected AgencyWithCoverageSchema(final String agencyId, final double lat, final double lon, final double latSpan, final double lonSpan) {
		this.agencyId = agencyId;
		this.lat = lat;
		this.lon = lon;
		this.latSpan = latSpan;
		this.lonSpan = lonSpan;
	}

	protected AgencyWithCoverageSchema(final ReaderBase readerBase) {
		agencyId = readerBase.getString("agencyId", "");
		lat = readerBase.getDouble("lat", 0);
		lon = readerBase.getDouble("lon", 0);
		latSpan = readerBase.getDouble("latSpan", 0);
		lonSpan = readerBase.getDouble("lonSpan", 0);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("agencyId", agencyId);
		writerBase.writeDouble("lat", lat);
		writerBase.writeDouble("lon", lon);
		writerBase.writeDouble("latSpan", latSpan);
		writerBase.writeDouble("lonSpan", lonSpan);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "agencyId: " + agencyId + "\n"
			+ "lat: " + lat + "\n"
			+ "lon: " + lon + "\n"
			+ "latSpan: " + latSpan + "\n"
			+ "lonSpan: " + lonSpan + "\n"
		;
	}
}