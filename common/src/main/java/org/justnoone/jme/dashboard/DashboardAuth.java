package org.justnoone.jme.dashboard;

import org.justnoone.jme.Jme;
import org.mtr.core.Main;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dashboard authentication. Players generate a 6-digit code via
 * {@code /magic auth}, then paste it into the :8088 web map to authenticate.
 * Authenticated sessions can make POST requests to the dashboard API.
 */
public final class DashboardAuth {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long CODE_EXPIRY_MILLIS = 5 * 60 * 1000L; // 5 minutes
    private static final long SESSION_MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L; // 24 hours

    /** Pending codes: code → PendingAuth */
    private static final Map<String, PendingAuth> pendingCodes = new ConcurrentHashMap<>();
    /** Active sessions: sessionToken → Session */
    private static final Map<String, Session> activeSessions = new ConcurrentHashMap<>();

    private static DashboardAuthConfig config;

    private DashboardAuth() {
    }

    /**
     * Generate a 6-digit auth code for the given player.
     * Any previous unused code for this player is replaced.
     *
     * @param uuid      player UUID
     * @param username  player display name
     * @param isAdmin   true if the player has OP level 2
     * @return the 6-digit code string
     */
    public static String generateCode(UUID uuid, String username, boolean isAdmin) {
        // Remove any existing pending code for this player
        pendingCodes.values().removeIf(p -> p.uuid.equals(uuid));

        final String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        pendingCodes.put(code, new PendingAuth(uuid, username, isAdmin, System.currentTimeMillis() + CODE_EXPIRY_MILLIS));

        Main.LOGGER.info("[{}] Auth code generated for {} ({})", Jme.MOD_ID, username, uuid);
        return code;
    }

    /**
     * Validate a 6-digit code and create a session.
     * Returns the session if valid, null if expired or invalid.
     */
    public static Session validateCode(String code) {
        if (code == null || code.length() != 6) return null;

        final PendingAuth pending = pendingCodes.remove(code);
        if (pending == null) return null;

        if (System.currentTimeMillis() > pending.expiryMillis) {
            return null;
        }

        final String sessionToken = UUID.randomUUID().toString();
        final Session session = new Session(pending.uuid, pending.username, pending.isAdmin, System.currentTimeMillis());
        activeSessions.put(sessionToken, session);

        Main.LOGGER.info("[{}] Dashboard authenticated: {} ({}) admin={}", Jme.MOD_ID, pending.username, pending.uuid, pending.isAdmin);
        return session;
    }

    /**
     * Validate and return both session and token string.
     */
    public static AuthResult validateAndReturnToken(String code) {
        if (code == null || code.length() != 6) return null;

        final PendingAuth pending = pendingCodes.remove(code);
        if (pending == null) return null;

        if (System.currentTimeMillis() > pending.expiryMillis) {
            return null;
        }

        final String sessionToken = UUID.randomUUID().toString();
        final Session session = new Session(pending.uuid, pending.username, pending.isAdmin, System.currentTimeMillis());
        activeSessions.put(sessionToken, session);

        Main.LOGGER.info("[{}] Dashboard authenticated: {} ({}) admin={}", Jme.MOD_ID, pending.username, pending.uuid, pending.isAdmin);
        return new AuthResult(sessionToken, session);
    }

    /**
     * Look up an active session by token.
     * Returns null if the token is invalid or the session has expired.
     */
    public static Session fromToken(String token) {
        if (token == null || token.isEmpty()) return null;
        final Session session = activeSessions.get(token);
        if (session == null) return null;
        if (System.currentTimeMillis() - session.loginTime > SESSION_MAX_AGE_MILLIS) {
            activeSessions.remove(token);
            return null;
        }
        return session;
    }

    /**
     * Invalidate a session (logout).
     */
    public static void invalidate(String token) {
        if (token != null) {
            activeSessions.remove(token);
        }
    }

    /**
     * Check if a session is authorized for the given action.
     * Admins (OP level 2) are always authorized.
     * Non-admins must be in the editors whitelist.
     */
    public static boolean isAuthorized(Session session, String action) {
        if (session == null) return false;
        if (session.isAdmin) return true;
        if (config == null) loadConfig();
        return config != null && config.isEditor(session.uuid);
    }

    /**
     * Load (or reload) the auth whitelist config from disk.
     */
    public static void loadConfig() {
        config = DashboardAuthConfig.load();
    }

    /**
     * Get the current config.
     */
    public static DashboardAuthConfig getConfig() {
        if (config == null) loadConfig();
        return config;
    }

    // ---- Types ----
    // Plain classes rather than records: the 1.16.5 build targets Java 8.

    public static final class PendingAuth {
        private final UUID uuid;
        private final String username;
        private final boolean isAdmin;
        private final long expiryMillis;

        public PendingAuth(UUID uuid, String username, boolean isAdmin, long expiryMillis) {
            this.uuid = uuid;
            this.username = username;
            this.isAdmin = isAdmin;
            this.expiryMillis = expiryMillis;
        }

        public UUID uuid() { return uuid; }

        public String username() { return username; }

        public boolean isAdmin() { return isAdmin; }

        public long expiryMillis() { return expiryMillis; }
    }

    public static final class Session {
        private final UUID uuid;
        private final String username;
        private final boolean isAdmin;
        private final long loginTime;

        public Session(UUID uuid, String username, boolean isAdmin, long loginTime) {
            this.uuid = uuid;
            this.username = username;
            this.isAdmin = isAdmin;
            this.loginTime = loginTime;
        }

        public UUID uuid() { return uuid; }

        public String username() { return username; }

        public boolean isAdmin() { return isAdmin; }

        public long loginTime() { return loginTime; }
    }

    public static final class AuthResult {
        private final String token;
        private final Session session;

        public AuthResult(String token, Session session) {
            this.token = token;
            this.session = session;
        }

        public String token() { return token; }

        public Session session() { return session; }
    }
}
