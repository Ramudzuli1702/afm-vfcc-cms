package com.afmvfcc.db;

import com.afmvfcc.utils.PasswordUtil;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Portable database connection manager.
 *
 * On first run, shows a setup wizard that:
 *   1. Connects to MySQL using the root password
 *   2. Creates the AFM_VFCC_CMS database
 *   3. Creates the afm_app user with a chosen password
 *   4. Creates all required tables  (including is_closed on attendance_sessions)
 *   5. Prompts the user to create their first admin account
 *   6. Saves connection details to a local config file
 */
public class DatabaseConnection {

    private static final String APP_NAME    = "AFM_VFCC_CMS";
    private static final String CONFIG_FILE = "db.properties";

    private static final String DEFAULT_HOST     = "127.0.0.1";
    private static final String DEFAULT_PORT     = "3306";
    private static final String DEFAULT_DATABASE = "AFM_VFCC_CMS";
    private static final String DEFAULT_USERNAME = "afm_app";

    // ── Shared style constants (mirrors login screen tokens) ──────────────────
    private static final String COLOR_NAVY        = "#1B3A6B";
    private static final String COLOR_NAVY_DARK   = "#152E56";
    private static final String COLOR_WHITE       = "#FFFFFF";
    private static final String COLOR_FIELD_BG    = "#F5F6FA";
    private static final String COLOR_BORDER      = "#DDE1EA";
    private static final String COLOR_LABEL       = "#9099AA";
    private static final String COLOR_ERROR       = "#D94040";
    private static final String COLOR_ACCENT_BLUE = "#3A86C8";
    private static final String COLOR_INFO_BG     = "#EBF2FF";
    private static final String COLOR_INFO_BORDER = "#C5D8F5";

    private static final String STYLE_CARD =
        "-fx-background-color:" + COLOR_WHITE + ";" +
        "-fx-background-radius:16;" +
        "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.35),24,0,0,6);";

    private static final String STYLE_FIELD =
        "-fx-background-color:" + COLOR_FIELD_BG + ";" +
        "-fx-border-color:" + COLOR_BORDER + ";" +
        "-fx-border-radius:8;" +
        "-fx-background-radius:8;" +
        "-fx-padding:10 14;" +
        "-fx-font-size:13px;" +
        "-fx-text-fill:#2C3454;";

    private static final String STYLE_FIELD_FOCUSED =
        "-fx-background-color:" + COLOR_WHITE + ";" +
        "-fx-border-color:" + COLOR_NAVY + ";" +
        "-fx-border-radius:8;" +
        "-fx-background-radius:8;" +
        "-fx-padding:10 14;" +
        "-fx-font-size:13px;" +
        "-fx-text-fill:#2C3454;";

    private static final String STYLE_LABEL =
        "-fx-font-size:10px;" +
        "-fx-font-weight:700;" +
        "-fx-text-fill:" + COLOR_LABEL + ";" +
        "-fx-letter-spacing:0.8;";

    private static final String STYLE_BTN_PRIMARY =
        "-fx-background-color:" + COLOR_NAVY + ";" +
        "-fx-text-fill:" + COLOR_WHITE + ";" +
        "-fx-font-size:13px;" +
        "-fx-font-weight:700;" +
        "-fx-background-radius:8;" +
        "-fx-cursor:hand;" +
        "-fx-padding:12 0;";

    private static final String STYLE_BTN_PRIMARY_HOVER =
        "-fx-background-color:" + COLOR_NAVY_DARK + ";" +
        "-fx-text-fill:" + COLOR_WHITE + ";" +
        "-fx-font-size:13px;" +
        "-fx-font-weight:700;" +
        "-fx-background-radius:8;" +
        "-fx-cursor:hand;" +
        "-fx-padding:12 0;";

    private static final String STYLE_BTN_GHOST =
        "-fx-background-color:transparent;" +
        "-fx-border-color:" + COLOR_BORDER + ";" +
        "-fx-border-radius:8;" +
        "-fx-text-fill:#5A6278;" +
        "-fx-font-size:12px;" +
        "-fx-cursor:hand;" +
        "-fx-padding:10 20;";

    private static Connection connection = null;
    private static Properties config     = null;

    // ── Config file location ──────────────────────────────────

