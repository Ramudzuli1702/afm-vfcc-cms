package com.afmvfcc.utils;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Builds a form window in the app's standard layout (the one introduced by the
 * member form): white header with title + hint, scrollable body of titled
 * section cards with each label above its input and related inputs side by side
 * in equal columns, an error line, and a footer with secondary actions on the
 * left and Cancel / Save on the right. The window is sized to fit the form.
 *
 * <pre>
 *   FormBuilder f = new FormBuilder("Add Inventory Item");
 *   f.section("Item").field("ITEM NAME *", nameField)
 *    .row("CATEGORY *", categoryCombo, "CONDITION *", conditionCombo);
 *   Button save = f.saveButton("Add Item");
 *   save.setOnAction(e -> { if (bad) { f.showError("..."); return; } ... });
 *   f.show(560);
 * </pre>
 */
public class FormBuilder {

    private final Stage      stage = new Stage();
    private final VBox       root;
    private final Label      titleLbl;
    private final Label      subtitleLbl;
    private final VBox       sections = new VBox(14);
    private final ScrollPane scroll;
    private final Label      error = new Label();
    private final HBox       footerLeft  = new HBox(10);
    private final HBox       footerRight = new HBox(10);
    private VBox current;

    public FormBuilder(String title) {
        this(title, "Fields marked * are required.");
    }

    public FormBuilder(String title, String subtitle) {
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(title);

        titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size:18px;-fx-font-weight:700;-fx-text-fill:#1E2130;");
        subtitleLbl = new Label(subtitle);
        subtitleLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#9099AA;");
        subtitleLbl.setVisible(subtitle != null && !subtitle.isBlank());
        subtitleLbl.setManaged(subtitleLbl.isVisible());
        VBox header = new VBox(2, titleLbl, subtitleLbl);
        header.setStyle("-fx-background-color:#FFFFFF;-fx-padding:16 28;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");

        error.setStyle("-fx-text-fill:#D94040;-fx-font-size:12px;-fx-font-weight:600;");
        error.setWrapText(true);
        error.setMaxWidth(Double.MAX_VALUE);
        clearError();

        VBox bodyContent = new VBox(14, sections, error);
        bodyContent.setStyle("-fx-padding:22 28;-fx-background-color:#F5F6FA;");
        scroll = new ScrollPane(bodyContent);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:transparent;-fx-background:transparent;-fx-border-color:transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        footerLeft.setAlignment(Pos.CENTER_LEFT);
        footerRight.setAlignment(Pos.CENTER_RIGHT);
        HBox footer = new HBox(10, footerLeft, spacer, footerRight);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-background-color:#FFFFFF;-fx-padding:14 28;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");

        root = new VBox(header, scroll, footer);
        root.setStyle("-fx-background-color:#F5F6FA;");
    }

    public Stage stage() { return stage; }

    /** Title-bar icon for this form, e.g. "fas-user-plus" (see {@link Icons#setWindowIcon}). */
    public FormBuilder icon(String iconLiteral) {
        Icons.setWindowIcon(stage, iconLiteral);
        return this;
    }

    /** Changes the header (e.g. "Add ..." vs "Edit ..."). */
    public FormBuilder title(String title) {
        titleLbl.setText(title);
        stage.setTitle(title);
        return this;
    }

    // ── Sections ───────────────────────────────────────────────

    /** Starts a new titled section card; following fields go into it. */
    public FormBuilder section(String title) {
        current = new VBox(12);
        current.getStyleClass().add("form-section");
        if (title != null && !title.isBlank()) {
            Label t = new Label(title.toUpperCase());
            t.getStyleClass().add("form-section-title");
            current.getChildren().add(t);
        }
        sections.getChildren().add(current);
        return this;
    }

    /** A full-width labelled input. */
    public FormBuilder field(String label, Node input) {
        target().getChildren().add(labelled(label, input));
        return this;
    }

