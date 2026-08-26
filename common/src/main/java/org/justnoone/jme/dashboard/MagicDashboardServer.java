package org.justnoone.jme.dashboard;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.justnoone.jme.Jme;
import org.justnoone.jme.config.MagicConfigPaths;
import org.mtr.core.Main;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;
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
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"GET".equals(method)) {
                sendText(exchange, 405, "text/plain; charset=utf-8", "Method not allowed");
                return;
            }

            final String path = exchange.getRequestURI() == null || exchange.getRequestURI().getPath() == null
                    ? "/"
                    : exchange.getRequestURI().getPath();
            if ("/".equals(path) || "/index.html".equals(path)) {
                try (java.io.InputStream is = MagicDashboardServer.class.getResourceAsStream("/assets/jme/dashboard/index.html")) {
                    if (is != null) {
                        byte[] bytes = new byte[is.available()];
                        is.read(bytes);
                        sendText(exchange, 200, "text/html; charset=utf-8", new String(bytes, StandardCharsets.UTF_8));
                    } else {
                        sendText(exchange, 404, "text/plain; charset=utf-8", "Dashboard not found");
                    }
                }
                return;
            }

            if ("/api/rails".equals(path) || "/rails".equals(path) || "/mtr/api/map/rails".equals(path)) {
                final Map<String, String> query = parseQuery(exchange);
                final Simulator simulator = findSimulator(query.get("dimension"));
                final JsonObject response = handleMagicRailsApiRequest(simulator, query.get("mode"), query.get("routeId"));
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
        headers.set("Access-Control-Allow-Methods", "GET, OPTIONS");
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
}
