package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class ArrivalAndDepartureSchema implements SerializedDataBase {

	protected final String routeId;

	protected final String tripId;

	protected final long serviceDate;

	protected final String stopId;

	protected final long stopSequence;

	protected final long totalStopsInTrip;

	protected final long blockTripSequence;

	protected final String routeShortName;

	protected final String routeLongName;

	protected final String tripHeadsign;

	protected final boolean arrivalEnabled;

	protected final boolean departureEnabled;

	protected final long scheduledArrivalTime;

	protected final long scheduledDepartureTime;

	protected Frequency frequency = getDefaultFrequency();

	protected final boolean predicted;

	protected final long predictedArrivalTime;

	protected final long predictedDepartureTime;

	protected final double distanceFromStop;

	protected final OccupancyStatus historicalOccupancy;

	protected final long numberOfStopsAway;

	protected final OccupancyStatus occupancyStatus;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> situationIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected final String status;

	protected TripStatus tripStatus = getDefaultTripStatus();

	protected final String vehicleId;

	protected ArrivalAndDepartureSchema(final String routeId, final String tripId, final long serviceDate, final String stopId, final long stopSequence, final long totalStopsInTrip, final long blockTripSequence, final String routeShortName, final String routeLongName, final String tripHeadsign, final boolean arrivalEnabled, final boolean departureEnabled, final long scheduledArrivalTime, final long scheduledDepartureTime, final boolean predicted, final long predictedArrivalTime, final long predictedDepartureTime, final double distanceFromStop, final OccupancyStatus historicalOccupancy, final long numberOfStopsAway, final OccupancyStatus occupancyStatus, final String status, final String vehicleId) {
		this.routeId = routeId;
		this.tripId = tripId;
		this.serviceDate = serviceDate;
		this.stopId = stopId;
		this.stopSequence = stopSequence;
		this.totalStopsInTrip = totalStopsInTrip;
		this.blockTripSequence = blockTripSequence;
		this.routeShortName = routeShortName;
		this.routeLongName = routeLongName;
		this.tripHeadsign = tripHeadsign;
		this.arrivalEnabled = arrivalEnabled;
		this.departureEnabled = departureEnabled;
		this.scheduledArrivalTime = scheduledArrivalTime;
		this.scheduledDepartureTime = scheduledDepartureTime;
		this.predicted = predicted;
		this.predictedArrivalTime = predictedArrivalTime;
		this.predictedDepartureTime = predictedDepartureTime;
		this.distanceFromStop = distanceFromStop;
		this.historicalOccupancy = historicalOccupancy;
		this.numberOfStopsAway = numberOfStopsAway;
		this.occupancyStatus = occupancyStatus;
		this.status = status;
		this.vehicleId = vehicleId;
	}

	protected ArrivalAndDepartureSchema(final ReaderBase readerBase) {
		routeId = readerBase.getString("routeId", "");
		tripId = readerBase.getString("tripId", "");
		serviceDate = readerBase.getLong("serviceDate", 0);
		stopId = readerBase.getString("stopId", "");
		stopSequence = readerBase.getLong("stopSequence", 0);
		totalStopsInTrip = readerBase.getLong("totalStopsInTrip", 0);
		blockTripSequence = readerBase.getLong("blockTripSequence", 0);
		routeShortName = readerBase.getString("routeShortName", "");
		routeLongName = readerBase.getString("routeLongName", "");
		tripHeadsign = readerBase.getString("tripHeadsign", "");
		arrivalEnabled = readerBase.getBoolean("arrivalEnabled", false);
		departureEnabled = readerBase.getBoolean("departureEnabled", false);
		scheduledArrivalTime = readerBase.getLong("scheduledArrivalTime", 0);
		scheduledDepartureTime = readerBase.getLong("scheduledDepartureTime", 0);
		predicted = readerBase.getBoolean("predicted", false);
		predictedArrivalTime = readerBase.getLong("predictedArrivalTime", 0);
		predictedDepartureTime = readerBase.getLong("predictedDepartureTime", 0);
		distanceFromStop = readerBase.getDouble("distanceFromStop", 0);
		historicalOccupancy = EnumHelper.valueOf(OccupancyStatus.values()[0], readerBase.getString("historicalOccupancy", ""));
		numberOfStopsAway = readerBase.getLong("numberOfStopsAway", 0);
		occupancyStatus = EnumHelper.valueOf(OccupancyStatus.values()[0], readerBase.getString("occupancyStatus", ""));
		status = readerBase.getString("status", "");
		vehicleId = readerBase.getString("vehicleId", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackChild("frequency", readerBaseChild -> frequency = new Frequency(readerBaseChild));
		readerBase.iterateStringArray("situationIds", situationIds::clear, situationIds::add);
		readerBase.unpackChild("tripStatus", readerBaseChild -> tripStatus = new TripStatus(readerBaseChild));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("routeId", routeId);
		writerBase.writeString("tripId", tripId);
		writerBase.writeLong("serviceDate", serviceDate);
		writerBase.writeString("stopId", stopId);
		writerBase.writeLong("stopSequence", stopSequence);
		writerBase.writeLong("totalStopsInTrip", totalStopsInTrip);
		writerBase.writeLong("blockTripSequence", blockTripSequence);
		writerBase.writeString("routeShortName", routeShortName);
		writerBase.writeString("routeLongName", routeLongName);
		writerBase.writeString("tripHeadsign", tripHeadsign);
		writerBase.writeBoolean("arrivalEnabled", arrivalEnabled);
		writerBase.writeBoolean("departureEnabled", departureEnabled);
		writerBase.writeLong("scheduledArrivalTime", scheduledArrivalTime);
		writerBase.writeLong("scheduledDepartureTime", scheduledDepartureTime);
		serializeFrequency(writerBase);
		writerBase.writeBoolean("predicted", predicted);
		writerBase.writeLong("predictedArrivalTime", predictedArrivalTime);
		writerBase.writeLong("predictedDepartureTime", predictedDepartureTime);
		writerBase.writeDouble("distanceFromStop", distanceFromStop);
		writerBase.writeString("historicalOccupancy", historicalOccupancy.toString());
		writerBase.writeLong("numberOfStopsAway", numberOfStopsAway);
		writerBase.writeString("occupancyStatus", occupancyStatus.toString());
		serializeSituationIds(writerBase);
		writerBase.writeString("status", status);
		serializeTripStatus(writerBase);
		writerBase.writeString("vehicleId", vehicleId);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "routeId: " + routeId + "\n"
			+ "tripId: " + tripId + "\n"
			+ "serviceDate: " + serviceDate + "\n"
			+ "stopId: " + stopId + "\n"
			+ "stopSequence: " + stopSequence + "\n"
			+ "totalStopsInTrip: " + totalStopsInTrip + "\n"
			+ "blockTripSequence: " + blockTripSequence + "\n"
			+ "routeShortName: " + routeShortName + "\n"
			+ "routeLongName: " + routeLongName + "\n"
			+ "tripHeadsign: " + tripHeadsign + "\n"
			+ "arrivalEnabled: " + arrivalEnabled + "\n"
			+ "departureEnabled: " + departureEnabled + "\n"
			+ "scheduledArrivalTime: " + scheduledArrivalTime + "\n"
			+ "scheduledDepartureTime: " + scheduledDepartureTime + "\n"
			+ "frequency: " + frequency + "\n"
			+ "predicted: " + predicted + "\n"
			+ "predictedArrivalTime: " + predictedArrivalTime + "\n"
			+ "predictedDepartureTime: " + predictedDepartureTime + "\n"
			+ "distanceFromStop: " + distanceFromStop + "\n"
			+ "historicalOccupancy: " + historicalOccupancy + "\n"
			+ "numberOfStopsAway: " + numberOfStopsAway + "\n"
			+ "occupancyStatus: " + occupancyStatus + "\n"
			+ "situationIds: " + situationIds + "\n"
			+ "status: " + status + "\n"
			+ "tripStatus: " + tripStatus + "\n"
			+ "vehicleId: " + vehicleId + "\n"
		;
	}

	protected abstract Frequency getDefaultFrequency();

	protected void serializeFrequency(final WriterBase writerBase) {
		if (frequency != null) frequency.serializeData(writerBase.writeChild("frequency"));
	}

	protected void serializeSituationIds(final WriterBase writerBase) {
		final WriterBase.Array situationIdsWriterBaseArray = writerBase.writeArray("situationIds"); situationIds.forEach(situationIdsWriterBaseArray::writeString);
	}

	protected abstract TripStatus getDefaultTripStatus();

	protected void serializeTripStatus(final WriterBase writerBase) {
		if (tripStatus != null) tripStatus.serializeData(writerBase.writeChild("tripStatus"));
	}
}