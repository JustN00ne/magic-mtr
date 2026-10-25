# MAGIC Railways Configuration

OpenRailwayMap-style railway line definitions for the MAGIC dashboard map.

## Config File

Location: `config/MAGIC/config/map.yaml`

The file is created automatically with example entries on first run. Edit it to define your railway lines.

## Format

```yaml
railways:
  - id: unique-line-id
    name: Display Name
    properties:
      railway: rail
      usage: main
      electrified: contact_line
      voltage: 3000
      frequency: 0
      gauge: 1435
      maxspeed: 160
      operator: Full Operator Name
      operatorShort: OP
      ref: "1"
      trafficMode: mixed
    match:
      routeIds:
        - "1"
        - "2"
      area:
        x1: 0
        z1: 0
        x2: 1000
        z2: 1000
```

## Properties Reference

### `railway`

The railway type. Affects the general rendering style.

| Value | Description |
|---|---|
| `rail` | Standard railway |
| `subway` | Underground metro |
| `tram` | Tram/streetcar |
| `light_rail` | Light rail |
| `narrow_gauge` | Narrow gauge railway |
| `miniature` | Miniature railway |
| `funicular` | Funicular |
| `monorail` | Monorail |

### `usage`

Controls the **line color** (main visible line).

| Value | Color | Description |
|---|---|---|
| `main` | Orange | Primary mainline railway |
| `secondary` | Invisible | Secondary lines (rendered but no visible usage color) |
| `hsr` | Red | High-speed rail lines |
| `branch` | Yellow | Branch/feeder lines |
| `industrial` | Brown | Industrial sidings and spurs |
| `spur` | Brown | Spur lines (same as industrial) |
| `military` | Gray | Military railway lines |

### `maxspeed`

Controls the **speed color** (gradient applied to the line) and **line thickness**.

| Speed Range | Color | Width |
|---|---|---|
| 0 (stopped) | Dark Blue (`#1a237e`) | 2.5px |
| 1-40 | Blue (`#1565c0`) | 3px |
| 41-80 | Teal (`#00838f`) | 3px |
| 81-120 | Green (`#2e7d32`) | 3.5px |
| 121-160 | Yellowish Green (`#9e9d24`) | 4px |
| 161-200 | Amber (`#f9a825`) | 4px |
| 201-250 | Deep Orange (`#ef6c00`) | 4.5px |
| 251-300 | Red (`#c62828`) | 5px |
| 301-350 | Purplish Red (`#7b1fa2`) | 5px |
| 351+ | Magenta (`#d500f9`) | 5px |

### `electrified`

Controls the **electrification overlay** (dashed lines drawn on top of the usage line).

| Value | Pattern | Description |
|---|---|---|
| `none` | No overlay | Not electrified |
| `contact_line` | Dashed (`- - -`) | Overhead catenary wire |
| `contact_side` | Dashed (`- - -`) | Side contact wire |
| `rail` | Dashed (`- - -`) | Third rail |
| `proposed` | Dash-dot-dot (`-__-__`) | Proposed electrification |
| `construction` | Dash-dot (`-_-_`) | Under construction |

When `electrified` is not `none`, the `voltage` and `frequency` fields determine the **color** of the dashed overlay:

#### AC Voltages (tilde `~`)

| Voltage | Frequency | Color | Hex |
|---|---|---|---|
| 25 kV | (any) | Red (dark border) | `#cc0000` + `#800000` border |
| 25 kV | 60 Hz | Red | `#cc0000` |
| 25 kV | 50 Hz | Red-Orange | `#e05000` |
| 20 kV | 60 Hz | Orange | `#e88000` |
| 20 kV | 50 Hz | Gold / Yellow-Orange | `#e8b000` |
| 15-25 kV | (any) | Yellow | `#e8d800` |
| 15 kV | 16.7 Hz | Teal / Dark Cyan | `#00838f` |
| 15 kV | 16.67 Hz | Bright Green | `#00c853` |
| 12.5-15 kV | (any) | Dark Olive Green | `#556b2f` |
| 12.5 kV | 60 Hz | Olive Green | `#6b8e23` |
| 12.5 kV | 25 Hz | Medium Green | `#2e7d32` |
| < 12.5 kV | (any) | Light Green | `#66bb6a` |

