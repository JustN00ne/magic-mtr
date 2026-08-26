package org.mtr.core.generated.map;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.map.*;

public abstract class StationSchema implements SerializedDataBase {

	protected final String id;

	protected final String name;

	protected final long color;

	protected final long zone1;

	protected final long zone2;

	protected final long zone3;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> connections = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected StationSchema(final String id, final String name, final long color, final long zone1, final long zone2, final long zone3) {
		this.id = id;
		this.name = name;
		this.color = color;
		this.zone1 = zone1;
		this.zone2 = zone2;
		this.zone3 = zone3;
	}

	protected StationSchema(final ReaderBase readerBase) {
		id = readerBase.getString("id", "");
		name = readerBase.getString("name", "");
		color = readerBase.getLong("color", 0);
		zone1 = readerBase.getLong("zone1", 0);
		zone2 = readerBase.getLong("zone2", 0);
		zone3 = readerBase.getLong("zone3", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateStringArray("connections", connections::clear, connections::add);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("id", id);
		writerBase.writeString("name", name);
		writerBase.writeLong("color", color);
		writerBase.writeLong("zone1", zone1);
		writerBase.writeLong("zone2", zone2);
		writerBase.writeLong("zone3", zone3);
		serializeConnections(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "id: " + id + "\n"
			+ "name: " + name + "\n"
			+ "color: " + color + "\n"
			+ "zone1: " + zone1 + "\n"
			+ "zone2: " + zone2 + "\n"
			+ "zone3: " + zone3 + "\n"
			+ "connections: " + connections + "\n"
		;
	}

	protected void serializeConnections(final WriterBase writerBase) {
		final WriterBase.Array connectionsWriterBaseArray = writerBase.writeArray("connections"); connections.forEach(connectionsWriterBaseArray::writeString);
	}
}