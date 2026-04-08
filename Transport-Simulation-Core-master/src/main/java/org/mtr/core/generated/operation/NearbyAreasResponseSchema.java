package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class NearbyAreasResponseSchema implements SerializedDataBase {

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Depot> depots = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Depot>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Station> stations = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Station>();

	protected NearbyAreasResponseSchema() {
	}

	protected NearbyAreasResponseSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("depots", depots::clear, readerBaseChild -> depots.add(new Depot(readerBaseChild, depotsDataParameter())));
		readerBase.iterateReaderArray("stations", stations::clear, readerBaseChild -> stations.add(new Station(readerBaseChild, stationsDataParameter())));
	}

	public void serializeData(final WriterBase writerBase) {
		serializeDepots(writerBase);
		serializeStations(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "depots: " + depots + "\n"
			+ "stations: " + stations + "\n"
		;
	}

	protected void serializeDepots(final WriterBase writerBase) {
		writerBase.writeDataset(depots, "depots");
	}

	@Nonnull
	protected abstract Data depotsDataParameter();

	protected void serializeStations(final WriterBase writerBase) {
		writerBase.writeDataset(stations, "stations");
	}

	@Nonnull
	protected abstract Data stationsDataParameter();
}