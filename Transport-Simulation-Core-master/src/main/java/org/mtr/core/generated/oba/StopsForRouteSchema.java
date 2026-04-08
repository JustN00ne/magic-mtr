package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class StopsForRouteSchema implements SerializedDataBase {

	protected final String routeId;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> stopIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<StopGrouping> stopGroupings = new it.unimi.dsi.fastutil.objects.ObjectArrayList<StopGrouping>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> polylines = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected StopsForRouteSchema(final String routeId) {
		this.routeId = routeId;
	}

	protected StopsForRouteSchema(final ReaderBase readerBase) {
		routeId = readerBase.getString("routeId", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateStringArray("stopIds", stopIds::clear, stopIds::add);
		readerBase.iterateReaderArray("stopGroupings", stopGroupings::clear, readerBaseChild -> stopGroupings.add(new StopGrouping(readerBaseChild)));
		readerBase.iterateStringArray("polylines", polylines::clear, polylines::add);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("routeId", routeId);
		serializeStopIds(writerBase);
		serializeStopGroupings(writerBase);
		serializePolylines(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "routeId: " + routeId + "\n"
			+ "stopIds: " + stopIds + "\n"
			+ "stopGroupings: " + stopGroupings + "\n"
			+ "polylines: " + polylines + "\n"
		;
	}

	protected void serializeStopIds(final WriterBase writerBase) {
		final WriterBase.Array stopIdsWriterBaseArray = writerBase.writeArray("stopIds"); stopIds.forEach(stopIdsWriterBaseArray::writeString);
	}

	protected void serializeStopGroupings(final WriterBase writerBase) {
		writerBase.writeDataset(stopGroupings, "stopGroupings");
	}

	protected void serializePolylines(final WriterBase writerBase) {
		final WriterBase.Array polylinesWriterBaseArray = writerBase.writeArray("polylines"); polylines.forEach(polylinesWriterBaseArray::writeString);
	}
}