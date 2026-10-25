package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class AreaBaseSchema extends NameColorDataBase {

	protected Position position1 = getDefaultPosition1();

	protected Position position2 = getDefaultPosition2();

	protected AreaBaseSchema(final TransportMode transportMode, final Data data) {
		super(transportMode, data);
	}

	protected AreaBaseSchema(final ReaderBase readerBase, final Data data) {
		super(readerBase, data);
	}

	public void updateData(final ReaderBase readerBase) {
		super.updateData(readerBase);
		readerBase.unpackChild("position1", readerBaseChild -> position1 = new Position(readerBaseChild));
		readerBase.unpackChild("position2", readerBaseChild -> position2 = new Position(readerBaseChild));
	}

	public void serializeData(final WriterBase writerBase) {
		super.serializeData(writerBase);
		serializePosition1(writerBase);
		serializePosition2(writerBase);
	}

	@Nonnull
	public String toString() {
		return super.toString()
			+ "position1: " + position1 + "\n"
			+ "position2: " + position2 + "\n"
		;
	}

	protected abstract Position getDefaultPosition1();

	protected void serializePosition1(final WriterBase writerBase) {
		if (position1 != null) position1.serializeData(writerBase.writeChild("position1"));
	}

	protected abstract Position getDefaultPosition2();

	protected void serializePosition2(final WriterBase writerBase) {
		if (position2 != null) position2.serializeData(writerBase.writeChild("position2"));
	}
}