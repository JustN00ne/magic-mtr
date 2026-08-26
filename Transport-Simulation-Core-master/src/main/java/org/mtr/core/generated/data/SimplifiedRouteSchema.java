package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class SimplifiedRouteSchema implements SerializedDataBase {

	protected final long id;

	protected final String name;

	protected final long color;

	protected final Route.CircularState circularState;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<SimplifiedRoutePlatform> platforms = new it.unimi.dsi.fastutil.objects.ObjectArrayList<SimplifiedRoutePlatform>();

	protected SimplifiedRouteSchema(final long id, final String name, final long color, final Route.CircularState circularState) {
		this.id = id;
		this.name = name;
		this.color = color;
		this.circularState = circularState;
	}

	protected SimplifiedRouteSchema(final ReaderBase readerBase) {
		id = readerBase.getLong("id", 0);
		name = readerBase.getString("name", "");
		color = readerBase.getLong("color", 0);
		circularState = EnumHelper.valueOf(Route.CircularState.values()[0], readerBase.getString("circularState", ""));
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("platforms", platforms::clear, readerBaseChild -> platforms.add(new SimplifiedRoutePlatform(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("id", id);
		writerBase.writeString("name", name);
		writerBase.writeLong("color", color);
		writerBase.writeString("circularState", circularState.toString());
		serializePlatforms(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "id: " + id + "\n"
			+ "name: " + name + "\n"
			+ "color: " + color + "\n"
			+ "circularState: " + circularState + "\n"
			+ "platforms: " + platforms + "\n"
		;
	}

	protected void serializePlatforms(final WriterBase writerBase) {
		writerBase.writeDataset(platforms, "platforms");
	}
}