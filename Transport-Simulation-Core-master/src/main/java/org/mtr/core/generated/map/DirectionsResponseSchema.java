package org.mtr.core.generated.map;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.map.*;

public abstract class DirectionsResponseSchema implements SerializedDataBase {

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<DirectionsConnection> connections = new it.unimi.dsi.fastutil.objects.ObjectArrayList<DirectionsConnection>();

	protected final long totalRefreshGraphTime;

	protected final long totalRefreshArrivalsTime;

	protected final long totalPathFindingTime;

	protected final long longestRefreshGraphTime;

	protected final long longestRefreshArrivalsTime;

	protected final long longestPathFindingTime;

	protected DirectionsResponseSchema(final long totalRefreshGraphTime, final long totalRefreshArrivalsTime, final long totalPathFindingTime, final long longestRefreshGraphTime, final long longestRefreshArrivalsTime, final long longestPathFindingTime) {
		this.totalRefreshGraphTime = totalRefreshGraphTime;
		this.totalRefreshArrivalsTime = totalRefreshArrivalsTime;
		this.totalPathFindingTime = totalPathFindingTime;
		this.longestRefreshGraphTime = longestRefreshGraphTime;
		this.longestRefreshArrivalsTime = longestRefreshArrivalsTime;
		this.longestPathFindingTime = longestPathFindingTime;
	}

	protected DirectionsResponseSchema(final ReaderBase readerBase) {
		totalRefreshGraphTime = readerBase.getLong("totalRefreshGraphTime", 0);
		totalRefreshArrivalsTime = readerBase.getLong("totalRefreshArrivalsTime", 0);
		totalPathFindingTime = readerBase.getLong("totalPathFindingTime", 0);
		longestRefreshGraphTime = readerBase.getLong("longestRefreshGraphTime", 0);
		longestRefreshArrivalsTime = readerBase.getLong("longestRefreshArrivalsTime", 0);
		longestPathFindingTime = readerBase.getLong("longestPathFindingTime", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("connections", connections::clear, readerBaseChild -> connections.add(new DirectionsConnection(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		serializeConnections(writerBase);
		writerBase.writeLong("totalRefreshGraphTime", totalRefreshGraphTime);
		writerBase.writeLong("totalRefreshArrivalsTime", totalRefreshArrivalsTime);
		writerBase.writeLong("totalPathFindingTime", totalPathFindingTime);
		writerBase.writeLong("longestRefreshGraphTime", longestRefreshGraphTime);
		writerBase.writeLong("longestRefreshArrivalsTime", longestRefreshArrivalsTime);
		writerBase.writeLong("longestPathFindingTime", longestPathFindingTime);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "connections: " + connections + "\n"
			+ "totalRefreshGraphTime: " + totalRefreshGraphTime + "\n"
			+ "totalRefreshArrivalsTime: " + totalRefreshArrivalsTime + "\n"
			+ "totalPathFindingTime: " + totalPathFindingTime + "\n"
			+ "longestRefreshGraphTime: " + longestRefreshGraphTime + "\n"
			+ "longestRefreshArrivalsTime: " + longestRefreshArrivalsTime + "\n"
			+ "longestPathFindingTime: " + longestPathFindingTime + "\n"
		;
	}

	protected void serializeConnections(final WriterBase writerBase) {
		writerBase.writeDataset(connections, "connections");
	}
}