package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.models.User;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.PasswordUtil;
import com.afmvfcc.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import java.sql.*;
import java.time.LocalDateTime;

public class LoginController {

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button        loginButton;
    @FXML private Label         errorLabel;
    @FXML private Label         lockLabel;

    private static final int MAX_ATTEMPTS = 5;

    @FXML
    public void initialize() {
        // Allow Enter key on password field to trigger login
        passwordField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) handleLogin();
        });
        usernameField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) passwordField.requestFocus();
        });
    }

    @FXML
    public void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        clearMessages();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter your username and password.");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM users WHERE username = ? AND is_active = 1";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                showError("Invalid username or password.");
                return;
            }

            // Check if account is locked
            Timestamp lockedUntil = rs.getTimestamp("locked_until");
            if (lockedUntil != null &&
                lockedUntil.toLocalDateTime().isAfter(LocalDateTime.now())) {
                showLock("Account locked. Please contact the super admin.");
                AuditLogger.log(-1, "Failed login attempt on locked account: " + username);
                return;
            }

            int userId         = rs.getInt("id");
            String storedHash  = rs.getString("password_hash");
            int failedAttempts = rs.getInt("failed_attempts");

            if (PasswordUtil.verify(password, storedHash)) {
                User user = mapUser(rs);

                if (user.isUsher()) {
                    // Correct credentials, but Usher accounts are app-only —
                    // don't reset failed_attempts or grant a desktop session.
                    showError("This is an Usher account for the mobile app only. " +
                        "Please log in from the AFM VFCC mobile app instead.");
                    AuditLogger.log(userId, "Usher account blocked from desktop login: " + username);
                    return;
                }

                // Successful login — reset failed attempts
                resetFailedAttempts(conn, userId);

                SessionManager.getInstance().login(user, Main::showLogin);
                Main.showMainLayout();

            } else {
                // Wrong password — increment failed attempts
                failedAttempts++;
                if (failedAttempts >= MAX_ATTEMPTS) {
                    lockAccount(conn, userId);
                    showLock("Too many failed attempts. Account locked. Contact super admin.");
                    AuditLogger.log(userId, "Account locked after " + MAX_ATTEMPTS + " failed login attempts.");
                } else {
                    updateFailedAttempts(conn, userId, failedAttempts);
                    int remaining = MAX_ATTEMPTS - failedAttempts;
                    showError("Invalid password. " + remaining + " attempt(s) remaining.");
                }
            }

        } catch (SQLException e) {
            showError("Database error. Please try again.");
            e.printStackTrace();
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private User mapUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setFullName(rs.getString("full_name"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPhone(rs.getString("phone"));
        user.setRoleTitle(rs.getString("role_title"));
        user.setPhotoPath(rs.getString("photo_path"));
        try { user.setAccountRole(rs.getString("account_role")); } catch (SQLException ignored) {}
        user.setSuperAdmin(rs.getInt("is_super_admin") == 1);
        user.setActive(rs.getInt("is_active") == 1);
        return user;
    }

    private void resetFailedAttempts(Connection conn, int userId) throws SQLException {
        String sql = "UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, userId);
        ps.executeUpdate();
    }

    private void updateFailedAttempts(Connection conn, int userId, int attempts) throws SQLException {
        String sql = "UPDATE users SET failed_attempts = ? WHERE id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, attempts);
        ps.setInt(2, userId);
        ps.executeUpdate();
    }

    private void lockAccount(Connection conn, int userId) throws SQLException {
        String sql = "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, MAX_ATTEMPTS);
        ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now().plusYears(100))); // permanent until admin unlocks
        ps.setInt(3, userId);
        ps.executeUpdate();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    private void showLock(String msg) {
        lockLabel.setText(msg);
        lockLabel.setVisible(true);
        loginButton.setDisable(true);
    }

    private void clearMessages() {
        errorLabel.setVisible(false);
        lockLabel.setVisible(false);
        loginButton.setDisable(false);
    }
}
