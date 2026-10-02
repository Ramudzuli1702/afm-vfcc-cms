package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.models.WelfareCase;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.Avatars;
import com.afmvfcc.utils.Dialogs;
import com.afmvfcc.utils.DocumentViewer;
import com.afmvfcc.utils.FormBuilder;
import com.afmvfcc.utils.Icons;
import com.afmvfcc.utils.SessionManager;
import com.afmvfcc.utils.TableActions;
import com.afmvfcc.utils.ToastManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.*;
import java.time.format.DateTimeFormatter;

public class WelfareController {

    // ── Welfare Cases ──────────────────────────────────────────
    @FXML private TableView<WelfareCase>            welfareTable;
    @FXML private TableColumn<WelfareCase, String>  colWMember, colWReason, colWWorker,
                                                     colWStatus, colWOpened;
    @FXML private TableColumn<WelfareCase, Void>    colWActions;
    @FXML private TextField                         welfareSearchField;
    @FXML private ComboBox<String>                  statusFilter;

    // ── Welfare Agents ─────────────────────────────────────────
    @FXML private TableView<String[]>            agentsTable;
    @FXML private TableColumn<String[], String>  colAgentName, colAgentPhone, colAgentCases;
    @FXML private TableColumn<String[], Void>    colAgentActions;
    @FXML private TextField                      agentSearchField;

    // ── Stat cards ─────────────────────────────────────────────
    @FXML private Label statPending, statInProgress, statCompleted, statAgents;

    private final ObservableList<WelfareCase> allCases  = FXCollections.observableArrayList();
    private final ObservableList<String[]>    allAgents = FXCollections.observableArrayList();
    private final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @FXML
    public void initialize() {
        setupTable();
        if (agentsTable != null) setupAgentsTable();

        statusFilter.getItems().addAll("All", "Pending", "In Progress", "Completed");
        statusFilter.setValue("All");
        loadCases();
        if (agentsTable != null) loadAgents();
    }

    // ══════════════════════════════════════════════════════════
    // WELFARE CASES
    // ══════════════════════════════════════════════════════════

