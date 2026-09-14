package com.afmvfcc.api;

import com.afmvfcc.db.DatabaseConnection;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Embedded HTTP API server for the AFM VFCC mobile app.
 * Runs on port 8080 on the local WiFi network.
 * The Android app connects using the CMS computer's local IP address.
 *
 * Endpoints:
 *   POST /api/login               - authenticate usher
 *   GET  /api/sessions            - list OPEN attendance sessions only (is_closed = 0)
 *   POST /api/sessions            - create new session from app
 *   GET  /api/members             - list members (filtered by session ministry if set)
 *   POST /api/sync/attendance     - receive attendance records from app (closed sessions rejected)
 *   POST /api/sync/guests         - receive guest records from app
 *   GET  /api/ping                - health check
 *
 * MINISTRY FILTERING:
 *   GET /api/members?sessionId=N
 *   The server looks up the ministry_id on the session.
 *   - If ministry_id IS set  → only members in that ministry are returned.
 *   - If ministry_id IS NULL → all active members are returned.
 *   This means the Android app always passes sessionId and gets exactly
 *   the right member list automatically.
 */
public class CmsApiServer {

    private static final int PORT = 8080;
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int TOKEN_TTL_DAYS = 90;
    private static Javalin app;
    private static final Gson gson = new Gson();
    private static final SecureRandom secureRandom = new SecureRandom();

    public static void start() {
        if (app != null) return;

        // Ensure required tables exist — safe to run every time
        try {
            Connection conn = DatabaseConnection.getConnection();
            conn.createStatement().executeUpdate(
                "CREATE TABLE IF NOT EXISTS app_tokens (" +
                "  id          INT AUTO_INCREMENT PRIMARY KEY," +
                "  device_name VARCHAR(100)," +
                "  token       VARCHAR(255) NOT NULL UNIQUE," +
                "  stored_user VARCHAR(50)," +
                "  user_id     INT," +
                "  is_active   TINYINT(1) DEFAULT 1," +
                "  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ")");
            // Plain MySQL (unlike MariaDB) has never supported
            // "ADD COLUMN IF NOT EXISTS" - check via metadata instead,
            // same pattern DatabaseConnection's own migrations use.
            if (!columnExists(conn, "app_tokens", "stored_user")) {
                conn.createStatement().executeUpdate(
                    "ALTER TABLE app_tokens ADD COLUMN stored_user VARCHAR(50)");
            }
            if (!columnExists(conn, "app_tokens", "user_id")) {
                conn.createStatement().executeUpdate(
                    "ALTER TABLE app_tokens ADD COLUMN user_id INT");
            }
            conn.createStatement().executeUpdate(
                "CREATE TABLE IF NOT EXISTS guests (" +
                "  id               INT AUTO_INCREMENT PRIMARY KEY," +
                "  full_name        VARCHAR(100) NOT NULL," +
                "  phone            VARCHAR(20)," +
                "  gender           ENUM('Male','Female','Other')," +
                "  sub_branch_id    INT DEFAULT NULL," +
                "  invited_by       VARCHAR(100)," +
                "  wants_membership TINYINT(1) DEFAULT 0," +
                "  prayer_request   TEXT," +
                "  session_id       INT DEFAULT NULL," +
                "  synced_from      VARCHAR(100)," +
                "  visit_date       DATE," +
                "  status           ENUM('Guest','Converted','Dismissed') DEFAULT 'Guest'," +
                "  created_at       DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ")");
        } catch (Exception e) {
            System.err.println("[CMS API] Table setup warning: " + e.getMessage());
        }

        // No CORS plugin: this API is consumed only by the native Android app
        // (not a browser), so there's no reason to let arbitrary web pages
        // read it cross-origin. Enabling `anyHost()` CORS here would let any
        // site a device on the church WiFi happens to visit silently read
        // member, attendance, and guest data via background fetch() calls.
        app = Javalin.create(config -> config.showJavalinBanner = false);

        // ── Health check ──────────────────────────────────────
        app.get("/api/ping", ctx -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("status", "ok");
            obj.addProperty("system", "AFM VFCC CMS");
            ctx.json(obj.toString());
        });

