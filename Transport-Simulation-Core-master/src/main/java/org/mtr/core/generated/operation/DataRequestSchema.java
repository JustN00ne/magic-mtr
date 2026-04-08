package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class DataRequestSchema implements SerializedDataBase {

	protected final String clientId;

	protected final Position clientPosition;

	protected final long requestRadius;

	protected final it.unimi.dsi.fastutil.longs.LongArrayList existingStationIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList existingPlatformIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList existingSidingIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList existingSimplifiedRouteIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList existingDepotIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> existingRailIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList existingHomeIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList existingLandmarkIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected DataRequestSchema(final String clientId, final Position clientPosition, final long requestRadius) {
		this.clientId = clientId;
		this.clientPosition = clientPosition;
		this.requestRadius = requestRadius;
	}

	protected DataRequestSchema(final ReaderBase readerBase) {
		clientId = readerBase.getString("clientId", "");
		clientPosition = new Position(readerBase.getChild("clientPosition"));
		requestRadius = readerBase.getLong("requestRadius", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateLongArray("existingStationIds", existingStationIds::clear, existingStationIds::add);
		readerBase.iterateLongArray("existingPlatformIds", existingPlatformIds::clear, existingPlatformIds::add);
		readerBase.iterateLongArray("existingSidingIds", existingSidingIds::clear, existingSidingIds::add);
		readerBase.iterateLongArray("existingSimplifiedRouteIds", existingSimplifiedRouteIds::clear, existingSimplifiedRouteIds::add);
		readerBase.iterateLongArray("existingDepotIds", existingDepotIds::clear, existingDepotIds::add);
		readerBase.iterateStringArray("existingRailIds", existingRailIds::clear, existingRailIds::add);
		readerBase.iterateLongArray("existingHomeIds", existingHomeIds::clear, existingHomeIds::add);
		readerBase.iterateLongArray("existingLandmarkIds", existingLandmarkIds::clear, existingLandmarkIds::add);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("clientId", clientId);
		if (clientPosition != null) clientPosition.serializeData(writerBase.writeChild("clientPosition"));
		writerBase.writeLong("requestRadius", requestRadius);
		serializeExistingStationIds(writerBase);
		serializeExistingPlatformIds(writerBase);
		serializeExistingSidingIds(writerBase);
		serializeExistingSimplifiedRouteIds(writerBase);
		serializeExistingDepotIds(writerBase);
		serializeExistingRailIds(writerBase);
		serializeExistingHomeIds(writerBase);
		serializeExistingLandmarkIds(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "clientId: " + clientId + "\n"
			+ "clientPosition: " + clientPosition + "\n"
			+ "requestRadius: " + requestRadius + "\n"
			+ "existingStationIds: " + existingStationIds + "\n"
			+ "existingPlatformIds: " + existingPlatformIds + "\n"
			+ "existingSidingIds: " + existingSidingIds + "\n"
			+ "existingSimplifiedRouteIds: " + existingSimplifiedRouteIds + "\n"
			+ "existingDepotIds: " + existingDepotIds + "\n"
			+ "existingRailIds: " + existingRailIds + "\n"
			+ "existingHomeIds: " + existingHomeIds + "\n"
			+ "existingLandmarkIds: " + existingLandmarkIds + "\n"
		;
	}

	protected void serializeExistingStationIds(final WriterBase writerBase) {
		final WriterBase.Array existingStationIdsWriterBaseArray = writerBase.writeArray("existingStationIds"); existingStationIds.forEach(existingStationIdsWriterBaseArray::writeLong);
	}

	protected void serializeExistingPlatformIds(final WriterBase writerBase) {
		final WriterBase.Array existingPlatformIdsWriterBaseArray = writerBase.writeArray("existingPlatformIds"); existingPlatformIds.forEach(existingPlatformIdsWriterBaseArray::writeLong);
	}

	protected void serializeExistingSidingIds(final WriterBase writerBase) {
		final WriterBase.Array existingSidingIdsWriterBaseArray = writerBase.writeArray("existingSidingIds"); existingSidingIds.forEach(existingSidingIdsWriterBaseArray::writeLong);
	}

	protected void serializeExistingSimplifiedRouteIds(final WriterBase writerBase) {
		final WriterBase.Array existingSimplifiedRouteIdsWriterBaseArray = writerBase.writeArray("existingSimplifiedRouteIds"); existingSimplifiedRouteIds.forEach(existingSimplifiedRouteIdsWriterBaseArray::writeLong);
	}

	protected void serializeExistingDepotIds(final WriterBase writerBase) {
		final WriterBase.Array existingDepotIdsWriterBaseArray = writerBase.writeArray("existingDepotIds"); existingDepotIds.forEach(existingDepotIdsWriterBaseArray::writeLong);
	}

	protected void serializeExistingRailIds(final WriterBase writerBase) {
		final WriterBase.Array existingRailIdsWriterBaseArray = writerBase.writeArray("existingRailIds"); existingRailIds.forEach(existingRailIdsWriterBaseArray::writeString);
	}

	protected void serializeExistingHomeIds(final WriterBase writerBase) {
		final WriterBase.Array existingHomeIdsWriterBaseArray = writerBase.writeArray("existingHomeIds"); existingHomeIds.forEach(existingHomeIdsWriterBaseArray::writeLong);
	}

	protected void serializeExistingLandmarkIds(final WriterBase writerBase) {
		final WriterBase.Array existingLandmarkIdsWriterBaseArray = writerBase.writeArray("existingLandmarkIds"); existingLandmarkIds.forEach(existingLandmarkIdsWriterBaseArray::writeLong);
	}
}