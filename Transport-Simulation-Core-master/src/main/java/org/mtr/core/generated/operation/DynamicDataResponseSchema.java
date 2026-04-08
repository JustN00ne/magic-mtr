package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class DynamicDataResponseSchema implements SerializedDataBase {

	protected final String clientId;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<VehicleUpdate> vehiclesToUpdate = new it.unimi.dsi.fastutil.objects.ObjectArrayList<VehicleUpdate>();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList vehiclesToKeep = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Lift> liftsToUpdate = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Lift>();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList liftsToKeep = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Passenger> passengersToUpdate = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Passenger>();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList passengersToKeep = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<SignalBlockUpdate> signalBlockUpdates = new it.unimi.dsi.fastutil.objects.ObjectArrayList<SignalBlockUpdate>();

	protected DynamicDataResponseSchema(final String clientId) {
		this.clientId = clientId;
	}

	protected DynamicDataResponseSchema(final ReaderBase readerBase) {
		clientId = readerBase.getString("clientId", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("vehiclesToUpdate", vehiclesToUpdate::clear, readerBaseChild -> vehiclesToUpdate.add(new VehicleUpdate(readerBaseChild)));
		readerBase.iterateLongArray("vehiclesToKeep", vehiclesToKeep::clear, vehiclesToKeep::add);
		readerBase.iterateReaderArray("liftsToUpdate", liftsToUpdate::clear, readerBaseChild -> liftsToUpdate.add(new Lift(readerBaseChild, liftsToUpdateDataParameter())));
		readerBase.iterateLongArray("liftsToKeep", liftsToKeep::clear, liftsToKeep::add);
		readerBase.iterateReaderArray("passengersToUpdate", passengersToUpdate::clear, readerBaseChild -> passengersToUpdate.add(new Passenger(readerBaseChild)));
		readerBase.iterateLongArray("passengersToKeep", passengersToKeep::clear, passengersToKeep::add);
		readerBase.iterateReaderArray("signalBlockUpdates", signalBlockUpdates::clear, readerBaseChild -> signalBlockUpdates.add(new SignalBlockUpdate(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("clientId", clientId);
		serializeVehiclesToUpdate(writerBase);
		serializeVehiclesToKeep(writerBase);
		serializeLiftsToUpdate(writerBase);
		serializeLiftsToKeep(writerBase);
		serializePassengersToUpdate(writerBase);
		serializePassengersToKeep(writerBase);
		serializeSignalBlockUpdates(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "clientId: " + clientId + "\n"
			+ "vehiclesToUpdate: " + vehiclesToUpdate + "\n"
			+ "vehiclesToKeep: " + vehiclesToKeep + "\n"
			+ "liftsToUpdate: " + liftsToUpdate + "\n"
			+ "liftsToKeep: " + liftsToKeep + "\n"
			+ "passengersToUpdate: " + passengersToUpdate + "\n"
			+ "passengersToKeep: " + passengersToKeep + "\n"
			+ "signalBlockUpdates: " + signalBlockUpdates + "\n"
		;
	}

	protected void serializeVehiclesToUpdate(final WriterBase writerBase) {
		writerBase.writeDataset(vehiclesToUpdate, "vehiclesToUpdate");
	}

	protected void serializeVehiclesToKeep(final WriterBase writerBase) {
		final WriterBase.Array vehiclesToKeepWriterBaseArray = writerBase.writeArray("vehiclesToKeep"); vehiclesToKeep.forEach(vehiclesToKeepWriterBaseArray::writeLong);
	}

	protected void serializeLiftsToUpdate(final WriterBase writerBase) {
		writerBase.writeDataset(liftsToUpdate, "liftsToUpdate");
	}

	@Nonnull
	protected abstract Data liftsToUpdateDataParameter();

	protected void serializeLiftsToKeep(final WriterBase writerBase) {
		final WriterBase.Array liftsToKeepWriterBaseArray = writerBase.writeArray("liftsToKeep"); liftsToKeep.forEach(liftsToKeepWriterBaseArray::writeLong);
	}

	protected void serializePassengersToUpdate(final WriterBase writerBase) {
		writerBase.writeDataset(passengersToUpdate, "passengersToUpdate");
	}

	protected void serializePassengersToKeep(final WriterBase writerBase) {
		final WriterBase.Array passengersToKeepWriterBaseArray = writerBase.writeArray("passengersToKeep"); passengersToKeep.forEach(passengersToKeepWriterBaseArray::writeLong);
	}

	protected void serializeSignalBlockUpdates(final WriterBase writerBase) {
		writerBase.writeDataset(signalBlockUpdates, "signalBlockUpdates");
	}
}