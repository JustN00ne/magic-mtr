package org.mtr.core.generated.map;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.map.*;

public abstract class RouteSchema implements SerializedDataBase {

	protected final String id;

	protected final String name;

	protected final long color;

	protected final String number;

	protected final String type;

	protected final org.mtr.core.data.Route.CircularState circularState;

	protected final boolean hidden;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<RouteStation> stations = new it.unimi.dsi.fastutil.objects.ObjectArrayList<RouteStation>();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList durations = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> depots = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected RouteSchema(final String id, final String name, final long color, final String number, final String type, final org.mtr.core.data.Route.CircularState circularState, final boolean hidden) {
		this.id = id;
		this.name = name;
		this.color = color;
		this.number = number;
		this.type = type;
		this.circularState = circularState;
		this.hidden = hidden;
	}

	protected RouteSchema(final ReaderBase readerBase) {
		id = readerBase.getString("id", "");
		name = readerBase.getString("name", "");
		color = readerBase.getLong("color", 0);
		number = readerBase.getString("number", "");
		type = readerBase.getString("type", "");
		circularState = EnumHelper.valueOf(org.mtr.core.data.Route.CircularState.values()[0], readerBase.getString("circularState", ""));
		hidden = readerBase.getBoolean("hidden", false);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("stations", stations::clear, readerBaseChild -> stations.add(new RouteStation(readerBaseChild)));
		readerBase.iterateLongArray("durations", durations::clear, durations::add);
		readerBase.iterateStringArray("depots", depots::clear, depots::add);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("id", id);
		writerBase.writeString("name", name);
		writerBase.writeLong("color", color);
		writerBase.writeString("number", number);
		writerBase.writeString("type", type);
		writerBase.writeString("circularState", circularState.toString());
		writerBase.writeBoolean("hidden", hidden);
		serializeStations(writerBase);
		serializeDurations(writerBase);
		serializeDepots(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "id: " + id + "\n"
			+ "name: " + name + "\n"
			+ "color: " + color + "\n"
			+ "number: " + number + "\n"
			+ "type: " + type + "\n"
			+ "circularState: " + circularState + "\n"
			+ "hidden: " + hidden + "\n"
			+ "stations: " + stations + "\n"
			+ "durations: " + durations + "\n"
			+ "depots: " + depots + "\n"
		;
	}

	protected void serializeStations(final WriterBase writerBase) {
		writerBase.writeDataset(stations, "stations");
	}

	protected void serializeDurations(final WriterBase writerBase) {
		final WriterBase.Array durationsWriterBaseArray = writerBase.writeArray("durations"); durations.forEach(durationsWriterBaseArray::writeLong);
	}

	protected void serializeDepots(final WriterBase writerBase) {
		final WriterBase.Array depotsWriterBaseArray = writerBase.writeArray("depots"); depots.forEach(depotsWriterBaseArray::writeString);
	}
}