package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class SignalModificationSchema extends TwoPositionsBase {

	protected final Position position1;

	protected final Position position2;

	protected final it.unimi.dsi.fastutil.longs.LongArrayList signalColorsAdd = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList signalColorsRemove = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final boolean clearAll;

	protected SignalModificationSchema(final Position position1, final Position position2, final boolean clearAll) {
		this.position1 = position1;
		this.position2 = position2;
		this.clearAll = clearAll;
	}

	protected SignalModificationSchema(final ReaderBase readerBase) {
		position1 = new Position(readerBase.getChild("position1"));
		position2 = new Position(readerBase.getChild("position2"));
		clearAll = readerBase.getBoolean("clearAll", false);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateLongArray("signalColorsAdd", signalColorsAdd::clear, signalColorsAdd::add);
		readerBase.iterateLongArray("signalColorsRemove", signalColorsRemove::clear, signalColorsRemove::add);
	}

	public void serializeData(final WriterBase writerBase) {
		if (position1 != null) position1.serializeData(writerBase.writeChild("position1"));
		if (position2 != null) position2.serializeData(writerBase.writeChild("position2"));
		serializeSignalColorsAdd(writerBase);
		serializeSignalColorsRemove(writerBase);
		writerBase.writeBoolean("clearAll", clearAll);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "position1: " + position1 + "\n"
			+ "position2: " + position2 + "\n"
			+ "signalColorsAdd: " + signalColorsAdd + "\n"
			+ "signalColorsRemove: " + signalColorsRemove + "\n"
			+ "clearAll: " + clearAll + "\n"
		;
	}

	protected void serializeSignalColorsAdd(final WriterBase writerBase) {
		final WriterBase.Array signalColorsAddWriterBaseArray = writerBase.writeArray("signalColorsAdd"); signalColorsAdd.forEach(signalColorsAddWriterBaseArray::writeLong);
	}

	protected void serializeSignalColorsRemove(final WriterBase writerBase) {
		final WriterBase.Array signalColorsRemoveWriterBaseArray = writerBase.writeArray("signalColorsRemove"); signalColorsRemove.forEach(signalColorsRemoveWriterBaseArray::writeLong);
	}
}