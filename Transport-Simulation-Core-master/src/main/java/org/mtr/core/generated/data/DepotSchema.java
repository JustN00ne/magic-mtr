package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class DepotSchema extends AreaBase<Depot, Siding> {

	protected final it.unimi.dsi.fastutil.longs.LongArrayList routeIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected long lastGeneratedMillis;

	protected Depot.GeneratedStatus lastGeneratedStatus = Depot.GeneratedStatus.values()[0];

	protected long lastGeneratedFailedStartId;

	protected long lastGeneratedFailedEndId;

	protected long lastGeneratedFailedSidingCount;

	protected boolean useRealTime;

	protected final it.unimi.dsi.fastutil.longs.LongArrayList frequencies = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList realTimeDepartures = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected boolean repeatInfinitely;

	protected long cruisingAltitude = 256;

	protected DepotSchema(final TransportMode transportMode, final Data data) {
		super(transportMode, data);
	}

	protected DepotSchema(final ReaderBase readerBase, final Data data) {
		super(readerBase, data);
	}

	public void updateData(final ReaderBase readerBase) {
		super.updateData(readerBase);
		readerBase.iterateLongArray("routeIds", routeIds::clear, routeIds::add);
		readerBase.unpackLong("lastGeneratedMillis", value -> lastGeneratedMillis = value);
		readerBase.unpackString("lastGeneratedStatus", value -> lastGeneratedStatus = EnumHelper.valueOf(Depot.GeneratedStatus.values()[0], value));
		readerBase.unpackLong("lastGeneratedFailedStartId", value -> lastGeneratedFailedStartId = value);
		readerBase.unpackLong("lastGeneratedFailedEndId", value -> lastGeneratedFailedEndId = value);
		readerBase.unpackLong("lastGeneratedFailedSidingCount", value -> lastGeneratedFailedSidingCount = value);
		readerBase.unpackBoolean("useRealTime", value -> useRealTime = value);
		readerBase.iterateLongArray("frequencies", frequencies::clear, frequencies::add);
		readerBase.iterateLongArray("realTimeDepartures", realTimeDepartures::clear, realTimeDepartures::add);
		readerBase.unpackBoolean("repeatInfinitely", value -> repeatInfinitely = value);
		readerBase.unpackLong("cruisingAltitude", value -> cruisingAltitude = value);
	}

	public void serializeData(final WriterBase writerBase) {
		super.serializeData(writerBase);
		serializeRouteIds(writerBase);
		serializeLastGeneratedMillis(writerBase);
		serializeLastGeneratedStatus(writerBase);
		serializeLastGeneratedFailedStartId(writerBase);
		serializeLastGeneratedFailedEndId(writerBase);
		serializeLastGeneratedFailedSidingCount(writerBase);
		serializeUseRealTime(writerBase);
		serializeFrequencies(writerBase);
		serializeRealTimeDepartures(writerBase);
		serializeRepeatInfinitely(writerBase);
		serializeCruisingAltitude(writerBase);
	}

	@Nonnull
	public String toString() {
		return super.toString()
			+ "routeIds: " + routeIds + "\n"
			+ "lastGeneratedMillis: " + lastGeneratedMillis + "\n"
			+ "lastGeneratedStatus: " + lastGeneratedStatus + "\n"
			+ "lastGeneratedFailedStartId: " + lastGeneratedFailedStartId + "\n"
			+ "lastGeneratedFailedEndId: " + lastGeneratedFailedEndId + "\n"
			+ "lastGeneratedFailedSidingCount: " + lastGeneratedFailedSidingCount + "\n"
			+ "useRealTime: " + useRealTime + "\n"
			+ "frequencies: " + frequencies + "\n"
			+ "realTimeDepartures: " + realTimeDepartures + "\n"
			+ "repeatInfinitely: " + repeatInfinitely + "\n"
			+ "cruisingAltitude: " + cruisingAltitude + "\n"
		;
	}

	protected void serializeRouteIds(final WriterBase writerBase) {
		final WriterBase.Array routeIdsWriterBaseArray = writerBase.writeArray("routeIds"); routeIds.forEach(routeIdsWriterBaseArray::writeLong);
	}

	protected void serializeLastGeneratedMillis(final WriterBase writerBase) {
		writerBase.writeLong("lastGeneratedMillis", lastGeneratedMillis);
	}

	protected void serializeLastGeneratedStatus(final WriterBase writerBase) {
		writerBase.writeString("lastGeneratedStatus", lastGeneratedStatus.toString());
	}

	protected void serializeLastGeneratedFailedStartId(final WriterBase writerBase) {
		writerBase.writeLong("lastGeneratedFailedStartId", lastGeneratedFailedStartId);
	}

	protected void serializeLastGeneratedFailedEndId(final WriterBase writerBase) {
		writerBase.writeLong("lastGeneratedFailedEndId", lastGeneratedFailedEndId);
	}

	protected void serializeLastGeneratedFailedSidingCount(final WriterBase writerBase) {
		writerBase.writeLong("lastGeneratedFailedSidingCount", lastGeneratedFailedSidingCount);
	}

	protected void serializeUseRealTime(final WriterBase writerBase) {
		writerBase.writeBoolean("useRealTime", useRealTime);
	}

	protected void serializeFrequencies(final WriterBase writerBase) {
		final WriterBase.Array frequenciesWriterBaseArray = writerBase.writeArray("frequencies"); frequencies.forEach(frequenciesWriterBaseArray::writeLong);
	}

	protected void serializeRealTimeDepartures(final WriterBase writerBase) {
		final WriterBase.Array realTimeDeparturesWriterBaseArray = writerBase.writeArray("realTimeDepartures"); realTimeDepartures.forEach(realTimeDeparturesWriterBaseArray::writeLong);
	}

	protected void serializeRepeatInfinitely(final WriterBase writerBase) {
		writerBase.writeBoolean("repeatInfinitely", repeatInfinitely);
	}

	protected void serializeCruisingAltitude(final WriterBase writerBase) {
		writerBase.writeLong("cruisingAltitude", cruisingAltitude);
	}
}