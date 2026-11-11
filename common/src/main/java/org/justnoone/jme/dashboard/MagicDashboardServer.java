package org.justnoone.jme.dashboard;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.justnoone.jme.Jme;
import org.justnoone.jme.config.MagicConfigPaths;
import org.justnoone.jme.config.MagicMapStyleConfig;
import org.justnoone.jme.config.MagicRailwaysConfig;
import com.google.gson.Gson;
import org.mtr.core.Main;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.mtr.mod.Init;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class MagicDashboardServer {

    public static final int PORT = Integer.parseInt(System.getProperty("magic.dashboard.port", "8088"));

    private static HttpServer httpServer;
    private static ExecutorService executorService;
    private static volatile Field initMainField;
    private static volatile Field mainSimulatorsField;
    private static volatile Method railsApiMethod;
    private static volatile boolean loggedSimulatorFailure;
    private static volatile Thread dashboardRetryThread;

    // Persistent rail data cache: served when the simulator is unavailable.
    private static volatile String cachedRailsJson;
    private static volatile long cachedRailsTime;
    private static final long RAILS_CACHE_STALE_MILLIS = 60_000L;
    private static final Path RAILS_CACHE_DIR = MagicConfigPaths.resolveMapFile("dashboard_rails_cache");

    public interface TileProvider {
        byte[] getTile(int x, int z, int zoom);
    }
    
    private static TileProvider tileProvider;
    
    public static void setTileProvider(TileProvider provider) {
        tileProvider = provider;
    }

    private MagicDashboardServer() {
    }

    public static synchronized void start() {
        if (httpServer != null) {
            dashboardRetryThread = null;
            return;
        }

        try {
            final HttpServer createdServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 64);
            // Bounded, adaptive thread pool: enough threads to serve parallel tile/data requests (async download)
            // while the fixed-size tile renderer workers produce images concurrently (async upload).
            final int cores = Runtime.getRuntime().availableProcessors();
            executorService = new ThreadPoolExecutor(
                    8,
                    Math.max(16, cores * 2),
                    60L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(1024),
                    runnable -> {
                        final Thread thread = new Thread(runnable, "MAGIC-Dashboard");
                        thread.setDaemon(true);
                        return thread;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy());
            createdServer.createContext("/", MagicDashboardServer::handleRequest);
            createdServer.setExecutor(executorService);
            createdServer.start();
            httpServer = createdServer;
            dashboardRetryThread = null;
            DashboardAuth.loadConfig();
            Main.LOGGER.info("[{}] MAGIC dashboard listening on http://localhost:{}", Jme.MOD_ID, PORT);
        } catch (Exception exception) {
            // Port may be taken by another MAGIC instance (e.g. an old client still running) - keep retrying
            // so the dashboard comes up without requiring a full client restart.
            Main.LOGGER.warn("[{}] Failed to start MAGIC dashboard on localhost:{} - will retry every 15 seconds", Jme.MOD_ID, PORT, exception);
            stop();
            if (dashboardRetryThread == null) {
                dashboardRetryThread = new Thread(() -> {
                    while (dashboardRetryThread == Thread.currentThread()) {
                        try {
                            Thread.sleep(15000);
                        } catch (InterruptedException interruptedException) {
                            return;
                        }
                        start();
                    }
                }, "MAGIC-Dashboard-Retry");
                dashboardRetryThread.setDaemon(true);
                dashboardRetryThread.start();
            }
        }
    }

    /**
     * True when the dashboard is served from this JVM, i.e. singleplayer or the LAN host. Players on a
     * dedicated server can't reach it, because it only listens on the loopback address.
     */
    public static synchronized boolean isRunning() {
        return httpServer != null;
    }

    public static String getLivemapUrl() {
        return "http://localhost:" + PORT + "/livemap";
    }

    public static synchronized void stop() {
        dashboardRetryThread = null;
        final HttpServer server = httpServer;
        httpServer = null;
        if (server != null) {
            server.stop(0);
        }

        final ExecutorService executor = executorService;
        executorService = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private static void handleRequest(HttpExchange exchange) {
        try {
            addCorsHeaders(exchange);

            final String method = exchange.getRequestMethod() == null ? "" : exchange.getRequestMethod().toUpperCase(Locale.ENGLISH);
            final boolean isGet = "GET".equals(method);
            final boolean isPost = "POST".equals(method);
            // Dashboard writes are intentionally disabled for now. Keep the server GET-only
            // at the request boundary so no POST handler can accidentally mutate state.
            if (!isGet) {
                sendText(exchange, 405, "text/plain; charset=utf-8", "GET only");
                return;
            }

            final String path = exchange.getRequestURI() == null || exchange.getRequestURI().getPath() == null
                    ? "/"
                    : exchange.getRequestURI().getPath();
            final Map<String, String> query = parseQuery(exchange);

            // ── Auth endpoints ──

            if (isPost && "/api/auth".equals(path)) {
                final String body = readRequestBody(exchange);
                handleAuthPost(exchange, body);
                return;
            }

            if (isGet && "/api/auth/me".equals(path)) {
                handleAuthMe(exchange);
                return;
            }

            if (isPost && "/api/auth/logout".equals(path)) {
                handleAuthLogout(exchange);
                return;
            }

            if ("/".equals(path) || "/index.html".equals(path)) {
                final byte[] htmlBytes = loadClasspathResource("/assets/jme/dashboard/index.html");
                if (htmlBytes != null) {
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                    exchange.sendResponseHeaders(200, htmlBytes.length);
                    try (OutputStream outputStream = exchange.getResponseBody()) {
                        outputStream.write(htmlBytes);
                    }
                } else {
                    sendText(exchange, 200, "text/html; charset=utf-8",
                            "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>MAGIC Dashboard</title>"
                            + "<style>body{margin:0;display:flex;align-items:center;justify-content:center;height:100vh;"
                            + "background:#0a0e1a;color:#e0e0e0;font-family:sans-serif}</style></head>"
                            + "<body><h1>MAGIC Dashboard</h1><p>index.html not found in classpath</p></body></html>");
                }
                return;
            }

            if ("/livemap".equals(path) || "/livemap.html".equals(path)) {
                final byte[] htmlBytes = loadClasspathResource("/assets/jme/dashboard/livemap.html");
                if (htmlBytes == null) {
                    sendText(exchange, 404, "text/plain; charset=utf-8", "livemap.html not found in classpath");
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, htmlBytes.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(htmlBytes);
                }
                return;
            }

            if ("/api/map-style".equals(path)) {
                sendText(exchange, 200, "application/json; charset=utf-8", new Gson().toJson(MagicMapStyleConfig.toJsonMap()));
                return;
            }

            // Script shared by the dashboard and the Livemap that applies map-style.yaml.
            if ("/assets/map-style.js".equals(path)) {
                final byte[] scriptBytes = loadClasspathResource("/assets/jme/dashboard/map-style.js");
                if (scriptBytes == null) {
                    sendText(exchange, 404, "text/plain; charset=utf-8", "Not found");
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "text/javascript; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, scriptBytes.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(scriptBytes);
                }
                return;
            }

            final Simulator simulator = findSimulator(query.get("dimension"));

            if (isGet && "/api/rail-routes".equals(path)) {
                final JsonObject response = handleRailRoutesRequest(simulator);
                sendText(exchange, 200, "application/json; charset=utf-8", response.toString());
                return;
            }

            if (isGet && "/api/livemap/lines".equals(path)) {
                final JsonObject response = handleLivemapLinesRequest(simulator);
                sendText(exchange, 200, "application/json; charset=utf-8", response.toString());
                return;
            }

            if (isGet && ("/api/rails".equals(path) || "/rails".equals(path) || "/mtr/api/map/rails".equals(path))) {
                final JsonObject response = handleMagicRailsApiRequest(simulator, query.get("mode"), query.get("routeId"));
                sendText(exchange, 200, "application/json; charset=utf-8", response.toString());
                return;
            }

            if (isGet && "/api/depots".equals(path)) {
                final JsonObject response = handleDepotsRequest(simulator);
                sendText(exchange, 200, "application/json; charset=utf-8", response.toString());
                return;
            }

            if (isGet && "/api/sidings".equals(path)) {
                final JsonObject response = handleSidingsRequest(simulator);
                sendText(exchange, 200, "application/json; charset=utf-8", response.toString());
                return;
            }

            if (isGet && "/api/resourcepack".equals(path)) {
                final JsonObject response = handleResourcePackRequest();
                sendText(exchange, 200, "application/json; charset=utf-8", response.toString());
                return;
            }

            if (isGet && "/api/trackgraph".equals(path)) {
                final JsonObject response = handleTrackGraphRequest(simulator);
                sendText(exchange, 200, "application/json; charset=utf-8", response.toString());
                return;
            }

            if (isGet && "/api/trains".equals(path)) {
                final JsonObject response = handleTrainsRequest(simulator);
                sendText(exchange, 200, "application/json; charset=utf-8", response.toString());
                return;
            }

            if (isGet && "/api/railways".equals(path)) {
                final String response = new com.google.gson.Gson().toJson(MagicRailwaysConfig.toJsonMap());
                sendText(exchange, 200, "application/json; charset=utf-8", response);
                return;
            }

            if (isPost && "/api/railways".equals(path)) {
                if (!requireAuth(exchange, "edit_railways")) return;
                final String body = readRequestBody(exchange);
                MagicRailwaysConfig.saveYaml(body);
                sendText(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true}");
                return;
            }

            if (isPost && "/api/routes".equals(path)) {
                if (!requireAuth(exchange, "edit_routes")) return;
                final String body = readRequestBody(exchange);
                final JsonObject response = handleRoutesPostRequest(simulator, body);
                sendText(exchange, 200, "application/json; charset=utf-8", response.toString());
                return;
            }

            if (isPost && "/api/depots".equals(path)) {
                if (!requireAuth(exchange, "edit_depots")) return;
                final String body = readRequestBody(exchange);
                final JsonObject response = handleDepotsPostRequest(simulator, body);
                sendText(exchange, 200, "application/json; charset=utf-8", response.toString());
                return;
            }

            if (isPost && "/api/platforms".equals(path)) {
                if (!requireAuth(exchange, "edit_platforms")) return;
                final String body = readRequestBody(exchange);
                final JsonObject response = handlePlatformsPostRequest(simulator, body);
                sendText(exchange, 200, "application/json; charset=utf-8", response.toString());
                return;
            }

            if (path.startsWith("/api/tiles/")) {
                byte[] tileData = null;
                try {
                    String[] parts = path.substring("/api/tiles/".length()).replace(".png", "").split("/");
                    if (parts.length >= 3) {
                        int z = Integer.parseInt(parts[0]);
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        tileData = tileProvider == null ? null : tileProvider.getTile(x, y, z);
                    }
                } catch (Exception e) {
                    // ignore and fall through to 404
                }
                if (tileData == null) {
                    sendText(exchange, 404, "text/plain; charset=utf-8", "Tile not found");
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=30, stale-while-revalidate=600");
                exchange.sendResponseHeaders(200, tileData.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(tileData);
                }
                return;
            }

            sendText(exchange, 404, "text/plain; charset=utf-8", "Not found");
        } catch (java.io.IOException ioException) {
            // Broken pipe / client disconnected — normal when browser navigates away or closes.
            // Only log at debug level to avoid log spam.
            Main.LOGGER.debug("[{}] MAGIC dashboard client disconnected: {}", Jme.MOD_ID, ioException.getMessage());
        } catch (Exception exception) {
            try {
                sendText(exchange, 500, "text/plain; charset=utf-8", "Internal server error");
            } catch (Exception ignored) {
            }
            Main.LOGGER.warn("[{}] MAGIC dashboard request failed", Jme.MOD_ID, exception);
        } finally {
            exchange.close();
        }
    }

    private static JsonObject handleMagicRailsApiRequest(Simulator simulator, String mode, String routeId) throws Exception {
        if (simulator != null) {
            Method localRailsApiMethod = railsApiMethod;
            if (localRailsApiMethod == null) {
                localRailsApiMethod = Class.forName("org.mtr.core.servlet.SystemMapServlet")
                        .getDeclaredMethod("jme$handleMagicRailsApiRequest", Simulator.class, String.class, String.class);
                localRailsApiMethod.setAccessible(true);
                railsApiMethod = localRailsApiMethod;
            }

            final Object response = localRailsApiMethod.invoke(null, simulator, mode, routeId);
            if (response instanceof JsonObject) {
                final JsonObject json = (JsonObject) response;
                // Cache the full rails response (without mode/routeId filter) for offline serving.
                if ((mode == null || mode.isEmpty()) && (routeId == null || routeId.isEmpty())) {
                    cachedRailsJson = json.toString();
                    cachedRailsTime = System.currentTimeMillis();
                    saveRailsToDisk(json);
                }
                return json;
            }
        }

        // Simulator unavailable: try serving from disk cache.
        final JsonObject cached = loadRailsFromDisk();
        if (cached != null) {
            return cached;
        }

        final JsonObject fallback = new JsonObject();
        fallback.addProperty("cachedResponseTime", System.currentTimeMillis());
        return fallback;
    }

    private static void saveRailsToDisk(JsonObject json) {
        try {
            Files.createDirectories(RAILS_CACHE_DIR);
            final byte[] raw = json.toString().getBytes(StandardCharsets.UTF_8);
            final LZMA2Options options = new LZMA2Options();
            options.setPreset(3);
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (XZOutputStream xz = new XZOutputStream(baos, options)) {
                xz.write(raw);
                xz.finish();
            }
            Files.write(RAILS_CACHE_DIR.resolve("rails.lzma2"), baos.toByteArray());
        } catch (Exception ignored) {
        }
    }

    private static JsonObject loadRailsFromDisk() {
        try {
            final Path path = RAILS_CACHE_DIR.resolve("rails.lzma2");
            if (!Files.exists(path)) {
                return null;
            }
            final byte[] compressed = Files.readAllBytes(path);
            final ByteArrayInputStream in = new ByteArrayInputStream(compressed);
            final XZInputStream xz = new XZInputStream(in);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[4096];
            int n;
            while ((n = xz.read(buf)) >= 0) {
                if (n > 0) out.write(buf, 0, n);
            }
            xz.close();
            final String json = out.toString(StandardCharsets.UTF_8.name());
            final org.mtr.libraries.com.google.gson.JsonElement parsed =
                    new org.mtr.libraries.com.google.gson.JsonParser().parse(json);
            if (parsed.isJsonObject()) {
                final JsonObject obj = parsed.getAsJsonObject();
                obj.addProperty("cachedResponseTime", System.currentTimeMillis());
                return obj;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        final Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
        headers.set("Cache-Control", "no-store");
    }

    private static void sendText(HttpExchange exchange, int status, String contentType, String body) throws Exception {
        final byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static Map<String, String> parseQuery(HttpExchange exchange) {
        final Map<String, String> result = new LinkedHashMap<>();
        final String rawQuery = exchange.getRequestURI() == null ? null : exchange.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }

        final String[] entries = rawQuery.split("&");
        for (final String entry : entries) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }

            final int equalsIndex = entry.indexOf('=');
            final String rawKey = equalsIndex < 0 ? entry : entry.substring(0, equalsIndex);
            final String rawValue = equalsIndex < 0 ? "" : entry.substring(equalsIndex + 1);
            final String key = decodeUrl(rawKey);
            if (!key.isEmpty()) {
                result.put(key, decodeUrl(rawValue));
            }
        }
        return result;
    }

    private static String decodeUrl(String value) {
        try {
            return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return value == null ? "" : value;
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws Exception {
        try (java.io.InputStream is = exchange.getRequestBody()) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) >= 0) {
                if (n > 0) baos.write(buf, 0, n);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    // ── Auth helpers ──

    private static final String SESSION_COOKIE_NAME = "MAGIC_SESSION";

    private static void handleAuthPost(HttpExchange exchange, String body) throws Exception {
        final JsonObject request;
        try {
            request = new org.mtr.libraries.com.google.gson.JsonParser().parse(body).getAsJsonObject();
        } catch (Exception e) {
            sendText(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"Invalid JSON\"}");
            return;
        }

        final String code = request.has("code") ? request.get("code").getAsString().trim() : "";
        final DashboardAuth.AuthResult result = DashboardAuth.validateAndReturnToken(code);
        if (result == null) {
            sendText(exchange, 401, "application/json; charset=utf-8", "{\"error\":\"Invalid or expired code\"}");
            return;
        }

        // Set session cookie
        final Headers headers = exchange.getResponseHeaders();
        headers.add("Set-Cookie", SESSION_COOKIE_NAME + "=" + result.token()
                + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=86400");

        final JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("username", result.session().username());
        response.addProperty("isAdmin", result.session().isAdmin());
        sendText(exchange, 200, "application/json; charset=utf-8", response.toString());
    }

    private static void handleAuthMe(HttpExchange exchange) throws Exception {
        final DashboardAuth.Session session = readSession(exchange);
        if (session == null) {
            sendText(exchange, 401, "application/json; charset=utf-8", "{\"error\":\"Not authenticated\"}");
            return;
        }

        final JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("username", session.username());
        response.addProperty("isAdmin", session.isAdmin());
        response.addProperty("uuid", session.uuid().toString());
        sendText(exchange, 200, "application/json; charset=utf-8", response.toString());
    }

    private static void handleAuthLogout(HttpExchange exchange) throws Exception {
        final String token = readSessionToken(exchange);
        DashboardAuth.invalidate(token);

        final Headers headers = exchange.getResponseHeaders();
        headers.add("Set-Cookie", SESSION_COOKIE_NAME + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");

        sendText(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true}");
    }

    /**
     * Require an authenticated session for a POST endpoint.
     * Sends 401/403 and returns false if unauthorized.
     */
    private static boolean requireAuth(HttpExchange exchange, String action) throws Exception {
        final DashboardAuth.Session session = readSession(exchange);
        if (session == null) {
            sendText(exchange, 401, "application/json; charset=utf-8", "{\"error\":\"Authentication required. Use /magic auth in-game to get a code.\"}");
            return false;
        }
        if (!DashboardAuth.isAuthorized(session, action)) {
            sendText(exchange, 403, "application/json; charset=utf-8", "{\"error\":\"Not authorized. Ask an admin to add you to the editors list in dashboard_auth.json.\"}");
            return false;
        }
        return true;
    }

    private static DashboardAuth.Session readSession(HttpExchange exchange) {
        return DashboardAuth.fromToken(readSessionToken(exchange));
    }

    private static String readSessionToken(HttpExchange exchange) {
        final Headers headers = exchange.getRequestHeaders();
        final java.util.List<String> cookies = headers.get("Cookie");
        if (cookies == null) return null;

        for (final String cookieHeader : cookies) {
            if (cookieHeader == null) continue;
            final String[] parts = cookieHeader.split(";");
            for (final String part : parts) {
                final String trimmed = part.trim();
                if (trimmed.startsWith(SESSION_COOKIE_NAME + "=")) {
                    return trimmed.substring(SESSION_COOKIE_NAME.length() + 1).trim();
                }
            }
        }
        return null;
    }

    private static JsonObject handleDepotsRequest(Simulator simulator) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        final JsonArray depotsArray = new JsonArray();
        if (simulator == null) {
            response.add("depots", depotsArray);
            return response;
        }
        try {
            for (final org.mtr.core.data.Depot depot : simulator.depots) {
                if (depot == null) continue;
                try {
                    final JsonObject depotJson = new JsonObject();
                    depotJson.addProperty("id", depot.getHexId());
                    depotJson.addProperty("name", depot.getName());
                    try {
                        final org.mtr.core.data.Position[] positions = parsePositionsFromHexId(depot.getHexId());
                        depotJson.add("position", positionToJson(positions[0]));
                    } catch (Exception ignored) {}
                    depotJson.addProperty("color", depot.getColor());
                    final JsonArray sidingIds = new JsonArray();
                    for (final org.mtr.core.data.Siding siding : simulator.sidings) {
                        if (siding != null && siding.area == depot) {
                            sidingIds.add(siding.getHexId());
                        }
                    }
                    depotJson.add("sidings", sidingIds);
                    final JsonArray routeIds = new JsonArray();
                    try {
                        java.lang.reflect.Method getRouteList = depot.getClass().getMethod("getRouteList");
                        Object routeList = getRouteList.invoke(depot);
                        if (routeList instanceof java.util.Collection) {
                            for (Object routeObj : (java.util.Collection<?>) routeList) {
                                if (routeObj instanceof org.mtr.core.data.Route) {
                                    routeIds.add(((org.mtr.core.data.Route) routeObj).getHexId());
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    depotJson.add("routes", routeIds);
                    try {
                        java.lang.reflect.Method getTrainCount = depot.getClass().getMethod("getTrainCount");
                        depotJson.addProperty("trainCount", ((Number) getTrainCount.invoke(depot)).intValue());
                    } catch (Exception ignored) {}
                    try {
                        java.lang.reflect.Method getTrainLength = depot.getClass().getMethod("getTrainLength");
                        depotJson.addProperty("trainLength", ((Number) getTrainLength.invoke(depot)).intValue());
                    } catch (Exception ignored) {}
                    depotsArray.add(depotJson);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        response.add("depots", depotsArray);
        return response;
    }

    // Livemap: visible routes with their stops in order, plus station centres. IDs are sent as hex strings
    // because MTR's own JSON writes 64-bit ids as numbers, which lose precision in the browser.
    private static JsonObject handleLivemapLinesRequest(Simulator simulator) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        final JsonArray linesArray = new JsonArray();
        final JsonArray stationsArray = new JsonArray();
        if (simulator == null) {
            response.add("lines", linesArray);
            response.add("stations", stationsArray);
            return response;
        }
        try {
            final Map<Long, JsonArray> pathsByRouteId = getLivemapRoutePaths(simulator);
            for (final org.mtr.core.data.Route route : simulator.routes) {
                if (route == null || route.getHidden()) continue;
                try {
                    final JsonObject lineJson = new JsonObject();
                    final JsonArray path = pathsByRouteId.get(route.getId());
                    lineJson.add("path", path == null ? new JsonArray() : path);
                    lineJson.addProperty("id", route.getHexId());
                    lineJson.addProperty("name", route.getName());
                    lineJson.addProperty("number", route.getRouteNumber());
                    lineJson.addProperty("color", route.getColor() & 0xFFFFFF);
                    lineJson.addProperty("transportMode", route.getTransportMode() == null ? "TRAIN" : route.getTransportMode().name());
                    lineJson.addProperty("circular", route.getCircularState() != null && route.getCircularState() != org.mtr.core.data.Route.CircularState.NONE);
                    final JsonArray stopsArray = new JsonArray();
                    for (final org.mtr.core.data.RoutePlatformData routePlatformData : route.getRoutePlatforms()) {
                        final org.mtr.core.data.Platform platform = routePlatformData == null ? null : routePlatformData.getPlatform();
                        if (platform == null) continue;
                        final JsonObject stopJson = new JsonObject();
                        stopJson.addProperty("platformId", platform.getHexId());
                        stopJson.addProperty("stationId", platform.area == null ? "" : platform.area.getHexId());
                        stopJson.addProperty("name", platform.getStationName());
                        final org.mtr.core.data.Position midPosition = platform.getMidPosition();
                        stopJson.addProperty("x", midPosition.getX());
                        stopJson.addProperty("z", midPosition.getZ());
                        stopsArray.add(stopJson);
                    }
                    lineJson.add("stops", stopsArray);
                    linesArray.add(lineJson);
                } catch (Exception ignored) {}
            }
            for (final org.mtr.core.data.Station station : simulator.stations) {
                if (station == null) continue;
                try {
                    final JsonObject stationJson = new JsonObject();
                    stationJson.addProperty("id", station.getHexId());
                    stationJson.addProperty("name", station.getName());
                    stationJson.addProperty("color", station.getColor() & 0xFFFFFF);
                    final org.mtr.core.data.Position center = station.getCenter();
                    stationJson.addProperty("x", center.getX());
                    stationJson.addProperty("z", center.getZ());
                    stationsArray.add(stationJson);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        response.add("lines", linesArray);
        response.add("stations", stationsArray);
        return response;
    }

    private static volatile Field depotPathField;
    private static final double LIVEMAP_SAMPLE_BLOCKS = 4;
    private static final int LIVEMAP_MAX_SAMPLES_PER_SEGMENT = 64;

    /**
     * Track geometry per route, taken from the path MTR generated for each depot - the same path its trains drive
     * on, so the drawn line always matches the train positions. Each route maps to a list of polylines of [x, z].
     */
    private static Map<Long, JsonArray> getLivemapRoutePaths(Simulator simulator) {
        final Map<Long, JsonArray> pathsByRouteId = new java.util.HashMap<>();
        final JsonArray[] currentLine = {null};
        final long[] currentRouteId = {0};
        final double[] last = {Double.NaN, Double.NaN};
        forEachDepotPathSegment(simulator, (pathData, route) -> {
            final double length = pathData.getRailLength();
            if (!(length > 0)) return;
            final int samples = (int) Math.max(1, Math.min(LIVEMAP_MAX_SAMPLES_PER_SEGMENT, Math.ceil(length / LIVEMAP_SAMPLE_BLOCKS)));
            final org.mtr.core.tool.Vector start = pathData.getPosition(0);
            // Start a new polyline when the route changes or the path jumps (eg a reversal elsewhere).
            final boolean continuous = currentLine[0] != null && currentRouteId[0] == route.getId()
                    && Math.abs(start.x - last[0]) < 2 && Math.abs(start.z - last[1]) < 2;
            if (!continuous) {
                currentLine[0] = new JsonArray();
                currentRouteId[0] = route.getId();
                pathsByRouteId.computeIfAbsent(route.getId(), key -> new JsonArray()).add(currentLine[0]);
                currentLine[0].add(livemapPoint(start));
            }
            for (int i = 1; i <= samples; i++) {
                final org.mtr.core.tool.Vector point = pathData.getPosition(length * i / samples);
                currentLine[0].add(livemapPoint(point));
                last[0] = point.x;
                last[1] = point.z;
            }
        });
        return pathsByRouteId;
    }

    /** /api/rail-routes: for each rail on a generated route path, the routes (hex IDs) that run over it. */
    private static JsonObject handleRailRoutesRequest(Simulator simulator) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        final JsonObject railsJson = new JsonObject();
        if (simulator != null) {
            final Map<String, java.util.Set<String>> routesByRail = new java.util.HashMap<>();
            forEachDepotPathSegment(simulator, (pathData, route) -> {
                final org.mtr.core.data.Rail rail = pathData.getRail();
                if (rail != null) {
                    routesByRail.computeIfAbsent(rail.getHexId(), key -> new java.util.LinkedHashSet<>()).add(route.getHexId());
                }
            });
            routesByRail.forEach((railId, routeIds) -> {
                final JsonArray routeIdsJson = new JsonArray();
                routeIds.forEach(routeIdsJson::add);
                railsJson.add(railId, routeIdsJson);
            });
        }
        response.add("rails", railsJson);
        return response;
    }

    /**
     * Walks the path MTR generated for every depot - the track its trains drive on - with the route each segment
     * belongs to.
     */
    private static void forEachDepotPathSegment(Simulator simulator, java.util.function.BiConsumer<org.mtr.core.data.PathData, org.mtr.core.data.Route> consumer) {
        for (final org.mtr.core.data.Depot depot : simulator.depots) {
            if (depot == null) continue;
            try {
                // Depot#platformsInRoute: the platforms of all the depot's routes in order, skipping a platform that
                // repeats the previous one. A path segment's stop index points into this list.
                final java.util.List<org.mtr.core.data.Route> routeByStopIndex = new java.util.ArrayList<>();
                long previousPlatformId = 0;
                for (final org.mtr.core.data.Route route : depot.routes) {
                    for (final org.mtr.core.data.RoutePlatformData routePlatformData : route.getRoutePlatforms()) {
                        final org.mtr.core.data.Platform platform = routePlatformData == null ? null : routePlatformData.platform;
                        if (platform == null || platform.getId() == previousPlatformId) continue;
                        routeByStopIndex.add(route);
                        previousPlatformId = platform.getId();
                    }
                }
                if (routeByStopIndex.isEmpty()) continue;
                for (final org.mtr.core.data.PathData pathData : getDepotPath(depot)) {
                    final int stopIndex = (int) Math.max(0, Math.min(routeByStopIndex.size() - 1, pathData.getStopIndex()));
                    consumer.accept(pathData, routeByStopIndex.get(stopIndex));
                }
            } catch (Exception ignored) {}
        }
    }

    private static java.util.List<org.mtr.core.data.PathData> getDepotPath(org.mtr.core.data.Depot depot) throws Exception {
        Field field = depotPathField;
        if (field == null) {
            field = org.mtr.core.data.Depot.class.getDeclaredField("path");
            field.setAccessible(true);
            depotPathField = field;
        }
        final Object path = field.get(depot);
        // Copy first: the simulator may regenerate the path while we read it.
        return path instanceof java.util.List<?> ? new java.util.ArrayList<>((java.util.List<org.mtr.core.data.PathData>) path) : new java.util.ArrayList<>();
    }

    private static JsonArray livemapPoint(org.mtr.core.tool.Vector vector) {
        final JsonArray point = new JsonArray();
        point.add(Math.round(vector.x * 10) / 10D);
        point.add(Math.round(vector.z * 10) / 10D);
        return point;
    }

    private static JsonObject handleSidingsRequest(Simulator simulator) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        final JsonArray sidingsArray = new JsonArray();
        if (simulator == null) {
            response.add("sidings", sidingsArray);
            return response;
        }
        try {
            for (final org.mtr.core.data.Siding siding : simulator.sidings) {
                if (siding == null) continue;
                try {
                    final JsonObject sidingJson = new JsonObject();
                    sidingJson.addProperty("id", siding.getHexId());
                    sidingJson.addProperty("name", siding.getName());
                    try {
                        java.lang.reflect.Method getTrainCount = siding.getClass().getMethod("getTrainCount");
                        sidingJson.addProperty("trainCount", ((Number) getTrainCount.invoke(siding)).intValue());
                    } catch (Exception ignored) {}
                    try {
                        java.lang.reflect.Method getTrainLength = siding.getClass().getMethod("getTrainLength");
                        sidingJson.addProperty("trainLength", ((Number) getTrainLength.invoke(siding)).intValue());
                    } catch (Exception ignored) {}
                    try {
                        final org.mtr.core.data.Position[] positions = parsePositionsFromHexId(siding.getHexId());
                        sidingJson.add("position", positionToJson(positions[0]));
                    } catch (Exception ignored) {}
                    if (siding.area instanceof org.mtr.core.data.Depot) {
                        sidingJson.addProperty("depotId", ((org.mtr.core.data.Depot) siding.area).getHexId());
                    }
                    final JsonArray vehiclesArray = new JsonArray();
                    final java.util.Collection<?> vehicles = getSidingVehicles(siding);
                    if (vehicles != null) {
                        for (final Object vehicleObj : vehicles) {
                            if (vehicleObj == null || !(vehicleObj instanceof org.mtr.core.data.Vehicle)) continue;
                            final org.mtr.core.data.Vehicle vehicle = (org.mtr.core.data.Vehicle) vehicleObj;
                            try {
                                final JsonObject vehicleJson = new JsonObject();
                                vehicleJson.addProperty("id", vehicle.getHexId());
                                try {
                                    java.lang.reflect.Field speedField = vehicle.getClass().getDeclaredField("speed");
                                    speedField.setAccessible(true);
                                    vehicleJson.addProperty("speed", speedField.getDouble(vehicle));
                                } catch (Exception ignored) {}
                                vehicleJson.addProperty("isOnRoute", vehicle.getIsOnRoute());
                                try {
                                    java.lang.reflect.Method m = vehicle.getClass().getMethod("getIsFixedRoute");
                                    vehicleJson.addProperty("isFixedRoute", (Boolean) m.invoke(vehicle));
                                } catch (Exception ignored) {}
                                final org.mtr.core.data.VehicleExtraData extraData = vehicle.vehicleExtraData;
                                if (extraData != null) {
                                    vehicleJson.addProperty("routeId", org.mtr.core.tool.Utilities.numberToPaddedHexString(extraData.getThisRouteId()));
                                    vehicleJson.addProperty("platformId", org.mtr.core.tool.Utilities.numberToPaddedHexString(extraData.getThisPlatformId()));
                                }
                                vehiclesArray.add(vehicleJson);
                            } catch (Exception ignored) {}
                        }
                    }
                    sidingJson.add("vehicles", vehiclesArray);
                    sidingsArray.add(sidingJson);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        response.add("sidings", sidingsArray);
        return response;
    }

    private static JsonObject handleResourcePackRequest() {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        final JsonArray carsArray = new JsonArray();
        try {
            final Class<?> vehicleClass = Class.forName("org.mtr.mod.data.VehicleVehicle");
            final Object[] values = (Object[]) vehicleClass.getMethod("values").invoke(null);
            for (final Object car : values) {
                try {
                    final JsonObject carJson = new JsonObject();
                    carJson.addProperty("name", car.toString());
                    final java.lang.reflect.Method getName = car.getClass().getMethod("getName");
                    carJson.addProperty("id", String.valueOf(getName.invoke(car)));
                    final java.lang.reflect.Method getLength = car.getClass().getMethod("getLength");
                    carJson.addProperty("length", ((Number) getLength.invoke(car)).doubleValue());
                    final java.lang.reflect.Method getWidth = car.getClass().getMethod("getWidth");
                    carJson.addProperty("width", ((Number) getWidth.invoke(car)).doubleValue());
                    final java.lang.reflect.Method getMaxSpeed = car.getClass().getMethod("getMaxSpeed");
                    carJson.addProperty("maxSpeed", ((Number) getMaxSpeed.invoke(car)).doubleValue());
                    carsArray.add(carJson);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
            final JsonObject fallback = new JsonObject();
            fallback.addProperty("note", "Vehicle types unavailable; check MTR resource pack");
            carsArray.add(fallback);
        }
        response.add("availableCars", carsArray);
        return response;
    }

    private static JsonObject handleTrackGraphRequest(Simulator simulator) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        final JsonArray nodesArray = new JsonArray();
        final JsonArray edgesArray = new JsonArray();
        if (simulator == null) {
            response.add("nodes", nodesArray);
            response.add("edges", edgesArray);
            return response;
        }
        try {
            final java.util.Map<String, Boolean> visitedPositions = new java.util.HashMap<>();
            simulator.rails.forEach(rail -> {
                try {
                    final String railId = rail.getHexId();
                    final org.mtr.core.data.Position[] positions = parsePositionsFromHexId(railId);
                    final org.mtr.core.data.Position p1 = positions[0];
                    final org.mtr.core.data.Position p2 = positions[1];
                    final String key1 = p1.getX() + "," + p1.getY() + "," + p1.getZ();
                    final String key2 = p2.getX() + "," + p2.getY() + "," + p2.getZ();
                    if (!visitedPositions.containsKey(key1)) {
                        visitedPositions.put(key1, true);
                        final JsonObject nodeJson = new JsonObject();
                        nodeJson.addProperty("x", p1.getX());
                        nodeJson.addProperty("y", p1.getY());
                        nodeJson.addProperty("z", p1.getZ());
                        nodesArray.add(nodeJson);
                    }
                    if (!visitedPositions.containsKey(key2)) {
                        visitedPositions.put(key2, true);
                        final JsonObject nodeJson = new JsonObject();
                        nodeJson.addProperty("x", p2.getX());
                        nodeJson.addProperty("y", p2.getY());
                        nodeJson.addProperty("z", p2.getZ());
                        nodesArray.add(nodeJson);
                    }
                    final JsonObject edgeJson = new JsonObject();
                        edgeJson.addProperty("id", railId);
                        edgeJson.add("from", positionToJson(p1));
                        edgeJson.add("to", positionToJson(p2));
                        edgeJson.addProperty("length", rail.railMath.getLength());
                        double speedLimit1 = 0;
                        double speedLimit2 = 0;
                        try {
                            speedLimit1 = rail.getSpeedLimitMetersPerMillisecond(p1);
                        } catch (Exception ignored) {}
                        try {
                            speedLimit2 = rail.getSpeedLimitMetersPerMillisecond(p2);
                        } catch (Exception ignored) {}
                        edgeJson.addProperty("speedLimit1", speedLimit1);
                        edgeJson.addProperty("speedLimit2", speedLimit2);
                        edgesArray.add(edgeJson);
                    } catch (Exception ignored) {}
                });
        } catch (Exception ignored) {}
        response.add("nodes", nodesArray);
        response.add("edges", edgesArray);
        return response;
    }

    private static JsonObject handleTrainsRequest(Simulator simulator) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        final JsonArray trainsArray = new JsonArray();
        if (simulator == null) {
            response.add("trains", trainsArray);
            return response;
        }
        try {
            for (final org.mtr.core.data.Siding siding : simulator.sidings) {
                if (siding == null) continue;
                try {
                    final java.util.Collection<?> vehicles = getSidingVehicles(siding);
                    if (vehicles == null) continue;
                    for (final Object vehicleObj : vehicles) {
                        if (vehicleObj == null || !(vehicleObj instanceof org.mtr.core.data.Vehicle)) continue;
                        final org.mtr.core.data.Vehicle vehicle = (org.mtr.core.data.Vehicle) vehicleObj;
                        try {
                            final JsonObject trainJson = new JsonObject();
                            trainJson.addProperty("id", vehicle.getHexId());
                            trainJson.addProperty("sidingId", siding.getHexId());
                            try {
                                java.lang.reflect.Field speedField = vehicle.getClass().getDeclaredField("speed");
                                speedField.setAccessible(true);
                                trainJson.addProperty("speed", speedField.getDouble(vehicle));
                            } catch (Exception ignored) {}
                            trainJson.addProperty("isOnRoute", vehicle.getIsOnRoute());
                            try {
                                java.lang.reflect.Field rpField = vehicle.getClass().getDeclaredField("railProgress");
                                rpField.setAccessible(true);
                                trainJson.addProperty("railProgress", rpField.getDouble(vehicle));
                            } catch (Exception ignored) {}
                            final org.mtr.core.data.VehicleExtraData extraData = vehicle.vehicleExtraData;
                            if (extraData != null) {
                                trainJson.addProperty("routeId", org.mtr.core.tool.Utilities.numberToPaddedHexString(extraData.getThisRouteId()));
                                trainJson.addProperty("platformId", org.mtr.core.tool.Utilities.numberToPaddedHexString(extraData.getThisPlatformId()));
                            }
                            trainsArray.add(trainJson);
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        response.add("trains", trainsArray);
        return response;
    }

    private static JsonObject handleRoutesPostRequest(Simulator simulator, String body) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        if (simulator == null) {
            response.addProperty("success", false);
            response.addProperty("error", "Simulator not available");
            return response;
        }
        try {
            final org.mtr.libraries.com.google.gson.JsonElement element = new org.mtr.libraries.com.google.gson.JsonParser().parse(body);
            if (!element.isJsonObject()) {
                response.addProperty("success", false);
                response.addProperty("error", "Invalid JSON body");
                return response;
            }
            final JsonObject request = element.getAsJsonObject();
            final String action = request.has("action") ? request.get("action").getAsString() : "";
            if ("create".equals(action)) {
                final String routeName = request.has("name") ? request.get("name").getAsString() : "";
                final String colorStr = request.has("color") ? request.get("color").getAsString() : "FFFFFF";
                if (routeName.isEmpty()) {
                    response.addProperty("success", false);
                    response.addProperty("error", "Route name is required");
                    return response;
                }
                try {
                    int colorInt = 0;
                    try {
                        colorInt = Integer.parseInt(colorStr.replace("#", ""), 16);
                    } catch (Exception ignored) {}
                    org.mtr.core.data.Route newRoute = null;
                    try {
                        java.lang.reflect.Constructor<?> ctor = org.mtr.core.data.Route.class.getConstructor(String.class, int.class);
                        newRoute = (org.mtr.core.data.Route) ctor.newInstance(routeName, colorInt);
                    } catch (Exception ignored) {}
                    if (newRoute == null) {
                        try {
                            java.lang.reflect.Constructor<?> ctor2 = org.mtr.core.data.Route.class.getConstructor(String.class);
                            newRoute = (org.mtr.core.data.Route) ctor2.newInstance(routeName);
                        } catch (Exception ignored) {}
                    }
                    if (newRoute == null) {
                        response.addProperty("success", false);
                        response.addProperty("error", "Cannot create Route");
                        return response;
                    }
                    try {
                        java.lang.reflect.Method addRouteMethod = simulator.getClass().getMethod("addRoute", org.mtr.core.data.Route.class);
                        addRouteMethod.invoke(simulator, newRoute);
                    } catch (Exception ignored) {}
                    response.addProperty("success", true);
                    response.addProperty("routeId", newRoute.getHexId());
                } catch (Exception e) {
                    response.addProperty("success", false);
                    response.addProperty("error", "Failed to create route: " + e.getMessage());
                }
            } else if ("setSchedule".equals(action)) {
                final String routeId = request.has("routeId") ? request.get("routeId").getAsString() : "";
                final int frequency = request.has("frequency") ? request.get("frequency").getAsInt() : 120;
                final boolean circular = request.has("circular") && request.get("circular").getAsBoolean();
                org.mtr.core.data.Route foundRoute = null;
                for (final org.mtr.core.data.Route route : simulator.routes) {
                    if (route != null && route.getHexId().equalsIgnoreCase(routeId)) {
                        foundRoute = route;
                        break;
                    }
                }
                if (foundRoute == null) {
                    response.addProperty("success", false);
                    response.addProperty("error", "Route not found");
                    return response;
                }
                try {
                    java.lang.reflect.Method setFrequency = null;
                    java.lang.reflect.Method setCircular = null;
                    try {
                        setFrequency = foundRoute.getClass().getMethod("setFrequency", int.class);
                    } catch (NoSuchMethodException ignored) {}
                    try {
                        setCircular = foundRoute.getClass().getMethod("setCircular", boolean.class);
                    } catch (NoSuchMethodException ignored) {}
                    if (setFrequency != null) {
                        setFrequency.invoke(foundRoute, frequency);
                    }
                    if (setCircular != null) {
                        setCircular.invoke(foundRoute, circular);
                    }
                    response.addProperty("success", true);
                    response.addProperty("routeId", foundRoute.getHexId());
                    response.addProperty("note", "Schedule update applied via reflection");
                } catch (Exception e) {
                    response.addProperty("success", false);
                    response.addProperty("error", "Failed to set schedule: " + e.getMessage());
                }
            } else {
                response.addProperty("success", false);
                response.addProperty("error", "Unknown action: " + action);
            }
        } catch (Exception e) {
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
        }
        return response;
    }

    private static JsonObject handleDepotsPostRequest(Simulator simulator, String body) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        if (simulator == null) {
            response.addProperty("success", false);
            response.addProperty("error", "Simulator not available");
            return response;
        }
        try {
            final org.mtr.libraries.com.google.gson.JsonElement element = new org.mtr.libraries.com.google.gson.JsonParser().parse(body);
            if (!element.isJsonObject()) {
                response.addProperty("success", false);
                response.addProperty("error", "Invalid JSON body");
                return response;
            }
            final JsonObject request = element.getAsJsonObject();
            final String depotId = request.has("depotId") ? request.get("depotId").getAsString() : "";
            org.mtr.core.data.Depot foundDepot = null;
            for (final org.mtr.core.data.Depot depot : simulator.depots) {
                if (depot != null && depot.getHexId().equalsIgnoreCase(depotId)) {
                    foundDepot = depot;
                    break;
                }
            }
            if (foundDepot == null) {
                response.addProperty("success", false);
                response.addProperty("error", "Depot not found");
                return response;
            }
            boolean applied = false;
            if (request.has("trainCount")) {
                try {
                    java.lang.reflect.Method m = foundDepot.getClass().getMethod("setTrainCount", int.class);
                    m.invoke(foundDepot, request.get("trainCount").getAsInt());
                    applied = true;
                } catch (Exception ignored) {}
            }
            if (request.has("trainLength")) {
                try {
                    java.lang.reflect.Method m = foundDepot.getClass().getMethod("setTrainLength", int.class);
                    m.invoke(foundDepot, request.get("trainLength").getAsInt());
                    applied = true;
                } catch (Exception ignored) {}
            }
            response.addProperty("success", applied);
            response.addProperty("depotId", foundDepot.getHexId());
            if (!applied) {
                response.addProperty("note", "Depot settings may require the MTR dashboard to modify");
            }
        } catch (Exception e) {
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
        }
        return response;
    }

    private static JsonObject handlePlatformsPostRequest(Simulator simulator, String body) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        if (simulator == null) {
            response.addProperty("success", false);
            response.addProperty("error", "Simulator not available");
            return response;
        }
        try {
            final org.mtr.libraries.com.google.gson.JsonElement element = new org.mtr.libraries.com.google.gson.JsonParser().parse(body);
            if (!element.isJsonObject()) {
                response.addProperty("success", false);
                response.addProperty("error", "Invalid JSON body");
                return response;
            }
            final JsonObject request = element.getAsJsonObject();
            final String platformId = request.has("platformId") ? request.get("platformId").getAsString() : "";
            org.mtr.core.data.Platform foundPlatform = null;
            for (final org.mtr.core.data.Platform platform : simulator.platforms) {
                if (platform != null && platform.getHexId().equalsIgnoreCase(platformId)) {
                    foundPlatform = platform;
                    break;
                }
            }
            if (foundPlatform == null) {
                response.addProperty("success", false);
                response.addProperty("error", "Platform not found");
                return response;
            }
            boolean applied = false;
            if (request.has("name")) {
                try {
                    java.lang.reflect.Method m = foundPlatform.getClass().getMethod("setName", String.class);
                    m.invoke(foundPlatform, request.get("name").getAsString());
                    applied = true;
                } catch (Exception ignored) {}
            }
            if (request.has("dwellTime")) {
                try {
                    java.lang.reflect.Method m = foundPlatform.getClass().getMethod("setDwellTime", int.class);
                    m.invoke(foundPlatform, request.get("dwellTime").getAsInt());
                    applied = true;
                } catch (Exception ignored) {}
            }
            response.addProperty("success", applied);
            response.addProperty("platformId", foundPlatform.getHexId());
            if (!applied) {
                response.addProperty("note", "Platform settings may require the MTR dashboard to modify");
            }
        } catch (Exception e) {
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
        }
        return response;
    }

    private static org.mtr.core.data.Position[] parsePositionsFromHexId(String hexId) {
        final String[] split = hexId.split("-");
        if (split.length != 6) {
            return new org.mtr.core.data.Position[]{
                    new org.mtr.core.data.Position(0, 0, 0),
                    new org.mtr.core.data.Position(0, 0, 0)
            };
        }
        return new org.mtr.core.data.Position[]{
                new org.mtr.core.data.Position(Long.parseUnsignedLong(split[0], 16), Long.parseUnsignedLong(split[1], 16), Long.parseUnsignedLong(split[2], 16)),
                new org.mtr.core.data.Position(Long.parseUnsignedLong(split[3], 16), Long.parseUnsignedLong(split[4], 16), Long.parseUnsignedLong(split[5], 16))
        };
    }

    private static JsonObject positionToJson(org.mtr.core.data.Position pos) {
        final JsonObject json = new JsonObject();
        json.addProperty("x", pos.getX());
        json.addProperty("y", pos.getY());
        json.addProperty("z", pos.getZ());
        return json;
    }

    private static volatile Field sidingVehiclesField;

    private static java.util.Collection<?> getSidingVehicles(Object siding) {
        try {
            Field field = sidingVehiclesField;
            if (field == null) {
                field = siding.getClass().getDeclaredField("vehicles");
                field.setAccessible(true);
                sidingVehiclesField = field;
            }
            final Object vehicles = field.get(siding);
            return vehicles instanceof java.util.Collection<?> ? (java.util.Collection<?>) vehicles : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Simulator findSimulator(String requestedDimension) {
        try {
            final Iterable<?> simulators = getSimulators();
            if (simulators == null) {
                return null;
            }

            final String normalizedRequestedDimension = normalizeDimensionId(requestedDimension);
            Simulator fallback = null;
            int count = 0;
            for (final Object simulatorObject : simulators) {
                if (!(simulatorObject instanceof Simulator)) {
                    continue;
                }

                final Simulator simulator = (Simulator) simulatorObject;
                count++;
                if (fallback == null) {
                    fallback = simulator;
                }

                if (dimensionsMatch(normalizedRequestedDimension, simulator.dimension)) {
                    return simulator;
                }
            }

            return normalizedRequestedDimension.isEmpty() || count == 1 ? fallback : null;
        } catch (Exception exception) {
            if (!loggedSimulatorFailure) {
                loggedSimulatorFailure = true;
                Main.LOGGER.warn("[{}] Failed to resolve simulator for MAGIC dashboard", Jme.MOD_ID, exception);
            }
            return null;
        }
    }

    private static Iterable<?> getSimulators() throws IllegalAccessException, NoSuchFieldException {
        Field localInitMainField = initMainField;
        if (localInitMainField == null) {
            localInitMainField = Init.class.getDeclaredField("main");
            localInitMainField.setAccessible(true);
            initMainField = localInitMainField;
        }

        final Object main = localInitMainField.get(null);
        if (main == null) {
            return null;
        }

        Field localMainSimulatorsField = mainSimulatorsField;
        if (localMainSimulatorsField == null) {
            localMainSimulatorsField = main.getClass().getDeclaredField("simulators");
            localMainSimulatorsField.setAccessible(true);
            mainSimulatorsField = localMainSimulatorsField;
        }

        final Object simulators = localMainSimulatorsField.get(main);
        return simulators instanceof Iterable<?> ? (Iterable<?>) simulators : null;
    }

    private static boolean dimensionsMatch(String requestedDimension, String simulatorDimension) {
        if (requestedDimension == null || requestedDimension.isEmpty()) {
            return false;
        }

        final String normalizedSimulatorDimension = normalizeDimensionId(simulatorDimension);
        return requestedDimension.equals(normalizedSimulatorDimension)
                || requestedDimension.endsWith("/" + normalizedSimulatorDimension)
                || normalizedSimulatorDimension.endsWith("/" + requestedDimension);
    }

    private static String normalizeDimensionId(String id) {
        if (id == null) {
            return "";
        }

        final String normalized = id.trim().replace(':', '/').toLowerCase(Locale.ENGLISH);
        if ("0".equals(normalized)) {
            return "minecraft/overworld";
        }
        if ("-1".equals(normalized)) {
            return "minecraft/the_nether";
        }
        if ("1".equals(normalized)) {
            return "minecraft/the_end";
        }
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    private static byte[] loadClasspathResource(String resourcePath) {
        try (java.io.InputStream is = MagicDashboardServer.class.getResourceAsStream(resourcePath)) {
            if (is == null) return null;
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) >= 0) {
                if (n > 0) baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
