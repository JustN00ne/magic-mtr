package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class ShapeSchema implements SerializedDataBase {

	protected final String shape;

	protected final long length;

	protected ShapeSchema(final String shape, final long length) {
		this.shape = shape;
		this.length = length;
	}

	protected ShapeSchema(final ReaderBase readerBase) {
		shape = readerBase.getString("shape", "");
		length = readerBase.getLong("length", 0);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("shape", shape);
		writerBase.writeLong("length", length);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "shape: " + shape + "\n"
			+ "length: " + length + "\n"
		;
	}
}