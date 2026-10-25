package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class ArrivalsRequestSchema implements SerializedDataBase {

	protected final it.unimi.dsi.fastutil.longs.LongArrayList platformIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> platformIdsHex = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList stationIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> stationIdsHex = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected final long maxCountPerPlatform;

	protected final long maxCountTotal;

	protected ArrivalsRequestSchema(final long maxCountPerPlatform, final long maxCountTotal) {
		this.maxCountPerPlatform = maxCountPerPlatform;
		this.maxCountTotal = maxCountTotal;
	}

	protected ArrivalsRequestSchema(final ReaderBase readerBase) {
		maxCountPerPlatform = readerBase.getLong("maxCountPerPlatform", 0);
		maxCountTotal = readerBase.getLong("maxCountTotal", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateLongArray("platformIds", platformIds::clear, platformIds::add);
		readerBase.iterateStringArray("platformIdsHex", platformIdsHex::clear, platformIdsHex::add);
		readerBase.iterateLongArray("stationIds", stationIds::clear, stationIds::add);
		readerBase.iterateStringArray("stationIdsHex", stationIdsHex::clear, stationIdsHex::add);
	}

	public void serializeData(final WriterBase writerBase) {
		serializePlatformIds(writerBase);
		serializePlatformIdsHex(writerBase);
		serializeStationIds(writerBase);
		serializeStationIdsHex(writerBase);
		writerBase.writeLong("maxCountPerPlatform", maxCountPerPlatform);
		writerBase.writeLong("maxCountTotal", maxCountTotal);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "platformIds: " + platformIds + "\n"
			+ "platformIdsHex: " + platformIdsHex + "\n"
			+ "stationIds: " + stationIds + "\n"
			+ "stationIdsHex: " + stationIdsHex + "\n"
			+ "maxCountPerPlatform: " + maxCountPerPlatform + "\n"
			+ "maxCountTotal: " + maxCountTotal + "\n"
		;
	}

	protected void serializePlatformIds(final WriterBase writerBase) {
		final WriterBase.Array platformIdsWriterBaseArray = writerBase.writeArray("platformIds"); platformIds.forEach(platformIdsWriterBaseArray::writeLong);
	}

	protected void serializePlatformIdsHex(final WriterBase writerBase) {
		final WriterBase.Array platformIdsHexWriterBaseArray = writerBase.writeArray("platformIdsHex"); platformIdsHex.forEach(platformIdsHexWriterBaseArray::writeString);
	}

	protected void serializeStationIds(final WriterBase writerBase) {
		final WriterBase.Array stationIdsWriterBaseArray = writerBase.writeArray("stationIds"); stationIds.forEach(stationIdsWriterBaseArray::writeLong);
	}

	protected void serializeStationIdsHex(final WriterBase writerBase) {
		final WriterBase.Array stationIdsHexWriterBaseArray = writerBase.writeArray("stationIdsHex"); stationIdsHex.forEach(stationIdsHexWriterBaseArray::writeString);
	}
}