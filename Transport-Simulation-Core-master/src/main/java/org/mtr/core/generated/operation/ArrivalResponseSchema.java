package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class ArrivalResponseSchema implements SerializedDataBase {

	protected final String destination;

	protected final long arrival;

	protected final long departure;

	protected final long deviation;

	protected final boolean realtime;

	protected final long departureIndex;

	protected final boolean isTerminating;

	protected final long routeId;

	protected final String routeName;

	protected final String routeNumber;

	protected final long routeColor;

	protected final Route.CircularState circularState;

	protected final long platformId;

	protected final String platformName;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<CarDetails> cars = new it.unimi.dsi.fastutil.objects.ObjectArrayList<CarDetails>();

	protected ArrivalResponseSchema(final String destination, final long arrival, final long departure, final long deviation, final boolean realtime, final long departureIndex, final boolean isTerminating, final long routeId, final String routeName, final String routeNumber, final long routeColor, final Route.CircularState circularState, final long platformId, final String platformName) {
		this.destination = destination;
		this.arrival = arrival;
		this.departure = departure;
		this.deviation = deviation;
		this.realtime = realtime;
		this.departureIndex = departureIndex;
		this.isTerminating = isTerminating;
		this.routeId = routeId;
		this.routeName = routeName;
		this.routeNumber = routeNumber;
		this.routeColor = routeColor;
		this.circularState = circularState;
		this.platformId = platformId;
		this.platformName = platformName;
	}

	protected ArrivalResponseSchema(final ReaderBase readerBase) {
		destination = readerBase.getString("destination", "");
		arrival = readerBase.getLong("arrival", 0);
		departure = readerBase.getLong("departure", 0);
		deviation = readerBase.getLong("deviation", 0);
		realtime = readerBase.getBoolean("realtime", false);
		departureIndex = readerBase.getLong("departureIndex", 0);
		isTerminating = readerBase.getBoolean("isTerminating", false);
		routeId = readerBase.getLong("routeId", 0);
		routeName = readerBase.getString("routeName", "");
		routeNumber = readerBase.getString("routeNumber", "");
		routeColor = readerBase.getLong("routeColor", 0);
		circularState = EnumHelper.valueOf(Route.CircularState.values()[0], readerBase.getString("circularState", ""));
		platformId = readerBase.getLong("platformId", 0);
		platformName = readerBase.getString("platformName", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("cars", cars::clear, readerBaseChild -> cars.add(new CarDetails(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("destination", destination);
		writerBase.writeLong("arrival", arrival);
		writerBase.writeLong("departure", departure);
		writerBase.writeLong("deviation", deviation);
		writerBase.writeBoolean("realtime", realtime);
		writerBase.writeLong("departureIndex", departureIndex);
		writerBase.writeBoolean("isTerminating", isTerminating);
		writerBase.writeLong("routeId", routeId);
		writerBase.writeString("routeName", routeName);
		writerBase.writeString("routeNumber", routeNumber);
		writerBase.writeLong("routeColor", routeColor);
		writerBase.writeString("circularState", circularState.toString());
		writerBase.writeLong("platformId", platformId);
		writerBase.writeString("platformName", platformName);
		serializeCars(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "destination: " + destination + "\n"
			+ "arrival: " + arrival + "\n"
			+ "departure: " + departure + "\n"
			+ "deviation: " + deviation + "\n"
			+ "realtime: " + realtime + "\n"
			+ "departureIndex: " + departureIndex + "\n"
			+ "isTerminating: " + isTerminating + "\n"
			+ "routeId: " + routeId + "\n"
			+ "routeName: " + routeName + "\n"
			+ "routeNumber: " + routeNumber + "\n"
			+ "routeColor: " + routeColor + "\n"
			+ "circularState: " + circularState + "\n"
			+ "platformId: " + platformId + "\n"
			+ "platformName: " + platformName + "\n"
			+ "cars: " + cars + "\n"
		;
	}

	protected void serializeCars(final WriterBase writerBase) {
		writerBase.writeDataset(cars, "cars");
	}
}