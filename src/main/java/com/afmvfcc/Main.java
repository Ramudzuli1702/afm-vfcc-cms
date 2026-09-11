package com.afmvfcc;

import com.afmvfcc.api.CmsApiServer;
import com.afmvfcc.controllers.BroadcastController;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.utils.SessionManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Label;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;

public class Main extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        primaryStage.setTitle("AFM VFCC Church Management System");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        try {
            Image icon = new Image(
                Main.class.getResourceAsStream("/com/afmvfcc/images/afm_logo_icon.png"));
            primaryStage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("[Main] Could not load window icon: " + e.getMessage());
        }

        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);

        showSplash();
        primaryStage.show();

        System.out.println("[Main] Testing database connection...");
        boolean dbConnected = false;

        try {
            dbConnected = DatabaseConnection.testConnection();
            System.out.println("[Main] Database connection test result: " + dbConnected);
        } catch (Exception e) {
            System.err.println("[Main] Exception during database test:");
            e.printStackTrace();
        }

        if (!dbConnected) {
            System.err.println("[Main] Database connection failed - showing alert");

            showAlert("Database Connection Error",
                "Could not connect to the database.\n\n" +
                "Please make sure:\n" +
                "• MySQL server is running\n" +
                "• Database configuration is correct\n\n" +
                "To re-run the setup wizard, delete the config file:\n" +
                DatabaseConnection.getConfigFilePath() + "\n\n" +
                "The application will now exit.");

            try { Thread.sleep(800); } catch (InterruptedException ignored) {}

            Platform.exit();
            return;
        }

        System.out.println("[Main] Database connected successfully. Loading login screen...");

        showLogin();

        // ── Window close (X button) ──────────────────────────────────────────
        // Block close if a broadcast download or upload is still running.
        // BroadcastController.confirmExitAllowed() shows a dialog and returns
        // true only when it is safe to proceed (or the user forces exit).
        primaryStage.setOnCloseRequest(e -> {
            if (!BroadcastController.confirmExitAllowed()) {
                e.consume();   // swallow the event — window stays open
                return;
            }
            System.out.println("[Main] Application closing...");
            SessionManager.getInstance().logout();
            DatabaseConnection.closeAll();
            CmsApiServer.stop();
            Platform.exit();
        });
    }

    /** Minimal splash screen */
    private static void showSplash() {
        StackPane splash = new StackPane();
        splash.setStyle("-fx-background-color: #1B3A6B;");

        Label lbl = new Label("Starting AFM VFCC CMS...");
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");
        splash.getChildren().add(lbl);

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        Scene splashScene = new Scene(splash, screen.getWidth(), screen.getHeight());

        primaryStage.setScene(splashScene);
        primaryStage.setMaximized(true);
    }

    public static void showLogin() {
        try {
            Parent root = FXMLLoader.load(
                Main.class.getResource("/com/afmvfcc/fxml/login.fxml")
            );

            Rectangle2D screen = Screen.getPrimary().getVisualBounds();
            Scene scene = new Scene(root, screen.getWidth(), screen.getHeight());

            scene.getStylesheets().add(
                Main.class.getResource("/com/afmvfcc/css/styles.css").toExternalForm()
            );

            primaryStage.setScene(scene);
            primaryStage.setResizable(true);
            primaryStage.setMaximized(true);
            primaryStage.centerOnScreen();

            System.out.println("[Main] Login screen loaded successfully");
        } catch (Exception e) {
            System.err.println("[Main] Failed to load login.fxml:");
            e.printStackTrace();
        }
    }

    public static void showMainLayout() {
        try {
            new Thread(() -> {
                try {
                    CmsApiServer.start();
                } catch (Exception e) {
                    System.err.println("[Main] API server failed to start: " + e.getMessage());
                }
            }, "api-server").start();

            Parent root = FXMLLoader.load(
                Main.class.getResource("/com/afmvfcc/fxml/main_layout.fxml")
            );

            Scene scene = new Scene(root, 1280, 780);
            scene.getStylesheets().add(
                Main.class.getResource("/com/afmvfcc/css/styles.css").toExternalForm()
            );

            scene.setOnMouseMoved(e -> SessionManager.getInstance().resetTimer());
            scene.setOnKeyPressed(e -> SessionManager.getInstance().resetTimer());

            primaryStage.setScene(scene);
            primaryStage.setResizable(true);
            primaryStage.setMaximized(false);
            Platform.runLater(() -> primaryStage.setMaximized(true));

            System.out.println("[Main] Main layout loaded successfully");
        } catch (Exception e) {
            System.err.println("[Main] Failed to load main_layout.fxml:");
            e.printStackTrace();
        }
    }

    private static void showAlert(String title, String message) {
        javafx.scene.control.Alert alert =
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);

        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        applyStyles(alert.getDialogPane());
        alert.showAndWait();
    }

    public static void applyStyles(javafx.scene.layout.Region region) {
        try {
            region.getStylesheets().add(
                Main.class.getResource("/com/afmvfcc/css/styles.css").toExternalForm()
            );
            region.setStyle("-fx-background-color: #FFFFFF;");
        } catch (Exception e) {
            System.err.println("[Main] Could not apply stylesheet to dialog: " + e.getMessage());
        }
    }

    public static Stage getPrimaryStage() { return primaryStage; }

    public static void main(String[] args) { launch(args); }
}