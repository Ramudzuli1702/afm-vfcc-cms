package com.afmvfcc.utils;

import javafx.concurrent.Task;

import java.util.function.Consumer;


public class BroadcastTaskManager {

    // ── Singleton ────────────────────────────────────────────────────────────

    private static final BroadcastTaskManager INSTANCE = new BroadcastTaskManager();

    public static BroadcastTaskManager getInstance() { return INSTANCE; }

    private BroadcastTaskManager() {}

    // ── Download state ───────────────────────────────────────────────────────

    public enum Phase { IDLE, RUNNING, SUCCEEDED, FAILED }

    // Download
    private Task<?> downloadTask;
    private Phase   downloadPhase   = Phase.IDLE;
    private double  downloadProgress = 0;
    private String  downloadStatus  = "";
    private boolean downloadError   = false;
    private java.io.File downloadedFile;

    // Upload
    private Task<?> uploadTask;
    private Phase   uploadPhase    = Phase.IDLE;
    private double  uploadProgress  = 0;
    private String  uploadStatus   = "";
    private boolean uploadError    = false;
    private String  lastYoutubeUrl;
    private String  lastVideoId;

    // ── Live listeners (set by the currently active controller) ─────────────

    // Download listeners
    private Consumer<Double>  downloadProgressListener;
    private Consumer<String>  downloadStatusListener;    // (msg)
    private Runnable          downloadSuccessListener;
    private Runnable          downloadFailureListener;

    // Upload listeners
    private Consumer<Double>  uploadProgressListener;
    private Consumer<String>  uploadStatusListener;
    private Runnable          uploadSuccessListener;
    private Runnable          uploadFailureListener;

    // ── Registration ─────────────────────────────────────────────────────────

    public void attachDownloadListeners(
            Consumer<Double> progress,
            Consumer<String> status,
            Runnable onSuccess,
            Runnable onFailure) {
        this.downloadProgressListener = progress;
        this.downloadStatusListener   = status;
        this.downloadSuccessListener  = onSuccess;
        this.downloadFailureListener  = onFailure;
    }

    public void attachUploadListeners(
            Consumer<Double> progress,
            Consumer<String> status,
            Runnable onSuccess,
            Runnable onFailure) {
        this.uploadProgressListener = progress;
        this.uploadStatusListener   = status;
        this.uploadSuccessListener  = onSuccess;
        this.uploadFailureListener  = onFailure;
    }

    public void detachListeners() {
        downloadProgressListener = null;
        downloadStatusListener   = null;
        downloadSuccessListener  = null;
        downloadFailureListener  = null;
        uploadProgressListener   = null;
        uploadStatusListener     = null;
        uploadSuccessListener    = null;
        uploadFailureListener    = null;
    }

    // ── Notification helpers (called from background threads via Platform.runLater) ──

    public void notifyDownloadProgress(double p) {
        downloadProgress = p;
        if (downloadProgressListener != null) downloadProgressListener.accept(p);
    }

    public void notifyDownloadStatus(String msg, boolean error) {
        downloadStatus = msg;
        downloadError  = error;
        if (downloadStatusListener != null) downloadStatusListener.accept(msg);
    }

    public void notifyDownloadSuccess(java.io.File file) {
        downloadedFile  = file;
        downloadPhase   = Phase.SUCCEEDED;
        downloadProgress = 1.0;
        if (downloadSuccessListener != null) downloadSuccessListener.run();
    }

    public void notifyDownloadFailure(String msg) {
        downloadPhase  = Phase.FAILED;
        downloadStatus = msg;
        downloadError  = true;
        if (downloadFailureListener != null) downloadFailureListener.run();
    }

    public void notifyUploadProgress(double p) {
        uploadProgress = p;
        if (uploadProgressListener != null) uploadProgressListener.accept(p);
    }

    public void notifyUploadStatus(String msg, boolean error) {
        uploadStatus = msg;
        uploadError  = error;
        if (uploadStatusListener != null) uploadStatusListener.accept(msg);
    }

    public void notifyUploadSuccess(String videoId, String videoUrl) {
        lastVideoId     = videoId;
        lastYoutubeUrl  = videoUrl;
        uploadPhase     = Phase.SUCCEEDED;
        uploadProgress  = 1.0;
        if (uploadSuccessListener != null) uploadSuccessListener.run();
    }

    public void notifyUploadFailure(String msg) {
        uploadPhase  = Phase.FAILED;
        uploadStatus = msg;
        uploadError  = true;
        if (uploadFailureListener != null) uploadFailureListener.run();
    }

    // ── Setters called when tasks start ─────────────────────────────────────

    public void startDownload(Task<?> task) {
        this.downloadTask     = task;
        this.downloadPhase    = Phase.RUNNING;
        this.downloadProgress = 0;
        this.downloadStatus   = "";
        this.downloadedFile   = null;
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    public void startUpload(Task<?> task) {
        this.uploadTask    = task;
        this.uploadPhase   = Phase.RUNNING;
        this.uploadProgress = 0;
        this.uploadStatus  = "";
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    /** Called when user manually selects a local file (skipping download). */
    public void setDownloadedFileManually(java.io.File file) {
        this.downloadedFile  = file;
        this.downloadPhase   = Phase.SUCCEEDED;
        this.downloadProgress = 1.0;
        this.downloadStatus  = "✓ File selected";
        this.downloadError   = false;
    }

    /** Called after a successful upload to reset for next use. */
    public void resetAfterUpload() {
        downloadTask      = null;
        downloadPhase     = Phase.IDLE;
        downloadProgress  = 0;
        downloadStatus    = "";
        downloadError     = false;
        downloadedFile    = null;

        uploadTask    = null;
        uploadPhase   = Phase.IDLE;
        uploadProgress = 0;
        uploadStatus  = "";
        uploadError   = false;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Phase   getDownloadPhase()    { return downloadPhase; }
    public double  getDownloadProgress() { return downloadProgress; }
    public String  getDownloadStatus()   { return downloadStatus; }
    public boolean isDownloadError()     { return downloadError; }
    public java.io.File getDownloadedFile() { return downloadedFile; }

    public Phase   getUploadPhase()    { return uploadPhase; }
    public double  getUploadProgress() { return uploadProgress; }
    public String  getUploadStatus()   { return uploadStatus; }
    public boolean isUploadError()     { return uploadError; }
    public String  getLastYoutubeUrl() { return lastYoutubeUrl; }
    public String  getLastVideoId()    { return lastVideoId; }

    public boolean isDownloadRunning() { return downloadPhase == Phase.RUNNING; }
    public boolean isUploadRunning()   { return uploadPhase   == Phase.RUNNING; }
}
