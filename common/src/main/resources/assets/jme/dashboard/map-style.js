/* MAGIC map styles: applies map-style.yaml (served as /api/map-style) to rails, for the dashboard and the Livemap.
   Rail properties come from map.yaml (/api/railways), the rails themselves (/api/rails) and the route paths
   MTR generated (/api/rail-routes). */
(function(global){
'use strict';

var ICONS = {
  tracks: '<path d="M8 3 6 21M16 3l2 18M7 7h10M6.5 12h11M6 17h12"/>',
  speed: '<path d="M4 18a8 8 0 1 1 16 0"/><path d="m12 18 4-6"/>',
  bolt: '<path d="M13 2 5 14h6l-1 8 8-12h-6z"/>',
  gauge: '<path d="M4 6v12M20 6v12M4 12h16M8 9l-4 3 4 3M16 9l4 3-4 3"/>',
  signal: '<rect x="8" y="2" width="8" height="14" rx="4"/><circle cx="12" cy="6" r="1.4"/><circle cx="12" cy="11" r="1.4"/><path d="M12 16v6"/>',
  building: '<path d="M4 21V8l8-5 8 5v13M9 21v-6h6v6"/>',
  pin: '<path d="M12 21s-7-6.5-7-12a7 7 0 0 1 14 0c0 5.5-7 12-7 12z"/><circle cx="12" cy="9" r="2.5"/>',
  star: '<path d="m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z"/>'
};

function icon(name, fallbackText){
  var body = ICONS[name];
  if (!body) {
    var letter = String(fallbackText || '?').trim().charAt(0).toUpperCase() || '?';
    return '<svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><text x="12" y="17" text-anchor="middle" font-size="14" font-weight="700" fill="currentColor">' + letter + '</text></svg>';
  }
  return '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + body + '</svg>';
}

function fetchJson(url){ return fetch(url).then(function(r){ return r.json(); }); }

/* ── map.yaml matching (track IDs, then routes, then area) ── */
function matchDefinitions(rails, defs, railRoutes){
  var result = {};
  if (!defs || !defs.length) return result;
  rails.forEach(function(rail){
    var routesOnRail = null;
    for (var i = 0; i < defs.length; i++) {
      var def = defs[i], match = def.match || {}, matched = false, j;
      if (match.trackIds && match.trackIds.length) {
        for (j = 0; j < match.trackIds.length; j++) if (String(match.trackIds[j]) === rail.id) { matched = true; break; }
      }
      if (!matched && match.routeIds && match.routeIds.length) {
        if (!routesOnRail) {
          routesOnRail = {};
          (rail.connectedNodes || []).forEach(function(n){ (n.routes || []).forEach(function(r){ routesOnRail[String(r).toLowerCase()] = true; }); });
          ((railRoutes && railRoutes[rail.id]) || []).forEach(function(r){ routesOnRail[String(r).toLowerCase()] = true; });
        }
        for (j = 0; j < match.routeIds.length; j++) if (routesOnRail[String(match.routeIds[j]).toLowerCase()]) { matched = true; break; }
      }
      if (!matched && match.area) {
        var a = match.area, pts = rail.curvePoints || [];
        for (j = 0; j < pts.length; j++) {
          if (pts[j].x >= a.x1 && pts[j].x <= a.x2 && pts[j].z >= a.z1 && pts[j].z <= a.z2) { matched = true; break; }
        }
      }
      if (matched) { result[rail.id] = def; break; }
    }
  });
  return result;
}

/* Every property a style can use for one rail. */
function railProps(rail, def, railRoutes){
  var props = {};
  var mtrSpeed = Math.max(rail.speedLimit1 || 0, rail.speedLimit2 || 0);
  var onRoute = !!(railRoutes && railRoutes[rail.id] && railRoutes[rail.id].length);
  if (!onRoute) (rail.connectedNodes || []).forEach(function(n){ if (n.routes && n.routes.length) onRoute = true; });
  if (def && def.properties) Object.keys(def.properties).forEach(function(k){ props[k] = def.properties[k]; });
  props.defined = !!def;
  props.mtrSpeed = mtrSpeed;
  props.onRoute = onRoute;
  if (!(Number(props.maxspeed) > 0)) props.maxspeed = mtrSpeed;
  if (def) {
    /* map.yaml defaults (docs/wiki/MAGIC-RAILWAYS.md): electrified without a voltage is 3 kV DC, and an
       AC voltage without a frequency is 50 Hz. */
    var e = String(props.electrified == null ? 'none' : props.electrified).toLowerCase();
    var notElectric = ['none', 'no', 'false', 'deelectrified', 'abandoned', 'construction', 'proposed'].indexOf(e) >= 0;
    if (!notElectric && !(Number(props.voltage) > 0)) { props.voltage = 3000; props.frequency = 0; }
    if (!notElectric && Number(props.voltage) >= 4000 && !(Number(props.frequency) > 0)) props.frequency = 50;
  }
  props.id = rail.id;
  return props;
}

/* ── rule evaluation ── */
function matchesValue(cond, value){
  if (Array.isArray(cond)) { for (var i = 0; i < cond.length; i++) if (matchesValue(cond[i], value)) return true; return false; }
  if (cond !== null && typeof cond === 'object') {
    var n = Number(value);
    if (value === null || value === undefined || value === '' || !isFinite(n)) return false;
    if (cond.min != null && n < Number(cond.min)) return false;
    if (cond.max != null && n > Number(cond.max)) return false;
    return true;
  }
  if (value === undefined || value === null) return cond === null;
  if (typeof cond === 'number') return Math.abs(Number(value) - cond) < 1e-9;
  if (typeof cond === 'boolean') return value === cond || String(value).toLowerCase() === String(cond);
  return String(value).toLowerCase() === String(cond).toLowerCase();
}

function ruleMatches(rule, props){
  var when = rule.when || {};
  for (var k in when) if (Object.prototype.hasOwnProperty.call(when, k) && !matchesValue(when[k], props[k])) return false;
  return true;
}

function hexToRgb(h){
  h = String(h || '#000').replace('#', '');
  if (h.length === 3) h = h.split('').map(function(c){ return c + c; }).join('');
  var n = parseInt(h, 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}
function rgbToHex(c){ return '#' + c.map(function(v){ return Math.round(v).toString(16).padStart(2, '0'); }).join(''); }

function gradientColor(gradient, value){
  var stops = (gradient.stops || []).slice().sort(function(a, b){ return a[0] - b[0]; });
  if (!stops.length) return null;
  if (value <= stops[0][0]) return stops[0][1];
  for (var i = 1; i < stops.length; i++) {
    if (value <= stops[i][0]) {
      var a = stops[i - 1], b = stops[i], t = (value - a[0]) / (b[0] - a[0]);
      var ca = hexToRgb(a[1]), cb = hexToRgb(b[1]);
      return rgbToHex([0, 1, 2].map(function(j){ return ca[j] + (cb[j] - ca[j]) * t; }));
    }
  }
  return stops[stops.length - 1][1];
}

/* Index (in value order) of the gradient stop closest to a value; legend rows of a gradient use it as their key. */
function nearestStop(gradient, value){
  var stops = (gradient.stops || []).slice().sort(function(a, b){ return a[0] - b[0]; });
  var best = 0;
  for (var i = 1; i < stops.length; i++) if (Math.abs(stops[i][0] - value) < Math.abs(stops[best][0] - value)) best = i;
  return best;
}

function fillText(template, props){
  if (template == null) return '';
  return String(template).replace(/\{([A-Za-z0-9_]+)\}/g, function(_, k){ var v = props[k]; return v == null ? '' : String(v); }).trim();
}

/* The look of one rail in one style. */
function evaluate(style, props){
  var rules = style.rules || [];
  for (var i = 0; i < rules.length; i++) {
    var rule = rules[i];
    if (!ruleMatches(rule, props)) continue;
    return {
      key: 'r' + i,
      hidden: !!rule.hidden,
      color: rule.color || '#808080',
      width: Number(rule.width) > 0 ? Number(rule.width) : 1,
      dash: Array.isArray(rule.dash) ? rule.dash : null,
      text: fillText(rule.text !== undefined ? rule.text : style.text, props)
    };
  }
  var g = style.gradient;
  if (g && g.property && isFinite(Number(props[g.property])) && props[g.property] !== '' && props[g.property] != null) {
    var color = gradientColor(g, Number(props[g.property]));
    if (color) return { key: 'g' + nearestStop(g, Number(props[g.property])), hidden: false, color: color, width: Number(g.width) > 0 ? Number(g.width) : 1, dash: null, text: fillText(style.text, props) };
  }
  var d = style.default || {};
  return {
    key: 'd',
    hidden: !!d.hidden,
    color: d.color || '#808080',
    width: Number(d.width) > 0 ? Number(d.width) : 1,
    dash: Array.isArray(d.dash) ? d.dash : null,
    text: fillText(d.text !== undefined ? d.text : style.text, props)
  };
}

/* Legend entries: labelled rules, the gradient's stops, then the default. */
function legend(style){
  var items = [];
  (style.rules || []).forEach(function(r, i){
    if (r.label && !r.hidden) items.push({ key: 'r' + i, color: r.color || '#808080', width: Number(r.width) > 0 ? Number(r.width) : 1, dash: r.dash || null, label: String(r.label) });
  });
  var g = style.gradient;
  if (g && g.stops) {
    g.stops.slice().sort(function(a, b){ return a[0] - b[0]; }).forEach(function(s, i){
      items.push({ key: 'g' + i, color: s[1], width: 1, dash: null, label: String(g.label || '{value}').replace('{value}', s[0]) });
    });
  }
  if (style.default && style.default.label && !style.default.hidden) {
    items.push({ key: 'd', color: style.default.color || '#808080', width: Number(style.default.width) || 1, dash: style.default.dash || null, label: String(style.default.label) });
  }
  return items;
}

function legendSwatch(item){
  var w = Math.max(2, Math.min(8, 3.5 * item.width));
  var dash = item.dash ? ' stroke-dasharray="' + item.dash.map(function(v){ return v * w; }).join(' ') + '"' : '';
  return '<svg width="40" height="14" viewBox="0 0 40 14" aria-hidden="true"><line x1="2" y1="7" x2="38" y2="7" stroke="' + item.color + '" stroke-width="' + w + '" stroke-linecap="round"' + dash + '/></svg>';
}

/* ── MapLibre rendering ── */
function dashKey(dash){ return dash ? dash.join(',') : ''; }

function railFeatures(rails, defsByRail, railRoutes, style, toLngLat, filterFn){
  var features = [];
  rails.forEach(function(rail){
    var pts = rail.curvePoints;
    if (!pts || pts.length < 2) return;
    var props = railProps(rail, defsByRail[rail.id], railRoutes);
    if (filterFn && !filterFn(rail, props)) return;
    var look = evaluate(style, props);
    if (look.hidden) return;
    features.push({
      type: 'Feature',
      properties: { id: rail.id, color: look.color, w: look.width, dash: dashKey(look.dash), text: look.text, name: props.defined && defsByRail[rail.id] ? (defsByRail[rail.id].name || '') : '' },
      geometry: { type: 'LineString', coordinates: pts.map(function(p){ return toLngLat(p.x, p.z); }) }
    });
  });
  return { type: 'FeatureCollection', features: features };
}

function dashPatterns(style){
  var seen = {}, list = [];
  (style.rules || []).concat(style.default ? [style.default] : []).forEach(function(r){
    if (Array.isArray(r.dash)) { var k = dashKey(r.dash); if (!seen[k]) { seen[k] = true; list.push(r.dash); } }
  });
  return list;
}

/* Line width by zoom, scaled by the rule's width; "extra" widens it for the casing. MapLibre only allows a zoom
   interpolation at the top of an expression, so the extra goes inside each stop. */
function widthExpr(extra){
  function at(base){ return ['+', extra, ['*', base, ['get', 'w']]]; }
  return ['interpolate', ['linear'], ['zoom'], 2, at(1.6), 5, at(3), 9, at(6)];
}
var WIDTH = widthExpr(0);

/* Adds the rail layers for a style (removing those of the previous one) and returns their IDs. */
function addRailLayers(map, sourceId, style, opts){
  opts = opts || {};
  var prefix = opts.prefix || 'ms-';
  removeRailLayers(map, prefix);
  var ids = [];
  var before = opts.before && map.getLayer(opts.before) ? opts.before : undefined;
  function add(layer){ map.addLayer(layer, before); ids.push(layer.id); }
  add({ id: prefix + 'casing', type: 'line', source: sourceId,
    layout: { 'line-join': 'round', 'line-cap': 'round' },
    paint: { 'line-color': opts.casing || '#ffffff', 'line-opacity': opts.casingOpacity == null ? 0.7 : opts.casingOpacity,
      'line-width': widthExpr(2) } });
  add({ id: prefix + 'line', type: 'line', source: sourceId, filter: ['==', ['get', 'dash'], ''],
    layout: { 'line-join': 'round', 'line-cap': 'round' },
    paint: { 'line-color': ['get', 'color'], 'line-width': WIDTH } });
  dashPatterns(style).forEach(function(d, i){
    add({ id: prefix + 'dash' + i, type: 'line', source: sourceId, filter: ['==', ['get', 'dash'], dashKey(d)],
      layout: { 'line-join': 'round' },
      paint: { 'line-color': ['get', 'color'], 'line-width': WIDTH, 'line-dasharray': d } });
  });
  add({ id: prefix + 'text', type: 'symbol', source: sourceId, minzoom: 5.5, filter: ['!=', ['get', 'text'], ''],
    layout: { 'symbol-placement': 'line', 'symbol-spacing': 260, 'text-field': ['get', 'text'], 'text-font': ['Open Sans Semibold'],
      'text-size': 11, 'text-keep-upright': true, 'text-max-angle': 30 },
    paint: { 'text-color': opts.textColor || '#585858', 'text-halo-color': opts.textHalo || '#ffffff', 'text-halo-width': 1.6 } });
  map.__magicRailLayers = (map.__magicRailLayers || {});
  map.__magicRailLayers[prefix] = ids;
  return ids;
}

function removeRailLayers(map, prefix){
  var ids = (map.__magicRailLayers && map.__magicRailLayers[prefix]) || [];
  ids.forEach(function(id){ if (map.getLayer(id)) map.removeLayer(id); });
  if (map.__magicRailLayers) map.__magicRailLayers[prefix] = [];
}

function setRailLayersVisible(map, prefix, visible){
  ((map.__magicRailLayers && map.__magicRailLayers[prefix]) || []).forEach(function(id){
    if (map.getLayer(id)) map.setLayoutProperty(id, 'visibility', visible ? 'visible' : 'none');
  });
}

/* theme: "light", "dark" or "system" */
function prefersDark(theme){
  if (theme === 'dark') return true;
  if (theme === 'light') return false;
  return !!(global.matchMedia && global.matchMedia('(prefers-color-scheme: dark)').matches);
}

function findStyle(cfg, id){
  var list = (cfg && cfg.styles) || [];
  for (var i = 0; i < list.length; i++) if (list[i] && list[i].id === id) return list[i];
  return null;
}

global.MagicMapStyle = {
  icon: icon,
  fetchJson: fetchJson,
  matchDefinitions: matchDefinitions,
  railProps: railProps,
  evaluate: evaluate,
  legend: legend,
  legendSwatch: legendSwatch,
  railFeatures: railFeatures,
  addRailLayers: addRailLayers,
  removeRailLayers: removeRailLayers,
  setRailLayersVisible: setRailLayersVisible,
  prefersDark: prefersDark,
  findStyle: findStyle
};
})(window);
