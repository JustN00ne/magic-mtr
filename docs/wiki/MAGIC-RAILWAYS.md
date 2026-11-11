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

These properties describe the railway. How they are drawn (colours, line widths and the styles in the dashboard's style
bar) is set in `map-style.yaml`; see [MAGIC Map Style](MAGIC-MAP-STYLE.md).

| Property | Type | Values / description |
|---|---|---|
| `railway` | text | `rail`, `subway`, `tram`, `light_rail`, `narrow_gauge`, `miniature`, `funicular`, `monorail` |
| `usage` | text | `main`, `hsr` (high-speed), `branch`, `industrial`, `spur`, `military`, `tourism`, `secondary` |
| `maxspeed` | number | km/h. Leave out or `0` to use the rail's MTR speed limit |
| `electrified` | text | `none`, `contact_line` (overhead wire), `contact_side`, `rail` (third rail), `proposed`, `construction`, `deelectrified` |
| `voltage` | number | Volts, eg `25000`, `15000`, `3000`, `1500`, `750`. Electrified without a voltage counts as 3000 V DC |
| `frequency` | number | Hz for AC (`50`, `60`, `16.7`); `0` for DC. A voltage of 4000 V or more without a frequency counts as 50 Hz |
| `gauge` | number | Track gauge in mm, eg `1435`, `1668`, `1520`, `1000` |
| `operator` | text | Full operator name |
| `operatorShort` | text | Short operator code |
| `ref` | text | Line reference number |
| `passengerLines` | number | Number of passenger lines |
| `trafficMode` | text | `mixed`, `passenger`, `freight` |
| `shp` | true/false | SHP flag |
| `workrules` | text | Working rules reference |
| `preferredDirection` | text | `forward` or `backward` |
| `trackClass`, `trackRef` | text | Track class and reference |

Write `"no"` in quotes: unquoted, YAML reads `no` as false (MAGIC treats both as "not electrified").

### Your own properties

Any other property you add is kept and can be used by a map style, for example:

```yaml
properties:
  usage: main
  owner: Northern Rail
  signalling: ETCS
```

[MAGIC Map Style](MAGIC-MAP-STYLE.md) shows how to add a style that colours rails by `owner`.

## Matching Rails to Definitions

Rails are matched to railway definitions using three methods (in priority order):

### 1. Track Hex IDs (primary)

Each rail has a unique hex ID (e.g. `"00000001-0000000a-00000005-00000002-0000000b-00000006"`). You can find these in:
- The `/api/rails` JSON response (`id` field)
- The dashboard map: click a rail and the popup shows its rail ID at the bottom
- The MTR system map (right-click a rail segment)

```yaml
match:
  trackIds:
    - "00000001-0000000a-00000005-00000002-0000000b-00000006"
    - "00000001-0000000a-00000005-00000003-0000000c-00000007"
```

### 2. Route IDs (fallback)

A rail matches if any route listed in `match.routeIds` runs over it, along the path MTR generated for the route
(the track its trains actually drive on, from platform to platform). Route IDs are the routes' hex IDs, as shown in
the popup when you click a rail ("Routes") or in `/api/livemap/lines`.

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

## How rails are drawn

The dashboard (and the Livemap's map styles) colour every rail with the style picked in the style bar; the styles and
their colours are in `map-style.yaml` ([MAGIC Map Style](MAGIC-MAP-STYLE.md)). Rails without a `map.yaml` entry are
still drawn: as a line when a route runs over them, otherwise as a siding, and with their MTR speed limit in the
speed style.

## API Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/railways` | Returns parsed config as JSON |
| GET | `/api/rail-routes` | For each rail on a route's generated path, the routes that run over it |
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

**Rails not drawn with your properties:**
- Click the rail on the dashboard map. The popup shows its rail ID and, if it matched, the `map.yaml` entry's name
- Check that the track hex IDs in your config match the rail IDs in your MTR world
- Verify the config file is at `config/MAGIC/config/map.yaml`
- Check the game log for `[jme] Loaded N railway definitions` message

**Electrification shows as grey:**
- Grey means the rail has no `map.yaml` entry, or is electrified with a voltage the style has no colour for
- Set `voltage` (and `frequency` for AC) to one of the values in the Electrification legend

**Config not loading:**
- The config is hot-reloaded: save the YAML file and the dashboard picks it up on its next refresh (every 15 seconds)
- Check for YAML syntax errors in the game log

**Finding rail hex IDs:**
1. Open the dashboard map in your browser
2. Click the rail: the popup shows its rail ID at the bottom
3. Or call `GET /api/rails` and search for the rail's `id` field
4. Copy the hex ID into your YAML config under `match.trackIds`
