package org.mtr.core.generated.integration;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.integration.*;

public abstract class ResponseSchema implements SerializedDataBase {

	protected final long code;

	protected final long currentTime;

	protected final String text;

	protected final long version;

	protected ResponseSchema(final long code, final long currentTime, final String text, final long version) {
		this.code = code;
		this.currentTime = currentTime;
		this.text = text;
		this.version = version;
	}

	protected ResponseSchema(final ReaderBase readerBase) {
		code = readerBase.getLong("code", 0);
		currentTime = readerBase.getLong("currentTime", 0);
		text = readerBase.getString("text", "");
		version = readerBase.getLong("version", 0);
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("code", code);
		writerBase.writeLong("currentTime", currentTime);
		writerBase.writeString("text", text);
		writerBase.writeLong("version", version);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "code: " + code + "\n"
			+ "currentTime: " + currentTime + "\n"
			+ "text: " + text + "\n"
			+ "version: " + version + "\n"
		;
	}
}