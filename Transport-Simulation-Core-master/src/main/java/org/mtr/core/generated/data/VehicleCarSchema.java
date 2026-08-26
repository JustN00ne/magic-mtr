package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class VehicleCarSchema implements SerializedDataBase {

	protected final String vehicleId;

	protected final double length;

	protected final double width;

	protected final double bogie1Position;

	protected final double bogie2Position;

	protected final double couplingPadding1;

	protected final double couplingPadding2;

	protected VehicleCarSchema(final String vehicleId, final double length, final double width, final double bogie1Position, final double bogie2Position, final double couplingPadding1, final double couplingPadding2) {
		this.vehicleId = vehicleId;
		this.length = length;
		this.width = width;
		this.bogie1Position = bogie1Position;
		this.bogie2Position = bogie2Position;
		this.couplingPadding1 = couplingPadding1;
		this.couplingPadding2 = couplingPadding2;
	}

	protected VehicleCarSchema(final ReaderBase readerBase) {
		vehicleId = readerBase.getString("vehicleId", "");
		length = readerBase.getDouble("length", 0);
		width = readerBase.getDouble("width", 0);
		bogie1Position = readerBase.getDouble("bogie1Position", 0);
		bogie2Position = readerBase.getDouble("bogie2Position", 0);
		couplingPadding1 = readerBase.getDouble("couplingPadding1", 0);
		couplingPadding2 = readerBase.getDouble("couplingPadding2", 0);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("vehicleId", vehicleId);
		writerBase.writeDouble("length", length);
		writerBase.writeDouble("width", width);
		writerBase.writeDouble("bogie1Position", bogie1Position);
		writerBase.writeDouble("bogie2Position", bogie2Position);
		writerBase.writeDouble("couplingPadding1", couplingPadding1);
		writerBase.writeDouble("couplingPadding2", couplingPadding2);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "vehicleId: " + vehicleId + "\n"
			+ "length: " + length + "\n"
			+ "width: " + width + "\n"
			+ "bogie1Position: " + bogie1Position + "\n"
			+ "bogie2Position: " + bogie2Position + "\n"
			+ "couplingPadding1: " + couplingPadding1 + "\n"
			+ "couplingPadding2: " + couplingPadding2 + "\n"
		;
	}
}