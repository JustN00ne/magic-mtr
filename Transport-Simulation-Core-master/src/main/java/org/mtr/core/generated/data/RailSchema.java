package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class RailSchema extends TwoPositionsBase {

	protected final Position position1;

	protected final Angle angle1;

	protected final Position position2;

	protected final Angle angle2;

	protected final Rail.Shape shape;

	protected final double verticalRadius;

	protected final long tiltPoints;

	protected final double tiltAngleDegrees1;

	protected final double tiltAngleDistance1a;

	protected final double tiltAngleDegrees1a;

	protected final double tiltAngleDegrees1b;

	protected final double tiltAngleDistance1b;

	protected final double tiltAngleDegreesMiddle;

	protected final double tiltAngleDistance2b;

	protected final double tiltAngleDegrees2b;

	protected final double tiltAngleDegrees2a;

	protected final double tiltAngleDistance2a;

	protected final double tiltAngleDegrees2;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> styles = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected final long speedLimit1;

	protected final long speedLimit2;

	protected final boolean isPlatform;

	protected final boolean isSiding;

	protected final boolean canAccelerate;

	protected final boolean canTurnBack;

	protected final boolean canConnectRemotely;

	protected final boolean canHaveSignal;

	protected final it.unimi.dsi.fastutil.longs.LongArrayList signalColors = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final TransportMode transportMode;

	protected boolean stylesMigratedLegacy;

	protected RailSchema(final Position position1, final Angle angle1, final Position position2, final Angle angle2, final Rail.Shape shape, final double verticalRadius, final long tiltPoints, final double tiltAngleDegrees1, final double tiltAngleDistance1a, final double tiltAngleDegrees1a, final double tiltAngleDegrees1b, final double tiltAngleDistance1b, final double tiltAngleDegreesMiddle, final double tiltAngleDistance2b, final double tiltAngleDegrees2b, final double tiltAngleDegrees2a, final double tiltAngleDistance2a, final double tiltAngleDegrees2, final long speedLimit1, final long speedLimit2, final boolean isPlatform, final boolean isSiding, final boolean canAccelerate, final boolean canTurnBack, final boolean canConnectRemotely, final boolean canHaveSignal, final TransportMode transportMode) {
		this.position1 = position1;
		this.angle1 = angle1;
		this.position2 = position2;
		this.angle2 = angle2;
		this.shape = shape;
		this.verticalRadius = verticalRadius;
		this.tiltPoints = tiltPoints;
		this.tiltAngleDegrees1 = tiltAngleDegrees1;
		this.tiltAngleDistance1a = tiltAngleDistance1a;
		this.tiltAngleDegrees1a = tiltAngleDegrees1a;
		this.tiltAngleDegrees1b = tiltAngleDegrees1b;
		this.tiltAngleDistance1b = tiltAngleDistance1b;
		this.tiltAngleDegreesMiddle = tiltAngleDegreesMiddle;
		this.tiltAngleDistance2b = tiltAngleDistance2b;
		this.tiltAngleDegrees2b = tiltAngleDegrees2b;
		this.tiltAngleDegrees2a = tiltAngleDegrees2a;
		this.tiltAngleDistance2a = tiltAngleDistance2a;
		this.tiltAngleDegrees2 = tiltAngleDegrees2;
		this.speedLimit1 = speedLimit1;
		this.speedLimit2 = speedLimit2;
		this.isPlatform = isPlatform;
		this.isSiding = isSiding;
		this.canAccelerate = canAccelerate;
		this.canTurnBack = canTurnBack;
		this.canConnectRemotely = canConnectRemotely;
		this.canHaveSignal = canHaveSignal;
		this.transportMode = transportMode;
	}

	protected RailSchema(final ReaderBase readerBase) {
		position1 = new Position(readerBase.getChild("position1"));
		angle1 = EnumHelper.valueOf(Angle.values()[0], readerBase.getString("angle1", ""));
		position2 = new Position(readerBase.getChild("position2"));
		angle2 = EnumHelper.valueOf(Angle.values()[0], readerBase.getString("angle2", ""));
		shape = EnumHelper.valueOf(Rail.Shape.values()[0], readerBase.getString("shape", ""));
		verticalRadius = readerBase.getDouble("verticalRadius", 0);
		tiltPoints = readerBase.getLong("tiltPoints", 0);
		tiltAngleDegrees1 = readerBase.getDouble("tiltAngleDegrees1", 0);
		tiltAngleDistance1a = readerBase.getDouble("tiltAngleDistance1a", 0);
		tiltAngleDegrees1a = readerBase.getDouble("tiltAngleDegrees1a", 0);
		tiltAngleDegrees1b = readerBase.getDouble("tiltAngleDegrees1b", 0);
		tiltAngleDistance1b = readerBase.getDouble("tiltAngleDistance1b", 0);
		tiltAngleDegreesMiddle = readerBase.getDouble("tiltAngleDegreesMiddle", 0);
		tiltAngleDistance2b = readerBase.getDouble("tiltAngleDistance2b", 0);
		tiltAngleDegrees2b = readerBase.getDouble("tiltAngleDegrees2b", 0);
		tiltAngleDegrees2a = readerBase.getDouble("tiltAngleDegrees2a", 0);
		tiltAngleDistance2a = readerBase.getDouble("tiltAngleDistance2a", 0);
		tiltAngleDegrees2 = readerBase.getDouble("tiltAngleDegrees2", 0);
		speedLimit1 = readerBase.getLong("speedLimit1", 0);
		speedLimit2 = readerBase.getLong("speedLimit2", 0);
		isPlatform = readerBase.getBoolean("isPlatform", false);
		isSiding = readerBase.getBoolean("isSiding", false);
		canAccelerate = readerBase.getBoolean("canAccelerate", false);
		canTurnBack = readerBase.getBoolean("canTurnBack", false);
		canConnectRemotely = readerBase.getBoolean("canConnectRemotely", false);
		canHaveSignal = readerBase.getBoolean("canHaveSignal", false);
		transportMode = EnumHelper.valueOf(TransportMode.values()[0], readerBase.getString("transportMode", ""));
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateStringArray("styles", styles::clear, styles::add);
		readerBase.iterateLongArray("signalColors", signalColors::clear, signalColors::add);
		readerBase.unpackBoolean("stylesMigratedLegacy", value -> stylesMigratedLegacy = value);
	}

	public void serializeData(final WriterBase writerBase) {
		if (position1 != null) position1.serializeData(writerBase.writeChild("position1"));
		writerBase.writeString("angle1", angle1.toString());
		if (position2 != null) position2.serializeData(writerBase.writeChild("position2"));
		writerBase.writeString("angle2", angle2.toString());
		writerBase.writeString("shape", shape.toString());
		writerBase.writeDouble("verticalRadius", verticalRadius);
		writerBase.writeLong("tiltPoints", tiltPoints);
		writerBase.writeDouble("tiltAngleDegrees1", tiltAngleDegrees1);
		writerBase.writeDouble("tiltAngleDistance1a", tiltAngleDistance1a);
		writerBase.writeDouble("tiltAngleDegrees1a", tiltAngleDegrees1a);
		writerBase.writeDouble("tiltAngleDegrees1b", tiltAngleDegrees1b);
		writerBase.writeDouble("tiltAngleDistance1b", tiltAngleDistance1b);
		writerBase.writeDouble("tiltAngleDegreesMiddle", tiltAngleDegreesMiddle);
		writerBase.writeDouble("tiltAngleDistance2b", tiltAngleDistance2b);
		writerBase.writeDouble("tiltAngleDegrees2b", tiltAngleDegrees2b);
		writerBase.writeDouble("tiltAngleDegrees2a", tiltAngleDegrees2a);
		writerBase.writeDouble("tiltAngleDistance2a", tiltAngleDistance2a);
		writerBase.writeDouble("tiltAngleDegrees2", tiltAngleDegrees2);
		serializeStyles(writerBase);
		writerBase.writeLong("speedLimit1", speedLimit1);
		writerBase.writeLong("speedLimit2", speedLimit2);
		writerBase.writeBoolean("isPlatform", isPlatform);
		writerBase.writeBoolean("isSiding", isSiding);
		writerBase.writeBoolean("canAccelerate", canAccelerate);
		writerBase.writeBoolean("canTurnBack", canTurnBack);
		writerBase.writeBoolean("canConnectRemotely", canConnectRemotely);
		writerBase.writeBoolean("canHaveSignal", canHaveSignal);
		serializeSignalColors(writerBase);
		writerBase.writeString("transportMode", transportMode.toString());
		serializeStylesMigratedLegacy(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "position1: " + position1 + "\n"
			+ "angle1: " + angle1 + "\n"
			+ "position2: " + position2 + "\n"
			+ "angle2: " + angle2 + "\n"
			+ "shape: " + shape + "\n"
			+ "verticalRadius: " + verticalRadius + "\n"
			+ "tiltPoints: " + tiltPoints + "\n"
			+ "tiltAngleDegrees1: " + tiltAngleDegrees1 + "\n"
			+ "tiltAngleDistance1a: " + tiltAngleDistance1a + "\n"
			+ "tiltAngleDegrees1a: " + tiltAngleDegrees1a + "\n"
			+ "tiltAngleDegrees1b: " + tiltAngleDegrees1b + "\n"
			+ "tiltAngleDistance1b: " + tiltAngleDistance1b + "\n"
			+ "tiltAngleDegreesMiddle: " + tiltAngleDegreesMiddle + "\n"
			+ "tiltAngleDistance2b: " + tiltAngleDistance2b + "\n"
			+ "tiltAngleDegrees2b: " + tiltAngleDegrees2b + "\n"
			+ "tiltAngleDegrees2a: " + tiltAngleDegrees2a + "\n"
			+ "tiltAngleDistance2a: " + tiltAngleDistance2a + "\n"
			+ "tiltAngleDegrees2: " + tiltAngleDegrees2 + "\n"
			+ "styles: " + styles + "\n"
			+ "speedLimit1: " + speedLimit1 + "\n"
			+ "speedLimit2: " + speedLimit2 + "\n"
			+ "isPlatform: " + isPlatform + "\n"
			+ "isSiding: " + isSiding + "\n"
			+ "canAccelerate: " + canAccelerate + "\n"
			+ "canTurnBack: " + canTurnBack + "\n"
			+ "canConnectRemotely: " + canConnectRemotely + "\n"
			+ "canHaveSignal: " + canHaveSignal + "\n"
			+ "signalColors: " + signalColors + "\n"
			+ "transportMode: " + transportMode + "\n"
			+ "stylesMigratedLegacy: " + stylesMigratedLegacy + "\n"
		;
	}

	protected void serializeStyles(final WriterBase writerBase) {
		final WriterBase.Array stylesWriterBaseArray = writerBase.writeArray("styles"); styles.forEach(stylesWriterBaseArray::writeString);
	}

	protected void serializeSignalColors(final WriterBase writerBase) {
		final WriterBase.Array signalColorsWriterBaseArray = writerBase.writeArray("signalColors"); signalColors.forEach(signalColorsWriterBaseArray::writeLong);
	}

	protected void serializeStylesMigratedLegacy(final WriterBase writerBase) {
		writerBase.writeBoolean("stylesMigratedLegacy", stylesMigratedLegacy);
	}
}