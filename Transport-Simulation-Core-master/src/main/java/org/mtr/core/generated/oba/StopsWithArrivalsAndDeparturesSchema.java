package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class StopsWithArrivalsAndDeparturesSchema implements SerializedDataBase {

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> stopIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<ArrivalAndDeparture> arrivalsAndDepartures = new it.unimi.dsi.fastutil.objects.ObjectArrayList<ArrivalAndDeparture>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<StopWithDistance> nearbyStopIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<StopWithDistance>();

	protected boolean limitedExceeded;

	protected StopsWithArrivalsAndDeparturesSchema() {
	}

	protected StopsWithArrivalsAndDeparturesSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateStringArray("stopIds", stopIds::clear, stopIds::add);
		readerBase.iterateReaderArray("arrivalsAndDepartures", arrivalsAndDepartures::clear, readerBaseChild -> arrivalsAndDepartures.add(new ArrivalAndDeparture(readerBaseChild)));
		readerBase.iterateReaderArray("nearbyStopIds", nearbyStopIds::clear, readerBaseChild -> nearbyStopIds.add(new StopWithDistance(readerBaseChild)));
		readerBase.unpackBoolean("limitedExceeded", value -> limitedExceeded = value);
	}

	public void serializeData(final WriterBase writerBase) {
		serializeStopIds(writerBase);
		serializeArrivalsAndDepartures(writerBase);
		serializeNearbyStopIds(writerBase);
		serializeLimitedExceeded(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "stopIds: " + stopIds + "\n"
			+ "arrivalsAndDepartures: " + arrivalsAndDepartures + "\n"
			+ "nearbyStopIds: " + nearbyStopIds + "\n"
			+ "limitedExceeded: " + limitedExceeded + "\n"
		;
	}

	protected void serializeStopIds(final WriterBase writerBase) {
		final WriterBase.Array stopIdsWriterBaseArray = writerBase.writeArray("stopIds"); stopIds.forEach(stopIdsWriterBaseArray::writeString);
	}

	protected void serializeArrivalsAndDepartures(final WriterBase writerBase) {
		writerBase.writeDataset(arrivalsAndDepartures, "arrivalsAndDepartures");
	}

	protected void serializeNearbyStopIds(final WriterBase writerBase) {
		writerBase.writeDataset(nearbyStopIds, "nearbyStopIds");
	}

	protected void serializeLimitedExceeded(final WriterBase writerBase) {
		writerBase.writeBoolean("limitedExceeded", limitedExceeded);
	}
}