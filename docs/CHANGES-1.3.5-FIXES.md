# MAGIC 1.4.0 development notes: regression review and fixes

Compared versions: original 1.2.0 (`magic-main-backup-20261018`), the fixed 1.2.0 line (`magic-main`) and 1.3.5.
Backup of 1.3.5 before these changes: `magic-1.3.5-backup-20261018`.

Verification done: the project compiles for every supported Minecraft version (see `dist_builds/`) and 47 unit tests
pass (cron parsing/expansion, crossing logic, delay computation, speed caps, CJK names, route types, platform
selection). Nothing below has been tested in game yet; mixin injection points were checked against MTR 4.0.5's
bytecode but only apply when the game runs.

## Regressions found in 1.3.5 (and fixed)

| Area | Regression | Fix |
|---|---|---|
| Speed limits | `SidingSpeedLimitMixin` targeted `writePathCache`, which matches nothing, so siding speed caps never applied to schedules | Retargeted to `generatePathDistancesAndTimeSegments` (where MTR builds the timetable) |
| Cancellation | Delay was read from MTR's stored deviation, which only updates when a train starts/stops, so trains stuck at signals were never cancelled | Live delay computed like `Vehicle#updateDeviation` |
| Cancellation | Cancelled trips stayed on departure boards as scheduled arrivals | Cancelled trips persisted (`canceled_trips.json`) and removed from boards |
| Cancellation (multiplayer) | Depot screen showed/edited the client's own settings file, not the server's | Settings are part of the server snapshot sent on join and after every change |
| Depot screen | Cancellations button overlapped MTR controls | Shares the Instant Deploy row (now with Cron) with width-aware labels |
| Siding screen | Dwell sliders sat under 1.3.5's Cancellations button | Sliders moved down |
| GUI | "Already added" flags lost MAGIC buttons after a window resize (`clearAndInit` clears children) | Widgets re-added on every init |
| Rails | Tilt and rotation both `@Redirect`ed the same call; the second was silently skipped, so rail rotation never rendered | Single combined redirect; `RenderRailsRotationMixin` removed |
| Route types | Buttons for Regional/Intercity/Trolleybus/Ferry missing; dropdown could run off-screen | 12 types, clamped dropdown, translations |
| System Map | New types had no entry in the map's type table (blank icon / errors) | All types registered with icon and label |
| System Map | Icon/name spacing: a CSS `aspect-ratio` rule on `.material-icons` squeezed the icons | Rule removed |
| CJK | `CJK_ONLY` dropped parts of names; CJK detection missed ranges | Shared `MultilingualName` helper |
| PIDS | Diverted trains stayed on the scheduled platform's board | Diverted trips moved to the real platform's board |
| Alternative platforms | Old selector screen; settings only on the editing client | New menu, server snapshot sync |
| Crossings | Crossing pole OBJ never loaded; it had degenerate UVs, self-intersecting faces and flat normals | OBJ repaired and rendered through MTR's OBJ pipeline |

## New in this update

- **Cron timetables** (depot screen -> `Cron`). See `wiki/FEATURES.md#cron-timetables`.
- **Railway crossing redstone integration**: the crossing now activates on approach from either side, stays active
  while occupied, clears after the last train leaves, supports following trains and up to 16 lights, outputs
  redstone (controller and axle counters), and lights accept redstone input. State persists; unloaded chunks are
  never loaded.
- **Performance**: axle counters cache their rail node (they searched every rail node of the dimension every 3 ticks
  each); rail tilt lookups use a spatial index; dashboard rail merges are throttled.

## Files changed (main ones)

- `common/.../util/CronExpression.java`, `CronTimetables.java`, `CrossingStateMachine.java` (new, unit tested)
- `common/.../rail/CronTimetableRegistry.java` (new), `DepotCancellationRegistry.java`, `CanceledTrainRegistry.java`,
  `PlatformDiversionRegistry.java` (new)
- `common/.../mixin/DepotCronTimetableMixin.java`, `SidingCronTimetableMixin.java` (new),
  `SidingDelayCancellationMixin.java`, `ArrivalsRequestAlternativePlatformMixin.java`, `SidingSpeedLimitMixin.java`
- `fabric/.../block/BlockCrossingController.java`, `BlockAxleCounter.java`, `BlockCrossingLights.java`,
  `CrossingLinkerHandler.java`
- `fabric/.../client/screen/CronTimetableScreen.java` (new), `client/render/CrossingPoleModel.java`,
  `RenderCrossingPole.java` (new)
- `fabric/.../network/MagicRouteStateSync.java`, `MagicRailNetworking.java`, `MagicSimulatorAccess.java` (new)
- `fabric/.../mixin/EditDepotScreenCancellationMixin.java`, `EditRouteScreenRouteTypesMixin.java`,
  `RenderRailsTiltMixin.java`, `WebserverResourcesMixin.java`
- Assets: crossing blockstates (`powered`), `crossing.obj`/`crossing.mtl`, crossing block models, System Map CSS/JS

## Known limitations

- Cron: the System Map's departure lists (not the PIDS boards) still list candidate departures on days the expression
  excludes. Trips longer than 24 hours are not supported by MTR's daily repeat in real-time mode.
- Crossings: two counters cannot tell trains' directions apart, so a train that enters and reverses back out keeps the
  crossing active until the 2-minute fail-safe.
- MAGIC's settings packets do not check permissions (as before); any player who can open MTR's screens can change
  them.
