package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class BlockConfigurationSchema implements SerializedDataBase {

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> activeServiceIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<BlockTrip> inactiveServiceIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<BlockTrip>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> trips = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected BlockConfigurationSchema() {
	}

	protected BlockConfigurationSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateStringArray("activeServiceIds", activeServiceIds::clear, activeServiceIds::add);
		readerBase.iterateReaderArray("inactiveServiceIds", inactiveServiceIds::clear, readerBaseChild -> inactiveServiceIds.add(new BlockTrip(readerBaseChild)));
		readerBase.iterateStringArray("trips", trips::clear, trips::add);
	}

	public void serializeData(final WriterBase writerBase) {
		serializeActiveServiceIds(writerBase);
		serializeInactiveServiceIds(writerBase);
		serializeTrips(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "activeServiceIds: " + activeServiceIds + "\n"
			+ "inactiveServiceIds: " + inactiveServiceIds + "\n"
			+ "trips: " + trips + "\n"
		;
	}

	protected void serializeActiveServiceIds(final WriterBase writerBase) {
		final WriterBase.Array activeServiceIdsWriterBaseArray = writerBase.writeArray("activeServiceIds"); activeServiceIds.forEach(activeServiceIdsWriterBaseArray::writeString);
	}

	protected void serializeInactiveServiceIds(final WriterBase writerBase) {
		writerBase.writeDataset(inactiveServiceIds, "inactiveServiceIds");
	}

	protected void serializeTrips(final WriterBase writerBase) {
		final WriterBase.Array tripsWriterBaseArray = writerBase.writeArray("trips"); trips.forEach(tripsWriterBaseArray::writeString);
	}
}