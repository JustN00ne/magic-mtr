package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class StopWithArrivalsAndDeparturesSchema implements SerializedDataBase {

	protected final String stopId;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<ArrivalAndDeparture> arrivalsAndDepartures = new it.unimi.dsi.fastutil.objects.ObjectArrayList<ArrivalAndDeparture>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> nearbyStopIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected StopWithArrivalsAndDeparturesSchema(final String stopId) {
		this.stopId = stopId;
	}

	protected StopWithArrivalsAndDeparturesSchema(final ReaderBase readerBase) {
		stopId = readerBase.getString("stopId", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("arrivalsAndDepartures", arrivalsAndDepartures::clear, readerBaseChild -> arrivalsAndDepartures.add(new ArrivalAndDeparture(readerBaseChild)));
		readerBase.iterateStringArray("nearbyStopIds", nearbyStopIds::clear, nearbyStopIds::add);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("stopId", stopId);
		serializeArrivalsAndDepartures(writerBase);
		serializeNearbyStopIds(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "stopId: " + stopId + "\n"
			+ "arrivalsAndDepartures: " + arrivalsAndDepartures + "\n"
			+ "nearbyStopIds: " + nearbyStopIds + "\n"
		;
	}

	protected void serializeArrivalsAndDepartures(final WriterBase writerBase) {
		writerBase.writeDataset(arrivalsAndDepartures, "arrivalsAndDepartures");
	}

	protected void serializeNearbyStopIds(final WriterBase writerBase) {
		final WriterBase.Array nearbyStopIdsWriterBaseArray = writerBase.writeArray("nearbyStopIds"); nearbyStopIds.forEach(nearbyStopIdsWriterBaseArray::writeString);
	}
}