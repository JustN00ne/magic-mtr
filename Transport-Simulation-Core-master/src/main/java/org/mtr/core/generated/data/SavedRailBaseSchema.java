package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class SavedRailBaseSchema extends NameColorDataBase {

	protected final Position position1;

	protected final Position position2;

	protected SavedRailBaseSchema(final Position position1, final Position position2, final TransportMode transportMode, final Data data) {
		super(transportMode, data);
		this.position1 = position1;
		this.position2 = position2;
	}

	protected SavedRailBaseSchema(final ReaderBase readerBase, final Data data) {
		super(readerBase, data);
		position1 = new Position(readerBase.getChild("position1"));
		position2 = new Position(readerBase.getChild("position2"));
	}

	public void updateData(final ReaderBase readerBase) {
		super.updateData(readerBase);
	}

	public void serializeData(final WriterBase writerBase) {
		super.serializeData(writerBase);
		if (position1 != null) position1.serializeData(writerBase.writeChild("position1"));
		if (position2 != null) position2.serializeData(writerBase.writeChild("position2"));
	}

	@Nonnull
	public String toString() {
		return super.toString()
			+ "position1: " + position1 + "\n"
			+ "position2: " + position2 + "\n"
		;
	}
}