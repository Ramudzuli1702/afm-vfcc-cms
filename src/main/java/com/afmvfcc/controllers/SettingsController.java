package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.EmailService;
import com.afmvfcc.utils.GitHubSync;
import com.afmvfcc.utils.NetworkUtils;
import com.afmvfcc.utils.QrCodeGenerator;
import com.afmvfcc.utils.SessionManager;
import com.afmvfcc.utils.SmsService;
import com.afmvfcc.utils.ToastManager;
import com.afmvfcc.utils.YouTubeUploader;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import java.io.*;
import java.nio.file.Files;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SettingsController {

    // Backup
    @FXML private TextField  backupPathField;
    // Website
    @FXML private TextField  websiteFolderField;
    @FXML private Label      backupStatusLabel;
    @FXML private ListView<String> backupHistoryList;

    // Email
    @FXML private TextField     smtpHostField;
    @FXML private TextField     smtpPortField;
    @FXML private TextField     smtpUserField;
    @FXML private PasswordField smtpPassField;
    @FXML private Label         emailTestLabel;

    // SMS
    @FXML private TextField     bulkSmsKeyField;
    @FXML private PasswordField bulkSmsSecretField;
    @FXML private TextField     bulkSmsSenderField;
    @FXML private Label         smsTestLabel;

    // Broadcast / YouTube
    @FXML private TextField broadcastDownloadFolderField;
    @FXML private Label     youtubeAuthStatusLabel;
    @FXML private Label     secretsStatusLabel;

    // Announcements (WhatsApp + Facebook)
    @FXML private TextField whatsappTokenField;
    @FXML private TextField whatsappPhoneIdField;
    @FXML private TextField whatsappRecipientField;
    @FXML private TextField facebookPageTokenField;
    @FXML private TextField facebookPageIdField;
    @FXML private Label     annTestStatus;

    // GitHub
    @FXML private PasswordField githubTokenField;
    @FXML private TextField     githubOwnerField;
    @FXML private TextField     githubRepoField;
    @FXML private TextField     githubBranchField;
    @FXML private TextField     websiteCustomDomainField;
    @FXML private Label         githubStatusLabel;

    // Mobile app connection
    @FXML private ImageView connectQrImage;
    @FXML private Label     serverAddressLabel;
    @FXML private Label     serverAdapterLabel;
    @FXML private Label     hotspotNoticeLabel;

    // Connected devices
    @FXML private TableView<String[]>           devicesTable;
    @FXML private TableColumn<String[], String> colDeviceUser;
    @FXML private TableColumn<String[], String> colDeviceRole;
    @FXML private TableColumn<String[], String> colDeviceName;
    @FXML private TableColumn<String[], String> colDeviceSince;
    @FXML private TableColumn<String[], Void>   colDeviceActions;

    private static final int APP_SERVER_PORT = 8080;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    @FXML
    public void initialize() {
        loadSettings();
        refreshYoutubeAuthStatus();
        refreshSecretsStatus();
        loadBackupHistory();
        setupDevicesTable();
        loadServerAddress();
        loadConnectedDevices();
    }

    // ── MOBILE APP CONNECTION ─────────────────────────────────────────

    @FXML
    public void handleRefreshServerAddress() {
        loadServerAddress();
        loadConnectedDevices();
    }

    @FXML
    public void handleCopyServerAddress() {
        String text = serverAddressLabel.getText();
        if (text == null || text.isBlank() || "Detecting...".equals(text)) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        ToastManager.success("Server address copied to clipboard.");
    }

    private void loadServerAddress() {
        NetworkUtils.ServerAddress addr = NetworkUtils.findServerAddress();
        if (addr == null) {
            serverAddressLabel.setText("No active network connection found");
            serverAdapterLabel.setText("");
            hotspotNoticeLabel.setText(
                "Connect to WiFi, or turn on Mobile Hotspot in Windows Settings, then click Refresh.");
            connectQrImage.setImage(null);
            return;
        }

        String display = addr.ip + ":" + APP_SERVER_PORT;
        serverAddressLabel.setText(display);
        serverAdapterLabel.setText("via " + addr.adapterName);

        if (addr.isHotspotAdapter) {
            hotspotNoticeLabel.setText("✓ Mobile Hotspot is on — phones can connect by joining it and scanning this code.");
        } else {
            hotspotNoticeLabel.setText(
                "Mobile Hotspot is off, so this is your regular WiFi address — phones must be on the same WiFi network. " +
                "Turn on Mobile Hotspot in Windows Settings for a dedicated connection, then click Refresh.");
        }

        String qrPayload = "afmvfcc://connect?ip=" + addr.ip + "&port=" + APP_SERVER_PORT;
        connectQrImage.setImage(QrCodeGenerator.generate(qrPayload, 320));
    }

    // ── CONNECTED DEVICES ──────────────────────────────────────────────

    private void setupDevicesTable() {
        colDeviceUser.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[0]));
        colDeviceRole.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[1]));
        colDeviceRole.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label(item);
                badge.getStyleClass().add("Usher".equals(item) ? "badge-pending" : "badge-flat");
                setGraphic(badge); setText(null);
            }
        });
        colDeviceName.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[2]));
        colDeviceSince.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[3]));

        colDeviceActions.setCellFactory(col -> new TableCell<>() {
            private final Button revokeBtn = new Button("Revoke");
            {
                revokeBtn.getStyleClass().add("btn-danger");
                revokeBtn.setStyle("-fx-font-size:11px;-fx-padding:4 10;");
                revokeBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    revokeDevice(Integer.parseInt(row[4]), row[0]);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : revokeBtn);
            }
        });
    }

    private void loadConnectedDevices() {
        List<String[]> rows = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT t.id, t.stored_user, t.device_name, t.created_at, " +
                "IFNULL(u.account_role,'admin') AS account_role " +
                "FROM app_tokens t LEFT JOIN users u ON u.id = t.user_id " +
                "WHERE t.is_active = 1 ORDER BY t.created_at DESC"
            );
            while (rs.next()) {
                Timestamp created = rs.getTimestamp("created_at");
                rows.add(new String[]{
                    rs.getString("stored_user") != null ? rs.getString("stored_user") : "Unknown",
                    "usher".equalsIgnoreCase(rs.getString("account_role")) ? "Usher" : "Admin",
                    rs.getString("device_name") != null ? rs.getString("device_name") : "Unknown device",
                    created != null ? created.toLocalDateTime().format(FMT) : "",
                    String.valueOf(rs.getInt("id"))
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        devicesTable.setItems(FXCollections.observableArrayList(rows));
    }

    private void revokeDevice(int tokenId, String userName) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Revoke Device");
        confirm.setHeaderText("Sign out " + userName + " on this device?");
        confirm.setContentText("They will need to log in again on the app to reconnect.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            try {
                Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                    "UPDATE app_tokens SET is_active = 0 WHERE id = ?");
                ps.setInt(1, tokenId);
                ps.executeUpdate();
                AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    "Revoked app device session for: " + userName);
                loadConnectedDevices();
                ToastManager.success("Device signed out successfully.");
            } catch (SQLException e) {
                e.printStackTrace();
                ToastManager.error("Failed to revoke device: " + e.getMessage());
            }
        });
    }

    // ── LOAD / SAVE SETTINGS ─────────────────────────────────────────
    private void loadSettings() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT setting_key, setting_value FROM system_settings");
            while (rs.next()) {
                String key = rs.getString("setting_key");
                String val = rs.getString("setting_value");
                if (val == null) continue;
                switch (key) {
                    case "smtp_host"       -> smtpHostField.setText(val);
                    case "smtp_port"       -> smtpPortField.setText(val);
                    case "smtp_user"       -> smtpUserField.setText(val);
                    case "smtp_password"   -> smtpPassField.setText(val);
                    case "bulksms_key"     -> bulkSmsKeyField.setText(val);
                    case "bulksms_secret"  -> bulkSmsSecretField.setText(val);
                    case "bulksms_sender"  -> bulkSmsSenderField.setText(val);
                    case "backup_location" -> backupPathField.setText(val);
                    case "website_folder"  -> { if (websiteFolderField != null) websiteFolderField.setText(val); }
                    case "broadcast_download_folder" -> broadcastDownloadFolderField.setText(val);
                    case "whatsapp_token"      -> { if (whatsappTokenField     != null) whatsappTokenField.setText(val); }
                    case "whatsapp_phone_id"   -> { if (whatsappPhoneIdField   != null) whatsappPhoneIdField.setText(val); }
                    case "whatsapp_recipient"  -> { if (whatsappRecipientField != null) whatsappRecipientField.setText(val); }
                    case "facebook_page_token" -> { if (facebookPageTokenField != null) facebookPageTokenField.setText(val); }
                    case "facebook_page_id"    -> { if (facebookPageIdField    != null) facebookPageIdField.setText(val); }
                    case "github_token"          -> { if (githubTokenField          != null) githubTokenField.setText(val); }
                    case "github_owner"          -> { if (githubOwnerField          != null) githubOwnerField.setText(val); }
                    case "github_repo"           -> { if (githubRepoField           != null) githubRepoField.setText(val); }
                    case "github_branch"         -> { if (githubBranchField         != null) githubBranchField.setText(val); }
                    case "website_custom_domain" -> { if (websiteCustomDomainField  != null) websiteCustomDomainField.setText(val); }
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @FXML
    public void handleSaveAll() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            save(conn, "smtp_host",       smtpHostField.getText().trim());
            save(conn, "smtp_port",       smtpPortField.getText().trim());
            save(conn, "smtp_user",       smtpUserField.getText().trim());
            save(conn, "smtp_password",   smtpPassField.getText().trim());
            save(conn, "bulksms_key",     bulkSmsKeyField.getText().trim());
            save(conn, "bulksms_secret",  bulkSmsSecretField.getText().trim());
            save(conn, "bulksms_sender",  bulkSmsSenderField.getText().trim());
            save(conn, "backup_location", backupPathField.getText().trim());
            if (websiteFolderField != null)
                save(conn, "website_folder", websiteFolderField.getText().trim());
            if (broadcastDownloadFolderField != null)
                save(conn, "broadcast_download_folder", broadcastDownloadFolderField.getText().trim());
            if (whatsappTokenField     != null) save(conn, "whatsapp_token",      whatsappTokenField.getText().trim());
            if (whatsappPhoneIdField   != null) save(conn, "whatsapp_phone_id",   whatsappPhoneIdField.getText().trim());
            if (whatsappRecipientField != null) save(conn, "whatsapp_recipient",  whatsappRecipientField.getText().trim());
            if (facebookPageTokenField != null) save(conn, "facebook_page_token", facebookPageTokenField.getText().trim());
            if (facebookPageIdField    != null) save(conn, "facebook_page_id",    facebookPageIdField.getText().trim());
            if (githubTokenField         != null) save(conn, "github_token",          githubTokenField.getText().trim());
            if (githubOwnerField         != null) save(conn, "github_owner",          githubOwnerField.getText().trim());
            if (githubRepoField          != null) save(conn, "github_repo",           githubRepoField.getText().trim());
            if (githubBranchField        != null) save(conn, "github_branch",         githubBranchField.getText().trim());
            if (websiteCustomDomainField != null) save(conn, "website_custom_domain", websiteCustomDomainField.getText().trim());

            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(), "Updated system settings.");
            showAlert(Alert.AlertType.INFORMATION, "Settings Saved", "All settings have been saved successfully.");
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Save Failed", e.getMessage());
        }
    }

    private void save(Connection conn, String key, String value) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO system_settings (setting_key, setting_value) VALUES (?,?) " +
                "ON DUPLICATE KEY UPDATE setting_value=?");
        ps.setString(1, key); ps.setString(2, value); ps.setString(3, value);
        ps.executeUpdate();
    }

    // ── BACKUP ────────────────────────────────────────────────────────
    @FXML
    public void handleBrowseBackup() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Backup Folder");
        String current = backupPathField.getText().trim();
        if (!current.isEmpty()) { File f = new File(current); if (f.exists()) chooser.setInitialDirectory(f); }
        File dir = chooser.showDialog(backupPathField.getScene().getWindow());
        if (dir != null) backupPathField.setText(dir.getAbsolutePath());
    }

    @FXML
    public void handleBackupNow() {
        String backupDir = backupPathField.getText().trim();
        if (backupDir.isEmpty()) { showBackupStatus("Please set a backup location first.", false); return; }
        File dir = new File(backupDir);
        if (!dir.exists()) dir.mkdirs();
        if (!dir.canWrite()) { showBackupStatus("Cannot write to selected folder.", false); return; }
        showBackupStatus("Backing up…", true);
        javafx.concurrent.Task<String> task = new javafx.concurrent.Task<>() {
            @Override protected String call() throws Exception {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String filename  = "AFM_VFCC_Backup_" + timestamp + ".sql";
                File outFile     = new File(dir, filename);
                String mysqldump = findMysqldump();
                if (mysqldump != null) {
                    ProcessBuilder pb = new ProcessBuilder(mysqldump,
                            "--host=127.0.0.1","--user=afm_app","--password=Ramos1702.",
                            "--single-transaction","--routines","--triggers","AFM_VFCC_CMS");
                    pb.redirectOutput(outFile); pb.redirectErrorStream(false);
                    int exitCode = pb.start().waitFor();
                    if (exitCode != 0) { outFile.delete(); manualExport(outFile, "AFM_VFCC_CMS"); }
                } else {
                    manualExport(outFile, "AFM_VFCC_CMS");
                }
                save(DatabaseConnection.getConnection(), "last_backup", LocalDateTime.now().format(FMT));
                return outFile.getName() + " (" + outFile.length() / 1024 + " KB)";
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            showBackupStatus("✓ Backup completed: " + task.getValue(), true);
            loadBackupHistory();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    "Database backup created in: " + backupDir);
        }));
        task.setOnFailed(e -> Platform.runLater(() ->
                showBackupStatus("Backup failed: " + task.getException().getMessage(), false)));
        new Thread(task).start();
    }

    // ── RESTORE BACKUP ────────────────────────────────────────────────

    /**
     * Lets the user pick a .sql backup file and replays it against the
     * live database.  Shows a stern confirmation dialog first so no one
     * accidentally overwrites good data.
     *
     * Strategy:
     *  1. Try mysql CLI (fastest, handles large files, proper multi-statement).
     *  2. Fall back to JDBC statement-by-statement execution so it works even
     *     on machines that don't have mysql on PATH or in the usual locations.
     */
    @FXML
    public void handleRestoreBackup() {
        // Step 1 — pick file
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Backup File to Restore");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SQL Backup Files (*.sql)", "*.sql"));
        String current = backupPathField.getText().trim();
        if (!current.isEmpty()) {
            File f = new File(current);
            if (f.exists()) chooser.setInitialDirectory(f);
        }
        File sqlFile = chooser.showOpenDialog(backupPathField.getScene().getWindow());
        if (sqlFile == null) return;

        // Step 2 — warn the user loudly
        Alert warn = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(warn.getDialogPane());
        warn.setTitle("Restore Database");
        warn.setHeaderText("⚠  This will REPLACE all current data!");
        warn.setContentText(
                "Restoring from:\n" + sqlFile.getName() + "\n\n" +
                "All existing records will be overwritten by the backup.\n" +
                "This cannot be undone.\n\n" +
                "Are you sure you want to continue?");

        ButtonType restoreBtn = new ButtonType("Yes, Restore Now", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn  = new ButtonType("Cancel",           ButtonBar.ButtonData.CANCEL_CLOSE);
        warn.getButtonTypes().setAll(restoreBtn, cancelBtn);

        warn.showAndWait().ifPresent(response -> {
            if (response != restoreBtn) return;

            showBackupStatus("Restoring… please wait.", true);

            javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
                @Override
                protected Void call() throws Exception {
                    String mysql = findMysqlCli();
                    if (mysql != null) {
                        restoreViaCli(mysql, sqlFile);
                    } else {
                        restoreViaJdbc(sqlFile);
                    }
                    return null;
                }
            };

            task.setOnSucceeded(e -> Platform.runLater(() -> {
                showBackupStatus("✓ Database restored successfully from: " + sqlFile.getName(), true);
                AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Database restored from backup: " + sqlFile.getAbsolutePath());
                showAlert(Alert.AlertType.INFORMATION, "Restore Complete",
                        "The database has been restored successfully.\n\n" +
                        "Please restart the application to reload all data.");
            }));

            task.setOnFailed(e -> Platform.runLater(() -> {
                String msg = task.getException() != null
                        ? task.getException().getMessage() : "Unknown error";
                showBackupStatus("✗ Restore failed: " + msg, false);
                showAlert(Alert.AlertType.ERROR, "Restore Failed",
                        "The restore could not be completed:\n\n" + msg);
            }));

            new Thread(task).start();
        });
    }

    /**
     * Restore using the mysql command-line client (preferred — handles
     * stored procedures, triggers, and large files correctly).
     */
    private void restoreViaCli(String mysqlPath, File sqlFile) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                mysqlPath,
                "--host=127.0.0.1",
                "--user=afm_app",
                "--password=Ramos1702.",
                "AFM_VFCC_CMS"
        );
        pb.redirectInput(sqlFile);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Capture any error output
        StringBuilder errorOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null)
                errorOutput.append(line).append("\n");
        }

        int exitCode = process.waitFor();
        if (exitCode != 0)
            throw new Exception("mysql exited with code " + exitCode + ":\n" + errorOutput);
    }

    /**
     * Fallback restore via JDBC — reads the .sql file line by line and
     * executes each complete statement.  Works when mysql is not installed.
     *
     * Handles:
     *  - Single-line and multi-line statements
     *  - -- comments and /* block comments
     *  - DELIMITER changes (skipped — not needed for standard mysqldump output)
     *  - SET FOREIGN_KEY_CHECKS = 0/1
     */
    private void restoreViaJdbc(File sqlFile) throws Exception {
        String content = Files.readString(sqlFile.toPath());

        // Strip block comments /* ... */
        content = content.replaceAll("/\\*.*?\\*/", "");

        // Split on semicolons to get individual statements
        String[] rawStatements = content.split(";");

        Connection conn = DatabaseConnection.getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try (Statement stmt = conn.createStatement()) {
            // Disable FK checks for the duration of the restore
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");

            for (String raw : rawStatements) {
                // Strip inline -- comments and blank lines
                String sql = stripLineComments(raw).trim();
                if (sql.isEmpty()) continue;

                // Skip pure DELIMITER directives (not valid JDBC SQL)
                if (sql.toUpperCase().startsWith("DELIMITER")) continue;

                try {
                    stmt.execute(sql);
                } catch (SQLException ex) {
                    // Re-enable FK checks before rolling back
                    try { stmt.execute("SET FOREIGN_KEY_CHECKS = 1"); } catch (Exception ignored) {}
                    conn.rollback();
                    conn.setAutoCommit(originalAutoCommit);
                    throw new Exception("SQL error on statement:\n" +
                            sql.substring(0, Math.min(120, sql.length())) +
                            "\n\nError: " + ex.getMessage(), ex);
                }
            }

            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
            conn.commit();
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    /**
     * Strips -- line comments from a SQL string.
     */
    private String stripLineComments(String sql) {
        StringBuilder sb = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("--"))
                sb.append(line).append("\n");
        }
        return sb.toString();
    }

    /**
     * Finds the mysql CLI client (distinct from mysqldump).
     */
    private String findMysqlCli() {
        try { new ProcessBuilder("mysql", "--version").start().waitFor(); return "mysql"; }
        catch (Exception ignored) {}
        String[] paths = {
            "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe",
            "C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe",
            "C:\\Program Files\\MySQL\\MySQL Server 9.0\\bin\\mysql.exe",
            "C:\\xampp\\mysql\\bin\\mysql.exe",
            "C:\\wamp64\\bin\\mysql\\mysql8.0.31\\bin\\mysql.exe",
            "/usr/local/bin/mysql", "/opt/homebrew/bin/mysql", "/usr/local/mysql/bin/mysql",
        };
        for (String p : paths) if (p != null && new File(p).exists()) return p;
        return null;
    }

    // ── MYSQLDUMP ─────────────────────────────────────────────────────
    private String findMysqldump() {
        try { new ProcessBuilder("mysqldump", "--version").start().waitFor(); return "mysqldump"; }
        catch (Exception ignored) {}
        String[] paths = {
            "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysqldump.exe",
            "C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysqldump.exe",
            "C:\\Program Files\\MySQL\\MySQL Server 9.0\\bin\\mysqldump.exe",
            "C:\\xampp\\mysql\\bin\\mysqldump.exe",
            "C:\\wamp64\\bin\\mysql\\mysql8.0.31\\bin\\mysqldump.exe",
            "/usr/local/bin/mysqldump", "/opt/homebrew/bin/mysqldump", "/usr/local/mysql/bin/mysqldump",
        };
        for (String p : paths) if (p != null && new File(p).exists()) return p;
        return null;
    }

    private void manualExport(File outFile, String dbName) throws Exception {
        Connection conn = DatabaseConnection.getConnection();
        try (PrintWriter writer = new PrintWriter(new FileWriter(outFile))) {
            writer.println("-- AFM VFCC CMS Database Backup (JDBC export)");
            writer.println("-- Generated: " + LocalDateTime.now().format(FMT));
            writer.println("USE `" + dbName + "`; SET FOREIGN_KEY_CHECKS=0;");
            List<String> tables = new ArrayList<>();
            ResultSet rs = conn.createStatement().executeQuery("SHOW TABLES");
            while (rs.next()) tables.add(rs.getString(1));
            for (String table : tables) {
                writer.println("TRUNCATE TABLE `" + table + "`;");
                ResultSet data = conn.createStatement().executeQuery("SELECT * FROM `" + table + "`");
                ResultSetMetaData meta = data.getMetaData();
                int cols = meta.getColumnCount();
                while (data.next()) {
                    StringBuilder sb = new StringBuilder("INSERT INTO `").append(table).append("` VALUES (");
                    for (int i = 1; i <= cols; i++) {
                        String val = data.getString(i);
                        sb.append(val == null ? "NULL" : "'" + val.replace("'", "\\'") + "'");
                        if (i < cols) sb.append(", ");
                    }
                    writer.println(sb.append(");"));
                }
            }
            writer.println("SET FOREIGN_KEY_CHECKS=1;");
        }
    }

    private void loadBackupHistory() {
        String backupDir = backupPathField.getText().trim();
        List<String> items = new ArrayList<>();
        if (!backupDir.isEmpty()) {
            File dir = new File(backupDir);
            if (dir.exists()) {
                File[] files = dir.listFiles((d, n) ->
                        n.startsWith("AFM_VFCC_Backup_") && n.endsWith(".sql"));
                if (files != null) {
                    java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (File f : files)
                        items.add(f.getName() + "  (" + f.length() / 1024 + " KB)");
                }
            }
        }
        if (items.isEmpty()) items.add("No backups found in selected folder.");
        backupHistoryList.setItems(FXCollections.observableArrayList(items));
    }

    // ── EMAIL ─────────────────────────────────────────────────────────
    @FXML
    public void handleTestEmail() {
        String email = smtpUserField.getText().trim();
        if (email.isEmpty() || !email.contains("@")) {
            showLabel(emailTestLabel, "Enter a valid email address first.", false); return;
        }
        try {
            Connection conn = DatabaseConnection.getConnection();
            save(conn, "smtp_host",     smtpHostField.getText().trim());
            save(conn, "smtp_port",     smtpPortField.getText().trim());
            save(conn, "smtp_user",     email);
            save(conn, "smtp_password", smtpPassField.getText().trim());
        } catch (SQLException ignored) {}
        showLabel(emailTestLabel, "Sending…", true);
        javafx.concurrent.Task<String> task = new javafx.concurrent.Task<>() {
            @Override protected String call() {
                return EmailService.send(email, "AFM VFCC - Test Email",
                        "This is a test email from AFM VFCC Church Management System.");
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            String err = task.getValue();
            showLabel(emailTestLabel, err == null ? "✓ Test email sent successfully." : "✗ " + err, err == null);
        }));
        task.setOnFailed(e -> Platform.runLater(() ->
                showLabel(emailTestLabel, "✗ " + task.getException().getMessage(), false)));
        new Thread(task).start();
    }

    // ── SMS ───────────────────────────────────────────────────────────
    @FXML
    public void handleTestSms() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Send Test SMS"); dialog.setHeaderText("Enter a phone number:");
        dialog.setContentText("Phone number:"); Main.applyStyles(dialog.getDialogPane());
        dialog.showAndWait().ifPresent(phone -> {
            if (phone.trim().isEmpty()) return;
            try {
                Connection conn = DatabaseConnection.getConnection();
                save(conn, "bulksms_key",    bulkSmsKeyField.getText().trim());
                save(conn, "bulksms_secret", bulkSmsSecretField.getText().trim());
                save(conn, "bulksms_sender", bulkSmsSenderField.getText().trim());
            } catch (SQLException ignored) {}
            showLabel(smsTestLabel, "Sending…", true);
            javafx.concurrent.Task<String> task = new javafx.concurrent.Task<>() {
                @Override protected String call() {
                    return SmsService.send(phone.trim(),
                            "AFM VFCC test SMS - your SMS settings are working correctly.");
                }
            };
            task.setOnSucceeded(e -> Platform.runLater(() -> {
                String err = task.getValue();
                showLabel(smsTestLabel, err == null ? "✓ Test SMS sent to " + phone : "✗ " + err, err == null);
            }));
            task.setOnFailed(e -> Platform.runLater(() ->
                    showLabel(smsTestLabel, "✗ " + task.getException().getMessage(), false)));
            new Thread(task).start();
        });
    }

    // ── BROADCAST ─────────────────────────────────────────────────────
    @FXML
    public void handleBrowseBroadcastFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Video Download Folder");
        String current = broadcastDownloadFolderField != null ? broadcastDownloadFolderField.getText().trim() : "";
        if (!current.isEmpty()) { File f = new File(current); if (f.exists()) chooser.setInitialDirectory(f); }
        File dir = chooser.showDialog(broadcastDownloadFolderField != null
                ? broadcastDownloadFolderField.getScene().getWindow() : null);
        if (dir != null && broadcastDownloadFolderField != null)
            broadcastDownloadFolderField.setText(dir.getAbsolutePath());
    }

    @FXML
    public void handleUploadClientSecrets() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select client_secrets.json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File chosen = chooser.showOpenDialog(broadcastDownloadFolderField != null
                ? broadcastDownloadFolderField.getScene().getWindow() : null);
        if (chosen == null) return;
        try {
            String content = Files.readString(chosen.toPath());
            if (!content.contains("client_id")) {
                showAlert(Alert.AlertType.ERROR, "Invalid File",
                        "The selected file doesn't look like a valid client_secrets.json.");
                return;
            }
            File dest = YouTubeUploader.USER_SECRETS_FILE;
            dest.getParentFile().mkdirs();
            Files.copy(chosen.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            refreshSecretsStatus();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    "Uploaded YouTube client_secrets.json to " + dest.getAbsolutePath());
            showAlert(Alert.AlertType.INFORMATION, "YouTube Credentials Saved",
                    "client_secrets.json has been saved successfully.\n\n" +
                    "The first upload will open a browser window to authorise the church Google account.");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "File Error",
                    "Could not read or copy the file: " + e.getMessage());
        }
    }

    @FXML
    public void handleRevokeYoutubeToken() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Re-authorise YouTube");
        confirm.setHeaderText("Clear YouTube authorisation?");
        confirm.setContentText("This will remove stored credentials. Continue?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                YouTubeUploader.revokeToken(); refreshYoutubeAuthStatus();
                showAlert(Alert.AlertType.INFORMATION, "Done",
                        "YouTube authorisation cleared. You will be asked to sign in again on the next upload.");
            }
        });
    }

    private void refreshYoutubeAuthStatus() {
        if (youtubeAuthStatusLabel == null) return;
        File tokenDir = new File(
                System.getenv("APPDATA") != null
                    ? System.getenv("APPDATA") + File.separator + "AFM_VFCC_CMS" + File.separator + "youtube_tokens"
                    : System.getProperty("user.home") + File.separator + ".afmvfcc" + File.separator + "youtube_tokens");
        boolean authorised = tokenDir.exists() &&
                tokenDir.listFiles() != null &&
                tokenDir.listFiles().length > 0;
        youtubeAuthStatusLabel.setText(authorised
                ? "✓  YouTube account is authorised" : "Not yet authorised");
        youtubeAuthStatusLabel.setStyle(authorised
                ? "-fx-font-size:12px;-fx-text-fill:#2E7D4F;-fx-font-weight:600;"
                : "-fx-font-size:12px;-fx-text-fill:#9099AA;");
    }

    private void refreshSecretsStatus() {
        if (secretsStatusLabel == null) return;
        String source = YouTubeUploader.getSecretsSource();
        if (source == null) {
            secretsStatusLabel.setText("⚠  No client_secrets.json found — upload one below.");
            secretsStatusLabel.setStyle("-fx-font-size:11px;-fx-text-fill:#D94040;");
        } else if (source.equals("Built-in (bundled)")) {
            secretsStatusLabel.setText("✓  Using built-in credentials (bundled with app).");
            secretsStatusLabel.setStyle("-fx-font-size:11px;-fx-text-fill:#2E7D4F;");
        } else {
            secretsStatusLabel.setText("✓  Using: " + source);
            secretsStatusLabel.setStyle("-fx-font-size:11px;-fx-text-fill:#2E7D4F;");
        }
    }

    // ── WEBSITE (local folder) ─────────────────────────────────────────
    @FXML
    public void handleBrowseWebsite() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Website Folder (where index.html is)");
        String current = websiteFolderField != null ? websiteFolderField.getText().trim() : "";
        if (!current.isEmpty()) { File f = new File(current); if (f.exists()) chooser.setInitialDirectory(f); }
        File dir = chooser.showDialog(websiteFolderField != null
                ? websiteFolderField.getScene().getWindow() : null);
        if (dir != null && websiteFolderField != null) websiteFolderField.setText(dir.getAbsolutePath());
    }

    // ── GITHUB ────────────────────────────────────────────────────────
    @FXML
    public void handleTestGitHub() {
        if (githubTokenField == null || githubOwnerField == null || githubRepoField == null) return;
        String token  = githubTokenField.getText().trim();
        String owner  = githubOwnerField.getText().trim();
        String repo   = githubRepoField.getText().trim();
        String branch = githubBranchField != null ? githubBranchField.getText().trim() : "main";

        if (token.isEmpty() || owner.isEmpty() || repo.isEmpty()) {
            showLabel(githubStatusLabel, "Fill in Token, Owner, and Repo first.", false); return;
        }
        try {
            Connection conn = DatabaseConnection.getConnection();
            save(conn, "github_token",  token);
            save(conn, "github_owner",  owner);
            save(conn, "github_repo",   repo);
            save(conn, "github_branch", branch.isEmpty() ? "main" : branch);
            if (websiteCustomDomainField != null)
                save(conn, "website_custom_domain", websiteCustomDomainField.getText().trim());
        } catch (SQLException ignored) {}

        showLabel(githubStatusLabel, "Connecting…", true);
        javafx.concurrent.Task<String> task = new javafx.concurrent.Task<>() {
            @Override protected String call() { return GitHubSync.testConnection(); }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            String err = task.getValue();
            if (err == null) {
                showLabel(githubStatusLabel,
                    "✓ Connected! Repo: " + owner + "/" + repo + "  branch: " +
                    (branch.isEmpty() ? "main" : branch), true);
            } else {
                showLabel(githubStatusLabel, "✗ " + err, false);
            }
        }));
        task.setOnFailed(e -> Platform.runLater(() ->
                showLabel(githubStatusLabel, "✗ " + task.getException().getMessage(), false)));
        new Thread(task).start();
    }

    // ── ANNOUNCEMENTS ─────────────────────────────────────────────────
    @FXML
    public void handleTestWhatsApp() {
        String token     = whatsappTokenField     != null ? whatsappTokenField.getText().trim()     : "";
        String phoneId   = whatsappPhoneIdField   != null ? whatsappPhoneIdField.getText().trim()   : "";
        String recipient = whatsappRecipientField != null ? whatsappRecipientField.getText().trim() : "";
        if (token.isEmpty() || phoneId.isEmpty() || recipient.isEmpty()) {
            showLabel(annTestStatus, "Fill in all WhatsApp fields first.", false); return;
        }
        try {
            Connection conn = DatabaseConnection.getConnection();
            save(conn, "whatsapp_token", token);
            save(conn, "whatsapp_phone_id", phoneId);
            save(conn, "whatsapp_recipient", recipient);
        } catch (SQLException ignored) {}
        showLabel(annTestStatus, "Sending test WhatsApp message…", true);
        javafx.concurrent.Task<com.afmvfcc.utils.AnnouncementsService.PostResult> task =
            new javafx.concurrent.Task<>() {
                @Override protected com.afmvfcc.utils.AnnouncementsService.PostResult call() {
                    return com.afmvfcc.utils.AnnouncementsService.sendWhatsApp(
                        "✅ AFM VFCC CMS — WhatsApp test message. Your settings are working correctly.");
                }
            };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            var r = task.getValue();
            showLabel(annTestStatus,
                    r.success ? "✓ WhatsApp test sent!" : "✗ WhatsApp: " + r.message, r.success);
        }));
        task.setOnFailed(e -> Platform.runLater(() ->
            showLabel(annTestStatus, "✗ " + task.getException().getMessage(), false)));
        new Thread(task).start();
    }

    @FXML
    public void handleTestFacebook() {
        String pageToken = facebookPageTokenField != null ? facebookPageTokenField.getText().trim() : "";
        String pageId    = facebookPageIdField    != null ? facebookPageIdField.getText().trim()    : "";
        if (pageToken.isEmpty() || pageId.isEmpty()) {
            showLabel(annTestStatus, "Fill in both Facebook fields first.", false); return;
        }
        try {
            Connection conn = DatabaseConnection.getConnection();
            save(conn, "facebook_page_token", pageToken);
            save(conn, "facebook_page_id", pageId);
        } catch (SQLException ignored) {}
        showLabel(annTestStatus, "Posting test to Facebook page…", true);
        javafx.concurrent.Task<com.afmvfcc.utils.AnnouncementsService.PostResult> task =
            new javafx.concurrent.Task<>() {
                @Override protected com.afmvfcc.utils.AnnouncementsService.PostResult call() {
                    return com.afmvfcc.utils.AnnouncementsService.postToFacebook(
                        "✅ AFM VFCC CMS — Facebook test post. Your settings are working correctly.");
                }
            };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            var r = task.getValue();
            showLabel(annTestStatus, r.success
                ? "✓ Facebook test posted! Post ID: " + r.postId : "✗ Facebook: " + r.message, r.success);
        }));
        task.setOnFailed(e -> Platform.runLater(() ->
            showLabel(annTestStatus, "✗ " + task.getException().getMessage(), false)));
        new Thread(task).start();
    }

    // ── HELPERS ───────────────────────────────────────────────────────
    private void showBackupStatus(String msg, boolean ok) {
        backupStatusLabel.setText(msg);
        backupStatusLabel.setStyle(ok
                ? "-fx-text-fill:#2E7D4F;-fx-font-size:12px;-fx-font-weight:600;"
                : "-fx-text-fill:#D94040;-fx-font-size:12px;-fx-font-weight:600;");
        backupStatusLabel.setVisible(true);
    }

    private void showLabel(Label label, String msg, boolean ok) {
        if (label == null) return;
        label.setText(msg);
        label.setStyle(ok
                ? "-fx-text-fill:#2E7D4F;-fx-font-size:12px;-fx-font-weight:600;"
                : "-fx-text-fill:#D94040;-fx-font-size:12px;-fx-font-weight:600;");
        label.setVisible(true);
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        Main.applyStyles(alert.getDialogPane());
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(msg);
        alert.showAndWait();
    }
}