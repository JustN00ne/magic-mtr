package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class RegisterAlarmForArrivalAndDepartureAtStopSchema implements SerializedDataBase {

	protected final String alarmId;

	protected RegisterAlarmForArrivalAndDepartureAtStopSchema(final String alarmId) {
		this.alarmId = alarmId;
	}

	protected RegisterAlarmForArrivalAndDepartureAtStopSchema(final ReaderBase readerBase) {
		alarmId = readerBase.getString("alarmId", "");
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("alarmId", alarmId);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "alarmId: " + alarmId + "\n"
		;
	}
}