# MAGIC 1.4.2

Level crossing update. Changes since 1.4.1.

## New

- **Crossing gates (left and right).** `Crossing Gate (Left)` and `Crossing Gate (Right)`: a barrier whose arm lowers
  when its crossing closes and rises when it opens again (5 s either way, with a small bounce). Place it facing the
  road (like the level crossing, in 45° steps); a left gate's arm swings to the left as seen from the road in front of
  it, a right gate's to the right, so both sides of the road can be closed whatever side traffic drives on. Link it to
  a crossing controller with the Crossing Linker in Object Linking mode. It also lowers while it receives a redstone
  signal, and emits a redstone signal (15) while its controller has it closed (eg to stop road vehicles).
- **Gate settings.** Right-click a gate with MTR's brush: arm length from 4 to 11 blocks (shown in a plan view), and
  its bell sound or no sound (gates are silent by default).
- **Railroad Crossing Controller.** A lineside cabinet that controls a whole crossing, so the controls do not have to
  be in one of the level crossings: it controls all the crossing's components (level crossings and gates). It has no
  lamps or bell, and emits a redstone signal (15) while the crossing is active. Level crossings that already control a
  crossing keep working as before.
- **Controller screen.** Right-click a Railroad Crossing Controller or a level crossing with MTR's brush:
  - *Components*: every start and end axle counter and every level crossing and gate it controls, with coordinates and
    distance; each can be unlinked from here.
  - *Timeline & Controls*: automatic or closed by hand, and a timeline editor (drag the markers, or ±0.05 s / ±0.5 s
    buttons) for when the lights switch on and the gates start lowering after a train is detected, and when the gates
    start rising and the lights switch off after the crossing clears. Defaults: lights at once, gates 3 s later; both
    open as soon as the crossing clears (as before).
  - *Lights* (level crossings): bell sound, or **no sound** (eg for crossings only used to link sections), and the
    flash rate. Sneak + right-click a level crossing with the brush opens this tab directly.
- The screens cover the whole screen, slightly transparent, and do not pause the game.
- **Portuguese level crossing bell** (Bell 6), recorded at a level crossing in Lisbon. Choose it in a level crossing's
  or gate's screen.
- **Faster speed limits.** Siding maximum speeds now go from 1 to 500 km/h, then "No limit" (was up to 299 km/h, then no
  limit), so trains can be capped above 300 km/h. MAGIC rail connectors and the speed brush go up to 500 km/h (was
  400). Sidings set to "No limit" before stay unlimited.
- **Livemap.** A live map of the network in the browser, as a dark transit map (design by JN): every line in its
  colour, drawn side by side with the others where they share track (both directions and both tracks of a line count
  as one line), stations as capsules across the lines that stop there, interchanges as larger squares, and smaller
  stations as a tick with their name along the line. Two-language MTR names ("Name|名字") show both. Trains move in
  real time on their own line, with their line number and a delay dot (on time, +1–4 min, +5 min, early,
  cancelled).
  - A panel on the left with a search ("Search for anything": stations, lines, trains) and three tabs: *Stations*
    (lines stopping there and the share of their trains running on time right now), *Delays* (every train, most
    delayed first) and *Routes* (each line with its trains). Click a station, line or train for its details: the
    trains heading to a station, a line's stops and trains (the other lines fade), or a train's destination, delay,
    next stop, speed and stops (with *Follow train*).
  - The layers button (top right) switches between the live lines and the map styles of `map-style.yaml`, shows or
    hides the world map, and sets the theme (dark, light or system) and the language: English, French, Dutch,
    Portuguese (Portugal), Polish, Czech, Russian, Chinese (Simplified), Korean, Japanese or Spanish. Each viewer's
    choices are remembered in their browser. Works on phones (the panel becomes a sheet at the bottom).
  - Open it from the MAGIC menu (the M button on the map of MTR's Dashboard) → *Open Livemap in browser*, from the
    *Open Livemap* button in the MAGIC web dashboard, or at `http://localhost:8088/livemap`. Like the web dashboard,
    it is served by the computer running the world (singleplayer or LAN host); trains appear only while the system
    map shows vehicles.
- **Web dashboard redesigned after OpenRailwayMap.** The map at `http://localhost:8088/` now looks like
  openrailwaymap.app: a style bar (Infrastructure, Max speed, Electrification, Track gauge), a layers menu, a legend,
  search for stations, lines and railways, a scale bar in blocks, a light and a dark theme, and text along the lines
  (voltage, speed, gauge). Click a rail to see its properties and rail ID; click a legend entry to hide those rails.
  Rails without a `map.yaml` entry are drawn too: as a line where a route runs, otherwise as a siding, and with their
  MTR speed limit in the speed style.
- **Map styles in a server file.** `config/MAGIC/config/map-style.yaml` holds every style and colour (by default those
  of the OpenRailwayMap map key). Change colours, widths, dashes and texts, or add your own styles that colour rails by
  any `map.yaml` property, including ones you add yourself (eg an `owner`). The Livemap can show the same styles
  under its live trains (its layers button). See the wiki page *MAGIC Map Style*.
- `map.yaml` keeps properties MAGIC doesn't know, so custom styles can use them, and `routeIds` now match every rail
  on the route's generated path (before, only the rails at its platforms).

## Changed

- **Axle counters ("balises") link at any distance, either way round, and several per controller.** A controller can
  have up to 16 start and 16 end nodes (eg one per track); trains reaching different counters of the same side in the
  same tick are all counted. In Start / End Node mode you can right-click the controller and then the counter, or the
  counter first and then the controller. The selection no longer cancels when you walk away (only when you put the
  linker away, set it to Inactive or change dimension). Objects (level crossings, gates) are still limited to 256
  blocks from their controller.
- **Far axle counters now work.** A counter in a chunk that is not loaded (eg several hundred blocks up the line) never
  detected trains, so long links seemed not to work. The controller now checks the counter's rail point in MTR's
  simulation itself, which runs everywhere. Counters linked before 1.4.2 switch to this once they have been loaded once.
- **Crossings work beyond the simulation distance.** Minecraft (1.18+) only ticks blocks within the simulation
  distance, so a crossing you could see further away stayed dark with its gates up. Controllers, level crossings and
  gates now run in every loaded chunk.
- Sneak + right-click on a level crossing with the brush now opens its Lights settings instead of cycling the bell.

## Compatibility

- Worlds from older versions load unchanged: controllers keep their counters and objects and get the default
  timeline. The first start / end counter is still saved in the old keys, so a world opened in an older MAGIC keeps
  at least that one.
