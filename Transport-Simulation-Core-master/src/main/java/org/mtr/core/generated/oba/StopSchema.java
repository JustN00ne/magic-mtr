package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class StopSchema implements SerializedDataBase {

	protected final String id;

	protected final String code;

	protected final String name;

	protected final String description;

	protected final double lat;

	protected final double lon;

	protected final String url;

	protected final long locationType;

	protected final WheelchairBoarding wheelchairBoarding;

	protected final StopDirection direction;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> routeIds = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected StopSchema(final String id, final String code, final String name, final String description, final double lat, final double lon, final String url, final long locationType, final WheelchairBoarding wheelchairBoarding, final StopDirection direction) {
		this.id = id;
		this.code = code;
		this.name = name;
		this.description = description;
		this.lat = lat;
		this.lon = lon;
		this.url = url;
		this.locationType = locationType;
		this.wheelchairBoarding = wheelchairBoarding;
		this.direction = direction;
	}

	protected StopSchema(final ReaderBase readerBase) {
		id = readerBase.getString("id", "");
		code = readerBase.getString("code", "");
		name = readerBase.getString("name", "");
		description = readerBase.getString("description", "");
		lat = readerBase.getDouble("lat", 0);
		lon = readerBase.getDouble("lon", 0);
		url = readerBase.getString("url", "");
		locationType = readerBase.getLong("locationType", 0);
		wheelchairBoarding = EnumHelper.valueOf(WheelchairBoarding.values()[0], readerBase.getString("wheelchairBoarding", ""));
		direction = EnumHelper.valueOf(StopDirection.values()[0], readerBase.getString("direction", ""));
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateStringArray("routeIds", routeIds::clear, routeIds::add);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("id", id);
		writerBase.writeString("code", code);
		writerBase.writeString("name", name);
		writerBase.writeString("description", description);
		writerBase.writeDouble("lat", lat);
		writerBase.writeDouble("lon", lon);
		writerBase.writeString("url", url);
		writerBase.writeLong("locationType", locationType);
		writerBase.writeString("wheelchairBoarding", wheelchairBoarding.toString());
		writerBase.writeString("direction", direction.toString());
		serializeRouteIds(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "id: " + id + "\n"
			+ "code: " + code + "\n"
			+ "name: " + name + "\n"
			+ "description: " + description + "\n"
			+ "lat: " + lat + "\n"
			+ "lon: " + lon + "\n"
			+ "url: " + url + "\n"
			+ "locationType: " + locationType + "\n"
			+ "wheelchairBoarding: " + wheelchairBoarding + "\n"
			+ "direction: " + direction + "\n"
			+ "routeIds: " + routeIds + "\n"
		;
	}

	protected void serializeRouteIds(final WriterBase writerBase) {
		final WriterBase.Array routeIdsWriterBaseArray = writerBase.writeArray("routeIds"); routeIds.forEach(routeIdsWriterBaseArray::writeString);
	}
}