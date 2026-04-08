package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class RoutePlatformDataSchema implements SerializedDataBase {

	protected final long platformId;

	protected String customDestination = "";

	protected RoutePlatformDataSchema(final long platformId) {
		this.platformId = platformId;
	}

	protected RoutePlatformDataSchema(final ReaderBase readerBase) {
		platformId = readerBase.getLong("platformId", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackString("customDestination", value -> customDestination = value);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("platformId", platformId);
		serializeCustomDestination(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "platformId: " + platformId + "\n"
			+ "customDestination: " + customDestination + "\n"
		;
	}

	protected void serializeCustomDestination(final WriterBase writerBase) {
		writerBase.writeString("customDestination", customDestination);
	}
}