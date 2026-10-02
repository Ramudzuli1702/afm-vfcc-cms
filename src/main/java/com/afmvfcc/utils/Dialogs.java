package com.afmvfcc.utils;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.stage.Screen;

/**
 * Helpers for the app's modal form/viewer windows.
 *
 * Most dialogs are header + scrollable body + footer. Giving their Scene a
 * fixed height meant long forms opened cut off (you had to drag the window
 * taller to reach the last fields). {@link #fittedScene} instead sizes the
 * window so the whole body is visible, only falling back to scrolling when
 * the form is taller than the screen.
 */
public class Dialogs {

    private static final String CSS = "/com/afmvfcc/css/styles.css";

    /** Space left for the window title bar/borders and the taskbar margin. */
    private static final double SCREEN_MARGIN = 90;

    /**
     * Builds a Scene for {@code root} at the given width whose height fits the
     * content of {@code body} (capped to the screen). The stylesheet is added.
     */
    /**
     * Makes {@code dialog} owned by the window that is active right now (the main
     * window, or a viewer it was opened from). An owned window always stays in
     * front of its owner, so a form can't end up hidden behind the main window —
     * which, being modal, would leave the whole app looking frozen.
     * Must be called before the dialog is shown.
     */
    public static void ownByActiveWindow(javafx.stage.Stage dialog) {
        javafx.stage.Window owner = javafx.stage.Window.getWindows().stream()
                .filter(w -> w.isShowing() && w.isFocused() && w != dialog)
                .findFirst()
                .orElseGet(() -> com.afmvfcc.Main.getPrimaryStage());
        if (owner != null && owner.isShowing() && dialog.getOwner() == null) dialog.initOwner(owner);
    }

    /**
     * Same as {@link #fittedScene(Parent, ScrollPane, double)}, finding the body
     * ScrollPane itself. Forms without one get a window sized to their natural height.
     */
    public static Scene fittedScene(Parent root, double width) {
        // Looked up before the Scene exists, so TextArea's internal scroller (created
        // with its skin) can't be mistaken for the form body.
        if (root.lookup(".scroll-pane") instanceof ScrollPane body)
            return fittedScene(root, body, width);
        if (root instanceof Region r) r.setPrefWidth(width);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(Dialogs.class.getResource(CSS).toExternalForm());
        scene.setFill(javafx.scene.paint.Color.web("#F5F6FA"));
        return scene;
    }

    public static Scene fittedScene(Parent root, ScrollPane body, double width) {
        if (root instanceof Region r) r.setPrefWidth(width);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(Dialogs.class.getResource(CSS).toExternalForm());
        scene.setFill(javafx.scene.paint.Color.web("#F5F6FA"));

        root.applyCss();
        // fitToWidth bodies lay their content out at the viewport width; a few px of
        // allowance covers the ScrollPane's own insets so wrapped text measures right.
        double contentH = body.getContent().prefHeight(width - 6) + 4;
        double chrome   = root.prefHeight(width) - body.prefHeight(width);
        double maxBody  = Screen.getPrimary().getVisualBounds().getHeight() - SCREEN_MARGIN - chrome;
        body.setPrefViewportHeight(Math.max(120, Math.min(contentH, maxBody)));
        return scene;
    }
}
