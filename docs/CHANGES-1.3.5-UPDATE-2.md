# MAGIC 1.4.0 development notes: second fix round (rail icons, OBJ models, alternative platforms, service types, performance, schedules)

Nothing in this round has been tested in game. The project builds and the unit tests pass (see "Verification").

## 1. Rail icons

The new 64x64 icons at the repository root (`rail_connector_magic.png`, `rail_connector_magic_one_way.png`) are what
the build packages as `assets/jme/textures/item/` (no other copy exists in the resources, so nothing shadows them), and
both connector item models use them.

Fixed: the "selected" icon variants (`*_selected.json`, MTR's selection overlay on top of the MAGIC icon) were never
shown. The item models switch on `mtr:selected`, but that model predicate was only registered by MTR for its own
connectors. `JmeClient` now registers it for both MAGIC connectors, with MTR's rule (a first node is stored in the
stack's `pos` tag).

## 2. OBJ models

Root cause: the axle counter's OBJ (a stepped body with a slanted top face) was shipped but never rendered; the block
drew a single-box JSON approximation. Its MTL also referenced the texture with a path MTR's OBJ loader cannot resolve
(`jme:block/axle_counter` -> `jme:block/axle_counter.png`), so the model would have been untextured even once loaded.

- `client/render/ObjBlockModel` (replaces `CrossingPoleModel`): one class for MAGIC's OBJ block models, loaded and
  drawn through MTR's OBJ pipeline (keeps OBJ normals/UVs, optimized renderer with automatic fallback). Used for the
  crossing pole and the axle counter. Models are rebuilt after a resource reload.
- `client/render/RenderObjBlock` (replaces `RenderCrossingPole`): block entity renderer, turns the model with the
  block's `facing` like MTR's eye candy.
- Axle counter: the placed block's JSON model no longer has geometry (only the particle texture), so the OBJ is not
  drawn on top of a box; the old box model is kept as `axle_counter_inventory` for the inventory icon.
- `axle_counter.mtl`: `map_Kd jme:textures/block/axle_counter.png`.

## 3. Alternative platforms

The changes made in this round (and a later rewrite) were reverted: alternative platforms are now the version that
worked in game (`magic-main`, 2026-10-18), with new features added on top. See `CHANGES-1.3.5-UPDATE-3.md`.

## 4. Performance

- Train detectors in "seconds offset" mode scanned the whole path of every train in the dimension, twice, every
  2 ticks per detector. They now use a rail -> path-entry index built once per occupancy refresh (1 s).
- Client: the riding-car roll walked every client vehicle on each call (camera + each car of the ridden train); it now
  does so once per frame.

No profiler was available; these are code-path fixes, not measured improvements.

## 5. Service-type buttons

- MAGIC web dashboard (port 8088), "Train Types" filter buttons: they stored their state but nothing applied it, so
  they had no effect. Vehicles are now filtered. Usage / Gauge / Electrification buttons had the same problem and now
  filter rails.
- The dashboard's rails API sent routes without a type key, so every route counted as `train_normal`. Routes now carry
  the web map type key (with MAGIC's extended types).
- Regional, Intercity and Ferry had no dashboard button/colour; added. The filter panel is no longer rebuilt every
  15 s (which could swallow clicks).

## 6. Vehicle maximum speed in schedules

Already in place: the siding's maximum speed caps both the simulation and MTR's time-segment timetable (acceleration,
braking, rail limits and dwell times stay MTR's own model).

Fixed: MTR only rebuilds a siding's time segments when it loads or its depot regenerates, so a changed maximum speed
(or acceleration/deceleration) only reached the timetable after a manual depot refresh. `SidingSpeedLimitMixin` now
rebuilds the time segments when a siding update changes any of them (existing paths reused, no pathfinding).

## Verification

- `gradlew build` (Minecraft 1.20.1): successful; 50 unit tests, 0 failures (new tests for platform length rule,
  web route type keys, schedule-change detection).
- `build_all.ps1` for 1.20.1, 1.20.4 and 1.19.4: all successful, jars in `dist_builds/`. The only compiler warning is
  the pre-existing optional legacy target in `RenderRailsTiltMixin` (`require = 0`). 1.19.2 and older were not
  rebuilt.
- Not tested in game.
