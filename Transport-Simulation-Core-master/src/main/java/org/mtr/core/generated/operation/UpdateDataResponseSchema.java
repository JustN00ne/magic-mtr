package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class UpdateDataResponseSchema implements SerializedDataBase {

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Station> stations = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Station>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Platform> platforms = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Platform>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Siding> sidings = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Siding>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Route> routes = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Route>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<SimplifiedRoute> simplifiedRoutes = new it.unimi.dsi.fastutil.objects.ObjectArrayList<SimplifiedRoute>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Depot> depots = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Depot>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Rail> rails = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Rail>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Home> homes = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Home>();

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Landmark> landmarks = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Landmark>();

	protected UpdateDataResponseSchema() {
	}

	protected UpdateDataResponseSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("stations", stations::clear, readerBaseChild -> stations.add(new Station(readerBaseChild, stationsDataParameter())));
		readerBase.iterateReaderArray("platforms", platforms::clear, readerBaseChild -> platforms.add(new Platform(readerBaseChild, platformsDataParameter())));
		readerBase.iterateReaderArray("sidings", sidings::clear, readerBaseChild -> sidings.add(new Siding(readerBaseChild, sidingsDataParameter())));
		readerBase.iterateReaderArray("routes", routes::clear, readerBaseChild -> routes.add(new Route(readerBaseChild, routesDataParameter())));
		readerBase.iterateReaderArray("simplifiedRoutes", simplifiedRoutes::clear, readerBaseChild -> simplifiedRoutes.add(new SimplifiedRoute(readerBaseChild)));
		readerBase.iterateReaderArray("depots", depots::clear, readerBaseChild -> depots.add(new Depot(readerBaseChild, depotsDataParameter())));
		readerBase.iterateReaderArray("rails", rails::clear, readerBaseChild -> rails.add(new Rail(readerBaseChild)));
		readerBase.iterateReaderArray("homes", homes::clear, readerBaseChild -> homes.add(new Home(readerBaseChild, homesDataParameter())));
		readerBase.iterateReaderArray("landmarks", landmarks::clear, readerBaseChild -> landmarks.add(new Landmark(readerBaseChild, landmarksDataParameter())));
	}

	public void serializeData(final WriterBase writerBase) {
		serializeStations(writerBase);
		serializePlatforms(writerBase);
		serializeSidings(writerBase);
		serializeRoutes(writerBase);
		serializeSimplifiedRoutes(writerBase);
		serializeDepots(writerBase);
		serializeRails(writerBase);
		serializeHomes(writerBase);
		serializeLandmarks(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "stations: " + stations + "\n"
			+ "platforms: " + platforms + "\n"
			+ "sidings: " + sidings + "\n"
			+ "routes: " + routes + "\n"
			+ "simplifiedRoutes: " + simplifiedRoutes + "\n"
			+ "depots: " + depots + "\n"
			+ "rails: " + rails + "\n"
			+ "homes: " + homes + "\n"
			+ "landmarks: " + landmarks + "\n"
		;
	}

	protected void serializeStations(final WriterBase writerBase) {
		writerBase.writeDataset(stations, "stations");
	}

	@Nonnull
	protected abstract Data stationsDataParameter();

	protected void serializePlatforms(final WriterBase writerBase) {
		writerBase.writeDataset(platforms, "platforms");
	}

	@Nonnull
	protected abstract Data platformsDataParameter();

	protected void serializeSidings(final WriterBase writerBase) {
		writerBase.writeDataset(sidings, "sidings");
	}

	@Nonnull
	protected abstract Data sidingsDataParameter();

	protected void serializeRoutes(final WriterBase writerBase) {
		writerBase.writeDataset(routes, "routes");
	}

	@Nonnull
	protected abstract Data routesDataParameter();

	protected void serializeSimplifiedRoutes(final WriterBase writerBase) {
		writerBase.writeDataset(simplifiedRoutes, "simplifiedRoutes");
	}

	protected void serializeDepots(final WriterBase writerBase) {
		writerBase.writeDataset(depots, "depots");
	}

	@Nonnull
	protected abstract Data depotsDataParameter();

	protected void serializeRails(final WriterBase writerBase) {
		writerBase.writeDataset(rails, "rails");
	}

	protected void serializeHomes(final WriterBase writerBase) {
		writerBase.writeDataset(homes, "homes");
	}

	@Nonnull
	protected abstract Data homesDataParameter();

	protected void serializeLandmarks(final WriterBase writerBase) {
		writerBase.writeDataset(landmarks, "landmarks");
	}

	@Nonnull
	protected abstract Data landmarksDataParameter();
}