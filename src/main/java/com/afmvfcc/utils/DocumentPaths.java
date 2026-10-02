package com.afmvfcc.utils;

import com.afmvfcc.db.DatabaseConnection;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Resolves where generated documents (member/welfare/attendance exports,
 * certificates, etc.) should be saved, based on the "document_output_folder"
 * setting configured in Settings. Each document type gets its own subfolder
 * under that base folder, created automatically on first use.
 *
 * When no base folder is configured, callers fall back to their previous
 * behaviour (a manual Save dialog).
 */
public class DocumentPaths {

    public static String getBaseFolder() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT setting_value FROM system_settings WHERE setting_key='document_output_folder'");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String v = rs.getString(1);
                return (v != null && !v.isBlank()) ? v : null;
            }
        } catch (SQLException ignored) {}
        return null;
    }

    /** Returns {@code <base>/<subfolder>} (creating it if needed), or null if no base folder is configured. */
    public static File resolveFolder(String subfolder) {
        String base = getBaseFolder();
        if (base == null) return null;
        File dir = new File(base, subfolder);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Returns {@code <base>/<subfolder>/<filename>}, or null if no base folder
     * is configured (caller should fall back to a manual Save dialog). If a
     * file with that name already exists, a "_2", "_3", ... suffix is added
     * so a fresh document is never silently overwritten.
     */
    public static File resolveFile(String subfolder, String filename) {
        File dir = resolveFolder(subfolder);
        if (dir == null) return null;
        File f = new File(dir, filename);
        if (!f.exists()) return f;

        String base = filename, ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot > 0) { base = filename.substring(0, dot); ext = filename.substring(dot); }
        int n = 2;
        File candidate;
        do {
            candidate = new File(dir, base + "_" + n + ext);
            n++;
        } while (candidate.exists());
        return candidate;
    }
}
