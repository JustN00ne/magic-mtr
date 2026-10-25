package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class LiftSchema extends NameColorDataBase {

	protected double height;

	protected double width;

	protected double depth;

	protected double offsetX;

	protected double offsetY;

	protected double offsetZ;

	protected boolean isDoubleSided;

	protected String style = "";

	protected Angle angle = Angle.values()[0];

	protected double railProgress;

	protected double speed;

	protected long stoppingCoolDown;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<LiftFloor> floors = new it.unimi.dsi.fastutil.objects.ObjectArrayList<LiftFloor>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<LiftInstruction> instructions = new it.unimi.dsi.fastutil.objects.ObjectArrayList<LiftInstruction>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<VehicleRidingEntity> ridingEntities = new it.unimi.dsi.fastutil.objects.ObjectArrayList<VehicleRidingEntity>();

	protected LiftSchema(final TransportMode transportMode, final Data data) {
		super(transportMode, data);
	}

	protected LiftSchema(final ReaderBase readerBase, final Data data) {
		super(readerBase, data);
	}

	public void updateData(final ReaderBase readerBase) {
		super.updateData(readerBase);
		readerBase.unpackDouble("height", value -> height = value);
		readerBase.unpackDouble("width", value -> width = value);
		readerBase.unpackDouble("depth", value -> depth = value);
		readerBase.unpackDouble("offsetX", value -> offsetX = value);
		readerBase.unpackDouble("offsetY", value -> offsetY = value);
		readerBase.unpackDouble("offsetZ", value -> offsetZ = value);
		readerBase.unpackBoolean("isDoubleSided", value -> isDoubleSided = value);
		readerBase.unpackString("style", value -> style = value);
		readerBase.unpackString("angle", value -> angle = EnumHelper.valueOf(Angle.values()[0], value));
		readerBase.unpackDouble("railProgress", value -> railProgress = value);
		readerBase.unpackDouble("speed", value -> speed = value);
		readerBase.unpackLong("stoppingCoolDown", value -> stoppingCoolDown = value);
		readerBase.iterateReaderArray("floors", floors::clear, readerBaseChild -> floors.add(new LiftFloor(readerBaseChild)));
		readerBase.iterateReaderArray("instructions", instructions::clear, readerBaseChild -> instructions.add(new LiftInstruction(readerBaseChild)));
		readerBase.iterateReaderArray("ridingEntities", ridingEntities::clear, readerBaseChild -> ridingEntities.add(new VehicleRidingEntity(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		super.serializeData(writerBase);
		serializeHeight(writerBase);
		serializeWidth(writerBase);
		serializeDepth(writerBase);
		serializeOffsetX(writerBase);
		serializeOffsetY(writerBase);
		serializeOffsetZ(writerBase);
		serializeIsDoubleSided(writerBase);
		serializeStyle(writerBase);
		serializeAngle(writerBase);
		serializeRailProgress(writerBase);
		serializeSpeed(writerBase);
		serializeStoppingCoolDown(writerBase);
		serializeFloors(writerBase);
		serializeInstructions(writerBase);
		serializeRidingEntities(writerBase);
	}

	@Nonnull
	public String toString() {
		return super.toString()
			+ "height: " + height + "\n"
			+ "width: " + width + "\n"
			+ "depth: " + depth + "\n"
			+ "offsetX: " + offsetX + "\n"
			+ "offsetY: " + offsetY + "\n"
			+ "offsetZ: " + offsetZ + "\n"
			+ "isDoubleSided: " + isDoubleSided + "\n"
			+ "style: " + style + "\n"
			+ "angle: " + angle + "\n"
			+ "railProgress: " + railProgress + "\n"
			+ "speed: " + speed + "\n"
			+ "stoppingCoolDown: " + stoppingCoolDown + "\n"
			+ "floors: " + floors + "\n"
			+ "instructions: " + instructions + "\n"
			+ "ridingEntities: " + ridingEntities + "\n"
		;
	}

	protected void serializeHeight(final WriterBase writerBase) {
		writerBase.writeDouble("height", height);
	}

	protected void serializeWidth(final WriterBase writerBase) {
		writerBase.writeDouble("width", width);
	}

	protected void serializeDepth(final WriterBase writerBase) {
		writerBase.writeDouble("depth", depth);
	}

	protected void serializeOffsetX(final WriterBase writerBase) {
		writerBase.writeDouble("offsetX", offsetX);
	}

	protected void serializeOffsetY(final WriterBase writerBase) {
		writerBase.writeDouble("offsetY", offsetY);
	}

	protected void serializeOffsetZ(final WriterBase writerBase) {
		writerBase.writeDouble("offsetZ", offsetZ);
	}

	protected void serializeIsDoubleSided(final WriterBase writerBase) {
		writerBase.writeBoolean("isDoubleSided", isDoubleSided);
	}

	protected void serializeStyle(final WriterBase writerBase) {
		writerBase.writeString("style", style);
	}

	protected void serializeAngle(final WriterBase writerBase) {
		writerBase.writeString("angle", angle.toString());
	}

	protected void serializeRailProgress(final WriterBase writerBase) {
		writerBase.writeDouble("railProgress", railProgress);
	}

	protected void serializeSpeed(final WriterBase writerBase) {
		writerBase.writeDouble("speed", speed);
	}

	protected void serializeStoppingCoolDown(final WriterBase writerBase) {
		writerBase.writeLong("stoppingCoolDown", stoppingCoolDown);
	}

	protected void serializeFloors(final WriterBase writerBase) {
		writerBase.writeDataset(floors, "floors");
	}

	protected void serializeInstructions(final WriterBase writerBase) {
		writerBase.writeDataset(instructions, "instructions");
	}

	protected void serializeRidingEntities(final WriterBase writerBase) {
		writerBase.writeDataset(ridingEntities, "ridingEntities");
	}
}