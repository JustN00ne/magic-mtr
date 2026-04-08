# MAGIC config folder

This folder lives under your Minecraft config directory as `config/MAGIC/`.
In this dev workspace it is under `run/config/MAGIC/`.

## Files and what they do

`configs/magic.json`
Main config for MAGIC (JME). This is what the in-game settings screen and the `:8888` System Map menu update.

`configs/route_types.json`
Optional per-route type overrides for the System Map (to show Metro/Bus/Tram/S-Bahn types on the web map).

`map/system_map.css`
Custom CSS injected into the `:8888` System Map page.

`map/system_map.js`
Custom JavaScript injected into the `:8888` System Map page.

`map/system_map_overlay_cache_*.lzma2`
Generated cache files when `system_map_overlay_cache_enabled` is on and `system_map_overlay_cache_persist_enabled` is enabled.

## `configs/magic.json` options

`use_mph` (boolean, default `false`)
If enabled, speed labels are shown in mph instead of km/h in supported MAGIC UI/tooltips.

`camera_tilt_enabled` (boolean, default `true`)
Enables/disables the MAGIC camera tilt feature.

`camera_tilt_strength` (number, default `1.0`, range `0` to `2`)
Strength of the camera tilt effect. `1.0` is normal.

`dashboard_route_list_mode` (`FOLDERS` or `FLAT`, default `FOLDERS`)
Controls how routes are grouped in the in-game dashboard route list.

`dashboard_map_auto_save_enabled` (boolean, default `true`)
If enabled, the in-game dashboard auto-saves map areas.

`dashboard_rail_overlay_mode` (`ALL`, `CULL`, `OFF`, default `ALL`)
Controls the rails overlay mode for the `:8888` map overlay and related dashboard overlay features.

`dashboard_rail_overlay_cull_max_per_cell` (integer, default `8`, range `1` to `64`)
Only used when `dashboard_rail_overlay_mode` is `CULL`. Higher values render more rails per screen cell.

`system_map_overlay_cache_enabled` (boolean, default `false`)
If enabled, the server caches rails/vehicles for the `:8888` map so content stays visible even after chunks unload.
This also helps the map remain usable when the integrated server pauses (singleplayer pause screen).

`system_map_overlay_cache_persist_enabled` (boolean, default `false`)
If enabled, the server writes the overlay cache to `config/MAGIC/map/` (can create large files).

`system_map_language_display` (`NORMAL`, `CJK_ONLY`, `NON_CJK_ONLY`, default `NORMAL`)
Controls how station/route names are shown on the `:8888` map.

If your names contain multiple languages separated by `|` like:
`Central|中央|センター`

then:
- `NORMAL` shows: `Central / 中央 / センター`
- `CJK_ONLY` shows the best CJK variant (and keeps digits/punctuation)
- `NON_CJK_ONLY` shows the best non-CJK variant

## `configs/route_types.json` format

This file is optional. When present it should look like:

```json
{
  "route_types": {
    "0123456789ABCDEF": "metro",
    "FEDCBA9876543210": "sbahn"
  }
}
```

Valid values (case-insensitive):
`normal`, `light_rail`, `high_speed`, `metro`, `tram`, `bus`, `sbahn`

## System Map styling and scripting

Edit:
- `map/system_map.css` for styling
- `map/system_map.js` for extra behaviour

You can also edit/save both from the MAGIC menu inside the `:8888` System Map.