    private void setupTable() {
        colWMember.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getMemberName()));
        Avatars.nameColumn(colWMember, WelfareCase::getMemberPhotoPath);

        colWReason.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getReason()));

        colWWorker.setCellValueFactory(d -> {
            String w = d.getValue().getAssignedWorkerName();
            return new SimpleStringProperty(w != null ? w : "Unassigned");
        });

        colWStatus.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getStatus()));

        colWStatus.setCellFactory(col -> new TableCell<WelfareCase, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label(item);
                String sc;
                switch (item) {
                    case "Completed":   sc = "badge-completed";  break;
                    case "In Progress": sc = "badge-inprogress"; break;
                    default:            sc = "badge-pending";    break;
                }
                badge.getStyleClass().add(sc);
                setGraphic(badge);
                setText(null);
            }
        });

        colWOpened.setCellValueFactory(d -> {
            String val = d.getValue().getOpenedAt() != null
                ? d.getValue().getOpenedAt().toLocalDate().format(FMT) : "-";
            return new SimpleStringProperty(val);
        });

        TableActions.viewOnly(welfareTable, colWActions, this::openCase);

        welfareTable.setItems(allCases);
    }

    private static boolean isCompleted(WelfareCase wc) { return "Completed".equals(wc.getStatus()); }

    private void refreshStats() {
        statPending.setText(String.valueOf(allCases.stream().filter(c -> "Pending".equals(c.getStatus())).count()));
        statInProgress.setText(String.valueOf(allCases.stream().filter(c -> "In Progress".equals(c.getStatus())).count()));
        statCompleted.setText(String.valueOf(allCases.stream().filter(WelfareController::isCompleted).count()));
        statAgents.setText(String.valueOf(allAgents.size()));
    }

    private void openCase(WelfareCase wc) {
        if (wc == null) return;
        DateTimeFormatter stamp = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
        DocumentViewer v = new DocumentViewer("Welfare Case", wc.getMemberName())
            .icon("fas-hand-holding-heart")
            .avatar(Avatars.of(wc.getMemberPhotoPath(), 64))
            .status(wc.getStatus(), switch (wc.getStatus()) {
                case "Completed"   -> "badge-completed";
                case "In Progress" -> "badge-inprogress";
                default            -> "badge-pending";
            })
            .width(720)
            .meta("Member",         wc.getMemberName())
            .meta("Assigned Agent", wc.getAssignedWorkerName())
            .meta("Opened",         wc.getOpenedAt()    != null ? wc.getOpenedAt().format(stamp)    : null)
            .meta("Completed",      wc.getCompletedAt() != null ? wc.getCompletedAt().format(stamp) : null)
            .section("Reason for Visit", wc.getReason(), "No reason recorded.")
            .section("Agent Report",     wc.getReport(), "No report was submitted for this case.")
            .action("Print", "fas-print", () -> WelfarePdfExporter.export(java.util.List.of(wc)));
        if (!isCompleted(wc))
            v.closingAction("Mark Completed", "fas-check", "btn-secondary", () -> closeCase(wc));
        v.onEdit(() -> openCaseDialog(wc))
         .onDelete(() -> deleteCase(wc))
         .show();
    }

    /** Confirms, then deletes. Returns true if the case was removed. */
    private boolean deleteCase(WelfareCase wc) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Delete Welfare Case");
        confirm.setHeaderText("Delete the welfare case for " + wc.getMemberName() + "?");
        confirm.setContentText("The reason and agent report are deleted permanently. This cannot be undone.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        try {
            PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement("DELETE FROM welfare_cases WHERE id=?");
            ps.setInt(1, wc.getId());
            ps.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                "Deleted welfare case for: " + wc.getMemberName());
            loadCases();
            loadAgents(); // case counts per agent change
            ToastManager.success("Welfare case deleted.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to delete welfare case: " + e.getMessage());
            return false;
        }
    }

    private void loadCases() {
        allCases.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT wc.*, m.full_name AS member_name, m.photo_path AS member_photo, " +
                         "wm.full_name AS worker_name " +
                         "FROM welfare_cases wc " +
                         "JOIN members m ON m.id = wc.member_id " +
                         "LEFT JOIN welfare_workers ww ON ww.id = wc.assigned_worker_id " +
                         "LEFT JOIN members wm ON wm.id = ww.member_id " +
                         "ORDER BY wc.opened_at DESC";
            ResultSet rs = conn.createStatement().executeQuery(sql);
            while (rs.next()) {
                WelfareCase wc = new WelfareCase();
                wc.setId(rs.getInt("id"));
                wc.setMemberId(rs.getInt("member_id"));
                wc.setMemberName(rs.getString("member_name"));
                wc.setMemberPhotoPath(rs.getString("member_photo"));
                wc.setReason(rs.getString("reason"));
                wc.setAssignedWorkerId(rs.getInt("assigned_worker_id"));
                wc.setAssignedWorkerName(rs.getString("worker_name"));
                wc.setReport(rs.getString("report"));
                wc.setStatus(rs.getString("status"));
                if (rs.getTimestamp("opened_at") != null)
                    wc.setOpenedAt(rs.getTimestamp("opened_at").toLocalDateTime());
                if (rs.getTimestamp("completed_at") != null)
                    wc.setCompletedAt(rs.getTimestamp("completed_at").toLocalDateTime());
                allCases.add(wc);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        applyFilter();
        refreshStats();
    }

    @FXML public void handleSearch() { applyFilter(); }
    @FXML public void handleFilter() { applyFilter(); }

    private void applyFilter() {
        String q      = welfareSearchField.getText().toLowerCase();
        String status = statusFilter.getValue();
        ObservableList<WelfareCase> filtered = FXCollections.observableArrayList();
        for (WelfareCase wc : allCases) {
            if (!q.isEmpty()) {
                boolean mn = wc.getMemberName() != null && wc.getMemberName().toLowerCase().contains(q);
                boolean mr = wc.getReason()     != null && wc.getReason().toLowerCase().contains(q);
                if (!mn && !mr) continue;
            }
            if (status != null && !"All".equals(status) && !status.equals(wc.getStatus())) continue;
            filtered.add(wc);
        }
        welfareTable.setItems(filtered);
    }

    @FXML public void handleAddCase()   { openCaseDialog(null); }
    @FXML public void handleExportPdf() { WelfarePdfExporter.export(allCases); }

    private void openCaseDialog(WelfareCase existing) {
        FormBuilder f = new FormBuilder(existing == null ? "New Welfare Case" : "Edit Welfare Case").icon("fas-hand-holding-heart");

        ComboBox<String> memberCombo = FormBuilder.combo("Search and select member...");
        memberCombo.setEditable(true);
        loadMembersInto(memberCombo);
        ComboBox<String> workerCombo = FormBuilder.combo("Assign welfare agent...");
        loadWorkersInto(workerCombo);
        TextArea reasonArea = FormBuilder.area("Why does this member need a welfare visit?", 80);
        TextArea reportArea = FormBuilder.area("What the agent found and did on their visits...", 110);
        ComboBox<String> statusCombo = FormBuilder.combo(null);
        statusCombo.getItems().addAll("Pending", "In Progress", "Completed");
        statusCombo.setValue("Pending");

        if (existing != null) {
            memberCombo.setValue(existing.getMemberName());
            memberCombo.setDisable(true);
            if (existing.getAssignedWorkerName() != null)
                workerCombo.setValue(existing.getAssignedWorkerName());
            if (existing.getReason() != null) reasonArea.setText(existing.getReason());
            if (existing.getReport() != null) reportArea.setText(existing.getReport());
            statusCombo.setValue(existing.getStatus());
        }

        f.section("Case")
         .field("MEMBER *", memberCombo)
         .field("REASON *", reasonArea);
        f.section("Follow-up")
         .row("ASSIGNED AGENT", workerCombo, "STATUS", statusCombo)
         .field("AGENT REPORT", reportArea);

        Button saveBtn = f.saveButton(existing == null ? "Open Case" : "Save Changes");
        saveBtn.setOnAction(e -> {
            String member = memberCombo.getValue();
            String reason = reasonArea.getText().trim();
            if (member == null || member.isBlank() || reason.isEmpty()) {
                f.showError("Please choose the member and enter the reason for the visit.");
                return;
            }
            try {
                Connection conn = DatabaseConnection.getConnection();
                int memberId = getMemberIdByName(conn, member);
                if (memberId <= 0) { f.showError("\"" + member + "\" is not a member — pick a name from the list."); return; }
                int workerId = getWorkerIdByName(conn, workerCombo.getValue());

                if (existing == null) {
                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO welfare_cases (member_id, reason, assigned_worker_id, report, status) " +
                        "VALUES (?,?,?,?,?)"
                    );
                    ps.setInt(1, memberId);
                    ps.setString(2, reason);
                    if (workerId > 0) ps.setInt(3, workerId); else ps.setNull(3, Types.INTEGER);
                    ps.setString(4, reportArea.getText().trim());
                    ps.setString(5, statusCombo.getValue());
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Created welfare case for: " + member);
                } else {
                    String extra = "Completed".equals(statusCombo.getValue()) ? ", completed_at=NOW() " : " ";
                    PreparedStatement ps = conn.prepareStatement(
                        "UPDATE welfare_cases SET reason=?, assigned_worker_id=?, report=?, status=?" +
                        extra + "WHERE id=?"
                    );
                    ps.setString(1, reason);
                    if (workerId > 0) ps.setInt(2, workerId); else ps.setNull(2, Types.INTEGER);
                    ps.setString(3, reportArea.getText().trim());
                    ps.setString(4, statusCombo.getValue());
                    ps.setInt(5, existing.getId());
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Updated welfare case for: " + member + " -> " + statusCombo.getValue());
                }
                loadCases();
                loadAgents(); // case counts per agent
                f.close();
                ToastManager.success(existing == null ? "Welfare case added successfully." : "Welfare case updated successfully.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                f.showError("Could not save the case: " + ex.getMessage());
                ToastManager.error("Failed to save welfare case: " + ex.getMessage());
            }
        });
        f.show(620);
    }

    /** Confirms, then marks the case Completed. Returns true if it was closed. */
    private boolean closeCase(WelfareCase wc) {
        if (wc == null) return false;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Close Case");
        confirm.setHeaderText(null);
        confirm.setContentText("Mark welfare case for " + wc.getMemberName() + " as Completed?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        try {
            PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(
                "UPDATE welfare_cases SET status='Completed', completed_at=NOW() WHERE id=?");
            ps.setInt(1, wc.getId());
            ps.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                "Closed welfare case for: " + wc.getMemberName());
            loadCases();
            ToastManager.success("Welfare case closed successfully.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to close welfare case: " + e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════
    // WELFARE AGENTS  (Fix #2)
    // ══════════════════════════════════════════════════════════

    private void setupAgentsTable() {
        colAgentName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        Avatars.nameColumn(colAgentName, row -> row[4]);
        colAgentPhone.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        colAgentCases.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));

        TableActions.viewOnly(agentsTable, colAgentActions, this::showAgent);
        agentsTable.setItems(allAgents);
    }

    private void loadAgents() {
        allAgents.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT ww.id, m.full_name, m.photo_path, IFNULL(m.phone,'—') AS phone, " +
                "(SELECT COUNT(*) FROM welfare_cases wc WHERE wc.assigned_worker_id = ww.id) AS case_count " +
                "FROM welfare_workers ww " +
                "JOIN members m ON m.id = ww.member_id " +
                "ORDER BY m.full_name"
            );
            while (rs.next()) {
                allAgents.add(new String[]{
                    rs.getString("full_name"),
                    rs.getString("phone"),
                    rs.getString("case_count"),
                    rs.getString("id"),        // hidden: welfare_workers.id
                    rs.getString("photo_path") // hidden: photo
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        applyAgentSearch();
        refreshStats();
    }

    private void showAgent(String[] row) {
        int workerId = Integer.parseInt(row[3]);
        java.util.List<String> cases = new java.util.ArrayList<>();
        for (WelfareCase wc : allCases)
            if (wc.getAssignedWorkerId() == workerId)
                cases.add(wc.getMemberName() + "  —  " + wc.getStatus());
        new DocumentViewer("Welfare Agent", row[0])
            .icon("fas-hands-helping")
            .avatar(Avatars.of(row[4], 64))
            .width(640)
            .meta("Phone", row[1])
            .meta("Cases Assigned", row[2])
            .section("Cases", DocumentViewer.bulletList(cases, "No cases assigned yet."))
            .deleteLabel("Remove Agent")
            .onDelete(() -> removeAgent(workerId, row[0]))
            .show();
    }

    /** Called by the "Add Agent" button in the Agents tab. */
    @FXML
    public void handleAddAgent() {
        FormBuilder f = new FormBuilder("Designate Welfare Agent").icon("fas-hands-helping");

        ComboBox<String> memberCombo = FormBuilder.combo("Search and select member...");
        memberCombo.setEditable(true);
        // Only members who are not already agents
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT full_name FROM members " +
                "WHERE is_deleted=0 AND is_deceased=0 " +
                "  AND id NOT IN (SELECT member_id FROM welfare_workers) " +
                "ORDER BY full_name"
            );
            while (rs.next()) memberCombo.getItems().add(rs.getString("full_name"));
        } catch (SQLException e) { e.printStackTrace(); }

        f.section("Agent")
         .field("MEMBER *", memberCombo)
         .hint("Agents are members who visit and support those in need. They can then be assigned to welfare cases.");

        Button saveBtn = f.saveButton("Designate as Agent");
        saveBtn.setOnAction(e -> {
            String selected = memberCombo.getValue();
            if (selected == null || selected.isBlank()) { f.showError("Please choose a member."); return; }
            try {
                Connection conn = DatabaseConnection.getConnection();
                int memberId = getMemberIdByName(conn, selected);
                if (memberId <= 0) { f.showError("\"" + selected + "\" is not a member — pick a name from the list."); return; }

                PreparedStatement check = conn.prepareStatement(
                    "SELECT id FROM welfare_workers WHERE member_id=?"
                );
                check.setInt(1, memberId);
                if (check.executeQuery().next()) { f.showError(selected + " is already a welfare agent."); return; }

                PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO welfare_workers (member_id) VALUES (?)"
                );
                ps.setInt(1, memberId);
                ps.executeUpdate();
                AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    "Designated welfare agent: " + selected);
                loadAgents();
                f.close();
                ToastManager.success(selected + " is now a welfare agent.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                f.showError("Could not save: " + ex.getMessage());
            }
        });
        f.show(520);
    }

    /** Search handler wired to agentSearchField in FXML. */
    @FXML
    public void handleAgentSearch() {
        applyAgentSearch();
    }

    private void applyAgentSearch() {
        if (agentsTable == null) return;
        String q = agentSearchField != null ? agentSearchField.getText().toLowerCase() : "";
        if (q.isEmpty()) {
            agentsTable.setItems(allAgents);
            return;
        }
        ObservableList<String[]> filtered = FXCollections.observableArrayList();
        for (String[] row : allAgents) {
            if (row[0].toLowerCase().contains(q)) filtered.add(row);
        }
        agentsTable.setItems(filtered);
    }

    /** Confirms, then removes the agent (their open cases become unassigned). Returns true if removed. */
    private boolean removeAgent(int welfareWorkerId, String name) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Remove Welfare Agent");
        confirm.setHeaderText("Remove " + name + " as a welfare agent?");
        confirm.setContentText("Their open cases will become unassigned.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement unassign = conn.prepareStatement(
                "UPDATE welfare_cases SET assigned_worker_id=NULL " +
                "WHERE assigned_worker_id=? AND status != 'Completed'");
            unassign.setInt(1, welfareWorkerId);
            unassign.executeUpdate();
            PreparedStatement del = conn.prepareStatement("DELETE FROM welfare_workers WHERE id=?");
            del.setInt(1, welfareWorkerId);
            del.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                "Removed welfare agent: " + name);
            loadAgents();
            loadCases(); // refresh case list (agent column changes)
            ToastManager.success(name + " removed as a welfare agent.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to remove agent: " + e.getMessage());
            return false;
        }
    }

    // ── HELPERS ────────────────────────────────────────────────

    private void loadMembersInto(ComboBox<String> combo) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT full_name FROM members WHERE is_deleted=0 ORDER BY full_name"
            );
            while (rs.next()) combo.getItems().add(rs.getString("full_name"));
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadWorkersInto(ComboBox<String> combo) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT m.full_name FROM welfare_workers ww " +
                "JOIN members m ON m.id = ww.member_id " +
                "ORDER BY m.full_name"
            );
            while (rs.next()) combo.getItems().add(rs.getString("full_name"));
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private int getMemberIdByName(Connection conn, String name) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
            "SELECT id FROM members WHERE full_name=? AND is_deleted=0 LIMIT 1"
        );
        ps.setString(1, name);
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getInt("id") : -1;
    }

    private int getWorkerIdByName(Connection conn, String name) {
        if (name == null || name.isEmpty()) return 0;
        try {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT ww.id FROM welfare_workers ww " +
                "JOIN members m ON m.id=ww.member_id " +
                "WHERE m.full_name=?"
            );
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("id") : 0;
        } catch (SQLException e) { return 0; }
    }

}
