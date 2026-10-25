package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class DepotOperationByIdsSchema implements SerializedDataBase {

	protected final it.unimi.dsi.fastutil.longs.LongArrayList depotIds = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected DepotOperationByIdsSchema() {
	}

	protected DepotOperationByIdsSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateLongArray("depotIds", depotIds::clear, depotIds::add);
	}

	public void serializeData(final WriterBase writerBase) {
		serializeDepotIds(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "depotIds: " + depotIds + "\n"
		;
	}

	protected void serializeDepotIds(final WriterBase writerBase) {
		final WriterBase.Array depotIdsWriterBaseArray = writerBase.writeArray("depotIds"); depotIds.forEach(depotIdsWriterBaseArray::writeLong);
	}
}