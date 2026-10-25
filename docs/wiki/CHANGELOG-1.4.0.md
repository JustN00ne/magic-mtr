# MAGIC 1.4.0

For Minecraft 1.19.4, 1.20.1 and 1.20.4 (MTR 4.0.5). Changes since 1.3.5.

## New features

### Cron timetables
- Depots can run on a cron expression instead of a fixed frequency (depot screen -> `Cron`), eg
  `*/30 5-23 * * 1-5` for every 30 minutes from 5:00 to 23:00 on weekdays, with a time zone per depot. See
  [Cron timetables](FEATURES.md#cron-timetables).

### Alternative platforms
- Rebuilt on the version that worked in game, with a new menu (dashboard `...` entry) and settings synced from the
  server to every player.
- **Early choice and reservations:** a train approaching its next stop reserves that platform. If another train
  already holds it, the train is sent to a free alternative ahead of the station (within its braking distance). The
  choice is kept for that stop.
- Trains held at a signal can also be rerouted to an alternative.
- Detours are refused: the way through an alternative may be at most 1.5 times the scheduled way, plus 100 m.
- **Departure boards (PIDS):** a diverted train leaves the scheduled platform's board and appears on the board of the
  platform it really uses, with MTR's times, delay and destination.
- **Route selector:** collapsible "Alternative platforms (n)" rows under each stop; click an entry to open its menu or
  delete it.

### Railway crossings
- **Polish Level Crossing:** the old "Level Crossing" is now a single working item with a new model made from the
  Blockbench model:
  - octagonal lamp heads with hoods;
  - red checkered lens when lit, dark when off, the two lamps flashing alternately;
  - red/white pole on its base.
- It faces the player who places it, and can also be placed at 45° (for crossings on curves).
- **Bells:** real level crossing bells instead of the note block. Sneak + right-click a crossing with MTR's brush to
  choose one of 5 bell sounds (Polish bells and the British alarm); each crossing keeps its own.
- **Axle counters on the rail:** the `Axle Counter` is placed directly onto an existing MTR rail (aim at the rail and
  right-click), without changing the rail. It:
  - clamps onto the rail and follows its curve and slope;
  - detects trains passing over its exact point;
  - is removed by hitting it, drops as an item if the rail is removed, and follows the rail if it is reshaped.
- **Crossing Linker modes:** sneak + right-click cycles Inactive, Start Node, End Node and Object Linking; the icon shows
  the mode.
- **Linking (the controller is always selected first):** right-click a level crossing to select it as the controller,
  then:
  - in Start Node mode, right-click an axle counter (green outline and green preview line);
  - in End Node mode, the same in red;
  - in Object Linking mode, right-click the other level crossings that should switch with it (blue outlines only).
  Linking the same thing again unlinks it.
- **See the connections with the MTR brush:** green and red lines from the start and end counters, with arrows to the
  controller, and blue lines to linked objects, with arrows to the objects. Nothing is shown otherwise.
- **How the crossing works:**
  - it activates as soon as a train reaches either counter and clears once every train has left the far side, so it
    works in both directions and with closely following trains;
  - fail-safe: it clears after 2 minutes without any detection;
  - it outputs redstone while a train is in the crossing, and flashes and rings while it receives redstone;
  - up to 16 linked objects;
  - its state is saved with the world, and chunks are never loaded by crossings.
- Ready for future crossing equipment (eg barrier gates), which can be linked in Object Linking mode.
- Old worlds: crossing controllers and axle counter blocks already placed keep working and keep their links.

### Route types
- New train types Regional, Intercity and Trolleybus, with buttons in the route screen (the dropdown stays on screen),
  translations, System Map icons and dashboard filter colours.

## Fixes
- **Joban Client Mod:** resource packs with JCM scripts (eg `BR_Class390_MTR4.zip`) failed with "Mixin transformation of
  org.mtr.core.data.Vehicle failed". MAGIC is now built against Fabric Loader 0.17.3, so it works with the Mixin
  version of player installs. Tested with JCM 2.2.4 and 2.3.0-prerelease.1.
- **Speed limits:** a siding's maximum speed now applies to the timetable too (before it never did), and changing the
  maximum speed, acceleration or deceleration updates the timetable straight away.
- **Delay-based cancellation:**
  - trains stuck at signals are now cancelled (the delay is computed live);
  - cancelled trips leave the departure boards and stay cancelled after a restart;
  - a train is only cancelled once its delay has stayed above the threshold for 5 s, so trains that are on time are no
    longer cancelled as they depart;
  - diverted trains are no longer cancelled when leaving an alternative platform;
  - in multiplayer the depot screen shows and edits the server's settings.
- **Screens:**
  - the depot screen's Cancellations button no longer overlaps MTR's controls;
  - the siding screen's dwell sliders are no longer under it;
  - MAGIC buttons no longer disappear after resizing the window.
- **Rails:** rail rotation is rendered again (it was silently disabled by the tilt feature).
- **System Map:** the new route types show their icons, and icons are no longer squeezed next to names.
- **CJK names:** CJK-only names no longer lose parts, and more CJK characters are recognised.
- **Rail connector icons:** the "selected" icon variants are shown again.
- **Web dashboard (port 8088):**
  - the train type, usage, gauge and electrification filter buttons now filter;
  - routes report their real type;
  - the filter panel no longer swallows clicks.
- **Item icons and names:**
  - the axle counter's icon is no longer drawn tiny with the wrong texture;
  - the level crossing and axle counter use their edited textures;
  - item names no longer show raw translation keys.
- "Ferry" was removed from the train route types (boats are MTR's own transport mode); a saved ferry route shows as
  Train.

## Performance
- Axle counters no longer search every rail node of the dimension every few ticks. Train positions are read once per
  second per dimension, shared by all counters.
- Train detectors in "seconds offset" mode use an index instead of scanning every train's path twice every 2 ticks.
- Rail tilt lookups use a spatial index; dashboard rail merges are throttled; the riding-car roll is computed once per
  frame.
- MTR's file loader uses a bounded thread pool.

## Known limitations
- Cron: the System Map's departure lists (not the PIDS boards) still show departures on days the expression excludes.
  Trips longer than 24 hours are not supported.
- Crossings: a train that enters the section and reverses back out keeps the crossing active until the 2-minute
  fail-safe.
- Alternative platforms are configured per route and platform, not per visit, for routes that stop at the same
  platform twice.
- MAGIC's settings do not check permissions: any player who can open MTR's screens can change them.

## Tested
- Unit tests: 73, all passing.
- Dedicated server on a copy of a test world: trains are diverted to the alternative platform when the main one is
  held, dwell with 0 s delay and continue; a train waits when both platforms are taken.
- In game (Fabric Loader 0.17.3, MTR 4.0.5):
  - JCM resource packs load;
  - the level crossing model, lamps, bells, facings and 45° placement;
  - axle counters on rails, linking with the linker, the brush view;
  - a real train switching a crossing on and off.
- Not tested in game: links surviving a restart (covered by unit tests) and multiplayer with several players.
