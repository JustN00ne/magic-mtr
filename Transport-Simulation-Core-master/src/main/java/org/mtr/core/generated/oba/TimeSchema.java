package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class TimeSchema implements SerializedDataBase {

	protected long time;

	protected final String readableTime;

	protected TimeSchema(final String readableTime) {
		this.readableTime = readableTime;
	}

	protected TimeSchema(final ReaderBase readerBase) {
		readableTime = readerBase.getString("readableTime", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackLong("time", value -> time = value);
	}

	public void serializeData(final WriterBase writerBase) {
		serializeTime(writerBase);
		writerBase.writeString("readableTime", readableTime);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "time: " + time + "\n"
			+ "readableTime: " + readableTime + "\n"
		;
	}

	protected void serializeTime(final WriterBase writerBase) {
		writerBase.writeLong("time", time);
	}
}