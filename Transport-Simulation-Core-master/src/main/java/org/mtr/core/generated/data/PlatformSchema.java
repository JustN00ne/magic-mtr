package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class PlatformSchema extends SavedRailBase<Platform, Station> {

	protected long dwellTime = 10000;

	protected PlatformSchema(final Position position1, final Position position2, final TransportMode transportMode, final Data data) {
		super(position1, position2, transportMode, data);
	}

	protected PlatformSchema(final ReaderBase readerBase, final Data data) {
		super(readerBase, data);
	}

	public void updateData(final ReaderBase readerBase) {
		super.updateData(readerBase);
		readerBase.unpackLong("dwellTime", value -> dwellTime = value);
	}

	public void serializeData(final WriterBase writerBase) {
		super.serializeData(writerBase);
		serializeDwellTime(writerBase);
	}

	@Nonnull
	public String toString() {
		return super.toString()
			+ "dwellTime: " + dwellTime + "\n"
		;
	}

	protected void serializeDwellTime(final WriterBase writerBase) {
		writerBase.writeLong("dwellTime", dwellTime);
	}
}