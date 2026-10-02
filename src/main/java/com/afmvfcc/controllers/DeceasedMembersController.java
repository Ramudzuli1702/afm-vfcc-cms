package com.afmvfcc.controllers;

import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.Avatars;
import com.afmvfcc.utils.Dialogs;
import com.afmvfcc.utils.DocumentViewer;
import com.afmvfcc.utils.FormBuilder;
import com.afmvfcc.utils.TableActions;
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
        Avatars.nameColumn(colDecName, row -> row[7]);
        colDecDod.setCellValueFactory(d       -> new SimpleStringProperty(d.getValue()[1]));
        colDecSubBranch.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        colDecObituary.setCellValueFactory(d  -> new SimpleStringProperty(d.getValue()[3]));
        colDecRecorded.setCellValueFactory(d  -> new SimpleStringProperty(d.getValue()[4]));

        TableActions.viewOnly(deceasedTable, colDecActions, this::showDeparted);

        deceasedTable.setItems(allDeceased);
    }

    private void showDeparted(String[] row) {
        int memberId = Integer.parseInt(row[5]);
        String photo = null;
        try {
            PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement("SELECT photo_path FROM members WHERE id=?");
            ps.setInt(1, memberId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) photo = rs.getString(1);
        } catch (SQLException e) { e.printStackTrace(); }

        DocumentViewer v = new DocumentViewer("Faithful Departed", row[0])
            .icon("fas-dove")
            .avatar(Avatars.of(photo, 64))
            .width(680)
            .meta("Date of Death", row[1])
            .meta("Sub-Branch",    row[2])
            .meta("Recorded",      row[4])
            .section("Memorial Note", row[3], "No memorial note recorded.");
        if (onViewMember != null)
            v.action("Full Profile", "fas-id-card", () -> onViewMember.accept(memberId));
        v.closingAction("Restore to Active", "fas-undo", "btn-secondary", () -> restoreMember(row))
         .editLabel("Edit Record")
         .onEdit(() -> openEditDialog(row))
         .show();
    }

    public void loadDeceased() {
        allDeceased.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT m.id, m.full_name, m.photo_path, " +
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
                    rs.getString("dm_id"),          // 6 → deceased_members.id
                    rs.getString("photo_path")      // 7 → photo
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

        FormBuilder f = new FormBuilder("Edit Faithful Departed Record", row[0]).icon("fas-dove");
        DatePicker dodPicker = FormBuilder.date();
        if (!"—".equals(row[1])) {
            try {
                // Dates are formatted by MySQL ("Sep"), so parse in English, not the system locale ("Sept")
                dodPicker.setValue(LocalDate.parse(row[1],
                    DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)));
            } catch (Exception ignored) {}
        }
        TextArea obituaryArea = FormBuilder.area("Memorial note or brief obituary...", 120);
        obituaryArea.setText(row[3]);

        f.section("Record")
         .field("DATE OF PASSING", dodPicker)
         .field("MEMORIAL NOTE", obituaryArea);

        Button saveBtn = f.saveButton("Save Changes");
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
                f.close();
                ToastManager.success("Faithful Departed record updated.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                f.showError("Could not save the record: " + ex.getMessage());
            }
        });
        f.show(520);
    }

    // ── Restore to active ─────────────────────────────────────

    /** Confirms, then returns the member to the active list. Returns true if restored. */
    private boolean restoreMember(String[] row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        com.afmvfcc.Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Restore Member");
        confirm.setHeaderText("Restore " + row[0] + " to the active member list?");
        confirm.setContentText(
            "This will remove them from the Faithful Departed list and mark them as active again."
        );
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
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
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to restore member: " + e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════
    // STATIC HELPER — called from MembersController action column
    // ══════════════════════════════════════════════════════════

    public static void showMarkDeceasedDialog(int memberId, String memberName,
                                               Runnable onComplete, String cssPath) {
        FormBuilder f = new FormBuilder("Record as Faithful Departed", memberName).icon("fas-dove");

        DatePicker dodPicker = FormBuilder.date();
        dodPicker.setValue(LocalDate.now());
        TextArea obituaryArea = FormBuilder.area("Optional: memorial note or brief obituary...", 100);

        f.section("Record")
         .hint(memberName + " will be moved to the Faithful Departed list. Their history, attendance and " +
               "records are kept, but they no longer appear in active member lists or communications.")
         .field("DATE OF PASSING", dodPicker)
         .field("MEMORIAL NOTE", obituaryArea);

        Button confirmBtn = f.saveButton("Record as Faithful Departed");
        confirmBtn.getStyleClass().setAll("button", "btn-danger");
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

                f.close();
                if (onComplete != null) onComplete.run();
                ToastManager.success(memberName + " recorded as Faithful Departed.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                f.showError("Could not record: " + ex.getMessage());
            }
        });
        f.show(540);
    }
}