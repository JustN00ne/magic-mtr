package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class NameColorDataBaseSchema implements SerializedDataBase {

	protected final long id;

	protected final TransportMode transportMode;

	protected String name = "";

	protected long color;

	protected final Data data;

	protected NameColorDataBaseSchema(final TransportMode transportMode, final Data data) {
		this.id = new java.util.Random().nextLong();
		this.transportMode = transportMode;
		this.data = data;
	}

	protected NameColorDataBaseSchema(final ReaderBase readerBase, final Data data) {
		id = readerBase.getLong("id", 0);
		transportMode = EnumHelper.valueOf(TransportMode.values()[0], readerBase.getString("transportMode", ""));
		this.data = data;
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackString("name", value -> name = value);
		readerBase.unpackLong("color", value -> color = value);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("id", id);
		writerBase.writeString("transportMode", transportMode.toString());
		serializeName(writerBase);
		serializeColor(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "id: " + id + "\n"
			+ "transportMode: " + transportMode + "\n"
			+ "name: " + name + "\n"
			+ "color: " + color + "\n"
		;
	}

	protected void serializeName(final WriterBase writerBase) {
		writerBase.writeString("name", name);
	}

	protected void serializeColor(final WriterBase writerBase) {
		writerBase.writeLong("color", color);
	}
}