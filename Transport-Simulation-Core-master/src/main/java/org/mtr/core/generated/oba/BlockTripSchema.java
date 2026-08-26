package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class BlockTripSchema implements SerializedDataBase {

	protected final String tripId;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<BlockStopTime> blockStopTimes = new it.unimi.dsi.fastutil.objects.ObjectArrayList<BlockStopTime>();

	protected final long accumulatedSlackTime;

	protected final double distanceAlongBlock;

	protected BlockTripSchema(final String tripId, final long accumulatedSlackTime, final double distanceAlongBlock) {
		this.tripId = tripId;
		this.accumulatedSlackTime = accumulatedSlackTime;
		this.distanceAlongBlock = distanceAlongBlock;
	}

	protected BlockTripSchema(final ReaderBase readerBase) {
		tripId = readerBase.getString("tripId", "");
		accumulatedSlackTime = readerBase.getLong("accumulatedSlackTime", 0);
		distanceAlongBlock = readerBase.getDouble("distanceAlongBlock", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("blockStopTimes", blockStopTimes::clear, readerBaseChild -> blockStopTimes.add(new BlockStopTime(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("tripId", tripId);
		serializeBlockStopTimes(writerBase);
		writerBase.writeLong("accumulatedSlackTime", accumulatedSlackTime);
		writerBase.writeDouble("distanceAlongBlock", distanceAlongBlock);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "tripId: " + tripId + "\n"
			+ "blockStopTimes: " + blockStopTimes + "\n"
			+ "accumulatedSlackTime: " + accumulatedSlackTime + "\n"
			+ "distanceAlongBlock: " + distanceAlongBlock + "\n"
		;
	}

	protected void serializeBlockStopTimes(final WriterBase writerBase) {
		writerBase.writeDataset(blockStopTimes, "blockStopTimes");
	}
}