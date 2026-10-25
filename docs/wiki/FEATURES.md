# MAGIC (jme) - Features & How To Use Them

Mod ID: `jme`  
Config root folder: `.minecraft/config/MAGIC/`

## Install/Requirements

Required:
- Fabric Loader + Fabric API
- Minecraft Transit Railway (MTR)

Optional (recommended):
- Mod Menu (to open settings easily)
- Cloth Config or YACL (nicer settings UI)
- BlueMap (server-side rail layers)

Multiplayer note:
- Many features are client-side visuals/UI, but anything that changes rails, routing, sensors, or server data requires MAGIC on the server as well. For best results, install MAGIC on both client and server.

## Where Files Are Saved

MAGIC stores everything under `.minecraft/config/MAGIC/`:
- Main settings: `configs/magic.json` (migrates from legacy `jme.json`)
- Rail tilt registry: `configs/rail_tilt.json`
- Alternative platforms: `configs/alternative_platforms.json`
- Route type overrides: `configs/route_types.json`
- Dashboard route folders: `configs/dashboard_folders.json`
- Dashboard map areas: `configs/dashboard_areas.lzma2`
- Siding slider cache: `configs/siding_speed_slider.properties`
- Depot cancellations (advanced/hidden UI): `configs/depot_cancellations.json`
- System Map overrides: `map/system_map.css`, `map/system_map.js`
- System Map overlay cache (optional): `map/system_map_overlay_cache_<worldKey>_<dimension>.lzma2`
- Dashboard overlay cache (automatic): `map/dashboard_overlay_cache_<context>_<dimension>.lzma2`
- Exports: `exports/`

## Opening MAGIC Settings

You can open MAGIC settings in several places:
- MTR "Config" screen: click the MAGIC icon button (top-right).
- Mod Menu: click Configure on MAGIC.
- System Map (port 8888): use the in-page MAGIC menu (top-right).

MAGIC chooses the best available settings UI automatically:
1. Cloth Config (if installed)
2. YACL (if installed)
3. MAGIC built-in settings screen (tabs: General, Dashboard, System Map, Track Colors)

Common settings that affect multiple features:
- `Use mph`: changes MAGIC speed labels (connector UI, sliders, etc) and also patches MTR's vehicle speed text (`gui.mtr.vehicle_speed`) to show mph instead of km/h.
- `Camera Tilt` + `Camera Tilt Strength`: controls whether (and how strongly) the camera rolls while riding on tilted/canted rails.

## Items & Blocks (MAGIC Creative Tab)

MAGIC adds a creative tab named `MAGIC` with the following player-facing items/blocks.

### MAGIC Universal Rail Connector

Item name: `MAGIC Universal Rail Connector`

What it does:
- Places rails like MTR's rail connector, but lets you configure:
  - speed limit (1..400 km/h, clamped to steps of 10)
  - rail model/style
  - easing (rail shape)
  - rail cant/tilt (start/middle/end, -45..45 degrees)

How to open the config screen:
1. Hold the connector in your hand.
2. Sneak (Shift).
3. Right-click (use) either in the air or on a block.

Connector config screen (exact labels/controls):
- Title text: `Rail Connector`
- Slider: `Speed` (displays `km/h` or `mph` depending on settings)
- Button: `Style: ...` (opens the rail model list)
- Button: `Easing: ...` (cycles shapes like `Quadratic (Default)`, `Two Radii`, `Cable`, etc)
- Sliders (each shows `X deg`):
  - `Tilt Start`
  - `Tilt Middle`
  - `Tilt End`
- Preview panel:
  - Label: `Preview`
  - Small toggle button: `||` / `>` (toggles the animated train preview in the 3D preview)
- Button: `Done` (saves and closes)

Saving behavior:
- `Done` saves immediately.
- Pressing `Esc` also saves (the screen saves on close).

What gets applied when you place rails with this connector:
- Speed limit and style/easing are applied to normal rails (not platforms, not sidings, not turnback rails).
- Tilt is stored per rail in `config/MAGIC/configs/rail_tilt.json` and affects rendering/vehicle roll.

Legacy support:
- Older MAGIC versions used MTR's `rail_connector_300` item as the universal connector. MAGIC still treats a `rail_connector_300` as universal if (and only if) it already has MAGIC NBT on it.

