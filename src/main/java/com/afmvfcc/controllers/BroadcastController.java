package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.BroadcastTaskManager;
import com.afmvfcc.utils.SessionManager;
import com.afmvfcc.utils.YouTubeUploader;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.*;
import java.net.URI;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class BroadcastController {

    // ── Warning banner ─────────────────────────────────────
    @FXML private VBox ytdlpWarningBox;

    // ── Step 1 — Download ──────────────────────────────────
    @FXML private TextField   facebookUrlField;
    @FXML private TextField   downloadFolderField;
    @FXML private Button      downloadBtn;
    @FXML private VBox        downloadProgressBox;
    @FXML private ProgressBar downloadProgressBar;
    @FXML private Label       downloadStatusLabel;
    @FXML private VBox        downloadedFileBox;
    @FXML private Label       downloadedFileLabel;

    // ── Step 2 — Upload ────────────────────────────────────
    @FXML private TextField        youtubeTitleField;
    @FXML private TextArea         youtubeDescField;
    @FXML private ComboBox<String> privacyCombo;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private Label            oauthNoticeLabel;
    @FXML private Button           uploadBtn;
    @FXML private Button           reauthorizeBtn;
    @FXML private VBox             uploadProgressBox;
    @FXML private ProgressBar      uploadProgressBar;
    @FXML private Label            uploadStatusLabel;
    @FXML private VBox             uploadSuccessBox;
    @FXML private Label            youtubeLinkLabel;

    // ── History table ──────────────────────────────────────
    @FXML private TableView<String[]>           historyTable;
    @FXML private TableColumn<String[], String> colHTitle;
    @FXML private TableColumn<String[], String> colHStatus;
    @FXML private TableColumn<String[], String> colHDate;
    @FXML private TableColumn<String[], String> colHUploadedBy;
    @FXML private TableColumn<String[], String> colHYoutubeLink;
    @FXML private TableColumn<String[], Void>   colHActions;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private static final String[][] CATEGORIES = {
            { "22", "People & Blogs"        },
            { "29", "Nonprofits & Activism" },
            { "27", "Education"             },
            { "28", "Science & Technology"  },
            { "24", "Entertainment"         },
            { "25", "News & Politics"       },
    };

    private final BroadcastTaskManager tm = BroadcastTaskManager.getInstance();

    // ─────────────────────────────────────────────────────────────────────────
    // EXIT GUARD — called by Main.java (window X) and any logout handler
    // ─────────────────────────────────────────────────────────────────────────
    public static boolean confirmExitAllowed() {
        BroadcastTaskManager tm = BroadcastTaskManager.getInstance();

        boolean downloading = tm.getDownloadPhase() == BroadcastTaskManager.Phase.RUNNING;
        boolean uploading   = tm.getUploadPhase()   == BroadcastTaskManager.Phase.RUNNING;

        if (!downloading && !uploading) return true;

        // Build a descriptive message so the user knows exactly what is running
        String activity;
        if (downloading && uploading) activity = "a download and an upload are";
        else if (downloading)         activity = "a video download is";
        else                          activity = "a YouTube upload is";

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(alert.getDialogPane());
        alert.setTitle("Task In Progress");
        alert.setHeaderText("Cannot exit — " + activity + " still running");
        alert.setContentText(
                "Closing or logging out now will cancel the operation and the video may be lost.\n\n" +
                "Wait for it to finish, or click \"Force Exit\" to stop it immediately.");

        // Replace default buttons with clearer labels
        ButtonType waitBtn  = new ButtonType("Wait — Keep Running", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType forceBtn = new ButtonType("Force Exit",           ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(waitBtn, forceBtn);

        return alert.showAndWait()
                    .map(btn -> btn == forceBtn)
                    .orElse(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INIT / TEARDOWN
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        privacyCombo.getItems().addAll("Public", "Unlisted", "Private");
        privacyCombo.setValue("Public");
        for (String[] cat : CATEGORIES) categoryCombo.getItems().add(cat[1]);
        categoryCombo.setValue("People & Blogs");

        loadDownloadFolder();
        checkYtdlp();

        boolean hasToken = hasYoutubeToken();
        oauthNoticeLabel.setVisible(!hasToken);
        reauthorizeBtn.setVisible(hasToken);
        reauthorizeBtn.setManaged(hasToken);

        setupHistoryTable();
        loadHistory();

        attachToTaskManager();
        restoreTaskState();
    }

    private void attachToTaskManager() {
        tm.attachDownloadListeners(
                p   -> downloadProgressBar.setProgress(p),
                msg -> setDownloadStatus(msg, tm.isDownloadError()),
                this::onDownloadSuccess,
                this::onDownloadFailure
        );
        tm.attachUploadListeners(
                p   -> uploadProgressBar.setProgress(p),
                msg -> setUploadStatus(msg, tm.isUploadError()),
                this::onUploadSuccess,
                this::onUploadFailure
        );
    }

    private void restoreTaskState() {
        switch (tm.getDownloadPhase()) {
            case RUNNING -> {
                showDownloadProgress(true);
                downloadProgressBar.setProgress(tm.getDownloadProgress());
                setDownloadStatus(tm.getDownloadStatus(), false);
                downloadBtn.setDisable(true);
            }
            case SUCCEEDED -> {
                File f = tm.getDownloadedFile();
                if (f != null && f.exists()) {
                    showDownloadProgress(true);
                    downloadProgressBar.setProgress(1.0);
                    setDownloadStatus("✓ Download complete", false);
                    showDownloadedFileBox(f);
                    uploadBtn.setDisable(false);
                }
            }
            case FAILED -> {
                showDownloadProgress(true);
                downloadProgressBar.setProgress(0);
                setDownloadStatus(tm.getDownloadStatus(), true);
                downloadBtn.setDisable(false);
            }
            default -> {}
        }

        switch (tm.getUploadPhase()) {
            case RUNNING -> {
                showUploadProgress(true);
                uploadProgressBar.setProgress(tm.getUploadProgress());
                setUploadStatus(tm.getUploadStatus(), false);
                uploadBtn.setDisable(true);
            }
            case SUCCEEDED -> {
                showUploadProgress(true);
                uploadProgressBar.setProgress(1.0);
                setUploadStatus("✓ Upload complete!", false);
                showUploadSuccess(tm.getLastYoutubeUrl());
            }
            case FAILED -> {
                showUploadProgress(true);
                uploadProgressBar.setProgress(0);
                setUploadStatus(tm.getUploadStatus(), true);
                uploadBtn.setDisable(false);
            }
            default -> {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // YT-DLP CHECK
    // ─────────────────────────────────────────────────────────────────────────

    private void checkYtdlp() {
        boolean found = findYtdlp() != null;
        ytdlpWarningBox.setVisible(!found);
        ytdlpWarningBox.setManaged(!found);
        downloadBtn.setDisable(!found);
    }

    private String findYtdlp() {
        return findExecutable("yt-dlp.exe", "yt-dlp", "--version", new String[] {
                "C:\\yt-dlp\\yt-dlp.exe",
                System.getenv("USERPROFILE") + "\\yt-dlp.exe",
                System.getenv("USERPROFILE") + "\\Downloads\\yt-dlp.exe",
        });
    }

    private String findFfmpeg() {
        return findExecutable("ffmpeg.exe", "ffmpeg", "-version", new String[] {
                "C:\\ffmpeg\\ffmpeg.exe",
                "C:\\ffmpeg\\bin\\ffmpeg.exe",
                System.getenv("USERPROFILE") + "\\ffmpeg.exe",
                System.getenv("USERPROFILE") + "\\Downloads\\ffmpeg.exe",
        });
    }

    /**
     * Looks for {@code exeName} next to the running jar (and its parent folder,
     * which is where bundled tools live in the installed app), then in the
     * current working directory (where it lives during `gradle run` — a
     * directory-based classpath's code source resolves to build/classes/...,
     * not the project root, so that check alone misses it in dev mode), then
     * on the system PATH, then in a list of common fallback locations.
     */
    private String findExecutable(String exeName, String pathCommand, String versionFlag, String[] fallbackLocations) {
        try {
            File jar = new File(BroadcastController.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile();
            File next = new File(jar, exeName);
            if (next.exists()) return next.getAbsolutePath();
            File up = new File(jar.getParentFile(), exeName);
            if (up.exists()) return up.getAbsolutePath();
        } catch (Exception ignored) {}

        File cwd = new File(System.getProperty("user.dir"), exeName);
        if (cwd.exists()) return cwd.getAbsolutePath();

        try {
            Process p = new ProcessBuilder(pathCommand, versionFlag).start();
            p.waitFor();
            return pathCommand;
        } catch (Exception ignored) {}

        for (String loc : fallbackLocations) {
            if (loc != null && new File(loc).exists()) return loc;
        }
        return null;
    }

    @FXML public void handleCheckYtdlp() {
        checkYtdlp();
        if (findYtdlp() != null)
            showInfo("yt-dlp found!", "yt-dlp.exe has been detected. You can now download Facebook videos.");
    }

    @FXML public void handleDownloadYtdlp() {
        try {
            Desktop.getDesktop().browse(new URI(
                    "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"));
            showInfo("Downloading yt-dlp",
                    "yt-dlp.exe is downloading in your browser.\n\n" +
                    "Once downloaded, move it to the same folder as the AFM VFCC CMS application, " +
                    "then click \"Check Again\".");
        } catch (Exception e) {
            showError("Could not open browser: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 1 — DOWNLOAD FROM FACEBOOK
    // ─────────────────────────────────────────────────────────────────────────

    @FXML public void handleBrowseDownload() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Download Folder");
        String current = downloadFolderField.getText().trim();
        if (!current.isEmpty()) {
            File f = new File(current);
            if (f.exists()) chooser.setInitialDirectory(f);
        }
        File dir = chooser.showDialog(downloadFolderField.getScene().getWindow());
        if (dir != null) downloadFolderField.setText(dir.getAbsolutePath());
    }

    @FXML public void handleBrowseLocalFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Video File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.mkv", "*.webm", "*.avi", "*.mov"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        String folder = downloadFolderField.getText().trim();
        if (!folder.isEmpty()) {
            File f = new File(folder);
            if (f.exists()) chooser.setInitialDirectory(f);
        }

        File chosen = chooser.showOpenDialog(downloadFolderField.getScene().getWindow());
        if (chosen == null) return;

        tm.setDownloadedFileManually(chosen);
        showDownloadProgress(false);
        showDownloadedFileBox(chosen);
        uploadBtn.setDisable(false);

        if (youtubeTitleField.getText().isBlank()) {
            String name = chosen.getName().replaceAll("\\.(mp4|mkv|webm|avi|mov)$", "");
            youtubeTitleField.setText(name);
        }
    }

    @FXML public void handleDownload() {
        String url    = facebookUrlField.getText().trim();
        String folder = downloadFolderField.getText().trim();

        if (url.isEmpty()) { showError("Please enter a Facebook video URL."); return; }
        if (!url.contains("facebook.com") && !url.contains("fb.watch")) {
            showError("That doesn't look like a Facebook URL. Please check and try again.");
            return;
        }
        if (folder.isEmpty()) { showError("Please choose a download folder."); return; }

        String ytdlp = findYtdlp();
        if (ytdlp == null) { checkYtdlp(); return; }

        String ffmpeg = findFfmpeg();
        if (ffmpeg == null) {
            showError("ffmpeg.exe was not found next to the application.\n\n" +
                    "Without it, yt-dlp cannot merge the downloaded video and audio into a single file. " +
                    "Please make sure ffmpeg.exe is in the same folder as the application and try again.");
            return;
        }
        String ffmpegLocation = new File(ffmpeg).getParent();
        if (ffmpegLocation == null) ffmpegLocation = ffmpeg;

        File outDir = new File(folder);
        outDir.mkdirs();

        downloadedFileBox.setVisible(false);
        downloadedFileBox.setManaged(false);
        uploadBtn.setDisable(true);
        showDownloadProgress(true);
        setDownloadStatus("Starting download...", false);
        downloadProgressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        downloadBtn.setDisable(true);

        // ── Suspend inactivity timeout for the duration of the download ──
        SessionManager.getInstance().suspendTimeout();

        final String finalUrl           = url;
        final String finalYtdlp         = ytdlp;
        final String finalFfmpegLocation = ffmpegLocation;

        Task<File> task = new Task<>() {
            @Override
            protected File call() throws Exception {
                String outputTemplate = outDir.getAbsolutePath()
                        + File.separator + "%(title)s.%(ext)s";

                ProcessBuilder pb = new ProcessBuilder(
                        finalYtdlp,
                        "--cookies", "cookies.txt",
                        "--user-agent", "Mozilla/5.0",
                        "--format", "bv*+ba/b",
                        "--merge-output-format", "mp4",
                        "--ffmpeg-location", finalFfmpegLocation,
                        "--no-playlist",
                        "--newline",
                        "--output", outputTemplate,
                        finalUrl);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                String downloadedFilename = null;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                        final String l = line;
                        if (l.contains("[download]") && l.contains("%")) {
                            try {
                                String pct = l.replaceAll(".*?(\\d+\\.\\d+)%.*", "$1");
                                double progress = Double.parseDouble(pct) / 100.0;
                                Platform.runLater(() -> {
                                    tm.notifyDownloadProgress(progress);
                                    tm.notifyDownloadStatus(l.trim(), false);
                                });
                            } catch (Exception ignored) {
                                Platform.runLater(() -> tm.notifyDownloadStatus(l.trim(), false));
                            }
                        } else if (l.contains("Destination:")) {
                            downloadedFilename = l.substring(l.indexOf("Destination:") + 12).trim();
                            Platform.runLater(() -> tm.notifyDownloadStatus(l.trim(), false));
                        } else {
                            Platform.runLater(() -> tm.notifyDownloadStatus(l.trim(), false));
                        }
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) throw new IOException("yt-dlp failed. Check console for details.");

                if (downloadedFilename != null && new File(downloadedFilename).exists())
                    return new File(downloadedFilename);

                File[] mp4s = outDir.listFiles((d, n) ->
                        n.toLowerCase().endsWith(".mp4") || n.toLowerCase().endsWith(".mkv"));
                if (mp4s != null && mp4s.length > 0) {
                    File newest = mp4s[0];
                    for (File f : mp4s)
                        if (f.lastModified() > newest.lastModified()) newest = f;
                    return newest;
                }

                throw new IOException("Download completed but file not found in " + outDir);
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            File downloaded = task.getValue();
            tm.notifyDownloadSuccess(downloaded);
            downloadBtn.setDisable(false);
            downloadProgressBar.setProgress(1.0);
            showDownloadedFileBox(downloaded);
            uploadBtn.setDisable(false);
            if (youtubeTitleField.getText().isBlank()) {
                String name = downloaded.getName().replaceAll("\\.(mp4|mkv|webm)$", "");
                youtubeTitleField.setText(name);
            }
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            String msg = task.getException().getMessage();
            tm.notifyDownloadFailure(msg);
            downloadBtn.setDisable(false);
        }));

        tm.startDownload(task);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 2 — UPLOAD TO YOUTUBE
    // ─────────────────────────────────────────────────────────────────────────

    @FXML public void handleUpload() {
        File fileToUpload = tm.getDownloadedFile();

        if (fileToUpload == null || !fileToUpload.exists()) {
            showError("No video file to upload. Please download or browse for a video first.");
            return;
        }

        String title = youtubeTitleField.getText().trim();
        if (title.isEmpty()) { showError("Please enter a YouTube title."); return; }

        if (!YouTubeUploader.isConfigured()) { showYoutubeSetupDialog(); return; }

        String description   = youtubeDescField.getText().trim();
        String privacyStatus = privacyCombo.getValue().toLowerCase();
        String categoryId    = getCategoryId(categoryCombo.getValue());

        uploadSuccessBox.setVisible(false);
        uploadSuccessBox.setManaged(false);
        showUploadProgress(true);
        uploadProgressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        uploadBtn.setDisable(true);

        // ── Suspend inactivity timeout for the duration of the upload ──
        SessionManager.getInstance().suspendTimeout();

        Task<YouTubeUploader.UploadResult> task = new Task<>() {
            @Override
            protected YouTubeUploader.UploadResult call() throws Exception {
                return new YouTubeUploader().upload(
                        fileToUpload, title, description, privacyStatus, categoryId,
                        p   -> Platform.runLater(() -> tm.notifyUploadProgress(p)),
                        msg -> Platform.runLater(() -> tm.notifyUploadStatus(msg, false)));
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            YouTubeUploader.UploadResult result = task.getValue();

            tm.notifyUploadSuccess(result.videoId, result.videoUrl);
            uploadProgressBar.setProgress(1.0);
            uploadBtn.setDisable(false);
            showUploadSuccess(result.videoUrl);

            saveUploadRecord(title, description,
                    facebookUrlField.getText().trim(),
                    result.videoId, result.videoUrl,
                    fileToUpload.getAbsolutePath(),
                    privacyStatus, "Uploaded");

            deleteLocalFile(fileToUpload);

            AuditLogger.log(
                    SessionManager.getInstance().getCurrentUser().getId(),
                    "Uploaded service to YouTube: " + title + " → " + result.videoUrl);

            loadHistory();

            oauthNoticeLabel.setVisible(false);
            reauthorizeBtn.setVisible(false);
            reauthorizeBtn.setManaged(false);

            facebookUrlField.clear();
            tm.resetAfterUpload();
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            uploadBtn.setDisable(false);
            uploadProgressBar.setProgress(0);

            Throwable ex = task.getException();
            ex.printStackTrace();

            String msg = "Upload failed: " + ex.getMessage();

            if (ex instanceof GoogleJsonResponseException gex) {
                String reason = gex.getDetails() != null ? gex.getDetails().getMessage() : "";
                msg = "YouTube Error (" + gex.getStatusCode() + "): " + reason;
                if (gex.getStatusCode() == 403) {
                    msg += "\n\nYour Google Cloud project may still be in 'Testing' mode.\n" +
                           "Go to https://console.cloud.google.com → OAuth consent screen → Publish.";
                } else if (reason.toLowerCase().contains("quota")) {
                    msg += "\n\nYou have exceeded your daily YouTube upload quota.";
                }
            }

            tm.notifyUploadFailure(msg);
            setUploadStatus("✗ " + msg, true);

            saveUploadRecord(title, description,
                    facebookUrlField.getText().trim(),
                    null, null,
                    fileToUpload.getAbsolutePath(),
                    privacyStatus, "Failed");

            loadHistory();
        }));

        tm.startUpload(task);
    }

    // ── Task-manager callbacks ───────────────────────────────────────────────

    private void onDownloadSuccess() {
        File f = tm.getDownloadedFile();
        downloadBtn.setDisable(false);
        downloadProgressBar.setProgress(1.0);
        setDownloadStatus("✓ Download complete", false);
        if (f != null) showDownloadedFileBox(f);
        uploadBtn.setDisable(false);
        // ── Resume inactivity timeout — user gets a fresh 15-minute window ──
        SessionManager.getInstance().resumeTimeout();
    }

    private void onDownloadFailure() {
        downloadBtn.setDisable(false);
        downloadProgressBar.setProgress(0);
        setDownloadStatus("✗ " + tm.getDownloadStatus(), true);
        // ── Resume even on failure so the session isn't frozen ──
        SessionManager.getInstance().resumeTimeout();
    }

    private void onUploadSuccess() {
        uploadProgressBar.setProgress(1.0);
        setUploadStatus("✓ Upload complete!", false);
        uploadBtn.setDisable(false);
        showUploadSuccess(tm.getLastYoutubeUrl());
        loadHistory();
        // ── Resume inactivity timeout — user gets a fresh 15-minute window ──
        SessionManager.getInstance().resumeTimeout();
    }

    private void onUploadFailure() {
        uploadBtn.setDisable(false);
        uploadProgressBar.setProgress(0);
        setUploadStatus("✗ " + tm.getUploadStatus(), true);
        loadHistory();
        // ── Resume even on failure so the session isn't frozen ──
        SessionManager.getInstance().resumeTimeout();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RE-AUTHORISE
    // ─────────────────────────────────────────────────────────────────────────

    @FXML public void handleReauthorize(ActionEvent event) {
        YouTubeUploader.revokeToken();
        showInfo("YouTube Re-authorised",
                "The previous authorisation has been cleared.\n\n" +
                "When you try to upload again, you will be asked to log in with Google.");
        oauthNoticeLabel.setVisible(true);
        reauthorizeBtn.setVisible(false);
        reauthorizeBtn.setManaged(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // YOUTUBE LINK ACTIONS
    // ─────────────────────────────────────────────────────────────────────────

    @FXML public void handleOpenYoutubeLink() { openUrl(youtubeLinkLabel.getText().trim()); }

    @FXML public void handleCopyLink() {
        String link = tm.getLastYoutubeUrl() != null
                ? tm.getLastYoutubeUrl()
                : youtubeLinkLabel.getText().trim();
        if (!link.isEmpty()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(link);
            Clipboard.getSystemClipboard().setContent(content);
            showInfo("Copied!", "YouTube link copied to clipboard.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HISTORY TABLE
    // ─────────────────────────────────────────────────────────────────────────

    private void setupHistoryTable() {
        colHTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        colHStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        colHDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        colHUploadedBy.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[3]));

        colHYoutubeLink.setCellFactory(col -> new TableCell<>() {
            private final Hyperlink link = new Hyperlink();
            { link.setOnAction(e -> { if (!link.getText().isBlank()) openUrl(link.getText()); }); }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    String url = ((String[]) getTableRow().getItem())[4];
                    link.setText(url != null && !url.isBlank() ? url : "—");
                    setGraphic(link);
                }
            }
        });

        colHActions.setCellFactory(col -> new TableCell<>() {
            private final Button copyBtn = new Button("Copy Link");
            {
                copyBtn.getStyleClass().add("btn-secondary");
                copyBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                copyBtn.setOnAction(e -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        String url = ((String[]) getTableRow().getItem())[4];
                        if (url != null && !url.isBlank()) {
                            ClipboardContent c = new ClipboardContent();
                            c.putString(url);
                            Clipboard.getSystemClipboard().setContent(c);
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null)
                    setGraphic(null);
                else {
                    String url = ((String[]) getTableRow().getItem())[4];
                    setGraphic((url != null && !url.isBlank()) ? copyBtn : null);
                }
            }
        });

        historyTable.setItems(FXCollections.observableArrayList());
    }

    private void loadHistory() {
        ObservableList<String[]> rows = FXCollections.observableArrayList();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT b.title, b.status, b.created_at, u.full_name, " +
                    "b.youtube_url, b.facebook_url, b.id " +
                    "FROM broadcast_uploads b " +
                    "LEFT JOIN users u ON u.id = b.uploaded_by " +
                    "ORDER BY b.created_at DESC");
            while (rs.next()) {
                rows.add(new String[] {
                        rs.getString("title"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at") != null
                                ? rs.getTimestamp("created_at").toLocalDateTime().format(FMT)
                                : "—",
                        rs.getString("full_name") != null ? rs.getString("full_name") : "—",
                        rs.getString("youtube_url")  != null ? rs.getString("youtube_url")  : "",
                        rs.getString("facebook_url") != null ? rs.getString("facebook_url") : "",
                        String.valueOf(rs.getInt("id"))
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        historyTable.setItems(rows);
    }

    @FXML public void handleRefreshHistory() { loadHistory(); }

    // ─────────────────────────────────────────────────────────────────────────
    // DATABASE
    // ─────────────────────────────────────────────────────────────────────────

    private void saveUploadRecord(String title, String description,
            String facebookUrl, String videoId, String youtubeUrl,
            String localPath, String privacy, String status) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO broadcast_uploads " +
                    "(title, description, facebook_url, youtube_video_id, youtube_url, " +
                    " local_file_path, privacy, status, uploaded_by) " +
                    "VALUES (?,?,?,?,?,?,?,?,?)");
            ps.setString(1, title);
            ps.setString(2, description);
            ps.setString(3, facebookUrl);
            ps.setString(4, videoId);
            ps.setString(5, youtubeUrl);
            ps.setString(6, localPath);
            ps.setString(7, privacy);
            ps.setString(8, status);
            ps.setInt(9, SessionManager.getInstance().getCurrentUser().getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void loadDownloadFolder() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT setting_value FROM system_settings WHERE setting_key = 'broadcast_download_folder'");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String val = rs.getString("setting_value");
                if (val != null && !val.isBlank()) downloadFolderField.setText(val);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean hasYoutubeToken() {
        File tokenDir = new File(
                System.getenv("APPDATA") != null
                        ? System.getenv("APPDATA") + File.separator + "AFM_VFCC_CMS"
                                + File.separator + "youtube_tokens"
                        : System.getProperty("user.home") + File.separator + ".afmvfcc"
                                + File.separator + "youtube_tokens");
        return tokenDir.exists() && tokenDir.listFiles() != null
                && tokenDir.listFiles().length > 0;
    }

    private void deleteLocalFile(File file) {
        if (file != null && file.exists()) {
            if (!file.delete()) file.deleteOnExit();
        }
    }

    private String getCategoryId(String label) {
        for (String[] cat : CATEGORIES)
            if (cat[1].equals(label)) return cat[0];
        return "22";
    }

    private void showDownloadProgress(boolean show) {
        downloadProgressBox.setVisible(show);
        downloadProgressBox.setManaged(show);
    }

    private void showUploadProgress(boolean show) {
        uploadProgressBox.setVisible(show);
        uploadProgressBox.setManaged(show);
    }

    private void showDownloadedFileBox(File f) {
        downloadedFileBox.setVisible(true);
        downloadedFileBox.setManaged(true);
        long mb = f.length() / (1024 * 1024);
        downloadedFileLabel.setText(f.getName() + "  (" + mb + " MB)");
    }

    private void showUploadSuccess(String url) {
        uploadSuccessBox.setVisible(true);
        uploadSuccessBox.setManaged(true);
        youtubeLinkLabel.setText(url != null ? url : "");
    }

    private void setDownloadStatus(String msg, boolean error) {
        downloadStatusLabel.setText(msg);
        downloadStatusLabel.setStyle(error
                ? "-fx-text-fill:#D94040;-fx-font-size:12px;"
                : "-fx-text-fill:#5A6275;-fx-font-size:12px;");
    }

    private void setUploadStatus(String msg, boolean error) {
        uploadStatusLabel.setText(msg);
        uploadStatusLabel.setStyle(error
                ? "-fx-text-fill:#D94040;-fx-font-size:12px;"
                : "-fx-text-fill:#5A6275;-fx-font-size:12px;");
    }

    private void openUrl(String url) {
        try { Desktop.getDesktop().browse(new URI(url)); }
        catch (Exception e) { showError("Could not open link: " + e.getMessage()); }
    }

    private void showYoutubeSetupDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        Main.applyStyles(alert.getDialogPane());
        alert.setTitle("YouTube API Setup Required");
        alert.setHeaderText("One-time YouTube setup needed");
        alert.setContentText(
                "To upload to YouTube, a client_secrets.json file is required.\n\n" +
                "Steps:\n" +
                "1. Go to https://console.cloud.google.com\n" +
                "2. Create a project and enable 'YouTube Data API v3'\n" +
                "3. Go to Credentials → Create OAuth 2.0 Client ID (Desktop app)\n" +
                "4. Download the JSON file\n" +
                "5. Go to Settings → Broadcast → Upload client_secrets.json\n\n" +
                "This only needs to be done once.");
        alert.showAndWait();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        Main.applyStyles(alert.getDialogPane());
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showInfo(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        Main.applyStyles(alert.getDialogPane());
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
