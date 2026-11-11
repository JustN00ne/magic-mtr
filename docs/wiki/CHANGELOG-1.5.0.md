# MAGIC 1.5.0

Timetables, overtaking, crossings and live map update. Changes since 1.4.2.

## New

- **Flexible route timetables.** MTR's route screen has a new **Timetable** button that opens the route's settings.
  For each stop of the route you can set the time the train leaves it, counted from the train's departure from its
  depot (`H:MM:SS`, for example `0:05:00` for Station 1 and `0:15:30` for Station 2). Leave a stop blank to keep its
  platform's dwell time.
  - MAGIC turns these times into the dwell time at each stop, so MTR's own timetable matches them. Arrival boards,
    delays and the "early trains wait" option keep working as before.
  - **Minimum dwell time** (for the whole route): no stop is ever shorter than this. If a train arrives too late to
    leave on time, it waits for the minimum dwell time instead of the scheduled departure. This also covers late
    trains that MTR shortens stops for (the siding's "reduce dwell time" setting).
  - Saving refreshes every depot that runs the route. A time only applies while its stop is still the same platform,
    so adding or removing stops does not move the other stops' times.
  - Times are real time, also for depots on Minecraft time.
  - Trains that arrive early only wait for their departure time if the siding's "early trains increase dwell time"
    option is on, as in MTR.
  - Stored in `config/MAGIC/configs/route_timetables.json` on the server and sent to players.
- **Overtaking at stations with alternative platforms, by headcode class.** The route settings have a **headcode
  class**, as on British railways (and Stepford County Railway): **9** high speed, **1** semi-fast, **2** stopping and
  suburban, **3** trains leaving the depot. Trains overtake in this order: 9 late > 9 > 1 late > 1 > 2 late > 2 >
  3 late > 3 (late: at least a minute behind time).
  - A train with a class that is still on its way from the depot to its first stop runs as class 3. Routes without a
    class neither overtake nor are overtaken.
  - When a train approaches a station where its route has alternative platforms, MAGIC checks the timetables of the
    trains behind it. If a train of a higher priority would get within a minute of it on the stretch they share after
    the station, the slower train is sent to a free alternative platform that is not on the faster train's path.
  - The slower train keeps its doors open there until the faster train has passed the station's normal platform (at
    most 5 minutes), then leaves behind it.
  - Set the environment variable `JME_DEBUG_ALTERNATIVES=1` on the server to log each decision.
- **Simpler alternative platform selector.**
  - While editing a route, a big **Alternative platforms...** button under the stop list opens a screen with all the
    route's stops on the left and the selected stop's platforms on the right, with **Add** / **Remove** buttons. It
    replaces the small `≣` and `⎇` icons and the map-click mode.
  - The "⎇ Alternative platforms" folders are gone from the route's stop list: the list only shows stops again, so
    they can be reordered normally. A stop with alternatives shows a mark after its name (for example `⎇2`).
  - Right-click a stop → **Alternative Platforms** also opens it, at that stop.
- **Tutorials** for players: `TUTORIAL-ALTERNATIVE-PLATFORMS.md` and `TUTORIAL-LEVEL-CROSSING.md` in the wiki.
- **Directional axle counters.** Right-click an axle counter with MTR's brush to switch between three modes: detect
  trains in both directions, only in one direction, or only in the other. The chat message names the direction (for
  example "only trains heading north-east"). The crossing controller's screen marks one-direction counters. Existing
  counters keep detecting both directions.
- **Train detectors linked to a crossing.** In the Crossing Linker's Object Linking mode, select a controller and then
  click a train detector: MAGIC's own detector, or any MTR powered train sensor. While a linked detector is powered,
  the whole crossing closes: lights, gates, linked objects and the redstone output. Up to 16 detectors per crossing.
  They are listed (and can be unlinked) in the controller's screen, and shown with orange lines while you hold the
  brush.
- **Live map (`/livemap` on the web dashboard, port 8088): trains follow the real track.**
  - Between updates, a train moves along the rails at its real speed and acceleration, and stops where it brakes,
    instead of cutting straight across curves.
  - Trains are updated every second (before, every 2 to 4 seconds).
  - With a track map style selected (Infrastructure, Speed, ...), trains are drawn at their real position on the rails,
    with a small arrow showing which way they run. The line diagram keeps placing them on their line.
  - **Speed:** shown in the train's panel, together with the speed limit of the track it is on, and in train lists and
    the marker's tooltip. A new map menu option, **Speed on trains**, shows it on every marker.

## Fixes

- **Camera tilt.** The camera banked the wrong way on some track headings, and hardly at all on east–west track: the
  roll was applied around the world axis instead of the train's direction of travel. It now banks around the train's
  forward axis, the same way as the train.
- **Camera tilt on 1.16.5–1.19.2.** The camera did not tilt at all on these versions.
- **Live map positions.** Trains were drawn at the middle of the rail they were on, not where they were: on long rails
  that was tens of blocks off. They are now drawn where the front of the train is.
- **Alternative platforms at termini.** Where one route of a depot ends and the next one starts at the same platform,
  the alternatives configured on either route now apply (before, only one of the two routes was looked at). The
  alternatives screen shows when a stop is shared like this.
- **Siding screen with Tianjin Metro.** Tianjin Metro's "Duplicate Settings" button and logo were drawn over MAGIC's
  "Manual" checkbox and "Cancellations" button. With Tianjin Metro installed, MTR's rows keep their own places and
  MAGIC's always visible maximum speed slider and "Cancellations" button go around Tianjin Metro's button.
