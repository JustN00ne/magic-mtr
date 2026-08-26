package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class InterchangeColorsForStationNameSchema implements SerializedDataBase {

	protected final String stationName;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<InterchangeRouteNamesForColor> interchangeRouteNamesForColorList = new it.unimi.dsi.fastutil.objects.ObjectArrayList<InterchangeRouteNamesForColor>();

	protected InterchangeColorsForStationNameSchema(final String stationName) {
		this.stationName = stationName;
	}

	protected InterchangeColorsForStationNameSchema(final ReaderBase readerBase) {
		stationName = readerBase.getString("stationName", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("interchangeRouteNamesForColorList", interchangeRouteNamesForColorList::clear, readerBaseChild -> interchangeRouteNamesForColorList.add(new InterchangeRouteNamesForColor(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("stationName", stationName);
		serializeInterchangeRouteNamesForColorList(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "stationName: " + stationName + "\n"
			+ "interchangeRouteNamesForColorList: " + interchangeRouteNamesForColorList + "\n"
		;
	}

	protected void serializeInterchangeRouteNamesForColorList(final WriterBase writerBase) {
		writerBase.writeDataset(interchangeRouteNamesForColorList, "interchangeRouteNamesForColorList");
	}
}