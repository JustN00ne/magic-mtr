package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class NearbyAreasRequestSchema implements SerializedDataBase {

	protected final Position position;

	protected final long radius;

	protected NearbyAreasRequestSchema(final Position position, final long radius) {
		this.position = position;
		this.radius = radius;
	}

	protected NearbyAreasRequestSchema(final ReaderBase readerBase) {
		position = new Position(readerBase.getChild("position"));
		radius = readerBase.getLong("radius", 0);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		if (position != null) position.serializeData(writerBase.writeChild("position"));
		writerBase.writeLong("radius", radius);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "position: " + position + "\n"
			+ "radius: " + radius + "\n"
		;
	}
}