package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class SetTimeSchema implements SerializedDataBase {

	protected final long gameMillis;

	protected final long millisPerDay;

	protected final boolean isTimeMoving;

	protected SetTimeSchema(final long gameMillis, final long millisPerDay, final boolean isTimeMoving) {
		this.gameMillis = gameMillis;
		this.millisPerDay = millisPerDay;
		this.isTimeMoving = isTimeMoving;
	}

	protected SetTimeSchema(final ReaderBase readerBase) {
		gameMillis = readerBase.getLong("gameMillis", 0);
		millisPerDay = readerBase.getLong("millisPerDay", 0);
		isTimeMoving = readerBase.getBoolean("isTimeMoving", false);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("gameMillis", gameMillis);
		writerBase.writeLong("millisPerDay", millisPerDay);
		writerBase.writeBoolean("isTimeMoving", isTimeMoving);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "gameMillis: " + gameMillis + "\n"
			+ "millisPerDay: " + millisPerDay + "\n"
			+ "isTimeMoving: " + isTimeMoving + "\n"
		;
	}
}