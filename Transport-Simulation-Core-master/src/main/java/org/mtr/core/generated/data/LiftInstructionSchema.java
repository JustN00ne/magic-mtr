package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class LiftInstructionSchema implements SerializedDataBase {

	protected final long floor;

	protected final LiftDirection direction;

	protected LiftInstructionSchema(final long floor, final LiftDirection direction) {
		this.floor = floor;
		this.direction = direction;
	}

	protected LiftInstructionSchema(final ReaderBase readerBase) {
		floor = readerBase.getLong("floor", 0);
		direction = EnumHelper.valueOf(LiftDirection.values()[0], readerBase.getString("direction", ""));
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("floor", floor);
		writerBase.writeString("direction", direction.toString());
	}

	@Nonnull
	public String toString() {
		return ""
			+ "floor: " + floor + "\n"
			+ "direction: " + direction + "\n"
		;
	}
}