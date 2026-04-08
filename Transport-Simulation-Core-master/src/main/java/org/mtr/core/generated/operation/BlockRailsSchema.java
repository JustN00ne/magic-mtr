package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class BlockRailsSchema implements SerializedDataBase {

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> railIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList signalColors = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected BlockRailsSchema() {
	}

	protected BlockRailsSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateStringArray("railIds", railIds::clear, railIds::add);
		readerBase.iterateLongArray("signalColors", signalColors::clear, signalColors::add);
	}

	public void serializeData(final WriterBase writerBase) {
		serializeRailIds(writerBase);
		serializeSignalColors(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "railIds: " + railIds + "\n"
			+ "signalColors: " + signalColors + "\n"
		;
	}

	protected void serializeRailIds(final WriterBase writerBase) {
		final WriterBase.Array railIdsWriterBaseArray = writerBase.writeArray("railIds"); railIds.forEach(railIdsWriterBaseArray::writeString);
	}

	protected void serializeSignalColors(final WriterBase writerBase) {
		final WriterBase.Array signalColorsWriterBaseArray = writerBase.writeArray("signalColors"); signalColors.forEach(signalColorsWriterBaseArray::writeLong);
	}
}