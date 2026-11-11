# MAGIC 1.4.1

Bug fix release. Changes since 1.4.0.

## Fixes

- **Train Detector settings were not saved.** Changing the activation range (nodes), the seconds offset or the "Use
  Seconds Offset" toggle and closing the screen had no effect: the range always went back to 6. The server looked up the
  player's world by a method name that only exists in development builds, so in released jars it found no world and
  dropped the settings. The same lookup also broke applying a brush profile to a rail with MTR's brush.
- **Camera tilt on tilted rails** never applied in released jars (same cause: the rotation was looked up by name).
- **Dashboard map tiles** rendered empty in released jars: the block map colours and the world's bottom height were
  looked up by name. Worlds below y = 0 (1.18+) are now drawn from their real bottom.
- **`/magic` server command replies** (eg the dashboard auth code) were not shown to the player or the console.
