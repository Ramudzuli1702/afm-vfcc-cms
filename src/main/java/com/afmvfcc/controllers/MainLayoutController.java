package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.models.User;
import com.afmvfcc.utils.GitHubSync;
import com.afmvfcc.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.util.HashMap;
import java.util.Map;

public class MainLayoutController {

    @FXML private StackPane contentPane;
    @FXML private Label     topbarTitle;
    @FXML private Label     topbarSub;
    @FXML private Label     sidebarUserName;
    @FXML private Label     sidebarUserRole;

    @FXML private Button btnDashboard;
    @FXML private Button btnMembers;
    @FXML private Button btnAttendance;
    @FXML private Button btnBoard;
    @FXML private Button btnCalendar;
    @FXML private Button btnWelfare;
    @FXML private Button btnFinance;
    @FXML private Button btnComms;
    @FXML private Button btnWebsite;
    @FXML private Button btnAdmins;
    @FXML private Button btnAuditLog;
    @FXML private Button btnSettings;
    @FXML private Button btnCommems;
    @FXML private Button btnViewWebsite;
    @FXML private Button btnBroadcast;

    private Button activeBtn;

    private final Map<String, String[]> pageMap = new HashMap<>();

    @FXML
    public void initialize() {

        pageMap.put("btnDashboard",  new String[]{"dashboard.fxml",       "Dashboard",       "Overview & reminders"});
        pageMap.put("btnMembers",    new String[]{"members.fxml",         "Members",         "Manage church members"});
        pageMap.put("btnAttendance", new String[]{"attendance.fxml",      "Attendance",      "Track service attendance"});
        pageMap.put("btnBoard",      new String[]{"board.fxml",           "Church Board",    "Board members & meetings"});
        pageMap.put("btnCalendar",   new String[]{"calendar.fxml",        "Calendar",        "Upcoming church events"});
        pageMap.put("btnWelfare",    new String[]{"welfare.fxml",         "Welfare",         "Member welfare cases"});
        pageMap.put("btnFinance",    new String[]{"finance.fxml",         "Finance",         "Financial management"});
        pageMap.put("btnComms",      new String[]{"communications.fxml",  "Communications",  "Email & SMS"});
        pageMap.put("btnWebsite",    new String[]{"website.fxml",         "Website",         "Manage blogs & events"});
        pageMap.put("btnAdmins",     new String[]{"admins.fxml",          "Admins",          "Manage system users"});
        pageMap.put("btnAuditLog",   new String[]{"audit_log.fxml",       "Audit Log",       "System activity trail"});
        pageMap.put("btnSettings",   new String[]{"settings.fxml",        "Settings",        "Backup, email & SMS"});
        pageMap.put("btnCommems",    new String[]{"commemorations.fxml",  "Commemorations",  "Issue certificates"});
        pageMap.put("btnBroadcast",  new String[]{"broadcast.fxml",       "Broadcast",       "Facebook → YouTube"});

        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null) {
            sidebarUserName.setText(user.getFullName());
            sidebarUserRole.setText(user.getRoleTitle() != null ? user.getRoleTitle() : "Admin");
            topbarSub.setText("Welcome back, " + user.getFullName().split(" ")[0]);
        }

        setNavIcons();
        loadPage(btnDashboard);
    }

    @FXML
    public void navigate(javafx.event.ActionEvent e) {
        Button clicked = (Button) e.getSource();
        if (clicked == activeBtn) return;
        loadPage(clicked);
    }

    @FXML
    public void handleLogout() {
        if (!BroadcastController.confirmExitAllowed()) return;
        SessionManager.getInstance().logout();
        Main.showLogin();
    }

    private void loadPage(Button btn) {
        String btnId = btn.getId();
        String[] info = pageMap.get(btnId);
        if (info == null) return;

        try {
            Node page = FXMLLoader.load(
                getClass().getResource("/com/afmvfcc/fxml/" + info[0])
            );
            contentPane.getChildren().setAll(page);
            topbarTitle.setText(info[1]);
            topbarSub.setText(info[2]);

            if (activeBtn != null) activeBtn.getStyleClass().remove("nav-btn-active");
            btn.getStyleClass().add("nav-btn-active");
            activeBtn = btn;

            boolean isWebsite = "btnWebsite".equals(btnId);
            btnViewWebsite.setVisible(isWebsite);
            btnViewWebsite.setManaged(isWebsite);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void handleViewWebsite() {
        // Build URL from settings — never touches local files
        String url = buildWebsiteUrl();

        if (url == null) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            Main.applyStyles(a.getDialogPane());
            a.setTitle("View Website");
            a.setHeaderText("GitHub not configured");
            a.setContentText(
                "No GitHub repository is configured yet.\n\n" +
                "Go to Settings → GitHub Integration, enter your:\n" +
                "  • Personal Access Token\n" +
                "  • Repository Owner (e.g. Ramudzuli1702)\n" +
                "  • Repository Name (e.g. afm_vfcc)\n\n" +
                "Then click Save All Settings and try again."
            );
            a.showAndWait();
            return;
        }

        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception e) {
            // Fallback for systems where Desktop.browse may not work
            try {
                new ProcessBuilder("cmd", "/c", "start", url).start();
            } catch (Exception ex) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                Main.applyStyles(a.getDialogPane());
                a.setTitle("View Website");
                a.setHeaderText("Could not open browser");
                a.setContentText("Please open this URL manually:\n\n" + url);
                a.showAndWait();
            }
        }
    }

    /**
     * Builds the public website URL from database settings.
     * Priority: custom domain → GitHub Pages URL → null
     */
    private String buildWebsiteUrl() {
        // 1. Custom domain (e.g. www.afmvfcc.co.za) — set when you get a real domain
        String custom = GitHubSync.loadSetting("website_custom_domain");
        if (custom != null && !custom.isEmpty()) {
            return custom.startsWith("http") ? custom : "https://" + custom;
        }
        // 2. GitHub Pages URL: https://<owner>.github.io/<repo>/
        String owner = GitHubSync.loadSetting("github_owner");
        String repo  = GitHubSync.loadSetting("github_repo");
        if (owner != null && !owner.isEmpty() && repo != null && !repo.isEmpty()) {
            return "https://" + owner.toLowerCase() + ".github.io/" + repo + "/";
        }
        return null;
    }

    private void setNavIcons() {
        btnDashboard.setText("🏠  Dashboard");
        btnMembers.setText("👥  Members");
        btnAttendance.setText("✅  Attendance");
        btnBoard.setText("🏛  Church Board");
        btnCalendar.setText("📅  Calendar");
        btnWelfare.setText("❤  Welfare");
        btnFinance.setText("💰  Finance");
        btnComms.setText("✉  Communications");
        btnWebsite.setText("🌐  Website");
        btnBroadcast.setText("📡  Broadcast");
        btnAdmins.setText("⚙  Admins");
        btnAuditLog.setText("📋  Audit Log");
        btnSettings.setText("🔧  Settings");
        btnCommems.setText("🎖  Commemorations");
    }
}
