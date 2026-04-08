package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class SignalBlockUpdateSchema implements SerializedDataBase {

	protected final String railId;

	protected final it.unimi.dsi.fastutil.longs.LongArrayList preBlockedSignalColors = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList currentlyBlockedSignalColors = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected SignalBlockUpdateSchema(final String railId) {
		this.railId = railId;
	}

	protected SignalBlockUpdateSchema(final ReaderBase readerBase) {
		railId = readerBase.getString("railId", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateLongArray("preBlockedSignalColors", preBlockedSignalColors::clear, preBlockedSignalColors::add);
		readerBase.iterateLongArray("currentlyBlockedSignalColors", currentlyBlockedSignalColors::clear, currentlyBlockedSignalColors::add);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("railId", railId);
		serializePreBlockedSignalColors(writerBase);
		serializeCurrentlyBlockedSignalColors(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "railId: " + railId + "\n"
			+ "preBlockedSignalColors: " + preBlockedSignalColors + "\n"
			+ "currentlyBlockedSignalColors: " + currentlyBlockedSignalColors + "\n"
		;
	}

	protected void serializePreBlockedSignalColors(final WriterBase writerBase) {
		final WriterBase.Array preBlockedSignalColorsWriterBaseArray = writerBase.writeArray("preBlockedSignalColors"); preBlockedSignalColors.forEach(preBlockedSignalColorsWriterBaseArray::writeLong);
	}

	protected void serializeCurrentlyBlockedSignalColors(final WriterBase writerBase) {
		final WriterBase.Array currentlyBlockedSignalColorsWriterBaseArray = writerBase.writeArray("currentlyBlockedSignalColors"); currentlyBlockedSignalColors.forEach(currentlyBlockedSignalColorsWriterBaseArray::writeLong);
	}
}