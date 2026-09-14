package com.afmvfcc.utils;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * App-wide "Success" / "Failed" toast notifications. MainLayoutController
 * registers its toast layer (a VBox floating above the whole window) once
 * on startup via {@link #attach}; any controller can then call
 * {@link #success} / {@link #error} from anywhere without holding a
 * reference to the layout, the same way SessionManager works as a singleton.
 *
 * Falls back to a console log if called before a layout is attached (e.g.
 * from a background thread during a very early startup step), rather than
 * throwing - a missing toast should never be the reason an action fails.
 */
public class ToastManager {

    private static VBox container;

    public static void attach(VBox toastContainer) {
        container = toastContainer;
    }

    public static void success(String message) { show(message, true); }
    public static void error(String message)   { show(message, false); }

    private static void show(String message, boolean isSuccess) {
        if (container == null) {
            System.out.println("[Toast] (" + (isSuccess ? "success" : "error") + ") " + message);
            return;
        }
        Runnable build = () -> {
            HBox toast = new HBox(12);
            toast.setAlignment(Pos.CENTER_LEFT);
            toast.setMinWidth(280);
            toast.setMaxWidth(380);
            toast.setStyle(
                "-fx-background-color:" + (isSuccess ? "#E6F4EC" : "#FFF0F0") + ";" +
                "-fx-border-color:" + (isSuccess ? "#2E7D4F" : "#D94040") + ";" +
                "-fx-border-width:0 0 0 5;" +
                "-fx-background-radius:12;" +
                "-fx-padding:14 20;" +
                "-fx-effect:dropshadow(gaussian, rgba(42,53,80,0.30), 20, 0, 0, 6);"
            );

            Label icon = new Label(isSuccess ? "✓" : "✕");
            icon.setMinSize(26, 26);
            icon.setMaxSize(26, 26);
            icon.setAlignment(Pos.CENTER);
            icon.setStyle(
                "-fx-font-size:14px;-fx-font-weight:800;-fx-text-fill:white;" +
                "-fx-background-color:" + (isSuccess ? "#2E7D4F" : "#D94040") + ";" +
                "-fx-background-radius:100;"
            );

            Label title = new Label(isSuccess ? "Success" : "Failed");
            title.setStyle(
                "-fx-font-size:13.5px;-fx-font-weight:800;" +
                "-fx-text-fill:" + (isSuccess ? "#1E4A2E" : "#7A2020") + ";"
            );

            Label text = new Label(message);
            text.setWrapText(true);
            text.setStyle(
                "-fx-font-size:12.5px;-fx-font-weight:600;" +
                "-fx-text-fill:" + (isSuccess ? "#1E4A2E" : "#7A2020") + ";"
            );

            VBox textBox = new VBox(2, title, text);
            toast.getChildren().addAll(icon, textBox);
            toast.setOpacity(0);
            container.getChildren().add(toast);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(180), toast);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            PauseTransition hold = new PauseTransition(Duration.seconds(3.8));

            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toast);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> container.getChildren().remove(toast));

            new SequentialTransition(fadeIn, hold, fadeOut).play();
        };

        if (Platform.isFxApplicationThread()) build.run();
        else Platform.runLater(build);
    }
}
