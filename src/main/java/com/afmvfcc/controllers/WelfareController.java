package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.models.WelfareCase;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.SessionManager;
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

        colWActions.setCellFactory(col -> new TableCell<WelfareCase, Void>() {
            private final Button viewBtn  = new Button("View");
            private final Button closeBtn = new Button("Close");
            private final HBox   box      = new HBox(6, viewBtn, closeBtn);
            {
                viewBtn.getStyleClass().add("btn-secondary");
                viewBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                closeBtn.getStyleClass().add("btn-primary");
                closeBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                box.setAlignment(Pos.CENTER_LEFT);
                viewBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        openCaseDialog(getTableView().getItems().get(idx));
                });
                closeBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        closeCase(getTableView().getItems().get(idx));
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        welfareTable.setItems(allCases);
    }

    private void loadCases() {
        allCases.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT wc.*, m.full_name AS member_name, wm.full_name AS worker_name " +
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
                wc.setReason(rs.getString("reason"));
                wc.setAssignedWorkerId(rs.getInt("assigned_worker_id"));
                wc.setAssignedWorkerName(rs.getString("worker_name"));
                wc.setReport(rs.getString("report"));
                wc.setStatus(rs.getString("status"));
                if (rs.getTimestamp("opened_at") != null)
                    wc.setOpenedAt(rs.getTimestamp("opened_at").toLocalDateTime());
                allCases.add(wc);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        applyFilter();
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
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(existing == null ? "New Welfare Case" : "Welfare Case: " + existing.getMemberName());

        VBox root = new VBox(16);
        root.setStyle("-fx-background-color:#F5F6FA;");

        ComboBox<String> memberCombo = new ComboBox<>();
        memberCombo.getStyleClass().add("form-combo");
        memberCombo.setEditable(true);
        memberCombo.setPromptText("Select member...");
        memberCombo.setMaxWidth(Double.MAX_VALUE);
        loadMembersInto(memberCombo);

        ComboBox<String> workerCombo = new ComboBox<>();
        workerCombo.getStyleClass().add("form-combo");
        workerCombo.setPromptText("Assign welfare agent...");
        workerCombo.setMaxWidth(Double.MAX_VALUE);
        loadWorkersInto(workerCombo);

        TextArea reasonArea = new TextArea();
        reasonArea.getStyleClass().add("form-textarea");
        reasonArea.setPromptText("Reason for welfare visit...");
        reasonArea.setPrefHeight(80);
        reasonArea.setMaxWidth(Double.MAX_VALUE);

        TextArea reportArea = new TextArea();
        reportArea.getStyleClass().add("form-textarea");
        reportArea.setPromptText("Welfare agent report...");
        reportArea.setPrefHeight(100);
        reportArea.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getStyleClass().add("form-combo");
        statusCombo.getItems().addAll("Pending", "In Progress", "Completed");
        statusCombo.setValue("Pending");
        statusCombo.setMaxWidth(Double.MAX_VALUE);

        if (existing != null) {
            memberCombo.setValue(existing.getMemberName());
            memberCombo.setDisable(true);
            if (existing.getAssignedWorkerName() != null)
                workerCombo.setValue(existing.getAssignedWorkerName());
            if (existing.getReason() != null) reasonArea.setText(existing.getReason());
            if (existing.getReport() != null) reportArea.setText(existing.getReport());
            statusCombo.setValue(existing.getStatus());
        }

        Button saveBtn   = new Button("Save");
        Button cancelBtn = new Button("Cancel");
        saveBtn.getStyleClass().add("btn-primary");
        cancelBtn.getStyleClass().add("btn-secondary");
        HBox btns = new HBox(10, cancelBtn, saveBtn);
        btns.setAlignment(Pos.CENTER_RIGHT);

        VBox formBody = new VBox(14);
        formBody.setStyle("-fx-padding:20 24;-fx-background-color:#F5F6FA;");
        formBody.getChildren().addAll(
            formRow("MEMBER *",          memberCombo),
            formRow("ASSIGNED AGENT",    workerCombo),
            formRow("REASON *",          reasonArea),
            formRow("AGENT REPORT",      reportArea),
            formRow("STATUS",            statusCombo)
        );

        ScrollPane bodyScroll = new ScrollPane(formBody);
        bodyScroll.setFitToWidth(true);
        bodyScroll.setStyle("-fx-background-color:transparent;-fx-background:transparent;-fx-border-color:transparent;");
        VBox.setVgrow(bodyScroll, Priority.ALWAYS);

        btns.setStyle("-fx-background-color:#FFFFFF;-fx-padding:14 24;-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");
        btns.setMaxWidth(Double.MAX_VALUE);
        root.getChildren().addAll(bodyScroll, btns);

        cancelBtn.setOnAction(e -> stage.close());
        saveBtn.setOnAction(e -> {
            String member = memberCombo.getValue();
            String reason = reasonArea.getText().trim();
            if (member == null || member.isEmpty() || reason.isEmpty()) return;
            try {
                Connection conn = DatabaseConnection.getConnection();
                int memberId = getMemberIdByName(conn, member);
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
                        "UPDATE welfare_cases SET assigned_worker_id=?, report=?, status=?" +
                        extra + "WHERE id=?"
                    );
                    if (workerId > 0) ps.setInt(1, workerId); else ps.setNull(1, Types.INTEGER);
                    ps.setString(2, reportArea.getText().trim());
                    ps.setString(3, statusCombo.getValue());
                    ps.setInt(4, existing.getId());
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Updated welfare case for: " + member + " -> " + statusCombo.getValue());
                }
                loadCases();
                stage.close();
                ToastManager.success(existing == null ? "Welfare case added successfully." : "Welfare case updated successfully.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                ToastManager.error("Failed to save welfare case: " + ex.getMessage());
            }
        });

        Scene scene = new Scene(root, 520, 580);
        scene.setFill(javafx.scene.paint.Color.web("#F5F6FA"));
        scene.getStylesheets().add(getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    private void closeCase(WelfareCase wc) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Close Case");
        confirm.setHeaderText(null);
        confirm.setContentText("Mark welfare case for " + wc.getMemberName() + " as Completed?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    Connection conn = DatabaseConnection.getConnection();
                    conn.createStatement().executeUpdate(
                        "UPDATE welfare_cases SET status='Completed', completed_at=NOW() WHERE id=" + wc.getId()
                    );
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Closed welfare case for: " + wc.getMemberName());
                    loadCases();
                    ToastManager.success("Welfare case closed successfully.");
                } catch (SQLException e) {
                    e.printStackTrace();
                    ToastManager.error("Failed to close welfare case: " + e.getMessage());
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════
    // WELFARE AGENTS  (Fix #2)
    // ══════════════════════════════════════════════════════════

    private void setupAgentsTable() {
        colAgentName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        colAgentPhone.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        colAgentCases.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));

        colAgentActions.setCellFactory(col -> new TableCell<String[], Void>() {
            private final Button removeBtn = new Button("Remove Agent");
            {
                removeBtn.getStyleClass().add("btn-danger");
                removeBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                removeBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        String[] row = getTableView().getItems().get(idx);
                        removeAgent(Integer.parseInt(row[3]), row[0]);
                    }
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : removeBtn);
            }
        });
        agentsTable.setItems(allAgents);
    }

    private void loadAgents() {
        allAgents.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT ww.id, m.full_name, IFNULL(m.phone,'—') AS phone, " +
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
                    rs.getString("id")         // hidden: welfare_workers.id
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        applyAgentSearch();
    }

    /** Called by the "Add Agent" button in the Agents tab. */
    @FXML
    public void handleAddAgent() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Designate Welfare Agent");

        VBox root = new VBox(16);
        root.setStyle("-fx-background-color:#F5F6FA;-fx-padding:24;");

        Label hint = new Label("Select a church member to designate as a welfare agent.\n" +
                               "Agents can be assigned to welfare cases.");
        hint.setStyle("-fx-font-size:12px;-fx-text-fill:#6B7280;");
        hint.setWrapText(true);

        ComboBox<String> memberCombo = new ComboBox<>();
        memberCombo.getStyleClass().add("form-combo");
        memberCombo.setEditable(true);
        memberCombo.setPromptText("Search and select member...");
        memberCombo.setMaxWidth(Double.MAX_VALUE);

        // Only show members who are NOT already agents
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT full_name FROM members " +
                "WHERE is_deleted=0 " +
                "  AND id NOT IN (SELECT member_id FROM welfare_workers) " +
                "ORDER BY full_name"
            );
            while (rs.next()) memberCombo.getItems().add(rs.getString("full_name"));
        } catch (SQLException e) { e.printStackTrace(); }

        Button saveBtn   = new Button("Designate as Agent");
        Button cancelBtn = new Button("Cancel");
        saveBtn.getStyleClass().add("btn-primary");
        cancelBtn.getStyleClass().add("btn-secondary");
        HBox btns = new HBox(10, cancelBtn, saveBtn);
        btns.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(
            hint,
            makeRow("SELECT MEMBER *", memberCombo),
            btns
        );

        cancelBtn.setOnAction(e -> stage.close());
        saveBtn.setOnAction(e -> {
            String selected = memberCombo.getValue();
            if (selected == null || selected.isEmpty()) return;
            try {
                Connection conn = DatabaseConnection.getConnection();
                int memberId = getMemberIdByName(conn, selected);
                if (memberId <= 0) return;

                // Check not already an agent
                PreparedStatement check = conn.prepareStatement(
                    "SELECT id FROM welfare_workers WHERE member_id=?"
                );
                check.setInt(1, memberId);
                if (check.executeQuery().next()) {
                    new Alert(Alert.AlertType.INFORMATION,
                        selected + " is already a welfare agent.").showAndWait();
                    return;
                }

                PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO welfare_workers (member_id) VALUES (?)"
                );
                ps.setInt(1, memberId);
                ps.executeUpdate();
                AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    "Designated welfare agent: " + selected);
                loadAgents();
                stage.close();
            } catch (SQLException ex) { ex.printStackTrace(); }
        });

        Scene scene = new Scene(root, 440, 240);
        scene.getStylesheets().add(
            getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm()
        );
        stage.setScene(scene);
        stage.showAndWait();
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

    private void removeAgent(int welfareWorkerId, String name) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Remove Welfare Agent");
        confirm.setHeaderText("Remove " + name + " as a welfare agent?");
        confirm.setContentText("Their existing cases will become unassigned.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    Connection conn = DatabaseConnection.getConnection();
                    // Unassign their open cases first
                    conn.createStatement().executeUpdate(
                        "UPDATE welfare_cases SET assigned_worker_id=NULL " +
                        "WHERE assigned_worker_id=" + welfareWorkerId +
                        "  AND status != 'Completed'"
                    );
                    // Remove agent record
                    conn.createStatement().executeUpdate(
                        "DELETE FROM welfare_workers WHERE id=" + welfareWorkerId
                    );
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Removed welfare agent: " + name);
                    loadAgents();
                    loadCases(); // refresh case list (agent column changes)
                } catch (SQLException e) { e.printStackTrace(); }
            }
        });
    }

    // ── HELPERS ────────────────────────────────────────────────

    private VBox makeRow(String labelText, javafx.scene.Node field) {
        VBox box = new VBox(6);
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        box.getChildren().addAll(lbl, field);
        if (field instanceof Control) ((Control) field).setMaxWidth(Double.MAX_VALUE);
        return box;
    }

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

    private VBox formRow(String labelText, javafx.scene.Node field) {
        return makeRow(labelText, field);
    }
}
