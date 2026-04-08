package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class ArrivalsResponseSchema implements SerializedDataBase {

	protected final long currentTime;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<ArrivalResponse> arrivals = new it.unimi.dsi.fastutil.objects.ObjectArrayList<ArrivalResponse>();

	protected ArrivalsResponseSchema(final long currentTime) {
		this.currentTime = currentTime;
	}

	protected ArrivalsResponseSchema(final ReaderBase readerBase) {
		currentTime = readerBase.getLong("currentTime", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("arrivals", arrivals::clear, readerBaseChild -> arrivals.add(new ArrivalResponse(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("currentTime", currentTime);
		serializeArrivals(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "currentTime: " + currentTime + "\n"
			+ "arrivals: " + arrivals + "\n"
		;
	}

	protected void serializeArrivals(final WriterBase writerBase) {
		writerBase.writeDataset(arrivals, "arrivals");
	}
}