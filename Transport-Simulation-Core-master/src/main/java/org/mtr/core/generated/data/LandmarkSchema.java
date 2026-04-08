package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class LandmarkSchema extends SimpleAreaBase {

	protected final it.unimi.dsi.fastutil.longs.LongArrayList densities = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected LandmarkSchema(final TransportMode transportMode, final Data data) {
		super(transportMode, data);
	}

	protected LandmarkSchema(final ReaderBase readerBase, final Data data) {
		super(readerBase, data);
	}

	public void updateData(final ReaderBase readerBase) {
		super.updateData(readerBase);
		readerBase.iterateLongArray("densities", densities::clear, densities::add);
	}

	public void serializeData(final WriterBase writerBase) {
		super.serializeData(writerBase);
		serializeDensities(writerBase);
	}

	@Nonnull
	public String toString() {
		return super.toString()
			+ "densities: " + densities + "\n"
		;
	}

	protected void serializeDensities(final WriterBase writerBase) {
		final WriterBase.Array densitiesWriterBaseArray = writerBase.writeArray("densities"); densities.forEach(densitiesWriterBaseArray::writeLong);
	}
}