#### DC Voltages (equals `=`)

| Voltage | Color | Hex |
|---|---|---|
| 3 kV | Medium Blue | `#1565c0` |
| 1.5-3 kV | Cyan / Light Blue | `#29b6f6` |
| 1.5 kV | Dark Cyan | `#00838f` |
| 1-1.5 kV | Deep Blue | `#1a237e` |
| 1 kV | Indigo / Purple | `#4a148c` |
| 750 V - 1 kV | Magenta / Purple | `#9c27b0` |
| 750 V | Pink / Bright Magenta | `#ec407a` |
| < 750 V | Light Pink | `#f48fb1` |

**Note:** For `contact_line` without explicit voltage, defaults to 3 kV DC. For AC catenary without explicit voltage, defaults to 25 kV 50 Hz.

### `gauge`

Controls the **gauge background color** (thick colored line drawn behind the usage line).

| Gauge (mm) | Color | Hex | Description |
|---|---|---|---|
| 3000 | Lime | `#aaff00` | Indian gauge |
| 1600 | Orange | `#ff8800` | Irish / Iberian gauge |
| 1520 | Dark Red | `#8b0000` | Russian gauge |
| 1435 | Black | `#222222` | Standard gauge (subtle dark outline) |
| 1432 | Dark Blue | `#1a237e` | Irish broad gauge |
| 1050 | Blue | `#1565c0` | Metre gauge variant |
| 800 | Pink | `#ff69b4` | Narrow gauge |
| 600 | White | `#ffffff` | Narrow gauge |
| 63 | Teal | `#00838f` | Miniature railway |

### Other Properties

| Property | Type | Description |
|---|---|---|
| `voltage` | number | Electrification voltage (V or kV depending on system) |
| `frequency` | number | AC frequency in Hz (0 for DC) |
| `operator` | string | Full operator name |
| `operatorShort` | string | Short operator code (shown in tooltips) |
| `ref` | string | Line reference number |
| `passengerLines` | number | Number of passenger lines |
| `trafficMode` | string | `mixed`, `passenger`, `freight` |
| `shp` | boolean | SHP (Szybka Kolej Miejska) flag |
| `workrules` | string | Working rules reference |
| `preferredDirection` | string | `forward` or `backward` |
| `trackClass` | string | Track class (currently ignored in rendering) |
| `trackRef` | string | Track reference |

## Matching Rails to Definitions

Rails are matched to railway definitions using three methods (in priority order):

### 1. Track Hex IDs (primary)

Each rail has a unique hex ID (e.g. `"00000001-0000000a-00000005-00000002-0000000b-00000006"`). You can find these in:
- The `/api/rails` JSON response (`id` field)
- The dashboard hover tooltip (shows rail ID at the bottom)
- The MTR system map (right-click a rail segment)

```yaml
match:
  trackIds:
    - "00000001-0000000a-00000005-00000002-0000000b-00000006"
    - "00000001-0000000a-00000005-00000003-0000000c-00000007"
```

### 2. Route IDs (fallback)

Each rail has `connectedNodes` containing `routes` arrays with route ID strings. A rail matches if **any** of its node route IDs appear in the definition's `match.routeIds`.

```yaml
match:
  routeIds:
    - "1"    # Route ID "1"
    - "2"    # Route ID "2"
```

### 3. Area Matching (last resort)

If neither track IDs nor route IDs match, a bounding box check is performed. The rail matches if **any** of its `curvePoints` fall within the defined area.

