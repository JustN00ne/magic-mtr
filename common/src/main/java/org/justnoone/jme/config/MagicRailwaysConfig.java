package org.justnoone.jme.config;

import org.mtr.core.Main;
import org.justnoone.jme.Jme;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class MagicRailwaysConfig {

    private static final Path CONFIG_PATH = MagicConfigPaths.resolveMapConfigFile("map.yaml", "magic-railways.yaml");
    private static volatile List<RailwayDefinition> railways = Collections.emptyList();
    private static volatile long lastModified;

    private MagicRailwaysConfig() {
    }

    public static void load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                createDefaultConfig();
            }
            long modified = Files.exists(CONFIG_PATH) ? Files.getLastModifiedTime(CONFIG_PATH).toMillis() : 0;
            if (modified == lastModified) return;
            lastModified = modified;

            final Yaml yaml = new Yaml();
            final String content = new String(Files.readAllBytes(CONFIG_PATH), StandardCharsets.UTF_8);
            final Map<String, Object> root = yaml.load(content);
            final List<RailwayDefinition> list = new ArrayList<>();

            if (root != null && root.containsKey("railways")) {
                Object railwaysObj = root.get("railways");
                if (railwaysObj instanceof List) {
                    for (Object item : (List<?>) railwaysObj) {
                        if (item instanceof Map) {
                            list.add(parseDefinition((Map<?, ?>) item));
                        }
                    }
                }
            }

            railways = Collections.unmodifiableList(list);
            Main.LOGGER.info("[{}] Loaded {} railway definitions from {}", Jme.MOD_ID, list.size(), CONFIG_PATH);
        } catch (Exception e) {
            Main.LOGGER.warn("[{}] Failed to load map.yaml: {} ({})", Jme.MOD_ID, e.getMessage(), CONFIG_PATH, e);
            railways = Collections.emptyList();
        }
    }

    public static List<RailwayDefinition> getRailways() {
        load();
        return railways;
    }

    /** Returns the raw YAML content for the GET /api/railways endpoint. */
    public static String toYamlString() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                return new String(Files.readAllBytes(CONFIG_PATH), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return "railways: []\n";
    }

    /** Returns railways as a JSON-like map for the JS client. */
    public static Map<String, Object> toJsonMap() {
        final Map<String, Object> root = new LinkedHashMap<>();
        final List<Map<String, Object>> arr = new ArrayList<>();
        for (RailwayDefinition r : railways) {
            arr.add(r.toMap());
        }
        root.put("railways", arr);
        return root;
    }

    public static void saveYaml(String yamlContent) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, yamlContent.getBytes(StandardCharsets.UTF_8));
            lastModified = 0;
            Main.LOGGER.info("[{}] Saved map.yaml to {}", Jme.MOD_ID, CONFIG_PATH);
        } catch (Exception e) {
            Main.LOGGER.warn("[{}] Failed to save map.yaml: {} ({})", Jme.MOD_ID, e.getMessage(), CONFIG_PATH);
        }
    }

    private static void createDefaultConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, DEFAULT_CONFIG.getBytes(StandardCharsets.UTF_8));
            Main.LOGGER.info("[{}] Created default map.yaml at {}", Jme.MOD_ID, CONFIG_PATH);
        } catch (Exception e) {
            Main.LOGGER.warn("[{}] Failed to create default config: {}", Jme.MOD_ID, e.getMessage());
        }
    }

    private static RailwayDefinition parseDefinition(Map<?, ?> map) {
        String id = getStr(map, "id", "unknown");
        String name = getStr(map, "name", id);
        RailwayProperties props = new RailwayProperties();
        RailwayMatch match = new RailwayMatch();

        Object propsObj = map.get("properties");
        if (propsObj instanceof Map) props = parseProperties((Map<?, ?>) propsObj);
        Object matchObj = map.get("match");
        if (matchObj instanceof Map) match = parseMatch((Map<?, ?>) matchObj);

        return new RailwayDefinition(id, name, props, match);
    }

    private static final Set<String> KNOWN_PROPERTIES = new HashSet<>(Arrays.asList(
            "railway", "electrified", "voltage", "frequency", "gauge", "maxspeed", "operator", "operatorShort", "ref", "usage",
            "trackClass", "trackRef", "passengerLines", "trafficMode", "shp", "workrules", "preferredDirection"));

    private static RailwayProperties parseProperties(Map<?, ?> map) {
        RailwayProperties p = new RailwayProperties();
        p.railway = getStr(map, "railway", "rail");
        p.electrified = getStr(map, "electrified", "none");
        p.voltage = getInt(map, "voltage", 0);
        p.frequency = getDbl(map, "frequency", 0);
        p.gauge = getInt(map, "gauge", 1435);
        p.maxspeed = getInt(map, "maxspeed", 0);
        p.operator = getStr(map, "operator", "");
        p.operatorShort = getStr(map, "operatorShort", "");
        p.ref = getStr(map, "ref", "");
        p.usage = getStr(map, "usage", "main");
        p.trackClass = getStr(map, "trackClass", "");
        p.trackRef = getStr(map, "trackRef", "");
        p.passengerLines = getInt(map, "passengerLines", 0);
        p.trafficMode = getStr(map, "trafficMode", "mixed");
        p.shp = getBool(map, "shp", false);
        p.workrules = getStr(map, "workrules", "");
        p.preferredDirection = getStr(map, "preferredDirection", "");
        // Keep properties MAGIC doesn't know about, so custom map styles (map-style.yaml) can use them.
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            final String key = String.valueOf(entry.getKey());
            final Object value = entry.getValue();
            if (!KNOWN_PROPERTIES.contains(key) && (value instanceof String || value instanceof Number || value instanceof Boolean)) {
                p.extra.put(key, value);
            }
        }
        return p;
    }

    private static RailwayMatch parseMatch(Map<?, ?> map) {
        RailwayMatch m = new RailwayMatch();
        Object trackIdsObj = map.get("trackIds");
        if (trackIdsObj instanceof List) {
            for (Object item : (List<?>) trackIdsObj) {
                m.trackIds.add(String.valueOf(item));
            }
        }
        Object routeIdsObj = map.get("routeIds");
        if (routeIdsObj instanceof List) {
            for (Object item : (List<?>) routeIdsObj) {
                m.routeIds.add(String.valueOf(item));
            }
        }
        Object areaObj = map.get("area");
        if (areaObj instanceof Map) {
            Map<?, ?> a = (Map<?, ?>) areaObj;
            m.area = new double[]{
                    getDbl(a, "x1", -1e9), getDbl(a, "z1", -1e9),
                    getDbl(a, "x2", 1e9), getDbl(a, "z2", 1e9)
            };
        }
        return m;
    }

    // ── Data classes ──

    public static final class RailwayDefinition {
        public final String id;
        public final String name;
        public final RailwayProperties properties;
        public final RailwayMatch match;

        public RailwayDefinition(String id, String name, RailwayProperties properties, RailwayMatch match) {
            this.id = id;
            this.name = name;
            this.properties = properties;
            this.match = match;
        }

        public Map<String, Object> toMap() {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("properties", properties.toMap());
            m.put("match", match.toMap());
            return m;
        }
    }

    public static final class RailwayProperties {
        public String railway = "rail";
        public String electrified = "none";
        public int voltage = 0;
        public double frequency = 0;
        public int gauge = 1435;
        public int maxspeed = 0;
        public String operator = "";
        public String operatorShort = "";
        public String ref = "";
        public String usage = "main";
        public String trackClass = "";
        public String trackRef = "";
        public int passengerLines = 0;
        public String trafficMode = "mixed";
        public boolean shp = false;
        public String workrules = "";
        public String preferredDirection = "";
        public final Map<String, Object> extra = new LinkedHashMap<>();

        public Map<String, Object> toMap() {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("railway", railway);
            m.put("electrified", electrified);
            m.put("voltage", voltage);
            m.put("frequency", frequency);
            m.put("gauge", gauge);
            m.put("maxspeed", maxspeed);
            if (!operator.isEmpty()) m.put("operator", operator);
            if (!operatorShort.isEmpty()) m.put("operatorShort", operatorShort);
            if (!ref.isEmpty()) m.put("ref", ref);
            m.put("usage", usage);
            if (!trackClass.isEmpty()) m.put("trackClass", trackClass);
            if (!trackRef.isEmpty()) m.put("trackRef", trackRef);
            if (passengerLines > 0) m.put("passengerLines", passengerLines);
            m.put("trafficMode", trafficMode);
            if (shp) m.put("shp", shp);
            if (!workrules.isEmpty()) m.put("workrules", workrules);
            if (!preferredDirection.isEmpty()) m.put("preferredDirection", preferredDirection);
            for (final Map.Entry<String, Object> entry : extra.entrySet()) {
                m.putIfAbsent(entry.getKey(), entry.getValue());
            }
            return m;
        }
    }

    public static final class RailwayMatch {
        public final List<String> trackIds = new ArrayList<>();
        public final List<String> routeIds = new ArrayList<>();
        public double[] area;

        public Map<String, Object> toMap() {
            final Map<String, Object> m = new LinkedHashMap<>();
            if (!trackIds.isEmpty()) m.put("trackIds", new ArrayList<>(trackIds));
            if (!routeIds.isEmpty()) m.put("routeIds", new ArrayList<>(routeIds));
            if (area != null) {
                final Map<String, Object> a = new LinkedHashMap<>();
                a.put("x1", area[0]); a.put("z1", area[1]);
                a.put("x2", area[2]); a.put("z2", area[3]);
                m.put("area", a);
            }
            return m;
        }
    }

    // ── Utility ──

    @SuppressWarnings("unchecked")
    private static String getStr(Map<?, ?> o, String key, String def) {
        Object v = o.get(key);
        return v != null ? String.valueOf(v) : def;
    }

    private static int getInt(Map<?, ?> o, String key, int def) {
        Object v = o.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) { try { return Integer.parseInt((String) v); } catch (Exception ignored) {} }
        return def;
    }

    private static double getDbl(Map<?, ?> o, String key, double def) {
        Object v = o.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) { try { return Double.parseDouble((String) v); } catch (Exception ignored) {} }
        return def;
    }

    private static boolean getBool(Map<?, ?> o, String key, boolean def) {
        Object v = o.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) return "true".equalsIgnoreCase((String) v);
        return def;
    }

    private static final String DEFAULT_CONFIG =
            "# MAGIC Railways Configuration (OpenRailwayMap style)\n" +
            "# Each entry defines a railway line with ORM properties.\n" +
            "#\n" +
            "# Matching priority: trackIds (rail hex IDs) > routeIds > area\n" +
            "# Track hex IDs are unique per rail segment (e.g. \"00000001-0000000a-...\").\n" +
            "# You can find them in the /api/rails response or the dashboard hover tooltip.\n" +
            "#\n" +
            "# Available usage types: main, hsr, branch, industrial, spur\n" +
            "# Available electrified types: none, contact_line, contact_side, rail, proposed, construction\n" +
            "# Gauge in mm: 3000, 1600, 1520, 1435, 1432, 1050, 800, 600, 63\n" +
            "#\n" +
            "# See docs/wiki/MAGIC-RAILWAYS.md for full reference.\n\n" +
            "railways:\n" +
            "  # ── High-Speed Rail (red, thick line) ──\n" +
            "  - id: high-speed-main\n" +
            "    name: High-Speed Main Line\n" +
            "    properties:\n" +
            "      railway: rail\n" +
            "      usage: hsr\n" +
            "      electrified: contact_line\n" +
            "      voltage: 25000\n" +
            "      frequency: 50\n" +
            "      gauge: 1435\n" +
            "      maxspeed: 300\n" +
            "      operator: Rapid Rail\n" +
            "      operatorShort: RR\n" +
            "      ref: \"HS1\"\n" +
            "      trafficMode: passenger\n" +
            "    match:\n" +
            "      trackIds:\n" +
            "        - \"00000001-0000000a-00000005-00000002-0000000b-00000006\"\n" +
            "        - \"00000001-0000000a-00000005-00000003-0000000c-00000007\"\n\n" +
            "  # ── Metro / Underground (blue, DC third rail) ──\n" +
            "  - id: metro-line-1\n" +
            "    name: Metro Line 1\n" +
            "    properties:\n" +
            "      railway: rail\n" +
            "      usage: main\n" +
            "      electrified: rail\n" +
            "      voltage: 750\n" +
            "      frequency: 0\n" +
            "      gauge: 1435\n" +
            "      maxspeed: 80\n" +
            "      operator: City Metro\n" +
            "      operatorShort: CM\n" +
            "      ref: \"M1\"\n" +
            "      trafficMode: passenger\n" +
            "    match:\n" +
            "      trackIds:\n" +
            "        - \"00000003-0000000a-00000005-00000004-0000000d-00000008\"\n" +
            "        - \"00000003-0000000a-00000005-00000005-0000000e-00000009\"\n\n" +
            "  # ── Commuter / S-Bahn (orange, 15kV AC) ──\n" +
            "  - id: commuter-ring\n" +
            "    name: S-Bahn Ring\n" +
            "    properties:\n" +
            "      railway: rail\n" +
            "      usage: main\n" +
            "      electrified: contact_line\n" +
            "      voltage: 15000\n" +
            "      frequency: 16.7\n" +
            "      gauge: 1435\n" +
            "      maxspeed: 120\n" +
            "      operator: Regionalbahn\n" +
            "      operatorShort: RB\n" +
            "      ref: \"S41\"\n" +
            "      trafficMode: passenger\n" +
            "    match:\n" +
            "      trackIds:\n" +
            "        - \"00000005-0000000a-00000005-00000006-0000000f-0000000a\"\n\n" +
            "  # ── Branch Line (yellow, no electrification) ──\n" +
            "  - id: rural-branch\n" +
            "    name: Rural Branch Line\n" +
            "    properties:\n" +
            "      railway: rail\n" +
            "      usage: branch\n" +
            "      electrified: none\n" +
            "      gauge: 1435\n" +
            "      maxspeed: 60\n" +
            "      operator: Country Link\n" +
            "      operatorShort: CL\n" +
            "      ref: \"BR2\"\n" +
            "      trafficMode: mixed\n" +
            "    match:\n" +
            "      trackIds:\n" +
            "        - \"00000007-0000000a-00000005-00000008-00000010-0000000b\"\n\n" +
            "  # ── Tram / Light Rail (purple, 750V DC) ──\n" +
            "  - id: tram-central\n" +
            "    name: Central Tramway\n" +
            "    properties:\n" +
            "      railway: tram\n" +
            "      usage: main\n" +
            "      electrified: contact_line\n" +
            "      voltage: 750\n" +
            "      frequency: 0\n" +
            "      gauge: 1435\n" +
            "      maxspeed: 50\n" +
            "      operator: TramCo\n" +
            "      operatorShort: TC\n" +
            "      ref: \"T1\"\n" +
            "      trafficMode: passenger\n" +
            "    match:\n" +
            "      trackIds:\n" +
            "        - \"00000009-0000000a-00000005-0000000a-00000011-0000000c\"\n\n" +
            "  # ── Freight / Industrial (brown, 3kV DC) ──\n" +
            "  - id: freight-south\n" +
            "    name: Southern Freight Corridor\n" +
            "    properties:\n" +
            "      railway: rail\n" +
            "      usage: industrial\n" +
            "      electrified: contact_line\n" +
            "      voltage: 3000\n" +
            "      frequency: 0\n" +
            "      gauge: 1435\n" +
            "      maxspeed: 80\n" +
            "      operator: Freight Logistics\n" +
            "      operatorShort: FL\n" +
            "      ref: \"FR1\"\n" +
            "      trafficMode: freight\n" +
            "    match:\n" +
            "      trackIds:\n" +
            "        - \"0000000b-0000000a-00000005-0000000c-00000012-0000000d\"\n\n" +
            "  # ── Narrow Gauge (pink, 1000mm, no electrification) ──\n" +
            "  - id: narrow-gauge-mountain\n" +
            "    name: Mountain Narrow Gauge\n" +
            "    properties:\n" +
            "      railway: rail\n" +
            "      usage: branch\n" +
            "      electrified: none\n" +
            "      gauge: 1000\n" +
            "      maxspeed: 30\n" +
            "      operator: Mountain Railway\n" +
            "      operatorShort: MR\n" +
            "      ref: \"NG1\"\n" +
            "      trafficMode: passenger\n" +
            "    match:\n" +
            "      trackIds:\n" +
            "        - \"0000000d-0000000a-00000005-0000000e-00000013-0000000e\"\n\n" +
            "  # ── Russian Broad Gauge (dark red, 1520mm) ──\n" +
            "  - id: broad-gauge-east\n" +
            "    name: Eastern Broad Gauge\n" +
            "    properties:\n" +
            "      railway: rail\n" +
            "      usage: main\n" +
            "      electrified: contact_line\n" +
            "      voltage: 3000\n" +
            "      frequency: 0\n" +
            "      gauge: 1520\n" +
            "      maxspeed: 140\n" +
            "      operator: TransRail\n" +
            "      operatorShort: TR\n" +
            "      ref: \"BG1\"\n" +
            "      trafficMode: mixed\n" +
            "    match:\n" +
            "      trackIds:\n" +
            "        - \"0000000f-0000000a-00000005-00000010-00000014-0000000f\"\n\n" +
            "  # ── Proposed High-Speed (dashed on map) ──\n" +
            "  - id: proposed-hs2\n" +
            "    name: Proposed HS2 Extension\n" +
            "    properties:\n" +
            "      railway: rail\n" +
            "      usage: hsr\n" +
            "      electrified: proposed\n" +
            "      voltage: 25000\n" +
            "      frequency: 50\n" +
            "      gauge: 1435\n" +
            "      maxspeed: 360\n" +
            "      operator: HS2 Ltd\n" +
            "      operatorShort: HS2\n" +
            "      ref: \"P-HS2\"\n" +
            "    match:\n" +
            "      trackIds:\n" +
            "        - \"00000011-0000000a-00000005-00000012-00000015-00000010\"\n\n" +
            "  # ── Under Construction (black + color overlay) ──\n" +
            "  - id: construction-metro-ext\n" +
            "    name: Metro Extension (Under Construction)\n" +
            "    properties:\n" +
            "      railway: rail\n" +
            "      usage: main\n" +
            "      electrified: construction\n" +
            "      voltage: 750\n" +
            "      frequency: 0\n" +
            "      gauge: 1435\n" +
            "      maxspeed: 80\n" +
            "      operator: City Metro\n" +
            "      operatorShort: CM\n" +
            "      ref: \"M1-ext\"\n" +
            "    match:\n" +
            "      trackIds:\n" +
            "        - \"00000013-0000000a-00000005-00000014-00000016-00000011\"\n\n" +
            "  # ── Spur (brown, short dead-end) ──\n" +
            "  - id: yard-spur\n" +
            "    name: Depot Yard Spur\n" +
            "    properties:\n" +
            "      railway: rail\n" +
            "      usage: spur\n" +
            "      electrified: none\n" +
            "      gauge: 1435\n" +
            "      maxspeed: 15\n" +
            "    match:\n" +
            "      trackIds:\n" +
            "        - \"00000015-0000000a-00000005-00000016-00000017-00000012\"\n\n" +
            "  # ── Area match example (covers a region) ──\n" +
            "  - id: city-center-zone\n" +
            "    name: City Center Zone (area match)\n" +
            "    properties:\n" +
            "      railway: rail\n" +
            "      usage: main\n" +
            "      electrified: contact_line\n" +
            "      voltage: 15000\n" +
            "      frequency: 16.7\n" +
            "      gauge: 1435\n" +
            "      maxspeed: 100\n" +
            "      operator: City Rail\n" +
            "      operatorShort: CR\n" +
            "      ref: \"CZ\"\n" +
            "    match:\n" +
            "      area:\n" +
            "        x1: -1000\n" +
            "        z1: -1000\n" +
            "        x2: 1000\n" +
            "        z2: 1000\n\n" +
            "  # ── Route ID match example ──\n" +
            "  - id: airport-express\n" +
            "    name: Airport Express (route match)\n" +
            "    properties:\n" +
            "      railway: rail\n" +
            "      usage: main\n" +
            "      electrified: contact_line\n" +
            "      voltage: 25000\n" +
            "      frequency: 50\n" +
            "      gauge: 1435\n" +
            "      maxspeed: 160\n" +
            "      operator: AirLink\n" +
            "      operatorShort: AL\n" +
            "      ref: \"AE\"\n" +
            "      trafficMode: passenger\n" +
            "    match:\n" +
            "      routeIds:\n" +
            "        - \"route-airport-express\"\n";
}
