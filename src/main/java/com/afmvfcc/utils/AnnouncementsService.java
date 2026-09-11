package com.afmvfcc.utils;

import com.afmvfcc.db.DatabaseConnection;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;

/**
 * AnnouncementsService
 *
 * Handles posting church announcements to:
 *   1. WhatsApp via WhatsApp Cloud API (Meta)
 *   2. Facebook Page via Graph API
 *
 * All credentials are stored in system_settings and managed via Settings UI.
 */
public class AnnouncementsService {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    // ── Result ────────────────────────────────────────────────────────────────

    public static class PostResult {
        public final boolean success;
        public final String  message;
        public final String  postId;

        public PostResult(boolean success, String message, String postId) {
            this.success = success;
            this.message = message;
            this.postId  = postId;
        }

        public static PostResult ok(String postId) {
            return new PostResult(true, "Posted successfully", postId);
        }
        public static PostResult fail(String reason) {
            return new PostResult(false, reason, null);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // WHATSAPP CLOUD API
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sends a text announcement to a WhatsApp group or number via WhatsApp Cloud API.
     *
     * Required settings in system_settings:
     *   whatsapp_token         — Permanent access token from Meta Developer Portal
     *   whatsapp_phone_id      — Phone Number ID (not the phone number itself)
     *   whatsapp_recipient     — Recipient phone number in international format: +27821234567
     *                            (for groups, use the group ID obtained from the API)
     *
     * @param message  The announcement text
     * @return PostResult
     */
    public static PostResult sendWhatsApp(String message) {
        try {
            String token       = getSetting("whatsapp_token");
            String phoneId     = getSetting("whatsapp_phone_id");
            String recipient   = getSetting("whatsapp_recipient");

            if (isEmpty(token))     return PostResult.fail("WhatsApp access token not configured. Go to Settings → Announcements.");
            if (isEmpty(phoneId))   return PostResult.fail("WhatsApp Phone Number ID not configured. Go to Settings → Announcements.");
            if (isEmpty(recipient)) return PostResult.fail("WhatsApp recipient not configured. Go to Settings → Announcements.");

            String url  = "https://graph.facebook.com/v19.0/" + phoneId + "/messages";
            String body = "{"
                + "\"messaging_product\":\"whatsapp\","
                + "\"to\":\"" + escapeJson(recipient) + "\","
                + "\"type\":\"text\","
                + "\"text\":{\"body\":\"" + escapeJson(message) + "\"}"
                + "}";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                // Extract message ID from response
                String resp = response.body();
                String msgId = extractJson(resp, "id");
                return PostResult.ok(msgId != null ? msgId : "sent");
            } else {
                String error = extractJson(response.body(), "message");
                return PostResult.fail("WhatsApp API error " + response.statusCode()
                    + (error != null ? ": " + error : ": " + response.body()));
            }

        } catch (Exception e) {
            return PostResult.fail("WhatsApp error: " + e.getMessage());
        }
    }

    /**
     * Sends a WhatsApp message with a media attachment (image, PDF, video, or document).
     * The file is uploaded to WhatsApp first, then sent as a media message.
     */
    public static PostResult sendWhatsAppWithAttachment(String message, File attachment) {
        try {
            String token     = getSetting("whatsapp_token");
            String phoneId   = getSetting("whatsapp_phone_id");
            String recipient = getSetting("whatsapp_recipient");

            if (isEmpty(token) || isEmpty(phoneId) || isEmpty(recipient)) {
                return PostResult.fail("WhatsApp credentials not configured. Go to Settings → Announcements.");
            }

            // Step 1: Upload the media file
            String mediaId = uploadWhatsAppMedia(token, phoneId, attachment);
            if (mediaId == null) {
                return PostResult.fail("Failed to upload attachment to WhatsApp.");
            }

            // Step 2: Determine media type
            String filename  = attachment.getName().toLowerCase();
            String mediaType = getWhatsAppMediaType(filename);
            String typeKey   = getWhatsAppTypeKey(filename);

            // Step 3: Send media message with caption
            String url  = "https://graph.facebook.com/v19.0/" + phoneId + "/messages";
            String body = "{"
                + "\"messaging_product\":\"whatsapp\","
                + "\"to\":\"" + escapeJson(recipient) + "\","
                + "\"type\":\"" + typeKey + "\","
                + "\"" + typeKey + "\":{"
                + "\"id\":\"" + mediaId + "\","
                + "\"caption\":\"" + escapeJson(message) + "\""
                + "}"
                + "}";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return PostResult.ok("sent_with_media");
            } else {
                String error = extractJson(response.body(), "message");
                return PostResult.fail("WhatsApp media send error " + response.statusCode()
                    + (error != null ? ": " + error : ""));
            }

        } catch (Exception e) {
            return PostResult.fail("WhatsApp attachment error: " + e.getMessage());
        }
    }

