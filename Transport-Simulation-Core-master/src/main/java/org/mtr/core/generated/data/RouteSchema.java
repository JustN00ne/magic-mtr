package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class RouteSchema extends NameColorDataBase {

	protected RouteType routeType = RouteType.values()[0];

	protected String routeNumber = "";

	protected boolean hidden;

	protected Route.CircularState circularState = Route.CircularState.values()[0];

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<RoutePlatformData> routePlatformData = new it.unimi.dsi.fastutil.objects.ObjectArrayList<RoutePlatformData>();

	protected RouteSchema(final TransportMode transportMode, final Data data) {
		super(transportMode, data);
	}

	protected RouteSchema(final ReaderBase readerBase, final Data data) {
		super(readerBase, data);
	}

	public void updateData(final ReaderBase readerBase) {
		super.updateData(readerBase);
		readerBase.unpackString("routeType", value -> routeType = EnumHelper.valueOf(RouteType.values()[0], value));
		readerBase.unpackString("routeNumber", value -> routeNumber = value);
		readerBase.unpackBoolean("hidden", value -> hidden = value);
		readerBase.unpackString("circularState", value -> circularState = EnumHelper.valueOf(Route.CircularState.values()[0], value));
		readerBase.iterateReaderArray("routePlatformData", routePlatformData::clear, readerBaseChild -> routePlatformData.add(new RoutePlatformData(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		super.serializeData(writerBase);
		serializeRouteType(writerBase);
		serializeRouteNumber(writerBase);
		serializeHidden(writerBase);
		serializeCircularState(writerBase);
		serializeRoutePlatformData(writerBase);
	}

	@Nonnull
	public String toString() {
		return super.toString()
			+ "routeType: " + routeType + "\n"
			+ "routeNumber: " + routeNumber + "\n"
			+ "hidden: " + hidden + "\n"
			+ "circularState: " + circularState + "\n"
			+ "routePlatformData: " + routePlatformData + "\n"
		;
	}

	protected void serializeRouteType(final WriterBase writerBase) {
		writerBase.writeString("routeType", routeType.toString());
	}

	protected void serializeRouteNumber(final WriterBase writerBase) {
		writerBase.writeString("routeNumber", routeNumber);
	}

	protected void serializeHidden(final WriterBase writerBase) {
		writerBase.writeBoolean("hidden", hidden);
	}

	protected void serializeCircularState(final WriterBase writerBase) {
		writerBase.writeString("circularState", circularState.toString());
	}

	protected void serializeRoutePlatformData(final WriterBase writerBase) {
		writerBase.writeDataset(routePlatformData, "routePlatformData");
	}
}