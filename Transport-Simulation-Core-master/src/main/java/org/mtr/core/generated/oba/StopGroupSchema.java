package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class StopGroupSchema implements SerializedDataBase {

	protected final long id;

	protected final Name name;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> stopIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> polylines = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected StopGroupSchema(final long id, final Name name) {
		this.id = id;
		this.name = name;
	}

	protected StopGroupSchema(final ReaderBase readerBase) {
		id = readerBase.getLong("id", 0);
		name = new Name(readerBase.getChild("name"));
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateStringArray("stopIds", stopIds::clear, stopIds::add);
		readerBase.iterateStringArray("polylines", polylines::clear, polylines::add);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("id", id);
		if (name != null) name.serializeData(writerBase.writeChild("name"));
		serializeStopIds(writerBase);
		serializePolylines(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "id: " + id + "\n"
			+ "name: " + name + "\n"
			+ "stopIds: " + stopIds + "\n"
			+ "polylines: " + polylines + "\n"
		;
	}

	protected void serializeStopIds(final WriterBase writerBase) {
		final WriterBase.Array stopIdsWriterBaseArray = writerBase.writeArray("stopIds"); stopIds.forEach(stopIdsWriterBaseArray::writeString);
	}

	protected void serializePolylines(final WriterBase writerBase) {
		final WriterBase.Array polylinesWriterBaseArray = writerBase.writeArray("polylines"); polylines.forEach(polylinesWriterBaseArray::writeString);
	}
}