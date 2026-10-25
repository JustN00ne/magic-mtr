# MAGIC 1.4.0 development notes: alternative platforms rebuilt on the version that worked

Nothing in this round has been tested in game yet.

## Base

Alternative platforms are now the implementation from `D:\magic-main\magic-main` as edited on 2026-10-18, which the
user confirmed works in game. Copied unchanged: `SidingDynamicPlatformRerouteMixin` (platform choice at departure,
including the check that counts a platform as busy when another train of the route has it among its next stops),
`VehicleAlternativePlatformRerouteMixin` (reroute of a train held at a signal), `AlternativePlatformRegistry`,
`AlternativePlatformSelection`, `AlternativePlatformClient`, `AlternativePlatformMenuScreen`,
`DashboardScreenAlternativePlatformMixin`. Adjusted for 1.3.5 only where it did not compile: no manual-mode check
(1.3.5 has no such invoker, as in 1.3.5 itself) and the one-argument sync call. The `...` menu entry opens the same
menu as in that version. The old selector screen it replaced was removed.

Earlier attempts in this file's history failed because they removed the "busy" check (trains then practically never
used alternatives), replaced everything with untested code, and then restored the untouched backup, whose buttons
vanish after the dashboard re-initialises.

## Added on top (separate code, the base logic is unchanged)

- **Early choice and reservations** (`VehicleAlternativePlatformRerouteMixin#jme$assignPlatformEarly`,
  `rail/PlatformClaims`): a train within 35 path nodes of its next stop (or standing at it) claims that platform;
  the first claim wins and is released when the train leaves (or after 5 s without refresh, for removed trains). If
  the platform in the path is claimed by another train, the train is sent to a free alternative using the same path
  builder as the signal reroute, from the first segment beyond its braking distance. The choice is kept for that
  stop. No change is made across the loop point of a repeating route or when there is no following stop.
- **PIDS** (`rail/DivertedArrivals`, `mixin/SidingDivertedArrivalsMixin`, `ArrivalsRequestAlternativePlatformMixin`):
  read from the train's own path. A train whose next stop in its path is not its scheduled platform is dropped from
  the scheduled platform's board (per siding, so other sidings' trips with the same departure index are untouched)
  and listed at the platform it really uses with MTR's times, delay and destination.
- **Route selector folders**: collapsible `⎇ Alternative platforms (n)` rows under each stop with alternatives;
  clicking an entry opens the menu, its delete button / `Remove alternative` removes only that alternative.

## Fixes after the first in-game test (2026-10-25)

Reproduced on a copy of the test world with a dedicated server (decision log: environment variable
`JME_DEBUG_ALTERNATIVES=1`) and fixed:

- **Second train did not take the free alternative.** The early check skipped every candidate with an id `<= 0`;
  MTR ids are random longs and the alternative's id was negative. Now only 0 and the wildcard `-1` are skipped.
- **Mid-route path to the alternative was never valid** (also the reason the signal reroute of the working version
  never diverted anything): `PositionPathFinder` dropped the start node (MTR's `PathFinder` lists the nodes reached,
  not the start), so the path began one rail ahead of the train and was rejected. The start node is now included,
  and the search leaves it in the train's direction of travel.
- **"Detours" around the whole network** (passing the scheduled platform without stopping) are rejected: the way
  through an alternative may be at most 1.5 × + 100 m the scheduled way.
