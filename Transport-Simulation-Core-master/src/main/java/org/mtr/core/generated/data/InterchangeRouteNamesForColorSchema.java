package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class InterchangeRouteNamesForColorSchema implements SerializedDataBase {

	protected final long color;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> routeNames = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected InterchangeRouteNamesForColorSchema(final long color) {
		this.color = color;
	}

	protected InterchangeRouteNamesForColorSchema(final ReaderBase readerBase) {
		color = readerBase.getLong("color", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateStringArray("routeNames", routeNames::clear, routeNames::add);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("color", color);
		serializeRouteNames(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "color: " + color + "\n"
			+ "routeNames: " + routeNames + "\n"
		;
	}

	protected void serializeRouteNames(final WriterBase writerBase) {
		final WriterBase.Array routeNamesWriterBaseArray = writerBase.writeArray("routeNames"); routeNames.forEach(routeNamesWriterBaseArray::writeString);
	}
}