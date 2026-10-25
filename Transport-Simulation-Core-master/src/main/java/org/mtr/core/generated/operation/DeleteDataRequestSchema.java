package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class DeleteDataRequestSchema implements SerializedDataBase {

	protected final it.unimi.dsi.fastutil.longs.LongArrayList stationIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList platformIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList sidingIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList routeIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList depotIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Position> liftFloorPositions = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Position>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> railIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Position> railNodePositions = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Position>();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList homeIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList landmarkIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected DeleteDataRequestSchema() {
	}

	protected DeleteDataRequestSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateLongArray("stationIds", stationIds::clear, stationIds::add);
		readerBase.iterateLongArray("platformIds", platformIds::clear, platformIds::add);
		readerBase.iterateLongArray("sidingIds", sidingIds::clear, sidingIds::add);
		readerBase.iterateLongArray("routeIds", routeIds::clear, routeIds::add);
		readerBase.iterateLongArray("depotIds", depotIds::clear, depotIds::add);
		readerBase.iterateReaderArray("liftFloorPositions", liftFloorPositions::clear, readerBaseChild -> liftFloorPositions.add(new Position(readerBaseChild)));
		readerBase.iterateStringArray("railIds", railIds::clear, railIds::add);
		readerBase.iterateReaderArray("railNodePositions", railNodePositions::clear, readerBaseChild -> railNodePositions.add(new Position(readerBaseChild)));
		readerBase.iterateLongArray("homeIds", homeIds::clear, homeIds::add);
		readerBase.iterateLongArray("landmarkIds", landmarkIds::clear, landmarkIds::add);
	}

	public void serializeData(final WriterBase writerBase) {
		serializeStationIds(writerBase);
		serializePlatformIds(writerBase);
		serializeSidingIds(writerBase);
		serializeRouteIds(writerBase);
		serializeDepotIds(writerBase);
		serializeLiftFloorPositions(writerBase);
		serializeRailIds(writerBase);
		serializeRailNodePositions(writerBase);
		serializeHomeIds(writerBase);
		serializeLandmarkIds(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "stationIds: " + stationIds + "\n"
			+ "platformIds: " + platformIds + "\n"
			+ "sidingIds: " + sidingIds + "\n"
			+ "routeIds: " + routeIds + "\n"
			+ "depotIds: " + depotIds + "\n"
			+ "liftFloorPositions: " + liftFloorPositions + "\n"
			+ "railIds: " + railIds + "\n"
			+ "railNodePositions: " + railNodePositions + "\n"
			+ "homeIds: " + homeIds + "\n"
			+ "landmarkIds: " + landmarkIds + "\n"
		;
	}

	protected void serializeStationIds(final WriterBase writerBase) {
		final WriterBase.Array stationIdsWriterBaseArray = writerBase.writeArray("stationIds"); stationIds.forEach(stationIdsWriterBaseArray::writeLong);
	}

	protected void serializePlatformIds(final WriterBase writerBase) {
		final WriterBase.Array platformIdsWriterBaseArray = writerBase.writeArray("platformIds"); platformIds.forEach(platformIdsWriterBaseArray::writeLong);
	}

	protected void serializeSidingIds(final WriterBase writerBase) {
		final WriterBase.Array sidingIdsWriterBaseArray = writerBase.writeArray("sidingIds"); sidingIds.forEach(sidingIdsWriterBaseArray::writeLong);
	}

	protected void serializeRouteIds(final WriterBase writerBase) {
		final WriterBase.Array routeIdsWriterBaseArray = writerBase.writeArray("routeIds"); routeIds.forEach(routeIdsWriterBaseArray::writeLong);
	}

	protected void serializeDepotIds(final WriterBase writerBase) {
		final WriterBase.Array depotIdsWriterBaseArray = writerBase.writeArray("depotIds"); depotIds.forEach(depotIdsWriterBaseArray::writeLong);
	}

	protected void serializeLiftFloorPositions(final WriterBase writerBase) {
		writerBase.writeDataset(liftFloorPositions, "liftFloorPositions");
	}

	protected void serializeRailIds(final WriterBase writerBase) {
		final WriterBase.Array railIdsWriterBaseArray = writerBase.writeArray("railIds"); railIds.forEach(railIdsWriterBaseArray::writeString);
	}

	protected void serializeRailNodePositions(final WriterBase writerBase) {
		writerBase.writeDataset(railNodePositions, "railNodePositions");
	}

	protected void serializeHomeIds(final WriterBase writerBase) {
		final WriterBase.Array homeIdsWriterBaseArray = writerBase.writeArray("homeIds"); homeIds.forEach(homeIdsWriterBaseArray::writeLong);
	}

	protected void serializeLandmarkIds(final WriterBase writerBase) {
		final WriterBase.Array landmarkIdsWriterBaseArray = writerBase.writeArray("landmarkIds"); landmarkIds.forEach(landmarkIdsWriterBaseArray::writeLong);
	}
}