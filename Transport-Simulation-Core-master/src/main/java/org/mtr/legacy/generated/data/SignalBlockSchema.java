package org.mtr.legacy.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.legacy.data.*;

public abstract class SignalBlockSchema implements SerializedDataBaseWithId {

	protected long color;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> rails = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected SignalBlockSchema() {
	}

	protected SignalBlockSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackLong("color", value -> color = value);
		readerBase.iterateStringArray("rails", rails::clear, rails::add);
	}

	public void serializeData(final WriterBase writerBase) {
		serializeColor(writerBase);
		serializeRails(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "color: " + color + "\n"
			+ "rails: " + rails + "\n"
		;
	}

	protected void serializeColor(final WriterBase writerBase) {
		writerBase.writeLong("color", color);
	}

	protected void serializeRails(final WriterBase writerBase) {
		final WriterBase.Array railsWriterBaseArray = writerBase.writeArray("rails"); rails.forEach(railsWriterBaseArray::writeString);
	}
}