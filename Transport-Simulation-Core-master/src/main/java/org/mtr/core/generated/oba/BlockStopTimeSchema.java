package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class BlockStopTimeSchema implements SerializedDataBase {

	protected final long blockSequence;

	protected final double distanceAlongBlock;

	protected final long accumulatedSlackTime;

	protected final StopTime stopTime;

	protected BlockStopTimeSchema(final long blockSequence, final double distanceAlongBlock, final long accumulatedSlackTime, final StopTime stopTime) {
		this.blockSequence = blockSequence;
		this.distanceAlongBlock = distanceAlongBlock;
		this.accumulatedSlackTime = accumulatedSlackTime;
		this.stopTime = stopTime;
	}

	protected BlockStopTimeSchema(final ReaderBase readerBase) {
		blockSequence = readerBase.getLong("blockSequence", 0);
		distanceAlongBlock = readerBase.getDouble("distanceAlongBlock", 0);
		accumulatedSlackTime = readerBase.getLong("accumulatedSlackTime", 0);
		stopTime = new StopTime(readerBase.getChild("stopTime"));
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("blockSequence", blockSequence);
		writerBase.writeDouble("distanceAlongBlock", distanceAlongBlock);
		writerBase.writeLong("accumulatedSlackTime", accumulatedSlackTime);
		if (stopTime != null) stopTime.serializeData(writerBase.writeChild("stopTime"));
	}

	@Nonnull
	public String toString() {
		return ""
			+ "blockSequence: " + blockSequence + "\n"
			+ "distanceAlongBlock: " + distanceAlongBlock + "\n"
			+ "accumulatedSlackTime: " + accumulatedSlackTime + "\n"
			+ "stopTime: " + stopTime + "\n"
		;
	}
}