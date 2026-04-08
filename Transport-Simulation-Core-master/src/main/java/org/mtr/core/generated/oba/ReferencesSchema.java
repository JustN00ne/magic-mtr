package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class ReferencesSchema implements SerializedDataBase {

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Agency> agencies = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Agency>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Route> routes = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Route>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Stop> stops = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Stop>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Trip> trips = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Trip>();

	protected ReferencesSchema() {
	}

	protected ReferencesSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("agencies", agencies::clear, readerBaseChild -> agencies.add(new Agency(readerBaseChild)));
		readerBase.iterateReaderArray("routes", routes::clear, readerBaseChild -> routes.add(new Route(readerBaseChild)));
		readerBase.iterateReaderArray("stops", stops::clear, readerBaseChild -> stops.add(new Stop(readerBaseChild)));
		readerBase.iterateReaderArray("trips", trips::clear, readerBaseChild -> trips.add(new Trip(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		serializeAgencies(writerBase);
		serializeRoutes(writerBase);
		serializeStops(writerBase);
		serializeTrips(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "agencies: " + agencies + "\n"
			+ "routes: " + routes + "\n"
			+ "stops: " + stops + "\n"
			+ "trips: " + trips + "\n"
		;
	}

	protected void serializeAgencies(final WriterBase writerBase) {
		writerBase.writeDataset(agencies, "agencies");
	}

	protected void serializeRoutes(final WriterBase writerBase) {
		writerBase.writeDataset(routes, "routes");
	}

	protected void serializeStops(final WriterBase writerBase) {
		writerBase.writeDataset(stops, "stops");
	}

	protected void serializeTrips(final WriterBase writerBase) {
		writerBase.writeDataset(trips, "trips");
	}
}