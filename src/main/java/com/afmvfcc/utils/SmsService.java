package com.afmvfcc.utils;

import com.afmvfcc.db.DatabaseConnection;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.Base64;

public class SmsService {

    private static final String BULKSMS_API = "https://api.bulksms.com/v1/messages";

    /**
     * Sends an SMS via BulkSMS API.
     * Returns null on success, or an error message string on failure.
     */
    public static String send(String toPhone, String body) {
        try {
            String apiKey    = getSetting("bulksms_key");
            String apiSecret = getSetting("bulksms_secret");
            String sender    = getSetting("bulksms_sender");

            if (apiKey == null || apiKey.isEmpty()) return "BulkSMS API key not configured.";

            // Clean phone number — ensure it starts with country code
            String phone = cleanPhone(toPhone);
            if (phone == null) return "Invalid phone number: " + toPhone;

            // Build JSON payload
            JsonObject payload = new JsonObject();
            payload.addProperty("body", body);

            JsonArray to = new JsonArray();
            JsonObject recipient = new JsonObject();
            recipient.addProperty("address", phone);
            to.add(recipient);
            payload.add("to", to);

            if (sender != null && !sender.isEmpty()) {
                payload.addProperty("from", sender);
            }

            // Auth: BulkSMS uses token ID + token secret as Basic auth
            // If only apiKey is set (older format), use it as both user:pass
            String authString;
            if (apiSecret != null && !apiSecret.isEmpty()) {
                authString = apiKey + ":" + apiSecret;
            } else {
                authString = apiKey + ":";
            }
            String encoded = Base64.getEncoder()
                .encodeToString(authString.getBytes(StandardCharsets.UTF_8));

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BULKSMS_API))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + encoded)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofSeconds(15))
                .build();

            HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status == 200 || status == 201) {
                return null; // success
            } else if (status == 401) {
                return "SMS authentication failed. Check your BulkSMS API key.";
            } else if (status == 402) {
                return "Insufficient BulkSMS credits.";
            } else {
                return "SMS send failed (HTTP " + status + "): " + response.body();
            }

        } catch (java.net.ConnectException e) {
            return "Cannot reach BulkSMS API. Check your internet connection.";
        } catch (Exception e) {
            return "SMS error: " + e.getMessage();
        }
    }

    /**
     * Normalise phone to international format for South Africa.
     * 0712345678 → +27712345678
     */
    private static String cleanPhone(String phone) {
        if (phone == null) return null;
        phone = phone.replaceAll("[\\s\\-()]", "").trim();
        if (phone.isEmpty()) return null;

        if (phone.startsWith("+")) return phone;          // already international
        if (phone.startsWith("27")) return "+" + phone;   // 27...
        if (phone.startsWith("0")) return "+27" + phone.substring(1); // 0... → +27...
        return "+" + phone;
    }

    private static String getSetting(String key) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT setting_value FROM system_settings WHERE setting_key = ?"
            );
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            return null;
        }
    }
}
