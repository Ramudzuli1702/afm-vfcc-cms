package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.models.User;
import com.afmvfcc.utils.AuditLogger;
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
    @FXML private Label                     accessDeniedLabel;
    @FXML private VBox                      contentBox;

    private ObservableList<User> allAdmins = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        boolean isSuperAdmin = SessionManager.getInstance().getCurrentUser().isSuperAdmin();
        if (!isSuperAdmin) {
            if (accessDeniedLabel != null) { accessDeniedLabel.setVisible(true); accessDeniedLabel.setManaged(true); }
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

        colAdminActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn   = new Button("Edit");
            private final Button unlockBtn = new Button("Unlock");
            private final Button deleteBtn = new Button("Delete");
            private final HBox   box       = new HBox(6, editBtn, unlockBtn, deleteBtn);
            {
                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setStyle("-fx-padding:4 8;-fx-font-size:11px;");
                unlockBtn.getStyleClass().add("btn-secondary");
                unlockBtn.setStyle("-fx-padding:4 8;-fx-font-size:11px;");
                deleteBtn.getStyleClass().add("btn-danger");
                deleteBtn.setStyle("-fx-padding:4 8;-fx-font-size:11px;");
                box.setAlignment(Pos.CENTER_LEFT);

                editBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        openAdminDialog(getTableView().getItems().get(idx));
                });
                unlockBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        unlockAdmin(getTableView().getItems().get(idx));
                });
                deleteBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        deleteAdmin(getTableView().getItems().get(idx));
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                    User u = getTableView().getItems().get(getIndex());
                    unlockBtn.setVisible(u.getLockedUntil() != null && u.getLockedUntil().isAfter(java.time.LocalDateTime.now()));
                    unlockBtn.setManaged(u.getLockedUntil() != null && u.getLockedUntil().isAfter(java.time.LocalDateTime.now()));
                    deleteBtn.setVisible(!u.isSuperAdmin());
                    deleteBtn.setManaged(!u.isSuperAdmin());
                }
                setGraphic(empty ? null : box);
            }
        });

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

    // -- Add / Edit dialog -------------------------------------
    private void openAdminDialog(User existing) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(existing == null ? "Add Admin" : "Edit Admin: " + existing.getUsername());
        stage.setMinWidth(480);

        // -- Header --
        HBox header = new HBox();
        header.setStyle("-fx-background-color:#FFFFFF;-fx-padding:18 24;" +
            "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        Label title = new Label(existing == null ? "Add New Admin" : "Edit Admin");
        title.setStyle("-fx-font-size:16px;-fx-font-weight:700;-fx-text-fill:#1E2130;");
        header.getChildren().add(title);

        // -- Form --
        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(14);
        form.setPadding(new Insets(22, 24, 8, 24));

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
        accountTypeHint.setStyle("-fx-font-size:10px;-fx-text-fill:#9099AA;");
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

        String[] labels = {"FULL NAME *", "USERNAME *",
            existing == null ? "PASSWORD *" : "NEW PASSWORD",
            "EMAIL", "PHONE", "ROLE / TITLE", "ACCOUNT TYPE *", "", "STATUS"};
        javafx.scene.Node[] controls = {fullNameField, usernameField, passField,
            emailField, phoneField, roleField, accountTypeCombo, accountTypeHint, statusCombo};

        for (int i = 0; i < labels.length; i++) {
            Label lbl = new Label(labels[i]);
            lbl.setStyle("-fx-font-size:10px;-fx-font-weight:700;-fx-text-fill:#9099AA;");
            form.add(lbl,                     0, i);
            form.add(controls[i],             1, i);
            GridPane.setHgrow(controls[i], Priority.ALWAYS);
        }

        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill:#D94040;-fx-font-size:11px;");
        errLabel.setVisible(false);
        form.add(errLabel, 0, labels.length, 2, 1);

        // -- Footer --
        Button saveBtn   = new Button(existing == null ? "Add Admin" : "Save Changes");
        saveBtn.getStyleClass().add("btn-primary");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        HBox footer = new HBox(10, cancelBtn, saveBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color:#FFFFFF;-fx-padding:14 24;" +
            "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");

        // -- Root --
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#F5F6FA;");

        // ScrollPane wraps the form so it never pushes the footer off screen
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:transparent;-fx-background:transparent;" +
            "-fx-border-color:transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.getChildren().addAll(header, scroll, footer);

        cancelBtn.setOnAction(e -> stage.close());

        saveBtn.setOnAction(e -> {
            errLabel.setVisible(false);
            String name = fullNameField.getText().trim();
            String user = usernameField.getText().trim();
            String pass = passField.getText();

            if (name.isEmpty() || user.isEmpty()) {
                errLabel.setText("Full name and username are required.");
                errLabel.setVisible(true); return;
            }
            if (existing == null && pass.isEmpty()) {
                errLabel.setText("Password is required for new admin.");
                errLabel.setVisible(true); return;
            }

            try {
                Connection conn = DatabaseConnection.getConnection();
                if (existing == null) {
                    PreparedStatement check = conn.prepareStatement(
                        "SELECT id FROM users WHERE username=?");
                    check.setString(1, user);
                    if (check.executeQuery().next()) {
                        errLabel.setText("That username is already taken.");
                        errLabel.setVisible(true); return;
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
                stage.close();
            } catch (SQLException ex) {
                errLabel.setText("Database error: " + ex.getMessage());
                errLabel.setVisible(true);
                ex.printStackTrace();
                ToastManager.error("Failed to save admin: " + ex.getMessage());
            }
        });

        Scene scene = new Scene(root, 500, 520);
        scene.getStylesheets().add(
            getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    // -- Unlock ------------------------------------------------
    private void unlockAdmin(User u) {
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
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to unlock account: " + e.getMessage());
        }
    }

    // -- Delete ------------------------------------------------
    private void deleteAdmin(User u) {
        if (u.isSuperAdmin()) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Delete Admin");
        confirm.setHeaderText("Delete " + u.getFullName() + "?");
        confirm.setContentText("This will permanently remove the admin account.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            try {
                Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id=?");
                ps.setInt(1, u.getId());
                ps.executeUpdate();
                AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    "Deleted admin: " + u.getUsername());
                loadAdmins();
                ToastManager.success("Admin \"" + u.getFullName() + "\" deleted.");
            } catch (SQLException e) {
                e.printStackTrace();
                ToastManager.error("Failed to delete admin: " + e.getMessage());
            }
        });
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
