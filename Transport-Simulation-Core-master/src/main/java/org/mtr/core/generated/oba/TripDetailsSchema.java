package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class TripDetailsSchema implements SerializedDataBase {

	protected final String tripId;

	protected final long serviceDate;

	protected Frequency frequency = getDefaultFrequency();

	protected final TripStatus status;

	protected final Schedule schedule;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> situationIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected TripDetailsSchema(final String tripId, final long serviceDate, final TripStatus status, final Schedule schedule) {
		this.tripId = tripId;
		this.serviceDate = serviceDate;
		this.status = status;
		this.schedule = schedule;
	}

	protected TripDetailsSchema(final ReaderBase readerBase) {
		tripId = readerBase.getString("tripId", "");
		serviceDate = readerBase.getLong("serviceDate", 0);
		status = new TripStatus(readerBase.getChild("status"));
		schedule = new Schedule(readerBase.getChild("schedule"));
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackChild("frequency", readerBaseChild -> frequency = new Frequency(readerBaseChild));
		readerBase.iterateStringArray("situationIds", situationIds::clear, situationIds::add);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("tripId", tripId);
		writerBase.writeLong("serviceDate", serviceDate);
		serializeFrequency(writerBase);
		if (status != null) status.serializeData(writerBase.writeChild("status"));
		if (schedule != null) schedule.serializeData(writerBase.writeChild("schedule"));
		serializeSituationIds(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "tripId: " + tripId + "\n"
			+ "serviceDate: " + serviceDate + "\n"
			+ "frequency: " + frequency + "\n"
			+ "status: " + status + "\n"
			+ "schedule: " + schedule + "\n"
			+ "situationIds: " + situationIds + "\n"
		;
	}

	protected abstract Frequency getDefaultFrequency();

	protected void serializeFrequency(final WriterBase writerBase) {
		if (frequency != null) frequency.serializeData(writerBase.writeChild("frequency"));
	}

	protected void serializeSituationIds(final WriterBase writerBase) {
		final WriterBase.Array situationIdsWriterBaseArray = writerBase.writeArray("situationIds"); situationIds.forEach(situationIdsWriterBaseArray::writeString);
	}
}