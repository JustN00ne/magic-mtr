package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class UpdateVehicleRidingEntitiesSchema implements SerializedDataBase {

	protected final long sidingId;

	protected final long vehicleId;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<VehicleRidingEntity> ridingEntities = new it.unimi.dsi.fastutil.objects.ObjectArrayList<VehicleRidingEntity>();

	protected UpdateVehicleRidingEntitiesSchema(final long sidingId, final long vehicleId) {
		this.sidingId = sidingId;
		this.vehicleId = vehicleId;
	}

	protected UpdateVehicleRidingEntitiesSchema(final ReaderBase readerBase) {
		sidingId = readerBase.getLong("sidingId", 0);
		vehicleId = readerBase.getLong("vehicleId", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("ridingEntities", ridingEntities::clear, readerBaseChild -> ridingEntities.add(new VehicleRidingEntity(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("sidingId", sidingId);
		writerBase.writeLong("vehicleId", vehicleId);
		serializeRidingEntities(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "sidingId: " + sidingId + "\n"
			+ "vehicleId: " + vehicleId + "\n"
			+ "ridingEntities: " + ridingEntities + "\n"
		;
	}

	protected void serializeRidingEntities(final WriterBase writerBase) {
		writerBase.writeDataset(ridingEntities, "ridingEntities");
	}
}