    public static Path getConfigDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) return Paths.get(appData, APP_NAME);
        } else if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"),
                "Library", "Application Support", APP_NAME);
        }
        return Paths.get(System.getProperty("user.home"), ".config", APP_NAME);
    }

    public static Path getConfigPath()       { return getConfigDir().resolve(CONFIG_FILE); }
    public static String getConfigFilePath() { return getConfigPath().toString(); }

    // ── Load config ───────────────────────────────────────────

    private static Properties loadConfig() {
        if (config != null) return config;
        config = new Properties();
        Path configPath = getConfigPath();
        if (Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                config.load(in);
                System.out.println("[DB] Config loaded from: " + configPath);
            } catch (IOException e) {
                System.err.println("[DB] Failed to read config: " + e.getMessage());
                showSetupWizard();
            }
        } else {
            showSetupWizard();
        }
        return config;
    }

    private static void saveConfig(Properties p, Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream out = Files.newOutputStream(configPath)) {
                p.store(out,
                    "AFM VFCC CMS - Database Configuration\n" +
                    "Do not share this file - it contains your database password.\n" +
                    "Delete this file to re-run the setup wizard.");
            }
            System.out.println("[DB] Config saved to: " + configPath);
        } catch (IOException e) {
            System.err.println("[DB] Could not save config: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    /** Applies the shared field style and wires focus-in / focus-out colour change. */
    private static void styleField(Control field) {
        field.setStyle(STYLE_FIELD);
        field.focusedProperty().addListener((obs, wasFocused, isFocused) ->
            field.setStyle(isFocused ? STYLE_FIELD_FOCUSED : STYLE_FIELD));
    }

    /** Primary button with hover effect. */
    private static void stylePrimaryBtn(Button btn) {
        btn.setStyle(STYLE_BTN_PRIMARY);
        btn.setOnMouseEntered(e -> btn.setStyle(STYLE_BTN_PRIMARY_HOVER));
        btn.setOnMouseExited(e  -> btn.setStyle(STYLE_BTN_PRIMARY));
    }

    /**
     * Builds the full-screen navy StackPane that wraps a white card —
     * matching the login screen layout exactly.
     *
     * @param card  The white card VBox to centre on screen.
     * @param width  Scene width
     * @param height Scene height
     */
    private static StackPane buildLoginShell(VBox card, double width, double height) {
        card.setStyle(STYLE_CARD);
        StackPane shell = new StackPane(card);
        shell.setStyle("-fx-background-color:" + COLOR_NAVY + ";");
        shell.setPrefSize(width, height);
        StackPane.setAlignment(card, Pos.CENTER);
        return shell;
    }

    /** Small uppercase section divider label (e.g. "STEP 1 OF 2 · DATABASE"). */
    private static Label sectionBadge(String text) {
        Label l = new Label(text);
        l.setStyle(
            "-fx-text-fill:rgba(255,255,255,0.55);" +
            "-fx-font-size:9px;" +
            "-fx-font-weight:700;" +
            "-fx-letter-spacing:1.2;");
        return l;
    }

    /** Thin horizontal rule rendered as a 1 px navy-tinted line. */
    private static Region divider() {
        Region r = new Region();
        r.setPrefHeight(1);
        r.setStyle("-fx-background-color:" + COLOR_BORDER + ";");
        VBox.setMargin(r, new Insets(4, 0, 4, 0));
        return r;
    }

    // ── Setup wizard ──────────────────────────────────────────

    public static void showSetupWizard() {
        CountDownLatch latch     = new CountDownLatch(1);
        AtomicBoolean  cancelled = new AtomicBoolean(false);

        Runnable run = () -> {
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("AFM VFCC CMS — First Time Setup");
            dialog.setResizable(false);

            javafx.stage.Window.getWindows().stream()
                .filter(javafx.stage.Window::isShowing)
                .findFirst()
                .ifPresent(dialog::initOwner);

            // ── White card ────────────────────────────────────
            VBox card = new VBox(0);
            card.setMaxWidth(400);
            card.setPrefWidth(400);

            // ── Card header (navy strip inside the card) ──────
            VBox cardHeader = new VBox(3);
            cardHeader.setStyle(
                "-fx-background-color:" + COLOR_NAVY + ";" +
                "-fx-background-radius:16 16 0 0;" +
                "-fx-padding:24 32 20 32;");

            javafx.scene.image.Image logoImg = new javafx.scene.image.Image(
                DatabaseConnection.class.getResourceAsStream("/com/afmvfcc/images/afm_logo.png"));
            javafx.scene.image.ImageView logo1 = new javafx.scene.image.ImageView(logoImg);
            logo1.setFitWidth(64);
            logo1.setFitHeight(64);
            logo1.setPreserveRatio(true);
            VBox.setMargin(logo1, new Insets(0, 0, 8, 0));

            Label title = new Label("Database Setup");
            title.setStyle(
                "-fx-text-fill:" + COLOR_WHITE + ";" +
                "-fx-font-size:20px;" +
                "-fx-font-weight:700;");

            Label subtitle = new Label("One-time setup for this computer");
            subtitle.setStyle(
                "-fx-text-fill:rgba(255,255,255,0.60);" +
                "-fx-font-size:11px;");

            cardHeader.getChildren().addAll(logo1, title, subtitle);

            // ── Info strip ────────────────────────────────────
            VBox infoStrip = new VBox(2);
            infoStrip.setStyle(
                "-fx-background-color:" + COLOR_INFO_BG + ";" +
                "-fx-padding:10 32 10 32;" +
                "-fx-border-color:" + COLOR_INFO_BORDER + ";" +
                "-fx-border-width:0 0 1 0;");
            Label infoText = new Label(
                "This will: create the database · set up a dedicated app user · " +
                "build all tables · create your admin account");
            infoText.setStyle(
                "-fx-font-size:10.5px;" +
                "-fx-text-fill:#3A5080;" +
                "-fx-wrap-text:true;");
            infoText.setWrapText(true);
            infoStrip.getChildren().add(infoText);

            // ── Form body ─────────────────────────────────────
            VBox body = new VBox(0);
            body.setStyle("-fx-padding:24 32 8 32;");

            // Root password
            Label rootLbl = new Label("MYSQL ROOT PASSWORD");
            rootLbl.setStyle(STYLE_LABEL);
            PasswordField rootPass = new PasswordField();
            rootPass.setPromptText("Enter your MySQL root password");
            rootPass.setMaxWidth(Double.MAX_VALUE);
            styleField(rootPass);
            VBox rootGroup = new VBox(6, rootLbl, rootPass);
            VBox.setMargin(rootGroup, new Insets(0, 0, 14, 0));

            // App password
            Label appLbl = new Label("APP USER PASSWORD");
            appLbl.setStyle(STYLE_LABEL);
            PasswordField appPass = new PasswordField();
            appPass.setPromptText("Min. 8 characters");
            appPass.setMaxWidth(Double.MAX_VALUE);
            styleField(appPass);
            VBox appGroup = new VBox(6, appLbl, appPass);
            VBox.setMargin(appGroup, new Insets(0, 0, 14, 0));

            // Confirm password
            Label confirmLbl = new Label("CONFIRM APP PASSWORD");
            confirmLbl.setStyle(STYLE_LABEL);
            PasswordField appConfirm = new PasswordField();
            appConfirm.setPromptText("Repeat password");
            appConfirm.setMaxWidth(Double.MAX_VALUE);
            styleField(appConfirm);
            VBox confirmGroup = new VBox(6, confirmLbl, appConfirm);
            VBox.setMargin(confirmGroup, new Insets(0, 0, 6, 0));

            // Error label
            Label errorLbl = new Label();
            errorLbl.setStyle(
                "-fx-text-fill:" + COLOR_ERROR + ";" +
                "-fx-font-size:11px;" +
                "-fx-wrap-text:true;");
            errorLbl.setWrapText(true);
            errorLbl.setVisible(false);
            errorLbl.setMaxWidth(Double.MAX_VALUE);
            VBox.setMargin(errorLbl, new Insets(4, 0, 4, 0));

            // Progress
            ProgressBar progress = new ProgressBar(0);
            progress.setMaxWidth(Double.MAX_VALUE);
            progress.setStyle("-fx-accent:" + COLOR_NAVY + ";");
            progress.setVisible(false);

            Label progressLbl = new Label();
            progressLbl.setStyle(
                "-fx-font-size:11px;" +
                "-fx-text-fill:" + COLOR_ACCENT_BLUE + ";");
            progressLbl.setVisible(false);

            body.getChildren().addAll(
                rootGroup, appGroup, confirmGroup,
                errorLbl, progress, progressLbl);

            // ── Button row ────────────────────────────────────
            HBox btnRow = new HBox(10);
            btnRow.setAlignment(Pos.CENTER_RIGHT);
            btnRow.setStyle(
                "-fx-padding:16 32 24 32;" +
                "-fx-background-color:" + COLOR_WHITE + ";" +
                "-fx-background-radius:0 0 16 16;");

            Button cancelBtn = new Button("Cancel");
            cancelBtn.setStyle(STYLE_BTN_GHOST);

            Button setupBtn = new Button("Set Up Database");
            setupBtn.setMaxWidth(Double.MAX_VALUE);
            stylePrimaryBtn(setupBtn);

            // Give "Set Up" more visual weight
            HBox.setHgrow(setupBtn, Priority.ALWAYS);
            btnRow.getChildren().addAll(cancelBtn, setupBtn);

            card.getChildren().addAll(cardHeader, infoStrip, body, btnRow);

            // ── Scene: full-screen navy shell ─────────────────
            StackPane shell = buildLoginShell(card, 520, 580);
            dialog.setScene(new Scene(shell, 520, 580));

            // ── Setup action ──────────────────────────────────
            setupBtn.setOnAction(e -> {
                errorLbl.setVisible(false);
                String rootPw = rootPass.getText();
                String appPw  = appPass.getText();
                String appPw2 = appConfirm.getText();

                if (rootPw.isEmpty()) {
                    errorLbl.setText("Please enter your MySQL root password.");
                    errorLbl.setVisible(true); return;
                }
                if (appPw.length() < 8) {
                    errorLbl.setText("App password must be at least 8 characters.");
                    errorLbl.setVisible(true); return;
                }
                if (!appPw.equals(appPw2)) {
                    errorLbl.setText("Passwords do not match.");
                    errorLbl.setVisible(true); return;
                }

                setupBtn.setDisable(true);
                cancelBtn.setDisable(true);
                progress.setVisible(true);
                progressLbl.setVisible(true);

                new Thread(() -> {
                    try {
                        String host = DEFAULT_HOST;
                        String port = DEFAULT_PORT;

                        Platform.runLater(() -> { progressLbl.setText("Connecting to MySQL…"); progress.setProgress(0.1); });
                        String rootUrl = "jdbc:mysql://" + host + ":" + port +
                            "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
                        Class.forName("com.mysql.cj.jdbc.Driver");
                        Connection adminConn = DriverManager.getConnection(rootUrl, "root", rootPw);

                        Platform.runLater(() -> { progressLbl.setText("Creating database…"); progress.setProgress(0.25); });
                        adminConn.createStatement().executeUpdate(
                            "CREATE DATABASE IF NOT EXISTS " + DEFAULT_DATABASE +
                            " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

                        Platform.runLater(() -> { progressLbl.setText("Creating application user…"); progress.setProgress(0.4); });
                        try {
                            adminConn.createStatement().executeUpdate(
                                "CREATE USER '" + DEFAULT_USERNAME + "'@'localhost' " +
                                "IDENTIFIED BY '" + appPw.replace("'", "\\'") + "'");
                        } catch (SQLException userExists) {
                            adminConn.createStatement().executeUpdate(
                                "ALTER USER '" + DEFAULT_USERNAME + "'@'localhost' " +
                                "IDENTIFIED BY '" + appPw.replace("'", "\\'") + "'");
                        }
                        adminConn.createStatement().executeUpdate(
                            "GRANT ALL PRIVILEGES ON " + DEFAULT_DATABASE +
                            ".* TO '" + DEFAULT_USERNAME + "'@'localhost'");
                        adminConn.createStatement().executeUpdate("FLUSH PRIVILEGES");

                        Platform.runLater(() -> { progressLbl.setText("Creating tables…"); progress.setProgress(0.6); });
                        adminConn.createStatement().executeUpdate("USE " + DEFAULT_DATABASE);
                        createTables(adminConn);
                        adminConn.close();

                        Platform.runLater(() -> { progressLbl.setText("Saving configuration…"); progress.setProgress(0.9); });
                        Properties p = new Properties();
                        p.setProperty("db.host",     host);
                        p.setProperty("db.port",     port);
                        p.setProperty("db.database", DEFAULT_DATABASE);
                        p.setProperty("db.username", DEFAULT_USERNAME);
                        p.setProperty("db.password", appPw);
                        config = p;
                        saveConfig(p, getConfigPath());

                        Platform.runLater(() -> {
                            progress.setProgress(1.0);
                            progressLbl.setText("Setup complete!");
                            dialog.close();
                            Platform.runLater(() -> showCreateFirstUserDialog(latch));
                        });

                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            String msg = ex.getMessage();
                            if (msg != null && msg.contains("Access denied"))
                                errorLbl.setText("Incorrect root password. Please try again.");
                            else if (msg != null && msg.contains("refused"))
                                errorLbl.setText("Cannot connect to MySQL. Make sure MySQL Server is running.");
                            else
                                errorLbl.setText("Setup failed: " + msg);
                            errorLbl.setVisible(true);
                            progress.setVisible(false);
                            progressLbl.setVisible(false);
                            setupBtn.setDisable(false);
                            cancelBtn.setDisable(false);
                        });
                    }
                }, "db-setup").start();
            });

            cancelBtn.setOnAction(e -> { cancelled.set(true); dialog.close(); latch.countDown(); });
            dialog.setOnCloseRequest(e -> { cancelled.set(true); latch.countDown(); });
            dialog.showAndWait();
        };

        if (Platform.isFxApplicationThread()) {
            run.run();
        } else {
            Platform.runLater(run);
            try { latch.await(); } catch (InterruptedException ignored) {}
        }

        if (cancelled.get()) {
            System.out.println("[DB] Setup cancelled. Exiting.");
            Platform.exit();
            System.exit(0);
        }
    }

    // ── Create first admin user dialog ────────────────────────

    /**
     * Modal dialog shown immediately after the DB setup wizard completes.
     * Forces the installer to create at least one admin account before the
     * app starts. The dialog cannot be dismissed without submitting a valid form.
     */
    private static void showCreateFirstUserDialog(CountDownLatch latch) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("AFM VFCC CMS — Create Admin Account");
        dialog.setResizable(false);

        javafx.stage.Window.getWindows().stream()
            .filter(w -> w instanceof Stage)
            .findFirst()
            .ifPresent(dialog::initOwner);

        // ── White card ────────────────────────────────────────
        VBox card = new VBox(0);
        card.setMaxWidth(400);
        card.setPrefWidth(400);

        // ── Card header ───────────────────────────────────────
        VBox cardHeader = new VBox(3);
        cardHeader.setStyle(
            "-fx-background-color:" + COLOR_NAVY + ";" +
            "-fx-background-radius:16 16 0 0;" +
            "-fx-padding:24 32 20 32;");

        javafx.scene.image.Image logoImg2 = new javafx.scene.image.Image(
            DatabaseConnection.class.getResourceAsStream("/com/afmvfcc/images/afm_logo.png"));
        javafx.scene.image.ImageView logo2 = new javafx.scene.image.ImageView(logoImg2);
        logo2.setFitWidth(64);
        logo2.setFitHeight(64);
        logo2.setPreserveRatio(true);
        VBox.setMargin(logo2, new Insets(0, 0, 8, 0));

        Label title = new Label("Create Admin Account");
        title.setStyle(
            "-fx-text-fill:" + COLOR_WHITE + ";" +
            "-fx-font-size:20px;" +
            "-fx-font-weight:700;");

        Label subtitle = new Label("Set up the first administrator for AFM VFCC CMS");
        subtitle.setStyle(
            "-fx-text-fill:rgba(255,255,255,0.60);" +
            "-fx-font-size:11px;");

        cardHeader.getChildren().addAll(logo2, title, subtitle);

        // ── Info strip ────────────────────────────────────────
        VBox infoStrip = new VBox(2);
        infoStrip.setStyle(
            "-fx-background-color:" + COLOR_INFO_BG + ";" +
            "-fx-padding:10 32 10 32;" +
            "-fx-border-color:" + COLOR_INFO_BORDER + ";" +
            "-fx-border-width:0 0 1 0;");
        Label infoText = new Label(
            "This account will have full super-admin access. " +
            "You can add more users after logging in.");
        infoText.setStyle(
            "-fx-font-size:10.5px;" +
            "-fx-text-fill:#3A5080;" +
            "-fx-wrap-text:true;");
        infoText.setWrapText(true);
        infoStrip.getChildren().add(infoText);

        // ── Form body ─────────────────────────────────────────
        VBox body = new VBox(0);
        body.setStyle("-fx-padding:24 32 8 32;");

        Label nameLbl = new Label("FULL NAME");
        nameLbl.setStyle(STYLE_LABEL);
        TextField nameField = new TextField();
        nameField.setPromptText("e.g. John Doe");
        nameField.setMaxWidth(Double.MAX_VALUE);
        styleField(nameField);
        VBox nameGroup = new VBox(6, nameLbl, nameField);
        VBox.setMargin(nameGroup, new Insets(0, 0, 14, 0));

        Label userLbl = new Label("USERNAME");
        userLbl.setStyle(STYLE_LABEL);
        TextField userField = new TextField();
        userField.setPromptText("e.g. admin");
        userField.setMaxWidth(Double.MAX_VALUE);
        styleField(userField);
        VBox userGroup = new VBox(6, userLbl, userField);
        VBox.setMargin(userGroup, new Insets(0, 0, 14, 0));

        Label passLbl = new Label("PASSWORD");
        passLbl.setStyle(STYLE_LABEL);
        PasswordField passField = new PasswordField();
        passField.setPromptText("Min. 8 characters");
        passField.setMaxWidth(Double.MAX_VALUE);
        styleField(passField);
        VBox passGroup = new VBox(6, passLbl, passField);
        VBox.setMargin(passGroup, new Insets(0, 0, 14, 0));

        Label confirmLbl = new Label("CONFIRM PASSWORD");
        confirmLbl.setStyle(STYLE_LABEL);
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Repeat password");
        confirmField.setMaxWidth(Double.MAX_VALUE);
        styleField(confirmField);
        VBox confirmGroup = new VBox(6, confirmLbl, confirmField);
        VBox.setMargin(confirmGroup, new Insets(0, 0, 6, 0));

        Label errorLbl = new Label();
        errorLbl.setStyle(
            "-fx-text-fill:" + COLOR_ERROR + ";" +
            "-fx-font-size:11px;" +
            "-fx-wrap-text:true;");
        errorLbl.setWrapText(true);
        errorLbl.setVisible(false);
        errorLbl.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(errorLbl, new Insets(4, 0, 4, 0));

        body.getChildren().addAll(nameGroup, userGroup, passGroup, confirmGroup, errorLbl);

        // ── Button row ────────────────────────────────────────
        VBox btnRow = new VBox();
        btnRow.setStyle(
            "-fx-padding:16 32 24 32;" +
            "-fx-background-color:" + COLOR_WHITE + ";" +
            "-fx-background-radius:0 0 16 16;");

        Button createBtn = new Button("Create Account & Launch");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        stylePrimaryBtn(createBtn);

        Label footerLbl = new Label("AFM Victory Fellowship Christian Centre");
        footerLbl.setStyle(
            "-fx-text-fill:" + COLOR_LABEL + ";" +
            "-fx-font-size:10px;" +
            "-fx-padding:14 0 0 0;");
        footerLbl.setMaxWidth(Double.MAX_VALUE);
        footerLbl.setAlignment(Pos.CENTER);

        btnRow.getChildren().addAll(createBtn, footerLbl);

        card.getChildren().addAll(cardHeader, infoStrip, body, btnRow);

        // ── Scene ─────────────────────────────────────────────
        StackPane shell = buildLoginShell(card, 520, 640);
        dialog.setScene(new Scene(shell, 520, 640));

        // ── Create action ─────────────────────────────────────
        createBtn.setOnAction(e -> {
            errorLbl.setVisible(false);
            String fullName = nameField.getText().trim();
            String username = userField.getText().trim();
            String password = passField.getText();
            String confirm  = confirmField.getText();

            if (fullName.isEmpty()) {
                errorLbl.setText("Full name is required.");
                errorLbl.setVisible(true); return;
            }
            if (username.isEmpty()) {
                errorLbl.setText("Username is required.");
                errorLbl.setVisible(true); return;
            }
            if (password.length() < 8) {
                errorLbl.setText("Password must be at least 8 characters.");
                errorLbl.setVisible(true); return;
            }
            if (!password.equals(confirm)) {
                errorLbl.setText("Passwords do not match.");
                errorLbl.setVisible(true); return;
            }

            createBtn.setDisable(true);

            new Thread(() -> {
                try {
                    Connection conn = getConnection();
                    String hash = PasswordUtil.hash(password);

                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO users " +
                        "(full_name, username, password_hash, role_title, is_super_admin, is_active) " +
                        "VALUES (?, ?, ?, 'System Administrator', 1, 1)");
                    ps.setString(1, fullName);
                    ps.setString(2, username);
                    ps.setString(3, hash);
                    ps.executeUpdate();

                    System.out.println("[DB] First admin user created: " + username);

                    Platform.runLater(() -> {
                        dialog.close();
                        latch.countDown();
                    });

                } catch (SQLIntegrityConstraintViolationException dupEx) {
                    Platform.runLater(() -> {
                        errorLbl.setText("Username '" + username + "' is already taken. Please choose another.");
                        errorLbl.setVisible(true);
                        createBtn.setDisable(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        errorLbl.setText("Could not create user: " + ex.getMessage());
                        errorLbl.setVisible(true);
                        createBtn.setDisable(false);
                    });
                }
            }, "db-create-user").start();
        });

        // Prevent closing without creating a user
        dialog.setOnCloseRequest(Event::consume);
        dialog.showAndWait();
    }

    // ── Create all tables ─────────────────────────────────────

    private static void createTables(Connection conn) throws SQLException {
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS sub_branches (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, is_active TINYINT(1) DEFAULT 1) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS families (id INT AUTO_INCREMENT PRIMARY KEY, family_name VARCHAR(100) NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS ministries (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, description TEXT, is_active TINYINT(1) DEFAULT 1) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS users (id INT AUTO_INCREMENT PRIMARY KEY, full_name VARCHAR(100) NOT NULL, username VARCHAR(50) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL, email VARCHAR(100), phone VARCHAR(20), role_title VARCHAR(50), photo_path VARCHAR(255), account_role VARCHAR(20) NOT NULL DEFAULT 'admin', is_super_admin TINYINT(1) DEFAULT 0, is_active TINYINT(1) DEFAULT 1, failed_attempts INT DEFAULT 0, locked_until DATETIME, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS members (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "full_name VARCHAR(100) NOT NULL, " +
                "date_of_birth DATE, " +
                "gender ENUM('Male','Female'), " +
                "marital_status ENUM('Single','Married','Divorced','Widowed') DEFAULT 'Single', " +
                "is_spouse_member TINYINT(1) DEFAULT 0, " +
                "spouse_member_id INT, " +
                "employment_status ENUM('Employed','Student','Retired','Unemployed') DEFAULT 'Employed', " +
                "address TEXT, " +
                "sub_branch_id INT, " +
                "phone VARCHAR(20), " +
                "email VARCHAR(100), " +
                "baptism_date DATE, " +
                "next_of_kin_name VARCHAR(100), " +
                "next_of_kin_phone VARCHAR(20), " +
                "next_of_kin_member_id INT, " +
                "family_id INT, " +
                "photo_path VARCHAR(255), " +
                "is_full_time TINYINT(1) DEFAULT 1, " +
                "is_active TINYINT(1) DEFAULT 1, " +
                "is_deleted TINYINT(1) DEFAULT 0, " +
                "is_deceased TINYINT(1) DEFAULT 0, " +
                "date_joined DATE, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (sub_branch_id) REFERENCES sub_branches(id) ON DELETE SET NULL, " +
                "FOREIGN KEY (family_id) REFERENCES families(id) ON DELETE SET NULL, " +
                "FOREIGN KEY (next_of_kin_member_id) REFERENCES members(id) ON DELETE SET NULL, " +
                "FOREIGN KEY (spouse_member_id) REFERENCES members(id) ON DELETE SET NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS pending_members (id INT AUTO_INCREMENT PRIMARY KEY, full_name VARCHAR(100) NOT NULL, phone VARCHAR(20), sub_branch_id INT, ministry_id INT, is_full_time TINYINT(1) DEFAULT 1, submitted_at DATETIME DEFAULT CURRENT_TIMESTAMP, device_id VARCHAR(100), status ENUM('Pending','Approved','Rejected') DEFAULT 'Pending', reviewed_by INT, reviewed_at DATETIME, FOREIGN KEY (sub_branch_id) REFERENCES sub_branches(id) ON DELETE SET NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS member_ministries (id INT AUTO_INCREMENT PRIMARY KEY, member_id INT NOT NULL, ministry_id INT NOT NULL, joined_date DATE, UNIQUE KEY unique_member_ministry (member_id, ministry_id), FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE, FOREIGN KEY (ministry_id) REFERENCES ministries(id) ON DELETE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            // ── attendance_sessions includes is_closed ─────────────────────────────
            "CREATE TABLE IF NOT EXISTS attendance_sessions (" +
                "id           INT AUTO_INCREMENT PRIMARY KEY, " +
                "session_name VARCHAR(150) NOT NULL, " +
                "session_date DATE         NOT NULL, " +
                "ministry_id  INT, " +
                "created_by   INT, " +
                "is_closed    TINYINT(1)   NOT NULL DEFAULT 0 " +
                "             COMMENT '1 = session completed/closed, 0 = still open', " +
                "created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (ministry_id) REFERENCES ministries(id) ON DELETE SET NULL, " +
                "FOREIGN KEY (created_by)  REFERENCES users(id)      ON DELETE SET NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            "CREATE INDEX idx_att_sess_closed ON attendance_sessions (is_closed)",

            "CREATE TABLE IF NOT EXISTS attendance_records (id INT AUTO_INCREMENT PRIMARY KEY, session_id INT NOT NULL, member_id INT NOT NULL, is_present TINYINT(1) DEFAULT 0, synced_from VARCHAR(100), synced_at DATETIME DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY unique_attendance (session_id, member_id), FOREIGN KEY (session_id) REFERENCES attendance_sessions(id) ON DELETE CASCADE, FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS guests (id INT AUTO_INCREMENT PRIMARY KEY, full_name VARCHAR(100) NOT NULL, phone VARCHAR(20), gender ENUM('Male','Female','Other'), sub_branch_id INT, invited_by VARCHAR(100), wants_membership TINYINT(1) DEFAULT 0, prayer_request TEXT, session_id INT, synced_from VARCHAR(100), visit_date DATE, status ENUM('Guest','Converted','Dismissed') DEFAULT 'Guest', created_at DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (sub_branch_id) REFERENCES sub_branches(id) ON DELETE SET NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS app_tokens (id INT AUTO_INCREMENT PRIMARY KEY, device_name VARCHAR(100), token VARCHAR(255) NOT NULL UNIQUE, is_active TINYINT(1) DEFAULT 1, created_at DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS events (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(150) NOT NULL, event_date DATE NOT NULL, event_time TIME, location VARCHAR(150), description TEXT, category VARCHAR(50), created_by INT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS website_events (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(200) NOT NULL, description LONGTEXT, image_filename VARCHAR(255), event_date DATE, published_at DATETIME DEFAULT CURRENT_TIMESTAMP, created_by INT, poster_path VARCHAR(500), FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS website_blogs (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(200) NOT NULL, author VARCHAR(100), content LONGTEXT NOT NULL, published_at DATETIME DEFAULT CURRENT_TIMESTAMP, created_by INT, FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS communications (id INT AUTO_INCREMENT PRIMARY KEY, subject VARCHAR(200), message_body LONGTEXT NOT NULL, channel ENUM('Email','SMS') NOT NULL, recipient_type ENUM('Individual','Ministry','Sub-branch','All') NOT NULL, recipient_id INT, sent_by INT, sent_at DATETIME DEFAULT CURRENT_TIMESTAMP, recipient_count INT DEFAULT 0, recipient_group VARCHAR(100), FOREIGN KEY (sent_by) REFERENCES users(id) ON DELETE SET NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS announcements (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(255) NOT NULL DEFAULT 'Church Announcement', announcement_text LONGTEXT NOT NULL, channels VARCHAR(100), attachment_path VARCHAR(500), attachment_name VARCHAR(255), whatsapp_post_id VARCHAR(200), facebook_post_id VARCHAR(200), status VARCHAR(100), posted_by INT, posted_at DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (posted_by) REFERENCES users(id) ON DELETE SET NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS broadcast_uploads (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(200) NOT NULL, description TEXT, facebook_url VARCHAR(500), youtube_video_id VARCHAR(50), youtube_url VARCHAR(200), local_file_path VARCHAR(500), privacy VARCHAR(20) DEFAULT 'public', status VARCHAR(50) DEFAULT 'Pending', uploaded_by INT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE SET NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS board_members (id INT AUTO_INCREMENT PRIMARY KEY, member_id INT NOT NULL, role VARCHAR(100) NOT NULL DEFAULT '', role_title VARCHAR(100), start_date DATE, end_date DATE, is_active TINYINT(1) DEFAULT 1, FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS board_meetings (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(150) NOT NULL, meeting_date DATE NOT NULL, location VARCHAR(150), agenda TEXT, minutes_text LONGTEXT, status ENUM('Upcoming','Completed') DEFAULT 'Upcoming', agenda_doc_path VARCHAR(500), minutes_doc_path VARCHAR(500), created_by INT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS board_meeting_attendees (id INT AUTO_INCREMENT PRIMARY KEY, meeting_id INT NOT NULL, member_id INT NOT NULL, attended TINYINT(1) DEFAULT 1, apology TINYINT(1) DEFAULT 0, FOREIGN KEY (meeting_id) REFERENCES board_meetings(id) ON DELETE CASCADE, FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS board_meeting_files (id INT AUTO_INCREMENT PRIMARY KEY, meeting_id INT NOT NULL, file_name VARCHAR(255) NOT NULL, file_path VARCHAR(255) NOT NULL, uploaded_at DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (meeting_id) REFERENCES board_meetings(id) ON DELETE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS welfare_workers (id INT AUTO_INCREMENT PRIMARY KEY, member_id INT NOT NULL UNIQUE, assigned_at DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS welfare_cases (id INT AUTO_INCREMENT PRIMARY KEY, member_id INT NOT NULL, reason TEXT NOT NULL, assigned_worker_id INT, report LONGTEXT, status ENUM('Pending','In Progress','Completed') DEFAULT 'Pending', opened_at DATETIME DEFAULT CURRENT_TIMESTAMP, completed_at DATETIME, FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE, FOREIGN KEY (assigned_worker_id) REFERENCES welfare_workers(id) ON DELETE SET NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS deceased_members (id INT AUTO_INCREMENT PRIMARY KEY, member_id INT NOT NULL UNIQUE, date_of_death DATE, obituary TEXT, recorded_by INT, recorded_at DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE, FOREIGN KEY (recorded_by) REFERENCES users(id) ON DELETE SET NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS audit_log (id INT AUTO_INCREMENT PRIMARY KEY, user_id INT, action TEXT NOT NULL, performed_at DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS system_settings (id INT AUTO_INCREMENT PRIMARY KEY, setting_key VARCHAR(100) NOT NULL UNIQUE, setting_value TEXT, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS inventory_items (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "item_name VARCHAR(150) NOT NULL, " +
                "category VARCHAR(50) NOT NULL DEFAULT 'Other', " +
                "quantity INT NOT NULL DEFAULT 1, " +
                "unit VARCHAR(30) DEFAULT 'pcs', " +
                "item_condition VARCHAR(30) NOT NULL DEFAULT 'Good', " +
                "location VARCHAR(150), " +
                "custodian VARCHAR(150), " +
                "purchase_date DATE, " +
                "purchase_value DECIMAL(12,2), " +
                "low_stock_threshold INT DEFAULT 0, " +
                "notes TEXT, " +
                "is_deleted TINYINT(1) DEFAULT 0, " +
                "created_by INT, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

            // Default data
            "INSERT IGNORE INTO ministries (id, name, is_active) VALUES (1,'Women''s Ministry',1),(2,'Men''s Ministry',1),(3,'Youth',1),(4,'Sunday School',1)",
            "INSERT IGNORE INTO system_settings (setting_key, setting_value) VALUES "
                + "('smtp_host','smtp.gmail.com'),('smtp_port','587'),('smtp_user',''),('smtp_password',''),"
                + "('bulksms_key',''),('bulksms_secret',''),('bulksms_sender','AFMVFCC'),"
                + "('backup_location',''),('website_folder',''),('broadcast_download_folder',''),"
                + "('whatsapp_token',''),('whatsapp_phone_id',''),('whatsapp_recipient',''),"
                + "('facebook_page_token',''),('facebook_page_id',''),"
                + "('github_token',''),('github_owner',''),('github_repo',''),"
                + "('github_branch','main'),('website_custom_domain','')"
        };

        for (String sql : ddl) {
            try {
                conn.createStatement().executeUpdate(sql);
            } catch (SQLException e) {
                if (!e.getMessage().toLowerCase().contains("duplicate key name"))
                    System.err.println("[DB] DDL warning: " + e.getMessage());
            }
        }
        System.out.println("[DB] All tables created/verified (attendance_sessions includes is_closed).");
    }

    // ── Connection ────────────────────────────────────────────

    public static Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                Properties p = loadConfig();
                String host     = p.getProperty("db.host",     DEFAULT_HOST);
                String port     = p.getProperty("db.port",     DEFAULT_PORT);
                String database = p.getProperty("db.database", DEFAULT_DATABASE);
                String username = p.getProperty("db.username", DEFAULT_USERNAME);
                String password = p.getProperty("db.password", "");

                String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(url, username, password);
                System.out.println("[DB] Connected: " + host + ":" + port + "/" + database);

                ensureAttendanceSessionsMigration(connection);
                ensureGitHubSettingsMigration(connection);
                ensureUserAccountRoleMigration(connection);
            }
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] JDBC Driver not found."); e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("[DB] Connection failed: " + e.getMessage()); e.printStackTrace();
        }
        return connection;
    }

    private static void ensureAttendanceSessionsMigration(Connection conn) {
        try {
            ResultSet rs = conn.getMetaData().getColumns(
                null, null, "attendance_sessions", "is_closed");
            if (!rs.next()) {
                conn.createStatement().executeUpdate(
                    "ALTER TABLE attendance_sessions " +
                    "ADD COLUMN is_closed TINYINT(1) NOT NULL DEFAULT 0 " +
                    "COMMENT '1 = session completed/closed, 0 = still open'");
                System.out.println("[DB] Migration: added is_closed to attendance_sessions");
            }
            rs.close();
        } catch (SQLException e) {
            System.err.println("[DB] Migration check failed: " + e.getMessage());
        }

        try {
            ResultSet ri = conn.getMetaData().getIndexInfo(
                null, null, "attendance_sessions", false, false);
            boolean found = false;
            while (ri.next()) {
                if ("idx_att_sess_closed".equalsIgnoreCase(ri.getString("INDEX_NAME"))) {
                    found = true; break;
                }
            }
            ri.close();
            if (!found) {
                conn.createStatement().executeUpdate(
                    "CREATE INDEX idx_att_sess_closed ON attendance_sessions (is_closed)");
                System.out.println("[DB] Migration: created idx_att_sess_closed");
            }
        } catch (SQLException e) {
            System.err.println("[DB] Index migration skipped: " + e.getMessage());
        }
    }

    private static void ensureUserAccountRoleMigration(Connection conn) {
        try {
            ResultSet rs = conn.getMetaData().getColumns(null, null, "users", "account_role");
            if (!rs.next()) {
                conn.createStatement().executeUpdate(
                    "ALTER TABLE users ADD COLUMN account_role VARCHAR(20) NOT NULL DEFAULT 'admin' " +
                    "COMMENT 'admin = full desktop + app access, usher = mobile app only'");
                System.out.println("[DB] Migration: added account_role to users");
            }
            rs.close();
        } catch (SQLException e) {
            System.err.println("[DB] Migration check failed: " + e.getMessage());
        }
    }

    private static void ensureGitHubSettingsMigration(Connection conn) {
        String[][] settings = {
            { "github_token",          "" },
            { "github_owner",          "" },
            { "github_repo",           "" },
            { "github_branch",         "main" },
            { "website_custom_domain", "" },
        };
        for (String[] kv : settings) {
            try {
                PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO system_settings (setting_key, setting_value) VALUES (?, ?)");
                ps.setString(1, kv[0]);
                ps.setString(2, kv[1]);
                int affected = ps.executeUpdate();
                if (affected > 0)
                    System.out.println("[DB] Migration: inserted system_settings row '" + kv[0] + "'");
            } catch (SQLException e) {
                System.err.println("[DB] GitHub settings migration warning (" + kv[0] + "): " + e.getMessage());
            }
        }
    }

    public static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DB] Connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("[DB] Failed to close: " + e.getMessage());
        }
    }

    public static void closeAll() { closeConnection(); connection = null; }

    public static boolean testConnection() {
        try {
            Connection conn = getConnection();
            return conn != null && !conn.isClosed();
        } catch (SQLException e) { return false; }
    }
}