package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class TripStatusSchema implements SerializedDataBase {

	protected final String activeTripId;

	protected final long blockTripSequence;

	protected final long serviceDate;

	protected Frequency frequency = getDefaultFrequency();

	protected final double scheduledDistanceAlongTrip;

	protected final double totalDistanceAlongTrip;

	protected final Position position;

	protected final double orientation;

	protected final String closestStop;

	protected final long closestStopTimeOffset;

	protected final String nextStop;

	protected final long nextStopTimeOffset;

	protected final OccupancyStatus occupancyStatus;

	protected final String phase;

	protected final String status;

	protected final boolean predicted;

	protected final long lastUpdateTime;

	protected final long lastLocationUpdateTime;

	protected final Position lastKnownLocation;

	protected final double lastKnownDistanceAlongTrip;

	protected final double lastKnownOrientation;

	protected final double distanceAlongTrip;

	protected final long scheduleDeviation;

	protected final String vehicleId;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> situationIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected TripStatusSchema(final String activeTripId, final long blockTripSequence, final long serviceDate, final double scheduledDistanceAlongTrip, final double totalDistanceAlongTrip, final Position position, final double orientation, final String closestStop, final long closestStopTimeOffset, final String nextStop, final long nextStopTimeOffset, final OccupancyStatus occupancyStatus, final String phase, final String status, final boolean predicted, final long lastUpdateTime, final long lastLocationUpdateTime, final Position lastKnownLocation, final double lastKnownDistanceAlongTrip, final double lastKnownOrientation, final double distanceAlongTrip, final long scheduleDeviation, final String vehicleId) {
		this.activeTripId = activeTripId;
		this.blockTripSequence = blockTripSequence;
		this.serviceDate = serviceDate;
		this.scheduledDistanceAlongTrip = scheduledDistanceAlongTrip;
		this.totalDistanceAlongTrip = totalDistanceAlongTrip;
		this.position = position;
		this.orientation = orientation;
		this.closestStop = closestStop;
		this.closestStopTimeOffset = closestStopTimeOffset;
		this.nextStop = nextStop;
		this.nextStopTimeOffset = nextStopTimeOffset;
		this.occupancyStatus = occupancyStatus;
		this.phase = phase;
		this.status = status;
		this.predicted = predicted;
		this.lastUpdateTime = lastUpdateTime;
		this.lastLocationUpdateTime = lastLocationUpdateTime;
		this.lastKnownLocation = lastKnownLocation;
		this.lastKnownDistanceAlongTrip = lastKnownDistanceAlongTrip;
		this.lastKnownOrientation = lastKnownOrientation;
		this.distanceAlongTrip = distanceAlongTrip;
		this.scheduleDeviation = scheduleDeviation;
		this.vehicleId = vehicleId;
	}

	protected TripStatusSchema(final ReaderBase readerBase) {
		activeTripId = readerBase.getString("activeTripId", "");
		blockTripSequence = readerBase.getLong("blockTripSequence", 0);
		serviceDate = readerBase.getLong("serviceDate", 0);
		scheduledDistanceAlongTrip = readerBase.getDouble("scheduledDistanceAlongTrip", 0);
		totalDistanceAlongTrip = readerBase.getDouble("totalDistanceAlongTrip", 0);
		position = new Position(readerBase.getChild("position"));
		orientation = readerBase.getDouble("orientation", 0);
		closestStop = readerBase.getString("closestStop", "");
		closestStopTimeOffset = readerBase.getLong("closestStopTimeOffset", 0);
		nextStop = readerBase.getString("nextStop", "");
		nextStopTimeOffset = readerBase.getLong("nextStopTimeOffset", 0);
		occupancyStatus = EnumHelper.valueOf(OccupancyStatus.values()[0], readerBase.getString("occupancyStatus", ""));
		phase = readerBase.getString("phase", "");
		status = readerBase.getString("status", "");
		predicted = readerBase.getBoolean("predicted", false);
		lastUpdateTime = readerBase.getLong("lastUpdateTime", 0);
		lastLocationUpdateTime = readerBase.getLong("lastLocationUpdateTime", 0);
		lastKnownLocation = new Position(readerBase.getChild("lastKnownLocation"));
		lastKnownDistanceAlongTrip = readerBase.getDouble("lastKnownDistanceAlongTrip", 0);
		lastKnownOrientation = readerBase.getDouble("lastKnownOrientation", 0);
		distanceAlongTrip = readerBase.getDouble("distanceAlongTrip", 0);
		scheduleDeviation = readerBase.getLong("scheduleDeviation", 0);
		vehicleId = readerBase.getString("vehicleId", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackChild("frequency", readerBaseChild -> frequency = new Frequency(readerBaseChild));
		readerBase.iterateStringArray("situationIds", situationIds::clear, situationIds::add);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("activeTripId", activeTripId);
		writerBase.writeLong("blockTripSequence", blockTripSequence);
		writerBase.writeLong("serviceDate", serviceDate);
		serializeFrequency(writerBase);
		writerBase.writeDouble("scheduledDistanceAlongTrip", scheduledDistanceAlongTrip);
		writerBase.writeDouble("totalDistanceAlongTrip", totalDistanceAlongTrip);
		if (position != null) position.serializeData(writerBase.writeChild("position"));
		writerBase.writeDouble("orientation", orientation);
		writerBase.writeString("closestStop", closestStop);
		writerBase.writeLong("closestStopTimeOffset", closestStopTimeOffset);
		writerBase.writeString("nextStop", nextStop);
		writerBase.writeLong("nextStopTimeOffset", nextStopTimeOffset);
		writerBase.writeString("occupancyStatus", occupancyStatus.toString());
		writerBase.writeString("phase", phase);
		writerBase.writeString("status", status);
		writerBase.writeBoolean("predicted", predicted);
		writerBase.writeLong("lastUpdateTime", lastUpdateTime);
		writerBase.writeLong("lastLocationUpdateTime", lastLocationUpdateTime);
		if (lastKnownLocation != null) lastKnownLocation.serializeData(writerBase.writeChild("lastKnownLocation"));
		writerBase.writeDouble("lastKnownDistanceAlongTrip", lastKnownDistanceAlongTrip);
		writerBase.writeDouble("lastKnownOrientation", lastKnownOrientation);
		writerBase.writeDouble("distanceAlongTrip", distanceAlongTrip);
		writerBase.writeLong("scheduleDeviation", scheduleDeviation);
		writerBase.writeString("vehicleId", vehicleId);
		serializeSituationIds(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "activeTripId: " + activeTripId + "\n"
			+ "blockTripSequence: " + blockTripSequence + "\n"
			+ "serviceDate: " + serviceDate + "\n"
			+ "frequency: " + frequency + "\n"
			+ "scheduledDistanceAlongTrip: " + scheduledDistanceAlongTrip + "\n"
			+ "totalDistanceAlongTrip: " + totalDistanceAlongTrip + "\n"
			+ "position: " + position + "\n"
			+ "orientation: " + orientation + "\n"
			+ "closestStop: " + closestStop + "\n"
			+ "closestStopTimeOffset: " + closestStopTimeOffset + "\n"
			+ "nextStop: " + nextStop + "\n"
			+ "nextStopTimeOffset: " + nextStopTimeOffset + "\n"
			+ "occupancyStatus: " + occupancyStatus + "\n"
			+ "phase: " + phase + "\n"
			+ "status: " + status + "\n"
			+ "predicted: " + predicted + "\n"
			+ "lastUpdateTime: " + lastUpdateTime + "\n"
			+ "lastLocationUpdateTime: " + lastLocationUpdateTime + "\n"
			+ "lastKnownLocation: " + lastKnownLocation + "\n"
			+ "lastKnownDistanceAlongTrip: " + lastKnownDistanceAlongTrip + "\n"
			+ "lastKnownOrientation: " + lastKnownOrientation + "\n"
			+ "distanceAlongTrip: " + distanceAlongTrip + "\n"
			+ "scheduleDeviation: " + scheduleDeviation + "\n"
			+ "vehicleId: " + vehicleId + "\n"
			+ "situationIds: " + situationIds + "\n"
		;
	}

	protected abstract Frequency getDefaultFrequency();

	protected void serializeFrequency(final WriterBase writerBase) {
		if (frequency != null) frequency.serializeData(writerBase.writeChild("frequency"));
	}

	protected void serializeSituationIds(final WriterBase writerBase) {
		final WriterBase.Array situationIdsWriterBaseArray = writerBase.writeArray("situationIds"); situationIds.forEach(situationIdsWriterBaseArray::writeString);
	}
}