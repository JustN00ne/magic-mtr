package org.mtr.core.generated.map;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.map.*;

public abstract class DeparturesByDeviationSchema implements SerializedDataBase {

	protected final long deviation;

	protected final it.unimi.dsi.fastutil.longs.LongArrayList departures = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected DeparturesByDeviationSchema(final long deviation) {
		this.deviation = deviation;
	}

	protected DeparturesByDeviationSchema(final ReaderBase readerBase) {
		deviation = readerBase.getLong("deviation", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateLongArray("departures", departures::clear, departures::add);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("deviation", deviation);
		serializeDepartures(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "deviation: " + deviation + "\n"
			+ "departures: " + departures + "\n"
		;
	}

	protected void serializeDepartures(final WriterBase writerBase) {
		final WriterBase.Array departuresWriterBaseArray = writerBase.writeArray("departures"); departures.forEach(departuresWriterBaseArray::writeLong);
	}
}