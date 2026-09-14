package com.afmvfcc.controllers;

import com.afmvfcc.db.DatabaseConnection;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * DeceasedMembersController — Faithful Departed
 *
 * Manages the "Faithful Departed" tab for members who have passed on.
 * They remain in the members table (is_deceased=1, is_active=0, is_deleted=0)
 * so all history, attendance, and records are preserved, but they are hidden
 * from all active member views and communications.
 *
 * Wired into MembersController the same way GuestsController is embedded.
 *
 */
public class DeceasedMembersController {

    @FXML public TableView<String[]>            deceasedTable;
    @FXML public TableColumn<String[], String>  colDecName, colDecDod, colDecSubBranch,
                                                 colDecObituary, colDecRecorded;
    @FXML public TableColumn<String[], Void>    colDecActions;
    @FXML public TextField                      deceasedSearchField;

    /** Callback to view full member details (called from MembersController) */
    public java.util.function.Consumer<Integer> onViewMember;

    private final ObservableList<String[]> allDeceased = FXCollections.observableArrayList();
    private final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @FXML
    public void initialize() {
        setupTable();
        loadDeceased();
    }

    private void setupTable() {
        colDecName.setCellValueFactory(d      -> new SimpleStringProperty(d.getValue()[0]));
        colDecDod.setCellValueFactory(d       -> new SimpleStringProperty(d.getValue()[1]));
        colDecSubBranch.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        colDecObituary.setCellValueFactory(d  -> new SimpleStringProperty(d.getValue()[3]));
        colDecRecorded.setCellValueFactory(d  -> new SimpleStringProperty(d.getValue()[4]));

        colDecActions.setCellFactory(col -> new TableCell<String[], Void>() {
            private final Button viewBtn    = new Button("View");
            private final Button editBtn    = new Button("Edit");
            private final Button restoreBtn = new Button("Restore");

            private final HBox   box        = new HBox(6, viewBtn, editBtn, restoreBtn);

            {
                // View button
                viewBtn.getStyleClass().add("btn-secondary");
                viewBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                viewBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size() && onViewMember != null) {
                        String[] row = getTableView().getItems().get(idx);
                        onViewMember.accept(Integer.parseInt(row[5])); // member id is at index 5
                    }
                });

                // Edit button
                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                editBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        openEditDialog(getTableView().getItems().get(idx));
                });

                // Restore button
                restoreBtn.getStyleClass().add("btn-primary");
                restoreBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                restoreBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        restoreMember(getTableView().getItems().get(idx));
                });

                box.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        deceasedTable.setItems(allDeceased);
    }

    public void loadDeceased() {
        allDeceased.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT m.id, m.full_name, " +
                "       IFNULL(sb.name, '—') AS sub_branch, " +
                "       IFNULL(DATE_FORMAT(dm.date_of_death,'%d %b %Y'),'—') AS dod, " +
                "       IFNULL(dm.obituary,'') AS obituary, " +
                "       IFNULL(DATE_FORMAT(dm.recorded_at,'%d %b %Y'),'—') AS recorded, " +
                "       dm.id AS dm_id " +
                "FROM members m " +
                "LEFT JOIN sub_branches sb ON sb.id = m.sub_branch_id " +
                "LEFT JOIN deceased_members dm ON dm.member_id = m.id " +
                "WHERE m.is_deceased = 1 " +
                "ORDER BY dm.date_of_death DESC, m.full_name ASC"
            );
            while (rs.next()) {
                allDeceased.add(new String[]{
                    rs.getString("full_name"),      // 0
                    rs.getString("dod"),            // 1
                    rs.getString("sub_branch"),     // 2
                    rs.getString("obituary"),       // 3
                    rs.getString("recorded"),       // 4
                    rs.getString("id"),             // 5 → member_id
                    rs.getString("dm_id")           // 6 → deceased_members.id
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        applySearch();
    }

    @FXML
    public void handleDeceasedSearch() {
        applySearch();
    }

    private void applySearch() {
        String q = deceasedSearchField != null ? deceasedSearchField.getText().toLowerCase().trim() : "";
        if (q.isEmpty()) {
            deceasedTable.setItems(allDeceased);
            return;
        }

        ObservableList<String[]> filtered = FXCollections.observableArrayList();
        for (String[] row : allDeceased) {
            if (row[0].toLowerCase().contains(q) || row[2].toLowerCase().contains(q)) {
                filtered.add(row);
            }
        }
        deceasedTable.setItems(filtered);
    }

    // ── Edit memorial note / date of passing ──────────────────

    private void openEditDialog(String[] row) {
        int memberId   = Integer.parseInt(row[5]);
        String dmIdStr = row[6];
        boolean hasRecord = dmIdStr != null && !dmIdStr.equals("null") && !dmIdStr.isEmpty();

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Faithful Departed — " + row[0]);

        VBox root = new VBox(16);
        root.setStyle("-fx-background-color:#F5F6FA;-fx-padding:24;");

        DatePicker dodPicker = new DatePicker();
        if (!"—".equals(row[1])) {
            try {
                dodPicker.setValue(LocalDate.parse(row[1], DateTimeFormatter.ofPattern("dd MMM yyyy")));
            } catch (Exception ignored) {}
        }
        dodPicker.getStyleClass().add("form-date-picker");
        dodPicker.setMaxWidth(Double.MAX_VALUE);

        TextArea obituaryArea = new TextArea(row[3]);
        obituaryArea.getStyleClass().add("form-textarea");
        obituaryArea.setPromptText("Memorial note or brief obituary...");
        obituaryArea.setPrefHeight(120);
        obituaryArea.setMaxWidth(Double.MAX_VALUE);

        Button saveBtn   = new Button("Save");
        Button cancelBtn = new Button("Cancel");
        saveBtn.getStyleClass().add("btn-primary");
        cancelBtn.getStyleClass().add("btn-secondary");
        HBox btns = new HBox(10, cancelBtn, saveBtn);
        btns.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(
            makeRow("DATE OF PASSING", dodPicker),
            makeRow("MEMORIAL NOTE",   obituaryArea),
            btns
        );

        cancelBtn.setOnAction(e -> stage.close());
        saveBtn.setOnAction(e -> {
            try {
                Connection conn = DatabaseConnection.getConnection();
                Date sqlDod = dodPicker.getValue() != null
                    ? Date.valueOf(dodPicker.getValue()) : null;
                String obit = obituaryArea.getText().trim();

                if (hasRecord) {
                    PreparedStatement ps = conn.prepareStatement(
                        "UPDATE deceased_members SET date_of_death=?, obituary=? WHERE id=?"
                    );
                    ps.setDate(1, sqlDod);
                    ps.setString(2, obit);
                    ps.setInt(3, Integer.parseInt(dmIdStr));
                    ps.executeUpdate();
                } else {
                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO deceased_members " +
                        "(member_id, date_of_death, obituary, recorded_by) VALUES (?,?,?,?)"
                    );
                    ps.setInt(1, memberId);
                    ps.setDate(2, sqlDod);
                    ps.setString(3, obit);
                    ps.setInt(4, SessionManager.getInstance().getCurrentUser().getId());
                    ps.executeUpdate();
                }

                AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    "Updated Faithful Departed record for: " + row[0]);

                loadDeceased();
                stage.close();
                ToastManager.success("Faithful Departed record updated.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                ToastManager.error("Failed to save record: " + ex.getMessage());
            }
        });

        Scene scene = new Scene(root, 460, 300);
        scene.getStylesheets().add(
            getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm()
        );
        stage.setScene(scene);
        stage.showAndWait();
    }

    // ── Restore to active ─────────────────────────────────────

    private void restoreMember(String[] row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Restore Member");
        confirm.setHeaderText("Restore " + row[0] + " to the active member list?");
        confirm.setContentText(
            "This will remove them from the Faithful Departed list and mark them as active again."
        );
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                        "UPDATE members SET is_deceased=0, is_active=1 WHERE id=?"
                    );
                    ps.setInt(1, Integer.parseInt(row[5]));
                    ps.executeUpdate();

                    AuditLogger.log(
                        SessionManager.getInstance().getCurrentUser().getId(),
                        "Restored member from Faithful Departed: " + row[0]
                    );

                    loadDeceased();
                    ToastManager.success(row[0] + " restored to the active member list.");
                } catch (SQLException e) {
                    e.printStackTrace();
                    ToastManager.error("Failed to restore member: " + e.getMessage());
                }
            }
        });
    }

    private VBox makeRow(String labelText, javafx.scene.Node field) {
        VBox box = new VBox(6);
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        box.getChildren().addAll(lbl, field);
        if (field instanceof Control) {
            ((Control) field).setMaxWidth(Double.MAX_VALUE);
        }
        return box;
    }

    // ══════════════════════════════════════════════════════════
    // STATIC HELPER — called from MembersController action column
    // ══════════════════════════════════════════════════════════

    public static void showMarkDeceasedDialog(int memberId, String memberName,
                                               Runnable onComplete, String cssPath) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Record as Faithful Departed — " + memberName);

        VBox root = new VBox(16);
        root.setStyle("-fx-background-color:#F5F6FA;-fx-padding:24;");

        Label info = new Label(
            memberName + " will be moved to the Faithful Departed list.\n" +
            "Their full history, attendance, and records will be preserved.\n" +
            "They will no longer appear in active member views or communications."
        );
        info.setStyle("-fx-font-size:12px;-fx-text-fill:#6B7280;");
        info.setWrapText(true);

        DatePicker dodPicker = new DatePicker(LocalDate.now());
        dodPicker.getStyleClass().add("form-date-picker");
        dodPicker.setMaxWidth(Double.MAX_VALUE);

        TextArea obituaryArea = new TextArea();
        obituaryArea.getStyleClass().add("form-textarea");
        obituaryArea.setPromptText("Optional: memorial note or brief obituary...");
        obituaryArea.setPrefHeight(100);
        obituaryArea.setMaxWidth(Double.MAX_VALUE);

        Button confirmBtn = new Button("Record as Faithful Departed");
        Button cancelBtn  = new Button("Cancel");
        confirmBtn.getStyleClass().add("btn-danger");
        cancelBtn.getStyleClass().add("btn-secondary");
        HBox btns = new HBox(10, cancelBtn, confirmBtn);
        btns.setAlignment(Pos.CENTER_RIGHT);

        VBox dodRow = new VBox(6);
        Label dodLbl = new Label("DATE OF PASSING");
        dodLbl.getStyleClass().add("form-label");
        dodRow.getChildren().addAll(dodLbl, dodPicker);

        VBox obitRow = new VBox(6);
        Label obitLbl = new Label("MEMORIAL NOTE");
        obitLbl.getStyleClass().add("form-label");
        obitRow.getChildren().addAll(obitLbl, obituaryArea);

        root.getChildren().addAll(info, dodRow, obitRow, btns);

        cancelBtn.setOnAction(e -> stage.close());
        confirmBtn.setOnAction(e -> {
            try {
                Connection conn = DatabaseConnection.getConnection();

                // Flag the member
                PreparedStatement flag = conn.prepareStatement(
                    "UPDATE members SET is_deceased=1, is_active=0 WHERE id=?"
                );
                flag.setInt(1, memberId);
                flag.executeUpdate();

                // Record details (upsert — safe to re-run)
                PreparedStatement rec = conn.prepareStatement(
                    "INSERT INTO deceased_members (member_id, date_of_death, obituary, recorded_by) " +
                    "VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "  date_of_death = VALUES(date_of_death), " +
                    "  obituary      = VALUES(obituary)"
                );
                rec.setInt(1, memberId);
                rec.setDate(2, dodPicker.getValue() != null
                    ? Date.valueOf(dodPicker.getValue()) : null);
                rec.setString(3, obituaryArea.getText().trim());
                rec.setInt(4, SessionManager.getInstance().getCurrentUser().getId());
                rec.executeUpdate();

                AuditLogger.log(
                    SessionManager.getInstance().getCurrentUser().getId(),
                    "Recorded as Faithful Departed: " + memberName
                );

                stage.close();
                if (onComplete != null) onComplete.run();
                ToastManager.success(memberName + " recorded as Faithful Departed.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                ToastManager.error("Failed to record: " + ex.getMessage());
            }
        });

        Scene scene = new Scene(root, 480, 380);
        if (cssPath != null) scene.getStylesheets().add(cssPath);
        stage.setScene(scene);
        stage.showAndWait();
    }
}