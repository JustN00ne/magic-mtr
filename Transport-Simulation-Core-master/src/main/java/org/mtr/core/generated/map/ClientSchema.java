package org.mtr.core.generated.map;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.map.*;

public abstract class ClientSchema implements SerializedDataBase {

	protected final String id;

	protected final String name;

	protected final long x;

	protected final long z;

	protected final String stationId;

	protected final String routeId;

	protected final String routeStationId1;

	protected final String routeStationId2;

	protected ClientSchema(final String id, final String name, final long x, final long z, final String stationId, final String routeId, final String routeStationId1, final String routeStationId2) {
		this.id = id;
		this.name = name;
		this.x = x;
		this.z = z;
		this.stationId = stationId;
		this.routeId = routeId;
		this.routeStationId1 = routeStationId1;
		this.routeStationId2 = routeStationId2;
	}

	protected ClientSchema(final ReaderBase readerBase) {
		id = readerBase.getString("id", "");
		name = readerBase.getString("name", "");
		x = readerBase.getLong("x", 0);
		z = readerBase.getLong("z", 0);
		stationId = readerBase.getString("stationId", "");
		routeId = readerBase.getString("routeId", "");
		routeStationId1 = readerBase.getString("routeStationId1", "");
		routeStationId2 = readerBase.getString("routeStationId2", "");
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("id", id);
		writerBase.writeString("name", name);
		writerBase.writeLong("x", x);
		writerBase.writeLong("z", z);
		writerBase.writeString("stationId", stationId);
		writerBase.writeString("routeId", routeId);
		writerBase.writeString("routeStationId1", routeStationId1);
		writerBase.writeString("routeStationId2", routeStationId2);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "id: " + id + "\n"
			+ "name: " + name + "\n"
			+ "x: " + x + "\n"
			+ "z: " + z + "\n"
			+ "stationId: " + stationId + "\n"
			+ "routeId: " + routeId + "\n"
			+ "routeStationId1: " + routeStationId1 + "\n"
			+ "routeStationId2: " + routeStationId2 + "\n"
		;
	}
}