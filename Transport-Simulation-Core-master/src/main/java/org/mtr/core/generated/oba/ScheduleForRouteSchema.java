package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class ScheduleForRouteSchema implements SerializedDataBase {

	protected final String routeId;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> serviceIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected final long scheduleDate;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<StopTripGrouping> stopTripGroupings = new it.unimi.dsi.fastutil.objects.ObjectArrayList<StopTripGrouping>();

	protected ScheduleForRouteSchema(final String routeId, final long scheduleDate) {
		this.routeId = routeId;
		this.scheduleDate = scheduleDate;
	}

	protected ScheduleForRouteSchema(final ReaderBase readerBase) {
		routeId = readerBase.getString("routeId", "");
		scheduleDate = readerBase.getLong("scheduleDate", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateStringArray("serviceIds", serviceIds::clear, serviceIds::add);
		readerBase.iterateReaderArray("stopTripGroupings", stopTripGroupings::clear, readerBaseChild -> stopTripGroupings.add(new StopTripGrouping(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("routeId", routeId);
		serializeServiceIds(writerBase);
		writerBase.writeLong("scheduleDate", scheduleDate);
		serializeStopTripGroupings(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "routeId: " + routeId + "\n"
			+ "serviceIds: " + serviceIds + "\n"
			+ "scheduleDate: " + scheduleDate + "\n"
			+ "stopTripGroupings: " + stopTripGroupings + "\n"
		;
	}

	protected void serializeServiceIds(final WriterBase writerBase) {
		final WriterBase.Array serviceIdsWriterBaseArray = writerBase.writeArray("serviceIds"); serviceIds.forEach(serviceIdsWriterBaseArray::writeString);
	}

	protected void serializeStopTripGroupings(final WriterBase writerBase) {
		writerBase.writeDataset(stopTripGroupings, "stopTripGroupings");
	}
}