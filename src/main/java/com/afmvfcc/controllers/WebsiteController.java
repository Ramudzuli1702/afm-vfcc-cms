package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.Icons;
import com.afmvfcc.utils.Dialogs;
import com.afmvfcc.utils.DocumentViewer;
import com.afmvfcc.utils.FormBuilder;
import com.afmvfcc.utils.TableActions;
import com.afmvfcc.utils.GitHubSync;
import com.afmvfcc.utils.WebsiteExporter;
import com.afmvfcc.utils.SessionManager;
import com.afmvfcc.utils.ToastManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


public class WebsiteController {

    // Blogs table
    @FXML private TableView<String[]> blogsTable;
    @FXML private TableColumn<String[], String> colBlogTitle, colBlogAuthor, colBlogDate;
    @FXML private TableColumn<String[], Void>   colBlogActions;

    // Website events table
    @FXML private TableView<String[]> webEventsTable;
    @FXML private TableColumn<String[], String> colWebEvTitle, colWebEvDate, colWebEvPoster;
    @FXML private TableColumn<String[], Void>   colWebEvActions;

    // Bishop's Message tab
    @FXML private Label     messageVideoStatusLabel;
    @FXML private ImageView messagePosterPreview;
    @FXML private TextField messageCaptionField;

    // Leaders & Board tab
    @FXML private ImageView leadersPhotoPreview, boardPhotoPreview;
    @FXML private TextField leader1NameField, leader1RoleField, leader2NameField, leader2RoleField;
    @FXML private ListView<String> boardMembersPreviewList;

    // Contact Details tab
    @FXML private TextArea  contactAddressField;
    @FXML private TextField contactPhoneField,
                             contactFacebookUrlField, contactFacebookLabelField,
                             contactYoutubeUrlField, contactYoutubeLabelField,
                             contactEmailField;

    private static final String POSTER_DIR = "posters";

    private File pendingLeadersPhoto, pendingBoardPhoto;
    private File pendingMessageVideo, pendingMessagePoster;

    @FXML
    public void initialize() {
        ensurePosterDir();
        setupBlogsTable();
        setupWebEventsTable();
        loadBlogs();
        loadWebEvents();
        if (leader1NameField != null) {
            loadLeadersTab();
            loadBoardMembersPreview();
        }
        if (contactAddressField != null) loadContactTab();
        if (messageCaptionField != null) loadMessageTab();
    }

    private void ensurePosterDir() { new File(POSTER_DIR).mkdirs(); }

    // =========================================================
    // BLOGS TABLE
    // =========================================================

