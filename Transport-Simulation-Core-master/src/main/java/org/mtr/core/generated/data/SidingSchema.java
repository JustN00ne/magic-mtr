package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class SidingSchema extends SavedRailBase<Siding, Depot> {

	protected final double railLength;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<VehicleCar> vehicleCars = new it.unimi.dsi.fastutil.objects.ObjectArrayList<VehicleCar>();

	protected long maxVehicles;

	protected long delayedVehicleSpeedIncreasePercentage = 25;

	protected long delayedVehicleReduceDwellTimePercentage = 100;

	protected boolean earlyVehicleIncreaseDwellTime = true;

	protected double maxManualSpeed;

	protected long manualToAutomaticTime = 10000;

	protected double acceleration = 0.000004;

	protected double deceleration = 0.000004;

	protected SidingSchema(final double railLength, final Position position1, final Position position2, final TransportMode transportMode, final Data data) {
		super(position1, position2, transportMode, data);
		this.railLength = railLength;
	}

	protected SidingSchema(final ReaderBase readerBase, final Data data) {
		super(readerBase, data);
		railLength = readerBase.getDouble("railLength", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		super.updateData(readerBase);
		readerBase.iterateReaderArray("vehicleCars", vehicleCars::clear, readerBaseChild -> vehicleCars.add(new VehicleCar(readerBaseChild)));
		readerBase.unpackLong("maxVehicles", value -> maxVehicles = value);
		readerBase.unpackLong("delayedVehicleSpeedIncreasePercentage", value -> delayedVehicleSpeedIncreasePercentage = value);
		readerBase.unpackLong("delayedVehicleReduceDwellTimePercentage", value -> delayedVehicleReduceDwellTimePercentage = value);
		readerBase.unpackBoolean("earlyVehicleIncreaseDwellTime", value -> earlyVehicleIncreaseDwellTime = value);
		readerBase.unpackDouble("maxManualSpeed", value -> maxManualSpeed = value);
		readerBase.unpackLong("manualToAutomaticTime", value -> manualToAutomaticTime = value);
		readerBase.unpackDouble("acceleration", value -> acceleration = value);
		readerBase.unpackDouble("deceleration", value -> deceleration = value);
	}

	public void serializeData(final WriterBase writerBase) {
		super.serializeData(writerBase);
		writerBase.writeDouble("railLength", railLength);
		serializeVehicleCars(writerBase);
		serializeMaxVehicles(writerBase);
		serializeDelayedVehicleSpeedIncreasePercentage(writerBase);
		serializeDelayedVehicleReduceDwellTimePercentage(writerBase);
		serializeEarlyVehicleIncreaseDwellTime(writerBase);
		serializeMaxManualSpeed(writerBase);
		serializeManualToAutomaticTime(writerBase);
		serializeAcceleration(writerBase);
		serializeDeceleration(writerBase);
	}

	@Nonnull
	public String toString() {
		return super.toString()
			+ "railLength: " + railLength + "\n"
			+ "vehicleCars: " + vehicleCars + "\n"
			+ "maxVehicles: " + maxVehicles + "\n"
			+ "delayedVehicleSpeedIncreasePercentage: " + delayedVehicleSpeedIncreasePercentage + "\n"
			+ "delayedVehicleReduceDwellTimePercentage: " + delayedVehicleReduceDwellTimePercentage + "\n"
			+ "earlyVehicleIncreaseDwellTime: " + earlyVehicleIncreaseDwellTime + "\n"
			+ "maxManualSpeed: " + maxManualSpeed + "\n"
			+ "manualToAutomaticTime: " + manualToAutomaticTime + "\n"
			+ "acceleration: " + acceleration + "\n"
			+ "deceleration: " + deceleration + "\n"
		;
	}

	protected void serializeVehicleCars(final WriterBase writerBase) {
		writerBase.writeDataset(vehicleCars, "vehicleCars");
	}

	protected void serializeMaxVehicles(final WriterBase writerBase) {
		writerBase.writeLong("maxVehicles", maxVehicles);
	}

	protected void serializeDelayedVehicleSpeedIncreasePercentage(final WriterBase writerBase) {
		writerBase.writeLong("delayedVehicleSpeedIncreasePercentage", delayedVehicleSpeedIncreasePercentage);
	}

	protected void serializeDelayedVehicleReduceDwellTimePercentage(final WriterBase writerBase) {
		writerBase.writeLong("delayedVehicleReduceDwellTimePercentage", delayedVehicleReduceDwellTimePercentage);
	}

	protected void serializeEarlyVehicleIncreaseDwellTime(final WriterBase writerBase) {
		writerBase.writeBoolean("earlyVehicleIncreaseDwellTime", earlyVehicleIncreaseDwellTime);
	}

	protected void serializeMaxManualSpeed(final WriterBase writerBase) {
		writerBase.writeDouble("maxManualSpeed", maxManualSpeed);
	}

	protected void serializeManualToAutomaticTime(final WriterBase writerBase) {
		writerBase.writeLong("manualToAutomaticTime", manualToAutomaticTime);
	}

	protected void serializeAcceleration(final WriterBase writerBase) {
		writerBase.writeDouble("acceleration", acceleration);
	}

	protected void serializeDeceleration(final WriterBase writerBase) {
		writerBase.writeDouble("deceleration", deceleration);
	}
}