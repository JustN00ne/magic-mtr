package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class LiftFloorSchema implements SerializedDataBase {

	protected final Position position;

	protected String number = "";

	protected String description = "";

	protected LiftFloorSchema(final Position position) {
		this.position = position;
	}

	protected LiftFloorSchema(final ReaderBase readerBase) {
		position = new Position(readerBase.getChild("position"));
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackString("number", value -> number = value);
		readerBase.unpackString("description", value -> description = value);
	}

	public void serializeData(final WriterBase writerBase) {
		if (position != null) position.serializeData(writerBase.writeChild("position"));
		serializeNumber(writerBase);
		serializeDescription(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "position: " + position + "\n"
			+ "number: " + number + "\n"
			+ "description: " + description + "\n"
		;
	}

	protected void serializeNumber(final WriterBase writerBase) {
		writerBase.writeString("number", number);
	}

	protected void serializeDescription(final WriterBase writerBase) {
		writerBase.writeString("description", description);
	}
}