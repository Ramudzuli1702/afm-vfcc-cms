package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.models.User;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.Dialogs;
import com.afmvfcc.utils.DocumentViewer;
import com.afmvfcc.utils.FormBuilder;
import com.afmvfcc.utils.TableActions;
import com.afmvfcc.utils.PasswordUtil;
import com.afmvfcc.utils.SessionManager;
import com.afmvfcc.utils.ToastManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.*;

public class AdminsController {

    @FXML private TableView<User>           adminsTable;
    @FXML private TableColumn<User, String> colAdminName, colAdminUsername, colAdminType,
                                             colAdminRole, colAdminEmail, colAdminStatus;
    @FXML private TableColumn<User, Void>   colAdminActions;
    @FXML private TextField                 adminSearchField;
    @FXML private HBox                      accessDeniedBox;
    @FXML private VBox                      contentBox;
    @FXML private Label                     statAdmins, statUshers, statInactive, statActionsToday;

    private ObservableList<User> allAdmins = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        boolean isSuperAdmin = SessionManager.getInstance().getCurrentUser().isSuperAdmin();
        if (!isSuperAdmin) {
            if (accessDeniedBox != null)   { accessDeniedBox.setVisible(true);   accessDeniedBox.setManaged(true); }
            if (contentBox != null)        { contentBox.setVisible(false);        contentBox.setManaged(false); }
            return;
        }
        setupTable();
        loadAdmins();
    }

    private void setupTable() {
        colAdminName.setCellValueFactory(d     -> new SimpleStringProperty(d.getValue().getFullName()));
        colAdminUsername.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsername()));
        colAdminType.setCellValueFactory(d     -> new SimpleStringProperty(
            d.getValue().isUsher() ? "Usher" : "Admin"));
        colAdminType.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label(item);
                badge.getStyleClass().add("Usher".equals(item) ? "badge-pending" : "badge-flat");
                setGraphic(badge); setText(null);
            }
        });
        colAdminRole.setCellValueFactory(d     -> new SimpleStringProperty(
            d.getValue().getRoleTitle() != null ? d.getValue().getRoleTitle() : "-"));
        colAdminEmail.setCellValueFactory(d    -> new SimpleStringProperty(
            d.getValue().getEmail() != null ? d.getValue().getEmail() : "-"));

        colAdminStatus.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().isActive() ? "Active" : "Inactive"));
        colAdminStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label(item);
                badge.getStyleClass().add("Active".equals(item) ? "badge-active" : "badge-inactive");
                setGraphic(badge); setText(null);
            }
        });

        TableActions.viewOnly(adminsTable, colAdminActions, this::showAccount);

        adminsTable.setItems(allAdmins);
    }

    private void loadAdmins() {
        allAdmins.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT * FROM users ORDER BY is_super_admin DESC, full_name ASC");
            while (rs.next()) allAdmins.add(mapUser(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        applySearch();
        refreshStats();
    }

    private void refreshStats() {
        statAdmins.setText(String.valueOf(allAdmins.stream().filter(u -> !u.isUsher()).count()));
        statUshers.setText(String.valueOf(allAdmins.stream().filter(User::isUsher).count()));
        statInactive.setText(String.valueOf(allAdmins.stream().filter(u -> !u.isActive()).count()));
        try {
            ResultSet rs = DatabaseConnection.getConnection().createStatement().executeQuery(
                "SELECT COUNT(*) FROM audit_log WHERE performed_at >= CURDATE()");
            statActionsToday.setText(rs.next() ? String.valueOf(rs.getInt(1)) : "0");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @FXML public void handleSearch() { applySearch(); }

    private void applySearch() {
        String q = adminSearchField != null ? adminSearchField.getText().toLowerCase().trim() : "";
        if (q.isEmpty()) { adminsTable.setItems(allAdmins); return; }
        ObservableList<User> filtered = FXCollections.observableArrayList();
        for (User u : allAdmins) {
            if (u.getFullName().toLowerCase().contains(q) ||
                u.getUsername().toLowerCase().contains(q)) filtered.add(u);
        }
        adminsTable.setItems(filtered);
    }

    @FXML public void handleAddAdmin() { openAdminDialog(null); }

    private static boolean isLocked(User u) {
        return u.getLockedUntil() != null && u.getLockedUntil().isAfter(java.time.LocalDateTime.now());
    }

    private void showAccount(User u) {
        String type = u.isSuperAdmin() ? "Super Admin" : u.isUsher() ? "Usher (mobile app only)" : "Admin";
        DocumentViewer v = new DocumentViewer("System Account", u.getFullName())
            .icon("fas-user-shield")
            .status(isLocked(u) ? "Locked" : u.isActive() ? "Active" : "Inactive",
                    isLocked(u) ? "badge-overdue" : u.isActive() ? "badge-active" : "badge-inactive")
            .width(640)
            .meta("Username",     u.getUsername())
            .meta("Account Type", type)
            .meta("Role / Title", u.getRoleTitle())
            .meta("Email",        u.getEmail())
            .meta("Phone",        u.getPhone())
            .meta("Locked Until", isLocked(u)
                ? u.getLockedUntil().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) : null);
        if (isLocked(u))
            v.closingAction("Unlock", "fas-unlock", "btn-secondary", () -> unlockAdmin(u));
        v.onEdit(() -> openAdminDialog(u));
        if (!u.isSuperAdmin()) v.onDelete(() -> deleteAdmin(u));
        v.show();
    }

    // -- Add / Edit dialog -------------------------------------
    private void openAdminDialog(User existing) {
        FormBuilder f = new FormBuilder(existing == null ? "Add Account" : "Edit Account").icon("fas-user-cog");

        TextField fullNameField = field("Enter full name");
        TextField usernameField = field("Username");
        PasswordField passField = new PasswordField();
        passField.setPromptText(existing == null ? "Password (required)" : "New password (blank = no change)");
        passField.getStyleClass().add("form-field");
        passField.setMaxWidth(Double.MAX_VALUE);
        TextField emailField    = field("email@example.com");
        TextField phoneField    = field("e.g. 0712345678");
        TextField roleField     = field("e.g. Secretary");
        ComboBox<String> accountTypeCombo = new ComboBox<>();
        accountTypeCombo.getStyleClass().add("form-combo");
        accountTypeCombo.getItems().addAll("Admin", "Usher");
        accountTypeCombo.setValue("Admin");
        accountTypeCombo.setMaxWidth(Double.MAX_VALUE);
        Label accountTypeHint = new Label(
            "Admin: full desktop + mobile app access.  Usher: mobile app only (attendance/guests) — cannot log into the desktop system.");
        accountTypeHint.setWrapText(true);
        accountTypeHint.setStyle("-fx-font-size:11px;-fx-text-fill:#9099AA;");
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getStyleClass().add("form-combo");
        statusCombo.getItems().addAll("Active", "Inactive");
        statusCombo.setValue("Active");
        statusCombo.setMaxWidth(Double.MAX_VALUE);

        if (existing != null) {
            fullNameField.setText(existing.getFullName());
            usernameField.setText(existing.getUsername());
            usernameField.setDisable(existing.isSuperAdmin());
            emailField.setText(nvl(existing.getEmail()));
            phoneField.setText(nvl(existing.getPhone()));
            roleField.setText(nvl(existing.getRoleTitle()));
            accountTypeCombo.setValue(existing.isUsher() ? "Usher" : "Admin");
            accountTypeCombo.setDisable(existing.isSuperAdmin());
            statusCombo.setValue(existing.isActive() ? "Active" : "Inactive");
        }

        f.section("Person")
         .field("FULL NAME *", fullNameField)
         .row("EMAIL", emailField, "PHONE", phoneField)
         .field("ROLE / TITLE", roleField);
        f.section("Login")
         .row("USERNAME *", usernameField, existing == null ? "PASSWORD *" : "NEW PASSWORD", passField);
        if (existing != null) f.hint("Leave the password blank to keep the current one.");
        f.section("Access")
         .row("ACCOUNT TYPE *", accountTypeCombo, "STATUS", statusCombo)
         .node(accountTypeHint);

        Button saveBtn = f.saveButton(existing == null ? "Add Account" : "Save Changes");

        saveBtn.setOnAction(e -> {
            f.clearError();
            String name = fullNameField.getText().trim();
            String user = usernameField.getText().trim();
            String pass = passField.getText();

            if (name.isEmpty() || user.isEmpty()) {
                f.showError("Full name and username are required."); return;
            }
            if (existing == null && pass.isEmpty()) {
                f.showError("Password is required for new admin."); return;
            }

            try {
                Connection conn = DatabaseConnection.getConnection();
                if (existing == null) {
                    PreparedStatement check = conn.prepareStatement(
                        "SELECT id FROM users WHERE username=?");
                    check.setString(1, user);
                    if (check.executeQuery().next()) {
                        f.showError("That username is already taken."); return;
                    }
                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO users (full_name, username, password_hash, email, phone, role_title, account_role, is_active) " +
                        "VALUES (?,?,?,?,?,?,?,?)");
                    ps.setString(1, name);
                    ps.setString(2, user);
                    ps.setString(3, PasswordUtil.hash(pass));
                    ps.setString(4, emailField.getText().trim());
                    ps.setString(5, phoneField.getText().trim());
                    ps.setString(6, roleField.getText().trim());
                    ps.setString(7, "Usher".equals(accountTypeCombo.getValue()) ? "usher" : "admin");
                    ps.setInt(8, "Active".equals(statusCombo.getValue()) ? 1 : 0);
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Created admin: " + user);
                    ToastManager.success("Admin \"" + name + "\" added successfully.");
                } else {
                    String sql = "UPDATE users SET full_name=?, email=?, phone=?, " +
                        "role_title=?, account_role=?, is_active=?" +
                        (!pass.isEmpty() ? ", password_hash=?" : "") + " WHERE id=?";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, name);
                    ps.setString(2, emailField.getText().trim());
                    ps.setString(3, phoneField.getText().trim());
                    ps.setString(4, roleField.getText().trim());
                    ps.setString(5, existing.isSuperAdmin() ? "admin" :
                        ("Usher".equals(accountTypeCombo.getValue()) ? "usher" : "admin"));
                    ps.setInt(6, "Active".equals(statusCombo.getValue()) ? 1 : 0);
                    int idx = 7;
                    if (!pass.isEmpty()) ps.setString(idx++, PasswordUtil.hash(pass));
                    ps.setInt(idx, existing.getId());
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Updated admin: " + existing.getUsername());
                    ToastManager.success("Admin \"" + name + "\" updated successfully.");
                }
                loadAdmins();
                f.close();
            } catch (SQLException ex) {
                f.showError("Database error: " + ex.getMessage());
                ex.printStackTrace();
                ToastManager.error("Failed to save admin: " + ex.getMessage());
            }
        });

        f.show(600);
    }

    // -- Unlock ------------------------------------------------
    /** Clears the lockout. Returns true on success. */
    private boolean unlockAdmin(User u) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET failed_attempts=0, locked_until=NULL WHERE id=?");
            ps.setInt(1, u.getId());
            ps.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                "Unlocked admin account: " + u.getUsername());
            loadAdmins();
            ToastManager.success("Account unlocked for " + u.getUsername() + ".");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to unlock account: " + e.getMessage());
            return false;
        }
    }

    // -- Delete ------------------------------------------------
    /** Confirms, then deletes the account. Returns true if deleted. */
    private boolean deleteAdmin(User u) {
        if (u.isSuperAdmin()) return false;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Delete Admin");
        confirm.setHeaderText("Delete " + u.getFullName() + "?");
        confirm.setContentText("This will permanently remove the admin account.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id=?");
            ps.setInt(1, u.getId());
            ps.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                "Deleted admin: " + u.getUsername());
            loadAdmins();
            ToastManager.success("Admin \"" + u.getFullName() + "\" deleted.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to delete admin: " + e.getMessage());
            return false;
        }
    }

    // -- Helpers -----------------------------------------------
    private TextField field(String prompt) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        f.getStyleClass().add("form-field");
        f.setMaxWidth(Double.MAX_VALUE);
        return f;
    }

    private String nvl(String s) { return s != null ? s : ""; }

    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setFullName(rs.getString("full_name"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setPhone(rs.getString("phone"));
        u.setRoleTitle(rs.getString("role_title"));
        try { u.setAccountRole(rs.getString("account_role")); } catch (SQLException ignored) {}
        u.setSuperAdmin(rs.getInt("is_super_admin") == 1);
        u.setActive(rs.getInt("is_active") == 1);
        try { java.sql.Timestamp ts = rs.getTimestamp("locked_until"); if (ts != null) u.setLockedUntil(ts.toLocalDateTime()); } catch (Exception ignored) {}
        return u;
    }
}
