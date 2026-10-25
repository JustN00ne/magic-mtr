package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class BlockSchema implements SerializedDataBase {

	protected final String id;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<BlockConfiguration> configurations = new it.unimi.dsi.fastutil.objects.ObjectArrayList<BlockConfiguration>();

	protected BlockSchema(final String id) {
		this.id = id;
	}

	protected BlockSchema(final ReaderBase readerBase) {
		id = readerBase.getString("id", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("configurations", configurations::clear, readerBaseChild -> configurations.add(new BlockConfiguration(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("id", id);
		serializeConfigurations(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "id: " + id + "\n"
			+ "configurations: " + configurations + "\n"
		;
	}

	protected void serializeConfigurations(final WriterBase writerBase) {
		writerBase.writeDataset(configurations, "configurations");
	}
}