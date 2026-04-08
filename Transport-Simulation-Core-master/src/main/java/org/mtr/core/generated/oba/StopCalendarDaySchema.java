package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class StopCalendarDaySchema implements SerializedDataBase {

	protected final long date;

	protected final long group;

	protected StopCalendarDaySchema(final long date, final long group) {
		this.date = date;
		this.group = group;
	}

	protected StopCalendarDaySchema(final ReaderBase readerBase) {
		date = readerBase.getLong("date", 0);
		group = readerBase.getLong("group", 0);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("date", date);
		writerBase.writeLong("group", group);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "date: " + date + "\n"
			+ "group: " + group + "\n"
		;
	}
}