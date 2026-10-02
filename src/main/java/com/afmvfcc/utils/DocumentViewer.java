package com.afmvfcc.utils;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Read-only record window used by every "View" button in the app. Content is
 * laid out like a printed page and cannot be changed in place — the only ways
 * to alter the record are the buttons in the footer (Edit, Delete and any
 * record-specific actions), which hand control back to the calling controller.
 *
 * Usage:
 *   new DocumentViewer("Board Meeting", meeting.getTitle())
 *       .status("Completed", "badge-completed")
 *       .meta("Date", "12 Mar 2026")
 *       .section("Minutes", meeting.getMinutesText(), "No minutes recorded.")
 *       .onEdit(() -> openMeetingDialog(meeting))
 *       .onDelete(() -> deleteMeeting(meeting))
 *       .show();
 */
public class DocumentViewer {

    private final String eyebrow;
    private final String title;
    private String statusText, statusClass;
    private Node   avatar;
    private double width = 820;
    private final List<String[]> meta     = new ArrayList<>();
    private final List<Node>     sections = new ArrayList<>();
    private final List<Button>   extraActions = new ArrayList<>();
    private Runnable        onEdit;
    private BooleanSupplier onDelete;
    private String editLabel = "Edit", deleteLabel = "Delete";
    private Stage           stage;
    private Runnable        afterClose;
    private Label           statusBadge;
    private String          windowIcon;

    public DocumentViewer(String eyebrow, String title) {
        this.eyebrow = eyebrow;
        this.title   = title;
    }

    public DocumentViewer status(String text, String badgeStyleClass) {
        this.statusText  = text;
        this.statusClass = badgeStyleClass;
        return this;
    }

    /** Picture shown beside the title (e.g. a member's profile photo). */
    public DocumentViewer avatar(Node avatar) { this.avatar = avatar; return this; }

    /** Title-bar icon for this window, e.g. "fas-id-card" (see {@link Icons#setWindowIcon}). */
    public DocumentViewer icon(String iconLiteral) { this.windowIcon = iconLiteral; return this; }

    /** Window width; the height always fits the content. */
    public DocumentViewer width(double w) { this.width = w; return this; }

    /** A label/value pair in the details block at the top of the page. */
    public DocumentViewer meta(String label, String value) {
        meta.add(new String[]{ label, value });
        return this;
    }

    /** A titled block of body text; {@code emptyText} is shown in italics when the text is blank. */
    public DocumentViewer section(String heading, String body, String emptyText) {
        boolean blank = body == null || body.isBlank();
        Label text = new Label(blank ? emptyText : body.strip());
        text.getStyleClass().add(blank ? "doc-empty" : "doc-body");
        text.setWrapText(true);
        text.setMaxWidth(Double.MAX_VALUE);
        return section(heading, text);
    }

    /** A titled block with arbitrary content (lists, file links, images, etc.). */
    public DocumentViewer section(String heading, Node content) {
        Label h = new Label(heading.toUpperCase());
        h.getStyleClass().add("doc-section-title");
        VBox box = new VBox(8, h, content);
        sections.add(box);
        return this;
    }

    /** Extra footer button that keeps the window open (e.g. Export, Print). */
    public DocumentViewer action(String text, String iconLiteral, Runnable handler) {
        extraActions.add(footerButton(text, iconLiteral, "btn-secondary", () -> { handler.run(); return false; }));
        return this;
    }

    /**
     * Extra footer button for a record-changing action (Restore, Approve, Unlock...).
     * The handler should confirm and act, returning true if it changed the record —
     * the window then closes, since what it shows is out of date.
     */
    public DocumentViewer closingAction(String text, String iconLiteral, String styleClass, BooleanSupplier handler) {
        extraActions.add(footerButton(text, iconLiteral, styleClass, handler));
        return this;
    }

    /** Runs after this window closes, so the editor never stacks on top of the read-only view. */
    public DocumentViewer onEdit(Runnable r) { this.onEdit = r; return this; }

    /** Should confirm and delete, returning true if the record was removed (the window then closes). */
    public DocumentViewer onDelete(BooleanSupplier r) { this.onDelete = r; return this; }

    public DocumentViewer editLabel(String s)   { this.editLabel = s;   return this; }
    public DocumentViewer deleteLabel(String s) { this.deleteLabel = s; return this; }

    public void close() { if (stage != null) stage.close(); }

    /**
     * Closes this window and runs {@code next} once it has fully closed. Use this
     * (never close() followed by opening another dialog) to move from the viewer to
     * an editor: opening a modal window while this one is still unwinding lets
     * Windows re-activate the main window on top of it, so the editor appears
     * blank or behind and nothing responds.
     */
    public void closeThen(Runnable next) {
        afterClose = next;
        close();
    }

    private Button footerButton(String text, String iconLiteral, String styleClass, BooleanSupplier handler) {
        Button b = new Button(text);
        b.getStyleClass().add(styleClass);
        if (iconLiteral != null) {
            String color = switch (styleClass) {
                case "btn-primary" -> Icons.WHITE;
                case "btn-danger"  -> Icons.RED;
                default            -> Icons.NAVY;
            };
            b.setGraphic(Icons.of(iconLiteral, 13, color));
            b.setGraphicTextGap(8);
        }
        b.setOnAction(e -> { if (handler.getAsBoolean()) close(); });
        return b;
    }

