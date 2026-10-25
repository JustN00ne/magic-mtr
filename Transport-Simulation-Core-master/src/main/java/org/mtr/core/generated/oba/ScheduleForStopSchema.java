package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class ScheduleForStopSchema implements SerializedDataBase {

	protected final long date;

	protected final String stopId;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<StopRouteSchedule> stopRouteSchedules = new it.unimi.dsi.fastutil.objects.ObjectArrayList<StopRouteSchedule>();

	protected final String timeZone;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<StopCalendarDay> stopCalendarDays = new it.unimi.dsi.fastutil.objects.ObjectArrayList<StopCalendarDay>();

	protected ScheduleForStopSchema(final long date, final String stopId, final String timeZone) {
		this.date = date;
		this.stopId = stopId;
		this.timeZone = timeZone;
	}

	protected ScheduleForStopSchema(final ReaderBase readerBase) {
		date = readerBase.getLong("date", 0);
		stopId = readerBase.getString("stopId", "");
		timeZone = readerBase.getString("timeZone", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("stopRouteSchedules", stopRouteSchedules::clear, readerBaseChild -> stopRouteSchedules.add(new StopRouteSchedule(readerBaseChild)));
		readerBase.iterateReaderArray("stopCalendarDays", stopCalendarDays::clear, readerBaseChild -> stopCalendarDays.add(new StopCalendarDay(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("date", date);
		writerBase.writeString("stopId", stopId);
		serializeStopRouteSchedules(writerBase);
		writerBase.writeString("timeZone", timeZone);
		serializeStopCalendarDays(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "date: " + date + "\n"
			+ "stopId: " + stopId + "\n"
			+ "stopRouteSchedules: " + stopRouteSchedules + "\n"
			+ "timeZone: " + timeZone + "\n"
			+ "stopCalendarDays: " + stopCalendarDays + "\n"
		;
	}

	protected void serializeStopRouteSchedules(final WriterBase writerBase) {
		writerBase.writeDataset(stopRouteSchedules, "stopRouteSchedules");
	}

	protected void serializeStopCalendarDays(final WriterBase writerBase) {
		writerBase.writeDataset(stopCalendarDays, "stopCalendarDays");
	}
}