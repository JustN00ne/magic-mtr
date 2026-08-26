package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class StopTripGroupingSchema implements SerializedDataBase {

	protected final long directionId;

	protected final String tripHeadsign;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> stopIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> tripIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<TripWithStopTimes> tripsWithStopTimes = new it.unimi.dsi.fastutil.objects.ObjectArrayList<TripWithStopTimes>();

	protected StopTripGroupingSchema(final long directionId, final String tripHeadsign) {
		this.directionId = directionId;
		this.tripHeadsign = tripHeadsign;
	}

	protected StopTripGroupingSchema(final ReaderBase readerBase) {
		directionId = readerBase.getLong("directionId", 0);
		tripHeadsign = readerBase.getString("tripHeadsign", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateStringArray("stopIds", stopIds::clear, stopIds::add);
		readerBase.iterateStringArray("tripIds", tripIds::clear, tripIds::add);
		readerBase.iterateReaderArray("tripsWithStopTimes", tripsWithStopTimes::clear, readerBaseChild -> tripsWithStopTimes.add(new TripWithStopTimes(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("directionId", directionId);
		writerBase.writeString("tripHeadsign", tripHeadsign);
		serializeStopIds(writerBase);
		serializeTripIds(writerBase);
		serializeTripsWithStopTimes(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "directionId: " + directionId + "\n"
			+ "tripHeadsign: " + tripHeadsign + "\n"
			+ "stopIds: " + stopIds + "\n"
			+ "tripIds: " + tripIds + "\n"
			+ "tripsWithStopTimes: " + tripsWithStopTimes + "\n"
		;
	}

	protected void serializeStopIds(final WriterBase writerBase) {
		final WriterBase.Array stopIdsWriterBaseArray = writerBase.writeArray("stopIds"); stopIds.forEach(stopIdsWriterBaseArray::writeString);
	}

	protected void serializeTripIds(final WriterBase writerBase) {
		final WriterBase.Array tripIdsWriterBaseArray = writerBase.writeArray("tripIds"); tripIds.forEach(tripIdsWriterBaseArray::writeString);
	}

	protected void serializeTripsWithStopTimes(final WriterBase writerBase) {
		writerBase.writeDataset(tripsWithStopTimes, "tripsWithStopTimes");
	}
}