- **Diverted trains were cancelled when leaving the alternative platform.** MTR reads the timetable by distance; the
  alternative platform ends ~1 m before the scheduled one, so at departure the dwell was not credited and the delay
  jumped by the dwell time (90 s > the depot's 60 s cancellation threshold). `TimetableProgressMap` maps the rebuilt
  path onto the timetable's path (stops matched exactly) for MTR's `Vehicle#getTimeAlongRoute` (delay, PIDS
  predictions) and for MAGIC's delay-based cancellation. Verified: a diverted train dwells and departs with 0 s delay.
- **Delay-based cancellation** now needs the delay to stay above the threshold for 5 s: in the tick a train
  departs any stop MTR briefly reports a delay as large as the dwell, which could cancel trains that were on time.
  Each cancellation is logged (`[MAGIC] Cancelled train ...`).
- **Level crossing / axle counter textures**: the root `crossing_pl*.png` and `axle_counter.png` (edited
  2026-10-24) were shadowed by older copies in `common/src/main/resources`; the build now packages the root file
  whenever it exists (checked: jar contents equal the root files).
- **Level crossing lights and bell**: idle lights showed one lamp lit (the "off" model used `crossing_pl_o1`) and the
  crossing never made a sound. A new `lamp` block state (0 = dark `crossing_pl`, 1/2 = `crossing_pl_o1`/`_o2`) is
  driven by `block/CrossingSignal`, which flashes the lamps alternately and rings the note block bell every 5 ticks
  while on. The crossing controller, drawn with the same model, now flashes and rings too (when its crossing is
  active or it receives redstone; redstone input does not switch its output, to avoid a latch through adjacent dust).
- **Level crossing texture**: the root `crossing_pl*.png` were the original textures flipped vertically, so 94 of
  the JSON model's 114 faces sampled transparent pixels (invisible crossbuck, floating lamp parts). Flipped back
  (identical to the originals). The pole OBJ's UVs pointed at a strip of unrelated atlas islands; its sides now map
  onto the red/white pole stripe (x 50-51, y 76-181 px) and its caps onto single pixels.
- **Level crossing facing**: the model's front (lamps, crossbuck) points west, but the blockstates treated north as
  unrotated, so placed crossings were turned 90°. Rotations fixed (west 0, north 90, east 180, south 270).
- **One level crossing item**: `CrossingControl` (axle counters, state machine, linked crossings) is held by every
  level crossing, so the counters link straight to one of them and the others link to it. The crossing controller
  (same model, looked like a second level crossing) left the creative tab but still works in existing worlds. Level
  crossings now output redstone while a train is in the crossing.
- **Names**: `Level Crossing`, `Axle Counter`, `Crossing Linker` (they showed their raw translation keys).
- **Joban Client Mod compatibility** (resource packs with JCM scripts, eg `BR_Class390_MTR4.zip`, failed with
  `Mixin transformation of org.mtr.core.data.Vehicle failed` / `ClassCastException`): the build always compiled
  against the newest Fabric Loader (0.20.x, Mixin 0.17.5), whose `@Redirect`/`@ModifyArg`/`@ModifyVariable` declare
  `at` as `At[]`. javac then wrote `at` as an array, which the Mixin of player installs (eg 0.16.5 in Loader 0.17.3)
  reads as a single `At`, so applying MAGIC's `Vehicle` mixins crashed. JCM only made it visible by loading
  `Vehicle` while parsing scripts. The build now pins `loader_version=0.17.3` (`gradle.properties`).
- **Level crossing model**: the JSON model could not describe the octagonal lamp heads (they rendered as diamonds).
  The whole crossing is now drawn by `client/render/RenderCrossing` from `models/obj/level_crossing.obj`, converted
  from the mesh elements of the Blockbench model `crossing_1.bbmodel` (MTR's own bbmodel loader only reads cubes),
  with the textures embedded in that file (`level_crossing.png`, `_o1`, `_o2`, swapped as the lamps flash). The
  placed blocks' JSON models are empty; the item still uses the JSON model.
- **Level crossing icon and bell**: the item uses the flat icon `textures/item/railroad_xing_pl_1.png`. The bell is
  the new sound `jme:railroad_crossing` (`sounds/railroad_crossing.ogg`, converted from the root
  `railroad_crossing_sound.wav`), rung every 17 ticks (one ring lasts about 0.85 s) instead of the note block bell.
- **Bell sounds**: sneak + right-click a level crossing with MTR's brush to pick the next of 5 bells
  (`railroad_crossing`, `crossing_bell_2` to `_5`, converted from the root `1790010085981.mp3`, `dzwon1.wav`,
  `ehl1 (1)2.wav`, `noname.mp3`, levelled to a similar loudness). The choice is saved per crossing
  (`jme_crossing_bell`). `crossing_bell_5` (British alarm) is a continuous two-tone cycle of about 453 ms: 4 cycles are cut
  from `noname.mp3` at a tone change, stretched by 0.7% to exactly 36 ticks and looped with a 30 ms faded overlap;
  it is stopped for nearby players when the crossing switches off.
- **Names / icons**: the level crossing is now `Polish Level Crossing`. The axle counter's inventory model used UVs
  that no longer matched its texture and was drawn tiny; it is rebuilt from the world OBJ (UVs, slanted side) and
  scaled to fill the slot.
- **45° placement**: a new `diagonal` block state turns a level crossing by a further 45°, so it is placed facing the
  player in 8 directions (for crossings on curves).
- **"Ferry"** removed from the train route types (boats are MTR's own transport mode); a saved ferry type shows as
  Train.

Observed in the test world but not caused by alternative platforms: with the depot's cancellation threshold of
1 minute, trains waiting to enter the depot siding (occupied) are cancelled every few minutes.

## Not done

- Separate alternatives per visit for routes that stop at the same platform twice (configuration stays per route
  and platform, as in the working version).

## Verification

- `gradlew build --offline` (1.20.1): successful, 56 unit tests, 0 failures (new: `PlatformClaimsTest`,
  `TimetableProgressMapTest`; restored: `AlternativePlatformSelectionTest` from the working version).
- `build_all.ps1` for 1.20.1, 1.20.4, 1.19.4: successful; only the known optional `RenderRailsTiltMixin` warning.
- Dedicated server on a copy of the user's test world (several runs, up to 7 minutes): trains are diverted to the
  alternative platform ahead of the station when the primary is held, stop there, dwell with 0 s delay and continue;
  a train waits when both platforms are taken and takes the alternative as soon as it is free.
- Not verified headless: the departure boards (need a client) and the route selector rows.
- Production client (Loader 0.17.3, MTR 4.0.5, JCM 2.2.4 and 2.3.0-prerelease.1, `BR_Class390_MTR4.zip`): the old
  jar reproduced the JCM error, the new one loads the pack's scripts without errors. Level crossings checked in game:
  model, lamp textures and flashing, the four facings and 45° placement.
