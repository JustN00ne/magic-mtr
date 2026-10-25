package org.mtr.core.generated.map;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.map.*;

public abstract class DeparturesSchema implements SerializedDataBase {

	protected final long cachedResponseTime;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<DeparturesByRoute> departures = new it.unimi.dsi.fastutil.objects.ObjectArrayList<DeparturesByRoute>();

	protected DeparturesSchema(final long cachedResponseTime) {
		this.cachedResponseTime = cachedResponseTime;
	}

	protected DeparturesSchema(final ReaderBase readerBase) {
		cachedResponseTime = readerBase.getLong("cachedResponseTime", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("departures", departures::clear, readerBaseChild -> departures.add(new DeparturesByRoute(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("cachedResponseTime", cachedResponseTime);
		serializeDepartures(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "cachedResponseTime: " + cachedResponseTime + "\n"
			+ "departures: " + departures + "\n"
		;
	}

	protected void serializeDepartures(final WriterBase writerBase) {
		writerBase.writeDataset(departures, "departures");
	}
}