package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class DepotOperationByNameSchema implements SerializedDataBase {

	protected String filter = "";

	protected DepotOperationByNameSchema() {
	}

	protected DepotOperationByNameSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackString("filter", value -> filter = value);
	}

	public void serializeData(final WriterBase writerBase) {
		serializeFilter(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "filter: " + filter + "\n"
		;
	}

	protected void serializeFilter(final WriterBase writerBase) {
		writerBase.writeString("filter", filter);
	}
}