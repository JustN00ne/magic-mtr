package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class StationSchema extends AreaBase<Station, Platform> {

	protected long zone1;

	protected long zone2;

	protected long zone3;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<StationExit> exits = new it.unimi.dsi.fastutil.objects.ObjectArrayList<StationExit>();

	protected StationSchema(final TransportMode transportMode, final Data data) {
		super(transportMode, data);
	}

	protected StationSchema(final ReaderBase readerBase, final Data data) {
		super(readerBase, data);
	}

	public void updateData(final ReaderBase readerBase) {
		super.updateData(readerBase);
		readerBase.unpackLong("zone1", value -> zone1 = value);
		readerBase.unpackLong("zone2", value -> zone2 = value);
		readerBase.unpackLong("zone3", value -> zone3 = value);
		readerBase.iterateReaderArray("exits", exits::clear, readerBaseChild -> exits.add(new StationExit(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		super.serializeData(writerBase);
		serializeZone1(writerBase);
		serializeZone2(writerBase);
		serializeZone3(writerBase);
		serializeExits(writerBase);
	}

	@Nonnull
	public String toString() {
		return super.toString()
			+ "zone1: " + zone1 + "\n"
			+ "zone2: " + zone2 + "\n"
			+ "zone3: " + zone3 + "\n"
			+ "exits: " + exits + "\n"
		;
	}

	protected void serializeZone1(final WriterBase writerBase) {
		writerBase.writeLong("zone1", zone1);
	}

	protected void serializeZone2(final WriterBase writerBase) {
		writerBase.writeLong("zone2", zone2);
	}

	protected void serializeZone3(final WriterBase writerBase) {
		writerBase.writeLong("zone3", zone3);
	}

	protected void serializeExits(final WriterBase writerBase) {
		writerBase.writeDataset(exits, "exits");
	}
}