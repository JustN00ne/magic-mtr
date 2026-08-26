package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class NameSchema implements SerializedDataBase {

	protected final String type;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> names = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected NameSchema(final String type) {
		this.type = type;
	}

	protected NameSchema(final ReaderBase readerBase) {
		type = readerBase.getString("type", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateStringArray("names", names::clear, names::add);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("type", type);
		serializeNames(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "type: " + type + "\n"
			+ "names: " + names + "\n"
		;
	}

	protected void serializeNames(final WriterBase writerBase) {
		final WriterBase.Array namesWriterBaseArray = writerBase.writeArray("names"); names.forEach(namesWriterBaseArray::writeString);
	}
}