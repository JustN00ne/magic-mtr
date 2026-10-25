package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class PlayerPresentResponseSchema implements SerializedDataBase {

	protected final String playerDimension;

	protected PlayerPresentResponseSchema(final String playerDimension) {
		this.playerDimension = playerDimension;
	}

	protected PlayerPresentResponseSchema(final ReaderBase readerBase) {
		playerDimension = readerBase.getString("playerDimension", "");
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("playerDimension", playerDimension);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "playerDimension: " + playerDimension + "\n"
		;
	}
}