package com.afmvfcc.controllers;

import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.utils.NavigationBus;
import com.afmvfcc.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DashboardController {

    @FXML private Label greetingTitle;
    @FXML private Label greetingSubtitle;

    @FXML private Label statTotal;
    @FXML private Label statActive;
    @FXML private Label statPartTime;
    @FXML private Label statLastAttendance;
    @FXML private Label attendanceTrendBadge;

    @FXML private BarChart<String, Number> ministryChart;
    @FXML private LineChart<String, Number> attendanceChart;
    @FXML private PieChart welfareStatusChart;

    @FXML private VBox eventsRemindersBox;
    @FXML private VBox birthdaysBox;
    @FXML private VBox welfareRemindersBox;
    @FXML private VBox pendingBox;
    @FXML private Label pendingBadge;
    @FXML private VBox recentActivityBox;

    private static final DateTimeFormatter FMT       = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter SUBTITLE_FMT = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy");
    private static final DateTimeFormatter WEEK_LABEL_FMT = DateTimeFormatter.ofPattern("dd MMM");

    @FXML
    public void initialize() {
        loadGreeting();
        loadStats();
        loadAttendanceTrend();
        loadWelfareStatusChart();
        loadRecentActivity();
        loadUpcomingEvents();
        loadBirthdays();
        loadWelfareDue();
        loadPendingMembers();
    }

    // ── INTERACTIVITY — clickable stat cards / charts ───────────

    @FXML public void handleStatMembersClick()    { NavigationBus.goTo("btnMembers"); }
    @FXML public void handleStatAttendanceClick()  { NavigationBus.goTo("btnAttendance"); }
    @FXML public void handleWelfareChartClick()    { NavigationBus.goTo("btnWelfare"); }

    // ── GREETING ───────────────────────────────────────────────

    private void loadGreeting() {
        int hour = LocalDateTime.now().getHour();
        String period = hour < 12 ? "morning" : (hour < 17 ? "afternoon" : "evening");

        String firstName = "";
        try {
            var user = SessionManager.getInstance().getCurrentUser();
            if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
                firstName = user.getFullName().split(" ")[0];
            }
        } catch (Exception ignored) {}

        greetingTitle.setText("Good " + period + (firstName.isEmpty() ? "" : ", " + firstName));
        greetingSubtitle.setText(LocalDate.now().format(SUBTITLE_FMT));
    }

    // ── STATS ──────────────────────────────────────────────────

    private void loadStats() {
        try {
            Connection conn = DatabaseConnection.getConnection();

            statTotal.setText(   String.valueOf(count(conn, "SELECT COUNT(*) FROM members WHERE is_deleted=0 AND is_deceased=0")));
            statActive.setText(  String.valueOf(count(conn, "SELECT COUNT(*) FROM members WHERE is_deleted=0 AND is_full_time=1 AND is_active=1 AND is_deceased=0")));
            statPartTime.setText(String.valueOf(count(conn, "SELECT COUNT(*) FROM members WHERE is_deleted=0 AND is_full_time=0 AND is_deceased=0")));

            int youth        = countMinistry(conn, "Youth");
            int sundaySchool = countMinistry(conn, "Sunday School");
            int women        = countMinistry(conn, "Women's Ministry");
            int men          = countMinistry(conn, "Men's Ministry");
            loadMinistryChart(youth, sundaySchool, women, men);

            int thisWeek = weeklyAttendance(conn, 0);
            int lastWeek = weeklyAttendance(conn, 7);
            statLastAttendance.setText(String.valueOf(thisWeek));
            setTrendBadge(thisWeek, lastWeek);

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

    /** Attendance count for the 7-day window ending {@code daysAgo} days before today. */
    private int weeklyAttendance(Connection conn, int daysAgo) throws SQLException {
        LocalDate end   = LocalDate.now().minusDays(daysAgo);
        LocalDate start = end.minusDays(7);
        String sql = """
            SELECT COUNT(*) FROM attendance_records ar
            JOIN attendance_sessions ase ON ase.id = ar.session_id
            WHERE ar.is_present = 1
            AND ase.session_date BETWEEN ? AND ?
            """;
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setDate(1, Date.valueOf(start));
        ps.setDate(2, Date.valueOf(end));
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getInt(1) : 0;
    }

    private void setTrendBadge(int thisWeek, int lastWeek) {
        attendanceTrendBadge.setVisible(true);
        attendanceTrendBadge.setManaged(true);

        if (lastWeek == 0) {
            attendanceTrendBadge.setVisible(false);
            attendanceTrendBadge.setManaged(false);
            return;
        }

        double changePct = ((thisWeek - lastWeek) / (double) lastWeek) * 100.0;
        String rounded = String.valueOf(Math.round(Math.abs(changePct)));

        attendanceTrendBadge.getStyleClass().removeAll("badge-up", "badge-down", "badge-flat");
        if (changePct > 0.5) {
            attendanceTrendBadge.setText("▲ " + rounded + "%");
            attendanceTrendBadge.getStyleClass().add("badge-up");
        } else if (changePct < -0.5) {
            attendanceTrendBadge.setText("▼ " + rounded + "%");
            attendanceTrendBadge.getStyleClass().add("badge-down");
        } else {
            attendanceTrendBadge.setText("Flat");
            attendanceTrendBadge.getStyleClass().add("badge-flat");
        }
    }

    // ── MINISTRY CHART ────────────────────────────────────────

    private void loadMinistryChart(int youth, int sundaySchool, int women, int men) {
        ministryChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(ministryPoint("Youth", youth));
        series.getData().add(ministryPoint("Sunday School", sundaySchool));
        series.getData().add(ministryPoint("Women's", women));
        series.getData().add(ministryPoint("Men's", men));
        ministryChart.getData().add(series);
    }

    private XYChart.Data<String, Number> ministryPoint(String ministry, int count) {
        XYChart.Data<String, Number> d = new XYChart.Data<>(ministry, count);
        installTooltip(d.nodeProperty(), ministry + ": " + count + (count == 1 ? " member" : " members"));
        return d;
    }

    // ── ATTENDANCE TREND CHART ────────────────────────────────

    private void loadAttendanceTrend() {
        attendanceChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            // 6 weekly buckets, oldest first, each a 7-day window ending on
            // that week's marker date (today, today-7, today-14, ...).
            for (int weeksAgo = 5; weeksAgo >= 0; weeksAgo--) {
                LocalDate end = LocalDate.now().minusDays((long) weeksAgo * 7);
                int weekCount = weeklyAttendance(conn, weeksAgo * 7);
                XYChart.Data<String, Number> d = new XYChart.Data<>(end.format(WEEK_LABEL_FMT), weekCount);
                installTooltip(d.nodeProperty(),
                    "Week ending " + end.format(FMT) + ": " + weekCount +
                    (weekCount == 1 ? " person present" : " people present"));
                series.getData().add(d);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        attendanceChart.getData().add(series);
    }

    // ── WELFARE STATUS CHART ──────────────────────────────────

    // Fixed order — matches the .data0/.data1/.data2 CSS rules in styles.css
    // (.welfare-status-chart), which are what actually colour the pie slices
    // AND their legend swatches, so the two always stay in sync. Always
    // adding all three (even at 0) keeps that position -> colour mapping
    // stable no matter which statuses currently have cases.
    private static final String[] WELFARE_STATUSES = { "Pending", "In Progress", "Completed" };

    private void loadWelfareStatusChart() {
        welfareStatusChart.getData().clear();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String s : WELFARE_STATUSES) counts.put(s, 0);
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT status, COUNT(*) AS c FROM welfare_cases GROUP BY status");
            while (rs.next()) {
                counts.put(rs.getString("status"), rs.getInt("c"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (counts.values().stream().allMatch(c -> c == 0)) {
            welfareStatusChart.setData(javafx.collections.FXCollections.observableArrayList(
                new PieChart.Data("No cases yet", 1)));
            return;
        }

        for (String status : WELFARE_STATUSES) {
            int count = counts.get(status);
            PieChart.Data slice = new PieChart.Data(status + " (" + count + ")", count);
            welfareStatusChart.getData().add(slice);
            installTooltip(slice.nodeProperty(), status + ": " + count + (count == 1 ? " case" : " cases"));
        }
    }

    // ── RECENT ACTIVITY ────────────────────────────────────────

    private void loadRecentActivity() {
        recentActivityBox.getChildren().clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = """
                SELECT al.action, al.performed_at, u.full_name
                FROM audit_log al
                LEFT JOIN users u ON u.id = al.user_id
                ORDER BY al.performed_at DESC
                LIMIT 8
                """;
            ResultSet rs = conn.createStatement().executeQuery(sql);

            boolean any = false;
            while (rs.next()) {
                any = true;
                VBox item = new VBox(2);
                Label action = new Label(rs.getString("action"));
                action.getStyleClass().add("reminder-title");
                String who = rs.getString("full_name") != null ? rs.getString("full_name") : "System";
                Timestamp ts = rs.getTimestamp("performed_at");
                Label sub = new Label(who + "  ·  " + relativeTime(ts));
                sub.getStyleClass().add("reminder-item-date");
                item.getChildren().addAll(action, sub);
                recentActivityBox.getChildren().add(item);
            }

            if (!any) {
                recentActivityBox.getChildren().add(makeEmpty("No recent activity."));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String relativeTime(Timestamp ts) {
        if (ts == null) return "";
        Duration d = Duration.between(ts.toLocalDateTime(), LocalDateTime.now());
        long minutes = d.toMinutes();
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + " min ago";
        long hours = d.toHours();
        if (hours < 24) return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        long days = d.toDays();
        if (days < 7) return days + " day" + (days == 1 ? "" : "s") + " ago";
        return ts.toLocalDateTime().format(FMT);
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

    // ── BIRTHDAYS THIS WEEK ────────────────────────────────────

    private void loadBirthdays() {
        birthdaysBox.getChildren().clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = """
                SELECT full_name, date_of_birth
                FROM members
                WHERE date_of_birth IS NOT NULL
                AND is_deleted = 0 AND is_deceased = 0
                """;
            ResultSet rs = conn.createStatement().executeQuery(sql);

            LocalDate today = LocalDate.now();
            List<Object[]> upcoming = new ArrayList<>(); // {name, daysUntil, MonthDay}

            while (rs.next()) {
                Date dob = rs.getDate("date_of_birth");
                if (dob == null) continue;
                MonthDay birthdayMd = MonthDay.from(dob.toLocalDate());

                LocalDate nextOccurrence = birthdayMd.atYear(today.getYear());
                if (nextOccurrence.isBefore(today)) {
                    nextOccurrence = birthdayMd.atYear(today.getYear() + 1);
                }
                long daysUntil = nextOccurrence.toEpochDay() - today.toEpochDay();

                if (daysUntil >= 0 && daysUntil <= 7) {
                    upcoming.add(new Object[]{ rs.getString("full_name"), daysUntil, birthdayMd });
                }
            }

            upcoming.sort(Comparator.comparingLong(o -> (long) o[1]));

            int shown = 0;
            for (Object[] row : upcoming) {
                if (shown >= 6) break;
                shown++;
                String name = (String) row[0];
                long daysUntil = (long) row[1];
                MonthDay md = (MonthDay) row[2];

                VBox item = new VBox(2);
                Label nameLabel = new Label(name);
                nameLabel.getStyleClass().add("reminder-title");
                String when = daysUntil == 0 ? "Today"
                            : daysUntil == 1 ? "Tomorrow"
                            : "In " + daysUntil + " days";
                Label sub = new Label(md.format(DateTimeFormatter.ofPattern("dd MMM")) + "  ·  " + when);
                sub.getStyleClass().add("reminder-item-date");
                item.getChildren().addAll(nameLabel, sub);
                birthdaysBox.getChildren().add(item);
            }

            if (shown == 0) {
                birthdaysBox.getChildren().add(makeEmpty("No birthdays in the next 7 days."));
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

    /**
     * Attaches a hover Tooltip to a chart data point (bar, line-chart symbol,
     * or pie slice). The underlying Node doesn't exist until the chart has
     * laid the data out, so this waits on the nodeProperty rather than
     * requiring the caller to know when that happens.
     */
    private void installTooltip(javafx.beans.value.ObservableValue<javafx.scene.Node> nodeProperty, String text) {
        Tooltip tooltip = new Tooltip(text);
        javafx.scene.Node existing = nodeProperty.getValue();
        if (existing != null) {
            Tooltip.install(existing, tooltip);
        } else {
            nodeProperty.addListener((obs, oldNode, newNode) -> {
                if (newNode != null) Tooltip.install(newNode, tooltip);
            });
        }
    }
}
