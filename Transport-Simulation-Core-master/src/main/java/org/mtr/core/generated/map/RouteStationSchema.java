package org.mtr.core.generated.map;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.map.*;

public abstract class RouteStationSchema implements SerializedDataBase {

	protected final String id;

	protected final long x;

	protected final long y;

	protected final long z;

	protected final String name;

	protected final long dwellTime;

	protected RouteStationSchema(final String id, final long x, final long y, final long z, final String name, final long dwellTime) {
		this.id = id;
		this.x = x;
		this.y = y;
		this.z = z;
		this.name = name;
		this.dwellTime = dwellTime;
	}

	protected RouteStationSchema(final ReaderBase readerBase) {
		id = readerBase.getString("id", "");
		x = readerBase.getLong("x", 0);
		y = readerBase.getLong("y", 0);
		z = readerBase.getLong("z", 0);
		name = readerBase.getString("name", "");
		dwellTime = readerBase.getLong("dwellTime", 0);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("id", id);
		writerBase.writeLong("x", x);
		writerBase.writeLong("y", y);
		writerBase.writeLong("z", z);
		writerBase.writeString("name", name);
		writerBase.writeLong("dwellTime", dwellTime);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "id: " + id + "\n"
			+ "x: " + x + "\n"
			+ "y: " + y + "\n"
			+ "z: " + z + "\n"
			+ "name: " + name + "\n"
			+ "dwellTime: " + dwellTime + "\n"
		;
	}
}