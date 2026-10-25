package org.mtr.core.generated.map;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.map.*;

public abstract class DeparturesByRouteSchema implements SerializedDataBase {

	protected final String id;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<DeparturesByDeviation> departures = new it.unimi.dsi.fastutil.objects.ObjectArrayList<DeparturesByDeviation>();

	protected DeparturesByRouteSchema(final String id) {
		this.id = id;
	}

	protected DeparturesByRouteSchema(final ReaderBase readerBase) {
		id = readerBase.getString("id", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("departures", departures::clear, readerBaseChild -> departures.add(new DeparturesByDeviation(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("id", id);
		serializeDepartures(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "id: " + id + "\n"
			+ "departures: " + departures + "\n"
		;
	}

	protected void serializeDepartures(final WriterBase writerBase) {
		writerBase.writeDataset(departures, "departures");
	}
}