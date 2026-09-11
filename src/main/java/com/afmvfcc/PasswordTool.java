package com.afmvfcc;

import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.utils.PasswordUtil;

import java.sql.*;
import java.util.Scanner;

/**
 * AFM VFCC — Password Manager Utility
 * Run this to create or update admin passwords with proper bcrypt hashing.
 * Usage: gradle runPasswordTool
 */
public class PasswordTool {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║   AFM VFCC — Password Manager Tool   ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println();

        // Test DB connection
        if (!DatabaseConnection.testConnection()) {
            System.out.println("❌ Cannot connect to database. Is MySQL running?");
            return;
        }
        System.out.println("✅ Database connected.\n");

        boolean running = true;
        while (running) {
            System.out.println("What do you want to do?");
            System.out.println("  1 — Update password for existing user");
            System.out.println("  2 — Create new admin user");
            System.out.println("  3 — List all users");
            System.out.println("  4 — Generate hash for a password (no DB change)");
            System.out.println("  0 — Exit");
            System.out.print("\nChoice: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> updatePassword(scanner);
                case "2" -> createUser(scanner);
                case "3" -> listUsers();
                case "4" -> generateHash(scanner);
                case "0" -> running = false;
                default  -> System.out.println("Invalid choice.\n");
            }
        }

        DatabaseConnection.closeAll();
        System.out.println("Bye.");
    }

    // ── UPDATE PASSWORD ───────────────────────────────────────

    private static void updatePassword(Scanner scanner) {
        listUsers();
        System.out.print("Enter username to update: ");
        String username = scanner.nextLine().trim();

        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement check = conn.prepareStatement(
                "SELECT id, full_name FROM users WHERE username = ?"
            );
            check.setString(1, username);
            ResultSet rs = check.executeQuery();

            if (!rs.next()) {
                System.out.println("❌ User '" + username + "' not found.\n");
                return;
            }
            System.out.println("Found: " + rs.getString("full_name"));

            System.out.print("Enter new password: ");
            String password = scanner.nextLine().trim();
            if (password.isEmpty()) {
                System.out.println("❌ Password cannot be empty.\n");
                return;
            }

            String hash = PasswordUtil.hash(password);

            PreparedStatement update = conn.prepareStatement(
                "UPDATE users SET password_hash = ?, failed_attempts = 0, locked_until = NULL WHERE username = ?"
            );
            update.setString(1, hash);
            update.setString(2, username);
            update.executeUpdate();

            System.out.println("✅ Password updated for '" + username + "'.");
            System.out.println("   Hash: " + hash);
            System.out.println();

        } catch (SQLException e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    // ── CREATE USER ───────────────────────────────────────────

    private static void createUser(Scanner scanner) {
        System.out.print("Full name: ");
        String fullName = scanner.nextLine().trim();

        System.out.print("Username: ");
        String username = scanner.nextLine().trim();

        System.out.print("Password: ");
        String password = scanner.nextLine().trim();

        System.out.print("Email (optional, press Enter to skip): ");
        String email = scanner.nextLine().trim();

        System.out.print("Role/Title (optional, press Enter to skip): ");
        String role = scanner.nextLine().trim();

        System.out.print("Super admin? (y/n): ");
        boolean isSuperAdmin = scanner.nextLine().trim().equalsIgnoreCase("y");

        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
            System.out.println("❌ Full name, username and password are required.\n");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getConnection();

            // Check username not taken
            PreparedStatement check = conn.prepareStatement(
                "SELECT id FROM users WHERE username = ?"
            );
            check.setString(1, username);
            if (check.executeQuery().next()) {
                System.out.println("❌ Username '" + username + "' is already taken.\n");
                return;
            }

            String hash = PasswordUtil.hash(password);

            PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO users (full_name, username, password_hash, email, role_title, is_super_admin, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, 1)"
            );
            insert.setString(1, fullName);
            insert.setString(2, username);
            insert.setString(3, hash);
            insert.setString(4, email.isEmpty()  ? null : email);
            insert.setString(5, role.isEmpty()   ? "Admin" : role);
            insert.setInt(6, isSuperAdmin ? 1 : 0);
            insert.executeUpdate();

            System.out.println("✅ User '" + username + "' created successfully.");
            System.out.println("   Hash: " + hash);
            System.out.println();

        } catch (SQLException e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    // ── LIST USERS ────────────────────────────────────────────

    private static void listUsers() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT username, full_name, role_title, is_super_admin, is_active, " +
                "failed_attempts, locked_until FROM users ORDER BY id"
            );

            System.out.println();
            System.out.printf("%-20s %-25s %-15s %-6s %-6s %-8s%n",
                "USERNAME", "FULL NAME", "ROLE", "SUPER", "ACTIVE", "LOCKED");
            System.out.println("─".repeat(85));

            while (rs.next()) {
                boolean locked = rs.getTimestamp("locked_until") != null &&
                    rs.getTimestamp("locked_until").toLocalDateTime()
                       .isAfter(java.time.LocalDateTime.now());
                System.out.printf("%-20s %-25s %-15s %-6s %-6s %-8s%n",
                    rs.getString("username"),
                    rs.getString("full_name"),
                    rs.getString("role_title") != null ? rs.getString("role_title") : "—",
                    rs.getInt("is_super_admin") == 1 ? "YES" : "no",
                    rs.getInt("is_active") == 1 ? "YES" : "no",
                    locked ? "LOCKED" : "—"
                );
            }
            System.out.println();

        } catch (SQLException e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    // ── GENERATE HASH ─────────────────────────────────────────

    private static void generateHash(Scanner scanner) {
        System.out.print("Enter password to hash: ");
        String password = scanner.nextLine().trim();
        if (password.isEmpty()) { System.out.println("❌ Empty password.\n"); return; }

        String hash = PasswordUtil.hash(password);
        System.out.println("✅ BCrypt hash for '" + password + "':");
        System.out.println("   " + hash);
        System.out.println();
        System.out.println("You can paste this directly into MySQL:");
        System.out.println("  UPDATE users SET password_hash = '" + hash + "' WHERE username = 'superadmin';");
        System.out.println();
    }
}