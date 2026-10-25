package org.mtr.legacy.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.legacy.data.*;

public abstract class RailNodeSchema implements SerializedDataBaseWithId {

	protected long node_pos;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<RailNodeConnection> rail_connections = new it.unimi.dsi.fastutil.objects.ObjectArrayList<RailNodeConnection>();

	protected RailNodeSchema() {
	}

	protected RailNodeSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackLong("node_pos", value -> node_pos = value);
		readerBase.iterateReaderArray("rail_connections", rail_connections::clear, readerBaseChild -> rail_connections.add(new RailNodeConnection(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		serializeNode_pos(writerBase);
		serializeRail_connections(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "node_pos: " + node_pos + "\n"
			+ "rail_connections: " + rail_connections + "\n"
		;
	}

	protected void serializeNode_pos(final WriterBase writerBase) {
		writerBase.writeLong("node_pos", node_pos);
	}

	protected void serializeRail_connections(final WriterBase writerBase) {
		writerBase.writeDataset(rail_connections, "rail_connections");
	}
}