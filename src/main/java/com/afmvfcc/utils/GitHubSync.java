package com.afmvfcc.utils;

import com.afmvfcc.db.DatabaseConnection;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.sql.*;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Syncs data.js and poster images to a GitHub repository via the
 * GitHub Contents REST API.
 *
 * No git binary is required — all operations use pure HTTPS, so the
 * feature works correctly inside the compiled .exe installer.
 *
 * Settings stored in system_settings:
 *   github_token       – Personal Access Token (classic) with repo scope
 *   github_owner       – Repository owner / organisation (e.g. "Ramudzuli1702")
 *   github_repo        – Repository name (e.g. "afm_vfcc")
 *   github_branch      – Branch to commit to (default: "main")
 */
public class GitHubSync {

    private static final Logger LOG = Logger.getLogger(GitHubSync.class.getName());
    private static final String API = "https://api.github.com";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .build();
    private static final Gson GSON = new Gson();
    private static final java.time.Duration REQUEST_TIMEOUT = java.time.Duration.ofSeconds(30);

    // ---------------------------------------------------------------
    // Public entry points
    // ---------------------------------------------------------------

    /**
     * Push data.js content to the repository.
     * Returns null on success, an error message on failure.
     */
    public static String pushDataJs(String content) {
        Config cfg = loadConfig();
        if (cfg == null) return "GitHub settings not configured (token / owner / repo missing).";
        return putFile(cfg, "data.js", content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "Update data.js via AFM VFCC CMS");
    }

    /**
     * Upload a local poster image file to posters/<filename> in the repo.
     * Returns null on success, an error message on failure.
     *
     * @param localFile  the file on disk to upload
     * @param repoName   desired filename inside the posters/ folder
     * @return           the public URL for the image on GitHub Pages, or null on failure
     *                   (check second element of returned String[2] for error)
     */
    public static String[] uploadPoster(File localFile, String repoName) {
        Config cfg = loadConfig();
        if (cfg == null)
            return new String[]{null, "GitHub settings not configured."};
        try {
            byte[] bytes = Files.readAllBytes(localFile.toPath());
            String repoPath = "posters/" + repoName;
            String err = putFile(cfg, repoPath, bytes, "Upload poster via AFM VFCC CMS");
            if (err != null) return new String[]{null, err};
            // Return public GitHub Pages URL
            String url = buildPagesUrl(cfg, repoPath);
            return new String[]{url, null};
        } catch (IOException e) {
            return new String[]{null, e.getMessage()};
        }
    }

    /**
     * Delete a poster file from the repo by its repo-relative path.
     * Silently succeeds if the file does not exist.
     * Returns null on success, an error message on failure.
     */
    public static String deletePoster(String repoPath) {
        Config cfg = loadConfig();
        if (cfg == null) return "GitHub settings not configured.";
        return deleteFile(cfg, repoPath, "Remove poster via AFM VFCC CMS");
    }