Tooltip:
- In inventory tooltips, the connector shows:
  - `MAGIC speed: ...`
  - `MAGIC style: ...`
  - `MAGIC easing: ...`
  - `MAGIC tilt: start/middle/end deg`

### MAGIC One-way Rail Connector

Item name: `MAGIC One-way Rail Connector`

How to use:
- Same config screen and controls as the Universal Rail Connector (Shift + Right-click to configure).
- When placing one-way rails, MAGIC preserves which direction is blocked and applies the configured speed only to the allowed direction.

### Train Detector

Block name: `Train Detector`

What it does:
- A powered sensor block that outputs redstone power when a matching train is detected.
- Supports two detection modes:
  - Node range mode: detect trains within N reachable rail-graph nodes of the detector
  - Seconds offset mode: detect trains based on time to/from passing the detector's rail position

How to use:
1. Place the `Train Detector` block near a rail (it will attach to the nearest rail node/segment it can find).
2. Right-click the block to open its GUI.
3. Configure filters and detection mode (details below).

Train Detector GUI (exact labels added by MAGIC):
- Text field: `Activation Range (Nodes)` (1..64)
- Text field: `Activation Offset (Seconds)` (-86400..86400)
- Checkbox: `Use Seconds Offset`

Filter controls (from MTR sensor UI):
- Route filter list (select which routes are allowed to trigger this sensor)
- Stopped/moving filters (if present in your MTR version's sensor UI)

Detection modes (how "Activation Offset (Seconds)" works):
- If `Use Seconds Offset` is OFF:
  - The detector powers when any matching train occupies any node within `Activation Range (Nodes)` nodes of the detector's node.
- If `Use Seconds Offset` is ON:
  - `0` seconds: powers while a matching train is currently over the detector point.
  - Positive value (e.g. `15`): powers if a matching train will reach the detector point within the next N seconds (prediction based on current speed/progress).
  - Negative value (e.g. `-10`): powers if the matching train's tail cleared the detector point within the last N seconds.

### MTR Train Schedule Sensor: Rail Detect Enhancement

What it does:
- MAGIC adds a server-side detection path to MTR's `Train Schedule Sensor` so it can power when a matching train is currently on the rail node nearest to the sensor block.

How to use:
1. Place an MTR `Train Schedule Sensor` near a rail.
2. Configure its existing filter UI normally (routes, etc).
3. When a matching train occupies the nearest rail node to the sensor, the sensor will power.

### Decorative Platform Blocks

Blocks (each also has a slab variant):
- German Platform, German Platform Slab
- Polish Platform, Polish Platform Slab
- Czech Platform, Czech Platform Slab
- Portuguese Platform, Portuguese Platform Slab
- Blue American Platform, Blue American Platform Slab
- Swedish Platform, Swedish Platform Slab
- Dutch Platform, Dutch Platform Slab
- Tactile Platform (Blue 1/2), slab variants
- Tactile Platform (White 1/2), slab variants
- Tactile Platform (Yellow 1/2), slab variants

These are cosmetic building blocks (non-opaque platform-style blocks).

## Rail Profile Tools

### Brush "Copy/Apply Rail Profile" (using MTR Brush on a Node)

What it does:
- Lets you copy a rail's profile (speed/style/easing/tilt) into the MTR Brush item, then apply it to other rails.

How to copy:
1. Hold the MTR `Brush` item.
2. Look at (face) a rail you want to copy settings from.
3. Right-click an MTR `Node` block.
4. MAGIC copies the facing rail's profile into the Brush NBT and syncs it to the server.

How to apply:
1. Hold the same MTR `Brush` item that contains a copied profile.
2. Look at a rail you want to update.
3. Sneak (Shift).
4. Right-click an MTR `Node` block.
5. MAGIC applies the stored profile to the facing rail and syncs it to the server.

What is included in the profile:
- Speed (clamped to steps of 10 in the 1..400 km/h range)
- Style (rail model id)
- Easing/shape
- Tilt start/middle/end (degrees)

One-way rails:
- Applying a profile preserves blocked directions (0 speed in blocked direction stays blocked).

## Rail Tilt (Cant) System

MAGIC's tilt affects:
- Rail surface rendering (canted track bed)
- Rail models (where possible)
- Vehicle roll on tilted rails
- Optional camera roll while riding (configurable)

Camera tilt (what it is):
- When `Camera Tilt` is enabled, MAGIC rolls the camera while you are riding an MTR vehicle, based on the tilt of the nearest tilted rail. The effect is smoothed and multiplied by `Camera Tilt Strength` (0.0 to 2.0).

### Setting Tilt While Placing Rails (Connector Screen)
- Use the connector config screen sliders:
  - `Tilt Start`, `Tilt Middle`, `Tilt End`
- Place rails normally with the connector; tilt is stored per rail in `config/MAGIC/configs/rail_tilt.json`.

### Editing Tilt On Existing Rails (MTR Rail Modifier Screen)

Where:
- Open the MTR Rail Modifier GUI for a rail.

What MAGIC adds:
- A collapsible section button:
  - `Tilt ▶` (collapsed)
  - `Tilt ▼` (expanded)
- Three sliders (shown when expanded):
  - `Tilt Start`
  - `Tilt Middle`
  - `Tilt End`

Changing any tilt slider:
- Updates `config/MAGIC/configs/rail_tilt.json`
- Syncs to the server immediately

## Track Speed Coloring & Labels

### Speed-Based Rail Colors

What it does:
- MAGIC overrides rail coloring for normal rails (not platforms/sidings/turnback) based on the rail's speed limit.

Where you see it:
- In-world rails
- Dashboard rail overlay
- System Map overlay (when `Show Details` is enabled in settings and the overlay is visible)
- BlueMap speed layer (if BlueMap integration is enabled)
- Preview coloring while dragging/placing rails (uses the held connector's configured speed)

Settings:
- `Track Color Mode`:
  - `OPEN_RAILWAY_MAP` (default)
  - `MTR_DEFAULT`
  - `CUSTOM_GRADIENT`
- Custom gradient stops (if using `CUSTOM_GRADIENT`):
  - In Cloth Config: edit the list `Custom Gradient Stops` using `speed=#RRGGBB` format.
  - In YACL: use the `Custom Gradient Editor` button.
  - In the built-in settings UI: use `Edit Custom Gradient`.

### In-World Speed Text

What it does:
- MAGIC renders a small `NNNkmh` (or `NNNmph`) label periodically on rails that have a speed limit.

Notes:
- The label respects `Use mph` (switches to `mph`).
- This feature is disabled by default because rendering lots of floating text can reduce FPS on large networks.

## Dashboard (In-Game Map) Features

These features apply to MTR's in-game Dashboard screens.

### Rail/Vehicle Overlay (with Culling)

What it does:
- Draws a rail network overlay on the Dashboard map, including:
  - rails (with speed coloring)
  - signal arrows (when zoomed in enough)
  - vehicles (when zoomed in enough)
- Uses a persistent cache so rails/vehicles don't instantly disappear when chunks unload.

Settings (MAGIC settings -> Dashboard):
- `Rail Overlay Mode`:
  - `ALL`: draw all known overlay rails
  - `CULL`: limit how many rails are drawn per map cell (for FPS)
  - `OFF`: disable overlay rendering
- `Overlay Cull Max (Per Cell)`: only used when mode is `CULL`

### Route Destination Folders (Dashboard Route Editing)

What it does:
- When you edit a route's destinations in the Dashboard, MAGIC can show the stop list as folders.
- Folders exist only in the Dashboard UI; they do not remove stops from the route.

Enable/disable:
- MAGIC settings -> Dashboard -> `Route List Layout`
  - `FOLDERS`: folder UI is active
  - `FLAT`: uses the normal flat platform list

Folder row visuals (what you will see in the route list):
- Collapsed: `▶ 🗀 FolderName`
- Expanded: `▼ 🗀 FolderName`
- Child stops (when expanded) are prefixed with bullets; the first stop in a folder is marked with a star.

Click behavior:
- Click a folder row to expand/collapse it.
- Click a platform row to edit that route destination.

Right-click context menu (exact button labels):
- `Make Folder`
- `Edit Folder` (folders only)
- `Sort` (folders only)
- `Remove`
- `Duplicate` (platform rows only; duplicates the stop in the route list)

Folder edit/create screen fields:
- `Name`
- `Icon Color (#RRGGBB, optional)`

Drag & drop:
- You can drag folder rows to reorder/nest folders.
- You can drag a non-folder platform row onto a folder to add that platform to the folder.

Storage file:
- `config/MAGIC/configs/dashboard_folders.json`

### Dashboard Map Auto-Save

What it does:
- Remembers the last Dashboard map position/zoom and restores it next time you open the Dashboard.

Setting:
- MAGIC settings -> Dashboard -> `Auto-save Dashboard Map`

Storage file:
- `config/MAGIC/configs/dashboard_areas.lzma2`

### Export Rails From Dashboard

What it does:
- Adds a MAGIC icon button on the Dashboard map (top-right of the map widget).
- Exports the rail network to image/vector files.

How to use:
1. Open the Dashboard.
2. Click the MAGIC icon on the map.
3. Click one of the menu entries:
   - `Export PNG (viewport)`
   - `Export SVG (viewport)`
   - `Export SVG (all rails)`

Where exports go:
- `config/MAGIC/exports/`
- Filenames are timestamped, e.g. `rails-20260101-235959-viewport.png`

## Alternative Platforms (Dynamic Platform Rerouting)

What it does:
- Lets a route stop use other platforms in the same station as alternatives.
- MAGIC can select an alternative platform at departure time (depot/siding dispatch) and can also reroute mid-route if a train gets stuck behind a signal before a station throat.
- Early choice: a train approaching its next stop (35 path nodes or less) claims that platform; the first train to
  claim a platform keeps it. If the platform in the train's path is already claimed by another train, the train is
  sent to a free alternative while it can still change track (beyond its braking distance), and keeps that choice.
- Departure boards (PIDS) follow the train's path: a train sent to an alternative platform disappears from its
  scheduled platform's board and appears on the board of the platform it really uses, with the same times.

### Configuring Alternatives In The Dashboard

Where:
- In the Dashboard, when editing a route destination.

Buttons added by MAGIC (to the right of the custom destination field, only while a route stop is being edited and
its station has another platform of the same transport mode):
- `≣` opens the alternative platforms menu.
- `⎇` toggles map-click selection mode; when active it shows `⎇*`.

The same menu is also available from the `...` menu of a route stop (`Alternative platforms`).

In the route's stop list, a stop with alternatives gets a collapsible `⎇ Alternative platforms (n)` row below it
(click to open or close; remembered in `dashboard_folders.json`). Open, it lists each alternative. Clicking one opens
the menu; its delete button (or `Remove alternative` in its `...` menu) removes that alternative, not the stop.

Method A: Alternative platforms menu
1. Select a route destination (platform) you want to configure.
2. Click `≣`.
3. The menu shows the primary platform (`★ Primary`), the assigned alternatives (`Remove`) and the other platforms of
   the same station that can be added (`Add`). Changes apply immediately and are synced to the server.
4. `Use every platform in station` toggles the wildcard (all compatible platforms of the station, including ones built later).

Method B: Map-click selection mode
1. Select a route destination (platform) you want to configure.
2. Click `⎇` so it becomes `⎇*`.
3. Click other platforms of the same station on the map to toggle them as alternatives (other platforms are ignored).
4. Click `⎇*` again to exit selection mode.

Rules:
- Only platforms of the same station and transport mode can be alternatives. Entries that become invalid (platform
  removed or moved) are ignored by trains and shown as `Unavailable platform` in the menu so they can be removed.
- Trains always use the primary platform unless it is blocked or busier than an alternative.

Storage file (server side; the server sends its state to every client):
- `config/MAGIC/configs/alternative_platforms.json`

Advanced wildcard:
- An alternatives list containing `-1` means "all compatible platforms in this station" (the menu toggle above).

Enable/disable:
- Global toggle: `config/MAGIC/configs/magic.json` -> `alternative_platforms_enabled`
- Optional per-route override: `config/MAGIC/configs/route_types.json` -> `alternative_platforms_enabled` (map of route ID to boolean)

## Route Types (Extra Train Route Types)

What it does:
- Extends the Train route type selector to support more display types (used by UI and the System Map).

Where:
- MTR `Edit Route` screen, for `TransportMode.TRAIN` routes.

How to use:
1. Open the route edit screen.
2. Click the route type button (MAGIC replaces the cycle behavior with a dropdown that always fits on screen).
3. Choose one of: Train (MTR's normal type), Metro, Commuter Rail, Regional Rail, Intercity, High-Speed Rail,
   Light Rail, Tram, Bus, Trolleybus, Ferry, Freight.

Storage file (for non-vanilla types; server side, sent to every client so all players see the same type):
- `config/MAGIC/configs/route_types.json`

System Map:
- Every type has its own icon and label on the System Map (route list, route details and directions), eg
  Regional Rail, Intercity, Trolleybus and Ferry no longer fall back to "Train", and the icon and route name are
  spaced like MTR's own entries.

## Siding Screen: "Maximum Drive Speed" Improvements

What it does:
- Keeps the `Maximum Drive Speed` slider visible even when manual mode is disabled.
- Shows `No limit` at the max slider value.
- Internally stores a high cap (1000 km/h) for `No limit` to avoid vanilla MTR's 300 km/h ceiling.
- Remembers the slider position per siding.

How to use:
1. Open an MTR Siding screen.
2. Use the `Maximum Drive Speed` slider.
3. Set it to the maximum value to show `No limit`.
4. Close the screen; MAGIC syncs the updated siding speed to the server.

Storage file:
- `config/MAGIC/configs/siding_speed_slider.properties`

## System Map (Port 8888) Enhancements

MAGIC injects an overlay and a menu into the MTR System Map web UI (usually `http://<server>:8888/`).

### Hide Player/Clients Layer

Setting (server-side): `Hide Player`
- When enabled, the System Map `/clients` layer will be empty (your player marker and other clients will not be shown).

### MAGIC Menu Location
- The MAGIC menu button appears in the top-right corner of the System Map page.

### Rails Overlay Menu (Exact Options)

Section: `Rails Overlay`
- `All`
- `Cull`
- `Off`
- Field (visible when Cull is selected): `Cull max per cell`

Section: `Overlay Layers` (these toggles apply in your browser; they are stored in local storage)
- `Rails (orange)`
- `Details (speed, signals, trains)`
- `Signals`
- `Vehicles`
- `Respect route filters`

Section: `Line Mode` (visible when `Details` is enabled)
- `Speed` (default)
- `Usage`
- `Freq`
- `Delay`

Section: `Config` (these controls patch `config/MAGIC/configs/magic.json` on the server)
- `Use mph`
- `Camera tilt`
- `Camera tilt strength`
- `Route list mode`
- `Map auto-save`
- `Name language`
- `Overlay cache`
- `Persist cache to disk`

Section: `Custom Styling`
- Custom CSS editor (injected live):
  - `Preview`
  - `Save CSS`
  - `Clear`
- Custom JS editor (runs on page load):
  - `Save JS`
  - `Clear`

Section: `Export Rails`
- `PNG (viewport)` (downloads `magic-rails-<dimension>-viewport.png`)
- `SVG (viewport)` (downloads `magic-rails-<dimension>-viewport.svg`)
- `SVG (all rails)` (downloads `magic-rails-<dimension>-full.svg`)

### System Map Name Language Modes

Setting: `Name language` / `system_map_language_display`
- `NORMAL`:
  - Station names containing `|` are reduced to a 2-line stacked label: `CJK|Other` (CJK shown first, larger).
  - If no clean 2-line split is possible, names are joined into one line with ` / `.
- `CJK_ONLY`: filters to the CJK portion (keeps digits/punctuation for readability).
- `NON_CJK_ONLY`: filters to the non-CJK portion.

### System Map Overlay Cache

Setting (server-side): `Overlay cache`
- When enabled, MAGIC merges rails/vehicles snapshots into a long-lived server-side cache so rails/vehicles can remain visible even when chunks unload.

Setting (server-side): `Persist cache to disk`
- When enabled, the cache is saved under `config/MAGIC/map/` as `.lzma2` files.

## BlueMap Integration (Optional)

What it does:
- If the BlueMap mod is installed on the server, MAGIC can publish rail layers as BlueMap marker sets.
- Two marker sets are supported:
  - Base rails/type layer (by default labeled `MAGIC Rails`)
  - Speed-colored layer (by default labeled `MAGIC Rails (Speed)`)

Where to configure:
- MAGIC settings -> `BlueMap` (Cloth Config/YACL)
- Or edit `config/MAGIC/configs/magic.json` directly

Common useful settings (keys in `magic.json`):
- `blue_map_enabled`
- Refresh timing:
  - `blue_map_refresh_interval_seconds`
  - `blue_map_refresh_initial_delay_seconds`
- Marker set ids/labels:
  - `blue_map_base_marker_set_id`, `blue_map_base_marker_set_label`
  - `blue_map_speed_marker_set_id`, `blue_map_speed_marker_set_label`
- Line width and vertical bias:
  - `blue_map_base_line_width`, `blue_map_speed_line_width`
  - `blue_map_line_y_bias`
- Base layer colors:
  - `blue_map_base_color`
  - `blue_map_base_platform_color`
  - `blue_map_base_siding_color`
  - `blue_map_base_turn_back_color`
- Speed layer overrides:
  - `blue_map_platform_rails_force_red_enabled`, `blue_map_platform_color`
  - `blue_map_high_speed_threshold_kmh`, `blue_map_high_speed_color`
  - `blue_map_high_speed_rails_force_red_enabled`

## Commands

MAGIC registers client commands under `/magic`.

All commands are under `/magic settings ...`:
- `/magic settings resources reload`
  - Reloads MAGIC state and triggers a client resource reload.
- `/magic settings functions reloadtilt`
  - Reloads `magic.json` and clears camera/vehicle tilt smoothing caches.
- `/magic settings functions reloadstate`
  - Reloads MAGIC state from disk (config + registries) and clears tilt smoothing caches.
- `/magic settings rails syncPositionsOnDash`
  - Forces the Dashboard rail node/rail-position cache to sync from the live client cache (useful if the Dashboard overlay seems out of date).

## Advanced/Hidden Features

### Depot Delay Cancellations

What it does:
- Cancels a depot's trains whose delay exceeds a threshold: the train is despawned immediately, its remaining stops
  disappear from every departure board (also after a server restart) and a "Service Cancelled" notice is shown at
  its current platform.
- The delay is computed live (MTR only refreshes a train's delay when it starts or stops, so a train held at a red
  signal never looked late before).

Where:
- MTR depot screen -> `Cancellations` (next to `Instant Deploy`).

Storage (server side, sent to every client so the screen shows the server's settings in multiplayer):
- `config/MAGIC/configs/depot_cancellations.json` (settings) and `canceled_trips.json` (cancelled trips)

File format example:
```json
{
  "12345": {
    "enabled": true,
    "threshold_minutes": 120,
    "action": "despawn"
  }
}
```
(`return_to_depot` from older versions is treated as `despawn`.)

## Cron Timetables

What it does:
- Schedules a depot's departures with a standard five-field cron expression
  (`minute hour day-of-month month day-of-week`) instead of MTR's hourly frequencies or departure list.
- Depots without a cron timetable keep using MTR's own scheduling unchanged.

Where:
- MTR depot screen -> `Cron` (next to `Cancellations`; shows `Cron: On` while active).

Screen:
- `Expression`: the cron expression. The screen checks it as you type and previews the next departures.
- `Time zone`: blank for the server's zone, or eg `Europe/London`, `America/New_York`, `UTC`, `+08:00`.
- `Clock`: `Real world` (wall-clock time in that zone; sets the depot to MTR's real-time mode) or `Minecraft`
  (Minecraft time of day; the day, month and weekday fields are ignored).
- `Enabled` / `Disabled`, `Remove`, `Cancel`, `Save`. Saving regenerates the depot (like MTR's Refresh button).

Syntax:
- `*` any value; lists `0,15,30,45`; ranges `5-23`; steps `*/30`, `5-23/2`, `10/15` (from 10 to the end, every 15).
- Month names `JAN`-`DEC`, weekday names `SUN`-`SAT`; weekday `0` and `7` are both Sunday; `?` = `*` in the day fields.
- Macros: `@yearly`/`@annually`, `@monthly`, `@weekly`, `@daily`/`@midnight`, `@hourly`.
- As in standard cron, if both day-of-month and day-of-week are restricted, a day matches if either matches.

Examples:
- `*/30 5-23 * * 1-5` every 30 minutes from 05:00 to 23:30, Monday to Friday
- `0,15,30,45 * * * *` every quarter hour
- `0 0 25 12 *` midnight on Christmas Day
- `*/10 7-9 * * MON-FRI` every 10 minutes in the weekday morning peak

How it works:
- MTR repeats one list of departures every day. MAGIC fills that list from the minute and hour fields; the day, month
  and weekday fields (and daylight-saving changes) are checked for each departure at dispatch time, so a departure the
  expression does not schedule that day neither spawns a train nor appears on departure boards.
- Each departure still needs a free train in one of the depot's sidings, exactly as with MTR's own schedules.
- Local times skipped by a daylight-saving change do not depart; repeated local times depart twice.

Storage (server side, sent to every client):
- `config/MAGIC/configs/cron_timetables.json`:
```json
{
  "12345": { "cron": "*/30 5-23 * * 1-5", "time_zone": "Europe/Lisbon", "enabled": true }
}
```

## Railway Crossings

Block `Polish Level Crossing`, items `Axle Counter` and `Crossing Linker`. Any Polish Level Crossing can be the crossing
controller; other level crossings (and future crossing equipment such as barrier gates) are linked to it as objects.
(The old `Crossing Controller` and axle counter blocks are no longer obtainable, but those already placed keep
working.)

Axle counters are placed directly on existing MTR rails: hold the `Axle Counter` and right-click while aiming at a rail.
The counter clamps onto the rail to the right of the direction you are looking along it; the rail itself is not
changed. Hit it (left click) to remove it. If the rail is removed the counter drops as an item; if the rail is reshaped
the counter follows it. A counter only detects trains passing over its own point of the rail.

Crossing Linker modes (sneak + right-click to cycle; the icon shows the mode; this never changes a connection):
Inactive -> Start Node (green) -> End Node (red) -> Object Linking (blue).

Setup (the controller is always selected first):
1. Start Node mode: right-click the controller (green outline, and a green line to where you aim), then right-click
   the axle counter on one side of the crossing.
2. End Node mode: the same for the counter on the other side (red).
3. Object Linking mode: right-click the controller (blue outline), then right-click each level crossing that should
   switch with it (the aimed one gets a blue outline; no line is drawn). Up to 16 objects.
- Linking a counter or object that is already linked the same way unlinks it; a counter cannot be both start and end;
  linking a new start/end counter replaces the old one. Right-click the selected controller again to cancel.
- The selection is cancelled when you put the linker away, set it to Inactive, change dimension or move more than 96
  blocks away. Counters and objects must be within 256 blocks of the controller.

Seeing the connections: hold an MTR brush. Each controller in range shows a green line from its start counter, a red
line from its end counter (arrows point to the controller) and a blue line to each linked object (arrows point to the
object). Without a brush nothing is shown.

A level crossing faces the player who places it (lamps towards the player), in steps of 45°: look diagonally while
placing it to turn it by 45°, eg for a crossing on a curve. Sneak + right-click it with MTR's brush to switch
between its 5 bell sounds (the new one rings once; each level crossing keeps its own choice).

Behaviour:
- The crossing activates as soon as a train reaches either counter and stays active while the section is occupied.
  It clears once every train that entered has passed the counter on the other side, so it works in both directions
  and with closely following trains. With only one counter linked, it is active while that counter is occupied.
- Fail-safe: if no counter has seen a train for 2 minutes (eg a train reversed back out), the crossing clears.
- While active, the lights' two lamps flash alternately (every 0.5 s) and a bell rings (heard up to 24 blocks away);
  while idle both lamps are dark.
- State is saved with the world; lights in unloaded chunks catch up when loaded.

Redstone:
- Each level crossing outputs a redstone signal (15) while a train is in the crossing, for gates, lamps, etc.
- Legacy axle counter blocks output a signal (15) while a train occupies their rail node.
- A level crossing also flashes and rings while it receives a redstone signal (lever, button, detector rail, an axle
  counter next to it, ...), without any linking. A redstone input only flashes and rings that level crossing: it does
  not switch on its redstone output or linked level crossings (which would latch through adjacent dust).

Performance:
- Train positions are read once per second per dimension (shared by all counters); a counter looks its rail up by id
  instead of searching the rails. Unloaded chunks are never loaded by crossings.

### MTR File Loader Thread Limit (Performance/Stability)

What it does:
- Limits MTR's file loader to a bounded thread pool instead of an unbounded cached thread pool, reducing the chance of runaway thread creation during heavy IO.
