# MAGIC Map Style

`map-style.yaml` sets how the web dashboard (`http://localhost:8088/`) and the Livemap (`/livemap`) draw railways:
which styles are in the style bar, and the colour, width, dashes and text of every rail in each style. The default
styles look like [OpenRailwayMap](https://openrailwaymap.app) and use the colours of its map key:

| Style | Colours rails by |
|---|---|
| Infrastructure | `railway` and `usage` (main line, high-speed, branch, subway, tram, ...) |
| Max speed | `maxspeed`, or the rail's MTR speed limit, on a colour scale from 10 to 380 km/h |
| Electrification | `electrified`, `voltage` and `frequency` |
| Track gauge | `gauge`, on a colour scale from 63 to 2000 mm |

The properties come from [map.yaml](MAGIC-RAILWAYS.md). Add your own styles to colour rails by any other property,
including ones you invent there.

## Config file

Location: `config/MAGIC/config/map-style.yaml`

MAGIC writes the file with the default styles on first start; the comments at its top describe every setting. Save
it and open pages pick the change up within 15 seconds. If the file has a mistake, the pages keep the last working
version and show the error at the top of the map. Delete the file to get the defaults back.

## Settings

```yaml
theme: system          # light, dark or system (follows the viewer's device)
background:
  opacity: 0.55        # how strongly the world map shows under the rails (0 to 1)
  saturation: 0        # 0 = grey like OpenRailwayMap, 1 = Minecraft's colours
defaultStyle: infrastructure
styles:
  - ...
```

Viewers can change the theme and the background in the dashboard's *Map configuration* (the sliders button). That is
saved in their browser only.

## Styles

Each style is a button in the style bar, in the order of the file.

```yaml
- id: electrification        # short unique name; the page address is /#style=electrification
  name: Electrification      # button tooltip and legend title
  icon: bolt                 # tracks, speed, bolt, gauge, signal, building, pin or star
  text: "{voltage}V"         # optional text along the lines; {property} becomes the rail's value
  rules:                     # the first rule that matches a rail styles it
    - when: {voltage: 25000, frequency: 50}
      color: "#ff0000"
      text: "25kV 50Hz"
      label: 25 kV 50 Hz ~
  gradient:                  # optional colour scale for rails no rule matched
    property: maxspeed
    label: "{value} km/h"
    stops:
      - [10, "#31123b"]
      - [380, "#7b0503"]
  default:                   # rails nothing else matched
    color: "#808080"
    label: Unknown
```

### Rules

| Key | Meaning |
|---|---|
| `when` | Conditions on the rail's properties; all must hold. `property: value` (text ignores upper/lower case), `property: [a, b]` (any of), `property: {min: 1, max: 9}` (number in range; either end optional). Leave `when` out to match every rail |
| `color` | `"#rrggbb"` |
| `width` | Line width, `1` = normal, `1.5` = half as wide again |
| `dash` | `[on, off]` for a dashed line, eg `[2, 4]` |
| `text` | Text along the line for this rule; overrides the style's `text`. `""` for none |
| `label` | The rule's entry in the legend. Rules without a label are drawn but not listed |
| `hidden` | `true` to leave these rails out |

### Properties every rail has

Besides its `map.yaml` properties:

| Property | Meaning |
|---|---|
| `mtrSpeed` | The rail's MTR speed limit in km/h |
| `maxspeed` | `map.yaml`'s `maxspeed`, or `mtrSpeed` when not set |
| `onRoute` | `true` when an MTR route runs over the rail (`false` for sidings, depots and spare track) |
| `defined` | `true` when the rail matches a `map.yaml` entry |

## Adding a style

Give some `map.yaml` entries an `owner`:

```yaml
- id: north-main
  name: Northern Main Line
  properties:
    usage: main
    owner: Northern Rail
  match:
    routeIds: ["00000000000003e8"]
```

Then add a style to `map-style.yaml`:

```yaml
  - id: owner
    name: Owner
    icon: building
    text: "{owner}"
    rules:
      - when: {owner: Northern Rail}
        color: "#7b2d8e"
        label: Northern Rail
      - when: {owner: [Southern Lines, Coastal Railway]}
        color: "#0072ce"
        label: Southern group
    default:
      color: "#9e9e9e"
      label: Other owners
```

The style appears in the dashboard's style bar and in the Livemap's style menu.

## In the pages

- **Dashboard:** the style bar (top right) switches styles; the layers button lists them too, with switches for trains,
  stations, labels and the world map, and train filters by service type. *Legend* (bottom left) explains the current
  style; click an entry to hide or show those rails. Click a rail for its properties and rail ID.
- **Livemap:** the layers button (top right) switches between the live lines and any style. Trains stay on
  top in every style.

## API

| Method | Path | Description |
|---|---|---|
| GET | `/api/map-style` | `map-style.yaml` as JSON (with `error` if the file could not be read) |
| GET | `/assets/map-style.js` | The script both pages use to apply the styles |