    /**
     * Verify that the stored credentials can reach the repo.
     * Returns null on success, an error description on failure.
     */
    public static String testConnection() {
        Config cfg = loadConfig();
        if (cfg == null) return "GitHub token, owner, or repo not set.";
        try {
            HttpRequest req = baseRequest(cfg, "repos/" + cfg.owner + "/" + cfg.repo)
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return "HTTP " + resp.statusCode() + ": " + extractMessage(resp.body());
            }

            // A 200 here only proves the token can READ the repo — it says
            // nothing about write access or whether the configured branch
            // exists, both of which every actual publish depends on. Check
            // both explicitly so "✓ Connected" is a real guarantee, not just
            // a repo-visibility check.
            JsonObject repoObj = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (repoObj.has("permissions")) {
                JsonObject perms = repoObj.getAsJsonObject("permissions");
                if (perms.has("push") && !perms.get("push").getAsBoolean()) {
                    return "Connected, but this token does not have push access to "
                            + cfg.owner + "/" + cfg.repo + " — publishing will fail.";
                }
            }

            HttpRequest branchReq = baseRequest(cfg, "repos/" + cfg.owner + "/" + cfg.repo
                    + "/branches/" + cfg.branch).GET().build();
            HttpResponse<String> branchResp = HTTP.send(branchReq, HttpResponse.BodyHandlers.ofString());
            if (branchResp.statusCode() != 200) {
                return "Repo found, but branch \"" + cfg.branch + "\" does not exist.";
            }

            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /** True if enough settings are present for GitHub sync to be attempted at all. */
    public static boolean isConfigured() {
        return loadConfig() != null;
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    /** PUT (create or update) a file in the repo. Returns null on success. */
    private static String putFile(Config cfg, String path, byte[] bytes, String message) {
        try {
            // 1. Fetch existing SHA (required for updates)
            String sha = getFileSha(cfg, path);

            // 2. Build request body
            JsonObject body = new JsonObject();
            body.addProperty("message", message);
            body.addProperty("content", Base64.getEncoder().encodeToString(bytes));
            body.addProperty("branch", cfg.branch);
            if (sha != null) body.addProperty("sha", sha);

            HttpRequest req = baseRequest(cfg, "repos/" + cfg.owner + "/" + cfg.repo
                    + "/contents/" + path)
                    .PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code == 200 || code == 201) return null; // success
            return "GitHub PUT " + path + " → HTTP " + code + ": " + extractMessage(resp.body());
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /** DELETE a file from the repo. Returns null on success (or if file not found). */
    private static String deleteFile(Config cfg, String path, String message) {
        try {
            String sha = getFileSha(cfg, path);
            if (sha == null) return null; // already gone – treat as success

            JsonObject body = new JsonObject();
            body.addProperty("message", message);
            body.addProperty("sha", sha);
            body.addProperty("branch", cfg.branch);

            HttpRequest req = baseRequest(cfg, "repos/" + cfg.owner + "/" + cfg.repo
                    + "/contents/" + path)
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code == 200 || code == 204) return null;
            return "GitHub DELETE " + path + " → HTTP " + code + ": " + extractMessage(resp.body());
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /**
     * Returns the blob SHA of a file in the repo, or null if it genuinely does
     * not exist yet (404). Any other non-200 status (401 bad token, 403
     * rate-limited/no access, 5xx, etc.) is a real failure and must NOT be
     * treated as "file doesn't exist" — doing so previously caused putFile to
     * attempt a create-style PUT with no sha, which GitHub then rejected with
     * a confusing 422 that masked the actual auth/permission problem.
     */
    private static String getFileSha(Config cfg, String path) throws Exception {
        HttpRequest req = baseRequest(cfg, "repos/" + cfg.owner + "/" + cfg.repo
                + "/contents/" + path + "?ref=" + cfg.branch)
                .GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404) return null;
        if (resp.statusCode() != 200) {
            throw new IOException("GitHub GET " + path + " → HTTP " + resp.statusCode()
                    + ": " + extractMessage(resp.body()));
        }
        JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
        return obj.has("sha") ? obj.get("sha").getAsString() : null;
    }

    /** Build a pre-configured HttpRequest.Builder for the GitHub API. */
    private static HttpRequest.Builder baseRequest(Config cfg, String apiPath) {
        return HttpRequest.newBuilder()
                .uri(URI.create(API + "/" + apiPath))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + cfg.token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Content-Type", "application/json");
    }

    /**
     * Build the public GitHub Pages URL for a repo file.
     * Works for user/org pages (username.github.io/repo/path) and custom domains.
     * If a custom domain is configured, it is preferred.
     */
    private static String buildPagesUrl(Config cfg, String repoPath) {
        // Check for custom domain in settings
        String custom = loadSetting("website_custom_domain");
        if (custom != null && !custom.isEmpty()) {
            String base = custom.startsWith("http") ? custom : "https://" + custom;
            return base.replaceAll("/$", "") + "/" + repoPath;
        }
        // Default GitHub Pages URL
        return "https://" + cfg.owner.toLowerCase() + ".github.io/"
                + cfg.repo + "/" + repoPath;
    }

    private static String extractMessage(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            return obj.has("message") ? obj.get("message").getAsString() : json;
        } catch (Exception e) {
            return json;
        }
    }

    // ---------------------------------------------------------------
    // Settings / Config
    // ---------------------------------------------------------------

    private static Config loadConfig() {
        String token = loadSetting("github_token");
        String owner = loadSetting("github_owner");
        String repo  = loadSetting("github_repo");
        String branch = loadSetting("github_branch");
        if (token == null || token.isEmpty() || owner == null || owner.isEmpty()
                || repo == null || repo.isEmpty()) return null;
        Config cfg = new Config();
        cfg.token  = token;
        cfg.owner  = owner;
        cfg.repo   = repo;
        cfg.branch = (branch == null || branch.isEmpty()) ? "main" : branch;
        return cfg;
    }

    public static String loadSetting(String key) {
        try {
            PreparedStatement ps = DatabaseConnection.getConnection()
                    .prepareStatement("SELECT setting_value FROM system_settings WHERE setting_key=?");
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch (SQLException ignored) {}
        return null;
    }

    private static class Config {
        String token, owner, repo, branch;
    }
}