    private void setupBlogsTable() {
        colBlogTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        colBlogAuthor.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        colBlogDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));

        TableActions.viewOnly(blogsTable, colBlogActions, this::showBlog);
    }

    private void loadBlogs() {
        ObservableList<String[]> items = FXCollections.observableArrayList();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT id, title, author, DATE_FORMAT(published_at,'%d %b %Y') " +
                    "FROM website_blogs ORDER BY published_at DESC");
            while (rs.next()) {
                items.add(new String[]{
                    rs.getString(2),
                    rs.getString(3) != null ? rs.getString(3) : "-",
                    rs.getString(4) != null ? rs.getString(4) : "-",
                    rs.getString(1)
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        blogsTable.setItems(items);
    }

    // =========================================================
    // WEBSITE EVENTS TABLE
    // =========================================================

    private void setupWebEventsTable() {
        colWebEvTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        colWebEvDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));

        if (colWebEvPoster != null) {
            colWebEvPoster.setCellValueFactory(
                d -> new SimpleStringProperty(
                    d.getValue()[3] != null && !d.getValue()[3].isEmpty() ? "Yes" : "No"));
            colWebEvPoster.setCellFactory(col -> new TableCell<>() {
                private final ImageView thumb = new ImageView();
                {
                    thumb.setFitWidth(40); thumb.setFitHeight(40);
                    thumb.setPreserveRatio(true);
                    thumb.setStyle("-fx-cursor:hand;");
                    thumb.setOnMouseClicked(e -> {
                        int i = getIndex();
                        if (i >= 0 && i < getTableView().getItems().size()) {
                            String p = getTableView().getItems().get(i)[3];
                            if (p != null && !p.isEmpty()) showPosterPreview(p);
                        }
                    });
                }
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) { setGraphic(null); setText(null); return; }
                    if (item == null || "No".equals(item)) {
                        setGraphic(null); setText("No poster");
                        setStyle("-fx-text-fill:#9099AA;-fx-font-size:11px;");
                        return;
                    }
                    try {
                        int i = getIndex();
                        if (i >= 0 && i < getTableView().getItems().size()) {
                            String path = getTableView().getItems().get(i)[3];
                            Image img = loadImageFromPathOrUrl(path);
                            if (img != null) { thumb.setImage(img); setGraphic(thumb); setText(null); return; }
                        }
                    } catch (Exception ignored) {}
                    setText("Uploaded"); setGraphic(null);
                }
            });
        }

        TableActions.viewOnly(webEventsTable, colWebEvActions, this::showWebEvent);
    }

    private void loadWebEvents() {
        ObservableList<String[]> items = FXCollections.observableArrayList();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT id, title, DATE_FORMAT(event_date,'%d %b %Y'), image_filename " +
                    "FROM website_events ORDER BY event_date DESC");
            while (rs.next()) {
                items.add(new String[]{
                    rs.getString(2),
                    rs.getString(3) != null ? rs.getString(3) : "-",
                    rs.getString(1),
                    rs.getString(4) != null ? rs.getString(4) : ""
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        webEventsTable.setItems(items);
    }

    // =========================================================
    // BLOG DIALOG
    // =========================================================

    @FXML public void handleAddBlog()     { openBlogDialog(null); }

    private void showBlog(String[] row) {
        int id = Integer.parseInt(row[3]);
        TextArea content = new TextArea();
        loadBlogContent(content, id);
        new DocumentViewer("Blog Post", row[0])
            .icon("fas-newspaper")
            .meta("Author",    row[1])
            .meta("Published", row[2])
            .section("Content", content.getText(), "This post has no content.")
            .onEdit(() -> openBlogDialog(row))
            .onDelete(() -> deleteBlog(id))
            .show();
    }

    private void showWebEvent(String[] row) {
        int id = Integer.parseInt(row[2]);
        TextArea desc = new TextArea();
        DatePicker unused = new DatePicker();
        loadWebEventDetails(desc, unused, id);
        DocumentViewer v = new DocumentViewer("Website Event", row[0])
            .icon("fas-bullhorn")
            .meta("Event Date", row[1])
            .meta("Poster", row[3].isEmpty() ? "None" : fileNameFromUrl(row[3]))
            .section("Description", desc.getText(), "No description.");
        Image poster = row[3].isEmpty() ? null : loadImageFromPathOrUrl(row[3]);
        if (poster != null) {
            ImageView iv = new ImageView(poster);
            iv.setPreserveRatio(true);
            iv.setFitWidth(320);
            v.section("Poster", iv);
        }
        v.onEdit(() -> openWebEventDialog(row))
         .onDelete(() -> deleteWebEvent(id))
         .show();
    }
    @FXML public void handleAddWebEvent() { openWebEventDialog(null); }

    private void openBlogDialog(String[] existing) {
        FormBuilder f = new FormBuilder(existing == null ? "New Blog Post" : "Edit Blog Post",
            "Fields marked * are required. Saving publishes the post to the website.").icon("fas-newspaper");

        TextField titleField  = styledField("Blog title");
        TextField authorField = styledField("Author name");
        TextArea  contentArea = new TextArea();
        contentArea.getStyleClass().add("form-textarea");
        contentArea.setWrapText(true);
        contentArea.setPromptText("Blog content...");
        contentArea.setPrefHeight(200); contentArea.setMaxWidth(Double.MAX_VALUE);

        if (existing != null) {
            titleField.setText(existing[0]);
            authorField.setText(existing[1]);
            loadBlogContent(contentArea, Integer.parseInt(existing[3]));
        }

        f.section("Post")
         .row("TITLE *", titleField, "AUTHOR", authorField)
         .field("CONTENT *", contentArea);

        Button saveBtn = f.saveButton(existing == null ? "Publish Post" : "Save & Republish");
        saveBtn.setOnAction(e -> {
            String t = titleField.getText().trim();
            String c = contentArea.getText().trim();
            if (t.isEmpty() || c.isEmpty()) { f.showError("Please enter a title and the post content."); return; }
            try {
                Connection conn = DatabaseConnection.getConnection();
                if (existing == null) {
                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO website_blogs (title, author, content, created_by) VALUES (?,?,?,?)");
                    ps.setString(1, t); ps.setString(2, authorField.getText().trim());
                    ps.setString(3, c); ps.setInt(4, SessionManager.getInstance().getCurrentUser().getId());
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(), "Published blog: " + t);
                } else {
                    PreparedStatement ps = conn.prepareStatement(
                        "UPDATE website_blogs SET title=?, author=?, content=? WHERE id=?");
                    ps.setString(1, t); ps.setString(2, authorField.getText().trim());
                    ps.setString(3, c); ps.setInt(4, Integer.parseInt(existing[3]));
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(), "Updated blog: " + t);
                }
                loadBlogs();
                // DB write is complete on the FX thread — safe to export now
                triggerExport();
                f.close();
                ToastManager.success(existing == null ? "Blog post published." : "Blog post updated.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                f.showError("Could not save the post: " + ex.getMessage());
                ToastManager.error("Failed to save blog post: " + ex.getMessage());
            }
        });

        f.show(680);
    }

    // =========================================================
    // WEBSITE EVENT DIALOG
    // =========================================================

    private void openWebEventDialog(String[] existing) {
        FormBuilder f = new FormBuilder(existing == null ? "New Website Event" : "Edit Website Event",
            "Fields marked * are required. Saving publishes the event to the website.").icon("fas-bullhorn");

        TextField  titleField = styledField("Event title");
        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.getStyleClass().add("form-date-picker");
        datePicker.setMaxWidth(Double.MAX_VALUE);
        TextArea descArea = new TextArea();
        descArea.getStyleClass().add("form-textarea");
        descArea.setWrapText(true);
        descArea.setPromptText("Event description...");
        descArea.setPrefHeight(100); descArea.setMaxWidth(Double.MAX_VALUE);

        // Poster — stored as GitHub Pages URL (or local path as fallback)
        final String[] selectedPosterUrl  = { existing != null ? existing[3] : "" };
        final File[]   selectedLocalFile  = { null }; // the locally chosen file

        ImageView posterPreview = new ImageView();
        posterPreview.setFitWidth(120); posterPreview.setFitHeight(120);
        posterPreview.setPreserveRatio(true);
        posterPreview.setStyle("-fx-border-color:#DDE1EA;-fx-border-width:1;-fx-border-radius:8;");

        Label posterLabel = new Label(selectedPosterUrl[0].isEmpty()
            ? "No poster selected"
            : fileNameFromUrl(selectedPosterUrl[0]));
        posterLabel.setStyle("-fx-text-fill:#5A6275;-fx-font-size:12px;");

        Label uploadStatus = new Label("");
        uploadStatus.setStyle("-fx-font-size:11px;-fx-text-fill:#3A86C8;");

        if (!selectedPosterUrl[0].isEmpty()) {
            Image img = loadImageFromPathOrUrl(selectedPosterUrl[0]);
            if (img != null) posterPreview.setImage(img);
        }

        Button choosePosterBtn = new Button("Choose Poster Image...");
        choosePosterBtn.getStyleClass().add("btn-secondary");
        Button removePosterBtn = new Button("Remove");
        removePosterBtn.getStyleClass().add("btn-danger");
        removePosterBtn.setStyle("-fx-padding:6 12;-fx-font-size:11px;");
        removePosterBtn.setVisible(!selectedPosterUrl[0].isEmpty());

        choosePosterBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Event Poster");
            fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.jpg","*.jpeg","*.png","*.gif","*.webp"));
            File chosen = fc.showOpenDialog(f.stage());
            if (chosen != null) {
                selectedLocalFile[0] = chosen;
                // Show local preview immediately
                posterPreview.setImage(new Image(chosen.toURI().toString()));
                posterLabel.setText(chosen.getName());
                Icons.status(uploadStatus, "Will upload to GitHub on save", Icons.Status.INFO);
                removePosterBtn.setVisible(true);
            }
        });

        removePosterBtn.setOnAction(e -> {
            // If there was an existing GitHub poster, schedule deletion
            if (!selectedPosterUrl[0].isEmpty()) {
                String repoPath = githubPagesUrlToRepoPath(selectedPosterUrl[0]);
                if (repoPath != null) {
                    new Thread(() -> GitHubSync.deletePoster(repoPath)).start();
                }
            }
            selectedPosterUrl[0] = "";
            selectedLocalFile[0] = null;
            posterPreview.setImage(null);
            posterLabel.setText("No poster selected");
            uploadStatus.setText("");
            uploadStatus.setGraphic(null);
            removePosterBtn.setVisible(false);
        });

        HBox posterBtns = new HBox(8, choosePosterBtn, removePosterBtn);
        posterBtns.setAlignment(Pos.CENTER_LEFT);
        VBox posterText = new VBox(8, posterLabel, uploadStatus, posterBtns);
        HBox posterBox = new HBox(16, posterPreview, posterText);
        posterBox.setAlignment(Pos.CENTER_LEFT);

        if (existing != null && existing.length > 2) {
            titleField.setText(existing[0]);
            loadWebEventDetails(descArea, datePicker, Integer.parseInt(existing[2]));
        }

        f.section("Event")
         .row("TITLE *", titleField, "EVENT DATE *", datePicker)
         .field("DESCRIPTION", descArea);
        f.section("Poster")
         .node(posterBox)
         .hint("The poster is uploaded to the website's GitHub repository when you save.");

        Button saveBtn = f.saveButton(existing == null ? "Publish Event" : "Save & Republish");

        saveBtn.setOnAction(e -> {
            String t = titleField.getText().trim();
            if (t.isEmpty()) { f.showError("Please enter a title for the event."); return; }
            if (datePicker.getValue() == null) { f.showError("Please choose the event date."); return; }

            saveBtn.setDisable(true);
            String descText  = descArea.getText().trim();
            LocalDate evDate = datePicker.getValue();
            int userId       = SessionManager.getInstance().getCurrentUser().getId();

            if (selectedLocalFile[0] != null) {
                // ── New poster chosen: upload first, then write DB, then export ──
                Icons.status(uploadStatus, "Uploading poster to GitHub…", Icons.Status.BUSY);
                final File localFile = selectedLocalFile[0];

                new Thread(() -> {

                    // 1. Copy to local posters/ folder as a fallback cache
                    String finalPosterUrl;
                    String posterUploadError = null;
                    try {
                        new File(POSTER_DIR).mkdirs();
                        String destName = System.currentTimeMillis() + "_" + localFile.getName();
                        Path dest = Paths.get(POSTER_DIR, destName);
                        Files.copy(localFile.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);

                        // 2. Upload to GitHub
                        String[] result = GitHubSync.uploadPoster(dest.toFile(), destName);
                        if (result[0] != null) {
                            finalPosterUrl = result[0];
                        } else {
                            // GitHub upload failed — fall back to the local relative
                            // path so it at least resolves for a local-folder-served
                            // site, but make sure the user is told it didn't reach
                            // GitHub, since the live site otherwise won't show it.
                            finalPosterUrl = "posters/" + destName;
                            posterUploadError = result[1];
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        finalPosterUrl = "";
                        posterUploadError = ex.getMessage();
                    }

                    final String posterUrlToSave = finalPosterUrl;
                    final String finalPosterUploadError = posterUploadError;

                    // 3. Write to DB on the FX thread — THEN export
                    javafx.application.Platform.runLater(() -> {
                        try {
                            writeEventToDb(existing, t, descText, evDate, posterUrlToSave, userId);
                            loadWebEvents();

                            // ── Export AFTER the DB write has completed ──
                            triggerExport();
                            f.close();
                            ToastManager.success(existing == null ? "Event published." : "Event updated.");
                            if (finalPosterUploadError != null) {
                                ToastManager.error("Poster image could not be uploaded to GitHub: " +
                                    finalPosterUploadError + " — it won't appear on the live site until this is fixed.");
                            }
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                            saveBtn.setDisable(false);
                            Icons.status(uploadStatus, "Save failed: " + ex.getMessage(), Icons.Status.ERROR);
                            ToastManager.error("Failed to save event: " + ex.getMessage());
                        }
                    });
                }).start();

            } else {
                // ── No new poster: write DB immediately on the FX thread, then export ──
                try {
                    writeEventToDb(existing, t, descText, evDate,
                            selectedPosterUrl[0], userId);
                    loadWebEvents();

                    // ── Export AFTER the DB write has completed ──
                    triggerExport();
                    f.close();
                    ToastManager.success(existing == null ? "Event published." : "Event updated.");
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    saveBtn.setDisable(false);
                    Icons.status(uploadStatus, "Save failed: " + ex.getMessage(), Icons.Status.ERROR);
                    ToastManager.error("Failed to save event: " + ex.getMessage());
                }
            }
        });

        f.show(640);
    }

    /**
     * INSERT or UPDATE a website_events row.
     * Extracted so both code paths (new-poster and no-poster) share one place.
     * Must be called on the FX thread after any poster URL is finalised.
     */
    private void writeEventToDb(String[] existing, String title, String desc,
            LocalDate eventDate, String posterUrl, int userId) throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        if (existing == null) {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO website_events " +
                "(title, description, event_date, image_filename, created_by) VALUES (?,?,?,?,?)");
            ps.setString(1, title);
            ps.setString(2, desc);
            ps.setDate(3, Date.valueOf(eventDate));
            ps.setString(4, posterUrl.isEmpty() ? null : posterUrl);
            ps.setInt(5, userId);
            ps.executeUpdate();
            AuditLogger.log(userId, "Published website event: " + title);
        } else {
            PreparedStatement ps = conn.prepareStatement(
                "UPDATE website_events SET title=?, description=?, event_date=?, image_filename=? WHERE id=?");
            ps.setString(1, title);
            ps.setString(2, desc);
            ps.setDate(3, Date.valueOf(eventDate));
            ps.setString(4, posterUrl.isEmpty() ? null : posterUrl);
            ps.setInt(5, Integer.parseInt(existing[2]));
            ps.executeUpdate();
            AuditLogger.log(userId, "Updated website event: " + title);
        }
    }

    // =========================================================
    // DELETE
    // =========================================================

    /** Confirms, then deletes the post and republishes. Returns true if deleted. */
    private boolean deleteBlog(int id) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Delete Blog");
        confirm.setHeaderText("Delete this blog post?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        try {
            PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement("DELETE FROM website_blogs WHERE id=?");
            ps.setInt(1, id);
            ps.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                "Deleted blog post ID " + id);
            loadBlogs();
            triggerExport();
            ToastManager.success("Blog post deleted.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to delete blog post: " + e.getMessage());
            return false;
        }
    }

    /** Confirms, then deletes the event (and its poster on GitHub) and republishes. Returns true if deleted. */
    private boolean deleteWebEvent(int id) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Delete Event");
        confirm.setHeaderText("Delete this website event?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        try {
            Connection conn = DatabaseConnection.getConnection();
            // Get poster URL before deleting
            PreparedStatement q = conn.prepareStatement("SELECT image_filename FROM website_events WHERE id=?");
            q.setInt(1, id);
            ResultSet rs = q.executeQuery();
            String posterUrl = rs.next() ? rs.getString(1) : null;

            PreparedStatement del = conn.prepareStatement("DELETE FROM website_events WHERE id=?");
            del.setInt(1, id);
            del.executeUpdate();

            // Delete poster from GitHub
            if (posterUrl != null && !posterUrl.isEmpty()) {
                String repoPath = githubPagesUrlToRepoPath(posterUrl);
                if (repoPath != null) {
                    final String rp = repoPath;
                    new Thread(() -> GitHubSync.deletePoster(rp)).start();
                }
            }

            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                "Deleted website event ID " + id);
            loadWebEvents();
            triggerExport();
            ToastManager.success("Event deleted.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to delete event: " + e.getMessage());
            return false;
        }
    }

    // =========================================================
    // POSTER PREVIEW
    // =========================================================

    private void showPosterPreview(String pathOrUrl) {
        Image img = loadImageFromPathOrUrl(pathOrUrl);
        if (img == null) return;
        Stage preview = new Stage();
        preview.setTitle("Poster Preview");
        ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true); iv.setFitWidth(500); iv.setFitHeight(600);
        VBox box = new VBox(iv);
        box.setStyle("-fx-background-color:#FFFFFF;-fx-padding:12;");
        box.setAlignment(Pos.CENTER);
        Scene sc = new Scene(box);
        sc.setFill(javafx.scene.paint.Color.WHITE);
        preview.setScene(sc);
        preview.show();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    /** Load an image from a local file path OR an http/https URL. */
    private static Image loadImageFromPathOrUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isEmpty()) return null;
        try {
            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                return new Image(pathOrUrl, true); // background loading
            }
            File f = new File(pathOrUrl);
            if (f.exists()) return new Image(f.toURI().toString());
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Convert a stored poster reference back to its repo-relative path, so it
     * can be deleted from GitHub. Handles all three shapes image_filename can
     * hold: a GitHub Pages URL (owner.github.io/repo/posters/x.png), a custom
     * domain URL (configured in Settings — previously not recognised at all,
     * silently skipping deletion for anyone using a custom domain), or an
     * already-relative local fallback path ("posters/x.png", saved when a
     * GitHub upload failed). Returns null only if none of these match.
     */
    private static String githubPagesUrlToRepoPath(String url) {
        if (url == null || url.isEmpty()) return null;
        if (url.startsWith("posters/")) return url;
        if (url.contains("github.io")) {
            try {
                String path = new URI(url).getPath(); // /<repo>/<path...>
                int second = path.indexOf('/', 1);    // position after /<repo>
                if (second < 0) return null;
                return path.substring(second + 1);   // posters/foo.png
            } catch (Exception e) { return null; }
        }
        String customDomain = GitHubSync.loadSetting("website_custom_domain");
        if (customDomain != null && !customDomain.isEmpty() && url.contains("/posters/")) {
            int idx = url.indexOf("/posters/");
            return url.substring(idx + 1); // posters/foo.png
        }
        return null;
    }

    /** Extract filename from a URL or file path. */
    private static String fileNameFromUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isEmpty()) return "No poster selected";
        int slash = Math.max(pathOrUrl.lastIndexOf('/'), pathOrUrl.lastIndexOf('\\'));
        return slash >= 0 ? pathOrUrl.substring(slash + 1) : pathOrUrl;
    }

    private void loadBlogContent(TextArea area, int id) {
        try {
            PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement("SELECT content FROM website_blogs WHERE id=?");
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) area.setText(rs.getString("content"));
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadWebEventDetails(TextArea area, DatePicker picker, int id) {
        try {
            PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement("SELECT description, event_date FROM website_events WHERE id=?");
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                area.setText(rs.getString("description") != null ? rs.getString("description") : "");
                if (rs.getDate("event_date") != null)
                    picker.setValue(rs.getDate("event_date").toLocalDate());
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private TextField styledField(String prompt) {
        TextField f = new TextField();
        f.getStyleClass().add("form-field");
        f.setPromptText(prompt);
        f.setMaxWidth(Double.MAX_VALUE);
        return f;
    }

    // =========================================================
    // VISIT WEBSITE
    // =========================================================

    /**
     * Opens the live GitHub Pages website (or custom domain) in the default browser.
     */
    @FXML
    public void handleVisitWebsite() {
        String url = buildWebsiteUrl();
        if (url == null) {
            showError("No website URL configured.\n\nPlease set your GitHub owner/repo in Settings " +
                      "(or a custom domain) so the CMS knows where your site lives.");
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            try {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    new ProcessBuilder("cmd", "/c", "start", url).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", url).start();
                } else {
                    new ProcessBuilder("xdg-open", url).start();
                }
            } catch (IOException ex) {
                showError("Could not open browser.\nURL: " + url);
            }
        }
    }

    /**
     * Returns the public website URL based on settings.
     * Priority: custom domain → GitHub Pages URL → null
     */
    private String buildWebsiteUrl() {
        String custom = GitHubSync.loadSetting("website_custom_domain");
        if (custom != null && !custom.isEmpty()) {
            return custom.startsWith("http") ? custom : "https://" + custom;
        }
        String owner = GitHubSync.loadSetting("github_owner");
        String repo  = GitHubSync.loadSetting("github_repo");
        if (owner != null && !owner.isEmpty() && repo != null && !repo.isEmpty()) {
            return "https://" + owner.toLowerCase() + ".github.io/" + repo + "/";
        }
        return null;
    }

    // =========================================================
    // EXPORT — always called AFTER the DB write has completed
    // =========================================================

    /**
     * Runs WebsiteExporter.exportAll on a background thread.
     * Must only be called once the DB write for the triggering change is done,
     * so the export reads the updated rows and data.js reflects the new state.
     */
    // Single-thread executor so concurrent saves can't run two exports at once
    // against the app's one shared DB connection, and so publishes apply in
    // the order they were triggered rather than racing each other.
    private static final java.util.concurrent.ExecutorService EXPORT_EXECUTOR =
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "website-export");
            t.setDaemon(true);
            return t;
        });

    private void triggerExport() {
        EXPORT_EXECUTOR.submit(() -> {
            try {
                String localFolder = GitHubSync.loadSetting("website_folder");
                WebsiteExporter.exportAll(localFolder != null ? localFolder : "");
                if (GitHubSync.isConfigured()) {
                    javafx.application.Platform.runLater(() ->
                        ToastManager.success("Website updated — the change is now live on GitHub."));
                }
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() ->
                    showError(ex.getMessage()));
            }
        });
    }

    private void showError(String msg) {
        javafx.application.Platform.runLater(() -> {
            ToastManager.error(msg);
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Website Export");
            alert.setHeaderText("Could not export to website");
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }

    // =========================================================
    // BISHOP'S MESSAGE
    // =========================================================

    private void loadMessageTab() {
        String video = setting("website_message_video");
        messageVideoStatusLabel.setText((video != null && !video.isEmpty())
            ? "Current video: " + fileNameFromUrl(video)
            : "No video chosen — the site currently shows the default demo video.");
        messageCaptionField.setText(nvl(setting("website_message_caption")));
        setPreview(messagePosterPreview, setting("website_message_poster"));
    }

    @FXML public void handleChooseMessageVideo() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Message Video");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Video", "*.mp4"));
        File f = fc.showOpenDialog(null);
        if (f != null) {
            pendingMessageVideo = f;
            messageVideoStatusLabel.setText("Will upload on save: " + f.getName());
        }
    }

    @FXML public void handleChooseMessagePoster() {
        File f = choosePhoto();
        if (f != null) { pendingMessagePoster = f; messagePosterPreview.setImage(new Image(f.toURI().toString())); }
    }

    @FXML
    public void handleSaveMessage() {
        String caption = messageCaptionField.getText().trim();
        messageVideoStatusLabel.setText(pendingMessageVideo != null
            ? "Uploading " + pendingMessageVideo.getName() + "…" : messageVideoStatusLabel.getText());

        new Thread(() -> {
            String videoUrl  = uploadIfChosen(pendingMessageVideo, "message.mp4");
            String posterUrl = uploadIfChosen(pendingMessagePoster, "message-poster.jpg");

            javafx.application.Platform.runLater(() -> {
                try {
                    Connection conn = DatabaseConnection.getConnection();
                    saveSetting(conn, "website_message_caption", caption);
                    if (videoUrl != null) saveSetting(conn, "website_message_video", videoUrl);
                    if (posterUrl != null) saveSetting(conn, "website_message_poster", posterUrl);

                    pendingMessageVideo = pendingMessagePoster = null;
                    loadMessageTab();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Updated website Bishop's Message");
                    triggerExport();
                    ToastManager.success("Bishop's Message saved.");
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    ToastManager.error("Failed to save: " + ex.getMessage());
                }
            });
        }, "message-save").start();
    }

    // =========================================================
    // MEET OUR LEADERS
    // =========================================================

    private void loadLeadersTab() {
        leader1NameField.setText(nvl(setting("website_leader1_name")));
        leader1RoleField.setText(nvl(setting("website_leader1_role")));
        leader2NameField.setText(nvl(setting("website_leader2_name")));
        leader2RoleField.setText(nvl(setting("website_leader2_role")));
        setPreview(leadersPhotoPreview, setting("website_leaders_photo"));
        setPreview(boardPhotoPreview, setting("website_board_photo"));
    }

    private void loadBoardMembersPreview() {
        ObservableList<String> names = FXCollections.observableArrayList();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT m.full_name, bm.role_title FROM board_members bm " +
                "JOIN members m ON m.id = bm.member_id " +
                "WHERE bm.is_active = 1 ORDER BY bm.start_date ASC, m.full_name ASC");
            while (rs.next()) {
                String role = rs.getString(2);
                names.add((role != null && !role.isBlank())
                    ? rs.getString(1) + " — " + role : rs.getString(1));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        if (names.isEmpty()) names.add("(No active board members yet — add them in Church Board.)");
        boardMembersPreviewList.setItems(names);
    }

    @FXML public void handleChooseLeadersPhoto() {
        File f = choosePhoto();
        if (f != null) { pendingLeadersPhoto = f; leadersPhotoPreview.setImage(new Image(f.toURI().toString())); }
    }
    @FXML public void handleChooseBoardPhoto() {
        File f = choosePhoto();
        if (f != null) { pendingBoardPhoto = f; boardPhotoPreview.setImage(new Image(f.toURI().toString())); }
    }

    private File choosePhoto() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Photo");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp"));
        return fc.showOpenDialog(null);
    }

    @FXML
    public void handleSaveLeaders() {
        String n1 = leader1NameField.getText().trim(), r1 = leader1RoleField.getText().trim();
        String n2 = leader2NameField.getText().trim(), r2 = leader2RoleField.getText().trim();

        new Thread(() -> {
            String ldrPhoto = uploadIfChosen(pendingLeadersPhoto, "leaders.jpg");
            String bPhoto  = uploadIfChosen(pendingBoardPhoto, "board-photo.jpg");

            javafx.application.Platform.runLater(() -> {
                try {
                    Connection conn = DatabaseConnection.getConnection();
                    saveSetting(conn, "website_leader1_name", n1);
                    saveSetting(conn, "website_leader1_role", r1);
                    saveSetting(conn, "website_leader2_name", n2);
                    saveSetting(conn, "website_leader2_role", r2);
                    if (ldrPhoto != null) saveSetting(conn, "website_leaders_photo", ldrPhoto);
                    if (bPhoto != null) saveSetting(conn, "website_board_photo", bPhoto);

                    pendingLeadersPhoto = pendingBoardPhoto = null;
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Updated website leaders & board");
                    triggerExport();
                    ToastManager.success("Leaders & Board saved.");
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    ToastManager.error("Failed to save: " + ex.getMessage());
                }
            });
        }, "leaders-save").start();
    }

    /**
     * Uploads a locally-chosen photo to GitHub under a fixed filename (so
     * re-uploading a leader's photo overwrites the same file rather than
     * piling up timestamped copies), returning the public URL — or, if
     * nothing new was chosen, null (caller then leaves the stored setting
     * untouched). On a GitHub upload failure, falls back to the bare
     * repo-relative path and reports the error via a toast.
     */
    private String uploadIfChosen(File localFile, String fixedName) {
        if (localFile == null) return null;
        try {
            new File(POSTER_DIR).mkdirs();
            Path dest = Paths.get(POSTER_DIR, fixedName);
            Files.copy(localFile.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            String[] result = GitHubSync.uploadPoster(dest.toFile(), fixedName);
            if (result[0] != null) return result[0];
            final String err = result[1];
            javafx.application.Platform.runLater(() -> ToastManager.error(
                "Could not upload " + fixedName + " to GitHub: " + err));
            return "posters/" + fixedName;
        } catch (IOException ex) {
            ex.printStackTrace();
            javafx.application.Platform.runLater(() -> ToastManager.error(
                "Could not upload " + fixedName + ": " + ex.getMessage()));
            return null;
        }
    }

    // =========================================================
    // CONTACT DETAILS
    // =========================================================

    private void loadContactTab() {
        contactAddressField.setText(nvl(setting("website_contact_address")));
        contactPhoneField.setText(nvl(setting("website_contact_phone")));
        contactFacebookUrlField.setText(nvl(setting("website_contact_facebook_url")));
        contactFacebookLabelField.setText(nvl(setting("website_contact_facebook_label")));
        contactYoutubeUrlField.setText(nvl(setting("website_contact_youtube_url")));
        contactYoutubeLabelField.setText(nvl(setting("website_contact_youtube_label")));
        contactEmailField.setText(nvl(setting("website_contact_email")));
    }

    @FXML
    public void handleSaveContact() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String phone = contactPhoneField.getText().trim();
            saveSetting(conn, "website_contact_address", contactAddressField.getText().trim());
            saveSetting(conn, "website_contact_phone", phone);
            saveSetting(conn, "website_contact_phone_href", phoneToTelHref(phone));
            saveSetting(conn, "website_contact_facebook_url", contactFacebookUrlField.getText().trim());
            saveSetting(conn, "website_contact_facebook_label", contactFacebookLabelField.getText().trim());
            saveSetting(conn, "website_contact_youtube_url", contactYoutubeUrlField.getText().trim());
            saveSetting(conn, "website_contact_youtube_label", contactYoutubeLabelField.getText().trim());
            saveSetting(conn, "website_contact_email", contactEmailField.getText().trim());
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(), "Updated website contact details");
            triggerExport();
            ToastManager.success("Contact details saved.");
        } catch (SQLException ex) {
            ex.printStackTrace();
            ToastManager.error("Failed to save contact details: " + ex.getMessage());
        }
    }

    // =========================================================
    // SETTINGS HELPERS (shared by Leaders & Contact tabs)
    // =========================================================

    private String setting(String key) { return GitHubSync.loadSetting(key); }

    private void saveSetting(Connection conn, String key, String value) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO system_settings (setting_key, setting_value) VALUES (?,?) " +
            "ON DUPLICATE KEY UPDATE setting_value=?");
        ps.setString(1, key); ps.setString(2, value); ps.setString(3, value);
        ps.executeUpdate();
    }

    private void setPreview(ImageView view, String pathOrUrl) {
        if (view == null) return;
        Image img = loadImageFromPathOrUrl(pathOrUrl);
        if (img != null) view.setImage(img);
    }

    /**
     * Turns a human-typed phone number (spaces, dashes, brackets and all)
     * into a proper "tel:" link — no separate raw-href field to fill in and
     * potentially get wrong (a blank/malformed href previously produced a
     * "Call" button on the live site that didn't actually dial anything).
     */
    private static String phoneToTelHref(String phone) {
        if (phone == null || phone.isBlank()) return "";
        String digits = phone.replaceAll("[^0-9+]", "");
        return digits.isEmpty() ? "" : "tel:" + digits;
    }

    private static String nvl(String s) { return s != null ? s : ""; }
}