        // ── Login ─────────────────────────────────────────────
        app.post("/api/login", ctx -> {
            try {
                JsonObject body = gson.fromJson(ctx.body(), JsonObject.class);
                String username = body.get("username").getAsString().trim();
                String password = body.get("password").getAsString();

                Connection conn = DatabaseConnection.getConnection();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, full_name, password_hash, is_active, locked_until, failed_attempts " +
                        "FROM users WHERE username=?")) {
                    ps.setString(1, username);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            ctx.status(401).json("{\"error\":\"Invalid username or password\"}");
                            return;
                        }
                        if (rs.getInt("is_active") == 0) {
                            ctx.status(403).json("{\"error\":\"Account is inactive\"}");
                            return;
                        }
                        int userId = rs.getInt("id");
                        Timestamp locked = rs.getTimestamp("locked_until");
                        if (locked != null && locked.after(new java.util.Date())) {
                            ctx.status(403).json("{\"error\":\"Account is locked. Contact the super admin.\"}");
                            return;
                        }
                        if (!BCrypt.checkpw(password, rs.getString("password_hash"))) {
                            recordFailedLogin(conn, userId, rs.getInt("failed_attempts"));
                            ctx.status(401).json("{\"error\":\"Invalid username or password\"}");
                            return;
                        }

                        // Successful login — reset lockout counter, same as the desktop app
                        try (PreparedStatement reset = conn.prepareStatement(
                                "UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE id = ?")) {
                            reset.setInt(1, userId);
                            reset.executeUpdate();
                        }

                        String name  = rs.getString("full_name");
                        String token = issueToken(conn, userId, username);

                        JsonObject resp = new JsonObject();
                        resp.addProperty("token", token);
                        resp.addProperty("userId", userId);
                        resp.addProperty("name", name);
                        ctx.json(resp.toString());
                    }
                }
            } catch (Exception e) {
                System.err.println("[CMS API] login error: " + e);
                ctx.status(500).json("{\"error\":\"Server error\"}");
            }
        });

        // ── Sessions (open only) ──────────────────────────────
        //
        // Only sessions with is_closed = 0 are returned. Closed sessions
        // are managed in the desktop CMS and are never surfaced to the app.
        app.get("/api/sessions", ctx -> {
            if (!isAuthorised(ctx)) return;
            try {
                Connection conn = DatabaseConnection.getConnection();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT s.id, s.session_name, s.session_date, " +
                        "IFNULL(m.name,'All') AS ministry_name, " +
                        "(SELECT COUNT(*) FROM attendance_records ar " +
                        " WHERE ar.session_id=s.id AND ar.is_present=1) AS present_count " +
                        "FROM attendance_sessions s " +
                        "LEFT JOIN ministries m ON m.id=s.ministry_id " +
                        "WHERE s.is_closed = 0 " +
                        "ORDER BY s.session_date DESC, s.id DESC LIMIT 30");
                     ResultSet rs = ps.executeQuery()) {
                    JsonArray arr = new JsonArray();
                    while (rs.next()) {
                        JsonObject o = new JsonObject();
                        o.addProperty("id",           rs.getInt("id"));
                        o.addProperty("name",         rs.getString("session_name"));
                        o.addProperty("date",         rs.getString("session_date"));
                        o.addProperty("ministry",     rs.getString("ministry_name"));
                        o.addProperty("presentCount", rs.getInt("present_count"));
                        arr.add(o);
                    }
                    ctx.json(arr.toString());
                }
            } catch (Exception e) {
                System.err.println("[CMS API] request error: " + e);
                ctx.status(500).json("{\"error\":\"Server error\"}");
            }
        });

        app.post("/api/sessions", ctx -> {
            if (!isAuthorised(ctx)) return;
            try {
                JsonObject body = gson.fromJson(ctx.body(), JsonObject.class);
                String name   = body.get("name").getAsString();
                String date   = body.has("date") ? body.get("date").getAsString()
                              : LocalDate.now().toString();
                Integer minId = body.has("ministryId") && !body.get("ministryId").isJsonNull()
                              ? body.get("ministryId").getAsInt() : null;

                Connection conn = DatabaseConnection.getConnection();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO attendance_sessions (session_name, session_date, ministry_id) VALUES (?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name);
                    ps.setDate(2, java.sql.Date.valueOf(date));
                    if (minId != null) ps.setInt(3, minId); else ps.setNull(3, Types.INTEGER);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        int newId = keys.getInt(1);
                        JsonObject resp = new JsonObject();
                        resp.addProperty("id", newId);
                        resp.addProperty("name", name);
                        resp.addProperty("date", date);
                        ctx.status(201).json(resp.toString());
                    }
                }
            } catch (Exception e) {
                System.err.println("[CMS API] request error: " + e);
                ctx.status(500).json("{\"error\":\"Server error\"}");
            }
        });

        // ── Members (ministry-aware) ──────────────────────────
        app.get("/api/members", ctx -> {
            if (!isAuthorised(ctx)) return;
            try {
                Connection conn = DatabaseConnection.getConnection();
                String sessionParam = ctx.queryParam("sessionId");

                Integer ministryId = null;
                if (sessionParam != null && !sessionParam.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT ministry_id FROM attendance_sessions WHERE id=?")) {
                        ps.setInt(1, Integer.parseInt(sessionParam));
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                int mid = rs.getInt("ministry_id");
                                // getInt returns 0 for SQL NULL — treat 0 as "no filter"
                                ministryId = rs.wasNull() ? null : mid;
                            }
                        }
                    }
                }

                boolean filterByMinistry = (ministryId != null);
                boolean hasSession       = (sessionParam != null && !sessionParam.isEmpty());

                StringBuilder sql = new StringBuilder();
                sql.append(
                    "SELECT m.id, m.full_name, m.phone, " +
                    "IFNULL(sb.name,'') AS sub_branch, " +
                    "IFNULL(GROUP_CONCAT(DISTINCT mi.name ORDER BY mi.name SEPARATOR ', '),'') AS ministries, "
                );

                if (hasSession) {
                    sql.append("IFNULL(ar.is_present, 0) AS is_present ");
                } else {
                    sql.append("0 AS is_present ");
                }

                sql.append("FROM members m ");
                sql.append("LEFT JOIN sub_branches sb ON sb.id = m.sub_branch_id ");

                if (filterByMinistry) {
                    // INNER JOIN — only members in the target ministry pass through
                    sql.append("INNER JOIN member_ministries mmf " +
                               "ON mmf.member_id = m.id AND mmf.ministry_id = ? ");
                }

                sql.append("LEFT JOIN member_ministries mm ON mm.member_id = m.id ");
                sql.append("LEFT JOIN ministries mi ON mi.id = mm.ministry_id ");

                if (hasSession) {
                    sql.append("LEFT JOIN attendance_records ar " +
                               "ON ar.member_id = m.id AND ar.session_id = ? ");
                }

                sql.append("WHERE m.is_deleted = 0 AND m.is_active = 1 AND m.is_deceased = 0 ");
                sql.append("GROUP BY m.id, m.full_name, m.phone, sub_branch");
                if (hasSession) sql.append(", ar.is_present");
                sql.append(" ORDER BY m.full_name ASC");

                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    int paramIndex = 1;
                    if (filterByMinistry) ps.setInt(paramIndex++, ministryId);
                    if (hasSession)       ps.setInt(paramIndex,   Integer.parseInt(sessionParam));

                    try (ResultSet rs = ps.executeQuery()) {
                        JsonArray arr = new JsonArray();
                        while (rs.next()) {
                            JsonObject o = new JsonObject();
                            o.addProperty("id",        rs.getInt("id"));
                            o.addProperty("name",      rs.getString("full_name"));
                            o.addProperty("phone",     rs.getString("phone") != null
                                                        ? rs.getString("phone") : "");
                            o.addProperty("subBranch", rs.getString("sub_branch"));
                            o.addProperty("ministry",  rs.getString("ministries"));
                            o.addProperty("isPresent", rs.getInt("is_present") == 1);
                            arr.add(o);
                        }
                        ctx.json(arr.toString());
                    }
                }
            } catch (Exception e) {
                System.err.println("[CMS API] /api/members error: " + e);
                ctx.status(500).json("{\"error\":\"Server error\"}");
            }
        });

        // ── Sub-branches (for guest form) ─────────────────────
        app.get("/api/sub-branches", ctx -> {
            if (!isAuthorised(ctx)) return;
            try {
                Connection conn = DatabaseConnection.getConnection();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, name FROM sub_branches WHERE is_active=1 ORDER BY name");
                     ResultSet rs = ps.executeQuery()) {
                    JsonArray arr = new JsonArray();
                    while (rs.next()) {
                        JsonObject o = new JsonObject();
                        o.addProperty("id",   rs.getInt("id"));
                        o.addProperty("name", rs.getString("name"));
                        arr.add(o);
                    }
                    ctx.json(arr.toString());
                }
            } catch (Exception e) {
                System.err.println("[CMS API] request error: " + e);
                ctx.status(500).json("{\"error\":\"Server error\"}");
            }
        });

        // ── Sync attendance ───────────────────────────────────
        app.post("/api/sync/attendance", ctx -> {
            if (!isAuthorised(ctx)) return;
            try {
                JsonObject body   = gson.fromJson(ctx.body(), JsonObject.class);
                int sessionId     = body.get("sessionId").getAsInt();
                JsonArray records = body.getAsJsonArray("records");
                String deviceId   = body.has("deviceId")
                                  ? body.get("deviceId").getAsString() : "app";

                Connection conn = DatabaseConnection.getConnection();

                // Guard: reject sync if session does not exist or is closed
                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT is_closed FROM attendance_sessions WHERE id = ?")) {
                    check.setInt(1, sessionId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next()) {
                            ctx.status(404).json("{\"error\":\"Session not found\"}");
                            return;
                        }
                        if (rs.getInt("is_closed") == 1) {
                            ctx.status(409).json("{\"error\":\"Session is closed and no longer accepts attendance\"}");
                            return;
                        }
                    }
                }

                int inserted = 0, updated = 0;

                for (int i = 0; i < records.size(); i++) {
                    JsonObject r = records.get(i).getAsJsonObject();
                    int memberId  = r.get("memberId").getAsInt();
                    boolean present = r.get("isPresent").getAsBoolean();

                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO attendance_records " +
                            "(session_id, member_id, is_present, synced_from) " +
                            "VALUES (?,?,?,?) " +
                            "ON DUPLICATE KEY UPDATE is_present=?, synced_from=?, synced_at=NOW()")) {
                        ps.setInt(1, sessionId);
                        ps.setInt(2, memberId);
                        ps.setInt(3, present ? 1 : 0);
                        ps.setString(4, deviceId);
                        ps.setInt(5, present ? 1 : 0);
                        ps.setString(6, deviceId);
                        int rows = ps.executeUpdate();
                        if (rows == 1) inserted++; else updated++;
                    }
                }

                JsonObject resp = new JsonObject();
                resp.addProperty("inserted", inserted);
                resp.addProperty("updated",  updated);
                resp.addProperty("total",    records.size());
                ctx.json(resp.toString());

            } catch (Exception e) {
                System.err.println("[CMS API] sync/attendance error: " + e);
                ctx.status(500).json("{\"error\":\"Server error\"}");
            }
        });

        // ── Sync guests ───────────────────────────────────────
        app.post("/api/sync/guests", ctx -> {
            if (!isAuthorised(ctx)) return;
            try {
                JsonObject body  = gson.fromJson(ctx.body(), JsonObject.class);
                JsonArray guests = body.getAsJsonArray("guests");
                String deviceId  = body.has("deviceId")
                                 ? body.get("deviceId").getAsString() : "app";

                Connection conn = DatabaseConnection.getConnection();
                int count = 0;

                for (int i = 0; i < guests.size(); i++) {
                    JsonObject g = guests.get(i).getAsJsonObject();
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO guests " +
                            "(full_name, phone, gender, sub_branch_id, invited_by, " +
                            " wants_membership, prayer_request, session_id, synced_from, visit_date) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?)")) {
                        ps.setString(1, g.has("name")   ? g.get("name").getAsString()  : "");
                        ps.setString(2, g.has("phone")  ? g.get("phone").getAsString() : null);
                        ps.setString(3, g.has("gender") ? g.get("gender").getAsString(): null);

                        if (g.has("subBranchId") && !g.get("subBranchId").isJsonNull())
                            ps.setInt(4, g.get("subBranchId").getAsInt());
                        else
                            ps.setNull(4, Types.INTEGER);

                        ps.setString(5, g.has("invitedBy")       ? g.get("invitedBy").getAsString()       : null);
                        ps.setInt(6,    g.has("wantsMembership") && g.get("wantsMembership").getAsBoolean() ? 1 : 0);
                        ps.setString(7, g.has("prayerRequest")   ? g.get("prayerRequest").getAsString()   : null);

                        if (g.has("sessionId") && !g.get("sessionId").isJsonNull())
                            ps.setInt(8, g.get("sessionId").getAsInt());
                        else
                            ps.setNull(8, Types.INTEGER);

                        ps.setString(9, deviceId);
                        ps.setDate(10, g.has("visitDate")
                            ? java.sql.Date.valueOf(g.get("visitDate").getAsString())
                            : java.sql.Date.valueOf(LocalDate.now()));

                        ps.executeUpdate();
                        count++;
                    }
                }

                JsonObject resp = new JsonObject();
                resp.addProperty("synced", count);
                ctx.json(resp.toString());

            } catch (Exception e) {
                System.err.println("[CMS API] sync/guests error: " + e);
                ctx.status(500).json("{\"error\":\"Server error\"}");
            }
        });

        app.start(PORT);
        System.out.println("[CMS API] Server started on port " + PORT);
    }

    public static void stop() {
        if (app != null) { app.stop(); app = null; }
    }

    public static int getPort() { return PORT; }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Verifies the bearer token against app_tokens: it must exist, be
     * active, not expired, and belong to a user who is still active — this
     * is what makes deactivating a user invalidate their app sessions.
     */
    private static boolean isAuthorised(Context ctx) {
        String auth = ctx.header("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            ctx.status(401).json("{\"error\":\"Unauthorised\"}");
            return false;
        }
        String token = auth.substring(7).trim();
        if (token.isEmpty()) {
            ctx.status(401).json("{\"error\":\"Unauthorised\"}");
            return false;
        }

        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT at.created_at FROM app_tokens at " +
                    "JOIN users u ON u.id = at.user_id " +
                    "WHERE at.token = ? AND at.is_active = 1 AND u.is_active = 1")) {
                ps.setString(1, token);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        ctx.status(401).json("{\"error\":\"Invalid or expired token\"}");
                        return false;
                    }
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    if (createdAt != null &&
                            createdAt.toLocalDateTime().plusDays(TOKEN_TTL_DAYS).isBefore(LocalDateTime.now())) {
                        ctx.status(401).json("{\"error\":\"Session expired, please log in again\"}");
                        return false;
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("[CMS API] token check error: " + e);
            ctx.status(500).json("{\"error\":\"Server error\"}");
            return false;
        }
    }

    /** Generates a cryptographically random token and stores it against this user. */
    private static String issueToken(Connection conn, int userId, String username) throws SQLException {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        String token = sb.toString();

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO app_tokens (device_name, token, stored_user, user_id, is_active) " +
                "VALUES (?, ?, ?, ?, 1)")) {
            ps.setString(1, "Android app");
            ps.setString(2, token);
            ps.setString(3, username);
            ps.setInt(4, userId);
            ps.executeUpdate();
        }
        return token;
    }

    /** Mirrors the desktop LoginController's lockout: 5 failed attempts locks the account. */
    private static void recordFailedLogin(Connection conn, int userId, int failedAttempts) throws SQLException {
        failedAttempts++;
        if (failedAttempts >= MAX_LOGIN_ATTEMPTS) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE id = ?")) {
                ps.setInt(1, failedAttempts);
                ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now().plusYears(100)));
                ps.setInt(3, userId);
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE users SET failed_attempts = ? WHERE id = ?")) {
                ps.setInt(1, failedAttempts);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }
}