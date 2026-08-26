package org.mtr.core.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.simulation.*;
import org.junit.jupiter.api.*;

public final class SchemaTests implements TestUtilities {

	@RepeatedTest(10)
	public void testClientSchema() {
		final Client data = TestUtilities.randomClient();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newClient);
		data.position = TestUtilities.randomPosition();
		data.updateRadius = RANDOM.nextDouble();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newClient);
	}

	@RepeatedTest(10)
	public void testDepotSchema() {
		final Depot data = TestUtilities.randomDepot();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newDepot);
		data.routeIds.clear(); TestUtilities.randomLoop(() -> data.routeIds.add(RANDOM.nextLong()));
		data.lastGeneratedMillis = RANDOM.nextLong();
		data.lastGeneratedStatus = TestUtilities.randomEnum(Depot.GeneratedStatus.values());
		data.lastGeneratedFailedStartId = RANDOM.nextLong();
		data.lastGeneratedFailedEndId = RANDOM.nextLong();
		data.lastGeneratedFailedSidingCount = RANDOM.nextLong();
		data.useRealTime = RANDOM.nextBoolean();
		data.frequencies.clear(); TestUtilities.randomLoop(() -> data.frequencies.add(RANDOM.nextLong()));
		data.realTimeDepartures.clear(); TestUtilities.randomLoop(() -> data.realTimeDepartures.add(RANDOM.nextLong()));
		data.repeatInfinitely = RANDOM.nextBoolean();
		data.cruisingAltitude = RANDOM.nextLong();
		data.position1 = TestUtilities.randomPosition();
		data.position2 = TestUtilities.randomPosition();
		data.name = TestUtilities.randomString();
		data.color = RANDOM.nextLong();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newDepot);
	}

	@RepeatedTest(10)
	public void testHomeSchema() {
		final Home data = TestUtilities.randomHome();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newHome);
		data.population = RANDOM.nextLong();
		data.passengers.clear(); TestUtilities.randomLoop(() -> data.passengers.add(TestUtilities.randomPassenger()));
		data.position1 = TestUtilities.randomPosition();
		data.position2 = TestUtilities.randomPosition();
		data.name = TestUtilities.randomString();
		data.color = RANDOM.nextLong();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newHome);
	}

	@RepeatedTest(10)
	public void testInterchangeRouteNamesForColorSchema() {
		final InterchangeRouteNamesForColor data = TestUtilities.randomInterchangeRouteNamesForColor();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newInterchangeRouteNamesForColor);
		data.routeNames.clear(); TestUtilities.randomLoop(() -> data.routeNames.add(TestUtilities.randomString()));
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newInterchangeRouteNamesForColor);
	}

	@RepeatedTest(10)
	public void testLandmarkSchema() {
		final Landmark data = TestUtilities.randomLandmark();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newLandmark);
		data.densities.clear(); TestUtilities.randomLoop(() -> data.densities.add(RANDOM.nextLong()));
		data.position1 = TestUtilities.randomPosition();
		data.position2 = TestUtilities.randomPosition();
		data.name = TestUtilities.randomString();
		data.color = RANDOM.nextLong();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newLandmark);
	}

	@RepeatedTest(10)
	public void testLiftFloorSchema() {
		final LiftFloor data = TestUtilities.randomLiftFloor();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newLiftFloor);
		data.number = TestUtilities.randomString();
		data.description = TestUtilities.randomString();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newLiftFloor);
	}

	@RepeatedTest(10)
	public void testLiftInstructionSchema() {
		final LiftInstruction data = TestUtilities.randomLiftInstruction();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newLiftInstruction);
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newLiftInstruction);
	}

	@RepeatedTest(10)
	public void testLiftSchema() {
		final Lift data = TestUtilities.randomLift();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newLift);
		data.height = RANDOM.nextDouble();
		data.width = RANDOM.nextDouble();
		data.depth = RANDOM.nextDouble();
		data.offsetX = RANDOM.nextDouble();
		data.offsetY = RANDOM.nextDouble();
		data.offsetZ = RANDOM.nextDouble();
		data.isDoubleSided = RANDOM.nextBoolean();
		data.style = TestUtilities.randomString();
		data.angle = TestUtilities.randomEnum(Angle.values());
		data.railProgress = RANDOM.nextDouble();
		data.speed = RANDOM.nextDouble();
		data.stoppingCoolDown = RANDOM.nextLong();
		data.floors.clear(); TestUtilities.randomLoop(() -> data.floors.add(TestUtilities.randomLiftFloor()));
		data.instructions.clear(); TestUtilities.randomLoop(() -> data.instructions.add(TestUtilities.randomLiftInstruction()));
		data.ridingEntities.clear(); TestUtilities.randomLoop(() -> data.ridingEntities.add(TestUtilities.randomVehicleRidingEntity()));
		data.name = TestUtilities.randomString();
		data.color = RANDOM.nextLong();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newLift);
	}

	@RepeatedTest(10)
	public void testPassengerDirectionSchema() {
		final PassengerDirection data = TestUtilities.randomPassengerDirection();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newPassengerDirection);
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newPassengerDirection);
	}

	@RepeatedTest(10)
	public void testPassengerSchema() {
		final Passenger data = TestUtilities.randomPassenger();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newPassenger);
		data.directions.clear(); TestUtilities.randomLoop(() -> data.directions.add(TestUtilities.randomPassengerDirection()));
		data.startLandmarkId = RANDOM.nextLong();
		data.endLandmarkId = RANDOM.nextLong();
		data.landmarkVisitStartTime = RANDOM.nextLong();
		data.landmarkVisitEndTime = RANDOM.nextLong();
		data.sidingId = RANDOM.nextLong();
		data.vehicleId = RANDOM.nextLong();
		data.name = TestUtilities.randomString();
		data.color = RANDOM.nextLong();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newPassenger);
	}

	@RepeatedTest(10)
	public void testPathDataSchema() {
		final PathData data = TestUtilities.randomPathData();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newPathData);
		data.speedLimit = RANDOM.nextLong();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newPathData);
	}

	@RepeatedTest(10)
	public void testPlatformSchema() {
		final Platform data = TestUtilities.randomPlatform();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newPlatform);
		data.dwellTime = RANDOM.nextLong();
		data.name = TestUtilities.randomString();
		data.color = RANDOM.nextLong();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newPlatform);
	}

	@RepeatedTest(10)
	public void testPositionSchema() {
		final Position data = TestUtilities.randomPosition();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newPosition);
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newPosition);
	}

	@RepeatedTest(10)
	public void testRailSchema() {
		final Rail data = TestUtilities.randomRail();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newRail);
		data.styles.clear(); TestUtilities.randomLoop(() -> data.styles.add(TestUtilities.randomString()));
		data.signalColors.clear(); TestUtilities.randomLoop(() -> data.signalColors.add(RANDOM.nextLong()));
		data.stylesMigratedLegacy = RANDOM.nextBoolean();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newRail);
	}

	@RepeatedTest(10)
	public void testRoutePlatformDataSchema() {
		final RoutePlatformData data = TestUtilities.randomRoutePlatformData();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newRoutePlatformData);
		data.customDestination = TestUtilities.randomString();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newRoutePlatformData);
	}

	@RepeatedTest(10)
	public void testRouteSchema() {
		final Route data = TestUtilities.randomRoute();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newRoute);
		data.routeType = TestUtilities.randomEnum(RouteType.values());
		data.routeNumber = TestUtilities.randomString();
		data.hidden = RANDOM.nextBoolean();
		data.circularState = TestUtilities.randomEnum(Route.CircularState.values());
		data.routePlatformData.clear(); TestUtilities.randomLoop(() -> data.routePlatformData.add(TestUtilities.randomRoutePlatformData()));
		data.name = TestUtilities.randomString();
		data.color = RANDOM.nextLong();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newRoute);
	}

	@RepeatedTest(10)
	public void testSettingsSchema() {
		final Settings data = TestUtilities.randomSettings();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newSettings);
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newSettings);
	}

	@RepeatedTest(10)
	public void testSidingSchema() {
		final Siding data = TestUtilities.randomSiding();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newSiding);
		data.vehicleCars.clear(); TestUtilities.randomLoop(() -> data.vehicleCars.add(TestUtilities.randomVehicleCar()));
		data.maxVehicles = RANDOM.nextLong();
		data.delayedVehicleSpeedIncreasePercentage = RANDOM.nextLong();
		data.delayedVehicleReduceDwellTimePercentage = RANDOM.nextLong();
		data.earlyVehicleIncreaseDwellTime = RANDOM.nextBoolean();
		data.maxManualSpeed = RANDOM.nextDouble();
		data.manualToAutomaticTime = RANDOM.nextLong();
		data.acceleration = RANDOM.nextDouble();
		data.deceleration = RANDOM.nextDouble();
		data.name = TestUtilities.randomString();
		data.color = RANDOM.nextLong();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newSiding);
	}

	@RepeatedTest(10)
	public void testSignalModificationSchema() {
		final SignalModification data = TestUtilities.randomSignalModification();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newSignalModification);
		data.signalColorsAdd.clear(); TestUtilities.randomLoop(() -> data.signalColorsAdd.add(RANDOM.nextLong()));
		data.signalColorsRemove.clear(); TestUtilities.randomLoop(() -> data.signalColorsRemove.add(RANDOM.nextLong()));
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newSignalModification);
	}

	@RepeatedTest(10)
	public void testSimplifiedRoutePlatformSchema() {
		final SimplifiedRoutePlatform data = TestUtilities.randomSimplifiedRoutePlatform();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newSimplifiedRoutePlatform);
		data.interchangeRouteNamesForColorList.clear(); TestUtilities.randomLoop(() -> data.interchangeRouteNamesForColorList.add(TestUtilities.randomInterchangeRouteNamesForColor()));
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newSimplifiedRoutePlatform);
	}

	@RepeatedTest(10)
	public void testSimplifiedRouteSchema() {
		final SimplifiedRoute data = TestUtilities.randomSimplifiedRoute();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newSimplifiedRoute);
		data.platforms.clear(); TestUtilities.randomLoop(() -> data.platforms.add(TestUtilities.randomSimplifiedRoutePlatform()));
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newSimplifiedRoute);
	}

	@RepeatedTest(10)
	public void testStationExitSchema() {
		final StationExit data = TestUtilities.randomStationExit();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newStationExit);
		data.name = TestUtilities.randomString();
		data.destinations.clear(); TestUtilities.randomLoop(() -> data.destinations.add(TestUtilities.randomString()));
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newStationExit);
	}

	@RepeatedTest(10)
	public void testStationSchema() {
		final Station data = TestUtilities.randomStation();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newStation);
		data.zone1 = RANDOM.nextLong();
		data.zone2 = RANDOM.nextLong();
		data.zone3 = RANDOM.nextLong();
		data.exits.clear(); TestUtilities.randomLoop(() -> data.exits.add(TestUtilities.randomStationExit()));
		data.position1 = TestUtilities.randomPosition();
		data.position2 = TestUtilities.randomPosition();
		data.name = TestUtilities.randomString();
		data.color = RANDOM.nextLong();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newStation);
	}

	@RepeatedTest(10)
	public void testVehicleCarSchema() {
		final VehicleCar data = TestUtilities.randomVehicleCar();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newVehicleCar);
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newVehicleCar);
	}

	@RepeatedTest(10)
	public void testVehicleExtraDataSchema() {
		final VehicleExtraData data = TestUtilities.randomVehicleExtraData();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newVehicleExtraData);
		data.previousRouteId = RANDOM.nextLong();
		data.previousPlatformId = RANDOM.nextLong();
		data.previousStationId = RANDOM.nextLong();
		data.previousRouteColor = RANDOM.nextLong();
		data.previousRouteName = TestUtilities.randomString();
		data.previousRouteNumber = TestUtilities.randomString();
		data.previousRouteType = TestUtilities.randomEnum(RouteType.values());
		data.previousRouteCircularState = TestUtilities.randomEnum(Route.CircularState.values());
		data.previousStationName = TestUtilities.randomString();
		data.previousRouteDestination = TestUtilities.randomString();
		data.thisRouteId = RANDOM.nextLong();
		data.thisPlatformId = RANDOM.nextLong();
		data.thisStationId = RANDOM.nextLong();
		data.thisRouteColor = RANDOM.nextLong();
		data.thisRouteName = TestUtilities.randomString();
		data.thisRouteNumber = TestUtilities.randomString();
		data.thisRouteType = TestUtilities.randomEnum(RouteType.values());
		data.thisRouteCircularState = TestUtilities.randomEnum(Route.CircularState.values());
		data.thisStationName = TestUtilities.randomString();
		data.thisRouteDestination = TestUtilities.randomString();
		data.nextRouteId = RANDOM.nextLong();
		data.nextPlatformId = RANDOM.nextLong();
		data.nextStationId = RANDOM.nextLong();
		data.nextRouteColor = RANDOM.nextLong();
		data.nextRouteName = TestUtilities.randomString();
		data.nextRouteNumber = TestUtilities.randomString();
		data.nextRouteType = TestUtilities.randomEnum(RouteType.values());
		data.nextRouteCircularState = TestUtilities.randomEnum(Route.CircularState.values());
		data.nextStationName = TestUtilities.randomString();
		data.nextRouteDestination = TestUtilities.randomString();
		data.isTerminating = RANDOM.nextBoolean();
		data.interchangeColorsForStationNameList.clear(); TestUtilities.randomLoop(() -> data.interchangeColorsForStationNameList.add(TestUtilities.randomInterchangeColorsForStationName()));
		data.stoppingPoint = RANDOM.nextDouble();
		data.powerLevel = RANDOM.nextLong();
		data.speedTarget = RANDOM.nextDouble();
		data.doorTarget = RANDOM.nextBoolean();
		data.isCurrentlyManual = RANDOM.nextBoolean();
		data.vehicleCars.clear(); TestUtilities.randomLoop(() -> data.vehicleCars.add(TestUtilities.randomVehicleCar()));
		data.path.clear(); TestUtilities.randomLoop(() -> data.path.add(TestUtilities.randomPathData()));
		data.ridingEntities.clear(); TestUtilities.randomLoop(() -> data.ridingEntities.add(TestUtilities.randomVehicleRidingEntity()));
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newVehicleExtraData);
	}

	@RepeatedTest(10)
	public void testVehicleRidingEntitySchema() {
		final VehicleRidingEntity data = TestUtilities.randomVehicleRidingEntity();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newVehicleRidingEntity);
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newVehicleRidingEntity);
	}

	@RepeatedTest(10)
	public void testVehicleSchema() {
		final Vehicle data = TestUtilities.randomVehicle();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newVehicle);
		data.speed = RANDOM.nextDouble();
		data.railProgress = RANDOM.nextDouble();
		data.elapsedDwellTime = RANDOM.nextLong();
		data.nextStoppingIndexAto = RANDOM.nextLong();
		data.nextStoppingIndexManual = RANDOM.nextLong();
		data.reversed = RANDOM.nextBoolean();
		data.departureIndex = RANDOM.nextLong();
		data.sidingDepartureTime = RANDOM.nextLong();
		data.name = TestUtilities.randomString();
		data.color = RANDOM.nextLong();
		TestUtilities.serializeAndDeserialize(data, TestUtilities::newVehicle);
	}
}