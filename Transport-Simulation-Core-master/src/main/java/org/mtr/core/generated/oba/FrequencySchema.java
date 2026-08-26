package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class FrequencySchema implements SerializedDataBase {

	protected final long startTime;

	protected final long endTime;

	protected final long headway;

	protected FrequencySchema(final long startTime, final long endTime, final long headway) {
		this.startTime = startTime;
		this.endTime = endTime;
		this.headway = headway;
	}

	protected FrequencySchema(final ReaderBase readerBase) {
		startTime = readerBase.getLong("startTime", 0);
		endTime = readerBase.getLong("endTime", 0);
		headway = readerBase.getLong("headway", 0);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("startTime", startTime);
		writerBase.writeLong("endTime", endTime);
		writerBase.writeLong("headway", headway);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "startTime: " + startTime + "\n"
			+ "endTime: " + endTime + "\n"
			+ "headway: " + headway + "\n"
		;
	}
}