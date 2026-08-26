package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class SettingsSchema implements SerializedDataBaseWithId {

	protected final long lastSimulationMillis;

	protected SettingsSchema(final long lastSimulationMillis) {
		this.lastSimulationMillis = lastSimulationMillis;
	}

	protected SettingsSchema(final ReaderBase readerBase) {
		lastSimulationMillis = readerBase.getLong("lastSimulationMillis", 0);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("lastSimulationMillis", lastSimulationMillis);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "lastSimulationMillis: " + lastSimulationMillis + "\n"
		;
	}
}