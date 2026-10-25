package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class ReferencesBaseSchema implements SerializedDataBase {

	protected final References references;

	protected ReferencesBaseSchema(final References references) {
		this.references = references;
	}

	protected ReferencesBaseSchema(final ReaderBase readerBase) {
		references = new References(readerBase.getChild("references"));
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		if (references != null) references.serializeData(writerBase.writeChild("references"));
	}

	@Nonnull
	public String toString() {
		return ""
			+ "references: " + references + "\n"
		;
	}
}