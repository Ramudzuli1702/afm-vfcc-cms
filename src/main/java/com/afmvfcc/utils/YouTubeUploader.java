package com.afmvfcc.utils;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles OAuth 2.0 authorisation and video uploads to YouTube using YouTube Data API v3.
 *
 * client_secrets.json resolution order:
 *   1. %APPDATA%\AFM_VFCC_CMS\client_secrets.json  (user-supplied via Settings)
 *   2. Bundled resource /com/afmvfcc/client_secrets.json        (build-time default)
 */
public class YouTubeUploader {

    private static final String APP_NAME       = "AFM-VFCC-CMS";
    private static final String BUNDLED_SECRET = "/com/afmvfcc/client_secrets.json";
    private static final List<String> SCOPES   = Arrays.asList(
            "https://www.googleapis.com/auth/youtube.upload",
            "https://www.googleapis.com/auth/youtube"
    );

    /**
     * Read timeout for upload requests.
     * 0 = no timeout (safest for very large files on slow connections).
     * Raise to e.g. 10 * 60 * 1000 (10 min) if you want a hard cap.
     */
    private static final int READ_TIMEOUT_MS    = 0;

    /**
     * Connect timeout — kept short so connection failures surface quickly.
     * 30 seconds is generous for a local → YouTube TLS handshake.
     */
    private static final int CONNECT_TIMEOUT_MS = 30_000;

    /** Folder where the user-placed client_secrets.json is stored. */
    public static final File CONFIG_DIR = new File(
            System.getenv("APPDATA") != null
                    ? System.getenv("APPDATA") + File.separator + "AFM_VFCC_CMS"
                    : System.getProperty("user.home") + File.separator + ".afmvfcc"
    );

    /** The user-supplied secrets file (may not exist). */
    public static final File USER_SECRETS_FILE = new File(CONFIG_DIR, "client_secrets.json");

    private static final File TOKEN_DIR = new File(CONFIG_DIR, "youtube_tokens");

    // ── Result ───────────────────────────────────────────────────────────────

    public static class UploadResult {
        public final String videoId;
        public final String videoUrl;

        public UploadResult(String videoId) {
            this.videoId  = videoId;
            this.videoUrl = "https://www.youtube.com/watch?v=" + videoId;
        }
    }

    // ── Configuration check ──────────────────────────────────────────────────

    /**
     * Returns true when a client_secrets.json is available either as a
     * user-placed file in %APPDATA%\AFM_VFCC_CMS\ or bundled inside the JAR.
     */
    public static boolean isConfigured() {
        return USER_SECRETS_FILE.exists()
                || YouTubeUploader.class.getResourceAsStream(BUNDLED_SECRET) != null;
    }

    /**
     * Returns a brief human-readable description of where the secrets file was found,
     * or null if not configured.
     */
    public static String getSecretsSource() {
        if (USER_SECRETS_FILE.exists()) return USER_SECRETS_FILE.getAbsolutePath();
        if (YouTubeUploader.class.getResourceAsStream(BUNDLED_SECRET) != null) return "Built-in (bundled)";
        return null;
    }

    // ── Secrets stream ───────────────────────────────────────────────────────

    /** Opens the best available client_secrets stream, or throws if none found. */
    private InputStream openSecretsStream() throws IOException {
        if (USER_SECRETS_FILE.exists()) {
            return new FileInputStream(USER_SECRETS_FILE);
        }
        InputStream bundled = getClass().getResourceAsStream(BUNDLED_SECRET);
        if (bundled != null) return bundled;
        throw new IOException(
                "client_secrets.json not found.\n" +
                "Please upload it in Settings → Broadcast → Upload client_secrets.json.");
    }

    // ── Timeout-aware request initialiser ────────────────────────────────────

    /**
     * Wraps a {@link Credential} so it both authorises the request AND applies
     * custom connect / read timeouts.
     *
     * The Google HTTP client sets read timeout to 20 s by default, which is far
     * too short for large video uploads on slow connections. Setting READ_TIMEOUT_MS
     * to 0 disables the read timeout entirely — the upload will take as long as it
     * needs to, which is the correct behaviour for a resumable chunked upload.
     */
    private static HttpRequestInitializer withTimeouts(Credential credential) {
        return (HttpRequest request) -> {
            credential.initialize(request);                    // sets Authorization header
            request.setConnectTimeout(CONNECT_TIMEOUT_MS);
            request.setReadTimeout(READ_TIMEOUT_MS);           // 0 = infinite
        };
    }

