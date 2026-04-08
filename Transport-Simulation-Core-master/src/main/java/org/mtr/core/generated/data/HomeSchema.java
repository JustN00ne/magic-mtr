package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;

public abstract class HomeSchema extends SimpleAreaBase {

	protected long population;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Passenger> passengers = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Passenger>();

	protected HomeSchema(final TransportMode transportMode, final Data data) {
		super(transportMode, data);
	}

	protected HomeSchema(final ReaderBase readerBase, final Data data) {
		super(readerBase, data);
	}

	public void updateData(final ReaderBase readerBase) {
		super.updateData(readerBase);
		readerBase.unpackLong("population", value -> population = value);
		readerBase.iterateReaderArray("passengers", passengers::clear, readerBaseChild -> passengers.add(new Passenger(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		super.serializeData(writerBase);
		serializePopulation(writerBase);
		serializePassengers(writerBase);
	}

	@Nonnull
	public String toString() {
		return super.toString()
			+ "population: " + population + "\n"
			+ "passengers: " + passengers + "\n"
		;
	}

	protected void serializePopulation(final WriterBase writerBase) {
		writerBase.writeLong("population", population);
	}

	protected void serializePassengers(final WriterBase writerBase) {
		writerBase.writeDataset(passengers, "passengers");
	}
}