package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class PassengerSchema extends NameColorDataBase {

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<PassengerDirection> directions = new it.unimi.dsi.fastutil.objects.ObjectArrayList<PassengerDirection>();

	protected long startLandmarkId;

	protected long endLandmarkId;

	protected long landmarkVisitStartTime;

	protected long landmarkVisitEndTime;

	protected long sidingId;

	protected long vehicleId;

	protected PassengerSchema(final TransportMode transportMode, final Data data) {
		super(transportMode, data);
	}

	protected PassengerSchema(final ReaderBase readerBase, final Data data) {
		super(readerBase, data);
	}

	public void updateData(final ReaderBase readerBase) {
		super.updateData(readerBase);
		readerBase.iterateReaderArray("directions", directions::clear, readerBaseChild -> directions.add(new PassengerDirection(readerBaseChild)));
		readerBase.unpackLong("startLandmarkId", value -> startLandmarkId = value);
		readerBase.unpackLong("endLandmarkId", value -> endLandmarkId = value);
		readerBase.unpackLong("landmarkVisitStartTime", value -> landmarkVisitStartTime = value);
		readerBase.unpackLong("landmarkVisitEndTime", value -> landmarkVisitEndTime = value);
		readerBase.unpackLong("sidingId", value -> sidingId = value);
		readerBase.unpackLong("vehicleId", value -> vehicleId = value);
	}

	public void serializeData(final WriterBase writerBase) {
		super.serializeData(writerBase);
		serializeDirections(writerBase);
		serializeStartLandmarkId(writerBase);
		serializeEndLandmarkId(writerBase);
		serializeLandmarkVisitStartTime(writerBase);
		serializeLandmarkVisitEndTime(writerBase);
		serializeSidingId(writerBase);
		serializeVehicleId(writerBase);
	}

	@Nonnull
	public String toString() {
		return super.toString()
			+ "directions: " + directions + "\n"
			+ "startLandmarkId: " + startLandmarkId + "\n"
			+ "endLandmarkId: " + endLandmarkId + "\n"
			+ "landmarkVisitStartTime: " + landmarkVisitStartTime + "\n"
			+ "landmarkVisitEndTime: " + landmarkVisitEndTime + "\n"
			+ "sidingId: " + sidingId + "\n"
			+ "vehicleId: " + vehicleId + "\n"
		;
	}

	protected void serializeDirections(final WriterBase writerBase) {
		writerBase.writeDataset(directions, "directions");
	}

	protected void serializeStartLandmarkId(final WriterBase writerBase) {
		writerBase.writeLong("startLandmarkId", startLandmarkId);
	}

	protected void serializeEndLandmarkId(final WriterBase writerBase) {
		writerBase.writeLong("endLandmarkId", endLandmarkId);
	}

	protected void serializeLandmarkVisitStartTime(final WriterBase writerBase) {
		writerBase.writeLong("landmarkVisitStartTime", landmarkVisitStartTime);
	}

	protected void serializeLandmarkVisitEndTime(final WriterBase writerBase) {
		writerBase.writeLong("landmarkVisitEndTime", landmarkVisitEndTime);
	}

	protected void serializeSidingId(final WriterBase writerBase) {
		writerBase.writeLong("sidingId", sidingId);
	}

	protected void serializeVehicleId(final WriterBase writerBase) {
		writerBase.writeLong("vehicleId", vehicleId);
	}
}