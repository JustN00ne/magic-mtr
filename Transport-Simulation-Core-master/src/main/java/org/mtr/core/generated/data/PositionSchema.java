package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class PositionSchema implements SerializedDataBase {

	protected final long x;

	protected final long y;

	protected final long z;

	protected PositionSchema(final long x, final long y, final long z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	protected PositionSchema(final ReaderBase readerBase) {
		x = readerBase.getLong("x", 0);
		y = readerBase.getLong("y", 0);
		z = readerBase.getLong("z", 0);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("x", x);
		writerBase.writeLong("y", y);
		writerBase.writeLong("z", z);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "x: " + x + "\n"
			+ "y: " + y + "\n"
			+ "z: " + z + "\n"
		;
	}
}