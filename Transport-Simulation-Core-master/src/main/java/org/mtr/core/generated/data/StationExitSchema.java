package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class StationExitSchema implements SerializedDataBase {

	protected String name = "";

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> destinations = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected StationExitSchema() {
	}

	protected StationExitSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackString("name", value -> name = value);
		readerBase.iterateStringArray("destinations", destinations::clear, destinations::add);
	}

	public void serializeData(final WriterBase writerBase) {
		serializeName(writerBase);
		serializeDestinations(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "name: " + name + "\n"
			+ "destinations: " + destinations + "\n"
		;
	}

	protected void serializeName(final WriterBase writerBase) {
		writerBase.writeString("name", name);
	}

	protected void serializeDestinations(final WriterBase writerBase) {
		final WriterBase.Array destinationsWriterBaseArray = writerBase.writeArray("destinations"); destinations.forEach(destinationsWriterBaseArray::writeString);
	}
}