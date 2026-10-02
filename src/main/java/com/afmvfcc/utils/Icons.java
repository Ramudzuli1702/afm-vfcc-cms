package com.afmvfcc.utils;

import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Central factory for the app's vector icons (Font Awesome 5, via Ikonli) —
 * replaces emoji throughout the UI so icons render identically across every
 * Windows machine instead of depending on whatever emoji font happens to be
 * installed. Keeping creation here (rather than scattering `new FontIcon(...)`
 * calls everywhere) keeps sizing/colour choices consistent app-wide.
 */
public class Icons {

    // ── Brand palette (matches styles.css) ──────────────────────
    public static final String NAVY      = "#1E2130";
    public static final String GOLD      = "#C9A84C";
    public static final String BLUE      = "#3A86C8";
    public static final String MUTED     = "#9099AA";
    public static final String SIDEBAR   = "#7C89A8"; // inactive sidebar icon colour
    public static final String WHITE     = "#FFFFFF";
    public static final String RED       = "#D94040";
    public static final String GREEN     = "#2E7D4F";

    public static FontIcon of(String literal) {
        return of(literal, 14, MUTED);
    }

    public static FontIcon of(String literal, double size) {
        return of(literal, size, MUTED);
    }

    public static FontIcon of(String literal, double size, String colorHex) {
        FontIcon icon = new FontIcon(literal);
        icon.setIconSize((int) size);
        icon.setIconColor(Color.web(colorHex));
        return icon;
    }

    /** Sidebar nav icon — consistent small size + muted colour. */
    public static FontIcon nav(String literal) {
        return of(literal, 15, SIDEBAR);
    }

    /** Icon for a button whose text is dark (btn-secondary, plain buttons). */
    public static FontIcon dark(String literal) {
        return of(literal, 13, NAVY);
    }

    /** Icon for a button whose text/background is white (btn-primary, btn-danger). */
    public static FontIcon white(String literal) {
        return of(literal, 13, WHITE);
    }

    // ── Status messages ─────────────────────────────────────────

    /** Kinds of inline status message, each with its own icon and colour. */
    public enum Status { OK, ERROR, WARNING, INFO, BUSY, LOCKED }

    /**
     * Shows a status message on {@code label} with a vector icon (check, cross,
     * warning, ...) in the matching colour — the app's one way of showing
     * success/failure text, instead of ✓ / ✗ / ⚠ characters whose look depends
     * on the installed fonts.
     */
    public static void status(javafx.scene.control.Label label, String text, Status kind) {
        String color = switch (kind) {
            case OK      -> GREEN;
            case ERROR   -> RED;
            case WARNING -> "#C07800";
            case INFO    -> BLUE;
            case BUSY, LOCKED -> "#5A6275";
        };
        String icon = switch (kind) {
            case OK      -> "fas-check-circle";
            case ERROR   -> "fas-times-circle";
            case WARNING -> "fas-exclamation-triangle";
            case INFO    -> "fas-info-circle";
            case BUSY    -> "fas-hourglass-half";
            case LOCKED  -> "fas-lock";
        };
        label.setText(text);
        label.setGraphic(of(icon, 12, color));
        label.setGraphicTextGap(6);
        label.setStyle("-fx-text-fill:" + color + ";-fx-font-size:12px;-fx-font-weight:600;");
    }

    // ── Window (title-bar / taskbar) icons ──────────────────────

    private static final java.util.Map<String, java.util.List<javafx.scene.image.Image>> WINDOW_ICONS =
            new java.util.HashMap<>();
    private static javafx.scene.image.Image appIcon;

    /**
     * Title-bar icon for a window: the glyph in white on a rounded tile in the
     * app's blue, rendered at several sizes so Windows picks a sharp one at any
     * display scaling. Call on the FX thread.
     */
    public static void setWindowIcon(javafx.stage.Stage stage, String literal) {
        stage.getIcons().setAll(WINDOW_ICONS.computeIfAbsent(literal, l -> {
            java.util.List<javafx.scene.image.Image> sizes = new java.util.ArrayList<>();
            for (int px : new int[] { 16, 20, 24, 32, 40, 48, 64 }) sizes.add(renderTile(l, px));
            return sizes;
        }));
    }

    private static javafx.scene.image.Image renderTile(String literal, int px) {
        javafx.scene.shape.Rectangle tile = new javafx.scene.shape.Rectangle(px, px);
        tile.setArcWidth(px * 0.42);
        tile.setArcHeight(px * 0.42);
        tile.setFill(Color.web(BLUE));
        FontIcon glyph = new FontIcon(literal);
        glyph.setIconSize((int) Math.round(px * 0.58));
        glyph.setIconColor(Color.WHITE);
        javafx.scene.layout.StackPane pane = new javafx.scene.layout.StackPane(tile, glyph);
        pane.setStyle("-fx-background-color: transparent;");
        // In a (transparent) scene so the icon font is styled before the snapshot
        new javafx.scene.Scene(pane, px, px, Color.TRANSPARENT);
        javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        javafx.scene.image.WritableImage snap = pane.snapshot(params, null);
        // Windows ignores a snapshot image as a title-bar icon (it shows the blank
        // default window glyph instead), but accepts the same pixels decoded from
        // PNG — so round-trip it, making it an ordinary loaded image like the logo.
        try {
            java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(snap, null), "png", png);
            return new javafx.scene.image.Image(new java.io.ByteArrayInputStream(png.toByteArray()));
        } catch (java.io.IOException e) {
            return snap;
        }
    }

    /** The church logo used for the main window and as the default for every other window. */
    public static javafx.scene.image.Image appIcon() {
        if (appIcon == null) {
            try (var in = Icons.class.getResourceAsStream("/com/afmvfcc/images/afm_logo_icon.png")) {
                if (in != null) appIcon = new javafx.scene.image.Image(in);
            } catch (Exception ignored) {}
        }
        return appIcon;
    }

    /**
     * Gives every window that doesn't set its own icon (alerts, file pickers' owners,
     * one-off dialogs) the church logo instead of the default Java cup.
     * Call once at start-up.
     */
    public static void useAppIconByDefault() {
        javafx.stage.Window.getWindows().addListener(
            (javafx.collections.ListChangeListener<javafx.stage.Window>) change -> {
                while (change.next()) {
                    for (javafx.stage.Window w : change.getAddedSubList()) {
                        if (w instanceof javafx.stage.Stage s && s.getIcons().isEmpty() && appIcon() != null)
                            s.getIcons().add(appIcon());
                    }
                }
            });
    }
}