    public void show() {
        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(eyebrow + ": " + title);
        Dialogs.ownByActiveWindow(stage);
        if (windowIcon != null) Icons.setWindowIcon(stage, windowIcon);

        // ── Page ───────────────────────────────────────────────
        VBox page = new VBox(22);
        page.getStyleClass().add("doc-page");
        page.setMaxWidth(780);

        Label eyebrowLbl = new Label(eyebrow.toUpperCase());
        eyebrowLbl.getStyleClass().add("section-label");
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("doc-title");
        titleLbl.setWrapText(true);
        titleLbl.setMaxWidth(Double.MAX_VALUE);
        VBox titleText = new VBox(4, eyebrowLbl, titleLbl);
        // The spacer (not the title) takes the leftover width: whichever child grows
        // also absorbs HiDPI pixel rounding, which truncated the badge to "Acti...".
        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox titleRow = new HBox(16);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        if (avatar != null) titleRow.getChildren().add(avatar);
        titleRow.getChildren().addAll(titleText, titleSpacer);
        if (statusText != null) {
            statusBadge = new Label(statusText);
            statusBadge.getStyleClass().add(statusClass);
            titleRow.getChildren().add(statusBadge);
        }
        page.getChildren().add(titleRow);

        if (!meta.isEmpty()) {
            GridPane grid = new GridPane();
            grid.getStyleClass().add("doc-meta");
            grid.setHgap(28);
            grid.setVgap(10);
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(50);
            grid.getColumnConstraints().addAll(c, c);
            for (int i = 0; i < meta.size(); i++) {
                Label l = new Label(meta.get(i)[0].toUpperCase());
                l.getStyleClass().add("form-label");
                String v = meta.get(i)[1];
                Label val = new Label(v == null || v.isBlank() ? "—" : v);
                val.getStyleClass().add("doc-meta-value");
                val.setWrapText(true);
                grid.add(new VBox(3, l, val), i % 2, i / 2);
            }
            page.getChildren().add(grid);
        }
        page.getChildren().addAll(sections);

        StackPane pageHolder = new StackPane(page);
        pageHolder.setStyle("-fx-padding:24;");
        StackPane.setAlignment(page, Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(pageHolder);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:#F5F6FA;-fx-background-color:#F5F6FA;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // ── Footer ─────────────────────────────────────────────
        Label lockHint = new Label("Read-only");
        lockHint.setGraphic(Icons.of("fas-lock", 11, Icons.MUTED));
        lockHint.setGraphicTextGap(6);
        lockHint.setStyle("-fx-font-size:11px;-fx-text-fill:#9099AA;");
        lockHint.setMinWidth(Region.USE_PREF_SIZE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox footer = new HBox(10, lockHint, spacer);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-background-color:#FFFFFF;-fx-padding:14 24;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");
        footer.getChildren().addAll(extraActions);

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("btn-secondary");
        closeBtn.setOnAction(e -> stage.close());
        footer.getChildren().add(closeBtn);

        if (onDelete != null) {
            Button deleteBtn = footerButton(deleteLabel, "fas-trash-alt", "btn-danger", onDelete);
            footer.getChildren().add(deleteBtn);
        }
        if (onEdit != null) {
            Button editBtn = new Button(editLabel);
            editBtn.getStyleClass().add("btn-primary");
            editBtn.setGraphic(Icons.white("fas-pen"));
            editBtn.setGraphicTextGap(8);
            editBtn.setOnAction(e -> closeThen(onEdit));
            footer.getChildren().add(editBtn);
        }
        footer.getChildren().forEach(n -> { if (n instanceof Button b) b.setMinWidth(Region.USE_PREF_SIZE); });

        VBox root = new VBox(scroll, footer);
        root.setStyle("-fx-background-color:#F5F6FA;");
        stage.setScene(Dialogs.fittedScene(root, scroll, width));
        if (statusBadge != null) {
            // At fractional display scaling (e.g. 125%) JavaFX can round a label a fraction
            // of a pixel below its preferred width, which ellipsises short text ("Acti...").
            // Now that CSS (font) is applied, reserve a little headroom.
            statusBadge.setMinWidth(Math.ceil(statusBadge.prefWidth(-1)) + 2);
        }
        stage.showAndWait();
        if (afterClose != null) afterClose.run();
    }

    /** Small bulleted list for use inside {@link #section(String, Node)}. */
    public static VBox bulletList(List<String> items, String emptyText) {
        VBox box = new VBox(4);
        if (items.isEmpty()) {
            Label l = new Label(emptyText);
            l.getStyleClass().add("doc-empty");
            box.getChildren().add(l);
            return box;
        }
        for (String s : items) {
            Label l = new Label("•  " + s);
            l.getStyleClass().add("doc-body");
            l.setWrapText(true);
            box.getChildren().add(l);
        }
        return box;
    }
}
