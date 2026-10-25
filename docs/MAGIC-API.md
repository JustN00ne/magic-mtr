# MAGIC Dashboard API Reference

MAGIC exposes an HTTP API on **port `8088`** (configurable via `-Dmagic.dashboard.port`). The server binds to `localhost` only and supports CORS for local development.

All responses are `application/json; charset=utf-8`. Every response includes a `cachedResponseTime` (epoch millis) field.

---

## Base URL

```
http://localhost:8088
```

---

## Authentication

None. The server is localhost-only.

---

## CORS

`Access-Control-Allow-Origin: *` is set on all responses. `GET`, `POST`, and `OPTIONS` are allowed methods.

---

## Contents

* [GET /api/depots](#get-apidepots)
* [GET /api/sidings](#get-apisidings)
* [GET /api/resourcepack](#get-apiresourcepack)
* [GET /api/trackgraph](#get-apitrackgraph)
* [GET /api/trains](#get-apitrains)
* [GET /api/rails](#get-apirails)
* [POST /api/routes](#post-apiroutes)
* [POST /api/depots](#post-apidepots)
* [POST /api/platforms](#post-apiplatforms)
* [GET /api/tiles/\{z\}/\{x\}/\{y\}.png](#get-apitileszxy-png)

---

## GET `/api/depots`

Returns all depots in the world with their metadata, assigned sidings, routes, and train configuration.

### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `dimension` | string | No | Filter by dimension (`minecraft/overworld`, `minecraft/the_nether`, etc.) |

### Response

```json
{
  "cachedResponseTime": 1726200000000,
  "depots": [
    {
      "id": "0000000000000001-0000000000000002-0000000000000003-0000000000000004-0000000000000005-0000000000000006",
      "name": "Central Depot",
      "position": { "x": 100, "y": 64, "z": 200 },
      "color": 255,
      "sidings": [
        "0000000000000011-0000000000000012-0000000000000013-0000000000000014-0000000000000015-0000000000000016"
      ],
      "routes": [
        "0000000000000021-0000000000000022"
      ],
      "trainCount": 4,
      "trainLength": 8
    }
  ]
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Hex-encoded unique ID of the depot |
| `name` | string | User-assigned name |
| `position` | object | World coordinates `{x, y, z}` of the depot's start position |
| `color` | int | ARGB color value |
| `sidings` | string[] | Hex IDs of sidings belonging to this depot |
| `routes` | string[] | Hex IDs of routes assigned to this depot |
| `trainCount` | int | Number of trains this depot spawns |
| `trainLength` | int | Number of cars per train |

---

## GET `/api/sidings`

Returns all sidings with their current vehicle (train) data.

### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `dimension` | string | No | Filter by dimension |

### Response

```json
{
  "cachedResponseTime": 1726200000000,
  "sidings": [
    {
      "id": "0000000000000011-0000000000000012-0000000000000013-0000000000000014-0000000000000015-0000000000000016",
      "name": "Siding A",
      "trainCount": 1,
      "trainLength": 8,
      "position": { "x": 100, "y": 64, "z": 200 },
      "depotId": "0000000000000001-0000000000000002-...",
      "vehicles": [
        {
          "id": "0000000000000031-0000000000000032",
          "speed": 0.45,
          "isOnRoute": true,
          "isFixedRoute": false,
          "routeId": "0000000000000021-0000000000000022",
          "platformId": "0000000000000041-0000000000000042"
        }
      ]
    }
  ]
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Hex-encoded siding ID |
| `name` | string | User-assigned name |
| `trainCount` | int | Max trains this siding holds |
| `trainLength` | int | Max cars per train |
| `position` | object | World coordinates of the siding start |
| `depotId` | string | Hex ID of the parent depot (if any) |
| `vehicles` | array | Currently assigned trains (see below) |

#### Vehicle Object

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Hex-encoded vehicle ID |
| `speed` | double | Current speed in blocks/tick |
| `isOnRoute` | boolean | Whether the vehicle is currently running its route |
| `isFixedRoute` | boolean | Whether the vehicle is on a fixed path |
| `routeId` | string | Hex ID of the current route |
| `platformId` | string | Hex ID of the next target platform |

---

## GET `/api/resourcepack`

Returns all available vehicle (car) types registered in the MTR resource pack.

### Response

```json
{
  "cachedResponseTime": 1726200000000,
  "availableCars": [
    {
      "name": "SP1900",
      "id": "SP1900",
      "length": 23.0,
      "width": 3.0,
      "maxSpeed": 0.5
    },
    {
      "name": "MLR",
      "id": "MLR",
      "length": 19.5,
      "width": 3.0,
      "maxSpeed": 0.361
    }
  ]
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Display name of the car type |
| `id` | string | Internal identifier |
| `length` | double | Car length in blocks |
| `width` | double | Car width in blocks |
| `maxSpeed` | double | Maximum speed in blocks/tick |

> **Note:** This data is read via reflection from `VehicleVehicle`. If the class structure changes, a fallback message is returned.

---

## GET `/api/trackgraph`

Returns the full track graph as a set of nodes and edges (rails).

### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `dimension` | string | No | Filter by dimension |

### Response

```json
{
  "cachedResponseTime": 1726200000000,
  "nodes": [
    { "x": 100, "y": 64, "z": 200 },
    { "x": 120, "y": 64, "z": 200 }
  ],
  "edges": [
    {
      "id": "0000000000000001-0000000000000002-...",
      "from": { "x": 100, "y": 64, "z": 200 },
      "to": { "x": 120, "y": 64, "z": 200 },
      "length": 20.0,
      "speedLimit1": 0.1389,
      "speedLimit2": 0.1389
    }
  ]
}
```

### Fields

#### Node

| Field | Type | Description |
|-------|------|-------------|
| `x` | long | X coordinate (block-level precision) |
| `y` | long | Y coordinate |
| `z` | long | Z coordinate |

#### Edge

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Hex-encoded rail ID |
| `from` | object | Start node `{x, y, z}` |
| `to` | object | End node `{x, y, z}` |
| `length` | double | Rail length in blocks |
| `speedLimit1` | double | Speed limit at the start node (blocks/ms, i.e. km/h) |
| `speedLimit2` | double | Speed limit at the end node (blocks/ms, i.e. km/h) |

---

## GET `/api/trains`

Returns the real-time positions of all trains across all sidings.

### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `dimension` | string | No | Filter by dimension |

### Response

```json
{
  "cachedResponseTime": 1726200000000,
  "trains": [
    {
      "id": "0000000000000031-0000000000000032",
      "sidingId": "0000000000000011-...",
      "speed": 0.45,
      "isOnRoute": true,
      "railProgress": 128.5,
      "routeId": "0000000000000021-0000000000000022",
      "platformId": "0000000000000041-0000000000000042"
    }
  ]
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Hex-encoded vehicle ID |
| `sidingId` | string | Hex ID of the siding this train is assigned to |
| `speed` | double | Current speed (blocks/tick) |
| `isOnRoute` | boolean | Whether the train is in active service |
| `railProgress` | double | Current progress along the rail path |
| `routeId` | string | Hex ID of the assigned route |
| `platformId` | string | Hex ID of the next platform |

---

## GET `/api/rails`

Legacy endpoint. Returns rails, vehicles, and waypoints for the system map overlay. Same data as the MTR System Map API.

### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `dimension` | string | No | Filter by dimension |
| `mode` | string | No | `"rails"`, `"vehicles"`, or `"routepath"` to return only that subset. Omit for full response. |
| `routeId` | string | No | When `mode=routepath`, the hex ID of the route to trace |

### Response (mode omitted)

```json
{
  "cachedResponseTime": 1726200000000,
  "jmeRailOverlayMode": "OPEN_RAILWAY_MAP",
  "jmeShowBaseRails": true,
  "jmeShowDetails": true,
  "jmeShowSignals": true,
  "jmeShowVehicles": true,
  "jmeRespectRouteFilters": true,
  "rails": [ ... ],
  "vehicles": [ ... ],
  "waypoints": [ ... ]
}
```

---

## POST `/api/routes`

Create a route or modify its schedule.

### Request Body

**Create a route:**

```json
{
  "action": "create",
  "name": "Tung Chung Line",
  "color": "FF0000"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `action` | string | Yes | Must be `"create"` or `"setSchedule"` |
| `name` | string | Yes (create) | Route display name |
| `color` | string | No | Hex color string (with or without `#`). Default: `FFFFFF` |

**Set schedule:**

```json
{
  "action": "setSchedule",
  "routeId": "0000000000000021-0000000000000022",
  "frequency": 120,
  "circular": false
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `action` | string | Yes | Must be `"setSchedule"` |
| `routeId` | string | Yes | Hex ID of the route to modify |
| `frequency` | int | No | Headway in seconds (default: 120) |
| `circular` | boolean | No | Whether the route runs in a loop |

### Response

```json
{
  "cachedResponseTime": 1726200000000,
  "success": true,
  "routeId": "0000000000000021-0000000000000022"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `success` | boolean | Whether the operation succeeded |
| `routeId` | string | Hex ID of the created/modified route |
| `error` | string | Error message (only when `success` is `false`) |

---

## POST `/api/depots`

Modify depot configuration (train count and train length).

### Request Body

```json
{
  "depotId": "0000000000000001-0000000000000002-...",
  "trainCount": 6,
  "trainLength": 10
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `depotId` | string | Yes | Hex ID of the depot |
| `trainCount` | int | No | New train count |
| `trainLength` | int | No | New train length (cars per train) |

### Response

```json
{
  "cachedResponseTime": 1726200000000,
  "success": true,
  "depotId": "0000000000000001-0000000000000002-..."
}
```

| Field | Type | Description |
|-------|------|-------------|
| `success` | boolean | Whether the operation succeeded |
| `depotId` | string | Hex ID of the depot |
| `error` | string | Error message (only when `success` is `false`) |
| `note` | string | Informational note (if setter methods were unavailable) |

---

## POST `/api/platforms`

Change platform properties: station name, dwell time.

### Request Body

```json
{
  "platformId": "0000000000000041-0000000000000042",
  "name": "Hong Kong",
  "dwellTime": 30
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `platformId` | string | Yes | Hex ID of the platform |
| `name` | string | No | New station/platform name |
| `dwellTime` | int | No | New dwell time in seconds |

### Response

```json
{
  "cachedResponseTime": 1726200000000,
  "success": true,
  "platformId": "0000000000000041-0000000000000042"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `success` | boolean | Whether the operation succeeded |
| `platformId` | string | Hex ID of the platform |
| `error` | string | Error message (only when `success` is `false`) |
| `note` | string | Informational note (if setter methods were unavailable) |

---

## GET `/api/tiles/{z}/{x}/{y}.png`

Returns a map tile image.

### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `z` | int | Zoom level |
| `x` | int | Tile X coordinate |
| `y` | int | Tile Y coordinate |

### Response

Binary PNG image (`image/png`).

Cache headers: `Cache-Control: public, max-age=30, stale-while-revalidate=600`.

---

## Route Types (Rail Categories)

MAGIC supports the following route/rail type categories. These can be assigned to routes via the route type override config or the System Map UI.

| Type Constant | String Value | Aliases |
|---------------|-------------|---------|
| `TRAIN_NORMAL` | `train_normal` | `normal` |
| `TRAIN_LIGHT_RAIL` | `train_light_rail` | `light_rail`, `lightrail` |
| `TRAIN_HIGH_SPEED` | `train_high_speed` | `high_speed`, `highspeed` |
| `TRAIN_METRO` | `train_metro` | `metro` |
| `TRAIN_BUS` | `train_bus` | `bus` |
| `TRAIN_TRAM` | `train_tram` | `tram` |
| `TRAIN_SBAHN` | `train_sbahn` | `sbahn`, `s_bahn`, `commuter`, `commuter_rail` |
| `TRAIN_TROLLEYBUS` | `train_trolleybus` | `trolleybus`, `trolley`, `trolley_bus` |
| `TRAIN_FREIGHT` | `train_freight` | `freight`, `cargo`, `goods` |

---

## Configuration

### Port

The dashboard server port defaults to **8088**. Override with:

```
-Dmagic.dashboard.port=9090
```

### Config Files

All MAGIC configuration files are stored under `config/MAGIC/`:

| File | Description |
|------|-------------|
| `configs/magic.json` | Main settings |
| `configs/route_types.json` | Per-route type overrides |
| `configs/depot_cancellations.json` | Per-depot cancellation settings |
| `configs/alternative_platforms.json` | Alternative platform mappings |
| `configs/platform_stop_positions.json` | Platform stop position settings |
| `configs/waypoints.json` | Custom waypoint nodes |

---

## Cancellation Behavior

When a train is cancelled (delay exceeds the depot threshold), it is **fully removed** from the vehicle list. It does not persist as a 0-car ghost entry. The cancellation record is stored internally for PIDS "Service Cancelled" display but does not appear in any API endpoint's vehicle/train data.

---

## Error Handling

All API endpoints return HTTP 200 with a JSON body containing `"success": false` and an `"error"` field when an operation fails. The server never returns HTTP 500 for application-level errors.

| HTTP Status | Meaning |
|-------------|---------|
| 200 | Request processed (check `success` field) |
| 404 | Endpoint not found |
| 405 | HTTP method not allowed |
| 500 | Internal server error (should not occur) |
