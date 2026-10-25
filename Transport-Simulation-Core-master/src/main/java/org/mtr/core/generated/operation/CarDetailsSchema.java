package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class CarDetailsSchema implements SerializedDataBase {

	protected final String vehicleId;

	protected final double occupancy;

	protected CarDetailsSchema(final String vehicleId, final double occupancy) {
		this.vehicleId = vehicleId;
		this.occupancy = occupancy;
	}

	protected CarDetailsSchema(final ReaderBase readerBase) {
		vehicleId = readerBase.getString("vehicleId", "");
		occupancy = readerBase.getDouble("occupancy", 0);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("vehicleId", vehicleId);
		writerBase.writeDouble("occupancy", occupancy);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "vehicleId: " + vehicleId + "\n"
			+ "occupancy: " + occupancy + "\n"
		;
	}
}