    /** Labelled inputs side by side in equal columns: row("A", a, "B", b, ...). */
    public FormBuilder row(Object... labelsAndInputs) {
        int cols = labelsAndInputs.length / 2;
        GridPane g = new GridPane();
        g.setHgap(14);
        for (int i = 0; i < cols; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / cols);
            g.getColumnConstraints().add(c);
            g.add(labelled((String) labelsAndInputs[i * 2], (Node) labelsAndInputs[i * 2 + 1]), i, 0);
        }
        target().getChildren().add(g);
        return this;
    }

    /** Any other content (checkbox lists, file pickers, previews...) inside the current section. */
    public FormBuilder node(Node n) {
        target().getChildren().add(n);
        return this;
    }

    /** A small muted note inside the current section. */
    public FormBuilder hint(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-font-size:11px;-fx-text-fill:#9099AA;");
        return node(l);
    }

    private VBox target() {
        if (current == null) section(null);
        return current;
    }

    // ── Errors ─────────────────────────────────────────────────

    public void showError(String msg) {
        error.setText(msg);
        error.setVisible(true);
        error.setManaged(true);
    }

    public void clearError() {
        error.setVisible(false);
        error.setManaged(false);
    }

    // ── Footer ─────────────────────────────────────────────────

    /** Adds Cancel (closes) and a primary save button to the footer; returns the save button. */
    public Button saveButton(String text) {
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("btn-secondary");
        cancel.setOnAction(e -> stage.close());
        Button save = new Button(text);
        save.getStyleClass().add("btn-primary");
        save.setDefaultButton(false);
        footerRight.getChildren().addAll(cancel, save);
        return save;
    }

    /** Extra buttons on the left of the footer (e.g. Print, Preview). */
    public FormBuilder footerLeft(Node... nodes) {
        footerLeft.getChildren().addAll(nodes);
        return this;
    }

    public void close() { stage.close(); }

    // ── Show ───────────────────────────────────────────────────

    /** Shows the window {@code width} px wide, sized to fit, and waits until it closes. */
    public void show(double width) {
        Dialogs.ownByActiveWindow(stage);
        stage.setScene(Dialogs.fittedScene(root, scroll, width));
        stage.showAndWait();
    }

    // ── Helpers ────────────────────────────────────────────────

    /** Label above input; stretches the input to the column width. */
    public static VBox labelled(String label, Node input) {
        Label l = new Label(label);
        l.getStyleClass().add("form-label");
        if (input instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        return new VBox(6, l, input);
    }

    public static TextField text(String prompt) {
        TextField f = new TextField();
        f.getStyleClass().add("form-field");
        f.setPromptText(prompt);
        return f;
    }

    public static TextArea area(String prompt, double height) {
        TextArea a = new TextArea();
        a.getStyleClass().add("form-textarea");
        a.setWrapText(true);
        a.setPromptText(prompt);
        a.setPrefHeight(height);
        a.setMinHeight(height);
        return a;
    }

    public static <T> ComboBox<T> combo(String prompt) {
        ComboBox<T> c = new ComboBox<>();
        c.getStyleClass().add("form-combo");
        c.setPromptText(prompt);
        return c;
    }

    public static DatePicker date() {
        DatePicker d = new DatePicker();
        d.getStyleClass().add("form-date-picker");
        return d;
    }

    /**
     * Single-field prompt in the standard form style (replaces TextInputDialog).
     * Returns the trimmed text, or empty if cancelled or left blank.
     */
    public static Optional<String> prompt(String title, String label, String initial, String saveText,
                                          String iconLiteral) {
        FormBuilder f = new FormBuilder(title, null).icon(iconLiteral);
        TextField field = text(label.toLowerCase());
        if (initial != null) field.setText(initial);
        f.section(null).field(label, field);
        String[] result = { null };
        Button save = f.saveButton(saveText);
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            String v = field.getText().trim();
            if (v.isEmpty()) { f.showError(label.replace(" *", "") + " is required."); return; }
            result[0] = v;
            f.close();
        });
        f.show(440);
        return Optional.ofNullable(result[0]);
    }
}
