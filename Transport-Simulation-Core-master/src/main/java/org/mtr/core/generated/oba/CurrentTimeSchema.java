package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class CurrentTimeSchema implements SerializedDataBase {

	protected final References references;

	protected final Time time;

	protected CurrentTimeSchema(final References references, final Time time) {
		this.references = references;
		this.time = time;
	}

	protected CurrentTimeSchema(final ReaderBase readerBase) {
		references = new References(readerBase.getChild("references"));
		time = new Time(readerBase.getChild("time"));
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		if (references != null) references.serializeData(writerBase.writeChild("references"));
		if (time != null) time.serializeData(writerBase.writeChild("time"));
	}

	@Nonnull
	public String toString() {
		return ""
			+ "references: " + references + "\n"
			+ "time: " + time + "\n"
		;
	}
}