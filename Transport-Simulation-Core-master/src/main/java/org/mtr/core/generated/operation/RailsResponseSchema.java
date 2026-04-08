package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class RailsResponseSchema implements SerializedDataBase {

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Rail> rails = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Rail>();

	protected RailsResponseSchema() {
	}

	protected RailsResponseSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("rails", rails::clear, readerBaseChild -> rails.add(new Rail(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		serializeRails(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "rails: " + rails + "\n"
		;
	}

	protected void serializeRails(final WriterBase writerBase) {
		writerBase.writeDataset(rails, "rails");
	}
}