package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class ListElementSchema extends ReferencesBase {

	protected boolean limitedExceeded;

	protected final boolean outOfRange;

	protected ListElementSchema(final boolean outOfRange, final References references) {
		super(references);
		this.outOfRange = outOfRange;
	}

	protected ListElementSchema(final ReaderBase readerBase) {
		super(readerBase);
		outOfRange = readerBase.getBoolean("outOfRange", false);
	}

	public void updateData(final ReaderBase readerBase) {
		super.updateData(readerBase);
		readerBase.unpackBoolean("limitedExceeded", value -> limitedExceeded = value);
	}

	public void serializeData(final WriterBase writerBase) {
		super.serializeData(writerBase);
		serializeLimitedExceeded(writerBase);
		writerBase.writeBoolean("outOfRange", outOfRange);
	}

	@Nonnull
	public String toString() {
		return super.toString()
			+ "limitedExceeded: " + limitedExceeded + "\n"
			+ "outOfRange: " + outOfRange + "\n"
		;
	}

	protected void serializeLimitedExceeded(final WriterBase writerBase) {
		writerBase.writeBoolean("limitedExceeded", limitedExceeded);
	}
}