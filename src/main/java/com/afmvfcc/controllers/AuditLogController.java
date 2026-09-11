package com.afmvfcc.controllers;

import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.models.AuditEntry;
import com.afmvfcc.utils.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.sql.*;
import java.time.format.DateTimeFormatter;

public class AuditLogController {

    @FXML private TableView<AuditEntry>             auditTable;
    @FXML private TableColumn<AuditEntry, String>   colAuditAdmin, colAuditAction, colAuditDate;
    @FXML private TextField                         auditSearchField;
    @FXML private Label                             accessDeniedLabel;
    @FXML private VBox                              contentBox;

    private ObservableList<AuditEntry> allEntries = FXCollections.observableArrayList();
    private final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");

    @FXML
    public void initialize() {
        if (!SessionManager.getInstance().isSuperAdmin()) {
            contentBox.setVisible(false);
            contentBox.setManaged(false);
            accessDeniedLabel.setVisible(true);
            accessDeniedLabel.setManaged(true);
            return;
        }
        setupTable();
        loadAuditLog();
    }

    private void setupTable() {
        colAuditAdmin.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getAdminName() != null ? d.getValue().getAdminName() : "System"
        ));
        colAuditAction.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAction()));
        colAuditDate.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getPerformedAt() != null
                ? d.getValue().getPerformedAt().format(FMT) : "—"
        ));
        auditTable.setItems(allEntries);
    }

    private void loadAuditLog() {
        allEntries.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = """
                SELECT al.id, al.user_id, u.full_name, al.action, al.performed_at
                FROM audit_log al
                LEFT JOIN users u ON u.id = al.user_id
                ORDER BY al.performed_at DESC
                LIMIT 500
                """;
            ResultSet rs = conn.createStatement().executeQuery(sql);
            while (rs.next()) {
                AuditEntry e = new AuditEntry();
                e.setId(rs.getInt("id"));
                e.setUserId(rs.getInt("user_id"));
                e.setAdminName(rs.getString("full_name"));
                e.setAction(rs.getString("action"));
                if (rs.getTimestamp("performed_at") != null)
                    e.setPerformedAt(rs.getTimestamp("performed_at").toLocalDateTime());
                allEntries.add(e);
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @FXML public void handleSearch() {
        String q = auditSearchField.getText().toLowerCase();
        if (q.isEmpty()) { auditTable.setItems(allEntries); return; }
        ObservableList<AuditEntry> f = FXCollections.observableArrayList();
        for (AuditEntry e : allEntries) {
            if (e.getAction().toLowerCase().contains(q) ||
                (e.getAdminName() != null && e.getAdminName().toLowerCase().contains(q)))
                f.add(e);
        }
        auditTable.setItems(f);
    }

    @FXML public void handleRefresh() { loadAuditLog(); }
}