```yaml
match:
  area:
    x1: -1000   # Min X (blocks)
    z1: -1000   # Min Z (blocks)
    x2: 1000    # Max X (blocks)
    z2: 1000    # Max Z (blocks)
```

**Priority:** `trackIds` > `routeIds` > `area`. The first matching definition wins.

## Rendering Layers

The dashboard renders ORM-configured rails in 4 passes:

1. **Gauge background** — Thick colored line (6px wider than usage line) showing gauge type
2. **Usage line** — Main visible line colored by usage type, with shadow for depth
3. **Electrification overlay** — Dashed lines offset to both sides of the rail, colored by voltage/frequency
4. **Speed labels** — Shown at zoom level 4+, displaying the max speed at the midpoint of each rail

Rails without ORM config fall back to the default speed-based rendering.

## API Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/railways` | Returns parsed config as JSON |
| POST | `/api/railways` | Saves raw YAML content |

## Examples

### Polish Mainline (PKP PLK)

```yaml
- id: pkp-line-1
  name: PKP CMK (Warszawa-Kraków)
  properties:
    railway: rail
    usage: hsr
    electrified: contact_line
    voltage: 3000
    frequency: 0
    gauge: 1435
    maxspeed: 200
    operator: PKP Polskie Linie Kolejowe
    operatorShort: PKP PLK
    ref: "1"
    trafficMode: mixed
  match:
    trackIds:
      - "00000001-0000000a-00000005-00000002-0000000b-00000006"
      - "00000001-0000000a-00000005-00000003-0000000c-00000007"
```

### German ICE Line (15 kV 16.7 Hz AC)

```yaml
- id: db-ice-line
  name: ICE Line Frankfurt-Köln
  properties:
    railway: rail
    usage: hsr
    electrified: contact_line
    voltage: 15000
    frequency: 16.7
    gauge: 1435
    maxspeed: 300
    operator: Deutsche Bahn
    operatorShort: DB
    ref: "ICE"
    trafficMode: mixed
  match:
    trackIds:
      - "00000003-0000000a-00000005-00000004-0000000d-00000008"
```

### Narrow Gauge Branch

```yaml
- id: harz-narrow
  name: Harz Narrow Gauge
  properties:
    railway: narrow_gauge
    usage: branch
    electrified: contact_line
    voltage: 750
    frequency: 0
    gauge: 1000
    maxspeed: 60
    operator: Harzer Schmalspurbahnen
    operatorShort: HSB
    ref: "HSB"
    trafficMode: passenger
  match:
    trackIds:
      - "00000004-0000000a-00000005-00000005-0000000e-00000009"
```

### Proposed Electrification

```yaml
- id: proposed-line
  name: Future Electrification
  properties:
    railway: rail
    usage: branch
    electrified: proposed
    voltage: 25000
    frequency: 50
    gauge: 1435
    maxspeed: 120
  match:
    trackIds:
      - "00000005-0000000a-00000005-00000006-0000000f-0000000a"
```

## Troubleshooting

**Rails not appearing with ORM colors:**
- Check that the track hex IDs in your config match the rail IDs in your MTR world
- Find rail hex IDs by hovering over a rail segment in the dashboard (shown at bottom of tooltip)
- Verify the config file is at `config/MAGIC/config/map.yaml`
- Check the game log for `[jme] Loaded N railway definitions` message

**Electrification not showing:**
- Ensure `electrified` is not `none`
- Set `voltage` and `frequency` for the correct color
- The electrification overlay only appears when zoomed in enough

**Config not loading:**
- The config is hot-reloaded — save the YAML file and it will take effect on the next data refresh (every 30 seconds)
- Check for YAML syntax errors in the game log

**Finding rail hex IDs:**
1. Open the dashboard map in your browser
2. Hover over any train — the tooltip shows the rail hex ID at the bottom
3. Or call `GET /api/rails` and search for the rail's `id` field
4. Copy the hex ID into your YAML config under `match.trackIds`
