package com.afmvfcc.utils;

import com.afmvfcc.db.DatabaseConnection;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.sql.*;
import java.util.Properties;

public class EmailService {

    /**
     * Sends an email using SMTP settings stored in system_settings table.
     * Returns null on success, or an error message string on failure.
     */
    public static String send(String toEmail, String subject, String body) {
        try {
            // Load settings from DB
            String host     = getSetting("smtp_host");
            String portStr  = getSetting("smtp_port");
            String user     = getSetting("smtp_user");
            String password = getSetting("smtp_password");

            if (host == null || host.isEmpty()) return "SMTP host not configured.";
            if (user == null || user.isEmpty()) return "SMTP username not configured.";
            if (password == null || password.isEmpty()) return "SMTP password not configured.";

            int port = 587;
            try { port = Integer.parseInt(portStr); } catch (Exception ignored) {}

            final String finalUser = user;
            final String finalPass = password;

            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", String.valueOf(port));
            props.put("mail.smtp.ssl.trust", host);
            props.put("mail.smtp.connectiontimeout", "8000");
            props.put("mail.smtp.timeout", "8000");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(finalUser, finalPass);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(user));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);
            message.setText(body);

            Transport.send(message);
            return null; // success

        } catch (AuthenticationFailedException e) {
            return "Email authentication failed. Check your username/password.";
        } catch (MessagingException e) {
            return "Email send failed: " + e.getMessage();
        } catch (Exception e) {
            return "Email error: " + e.getMessage();
        }
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
