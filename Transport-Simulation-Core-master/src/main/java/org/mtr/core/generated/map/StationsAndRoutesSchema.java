package org.mtr.core.generated.map;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.map.*;

public abstract class StationsAndRoutesSchema implements SerializedDataBase {

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Station> stations = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Station>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Route> routes = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Route>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> dimensions = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected StationsAndRoutesSchema() {
	}

	protected StationsAndRoutesSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("stations", stations::clear, readerBaseChild -> stations.add(new Station(readerBaseChild)));
		readerBase.iterateReaderArray("routes", routes::clear, readerBaseChild -> routes.add(new Route(readerBaseChild)));
		readerBase.iterateStringArray("dimensions", dimensions::clear, dimensions::add);
	}

	public void serializeData(final WriterBase writerBase) {
		serializeStations(writerBase);
		serializeRoutes(writerBase);
		serializeDimensions(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "stations: " + stations + "\n"
			+ "routes: " + routes + "\n"
			+ "dimensions: " + dimensions + "\n"
		;
	}

	protected void serializeStations(final WriterBase writerBase) {
		writerBase.writeDataset(stations, "stations");
	}

	protected void serializeRoutes(final WriterBase writerBase) {
		writerBase.writeDataset(routes, "routes");
	}

	protected void serializeDimensions(final WriterBase writerBase) {
		final WriterBase.Array dimensionsWriterBaseArray = writerBase.writeArray("dimensions"); dimensions.forEach(dimensionsWriterBaseArray::writeString);
	}
}