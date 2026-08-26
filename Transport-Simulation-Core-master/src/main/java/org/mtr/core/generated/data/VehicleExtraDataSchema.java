package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class VehicleExtraDataSchema implements SerializedDataBase {

	protected final long depotId;

	protected final long sidingId;

	protected long previousRouteId;

	protected long previousPlatformId;

	protected long previousStationId;

	protected long previousRouteColor;

	protected String previousRouteName = "";

	protected String previousRouteNumber = "";

	protected RouteType previousRouteType = RouteType.values()[0];

	protected Route.CircularState previousRouteCircularState = Route.CircularState.values()[0];

	protected String previousStationName = "";

	protected String previousRouteDestination = "";

	protected long thisRouteId;

	protected long thisPlatformId;

	protected long thisStationId;

	protected long thisRouteColor;

	protected String thisRouteName = "";

	protected String thisRouteNumber = "";

	protected RouteType thisRouteType = RouteType.values()[0];

	protected Route.CircularState thisRouteCircularState = Route.CircularState.values()[0];

	protected String thisStationName = "";

	protected String thisRouteDestination = "";

	protected long nextRouteId;

	protected long nextPlatformId;

	protected long nextStationId;

	protected long nextRouteColor;

	protected String nextRouteName = "";

	protected String nextRouteNumber = "";

	protected RouteType nextRouteType = RouteType.values()[0];

	protected Route.CircularState nextRouteCircularState = Route.CircularState.values()[0];

	protected String nextStationName = "";

	protected String nextRouteDestination = "";

	protected boolean isTerminating;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<InterchangeColorsForStationName> interchangeColorsForStationNameList = new it.unimi.dsi.fastutil.objects.ObjectArrayList<InterchangeColorsForStationName>();

	protected double stoppingPoint;

	protected long powerLevel;

	protected double speedTarget;

	protected boolean doorTarget;

	protected boolean isCurrentlyManual;

	protected final double railLength;

	protected final double totalVehicleLength;

	protected final long repeatIndex1;

	protected final long repeatIndex2;

	protected final double acceleration;

	protected final double deceleration;

	protected final boolean isManualAllowed;

	protected final double maxManualSpeed;

	protected final long manualToAutomaticTime;

	protected final double totalDistance;

	protected final double defaultPosition;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<VehicleCar> vehicleCars = new it.unimi.dsi.fastutil.objects.ObjectArrayList<VehicleCar>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<PathData> path = new it.unimi.dsi.fastutil.objects.ObjectArrayList<PathData>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<VehicleRidingEntity> ridingEntities = new it.unimi.dsi.fastutil.objects.ObjectArrayList<VehicleRidingEntity>();

	protected VehicleExtraDataSchema(final long depotId, final long sidingId, final double railLength, final double totalVehicleLength, final long repeatIndex1, final long repeatIndex2, final double acceleration, final double deceleration, final boolean isManualAllowed, final double maxManualSpeed, final long manualToAutomaticTime, final double totalDistance, final double defaultPosition) {
		this.depotId = depotId;
		this.sidingId = sidingId;
		this.railLength = railLength;
		this.totalVehicleLength = totalVehicleLength;
		this.repeatIndex1 = repeatIndex1;
		this.repeatIndex2 = repeatIndex2;
		this.acceleration = acceleration;
		this.deceleration = deceleration;
		this.isManualAllowed = isManualAllowed;
		this.maxManualSpeed = maxManualSpeed;
		this.manualToAutomaticTime = manualToAutomaticTime;
		this.totalDistance = totalDistance;
		this.defaultPosition = defaultPosition;
	}

	protected VehicleExtraDataSchema(final ReaderBase readerBase) {
		depotId = readerBase.getLong("depotId", 0);
		sidingId = readerBase.getLong("sidingId", 0);
		railLength = readerBase.getDouble("railLength", 0);
		totalVehicleLength = readerBase.getDouble("totalVehicleLength", 0);
		repeatIndex1 = readerBase.getLong("repeatIndex1", 0);
		repeatIndex2 = readerBase.getLong("repeatIndex2", 0);
		acceleration = readerBase.getDouble("acceleration", 0);
		deceleration = readerBase.getDouble("deceleration", 0);
		isManualAllowed = readerBase.getBoolean("isManualAllowed", false);
		maxManualSpeed = readerBase.getDouble("maxManualSpeed", 0);
		manualToAutomaticTime = readerBase.getLong("manualToAutomaticTime", 0);
		totalDistance = readerBase.getDouble("totalDistance", 0);
		defaultPosition = readerBase.getDouble("defaultPosition", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackLong("previousRouteId", value -> previousRouteId = value);
		readerBase.unpackLong("previousPlatformId", value -> previousPlatformId = value);
		readerBase.unpackLong("previousStationId", value -> previousStationId = value);
		readerBase.unpackLong("previousRouteColor", value -> previousRouteColor = value);
		readerBase.unpackString("previousRouteName", value -> previousRouteName = value);
		readerBase.unpackString("previousRouteNumber", value -> previousRouteNumber = value);
		readerBase.unpackString("previousRouteType", value -> previousRouteType = EnumHelper.valueOf(RouteType.values()[0], value));
		readerBase.unpackString("previousRouteCircularState", value -> previousRouteCircularState = EnumHelper.valueOf(Route.CircularState.values()[0], value));
		readerBase.unpackString("previousStationName", value -> previousStationName = value);
		readerBase.unpackString("previousRouteDestination", value -> previousRouteDestination = value);
		readerBase.unpackLong("thisRouteId", value -> thisRouteId = value);
		readerBase.unpackLong("thisPlatformId", value -> thisPlatformId = value);
		readerBase.unpackLong("thisStationId", value -> thisStationId = value);
		readerBase.unpackLong("thisRouteColor", value -> thisRouteColor = value);
		readerBase.unpackString("thisRouteName", value -> thisRouteName = value);
		readerBase.unpackString("thisRouteNumber", value -> thisRouteNumber = value);
		readerBase.unpackString("thisRouteType", value -> thisRouteType = EnumHelper.valueOf(RouteType.values()[0], value));
		readerBase.unpackString("thisRouteCircularState", value -> thisRouteCircularState = EnumHelper.valueOf(Route.CircularState.values()[0], value));
		readerBase.unpackString("thisStationName", value -> thisStationName = value);
		readerBase.unpackString("thisRouteDestination", value -> thisRouteDestination = value);
		readerBase.unpackLong("nextRouteId", value -> nextRouteId = value);
		readerBase.unpackLong("nextPlatformId", value -> nextPlatformId = value);
		readerBase.unpackLong("nextStationId", value -> nextStationId = value);
		readerBase.unpackLong("nextRouteColor", value -> nextRouteColor = value);
		readerBase.unpackString("nextRouteName", value -> nextRouteName = value);
		readerBase.unpackString("nextRouteNumber", value -> nextRouteNumber = value);
		readerBase.unpackString("nextRouteType", value -> nextRouteType = EnumHelper.valueOf(RouteType.values()[0], value));
		readerBase.unpackString("nextRouteCircularState", value -> nextRouteCircularState = EnumHelper.valueOf(Route.CircularState.values()[0], value));
		readerBase.unpackString("nextStationName", value -> nextStationName = value);
		readerBase.unpackString("nextRouteDestination", value -> nextRouteDestination = value);
		readerBase.unpackBoolean("isTerminating", value -> isTerminating = value);
		readerBase.iterateReaderArray("interchangeColorsForStationNameList", interchangeColorsForStationNameList::clear, readerBaseChild -> interchangeColorsForStationNameList.add(new InterchangeColorsForStationName(readerBaseChild)));
		readerBase.unpackDouble("stoppingPoint", value -> stoppingPoint = value);
		readerBase.unpackLong("powerLevel", value -> powerLevel = value);
		readerBase.unpackDouble("speedTarget", value -> speedTarget = value);
		readerBase.unpackBoolean("doorTarget", value -> doorTarget = value);
		readerBase.unpackBoolean("isCurrentlyManual", value -> isCurrentlyManual = value);
		readerBase.iterateReaderArray("vehicleCars", vehicleCars::clear, readerBaseChild -> vehicleCars.add(new VehicleCar(readerBaseChild)));
		readerBase.iterateReaderArray("path", path::clear, readerBaseChild -> path.add(new PathData(readerBaseChild)));
		readerBase.iterateReaderArray("ridingEntities", ridingEntities::clear, readerBaseChild -> ridingEntities.add(new VehicleRidingEntity(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("depotId", depotId);
		writerBase.writeLong("sidingId", sidingId);
		serializePreviousRouteId(writerBase);
		serializePreviousPlatformId(writerBase);
		serializePreviousStationId(writerBase);
		serializePreviousRouteColor(writerBase);
		serializePreviousRouteName(writerBase);
		serializePreviousRouteNumber(writerBase);
		serializePreviousRouteType(writerBase);
		serializePreviousRouteCircularState(writerBase);
		serializePreviousStationName(writerBase);
		serializePreviousRouteDestination(writerBase);
		serializeThisRouteId(writerBase);
		serializeThisPlatformId(writerBase);
		serializeThisStationId(writerBase);
		serializeThisRouteColor(writerBase);
		serializeThisRouteName(writerBase);
		serializeThisRouteNumber(writerBase);
		serializeThisRouteType(writerBase);
		serializeThisRouteCircularState(writerBase);
		serializeThisStationName(writerBase);
		serializeThisRouteDestination(writerBase);
		serializeNextRouteId(writerBase);
		serializeNextPlatformId(writerBase);
		serializeNextStationId(writerBase);
		serializeNextRouteColor(writerBase);
		serializeNextRouteName(writerBase);
		serializeNextRouteNumber(writerBase);
		serializeNextRouteType(writerBase);
		serializeNextRouteCircularState(writerBase);
		serializeNextStationName(writerBase);
		serializeNextRouteDestination(writerBase);
		serializeIsTerminating(writerBase);
		serializeInterchangeColorsForStationNameList(writerBase);
		serializeStoppingPoint(writerBase);
		serializePowerLevel(writerBase);
		serializeSpeedTarget(writerBase);
		serializeDoorTarget(writerBase);
		serializeIsCurrentlyManual(writerBase);
		writerBase.writeDouble("railLength", railLength);
		writerBase.writeDouble("totalVehicleLength", totalVehicleLength);
		writerBase.writeLong("repeatIndex1", repeatIndex1);
		writerBase.writeLong("repeatIndex2", repeatIndex2);
		writerBase.writeDouble("acceleration", acceleration);
		writerBase.writeDouble("deceleration", deceleration);
		writerBase.writeBoolean("isManualAllowed", isManualAllowed);
		writerBase.writeDouble("maxManualSpeed", maxManualSpeed);
		writerBase.writeLong("manualToAutomaticTime", manualToAutomaticTime);
		writerBase.writeDouble("totalDistance", totalDistance);
		writerBase.writeDouble("defaultPosition", defaultPosition);
		serializeVehicleCars(writerBase);
		serializePath(writerBase);
		serializeRidingEntities(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "depotId: " + depotId + "\n"
			+ "sidingId: " + sidingId + "\n"
			+ "previousRouteId: " + previousRouteId + "\n"
			+ "previousPlatformId: " + previousPlatformId + "\n"
			+ "previousStationId: " + previousStationId + "\n"
			+ "previousRouteColor: " + previousRouteColor + "\n"
			+ "previousRouteName: " + previousRouteName + "\n"
			+ "previousRouteNumber: " + previousRouteNumber + "\n"
			+ "previousRouteType: " + previousRouteType + "\n"
			+ "previousRouteCircularState: " + previousRouteCircularState + "\n"
			+ "previousStationName: " + previousStationName + "\n"
			+ "previousRouteDestination: " + previousRouteDestination + "\n"
			+ "thisRouteId: " + thisRouteId + "\n"
			+ "thisPlatformId: " + thisPlatformId + "\n"
			+ "thisStationId: " + thisStationId + "\n"
			+ "thisRouteColor: " + thisRouteColor + "\n"
			+ "thisRouteName: " + thisRouteName + "\n"
			+ "thisRouteNumber: " + thisRouteNumber + "\n"
			+ "thisRouteType: " + thisRouteType + "\n"
			+ "thisRouteCircularState: " + thisRouteCircularState + "\n"
			+ "thisStationName: " + thisStationName + "\n"
			+ "thisRouteDestination: " + thisRouteDestination + "\n"
			+ "nextRouteId: " + nextRouteId + "\n"
			+ "nextPlatformId: " + nextPlatformId + "\n"
			+ "nextStationId: " + nextStationId + "\n"
			+ "nextRouteColor: " + nextRouteColor + "\n"
			+ "nextRouteName: " + nextRouteName + "\n"
			+ "nextRouteNumber: " + nextRouteNumber + "\n"
			+ "nextRouteType: " + nextRouteType + "\n"
			+ "nextRouteCircularState: " + nextRouteCircularState + "\n"
			+ "nextStationName: " + nextStationName + "\n"
			+ "nextRouteDestination: " + nextRouteDestination + "\n"
			+ "isTerminating: " + isTerminating + "\n"
			+ "interchangeColorsForStationNameList: " + interchangeColorsForStationNameList + "\n"
			+ "stoppingPoint: " + stoppingPoint + "\n"
			+ "powerLevel: " + powerLevel + "\n"
			+ "speedTarget: " + speedTarget + "\n"
			+ "doorTarget: " + doorTarget + "\n"
			+ "isCurrentlyManual: " + isCurrentlyManual + "\n"
			+ "railLength: " + railLength + "\n"
			+ "totalVehicleLength: " + totalVehicleLength + "\n"
			+ "repeatIndex1: " + repeatIndex1 + "\n"
			+ "repeatIndex2: " + repeatIndex2 + "\n"
			+ "acceleration: " + acceleration + "\n"
			+ "deceleration: " + deceleration + "\n"
			+ "isManualAllowed: " + isManualAllowed + "\n"
			+ "maxManualSpeed: " + maxManualSpeed + "\n"
			+ "manualToAutomaticTime: " + manualToAutomaticTime + "\n"
			+ "totalDistance: " + totalDistance + "\n"
			+ "defaultPosition: " + defaultPosition + "\n"
			+ "vehicleCars: " + vehicleCars + "\n"
			+ "path: " + path + "\n"
			+ "ridingEntities: " + ridingEntities + "\n"
		;
	}

	protected void serializePreviousRouteId(final WriterBase writerBase) {
		writerBase.writeLong("previousRouteId", previousRouteId);
	}

	protected void serializePreviousPlatformId(final WriterBase writerBase) {
		writerBase.writeLong("previousPlatformId", previousPlatformId);
	}

	protected void serializePreviousStationId(final WriterBase writerBase) {
		writerBase.writeLong("previousStationId", previousStationId);
	}

	protected void serializePreviousRouteColor(final WriterBase writerBase) {
		writerBase.writeLong("previousRouteColor", previousRouteColor);
	}

	protected void serializePreviousRouteName(final WriterBase writerBase) {
		writerBase.writeString("previousRouteName", previousRouteName);
	}

	protected void serializePreviousRouteNumber(final WriterBase writerBase) {
		writerBase.writeString("previousRouteNumber", previousRouteNumber);
	}

	protected void serializePreviousRouteType(final WriterBase writerBase) {
		writerBase.writeString("previousRouteType", previousRouteType.toString());
	}

	protected void serializePreviousRouteCircularState(final WriterBase writerBase) {
		writerBase.writeString("previousRouteCircularState", previousRouteCircularState.toString());
	}

	protected void serializePreviousStationName(final WriterBase writerBase) {
		writerBase.writeString("previousStationName", previousStationName);
	}

	protected void serializePreviousRouteDestination(final WriterBase writerBase) {
		writerBase.writeString("previousRouteDestination", previousRouteDestination);
	}

	protected void serializeThisRouteId(final WriterBase writerBase) {
		writerBase.writeLong("thisRouteId", thisRouteId);
	}

	protected void serializeThisPlatformId(final WriterBase writerBase) {
		writerBase.writeLong("thisPlatformId", thisPlatformId);
	}

	protected void serializeThisStationId(final WriterBase writerBase) {
		writerBase.writeLong("thisStationId", thisStationId);
	}

	protected void serializeThisRouteColor(final WriterBase writerBase) {
		writerBase.writeLong("thisRouteColor", thisRouteColor);
	}

	protected void serializeThisRouteName(final WriterBase writerBase) {
		writerBase.writeString("thisRouteName", thisRouteName);
	}

	protected void serializeThisRouteNumber(final WriterBase writerBase) {
		writerBase.writeString("thisRouteNumber", thisRouteNumber);
	}

	protected void serializeThisRouteType(final WriterBase writerBase) {
		writerBase.writeString("thisRouteType", thisRouteType.toString());
	}

	protected void serializeThisRouteCircularState(final WriterBase writerBase) {
		writerBase.writeString("thisRouteCircularState", thisRouteCircularState.toString());
	}

	protected void serializeThisStationName(final WriterBase writerBase) {
		writerBase.writeString("thisStationName", thisStationName);
	}

	protected void serializeThisRouteDestination(final WriterBase writerBase) {
		writerBase.writeString("thisRouteDestination", thisRouteDestination);
	}

	protected void serializeNextRouteId(final WriterBase writerBase) {
		writerBase.writeLong("nextRouteId", nextRouteId);
	}

	protected void serializeNextPlatformId(final WriterBase writerBase) {
		writerBase.writeLong("nextPlatformId", nextPlatformId);
	}

	protected void serializeNextStationId(final WriterBase writerBase) {
		writerBase.writeLong("nextStationId", nextStationId);
	}

	protected void serializeNextRouteColor(final WriterBase writerBase) {
		writerBase.writeLong("nextRouteColor", nextRouteColor);
	}

	protected void serializeNextRouteName(final WriterBase writerBase) {
		writerBase.writeString("nextRouteName", nextRouteName);
	}

	protected void serializeNextRouteNumber(final WriterBase writerBase) {
		writerBase.writeString("nextRouteNumber", nextRouteNumber);
	}

	protected void serializeNextRouteType(final WriterBase writerBase) {
		writerBase.writeString("nextRouteType", nextRouteType.toString());
	}

	protected void serializeNextRouteCircularState(final WriterBase writerBase) {
		writerBase.writeString("nextRouteCircularState", nextRouteCircularState.toString());
	}

	protected void serializeNextStationName(final WriterBase writerBase) {
		writerBase.writeString("nextStationName", nextStationName);
	}

	protected void serializeNextRouteDestination(final WriterBase writerBase) {
		writerBase.writeString("nextRouteDestination", nextRouteDestination);
	}

	protected void serializeIsTerminating(final WriterBase writerBase) {
		writerBase.writeBoolean("isTerminating", isTerminating);
	}

	protected void serializeInterchangeColorsForStationNameList(final WriterBase writerBase) {
		writerBase.writeDataset(interchangeColorsForStationNameList, "interchangeColorsForStationNameList");
	}

	protected void serializeStoppingPoint(final WriterBase writerBase) {
		writerBase.writeDouble("stoppingPoint", stoppingPoint);
	}

	protected void serializePowerLevel(final WriterBase writerBase) {
		writerBase.writeLong("powerLevel", powerLevel);
	}

	protected void serializeSpeedTarget(final WriterBase writerBase) {
		writerBase.writeDouble("speedTarget", speedTarget);
	}

	protected void serializeDoorTarget(final WriterBase writerBase) {
		writerBase.writeBoolean("doorTarget", doorTarget);
	}

	protected void serializeIsCurrentlyManual(final WriterBase writerBase) {
		writerBase.writeBoolean("isCurrentlyManual", isCurrentlyManual);
	}

	protected void serializeVehicleCars(final WriterBase writerBase) {
		writerBase.writeDataset(vehicleCars, "vehicleCars");
	}

	protected void serializePath(final WriterBase writerBase) {
		writerBase.writeDataset(path, "path");
	}

	protected void serializeRidingEntities(final WriterBase writerBase) {
		writerBase.writeDataset(ridingEntities, "ridingEntities");
	}
}