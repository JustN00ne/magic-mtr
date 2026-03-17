(function () {
  if (window.__jmeSystemMapOverlayInitialized) {
    return;
  }
  window.__jmeSystemMapOverlayInitialized = true;

  const REFRESH_ROUTES_MS = 30000;
  const REFRESH_RAILS_MS = 3000;
  const TRANSFORM_REFRESH_MS = 120;
  const MIN_MATCHES_FOR_TRANSFORM = 4;
  const MAX_TRANSFORM_ERROR_PX = 72;
  const MAX_OFFSCREEN_MARGIN = 0.75;

  const SPEED_STOPS = [5, 100, 180, 220, 300, 400];
  const SPEED_COLORS = [
    [16, 42, 138],
    [37, 201, 119],
    [217, 227, 68],
    [255, 224, 40],
    [239, 58, 38],
    [180, 42, 230],
  ];

  const state = {
    ready: false,
    wrapper: null,
    overlayCanvas: null,
    overlayCtx: null,
    dimension: "0",
    stations: [],
    routes: [],
    rails: [],
    vehicles: [],
    stationByName: new Map(),
    routeColorById: new Map(),
    routeTypeById: new Map(),
    transform: null,
    lastTransformUpdate: 0,
    routesRequestInFlight: false,
    railsRequestInFlight: false,
    railOverlayMode: "all", // all | cull | off
    railCullMaxPerCell: 8,
    menuRoot: null,
    menuButton: null,
    menuPanel: null,
    menuOpen: false,
    lastMouseScreen: null,
    transformSamples: [],
    lastSampleAt: 0,
    syntheticProbeQueue: [],
    lastSyntheticProbeAt: 0,
  };

  patchRequestTracking();
  bootstrap();

  function bootstrap() {
    if (state.ready) {
      return;
    }

    const wrapper = document.querySelector("app-map .wrapper");
    if (!wrapper) {
      setTimeout(bootstrap, 500);
      return;
    }

    state.wrapper = wrapper;
    ensureGoogleIconsLoaded();
    initOverlay();
    initTransformSampling();

    state.ready = true;
    refreshAll();
    setInterval(() => {
      if (!isPageHidden()) {
        fetchStationsAndRoutes();
      }
    }, REFRESH_ROUTES_MS);
    setInterval(() => {
      if (!isPageHidden()) {
        fetchRails();
      }
    }, REFRESH_RAILS_MS);

    requestAnimationFrame(renderLoop);
  }

  function ensureGoogleIconsLoaded() {
    const head = document.head || document.getElementsByTagName("head")[0];
    if (!head) {
      return;
    }

    const hasStylesheet = (needle) => {
      const links = Array.from(document.querySelectorAll('link[rel="stylesheet"]'));
      return links.some(link => {
        try {
          return String(link.href || "").includes(needle);
        } catch (ignored) {
          return false;
        }
      });
    };

    // Used by the menu chips and many of MTR's own UI icons.
    if (!hasStylesheet("fonts.googleapis.com/icon?family=Material+Icons")) {
      const link = document.createElement("link");
      link.rel = "stylesheet";
      link.href = "https://fonts.googleapis.com/icon?family=Material+Icons";
      link.setAttribute("data-jme-google-icons", "material-icons");
      head.appendChild(link);
    }

    // Used by vehicle markers rendered onto the canvas.
    if (!hasStylesheet("fonts.googleapis.com/css2?family=Material+Symbols+Outlined")) {
      const link = document.createElement("link");
      link.rel = "stylesheet";
      link.href = "https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,400,0,0";
      link.setAttribute("data-jme-google-icons", "material-symbols-outlined");
      head.appendChild(link);
    }
  }

  function initOverlay() {
    const existing = state.wrapper.querySelector("#jme-rails-overlay");
    if (existing) {
      existing.remove();
    }

    const overlayCanvas = document.createElement("canvas");
    overlayCanvas.id = "jme-rails-overlay";
    overlayCanvas.style.pointerEvents = "none";
    overlayCanvas.style.zIndex = "6";
    state.wrapper.appendChild(overlayCanvas);

    state.overlayCanvas = overlayCanvas;
    state.overlayCtx = overlayCanvas.getContext("2d");

    resizeOverlay();
    window.addEventListener("resize", resizeOverlay);
  }

  function refreshAll() {
    fetchStationsAndRoutes();
    fetchRails();
  }

  async function fetchStationsAndRoutes() {
    if (state.routesRequestInFlight) {
      return;
    }
    state.routesRequestInFlight = true;
    try {
      const response = await fetch(apiUrl("stations-and-routes"), { cache: "no-store" });
      if (!response.ok) {
        return;
      }

      const payload = await response.json();
      const data = unwrapData(payload);
      if (!data) {
        return;
      }

      state.stations = Array.isArray(data.stations) ? data.stations : [];
      state.routes = Array.isArray(data.routes) ? data.routes : [];

      rebuildRouteMaps();
      rebuildStationLookup();
    } catch (ignored) {
      // Keep previous snapshot.
    } finally {
      state.routesRequestInFlight = false;
    }
  }

  async function fetchRails() {
    if (state.railsRequestInFlight) {
      return;
    }
    state.railsRequestInFlight = true;
    try {
      const response = await fetch(apiUrl("rails"), { cache: "no-store" });
      if (!response.ok) {
        return;
      }

      const payload = await response.json();
      const data = unwrapData(payload);
      applyServerOverlaySettings(data);
      state.rails = Array.isArray(data.rails) ? data.rails : [];
      state.vehicles = Array.isArray(data.vehicles) ? data.vehicles : [];
    } catch (ignored) {
      // Keep previous snapshot.
    } finally {
      state.railsRequestInFlight = false;
    }
  }

  function isPageHidden() {
    return typeof document !== "undefined" && document.visibilityState === "hidden";
  }

  function unwrapData(payload) {
    if (!payload || typeof payload !== "object") {
      return {};
    }
    return payload.data && typeof payload.data === "object" ? payload.data : payload;
  }

  function applyServerOverlaySettings(data) {
    if (!data || typeof data !== "object") {
      return;
    }

    const mode = String(data.jmeRailOverlayMode || "").trim().toLowerCase();
    if (mode === "all" || mode === "cull" || mode === "off") {
      state.railOverlayMode = mode;
    }

    const maxPerCell = parseNumeric(data.jmeRailCullMaxPerCell);
    if (Number.isFinite(maxPerCell)) {
      state.railCullMaxPerCell = clamp(Math.round(maxPerCell), 1, 64);
    }
  }

  function rebuildRouteMaps() {
    state.routeColorById.clear();
    state.routeTypeById.clear();

    state.routes.forEach(route => {
      const routeId = normalizeId(route && route.id);
      if (!routeId) {
        return;
      }

      const color = parseNumeric(route && route.color);
      if (Number.isFinite(color)) {
        state.routeColorById.set(routeId, color & 0xFFFFFF);
      }

      const routeType = String((route && route.type) || "").trim();
      if (routeType) {
        state.routeTypeById.set(routeId, routeType);
      }
    });
  }

  function rebuildStationLookup() {
    const positionsByName = new Map();

    state.routes.forEach(route => {
      if (!route || route.hidden || !Array.isArray(route.stations)) {
        return;
      }

      route.stations.forEach(routeStation => {
        if (!routeStation) {
          return;
        }

        const name = normalizeName(routeStation.name);
        if (!name) {
          return;
        }

        const x = Number(routeStation.x);
        const z = Number(routeStation.z);
        if (!Number.isFinite(x) || !Number.isFinite(z)) {
          return;
        }

        let entries = positionsByName.get(name);
        if (!entries) {
          entries = [];
          positionsByName.set(name, entries);
        }
        entries.push({ x, z });
      });
    });

    state.stationByName.clear();
    positionsByName.forEach((entries, name) => {
      if (!entries || !entries.length) {
        return;
      }
      state.stationByName.set(name, entries);
    });
  }

  function renderLoop(timestamp) {
    requestAnimationFrame(renderLoop);

    if (!state.ready || !state.overlayCanvas || !state.overlayCtx) {
      return;
    }

    resizeOverlay();
    runSyntheticProbeStep();
    updateTransform(timestamp);
    drawMapOverlay();
  }

  function updateTransform(timestamp) {
    if (timestamp - state.lastTransformUpdate < TRANSFORM_REFRESH_MS) {
      return;
    }
    state.lastTransformUpdate = timestamp;

    const matches = collectStationMatches();
    if (matches.length >= MIN_MATCHES_FOR_TRANSFORM) {
      const candidate = solveTransform(matches);
      if (candidate && isTransformStable(candidate, matches)) {
        state.transform = candidate;
        return;
      }
    }

    const sampleMatches = getTransformSamples();
    if (sampleMatches.length >= 3) {
      const candidateFromSamples = solveTransformFromSamples(sampleMatches);
      if (candidateFromSamples && isTransformStable(candidateFromSamples, sampleMatches)) {
        state.transform = candidateFromSamples;
        return;
      }
    }

    // Keep the last known transform if we can't solve a new one; it is still better than hiding everything.
    if (!state.transform && !state.syntheticProbeQueue.length) {
      scheduleSyntheticProbe(timestamp);
    }
  }

  function collectStationMatches() {
    if (!state.wrapper) {
      return [];
    }

    const labels = state.wrapper.querySelectorAll(".column.center.label");
    const matches = [];

    labels.forEach(label => {
      const stationName = readStationNameFromLabel(label);
      if (!stationName) {
        return;
      }

      const screenPosition = readLabelScreenPosition(label);
      if (!screenPosition) {
        return;
      }

      const worldPosition = pickWorldPositionForLabel(normalizeName(stationName), screenPosition);
      if (!worldPosition) {
        return;
      }

      matches.push({
        worldX: worldPosition.x,
        worldZ: worldPosition.z,
        screenX: screenPosition.x,
        screenY: screenPosition.y,
      });
    });

    return matches;
  }

  function drawMapOverlay() {
    const ctx = state.overlayCtx;
    const width = state.overlayCanvas.clientWidth;
    const height = state.overlayCanvas.clientHeight;
    ctx.clearRect(0, 0, width, height);

    if (!state.transform) {
      return;
    }

    const pxPerBlock = (Math.abs(state.transform.scaleX) + Math.abs(state.transform.scaleY)) / 2;
    const preparedRails = prepareRailsForRender(pxPerBlock);
    drawRails(ctx, pxPerBlock, preparedRails);
    drawSignals(ctx, pxPerBlock, preparedRails);
    drawVehicles(ctx, pxPerBlock);
  }

  function drawRails(ctx, pxPerBlock, preparedRails) {
    const lineWidth = clamp(pxPerBlock * 0.22, 1.2, 5.6);
    ctx.lineCap = "round";
    ctx.lineJoin = "round";

    preparedRails.forEach(entry => {
      const rail = entry.rail;
      const points = entry.points;
      const speedKmh = entry.speedKmh;

      let started = false;
      ctx.beginPath();
      points.forEach(point => {
        const screen = worldToScreen(point.x, point.z);
        if (!screen) {
          return;
        }

        if (!started) {
          ctx.moveTo(screen.x, screen.y);
          started = true;
        } else {
          ctx.lineTo(screen.x, screen.y);
        }
      });

      if (!started) {
        return;
      }

      ctx.strokeStyle = railSpeedColor(speedKmh);
      ctx.globalAlpha = 0.95;
      ctx.lineWidth = lineWidth;
      ctx.stroke();
    });

    ctx.globalAlpha = 1;
  }

  function isProjectedRailPlausible(points) {
    if (!state.overlayCanvas || !points || points.length < 2) {
      return false;
    }

    const width = state.overlayCanvas.clientWidth;
    const height = state.overlayCanvas.clientHeight;
    if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
      return false;
    }

    const minX = -width * MAX_OFFSCREEN_MARGIN;
    const maxX = width * (1 + MAX_OFFSCREEN_MARGIN);
    const minY = -height * MAX_OFFSCREEN_MARGIN;
    const maxY = height * (1 + MAX_OFFSCREEN_MARGIN);

    let inBoundsCount = 0;
    for (let i = 0; i < points.length; i++) {
      const screen = worldToScreen(Number(points[i].x), Number(points[i].z));
      if (!screen || !Number.isFinite(screen.x) || !Number.isFinite(screen.y)) {
        continue;
      }
      if (screen.x >= minX && screen.x <= maxX && screen.y >= minY && screen.y <= maxY) {
        inBoundsCount++;
      }
    }

    return inBoundsCount >= 2;
  }

  function drawSignals(ctx, pxPerBlock, preparedRails) {
    const size = clamp(pxPerBlock * 1.85, 5, 12);

    preparedRails.forEach(entry => {
      const rail = entry.rail;
      const points = entry.points;
      const signalColors = Array.isArray(rail && rail.signalColors) ? rail.signalColors : [];
      if (!signalColors.length) {
        return;
      }

      const signalColor = resolveSignalAspectColor(signalColors, rail);
      drawArrowAlongSegment(ctx, points[0], points[1], size, signalColor, 0.95);
      drawArrowAlongSegment(ctx, points[points.length - 1], points[points.length - 2], size, signalColor, 0.95);
    });

    ctx.globalAlpha = 1;
  }

  function drawVehicles(ctx, pxPerBlock) {
    if (!state.vehicles.length) {
      return;
    }

    const headSize = clamp(pxPerBlock * 2.4, 9, 18);
    const carSize = clamp(headSize * 0.65, 6, 12);

    state.vehicles.forEach(vehicle => {
      const routeId = normalizeId(vehicle && vehicle.routeId);
      if (routeId && !isRouteIdVisibleByMode(routeId)) {
        return;
      }

      const routeType = state.routeTypeById.get(routeId) || "";
      const iconName = routeTypeToIcon(routeType);
      const routeColor = state.routeColorById.has(routeId) ? intToColor(state.routeColorById.get(routeId)) : "#7ea6ff";

      const cars = Array.isArray(vehicle && vehicle.cars) ? vehicle.cars : [];
      cars.forEach(car => {
        const position = car && car.position;
        if (!position) {
          return;
        }
        const screen = worldToScreen(Number(position.x), Number(position.z));
        if (!screen) {
          return;
        }
        drawVehicleMarker(ctx, screen.x, screen.y, carSize, routeColor, iconName, 0.82);
      });

      const position = vehicle && vehicle.position;
      if (!position) {
        return;
      }

      const screen = worldToScreen(Number(position.x), Number(position.z));
      if (!screen) {
        return;
      }

      drawVehicleMarker(ctx, screen.x, screen.y, headSize, routeColor, iconName, 1);
    });

    ctx.globalAlpha = 1;
  }

  function drawVehicleMarker(ctx, x, y, size, color, iconName, alpha) {
    ctx.save();
    ctx.globalAlpha = clamp(alpha, 0, 1);

    const radius = size * 0.52;
    ctx.beginPath();
    ctx.arc(x, y, radius, 0, Math.PI * 2);
    ctx.fillStyle = color;
    ctx.fill();

    ctx.lineWidth = clamp(size * 0.14, 1.2, 2.4);
    ctx.strokeStyle = "#141414";
    ctx.stroke();

    ctx.fillStyle = "#ffffff";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.font = `${Math.round(size * 0.82)}px "Material Symbols Outlined", "Material Icons", sans-serif`;
    ctx.fillText(iconName, x, y + size * 0.02);

    ctx.restore();
  }

  function routeTypeToIcon(rawType) {
    const type = normalizeRouteType(rawType);

    const exact = {
      train_metro: "subway",
      train_bus: "directions_bus",
      train_tram: "tram",
      train_sbahn: "directions_railway_2",
      train_normal: "directions_railway",
      train_light_rail: "tram",
      train_high_speed: "train",
      boat_normal: "sailing",
      boat_light_rail: "directions_boat",
      boat_high_speed: "snowmobile",
      cable_car_normal: "airline_seat_recline_extra",
      bus_normal: "directions_bus",
      bus_light_rail: "local_taxi",
      bus_high_speed: "airport_shuttle",
      airplane_normal: "flight",
    };
    if (exact[type]) {
      return exact[type];
    }

    if (type.includes("metro")) {
      return "subway";
    }
    if (type.includes("bus")) {
      return "directions_bus";
    }
    if (type.includes("tram") || type.includes("lightrail")) {
      return "tram";
    }
    if (type.includes("sbahn") || type.includes("s_bahn")) {
      return "directions_railway_2";
    }
    if (type.includes("highspeed")) {
      return "train";
    }

    return "directions_railway";
  }

  function normalizeRouteType(value) {
    return String(value || "")
      .toLowerCase()
      .replace(/[^a-z0-9_]+/g, "_");
  }

  function getRailPoints(rail) {
    const points = [];

    const curvePoints = Array.isArray(rail && rail.curvePoints) ? rail.curvePoints : [];
    curvePoints.forEach(point => {
      const x = Number(point && point.x);
      const z = Number(point && point.z);
      if (Number.isFinite(x) && Number.isFinite(z)) {
        points.push({ x, z });
      }
    });

    if (points.length >= 2) {
      return points;
    }

    const position1 = rail && rail.position1;
    const position2 = rail && rail.position2;
    const x1 = Number(position1 && position1.x);
    const z1 = Number(position1 && position1.z);
    const x2 = Number(position2 && position2.x);
    const z2 = Number(position2 && position2.z);

    if (Number.isFinite(x1) && Number.isFinite(z1) && Number.isFinite(x2) && Number.isFinite(z2)) {
      return [{ x: x1, z: z1 }, { x: x2, z: z2 }];
    }

    return points;
  }

  function getRouteIdsForRail(rail) {
    const result = new Set();
    const nodes = Array.isArray(rail && rail.connectedNodes) ? rail.connectedNodes : [];
    nodes.forEach(node => {
      const routes = Array.isArray(node && node.routes) ? node.routes : [];
      routes.forEach(routeId => {
        const normalized = normalizeId(routeId);
        if (normalized) {
          result.add(normalized);
        }
      });
    });
    return Array.from(result);
  }

  function isRouteIdVisibleByMode(routeId) {
    const routeType = normalizeRouteType(state.routeTypeById.get(routeId) || "");
    if (!routeType) {
      return true;
    }
    const cookieValue = readCookie(`visibility_${routeType}`);
    return cookieValue !== "HIDDEN";
  }

  function readCookie(name) {
    if (!name) {
      return "";
    }

    const source = String(document.cookie || "");
    if (!source) {
      return "";
    }

    const target = `${name}=`;
    const parts = source.split(";");
    for (let i = 0; i < parts.length; i++) {
      const entry = parts[i].trim();
      if (entry.startsWith(target)) {
        try {
          return decodeURIComponent(entry.substring(target.length));
        } catch (ignored) {
          return entry.substring(target.length);
        }
      }
    }
    return "";
  }

  function getRailSpeedLimitKmh(rail) {
    let best = 0;

    const nodes = Array.isArray(rail && rail.connectedNodes) ? rail.connectedNodes : [];
    nodes.forEach(node => {
      const speed = parseNumeric(node && node.speedLimit);
      if (Number.isFinite(speed) && speed > best) {
        best = speed;
      }
    });

    const speedLimit1 = parseNumeric(rail && rail.speedLimit1);
    const speedLimit2 = parseNumeric(rail && rail.speedLimit2);
    if (Number.isFinite(speedLimit1) && speedLimit1 > best) {
      best = speedLimit1;
    }
    if (Number.isFinite(speedLimit2) && speedLimit2 > best) {
      best = speedLimit2;
    }

    return Math.max(0, best);
  }

  function railSpeedColor(speedKmh) {
    const speed = clamp(Number(speedKmh) || 0, SPEED_STOPS[0], SPEED_STOPS[SPEED_STOPS.length - 1]);

    for (let i = 0; i < SPEED_STOPS.length - 1; i++) {
      const start = SPEED_STOPS[i];
      const end = SPEED_STOPS[i + 1];
      if (speed <= end) {
        const t = end === start ? 0 : (speed - start) / (end - start);
        const c1 = SPEED_COLORS[i];
        const c2 = SPEED_COLORS[i + 1];
        const r = Math.round(c1[0] + (c2[0] - c1[0]) * t);
        const g = Math.round(c1[1] + (c2[1] - c1[1]) * t);
        const b = Math.round(c1[2] + (c2[2] - c1[2]) * t);
        return `rgb(${r}, ${g}, ${b})`;
      }
    }

    const last = SPEED_COLORS[SPEED_COLORS.length - 1];
    return `rgb(${last[0]}, ${last[1]}, ${last[2]})`;
  }

  function resolveSignalAspectColor(signalColors, rail) {
    const values = signalColors
      .map(value => parseNumeric(value))
      .filter(value => Number.isFinite(value));

    if (!values.length) {
      return railSpeedColor(getRailSpeedLimitKmh(rail));
    }

    const first = values[0];
    if (first >= 0 && first <= 3) {
      switch (Math.round(first)) {
        case 0:
          return "#ff3b30";
        case 1:
          return "#ffd60a";
        case 2:
          return "#34c759";
        case 3:
          return "#0a84ff";
        default:
          break;
      }
    }

    return intToColor(first);
  }

  function drawArrowAlongSegment(ctx, fromPoint, toPoint, size, color, alpha) {
    if (!fromPoint || !toPoint) {
      return;
    }

    const from = worldToScreen(Number(fromPoint.x), Number(fromPoint.z));
    const to = worldToScreen(Number(toPoint.x), Number(toPoint.z));
    if (!from || !to) {
      return;
    }

    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const distance = Math.hypot(dx, dy);
    if (distance < 1) {
      return;
    }

    const ux = dx / distance;
    const uy = dy / distance;
    const offset = clamp(size * 0.68, 3, 9);
    const x = from.x + ux * offset;
    const y = from.y + uy * offset;
    const angle = Math.atan2(dy, dx);
    const width = size * 0.8;
    const tail = size * 0.9;

    ctx.save();
    ctx.translate(x, y);
    ctx.rotate(angle);
    ctx.globalAlpha = alpha;

    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.moveTo(size, 0);
    ctx.lineTo(-tail, width * 0.65);
    ctx.lineTo(-tail * 0.2, 0);
    ctx.lineTo(-tail, -width * 0.65);
    ctx.closePath();
    ctx.fill();

    ctx.strokeStyle = "rgba(20, 20, 20, 0.85)";
    ctx.lineWidth = clamp(size * 0.16, 1, 2);
    ctx.stroke();

    ctx.restore();
  }

  function resizeOverlay() {
    if (!state.wrapper || !state.overlayCanvas || !state.overlayCtx) {
      return;
    }

    const cssWidth = state.wrapper.clientWidth;
    const cssHeight = state.wrapper.clientHeight;
    const dpr = window.devicePixelRatio || 1;
    const pixelWidth = Math.max(1, Math.floor(cssWidth * dpr));
    const pixelHeight = Math.max(1, Math.floor(cssHeight * dpr));

    if (state.overlayCanvas.width !== pixelWidth || state.overlayCanvas.height !== pixelHeight) {
      state.overlayCanvas.width = pixelWidth;
      state.overlayCanvas.height = pixelHeight;
      state.overlayCanvas.style.width = `${cssWidth}px`;
      state.overlayCanvas.style.height = `${cssHeight}px`;
      state.overlayCtx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
  }

  function readStationNameFromLabel(label) {
    const textNodes = label.querySelectorAll(".station-name.text");
    if (textNodes.length) {
      const parts = [];
      textNodes.forEach(node => {
        const text = node.textContent ? node.textContent.trim() : "";
        if (text) {
          parts.push(text);
        }
      });
      return parts.join(" ");
    }

    return label.textContent ? label.textContent.trim() : "";
  }

  function readLabelScreenPosition(label) {
    const transform = label.style.transform || "";
    const translateMatch = transform.match(/translate\(([-\d.]+)px,\s*([-\d.]+)px\)(?!.*translate)/);
    if (translateMatch) {
      const x = Number(translateMatch[1]);
      const y = Number(translateMatch[2]);
      if (Number.isFinite(x) && Number.isFinite(y)) {
        return { x, y };
      }
    }

    if (!state.wrapper) {
      return null;
    }

    const wrapperRect = state.wrapper.getBoundingClientRect();
    const labelRect = label.getBoundingClientRect();
    if (!wrapperRect || !labelRect) {
      return null;
    }

    return {
      x: labelRect.left - wrapperRect.left + labelRect.width / 2,
      y: labelRect.top - wrapperRect.top + labelRect.height / 2,
    };
  }

  function pickWorldPositionForLabel(normalizedName, screenPosition) {
    if (!normalizedName) {
      return null;
    }

    const candidates = state.stationByName.get(normalizedName);
    if (!candidates || !candidates.length) {
      return null;
    }
    if (candidates.length === 1 || !screenPosition) {
      return candidates[0];
    }

    if (!state.transform) {
      let sumX = 0;
      let sumZ = 0;
      let count = 0;
      candidates.forEach(candidate => {
        if (!candidate) {
          return;
        }
        sumX += Number(candidate.x) || 0;
        sumZ += Number(candidate.z) || 0;
        count += 1;
      });
      if (count > 0) {
        return { x: sumX / count, z: sumZ / count };
      }
      return candidates[0];
    }

    let best = candidates[0];
    let bestDistance = Number.POSITIVE_INFINITY;
    candidates.forEach(candidate => {
      const projected = worldToScreenWithTransform(state.transform, candidate.x, candidate.z);
      if (!projected) {
        return;
      }
      const distance = Math.hypot(projected.x - screenPosition.x, projected.y - screenPosition.y);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    });

    return best;
  }

  function isTransformStable(transform, matches) {
    const scaleX = Math.abs(transform.scaleX);
    const scaleY = Math.abs(transform.scaleY);
    if (!Number.isFinite(scaleX) || !Number.isFinite(scaleY) || scaleX < 1.0e-5 || scaleY < 1.0e-5) {
      return false;
    }

    if (scaleX > 20 || scaleY > 20) {
      return false;
    }

    const ratio = Math.max(scaleX, scaleY) / Math.max(1.0e-5, Math.min(scaleX, scaleY));
    if (ratio > 12) {
      return false;
    }

    const error = computeTransformError(transform, matches);
    return Number.isFinite(error) && error <= MAX_TRANSFORM_ERROR_PX;
  }

  function computeTransformError(transform, matches) {
    if (!matches || !matches.length) {
      return Number.POSITIVE_INFINITY;
    }

    let totalError = 0;
    let count = 0;
    matches.forEach(match => {
      const projected = worldToScreenWithTransform(transform, match.worldX, match.worldZ);
      if (!projected) {
        return;
      }
      totalError += Math.hypot(projected.x - match.screenX, projected.y - match.screenY);
      count += 1;
    });

    return count <= 0 ? Number.POSITIVE_INFINITY : totalError / count;
  }

  function solveLinear(points, worldKey, screenKey) {
    let count = 0;
    let sumWorld = 0;
    let sumScreen = 0;
    let sumWorldSquared = 0;
    let sumWorldScreen = 0;

    points.forEach(point => {
      const world = Number(point[worldKey]);
      const screen = Number(point[screenKey]);
      if (!Number.isFinite(world) || !Number.isFinite(screen)) {
        return;
      }

      count += 1;
      sumWorld += world;
      sumScreen += screen;
      sumWorldSquared += world * world;
      sumWorldScreen += world * screen;
    });

    if (count < 2) {
      return null;
    }

    const denominator = count * sumWorldSquared - sumWorld * sumWorld;
    if (Math.abs(denominator) < 1.0e-6) {
      return null;
    }

    const scale = (count * sumWorldScreen - sumWorld * sumScreen) / denominator;
    const offset = (sumScreen - scale * sumWorld) / count;
    if (!Number.isFinite(scale) || !Number.isFinite(offset)) {
      return null;
    }

    return { scale, offset };
  }

  function worldToScreen(x, z) {
    return worldToScreenWithTransform(state.transform, x, z);
  }

  function worldToScreenWithTransform(transform, x, z) {
    if (!transform || !Number.isFinite(x) || !Number.isFinite(z)) {
      return null;
    }

    return {
      x: transform.scaleX * x + transform.offsetX,
      y: transform.scaleY * z + transform.offsetY,
    };
  }

  function apiUrl(endpoint) {
    const dimension = encodeURIComponent(state.dimension || "0");
    return `${window.location.origin}${window.location.pathname}mtr/api/map/${endpoint}?dimension=${dimension}`;
  }

  function normalizeName(value) {
    return String(value || "")
      .toLowerCase()
      .replace(/\|/g, " ")
      .replace(/[^a-z0-9\u00c0-\uffff]+/g, "")
      .trim();
  }

  function normalizeId(value) {
    if (value === undefined || value === null) {
      return "";
    }
    return String(value).trim().toLowerCase();
  }

  function intToColor(colorInt) {
    const hex = (Number(colorInt) & 0xFFFFFF).toString(16).padStart(6, "0");
    return `#${hex}`;
  }

  function parseNumeric(value) {
    if (typeof value === "number") {
      return value;
    }

    const stringValue = String(value || "").trim();
    if (!stringValue) {
      return Number.NaN;
    }

    if (/^0x/i.test(stringValue)) {
      return parseInt(stringValue.substring(2), 16);
    }
    if (/^[0-9a-f]+$/i.test(stringValue) && /[a-f]/i.test(stringValue)) {
      return parseInt(stringValue, 16);
    }
    return Number(stringValue);
  }

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function loadSettings() {
    const mode = readLocalStorage("jme_rail_overlay_mode");
    if (mode === "all" || mode === "cull" || mode === "off") {
      state.railOverlayMode = mode;
    }
  }

  function saveSettings() {
    writeLocalStorage("jme_rail_overlay_mode", state.railOverlayMode);
  }

  function readLocalStorage(key) {
    try {
      return window.localStorage ? window.localStorage.getItem(String(key)) : null;
    } catch (ignored) {
      return null;
    }
  }

  function writeLocalStorage(key, value) {
    try {
      if (window.localStorage) {
        window.localStorage.setItem(String(key), String(value));
      }
    } catch (ignored) {
      // Ignore storage errors (private mode, etc).
    }
  }

  function initMenu() {
    if (!state.wrapper) {
      return;
    }

    const existing = state.wrapper.querySelector("#jme-map-menu-root");
    if (existing) {
      existing.remove();
    }

    const root = document.createElement("div");
    root.id = "jme-map-menu-root";

    const button = document.createElement("button");
    button.id = "jme-map-menu-button";
    button.type = "button";
    button.title = "MAGIC";
    button.setAttribute("aria-label", "MAGIC menu");
    button.innerHTML = `
      <svg class="jme-map-menu-logo" viewBox="0 0 24 24" role="img" aria-hidden="true">
        <defs>
          <linearGradient id="jmeMagicGradient" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#ff5a57"/>
            <stop offset="55%" stop-color="#e02f75"/>
            <stop offset="100%" stop-color="#6700a3"/>
          </linearGradient>
        </defs>
        <path d="M12 1.8l9.2 5.3v10.6L12 23l-9.2-5.3V7.1L12 1.8z" fill="url(#jmeMagicGradient)"/>
        <path d="M12 7.1l1.4 3.4c1 2.4 2.9 4.3 5.3 5.3l.8.3-.8.3c-2.4 1-4.3 2.9-5.3 5.3L12 25l-1.4-3.4c-1-2.4-2.9-4.3-5.3-5.3l-.8-.3.8-.3c2.4-1 4.3-2.9 5.3-5.3L12 7.1z" fill="rgba(255,255,255,0.92)"/>
      </svg>
    `.trim();

    const panel = document.createElement("div");
    panel.id = "jme-map-menu-panel";
    panel.setAttribute("role", "dialog");
    panel.setAttribute("aria-label", "MAGIC map options");
    panel.hidden = true;
    panel.innerHTML = `
      <div class="jme-menu-header">
        <div class="jme-menu-title">MAGIC</div>
        <button type="button" class="jme-menu-close material-icons" aria-label="Close">close</button>
      </div>
      <div class="jme-menu-section">
        <div class="jme-menu-section-title">Rails Overlay</div>
        <div class="jme-menu-row">
          <button type="button" class="jme-menu-chip" data-rail-mode="all"><span class="material-icons">polyline</span><span>All</span></button>
          <button type="button" class="jme-menu-chip" data-rail-mode="cull"><span class="material-icons">grain</span><span>Cull</span></button>
          <button type="button" class="jme-menu-chip" data-rail-mode="off"><span class="material-icons">visibility_off</span><span>Off</span></button>
        </div>
      </div>
      <div class="jme-menu-section">
        <div class="jme-menu-section-title">Export Rails</div>
        <div class="jme-menu-col">
          <button type="button" class="jme-menu-action" data-export="png_viewport"><span class="material-icons">image</span><span>PNG (viewport)</span></button>
          <button type="button" class="jme-menu-action" data-export="svg_viewport"><span class="material-icons">polyline</span><span>SVG (viewport)</span></button>
          <button type="button" class="jme-menu-action" data-export="svg_full"><span class="material-icons">map</span><span>SVG (all rails)</span></button>
        </div>
      </div>
    `.trim();

    root.appendChild(button);
    root.appendChild(panel);
    state.wrapper.appendChild(root);

    state.menuRoot = root;
    state.menuButton = button;
    state.menuPanel = panel;

    button.addEventListener("click", event => {
      event.stopPropagation();
      setMenuOpen(!state.menuOpen);
    });

    panel.querySelector(".jme-menu-close")?.addEventListener("click", event => {
      event.stopPropagation();
      setMenuOpen(false);
    });

    panel.querySelectorAll("[data-rail-mode]").forEach(chip => {
      chip.addEventListener("click", event => {
        event.stopPropagation();
        const mode = chip.getAttribute("data-rail-mode");
        if (mode === "all" || mode === "cull" || mode === "off") {
          state.railOverlayMode = mode;
          saveSettings();
          updateMenuState();
        }
      });
    });

    panel.querySelectorAll("[data-export]").forEach(action => {
      action.addEventListener("click", event => {
        event.stopPropagation();
        const type = action.getAttribute("data-export") || "";
        if (type === "png_viewport") {
          exportRailsPngViewport();
        } else if (type === "svg_viewport") {
          exportRailsSvgViewport();
        } else if (type === "svg_full") {
          exportRailsSvgFull();
        }
      });
    });

    if (!window.__jmeMapMenuOutsideClick) {
      window.__jmeMapMenuOutsideClick = true;
      document.addEventListener("click", event => {
        if (!state.menuOpen || !state.menuRoot) {
          return;
        }
        if (state.menuRoot.contains(event.target)) {
          return;
        }
        setMenuOpen(false);
      });
    }

    updateMenuState();
  }

  function setMenuOpen(open) {
    state.menuOpen = !!open;
    if (state.menuPanel) {
      state.menuPanel.hidden = !state.menuOpen;
    }
    if (state.menuRoot) {
      state.menuRoot.classList.toggle("open", state.menuOpen);
    }
  }

  function updateMenuState() {
    if (!state.menuPanel) {
      return;
    }
    state.menuPanel.querySelectorAll("[data-rail-mode]").forEach(chip => {
      const mode = chip.getAttribute("data-rail-mode");
      chip.classList.toggle("active", mode === state.railOverlayMode);
    });
  }

  function initTransformSampling() {
    if (!state.wrapper || state.wrapper.__jmeTransformSamplingInitialized) {
      return;
    }
    state.wrapper.__jmeTransformSamplingInitialized = true;

    state.wrapper.addEventListener("mousemove", event => {
      if (!state.wrapper) {
        return;
      }
      const rect = state.wrapper.getBoundingClientRect();
      state.lastMouseScreen = {
        x: event.clientX - rect.left,
        y: event.clientY - rect.top,
      };
    });

    const coordinateDisplay = state.wrapper.querySelector(".coordinate-display");
    if (!coordinateDisplay || typeof MutationObserver !== "function") {
      return;
    }

    const observer = new MutationObserver(() => {
      const world = readCoordinateDisplay();
      const screen = state.lastMouseScreen;
      if (!world || !screen) {
        return;
      }

      const now = Date.now();
      if (now - state.lastSampleAt < 40) {
        return;
      }
      state.lastSampleAt = now;

      state.transformSamples.push({
        worldX: world.x,
        worldZ: world.z,
        screenX: screen.x,
        screenY: screen.y,
        at: now,
      });

      const cutoff = now - 2500;
      state.transformSamples = state.transformSamples.filter(sample => sample && sample.at >= cutoff);
      if (state.transformSamples.length > 14) {
        state.transformSamples.splice(0, state.transformSamples.length - 14);
      }
    });

    observer.observe(coordinateDisplay, {
      subtree: true,
      characterData: true,
      childList: true,
    });
  }

  function readCoordinateDisplay() {
    if (!state.wrapper) {
      return null;
    }
    const element = state.wrapper.querySelector(".coordinate-display");
    if (!element) {
      return null;
    }
    const text = String(element.textContent || "");
    const matchX = text.match(/\\bX:\\s*(-?\\d+)/i);
    const matchZ = text.match(/\\bZ:\\s*(-?\\d+)/i);
    if (!matchX || !matchZ) {
      return null;
    }
    const x = Number(matchX[1]);
    const z = Number(matchZ[1]);
    if (!Number.isFinite(x) || !Number.isFinite(z)) {
      return null;
    }
    return { x, z };
  }

  function getTransformSamples() {
    const samples = Array.isArray(state.transformSamples) ? state.transformSamples : [];
    return samples.filter(sample => sample && Number.isFinite(sample.worldX) && Number.isFinite(sample.worldZ) && Number.isFinite(sample.screenX) && Number.isFinite(sample.screenY));
  }

  function solveTransform(matches) {
    const xAxis = solveLinear(matches, "worldX", "screenX");
    const yAxis = solveLinear(matches, "worldZ", "screenY");
    if (!xAxis || !yAxis) {
      return null;
    }
    return {
      scaleX: xAxis.scale,
      offsetX: xAxis.offset,
      scaleY: yAxis.scale,
      offsetY: yAxis.offset,
    };
  }

  function solveTransformFromSamples(samples) {
    return solveTransform(samples);
  }

  function scheduleSyntheticProbe(timestamp) {
    if (!state.wrapper || !Number.isFinite(timestamp)) {
      return;
    }
    if (timestamp - state.lastSyntheticProbeAt < 900) {
      return;
    }

    const canvas = state.wrapper.querySelector("canvas");
    if (!canvas) {
      return;
    }
    const rect = canvas.getBoundingClientRect();
    if (!rect || !Number.isFinite(rect.left) || !Number.isFinite(rect.width) || rect.width <= 10 || rect.height <= 10) {
      return;
    }

    // Probe a few points to seed coordinate samples even when station labels aren't visible.
    state.syntheticProbeQueue = [
      { clientX: rect.left + rect.width * 0.5, clientY: rect.top + rect.height * 0.5 },
      { clientX: rect.left + rect.width * 0.8, clientY: rect.top + rect.height * 0.5 },
      { clientX: rect.left + rect.width * 0.5, clientY: rect.top + rect.height * 0.8 },
    ];
    state.lastSyntheticProbeAt = timestamp;
  }

  function runSyntheticProbeStep() {
    if (!state.wrapper || !state.syntheticProbeQueue || !state.syntheticProbeQueue.length) {
      return;
    }
    const next = state.syntheticProbeQueue.shift();
    if (!next) {
      return;
    }
    try {
      const event = new MouseEvent("mousemove", {
        bubbles: true,
        cancelable: true,
        clientX: Number(next.clientX),
        clientY: Number(next.clientY),
      });
      state.wrapper.dispatchEvent(event);
    } catch (ignored) {
      state.syntheticProbeQueue.length = 0;
    }
  }

  function prepareRailsForRender(pxPerBlock, modeOverride) {
    const mode = modeOverride || state.railOverlayMode;
    if (!state.transform || !Array.isArray(state.rails) || !state.rails.length) {
      return [];
    }
    if (mode === "off") {
      return [];
    }

    const cellSize = Math.round(clamp(140 / Math.max(0.35, pxPerBlock || 1), 50, 140));
    const maxPerCell = clamp(Math.round(state.railCullMaxPerCell || 8), 1, 64);
    const countsByCell = new Map();
    const prepared = [];

    for (let i = 0; i < state.rails.length; i++) {
      const rail = state.rails[i];
      if (!rail) {
        continue;
      }

      const routeIds = getRouteIdsForRail(rail);
      if (routeIds.length && !routeIds.some(isRouteIdVisibleByMode)) {
        continue;
      }

      const points = getRailPoints(rail);
      if (points.length < 2) {
        continue;
      }

      if (!isProjectedRailPlausible(points)) {
        continue;
      }

      if (mode === "cull") {
        const mid = points[Math.floor(points.length / 2)];
        const screen = mid ? worldToScreen(Number(mid.x), Number(mid.z)) : null;
        if (!screen) {
          continue;
        }
        const cellX = Math.floor(screen.x / cellSize);
        const cellY = Math.floor(screen.y / cellSize);
        const key = `${cellX},${cellY}`;
        const count = countsByCell.get(key) || 0;
        if (count >= maxPerCell) {
          continue;
        }
        countsByCell.set(key, count + 1);
      }

      prepared.push({
        rail,
        points,
        speedKmh: getRailSpeedLimitKmh(rail),
      });
    }

    return prepared;
  }

  function exportRailsPngViewport() {
    if (!state.wrapper || !state.transform) {
      return;
    }

    const width = state.wrapper.clientWidth;
    const height = state.wrapper.clientHeight;
    if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
      return;
    }

    const dpr = window.devicePixelRatio || 1;
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.floor(width * dpr));
    canvas.height = Math.max(1, Math.floor(height * dpr));

    const ctx = canvas.getContext("2d");
    if (!ctx) {
      return;
    }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    const pxPerBlock = (Math.abs(state.transform.scaleX) + Math.abs(state.transform.scaleY)) / 2;
    const preparedRails = prepareRailsForRender(pxPerBlock, "all");
    drawRails(ctx, pxPerBlock, preparedRails);
    drawSignals(ctx, pxPerBlock, preparedRails);

    downloadDataUrl(`magic-rails-${state.dimension || "0"}-viewport.png`, canvas.toDataURL("image/png"));
  }

  function exportRailsSvgViewport() {
    if (!state.wrapper || !state.transform) {
      return;
    }

    const width = state.wrapper.clientWidth;
    const height = state.wrapper.clientHeight;
    if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
      return;
    }

    const pxPerBlock = (Math.abs(state.transform.scaleX) + Math.abs(state.transform.scaleY)) / 2;
    const preparedRails = prepareRailsForRender(pxPerBlock, "all");
    const svg = buildRailsSvgViewport(preparedRails, width, height, pxPerBlock);
    downloadBlob(`magic-rails-${state.dimension || "0"}-viewport.svg`, new Blob([svg], { type: "image/svg+xml;charset=utf-8" }));
  }

  function exportRailsSvgFull() {
    if (!Array.isArray(state.rails) || !state.rails.length) {
      return;
    }

    const svg = buildRailsSvgFull(state.rails);
    downloadBlob(`magic-rails-${state.dimension || "0"}-full.svg`, new Blob([svg], { type: "image/svg+xml;charset=utf-8" }));
  }

  function buildRailsSvgViewport(preparedRails, width, height, pxPerBlock) {
    const lineWidth = clamp((pxPerBlock || 1) * 0.22, 1.2, 5.6);

    let svg = "";
    svg += `<svg xmlns="http://www.w3.org/2000/svg" width="${escapeAttr(width)}" height="${escapeAttr(height)}" viewBox="0 0 ${escapeAttr(width)} ${escapeAttr(height)}">`;
    svg += `<g fill="none" stroke-linecap="round" stroke-linejoin="round">`;

    preparedRails.forEach(entry => {
      const points = entry.points || [];
      if (points.length < 2) {
        return;
      }

      const segments = [];
      for (let i = 0; i < points.length; i++) {
        const screen = worldToScreen(Number(points[i].x), Number(points[i].z));
        if (screen) {
          segments.push(screen);
        }
      }
      if (segments.length < 2) {
        return;
      }

      let d = "";
      for (let i = 0; i < segments.length; i++) {
        const x = roundSvgNumber(segments[i].x);
        const y = roundSvgNumber(segments[i].y);
        d += (i === 0 ? `M${x} ${y}` : ` L${x} ${y}`);
      }

      const color = railSpeedColor(entry.speedKmh);
      svg += `<path d="${d}" stroke="${escapeAttr(color)}" stroke-opacity="0.95" stroke-width="${escapeAttr(roundSvgNumber(lineWidth))}"/>`;
    });

    svg += `</g></svg>`;
    return svg;
  }

  function buildRailsSvgFull(rails) {
    let minX = Number.POSITIVE_INFINITY;
    let minZ = Number.POSITIVE_INFINITY;
    let maxX = Number.NEGATIVE_INFINITY;
    let maxZ = Number.NEGATIVE_INFINITY;

    const railPoints = [];
    rails.forEach(rail => {
      const points = getRailPoints(rail);
      if (points.length < 2) {
        return;
      }
      railPoints.push({ rail, points });
      points.forEach(point => {
        const x = Number(point.x);
        const z = Number(point.z);
        if (!Number.isFinite(x) || !Number.isFinite(z)) {
          return;
        }
        minX = Math.min(minX, x);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxZ = Math.max(maxZ, z);
      });
    });

    if (!Number.isFinite(minX) || !Number.isFinite(minZ) || !Number.isFinite(maxX) || !Number.isFinite(maxZ)) {
      return `<svg xmlns="http://www.w3.org/2000/svg"></svg>`;
    }

    const margin = 2;
    minX -= margin;
    minZ -= margin;
    maxX += margin;
    maxZ += margin;

    const width = Math.max(1, maxX - minX);
    const height = Math.max(1, maxZ - minZ);
    const strokeWidth = 0.35;

    let svg = "";
    svg += `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${escapeAttr(roundSvgNumber(minX))} ${escapeAttr(roundSvgNumber(minZ))} ${escapeAttr(roundSvgNumber(width))} ${escapeAttr(roundSvgNumber(height))}">`;
    svg += `<g fill="none" stroke-linecap="round" stroke-linejoin="round">`;

    railPoints.forEach(entry => {
      const points = entry.points || [];
      if (points.length < 2) {
        return;
      }

      let d = "";
      for (let i = 0; i < points.length; i++) {
        const x = roundSvgNumber(points[i].x);
        const z = roundSvgNumber(points[i].z);
        d += (i === 0 ? `M${x} ${z}` : ` L${x} ${z}`);
      }

      const color = railSpeedColor(getRailSpeedLimitKmh(entry.rail));
      svg += `<path d="${d}" stroke="${escapeAttr(color)}" stroke-opacity="0.95" stroke-width="${escapeAttr(strokeWidth)}"/>`;
    });

    svg += `</g></svg>`;
    return svg;
  }

  function downloadDataUrl(filename, dataUrl) {
    if (!dataUrl) {
      return;
    }
    try {
      const link = document.createElement("a");
      link.href = dataUrl;
      link.download = filename || "export";
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (ignored) {
      // Ignore download failures.
    }
  }

  function downloadBlob(filename, blob) {
    try {
      const url = URL.createObjectURL(blob);
      downloadDataUrl(filename, url);
      setTimeout(() => URL.revokeObjectURL(url), 1500);
    } catch (ignored) {
      // Ignore download failures.
    }
  }

  function roundSvgNumber(value) {
    const num = Number(value);
    if (!Number.isFinite(num)) {
      return 0;
    }
    return Math.round(num * 100) / 100;
  }

  function escapeAttr(value) {
    return String(value || "").replace(/&/g, "&amp;").replace(/\"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function patchRequestTracking() {
    if (window.__jmeSystemMapRequestPatched) {
      return;
    }

    window.__jmeSystemMapRequestPatched = true;

    if (typeof window.fetch === "function") {
      const originalFetch = window.fetch;
      window.fetch = function (input, init) {
        trackDimensionFromUrl(input);
        return originalFetch.call(this, input, init);
      };
    }

    if (typeof window.XMLHttpRequest === "function") {
      const originalOpen = XMLHttpRequest.prototype.open;
      XMLHttpRequest.prototype.open = function (method, url) {
        trackDimensionFromUrl(url);
        return originalOpen.apply(this, arguments);
      };
    }
  }

  function trackDimensionFromUrl(urlLike) {
    try {
      let rawUrl;
      if (typeof urlLike === "string") {
        rawUrl = urlLike;
      } else if (urlLike && typeof urlLike.url === "string") {
        rawUrl = urlLike.url;
      } else {
        rawUrl = String(urlLike);
      }

      const url = new URL(rawUrl, window.location.href);
      if (!url.pathname.includes("/mtr/api/map/")) {
        return;
      }

      const dimension = url.searchParams.get("dimension");
      if (dimension !== null && dimension !== "") {
        const previous = state.dimension;
        state.dimension = dimension;
        if (state.ready && previous !== dimension) {
          refreshAll();
        }
      }
    } catch (ignored) {
      // Ignore malformed URLs.
    }
  }
})();
