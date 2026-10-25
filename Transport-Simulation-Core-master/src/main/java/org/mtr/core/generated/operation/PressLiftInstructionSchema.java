package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class PressLiftInstructionSchema implements SerializedDataBase {

	protected final Position position;

	protected final LiftDirection direction;

	protected PressLiftInstructionSchema(final Position position, final LiftDirection direction) {
		this.position = position;
		this.direction = direction;
	}

	protected PressLiftInstructionSchema(final ReaderBase readerBase) {
		position = new Position(readerBase.getChild("position"));
		direction = EnumHelper.valueOf(LiftDirection.values()[0], readerBase.getString("direction", ""));
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		if (position != null) position.serializeData(writerBase.writeChild("position"));
		writerBase.writeString("direction", direction.toString());
	}

	@Nonnull
	public String toString() {
		return ""
			+ "position: " + position + "\n"
			+ "direction: " + direction + "\n"
		;
	}
}