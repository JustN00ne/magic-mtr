# MAGIC 1.5.0 plan: level crossing feedback (from JN)

JN's list after testing 1.4.0, with what each item was clarified to mean (2026-10-25). "Balises" are the axle counters
placed on MTR rails (`AxleCounterEntity`).

| # | JN's note | Meaning / plan |
|---|-----------|----------------|
| 1 | fix the distance for the rail balises | The maximum link distance between a controller and its axle counters / objects (`CrossingControl.MAX_LINK_DISTANCE`, 256 blocks; the linker session also cancels at 96 blocks, `CrossingLinkerHandler.MAX_SESSION_DISTANCE`). Raise it (and the session limit with it), check the rendering of long link lines. |
| 2 | allow multiple balises per signal | A controller holds one start and one end counter today (`CrossingControl.start` / `end`). Make each a list (eg up to 8), the state machine counts "any start counter" / "any end counter"; keep reading the old single-counter NBT keys. |
| 3 | move controls to a separate block than a crossing light block | Bring back a dedicated controller block (the legacy `BlockCrossingController` is hidden from the creative tab). Crossing lights stay linkable objects; lights already used as controllers in existing worlds must keep working. |
| 4 | add crossing gates, crosses, etc. | New `CrossingObject` blocks: barrier gate (animated arm, lowers when active) and crossbuck (St Andrew's cross, static). Simple placeholder models for now, to be replaced by proper Blockbench models later. |
| 5 | allow connection either way | Trains in both directions. `CrossingStateMachine` is already bidirectional on paper; verify with tests and in game (train entering from the end counter, reversing trains) and fix what fails. |
| 6 | show arrows on top of the balises to show facing | Draw an arrow on the axle counter model in the direction it faces (`AxleCounterEntity.facingForward`), at least while holding the Crossing Linker. |

All of it must stay Java 8 and build for 1.16.5 to 1.20.4. Never reflect on Minecraft class, method or field names:
released jars use intermediary names (see CHANGELOG-1.4.1).
