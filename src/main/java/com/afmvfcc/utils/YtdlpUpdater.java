package com.afmvfcc.utils;

import com.afmvfcc.db.DatabaseConnection;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Keeps the bundled yt-dlp.exe from going stale by running its own
 * self-update ({@code yt-dlp -U}) roughly every 90 days - yt-dlp's whole
 * advantage over a hand-rolled downloader is that it's actively maintained
 * against sites like Facebook changing their page structure, which only
 * actually helps if the copy on disk stays current.
 */
public class YtdlpUpdater {

    private static final long UPDATE_INTERVAL_DAYS = 90;
    private static final String SETTING_KEY = "ytdlp_last_update_check";

    /**
     * Fire-and-forget: checks (on a background thread) whether an update is
     * due, and if so runs {@code yt-dlp -U}. Safe to call every time the
     * Broadcast module opens - it's a no-op unless 90+ days have passed
     * since the last successful check.
     */
    public static void checkAndUpdateIfDue(String ytdlpPath) {
        new Thread(() -> runCheck(ytdlpPath), "ytdlp-update-check").start();
    }

    private static void runCheck(String ytdlpPath) {
        try {
            String lastStr = loadSetting(SETTING_KEY);
            if (lastStr != null) {
                LocalDate last = LocalDate.parse(lastStr);
                if (!LocalDate.now().isAfter(last.plusDays(UPDATE_INTERVAL_DAYS))) {
                    return; // not due yet
                }
            }

            System.out.println("[yt-dlp] 90+ days since last update check - running yt-dlp -U...");
            Process process = new ProcessBuilder(ytdlpPath, "-U")
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[yt-dlp -U] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                // Only record success - a failed attempt (no internet, etc.)
                // should retry on the next launch rather than wait out the
                // full 90 days again.
                saveSetting(SETTING_KEY, LocalDate.now().toString());
                System.out.println("[yt-dlp] Update check complete.");
            } else {
                System.err.println("[yt-dlp] Update check exited with code " + exitCode
                        + " - will retry next launch.");
            }
        } catch (Exception e) {
            System.err.println("[yt-dlp] Update check failed: " + e.getMessage());
        }
    }

    private static String loadSetting(String key) {
        try {
            PreparedStatement ps = DatabaseConnection.getConnection()
                    .prepareStatement("SELECT setting_value FROM system_settings WHERE setting_key=?");
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch (SQLException ignored) {}
        return null;
    }

    private static void saveSetting(String key, String value) {
        try {
            PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(
                    "INSERT INTO system_settings (setting_key, setting_value) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)");
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[yt-dlp] Could not save last-update-check date: " + e.getMessage());
        }
    }
}
