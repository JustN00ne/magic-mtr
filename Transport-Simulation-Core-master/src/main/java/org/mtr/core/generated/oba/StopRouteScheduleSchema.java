package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class StopRouteScheduleSchema implements SerializedDataBase {

	protected final String routeId;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<StopRouteDirectionSchedule> stopRouteDirectionSchedules = new it.unimi.dsi.fastutil.objects.ObjectArrayList<StopRouteDirectionSchedule>();

	protected StopRouteScheduleSchema(final String routeId) {
		this.routeId = routeId;
	}

	protected StopRouteScheduleSchema(final ReaderBase readerBase) {
		routeId = readerBase.getString("routeId", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("stopRouteDirectionSchedules", stopRouteDirectionSchedules::clear, readerBaseChild -> stopRouteDirectionSchedules.add(new StopRouteDirectionSchedule(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("routeId", routeId);
		serializeStopRouteDirectionSchedules(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "routeId: " + routeId + "\n"
			+ "stopRouteDirectionSchedules: " + stopRouteDirectionSchedules + "\n"
		;
	}

	protected void serializeStopRouteDirectionSchedules(final WriterBase writerBase) {
		writerBase.writeDataset(stopRouteDirectionSchedules, "stopRouteDirectionSchedules");
	}
}