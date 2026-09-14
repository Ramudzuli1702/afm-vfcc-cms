package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.SessionManager;
import com.afmvfcc.utils.ToastManager;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class CommemorationsController {

    private static final DateTimeFormatter DISPLAY_FMT =
        DateTimeFormatter.ofPattern("dd MMMM yyyy");

    @FXML public void initialize() {}

    // =========================================================
    // BAPTISM
    // =========================================================
    @FXML
    public void handleBaptismCert() {
        Stage stage = buildStage("Certificate of Baptism", 560, 560);
        VBox root = stageRoot();

        root.getChildren().add(sectionHeader("Certificate of Baptism",
            "BAPTISM", "#3A86C8"));

        VBox form = formBody();

        ComboBox<String> memberCombo = memberCombo();
        DatePicker       datePicker  = datePicker();
        TextField        minField    = textField("e.g. Ev. Maliaga HP");

        prefillMinister(minField);

        form.getChildren().addAll(
            formRow("RECIPIENT NAME *", memberCombo),
            formRow("DATE OF BAPTISM *", datePicker),
            formRow("CHURCH MINISTER", minField)
        );

        Label previewHint = hintLabel("Fill in the fields above then click Preview.");
        ImageView previewImg = previewImg();
        VBox previewBox = previewBox(previewHint, previewImg);
        form.getChildren().add(previewBox);

        Button previewBtn = new Button("Preview Certificate");
        Button saveBtn    = new Button("Save PDF...");
        Button cancelBtn  = new Button("Cancel");
        styleButtons(previewBtn, saveBtn, cancelBtn);

        previewBtn.setOnAction(e -> {
            String name     = memberCombo.getValue();
            String date     = datePicker.getValue() != null ? datePicker.getValue().format(DISPLAY_FMT) : "";
            String minister = minField.getText().trim();
            if (name == null || name.trim().isEmpty()) { showError("Enter the recipient name."); return; }
            try {
                File tmp = File.createTempFile("bapt_preview_", ".pdf");
                tmp.deleteOnExit();
                CertificateGenerator.generateBaptism(name, date, minister, tmp.getAbsolutePath());
                showPdfPreview(tmp, previewImg, previewHint);
            } catch (Exception ex) { showError("Preview failed: " + ex.getMessage()); ex.printStackTrace(); }
        });

        saveBtn.setOnAction(e -> {
            String name     = memberCombo.getValue();
            String date     = datePicker.getValue() != null ? datePicker.getValue().format(DISPLAY_FMT) : "";
            String minister = minField.getText().trim();
            if (name == null || name.trim().isEmpty()) { showError("Enter the recipient name."); return; }
            File out = chooseSaveFile(stage, "BaptismCert_" + safeName(name));
            if (out == null) return;
            try {
                CertificateGenerator.generateBaptism(name, date, minister, out.getAbsolutePath());
                audit("Generated Baptism Certificate for: " + name);
                showInfo("Saved to:\n" + out.getAbsolutePath());
                stage.close();
            } catch (Exception ex) { showError("Save failed: " + ex.getMessage()); ex.printStackTrace(); }
        });

        cancelBtn.setOnAction(e -> stage.close());
        root.getChildren().addAll(form, footer(cancelBtn, previewBtn, saveBtn));
        launch(stage, root, 560, 620);
    }

    // =========================================================
    // APPRECIATION
    // =========================================================
    @FXML
    public void handleAppreciationCert() {
        Stage stage = buildStage("Certificate of Appreciation", 560, 460);
        VBox root = stageRoot();
        root.getChildren().add(sectionHeader("Certificate of Appreciation", "APPRECIATION", "#3A86C8"));

        VBox form = formBody();
        ComboBox<String> memberCombo = memberCombo();
        DatePicker       datePicker  = datePicker();

        form.getChildren().addAll(
            formRow("RECIPIENT NAME *", memberCombo),
            formRow("DATE *", datePicker)
        );

        Label     previewHint = hintLabel("Fill in the fields above then click Preview.");
        ImageView previewImg  = previewImg();
        form.getChildren().add(previewBox(previewHint, previewImg));

        Button previewBtn = new Button("Preview Certificate");
        Button saveBtn    = new Button("Save PDF...");
        Button cancelBtn  = new Button("Cancel");
        styleButtons(previewBtn, saveBtn, cancelBtn);

        previewBtn.setOnAction(e -> {
            String name = memberCombo.getValue();
            String date = datePicker.getValue() != null ? datePicker.getValue().format(DISPLAY_FMT) : "";
            if (name == null || name.trim().isEmpty()) { showError("Enter the recipient name."); return; }
            try {
                File tmp = File.createTempFile("app_preview_", ".pdf");
                tmp.deleteOnExit();
                CertificateGenerator.generateAppreciation(name, date, tmp.getAbsolutePath());
                showPdfPreview(tmp, previewImg, previewHint);
            } catch (Exception ex) { showError("Preview failed: " + ex.getMessage()); ex.printStackTrace(); }
        });

        saveBtn.setOnAction(e -> {
            String name = memberCombo.getValue();
            String date = datePicker.getValue() != null ? datePicker.getValue().format(DISPLAY_FMT) : "";
            if (name == null || name.trim().isEmpty()) { showError("Enter the recipient name."); return; }
            File out = chooseSaveFile(stage, "Appreciation_" + safeName(name));
            if (out == null) return;
            try {
                CertificateGenerator.generateAppreciation(name, date, out.getAbsolutePath());
                audit("Generated Certificate of Appreciation for: " + name);
                showInfo("Saved to:\n" + out.getAbsolutePath());
                stage.close();
            } catch (Exception ex) { showError("Save failed: " + ex.getMessage()); ex.printStackTrace(); }
        });

        cancelBtn.setOnAction(e -> stage.close());
        root.getChildren().addAll(form, footer(cancelBtn, previewBtn, saveBtn));
        launch(stage, root, 560, 580);
    }

    // =========================================================
    // MARRIAGE BLESSING
    // =========================================================
    @FXML
    public void handleBlessingCert() {
        Stage stage = buildStage("Marriage Blessing", 580, 640);
        VBox root = stageRoot();
        root.getChildren().add(sectionHeader("Marriage Blessing", "MARRIAGE", "#B5862A"));

        VBox form = formBody();
        TextField  brideField = textField("Bride full name");
        TextField  groomField = textField("Groom full name");
        DatePicker datePicker  = datePicker();

        final String[] photoPath = {""};
        Label photoNameLabel = hintLabel("No photo selected  (optional)");
        Button choosePhotoBtn = new Button("Choose Couple Photo...");
        choosePhotoBtn.getStyleClass().add("btn-secondary");
        choosePhotoBtn.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Couple Photo");
            fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images",
                    "*.jpg","*.jpeg","*.png","*.bmp","*.gif","*.tif","*.tiff","*.webp",
                    "*.JPG","*.JPEG","*.PNG","*.BMP","*.GIF","*.TIF","*.TIFF","*.WEBP"));
            File chosen = fc.showOpenDialog(stage);
            if (chosen != null) {
                photoPath[0] = chosen.getAbsolutePath();
                photoNameLabel.setText(chosen.getName());
            }
        });
        HBox photoRow = new HBox(10, choosePhotoBtn, photoNameLabel);
        photoRow.setAlignment(Pos.CENTER_LEFT);

        form.getChildren().addAll(
            formRow("BRIDE NAME *",  brideField),
            formRow("GROOM NAME *",  groomField),
            formRow("DATE *",        datePicker),
            formRow("COUPLE PHOTO",  photoRow)
        );

        Label     previewHint = hintLabel("Fill in the fields above then click Preview.");
        ImageView previewImg  = previewImg();
        form.getChildren().add(previewBox(previewHint, previewImg));

        Button previewBtn = new Button("Preview Certificate");
        Button saveBtn    = new Button("Save PDF...");
        Button cancelBtn  = new Button("Cancel");
        styleButtons(previewBtn, saveBtn, cancelBtn);

        previewBtn.setOnAction(e -> {
            String bride = brideField.getText().trim();
            String groom = groomField.getText().trim();
            String date  = datePicker.getValue() != null ? datePicker.getValue().format(DISPLAY_FMT) : "";
            if (bride.isEmpty() || groom.isEmpty()) { showError("Enter both bride and groom names."); return; }
            try {
                File tmp = File.createTempFile("bless_preview_", ".pdf");
                tmp.deleteOnExit();
                CertificateGenerator.generateBlessing(bride, groom, date, photoPath[0], tmp.getAbsolutePath());
                showPdfPreview(tmp, previewImg, previewHint);
            } catch (Exception ex) { showError("Preview failed: " + ex.getMessage()); ex.printStackTrace(); }
        });

        saveBtn.setOnAction(e -> {
            String bride = brideField.getText().trim();
            String groom = groomField.getText().trim();
            String date  = datePicker.getValue() != null ? datePicker.getValue().format(DISPLAY_FMT) : "";
            if (bride.isEmpty() || groom.isEmpty()) { showError("Enter both bride and groom names."); return; }
            File out = chooseSaveFile(stage, "Blessing_" + safeName(bride) + "_" + safeName(groom));
            if (out == null) return;
            try {
                CertificateGenerator.generateBlessing(bride, groom, date, photoPath[0], out.getAbsolutePath());
                audit("Generated Marriage Blessing for: " + bride + " & " + groom);
                showInfo("Saved to:\n" + out.getAbsolutePath());
                stage.close();
            } catch (Exception ex) { showError("Save failed: " + ex.getMessage()); ex.printStackTrace(); }
        });

        cancelBtn.setOnAction(e -> stage.close());
        root.getChildren().addAll(form, footer(cancelBtn, previewBtn, saveBtn));
        launch(stage, root, 580, 720);
    }

    // =========================================================
    // DEATH ANNOUNCEMENT
    // Outputs a PNG image (not PDF) for easy sharing on WhatsApp
    // and church platforms.
    // Called with no args from the FXML card button.
    // Called statically with a pre-filled name when redirected from
    // the "Faithful Departed" action in the member edit form.
    // =========================================================
    @FXML
    public void handleDeathAnnouncement() {
        openDeathAnnouncementDialog(null);
    }

    /**
     * Static entry point used by AddEditMemberController after recording a
     * passing.  Creates a fresh stage with no modal owner so it sits cleanly
     * on top of the main window without inheriting the old modal chain.
     */
    public static void showDeathAnnouncementDialog(String prefilledName) {
        new CommemorationsController().openDeathAnnouncementDialog(prefilledName);
    }

    private void openDeathAnnouncementDialog(String prefilledName) {
        Stage stage = buildStage("Death Announcement", 560, 560);
        VBox root = stageRoot();
        root.getChildren().add(sectionHeader(
            "Death Announcement", "ANNOUNCEMENT", "#4A5568"));

        VBox form = formBody();

        // Deceased name — pre-populated from Faithful Departed list
        ComboBox<String> nameCombo = new ComboBox<>();
        nameCombo.setEditable(true);
        nameCombo.getStyleClass().add("form-combo");
        nameCombo.setPromptText("Select or type deceased member name...");
        nameCombo.setMaxWidth(Double.MAX_VALUE);
        loadFaithfulDeparted(nameCombo);

        // Pre-fill if redirected from the Faithful Departed action
        if (prefilledName != null && !prefilledName.isEmpty()) {
            nameCombo.setValue(prefilledName);
        }
        DatePicker burialDatePicker = datePicker();
        TextField  timeField        = textField("e.g. 10H00");
        TextField  venueField       = textField("e.g. AFM VFCC Auditorium");

        form.getChildren().addAll(
            formRow("DECEASED NAME *",  nameCombo),
            formRow("BURIAL DATE *",    burialDatePicker),
            formRow("TIME *",           timeField),
            formRow("VENUE *",          venueField)
        );

        Label     previewHint = hintLabel("Fill in the fields above then click Preview.");
        ImageView previewImg  = previewImg();
        form.getChildren().add(previewBox(previewHint, previewImg));

        Button previewBtn = new Button("Preview");
        Button saveBtn    = new Button("Save Image...");
        Button cancelBtn  = new Button("Cancel");

        // Distinct styling: muted/solemn for this dialog
        previewBtn.getStyleClass().add("btn-secondary");
        saveBtn.setStyle(
            "-fx-background-color:#4A5568;-fx-text-fill:#FFFFFF;" +
            "-fx-font-weight:700;-fx-background-radius:8;-fx-padding:8 18;"
        );
        cancelBtn.getStyleClass().add("btn-secondary");

        previewBtn.setOnAction(e -> {
            String name  = nameCombo.getValue();
            String date  = burialDatePicker.getValue() != null
                           ? burialDatePicker.getValue().format(DISPLAY_FMT) : "";
            String time  = timeField.getText().trim();
            String venue = venueField.getText().trim();
            if (name == null || name.trim().isEmpty()) { showError("Enter the deceased member's name."); return; }
            if (date.isEmpty()) { showError("Enter the burial date."); return; }
            try {
                File tmp = File.createTempFile("announce_preview_", ".png");
                tmp.deleteOnExit();
                CertificateGenerator.generateAnnouncement(name, date, time, venue, tmp.getAbsolutePath());
                showImagePreview(tmp, previewImg, previewHint);
            } catch (Exception ex) { showError("Preview failed: " + ex.getMessage()); ex.printStackTrace(); }
        });

        saveBtn.setOnAction(e -> {
            String name  = nameCombo.getValue();
            String date  = burialDatePicker.getValue() != null
                           ? burialDatePicker.getValue().format(DISPLAY_FMT) : "";
            String time  = timeField.getText().trim();
            String venue = venueField.getText().trim();
            if (name == null || name.trim().isEmpty()) { showError("Enter the deceased member's name."); return; }
            if (date.isEmpty()) { showError("Enter the burial date."); return; }

            FileChooser fc = new FileChooser();
            fc.setTitle("Save Announcement Image");
            fc.setInitialFileName("Announcement_" + safeName(name) + ".png");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
            File out = fc.showSaveDialog(stage);
            if (out == null) return;
            try {
                CertificateGenerator.generateAnnouncement(name, date, time, venue, out.getAbsolutePath());
                audit("Generated Death Announcement for: " + name);
                showInfo("Announcement image saved to:\n" + out.getAbsolutePath());
                stage.close();
            } catch (Exception ex) { showError("Save failed: " + ex.getMessage()); ex.printStackTrace(); }
        });

        cancelBtn.setOnAction(e -> stage.close());
        root.getChildren().addAll(form, footer(cancelBtn, previewBtn, saveBtn));
        launch(stage, root, 560, 640);
    }

    // =========================================================
    // PDF PREVIEW - renders first page as image inside the dialog
    // =========================================================
    private void showPdfPreview(File pdfFile, ImageView previewImg, Label hint) {
        try {
            org.apache.pdfbox.pdmodel.PDDocument doc =
                org.apache.pdfbox.Loader.loadPDF(
                    new org.apache.pdfbox.io.RandomAccessReadBufferedFile(pdfFile));
            org.apache.pdfbox.rendering.PDFRenderer renderer =
                new org.apache.pdfbox.rendering.PDFRenderer(doc);
            java.awt.image.BufferedImage bi = renderer.renderImageWithDPI(0, 120);
            doc.close();

            File tmp = File.createTempFile("cert_preview_", ".png");
            tmp.deleteOnExit();
            javax.imageio.ImageIO.write(bi, "PNG", tmp);
            Image fxImage = new Image(tmp.toURI().toString());
            previewImg.setImage(fxImage);
            previewImg.setVisible(true);
            previewImg.setManaged(true);
            hint.setVisible(false);
            hint.setManaged(false);
        } catch (Exception e) {
            hint.setText("Preview unavailable: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Shows a PNG image directly in the preview pane. */
    private void showImagePreview(File imageFile, ImageView previewImg, Label hint) {
        try {
            Image fxImage = new Image(imageFile.toURI().toString());
            previewImg.setImage(fxImage);
            previewImg.setVisible(true);
            previewImg.setManaged(true);
            hint.setVisible(false);
            hint.setManaged(false);
        } catch (Exception e) {
            hint.setText("Preview unavailable: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================
    // UI BUILDER HELPERS
    // =========================================================
    private Stage buildStage(String title, double w, double h) {
        Stage s = new Stage();
        
        s.initModality(javafx.stage.Modality.NONE);
        s.setTitle(title);
        s.setResizable(true);
        return s;
    }

    private VBox stageRoot() {
        VBox v = new VBox(0);
        v.setStyle("-fx-background-color:#F5F6FA;");
        return v;
    }

    private VBox sectionHeader(String title, String badge, String badgeColor) {
        VBox h = new VBox(4);
        h.setStyle("-fx-background-color:#FFFFFF;-fx-padding:18 24 14 24;" +
            "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        Label badgeLbl = new Label(badge);
        badgeLbl.setStyle("-fx-font-size:9px;-fx-font-weight:700;" +
            "-fx-text-fill:" + badgeColor + ";-fx-letter-spacing:2px;");
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size:17px;-fx-font-weight:700;-fx-text-fill:#1E2130;");
        h.getChildren().addAll(badgeLbl, titleLbl);
        return h;
    }

    private VBox formBody() {
        VBox v = new VBox(14);
        v.setStyle("-fx-padding:18 24 8 24;");
        VBox.setVgrow(v, Priority.ALWAYS);
        return v;
    }

    private VBox formRow(String label, javafx.scene.Node field) {
        VBox box = new VBox(5);
        Label l = new Label(label);
        l.setStyle("-fx-font-size:10px;-fx-font-weight:700;-fx-text-fill:#5A6275;-fx-letter-spacing:1px;");
        box.getChildren().addAll(l, field);
        if (field instanceof Region) ((Region) field).setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private ComboBox<String> memberCombo() {
        ComboBox<String> c = new ComboBox<>();
        c.setEditable(true);
        c.getStyleClass().add("form-combo");
        c.setPromptText("Select or type member name...");
        c.setMaxWidth(Double.MAX_VALUE);
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT full_name FROM members WHERE is_deleted=0 AND is_active=1 ORDER BY full_name");
            while (rs.next()) c.getItems().add(rs.getString("full_name"));
        } catch (SQLException e) { e.printStackTrace(); }
        return c;
    }

    /** Loads the Faithful Departed list into the combo so the user can pick a name they already recorded, or type a new one. */
    private void loadFaithfulDeparted(ComboBox<String> combo) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            // Prefer Faithful Departed first, then allow any active member
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT m.full_name " +
                "FROM members m " +
                "WHERE m.is_deceased = 1 AND m.is_deleted = 0 " +
                "ORDER BY m.full_name"
            );
            while (rs.next()) combo.getItems().add(rs.getString("full_name"));
            // Separator hint
            if (!combo.getItems().isEmpty())
                combo.getItems().add("─────────────────");
            // Also allow typing a name not yet in the system
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private DatePicker datePicker() {
        DatePicker d = new DatePicker(LocalDate.now());
        d.getStyleClass().add("form-date-picker");
        d.setMaxWidth(Double.MAX_VALUE);
        return d;
    }

    private TextField textField(String prompt) {
        TextField t = new TextField();
        t.getStyleClass().add("form-field");
        t.setPromptText(prompt);
        t.setMaxWidth(Double.MAX_VALUE);
        return t;
    }

    private Label hintLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:#9099AA;-fx-font-size:11px;-fx-font-style:italic;");
        return l;
    }

    private ImageView previewImg() {
        ImageView iv = new ImageView();
        iv.setFitWidth(500);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        iv.setVisible(false);
        iv.setManaged(false);
        return iv;
    }

    private VBox previewBox(Label hint, ImageView img) {
        VBox box = new VBox(8, hint, img);
        box.setStyle("-fx-background-color:#FFFFFF;-fx-border-color:#DDE1EA;" +
            "-fx-border-radius:8;-fx-background-radius:8;-fx-padding:12;" +
            "-fx-border-width:1;");
        box.setAlignment(Pos.CENTER);
        box.setMinHeight(60);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    private HBox footer(Button cancel, Button preview, Button save) {
        HBox h = new HBox(10, cancel, preview, save);
        h.setAlignment(Pos.CENTER_RIGHT);
        h.setStyle("-fx-background-color:#FFFFFF;-fx-padding:14 24;" +
            "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");
        return h;
    }

    private void styleButtons(Button preview, Button save, Button cancel) {
        preview.getStyleClass().add("btn-secondary");
        save.getStyleClass().add("btn-primary");
        cancel.getStyleClass().add("btn-secondary");
    }

    private void launch(Stage stage, VBox root, double w, double h) {
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:#F5F6FA;-fx-background:transparent;");
        Scene scene = new Scene(scroll, w, h);
        scene.setFill(javafx.scene.paint.Color.web("#F5F6FA"));
        scene.getStylesheets().add(
            getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    private File chooseSaveFile(Stage owner, String defaultName) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Certificate");
        fc.setInitialFileName(defaultName + ".pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF","*.pdf"));
        return fc.showSaveDialog(owner);
    }

    private void prefillMinister(TextField field) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT m.full_name FROM board_members bm " +
                "JOIN members m ON m.id = bm.member_id " +
                "WHERE bm.is_active=1 AND LOWER(bm.role_title) LIKE '%minister%' " +
                "ORDER BY bm.id DESC LIMIT 1");
            if (rs.next()) field.setText(rs.getString("full_name"));
        } catch (Exception ignored) {}
    }

    private String safeName(String s) {
        return s == null ? "" : s.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private void audit(String msg) {
        try {
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(), msg);
        } catch (Exception ignored) {}
    }

    private void showError(String msg) {
        ToastManager.error(msg);
        Alert a = new Alert(Alert.AlertType.ERROR);
        Main.applyStyles(a.getDialogPane());
        a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }

    private void showInfo(String msg) {
        ToastManager.success(msg);
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        Main.applyStyles(a.getDialogPane());
        a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}
