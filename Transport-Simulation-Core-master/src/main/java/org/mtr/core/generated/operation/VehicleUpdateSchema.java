package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class VehicleUpdateSchema implements SerializedDataBase {

	protected final Vehicle vehicle;

	protected final VehicleExtraData data;

	protected VehicleUpdateSchema(final Vehicle vehicle, final VehicleExtraData data) {
		this.vehicle = vehicle;
		this.data = data;
	}

	protected VehicleUpdateSchema(final ReaderBase readerBase) {
		vehicle = new Vehicle(readerBase.getChild("vehicle"));
		data = new VehicleExtraData(readerBase.getChild("data"));
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		if (vehicle != null) vehicle.serializeData(writerBase.writeChild("vehicle"));
		if (data != null) data.serializeData(writerBase.writeChild("data"));
	}

	@Nonnull
	public String toString() {
		return ""
			+ "vehicle: " + vehicle + "\n"
			+ "data: " + data + "\n"
		;
	}
}