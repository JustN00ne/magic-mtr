package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class VehicleSchema extends NameColorDataBase {

	protected double speed;

	protected double railProgress;

	protected long elapsedDwellTime;

	protected long nextStoppingIndexAto;

	protected long nextStoppingIndexManual;

	protected boolean reversed;

	protected long departureIndex = -1;

	protected long sidingDepartureTime = -1;

	protected VehicleSchema(final TransportMode transportMode, final Data data) {
		super(transportMode, data);
	}

	protected VehicleSchema(final ReaderBase readerBase, final Data data) {
		super(readerBase, data);
	}

	public void updateData(final ReaderBase readerBase) {
		super.updateData(readerBase);
		readerBase.unpackDouble("speed", value -> speed = value);
		readerBase.unpackDouble("railProgress", value -> railProgress = value);
		readerBase.unpackLong("elapsedDwellTime", value -> elapsedDwellTime = value);
		readerBase.unpackLong("nextStoppingIndexAto", value -> nextStoppingIndexAto = value);
		readerBase.unpackLong("nextStoppingIndexManual", value -> nextStoppingIndexManual = value);
		readerBase.unpackBoolean("reversed", value -> reversed = value);
		readerBase.unpackLong("departureIndex", value -> departureIndex = value);
		readerBase.unpackLong("sidingDepartureTime", value -> sidingDepartureTime = value);
	}

	public void serializeData(final WriterBase writerBase) {
		super.serializeData(writerBase);
		serializeSpeed(writerBase);
		serializeRailProgress(writerBase);
		serializeElapsedDwellTime(writerBase);
		serializeNextStoppingIndexAto(writerBase);
		serializeNextStoppingIndexManual(writerBase);
		serializeReversed(writerBase);
		serializeDepartureIndex(writerBase);
		serializeSidingDepartureTime(writerBase);
	}

	@Nonnull
	public String toString() {
		return super.toString()
			+ "speed: " + speed + "\n"
			+ "railProgress: " + railProgress + "\n"
			+ "elapsedDwellTime: " + elapsedDwellTime + "\n"
			+ "nextStoppingIndexAto: " + nextStoppingIndexAto + "\n"
			+ "nextStoppingIndexManual: " + nextStoppingIndexManual + "\n"
			+ "reversed: " + reversed + "\n"
			+ "departureIndex: " + departureIndex + "\n"
			+ "sidingDepartureTime: " + sidingDepartureTime + "\n"
		;
	}

	protected void serializeSpeed(final WriterBase writerBase) {
		writerBase.writeDouble("speed", speed);
	}

	protected void serializeRailProgress(final WriterBase writerBase) {
		writerBase.writeDouble("railProgress", railProgress);
	}

	protected void serializeElapsedDwellTime(final WriterBase writerBase) {
		writerBase.writeLong("elapsedDwellTime", elapsedDwellTime);
	}

	protected void serializeNextStoppingIndexAto(final WriterBase writerBase) {
		writerBase.writeLong("nextStoppingIndexAto", nextStoppingIndexAto);
	}

	protected void serializeNextStoppingIndexManual(final WriterBase writerBase) {
		writerBase.writeLong("nextStoppingIndexManual", nextStoppingIndexManual);
	}

	protected void serializeReversed(final WriterBase writerBase) {
		writerBase.writeBoolean("reversed", reversed);
	}

	protected void serializeDepartureIndex(final WriterBase writerBase) {
		writerBase.writeLong("departureIndex", departureIndex);
	}

	protected void serializeSidingDepartureTime(final WriterBase writerBase) {
		writerBase.writeLong("sidingDepartureTime", sidingDepartureTime);
	}
}