    // ── Upload ───────────────────────────────────────────────────────────────

    public UploadResult upload(
            File videoFile,
            String title,
            String description,
            String privacyStatus,
            String categoryId,
            Consumer<Double> progressListener,
            Consumer<String> statusListener
    ) throws IOException, GeneralSecurityException {

        statusListener.accept("Authorising with YouTube...");

        GoogleClientSecrets clientSecrets;
        try (InputStream stream = openSecretsStream()) {
            clientSecrets = GoogleClientSecrets.load(GsonFactory.getDefaultInstance(),
                    new InputStreamReader(stream));
        }

        TOKEN_DIR.mkdirs();

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                clientSecrets,
                SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(TOKEN_DIR))
                .setAccessType("offline")
                .build();

        Credential credential = new AuthorizationCodeInstalledApp(
                flow,
                new LocalServerReceiver.Builder().setPort(8888).build())
                .authorize("afmvfcc_church");

        // ── Build YouTube client with timeout-aware initialiser ──────────────
        YouTube youtube = new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                withTimeouts(credential))          // <-- replaces passing credential directly
                .setApplicationName(APP_NAME)
                .build();

        statusListener.accept("Preparing video metadata...");

        // ── Sanitise title ───────────────────────────────────────────────────
        String safeTitle = (title != null ? title.replaceAll("[<>]", "") : "").trim();
        if (safeTitle.length() > 100) safeTitle = safeTitle.substring(0, 100).trim();
        if (safeTitle.isEmpty())       safeTitle = "AFM VFCC Church Service";

        Video video = new Video();
        video.setStatus(new VideoStatus().setPrivacyStatus(
                privacyStatus != null ? privacyStatus.toLowerCase() : "public"));

        VideoSnippet snippet = new VideoSnippet();
        snippet.setTitle(safeTitle);
        snippet.setDescription(description != null ? description : "");
        snippet.setCategoryId(categoryId != null ? categoryId : "22");
        video.setSnippet(snippet);

        statusListener.accept("Starting upload to YouTube...");

        InputStreamContent mediaContent = new InputStreamContent(
                "video/*", new BufferedInputStream(new FileInputStream(videoFile)));
        mediaContent.setLength(videoFile.length());

        YouTube.Videos.Insert videoInsert = youtube.videos()
                .insert(Arrays.asList("snippet", "status"), video, mediaContent);

        MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();
        uploader.setDirectUploadEnabled(false);
        uploader.setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE * 4);

        uploader.setProgressListener(u -> {
            switch (u.getUploadState()) {
                case INITIATION_STARTED  -> statusListener.accept("Initiating upload...");
                case INITIATION_COMPLETE -> statusListener.accept("Upload initiated...");
                case MEDIA_IN_PROGRESS   -> {
                    double progress = u.getProgress();
                    progressListener.accept(progress);
                    long uploadedMB = (long) (videoFile.length() * progress) / (1024 * 1024);
                    long totalMB    = videoFile.length() / (1024 * 1024);
                    statusListener.accept(String.format(
                            "Uploading... %d%% (%d MB / %d MB)",
                            (int) (progress * 100), uploadedMB, totalMB));
                }
                case MEDIA_COMPLETE -> {
                    progressListener.accept(1.0);
                    statusListener.accept("Finalising upload...");
                }
            }
        });

        try {
            Video returnedVideo = videoInsert.execute();
            return new UploadResult(returnedVideo.getId());
        } catch (GoogleJsonResponseException e) {
            System.err.println("YouTube API Error " + e.getStatusCode() + ": " + e.getDetails());
            throw e;
        }
    }

    // ── Token revocation ─────────────────────────────────────────────────────

    public static void revokeToken() {
        deleteDirectory(TOKEN_DIR);
    }

    private static void deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) f.delete();
            dir.delete();
        }
    }
}