    private static String uploadWhatsAppMedia(String token, String phoneId, File file) {
        try {
            String filename  = file.getName();
            String mimeType  = getMimeType(filename);
            String uploadUrl = "https://graph.facebook.com/v19.0/" + phoneId + "/media";

            // Build multipart body manually
            String boundary = "----FormBoundary" + System.currentTimeMillis();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // messaging_product field
            writeMultipartField(baos, boundary, "messaging_product", "whatsapp");

            // type field
            writeMultipartField(baos, boundary, "type", mimeType);

            // file field
            baos.write(("--" + boundary + "\r\n").getBytes());
            baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes());
            baos.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes());
            baos.write(Files.readAllBytes(file.toPath()));
            baos.write("\r\n".getBytes());
            baos.write(("--" + boundary + "--\r\n").getBytes());

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractJson(response.body(), "id");
            }
            System.err.println("Media upload failed: " + response.statusCode() + " " + response.body());
            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FACEBOOK GRAPH API — PAGE POSTS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Posts a text announcement to the church Facebook Page.
     *
     * Required settings:
     *   facebook_page_token  — Page Access Token (permanent, from Meta Developer Portal)
     *   facebook_page_id     — The numeric Page ID
     *
     * @param message  The announcement text
     * @return PostResult
     */
    public static PostResult postToFacebook(String message) {
        try {
            String pageToken = getSetting("facebook_page_token");
            String pageId    = getSetting("facebook_page_id");

            if (isEmpty(pageToken)) return PostResult.fail("Facebook Page Access Token not configured. Go to Settings → Announcements.");
            if (isEmpty(pageId))    return PostResult.fail("Facebook Page ID not configured. Go to Settings → Announcements.");

            String url  = "https://graph.facebook.com/v19.0/" + pageId + "/feed";
            String body = "message=" + URLEncoder.encode(message, StandardCharsets.UTF_8)
                + "&access_token=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String postId = extractJson(response.body(), "id");
                return PostResult.ok(postId);
            } else {
                String error = extractJson(response.body(), "message");
                return PostResult.fail("Facebook API error " + response.statusCode()
                    + (error != null ? ": " + error : ": " + response.body()));
            }

        } catch (Exception e) {
            return PostResult.fail("Facebook error: " + e.getMessage());
        }
    }

    /**
     * Posts an announcement to Facebook with an image attachment.
     * Uses the /photos endpoint which embeds the image in the post.
     */
    public static PostResult postToFacebookWithImage(String message, File imageFile) {
        try {
            String pageToken = getSetting("facebook_page_token");
            String pageId    = getSetting("facebook_page_id");

            if (isEmpty(pageToken) || isEmpty(pageId)) {
                return PostResult.fail("Facebook credentials not configured. Go to Settings → Announcements.");
            }

            // Upload photo with caption via /photos endpoint
            String uploadUrl = "https://graph.facebook.com/v19.0/" + pageId + "/photos";
            String boundary  = "----FormBoundary" + System.currentTimeMillis();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writeMultipartField(baos, boundary, "caption", message);
            writeMultipartField(baos, boundary, "access_token", pageToken);

            // Image file
            baos.write(("--" + boundary + "\r\n").getBytes());
            baos.write(("Content-Disposition: form-data; name=\"source\"; filename=\""
                + imageFile.getName() + "\"\r\n").getBytes());
            baos.write(("Content-Type: " + getMimeType(imageFile.getName()) + "\r\n\r\n").getBytes());
            baos.write(Files.readAllBytes(imageFile.toPath()));
            baos.write("\r\n".getBytes());
            baos.write(("--" + boundary + "--\r\n").getBytes());

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String postId = extractJson(response.body(), "post_id");
                if (postId == null) postId = extractJson(response.body(), "id");
                return PostResult.ok(postId);
            } else {
                String error = extractJson(response.body(), "message");
                return PostResult.fail("Facebook photo post error " + response.statusCode()
                    + (error != null ? ": " + error : ""));
            }

        } catch (Exception e) {
            return PostResult.fail("Facebook image post error: " + e.getMessage());
        }
    }

    /**
     * Posts an announcement to Facebook with a document (PDF/DOCX).
     * Facebook doesn't support document attachments natively — we post the text
     * and note the document is available. The file can be stored locally for reference.
     */
    public static PostResult postToFacebookWithDocument(String message, File docFile) {
        // Facebook Graph API does not support uploading PDFs/DOCX as page posts.
        // We post the message text only, with a note about the document.
        String enrichedMessage = message + "\n\n📎 Attachment: " + docFile.getName()
            + "\n(Document available from the church administrator)";
        return postToFacebook(enrichedMessage);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SETTINGS HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private static String getSetting(String key) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT setting_value FROM system_settings WHERE setting_key = ?");
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MIME / MEDIA TYPE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private static String getMimeType(String filename) {
        String f = filename.toLowerCase();
        if (f.endsWith(".pdf"))  return "application/pdf";
        if (f.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (f.endsWith(".doc"))  return "application/msword";
        if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return "image/jpeg";
        if (f.endsWith(".png"))  return "image/png";
        if (f.endsWith(".mp4"))  return "video/mp4";
        if (f.endsWith(".mov"))  return "video/quicktime";
        if (f.endsWith(".avi"))  return "video/avi";
        return "application/octet-stream";
    }

    /** WhatsApp Cloud API type string for the message payload */
    private static String getWhatsAppTypeKey(String filename) {
        String f = filename.toLowerCase();
        if (f.endsWith(".jpg") || f.endsWith(".jpeg") || f.endsWith(".png")) return "image";
        if (f.endsWith(".mp4") || f.endsWith(".mov") || f.endsWith(".avi"))  return "video";
        return "document"; // PDF, DOCX, etc.
    }

    private static String getWhatsAppMediaType(String filename) {
        return getMimeType(filename);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MULTIPART / JSON HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private static void writeMultipartField(ByteArrayOutputStream out,
                                             String boundary,
                                             String name,
                                             String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes());
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes());
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes());
    }

    /** Very lightweight JSON value extractor — avoids needing Gson for simple cases */
    private static String extractJson(String json, String key) {
        if (json == null) return null;
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        // Skip whitespace
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n')) start++;
        if (start >= json.length()) return null;
        if (json.charAt(start) == '"') {
            // String value
            int end = json.indexOf('"', start + 1);
            return end > start ? json.substring(start + 1, end) : null;
        } else {
            // Number or other
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(start, end).trim();
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
