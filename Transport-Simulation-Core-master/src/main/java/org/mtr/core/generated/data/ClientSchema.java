package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class ClientSchema implements SerializedDataBase {

	protected final String clientId;

	protected Position position = getDefaultPosition();

	protected double updateRadius;

	protected ClientSchema(final String clientId) {
		this.clientId = clientId;
	}

	protected ClientSchema(final ReaderBase readerBase) {
		clientId = readerBase.getString("clientId", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackChild("position", readerBaseChild -> position = new Position(readerBaseChild));
		readerBase.unpackDouble("updateRadius", value -> updateRadius = value);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("clientId", clientId);
		serializePosition(writerBase);
		serializeUpdateRadius(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "clientId: " + clientId + "\n"
			+ "position: " + position + "\n"
			+ "updateRadius: " + updateRadius + "\n"
		;
	}

	protected abstract Position getDefaultPosition();

	protected void serializePosition(final WriterBase writerBase) {
		if (position != null) position.serializeData(writerBase.writeChild("position"));
	}

	protected void serializeUpdateRadius(final WriterBase writerBase) {
		writerBase.writeDouble("updateRadius", updateRadius);
	}
}