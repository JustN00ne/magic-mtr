package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class DataResponseSchema implements SerializedDataBase {

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Station> stations = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Station>();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList stationsToKeep = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Platform> platforms = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Platform>();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList platformsToKeep = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Siding> sidings = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Siding>();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList sidingsToKeep = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<SimplifiedRoute> simplifiedRoutes = new it.unimi.dsi.fastutil.objects.ObjectArrayList<SimplifiedRoute>();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList simplifiedRoutesToKeep = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Depot> depots = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Depot>();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList depotsToKeep = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Rail> rails = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Rail>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<String> railsToKeep = new it.unimi.dsi.fastutil.objects.ObjectArrayList<String>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Home> homes = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Home>();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList homesToKeep = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Landmark> landmarks = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Landmark>();

	protected final it.unimi.dsi.fastutil.longs.LongArrayList landmarksToKeep = new it.unimi.dsi.fastutil.longs.LongArrayList();

	protected DataResponseSchema() {
	}

	protected DataResponseSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("stations", stations::clear, readerBaseChild -> stations.add(new Station(readerBaseChild, stationsDataParameter())));
		readerBase.iterateLongArray("stationsToKeep", stationsToKeep::clear, stationsToKeep::add);
		readerBase.iterateReaderArray("platforms", platforms::clear, readerBaseChild -> platforms.add(new Platform(readerBaseChild, platformsDataParameter())));
		readerBase.iterateLongArray("platformsToKeep", platformsToKeep::clear, platformsToKeep::add);
		readerBase.iterateReaderArray("sidings", sidings::clear, readerBaseChild -> sidings.add(new Siding(readerBaseChild, sidingsDataParameter())));
		readerBase.iterateLongArray("sidingsToKeep", sidingsToKeep::clear, sidingsToKeep::add);
		readerBase.iterateReaderArray("simplifiedRoutes", simplifiedRoutes::clear, readerBaseChild -> simplifiedRoutes.add(new SimplifiedRoute(readerBaseChild)));
		readerBase.iterateLongArray("simplifiedRoutesToKeep", simplifiedRoutesToKeep::clear, simplifiedRoutesToKeep::add);
		readerBase.iterateReaderArray("depots", depots::clear, readerBaseChild -> depots.add(new Depot(readerBaseChild, depotsDataParameter())));
		readerBase.iterateLongArray("depotsToKeep", depotsToKeep::clear, depotsToKeep::add);
		readerBase.iterateReaderArray("rails", rails::clear, readerBaseChild -> rails.add(new Rail(readerBaseChild)));
		readerBase.iterateStringArray("railsToKeep", railsToKeep::clear, railsToKeep::add);
		readerBase.iterateReaderArray("homes", homes::clear, readerBaseChild -> homes.add(new Home(readerBaseChild, homesDataParameter())));
		readerBase.iterateLongArray("homesToKeep", homesToKeep::clear, homesToKeep::add);
		readerBase.iterateReaderArray("landmarks", landmarks::clear, readerBaseChild -> landmarks.add(new Landmark(readerBaseChild, landmarksDataParameter())));
		readerBase.iterateLongArray("landmarksToKeep", landmarksToKeep::clear, landmarksToKeep::add);
	}

	public void serializeData(final WriterBase writerBase) {
		serializeStations(writerBase);
		serializeStationsToKeep(writerBase);
		serializePlatforms(writerBase);
		serializePlatformsToKeep(writerBase);
		serializeSidings(writerBase);
		serializeSidingsToKeep(writerBase);
		serializeSimplifiedRoutes(writerBase);
		serializeSimplifiedRoutesToKeep(writerBase);
		serializeDepots(writerBase);
		serializeDepotsToKeep(writerBase);
		serializeRails(writerBase);
		serializeRailsToKeep(writerBase);
		serializeHomes(writerBase);
		serializeHomesToKeep(writerBase);
		serializeLandmarks(writerBase);
		serializeLandmarksToKeep(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "stations: " + stations + "\n"
			+ "stationsToKeep: " + stationsToKeep + "\n"
			+ "platforms: " + platforms + "\n"
			+ "platformsToKeep: " + platformsToKeep + "\n"
			+ "sidings: " + sidings + "\n"
			+ "sidingsToKeep: " + sidingsToKeep + "\n"
			+ "simplifiedRoutes: " + simplifiedRoutes + "\n"
			+ "simplifiedRoutesToKeep: " + simplifiedRoutesToKeep + "\n"
			+ "depots: " + depots + "\n"
			+ "depotsToKeep: " + depotsToKeep + "\n"
			+ "rails: " + rails + "\n"
			+ "railsToKeep: " + railsToKeep + "\n"
			+ "homes: " + homes + "\n"
			+ "homesToKeep: " + homesToKeep + "\n"
			+ "landmarks: " + landmarks + "\n"
			+ "landmarksToKeep: " + landmarksToKeep + "\n"
		;
	}

	protected void serializeStations(final WriterBase writerBase) {
		writerBase.writeDataset(stations, "stations");
	}

	@Nonnull
	protected abstract Data stationsDataParameter();

	protected void serializeStationsToKeep(final WriterBase writerBase) {
		final WriterBase.Array stationsToKeepWriterBaseArray = writerBase.writeArray("stationsToKeep"); stationsToKeep.forEach(stationsToKeepWriterBaseArray::writeLong);
	}

	protected void serializePlatforms(final WriterBase writerBase) {
		writerBase.writeDataset(platforms, "platforms");
	}

	@Nonnull
	protected abstract Data platformsDataParameter();

	protected void serializePlatformsToKeep(final WriterBase writerBase) {
		final WriterBase.Array platformsToKeepWriterBaseArray = writerBase.writeArray("platformsToKeep"); platformsToKeep.forEach(platformsToKeepWriterBaseArray::writeLong);
	}

	protected void serializeSidings(final WriterBase writerBase) {
		writerBase.writeDataset(sidings, "sidings");
	}

	@Nonnull
	protected abstract Data sidingsDataParameter();

	protected void serializeSidingsToKeep(final WriterBase writerBase) {
		final WriterBase.Array sidingsToKeepWriterBaseArray = writerBase.writeArray("sidingsToKeep"); sidingsToKeep.forEach(sidingsToKeepWriterBaseArray::writeLong);
	}

	protected void serializeSimplifiedRoutes(final WriterBase writerBase) {
		writerBase.writeDataset(simplifiedRoutes, "simplifiedRoutes");
	}

	protected void serializeSimplifiedRoutesToKeep(final WriterBase writerBase) {
		final WriterBase.Array simplifiedRoutesToKeepWriterBaseArray = writerBase.writeArray("simplifiedRoutesToKeep"); simplifiedRoutesToKeep.forEach(simplifiedRoutesToKeepWriterBaseArray::writeLong);
	}

	protected void serializeDepots(final WriterBase writerBase) {
		writerBase.writeDataset(depots, "depots");
	}

	@Nonnull
	protected abstract Data depotsDataParameter();

	protected void serializeDepotsToKeep(final WriterBase writerBase) {
		final WriterBase.Array depotsToKeepWriterBaseArray = writerBase.writeArray("depotsToKeep"); depotsToKeep.forEach(depotsToKeepWriterBaseArray::writeLong);
	}

	protected void serializeRails(final WriterBase writerBase) {
		writerBase.writeDataset(rails, "rails");
	}

	protected void serializeRailsToKeep(final WriterBase writerBase) {
		final WriterBase.Array railsToKeepWriterBaseArray = writerBase.writeArray("railsToKeep"); railsToKeep.forEach(railsToKeepWriterBaseArray::writeString);
	}

	protected void serializeHomes(final WriterBase writerBase) {
		writerBase.writeDataset(homes, "homes");
	}

	@Nonnull
	protected abstract Data homesDataParameter();

	protected void serializeHomesToKeep(final WriterBase writerBase) {
		final WriterBase.Array homesToKeepWriterBaseArray = writerBase.writeArray("homesToKeep"); homesToKeep.forEach(homesToKeepWriterBaseArray::writeLong);
	}

	protected void serializeLandmarks(final WriterBase writerBase) {
		writerBase.writeDataset(landmarks, "landmarks");
	}

	@Nonnull
	protected abstract Data landmarksDataParameter();

	protected void serializeLandmarksToKeep(final WriterBase writerBase) {
		final WriterBase.Array landmarksToKeepWriterBaseArray = writerBase.writeArray("landmarksToKeep"); landmarksToKeep.forEach(landmarksToKeepWriterBaseArray::writeLong);
	}
}