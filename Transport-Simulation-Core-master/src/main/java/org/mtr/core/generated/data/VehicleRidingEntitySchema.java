package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class VehicleRidingEntitySchema implements SerializedDataBase {

	protected final String clientId;

	protected final long ridingCar;

	protected final double x;

	protected final double y;

	protected final double z;

	protected final boolean isOnGangway;

	protected final boolean isDriver;

	protected final boolean manualAccelerate;

	protected final boolean manualBrake;

	protected final boolean manualToggleDoors;

	protected final boolean manualToggleAto;

	protected final boolean doorOverride;

	protected VehicleRidingEntitySchema(final String clientId, final long ridingCar, final double x, final double y, final double z, final boolean isOnGangway, final boolean isDriver, final boolean manualAccelerate, final boolean manualBrake, final boolean manualToggleDoors, final boolean manualToggleAto, final boolean doorOverride) {
		this.clientId = clientId;
		this.ridingCar = ridingCar;
		this.x = x;
		this.y = y;
		this.z = z;
		this.isOnGangway = isOnGangway;
		this.isDriver = isDriver;
		this.manualAccelerate = manualAccelerate;
		this.manualBrake = manualBrake;
		this.manualToggleDoors = manualToggleDoors;
		this.manualToggleAto = manualToggleAto;
		this.doorOverride = doorOverride;
	}

	protected VehicleRidingEntitySchema(final ReaderBase readerBase) {
		clientId = readerBase.getString("clientId", "");
		ridingCar = readerBase.getLong("ridingCar", 0);
		x = readerBase.getDouble("x", 0);
		y = readerBase.getDouble("y", 0);
		z = readerBase.getDouble("z", 0);
		isOnGangway = readerBase.getBoolean("isOnGangway", false);
		isDriver = readerBase.getBoolean("isDriver", false);
		manualAccelerate = readerBase.getBoolean("manualAccelerate", false);
		manualBrake = readerBase.getBoolean("manualBrake", false);
		manualToggleDoors = readerBase.getBoolean("manualToggleDoors", false);
		manualToggleAto = readerBase.getBoolean("manualToggleAto", false);
		doorOverride = readerBase.getBoolean("doorOverride", false);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("clientId", clientId);
		writerBase.writeLong("ridingCar", ridingCar);
		writerBase.writeDouble("x", x);
		writerBase.writeDouble("y", y);
		writerBase.writeDouble("z", z);
		writerBase.writeBoolean("isOnGangway", isOnGangway);
		writerBase.writeBoolean("isDriver", isDriver);
		writerBase.writeBoolean("manualAccelerate", manualAccelerate);
		writerBase.writeBoolean("manualBrake", manualBrake);
		writerBase.writeBoolean("manualToggleDoors", manualToggleDoors);
		writerBase.writeBoolean("manualToggleAto", manualToggleAto);
		writerBase.writeBoolean("doorOverride", doorOverride);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "clientId: " + clientId + "\n"
			+ "ridingCar: " + ridingCar + "\n"
			+ "x: " + x + "\n"
			+ "y: " + y + "\n"
			+ "z: " + z + "\n"
			+ "isOnGangway: " + isOnGangway + "\n"
			+ "isDriver: " + isDriver + "\n"
			+ "manualAccelerate: " + manualAccelerate + "\n"
			+ "manualBrake: " + manualBrake + "\n"
			+ "manualToggleDoors: " + manualToggleDoors + "\n"
			+ "manualToggleAto: " + manualToggleAto + "\n"
			+ "doorOverride: " + doorOverride + "\n"
		;
	}
}