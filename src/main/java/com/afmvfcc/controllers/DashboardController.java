package com.afmvfcc.controllers;

import com.afmvfcc.db.DatabaseConnection;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DashboardController {

    @FXML private Label statTotal;
    @FXML private Label statActive;
    @FXML private Label statPartTime;
    @FXML private Label statLastAttendance;
    @FXML private Label statYouth;
    @FXML private Label statSundaySchool;
    @FXML private Label statWomen;
    @FXML private Label statMen;

    @FXML private VBox eventsRemindersBox;
    @FXML private VBox welfareRemindersBox;
    @FXML private VBox pendingBox;
    @FXML private Label pendingBadge;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @FXML
    public void initialize() {
        loadStats();
        loadUpcomingEvents();
        loadWelfareDue();
        loadPendingMembers();
    }

    // ── STATS ──────────────────────────────────────────────────

    private void loadStats() {
        try {
            Connection conn = DatabaseConnection.getConnection();

            statTotal.setText(       String.valueOf(count(conn, "SELECT COUNT(*) FROM members WHERE is_deleted=0 AND is_deceased=0")));
            statActive.setText(      String.valueOf(count(conn, "SELECT COUNT(*) FROM members WHERE is_deleted=0 AND is_full_time=1 AND is_active=1 AND is_deceased=0")));
            statPartTime.setText(    String.valueOf(count(conn, "SELECT COUNT(*) FROM members WHERE is_deleted=0 AND is_full_time=0 AND is_deceased=0")));
            statYouth.setText(       String.valueOf(countMinistry(conn, "Youth")));
            statSundaySchool.setText(String.valueOf(countMinistry(conn, "Sunday School")));
            statWomen.setText(       String.valueOf(countMinistry(conn, "Women's Ministry")));
            statMen.setText(         String.valueOf(countMinistry(conn, "Men's Ministry")));
            statLastAttendance.setText(String.valueOf(lastWeekAttendance(conn)));

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private int count(Connection conn, String sql) throws SQLException {
        ResultSet rs = conn.createStatement().executeQuery(sql);
        return rs.next() ? rs.getInt(1) : 0;
    }

    private int countMinistry(Connection conn, String ministryName) throws SQLException {
        String sql = """
            SELECT COUNT(DISTINCT mm.member_id)
            FROM member_ministries mm
            JOIN ministries m ON m.id = mm.ministry_id
            JOIN members mb ON mb.id = mm.member_id
            WHERE m.name = ? AND mb.is_deleted = 0
            """;
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, ministryName);
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getInt(1) : 0;
    }

    private int lastWeekAttendance(Connection conn) throws SQLException {
        LocalDate today    = LocalDate.now();
        LocalDate weekAgo  = today.minusDays(7);
        String sql = """
            SELECT COUNT(*) FROM attendance_records ar
            JOIN attendance_sessions ase ON ase.id = ar.session_id
            WHERE ar.is_present = 1
            AND ase.session_date BETWEEN ? AND ?
            """;
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setDate(1, Date.valueOf(weekAgo));
        ps.setDate(2, Date.valueOf(today));
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getInt(1) : 0;
    }

    // ── UPCOMING EVENTS ────────────────────────────────────────

    private void loadUpcomingEvents() {
        eventsRemindersBox.getChildren().clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = """
                SELECT title, event_date, event_time, location
                FROM events
                WHERE event_date BETWEEN ? AND ?
                ORDER BY event_date ASC
                LIMIT 6
                """;
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setDate(1, Date.valueOf(LocalDate.now()));
            ps.setDate(2, Date.valueOf(LocalDate.now().plusDays(7)));
            ResultSet rs = ps.executeQuery();

            boolean any = false;
            while (rs.next()) {
                any = true;
                VBox item = new VBox(2);
                Label title = new Label(rs.getString("title"));
                title.getStyleClass().add("reminder-title");
                Label date = new Label(
                    rs.getDate("event_date").toLocalDate().format(FMT) +
                    (rs.getTime("event_time") != null
                        ? "  ·  " + rs.getTime("event_time").toString().substring(0,5) : "") +
                    (rs.getString("location") != null
                        ? "  ·  " + rs.getString("location") : "")
                );
                date.getStyleClass().add("reminder-item-date");
                item.getChildren().addAll(title, date);
                eventsRemindersBox.getChildren().add(item);
            }

            if (!any) {
                eventsRemindersBox.getChildren().add(
                    makeEmpty("No upcoming events in the next 7 days.")
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ── WELFARE DUE ────────────────────────────────────────────

    private void loadWelfareDue() {
        welfareRemindersBox.getChildren().clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = """
                SELECT wc.id, m.full_name, wc.status, wc.opened_at,
                       ww_member.full_name AS worker_name
                FROM welfare_cases wc
                JOIN members m ON m.id = wc.member_id
                LEFT JOIN welfare_workers ww ON ww.id = wc.assigned_worker_id
                LEFT JOIN members ww_member ON ww_member.id = ww.member_id
                WHERE wc.status IN ('Pending','In Progress')
                ORDER BY wc.opened_at ASC
                LIMIT 6
                """;
            ResultSet rs = conn.createStatement().executeQuery(sql);

            boolean any = false;
            while (rs.next()) {
                any = true;
                VBox item = new VBox(2);
                Label name = new Label(rs.getString("full_name"));
                name.getStyleClass().add("reminder-title");
                String worker = rs.getString("worker_name");
                Label sub = new Label(
                    rs.getString("status") +
                    (worker != null ? "  ·  " + worker : "  ·  Unassigned")
                );
                sub.getStyleClass().add("reminder-item-date");
                item.getChildren().addAll(name, sub);
                welfareRemindersBox.getChildren().add(item);
            }

            if (!any) {
                welfareRemindersBox.getChildren().add(
                    makeEmpty("No pending welfare cases.")
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ── PENDING MEMBERS ────────────────────────────────────────

    private void loadPendingMembers() {
        pendingBox.getChildren().clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = """
                SELECT pm.full_name, pm.phone, sb.name AS branch,
                       pm.submitted_at
                FROM pending_members pm
                LEFT JOIN sub_branches sb ON sb.id = pm.sub_branch_id
                WHERE pm.status = 'Pending'
                ORDER BY pm.submitted_at ASC
                LIMIT 5
                """;
            ResultSet rs = conn.createStatement().executeQuery(sql);

            int count = 0;
            while (rs.next()) {
                count++;
                VBox item = new VBox(2);
                Label name = new Label(rs.getString("full_name"));
                name.getStyleClass().add("reminder-title");
                String branch = rs.getString("branch");
                Label sub = new Label(
                    (branch != null ? branch : "Unknown branch") +
                    "  ·  " + rs.getString("phone")
                );
                sub.getStyleClass().add("reminder-item-date");
                item.getChildren().addAll(name, sub);
                pendingBox.getChildren().add(item);
            }

            pendingBadge.setText(String.valueOf(count));

            if (count == 0) {
                pendingBox.getChildren().add(makeEmpty("No pending approvals."));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ── Helper ─────────────────────────────────────────────────

    private Label makeEmpty(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("reminder-item");
        return l;
    }
}
