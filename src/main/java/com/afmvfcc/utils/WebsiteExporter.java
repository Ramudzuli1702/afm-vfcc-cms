package com.afmvfcc.utils;

import com.afmvfcc.db.DatabaseConnection;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Generates data.js from the database and pushes it to:
 *   1. The local website folder (if configured), AND
 *   2. The GitHub repository (if configured).
 *
 * Both destinations are optional — whichever settings are present will be used.
 */
public class WebsiteExporter {

    private static final Logger LOG = Logger.getLogger(WebsiteExporter.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ----------------------------------------------------------------
    // Main export method — called after every save/delete
    // ----------------------------------------------------------------

    /**
     * @param localFolder  path to the local website folder (may be null / empty)
     * @throws RuntimeException if GitHub IS configured but the push fails, so the
     *         caller (WebsiteController) can actually surface the failure to the
     *         user instead of the CMS silently reporting success while the live
     *         site never updates. If GitHub isn't configured at all, that's a
     *         valid local-only setup and is not treated as an error.
     */
    public static void exportAll(String localFolder) {
        String dataJs;
        try {
            dataJs = buildDataJs();
        } catch (Exception e) {
            LOG.severe("WebsiteExporter: could not read website content from the database: " + e.getMessage());
            throw new RuntimeException("Could not read website content from the database: " + e.getMessage(), e);
        }

        // 1. Write to local folder — best-effort. A bad/inaccessible local path
        //    must not prevent the GitHub push below from being attempted.
        if (localFolder != null && !localFolder.isEmpty()) {
            try {
                File f = new File(localFolder, "data.js");
                try (PrintWriter w = new PrintWriter(new FileWriter(f))) {
                    w.print(dataJs);
                }
                LOG.info("data.js written to local folder: " + localFolder);
            } catch (Exception e) {
                LOG.warning("Could not write data.js to local folder \"" + localFolder + "\": " + e.getMessage());
            }
        }

        // 2. Push to GitHub
        if (GitHubSync.isConfigured()) {
            String ghErr = GitHubSync.pushDataJs(dataJs);
            if (ghErr != null) {
                LOG.warning("GitHub push failed: " + ghErr);
                throw new RuntimeException("Could not publish to GitHub: " + ghErr);
            }
            LOG.info("data.js pushed to GitHub successfully.");
        }
    }

    // ----------------------------------------------------------------
    // Build data.js content
    // ----------------------------------------------------------------

    public static String buildDataJs() throws SQLException {
        Connection conn = DatabaseConnection.getConnection();

        // ── Events ──
        List<Map<String, String>> events = new ArrayList<>();
        ResultSet evRs = conn.createStatement().executeQuery(
                "SELECT title, description, DATE_FORMAT(event_date,'%d %b %Y'), image_filename " +
                "FROM website_events WHERE event_date >= CURDATE() " +
                "ORDER BY event_date ASC LIMIT 50");
        while (evRs.next()) {
            Map<String, String> ev = new LinkedHashMap<>();
            ev.put("title", nvl(evRs.getString(1)));
            ev.put("desc",  nvl(evRs.getString(2)));
            ev.put("date",  nvl(evRs.getString(3)));
            // image_filename stored as GitHub Pages URL or local path
            String img = nvl(evRs.getString(4));
            ev.put("image", img);
            events.add(ev);
        }

        // ── Blogs ──
        List<Map<String, String>> blogs = new ArrayList<>();
        ResultSet blRs = conn.createStatement().executeQuery(
                "SELECT title, author, DATE_FORMAT(published_at,'%d %b %Y'), content " +
                "FROM website_blogs ORDER BY published_at DESC LIMIT 30");
        while (blRs.next()) {
            Map<String, String> bl = new LinkedHashMap<>();
            bl.put("title",   nvl(blRs.getString(1)));
            bl.put("author",  nvl(blRs.getString(2)));
            bl.put("date",    nvl(blRs.getString(3)));
            bl.put("content", nvl(blRs.getString(4)));
            blogs.add(bl);
        }

        // ── Leaders — one combined photo of Bishop & Mom Bishop together,
        //    with each of their names/roles listed underneath it ──
        String leadersPhoto = nvl(loadSetting(conn, "website_leaders_photo"));
        List<Map<String, String>> leaders = new ArrayList<>();
        addLeaderIfSet(leaders, conn, "website_leader1_name", "website_leader1_role");
        addLeaderIfSet(leaders, conn, "website_leader2_name", "website_leader2_role");

        // ── Church Board — photo from settings, member names live from the Board module ──
        String boardPhoto = nvl(loadSetting(conn, "website_board_photo"));
        List<String> boardMembers = new ArrayList<>();
        ResultSet bmRs = conn.createStatement().executeQuery(
                "SELECT m.full_name, bm.role_title FROM board_members bm " +
                "JOIN members m ON m.id = bm.member_id " +
                "WHERE bm.is_active = 1 ORDER BY bm.start_date ASC, m.full_name ASC");
        while (bmRs.next()) {
            String name = bmRs.getString(1);
            String role = bmRs.getString(2);
            boardMembers.add((role != null && !role.isBlank()) ? name + " — " + role : name);
        }

        // ── Bishop's Message (video) ──
        Map<String, String> message = new LinkedHashMap<>();
        message.put("video",   nvl(loadSetting(conn, "website_message_video")));
        message.put("poster",  nvl(loadSetting(conn, "website_message_poster")));
        message.put("caption", nvl(loadSetting(conn, "website_message_caption")));
        boolean messageIsSet = message.values().stream().anyMatch(v -> !v.isEmpty());

        // ── Contact details ──
        Map<String, String> contact = new LinkedHashMap<>();
        contact.put("address",       nvl(loadSetting(conn, "website_contact_address")));
        contact.put("phone",         nvl(loadSetting(conn, "website_contact_phone")));
        contact.put("phoneHref",     nvl(loadSetting(conn, "website_contact_phone_href")));
        contact.put("facebookUrl",   nvl(loadSetting(conn, "website_contact_facebook_url")));
        contact.put("facebookLabel", nvl(loadSetting(conn, "website_contact_facebook_label")));
        contact.put("youtubeUrl",    nvl(loadSetting(conn, "website_contact_youtube_url")));
        contact.put("youtubeLabel",  nvl(loadSetting(conn, "website_contact_youtube_label")));
        contact.put("email",         nvl(loadSetting(conn, "website_contact_email")));
        boolean contactIsSet = contact.values().stream().anyMatch(v -> !v.isEmpty());

        // ── Serialise ──
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("events", events);
        root.put("blogs",  blogs);
        root.put("leadersPhoto", leadersPhoto);
        root.put("leaders", leaders);
        root.put("boardPhoto", boardPhoto);
        root.put("boardMembers", boardMembers);
        if (messageIsSet) root.put("message", message);
        if (contactIsSet) root.put("contact", contact);

        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated by AFM VFCC CMS — do not edit manually\n");
        sb.append("window.churchData = ");
        sb.append(GSON.toJson(root));
        sb.append(";\n");
        return sb.toString();
    }

    private static String nvl(String s) { return s != null ? s : ""; }

    private static String loadSetting(Connection conn, String key) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT setting_value FROM system_settings WHERE setting_key=?");
        ps.setString(1, key);
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getString(1) : null;
    }

    private static void addLeaderIfSet(List<Map<String, String>> leaders, Connection conn,
            String nameKey, String roleKey) throws SQLException {
        String name = loadSetting(conn, nameKey);
        if (name == null || name.isBlank()) return;
        Map<String, String> l = new LinkedHashMap<>();
        l.put("name", name);
        l.put("role", nvl(loadSetting(conn, roleKey)));
        leaders.add(l);
    }
}
