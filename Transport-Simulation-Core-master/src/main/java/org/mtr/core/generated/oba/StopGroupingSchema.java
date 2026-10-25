package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class StopGroupingSchema implements SerializedDataBase {

	protected final String type;

	protected final boolean ordered;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<StopGroup> stopGroups = new it.unimi.dsi.fastutil.objects.ObjectArrayList<StopGroup>();

	protected StopGroupingSchema(final String type, final boolean ordered) {
		this.type = type;
		this.ordered = ordered;
	}

	protected StopGroupingSchema(final ReaderBase readerBase) {
		type = readerBase.getString("type", "");
		ordered = readerBase.getBoolean("ordered", false);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("stopGroups", stopGroups::clear, readerBaseChild -> stopGroups.add(new StopGroup(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("type", type);
		writerBase.writeBoolean("ordered", ordered);
		serializeStopGroups(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "type: " + type + "\n"
			+ "ordered: " + ordered + "\n"
			+ "stopGroups: " + stopGroups + "\n"
		;
	}

	protected void serializeStopGroups(final WriterBase writerBase) {
		writerBase.writeDataset(stopGroups, "stopGroups");
	}
}