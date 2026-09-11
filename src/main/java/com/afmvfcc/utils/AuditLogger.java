package com.afmvfcc.utils;

import com.afmvfcc.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class AuditLogger {

    public static void log(int userId, String action) {
        String sql = "INSERT INTO audit_log (user_id, action) VALUES (?, ?)";
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            if (userId <= 0) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, userId);
            }
            ps.setString(2, action);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("❌ Failed to write audit log: " + e.getMessage());
        }
    }
}
