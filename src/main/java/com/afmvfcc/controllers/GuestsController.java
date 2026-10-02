package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.DocumentViewer;
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
import java.util.ArrayList;
import java.util.List;

public class GuestsController {

    // Package-private so MembersController can inject them
    @FXML TableView<String[]>            guestsTable;
    @FXML TableColumn<String[], String>  colGuestName, colGuestPhone, colGuestGender,
                                          colGuestBranch, colGuestInvited,
                                          colGuestMembership, colGuestDate, colGuestStatus;
    @FXML TableColumn<String[], Void>    colGuestActions;
    @FXML TextField                      guestSearchField;
    @FXML ComboBox<String>               filterGuestStatus;
    @FXML Label                          guestCountLabel;

    private final ObservableList<String[]> allGuests = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();
        filterGuestStatus.setItems(FXCollections.observableArrayList(
            "All", "Guest", "Converted", "Dismissed"));
        filterGuestStatus.setValue("All");
        filterGuestStatus.setOnAction(e -> applyFilter());
        loadGuests();
    }

    private void setupTable() {
        colGuestName.setCellValueFactory(      d -> new SimpleStringProperty(d.getValue()[1]));
        colGuestPhone.setCellValueFactory(     d -> new SimpleStringProperty(nvl(d.getValue()[2])));
        colGuestGender.setCellValueFactory(    d -> new SimpleStringProperty(nvl(d.getValue()[3])));
        colGuestBranch.setCellValueFactory(    d -> new SimpleStringProperty(nvl(d.getValue()[4])));
        colGuestInvited.setCellValueFactory(   d -> new SimpleStringProperty(nvl(d.getValue()[5])));
        colGuestDate.setCellValueFactory(      d -> new SimpleStringProperty(nvl(d.getValue()[8])));

        colGuestMembership.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[6]));
        colGuestMembership.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label("1".equals(item) ? "Yes" : "No");
                badge.getStyleClass().add("1".equals(item) ? "badge-active" : "badge-inactive");
                setGraphic(badge); setText(null);
            }
        });

        colGuestStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[9]));
        colGuestStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label(item);
                badge.getStyleClass().add(
                    "Guest".equals(item)     ? "badge-pending" :
                    "Converted".equals(item) ? "badge-active"  : "badge-inactive");
                setGraphic(badge); setText(null);
            }
        });

        TableActions.viewOnly(guestsTable, colGuestActions, this::showGuestDetail);

        guestsTable.setItems(allGuests);
    }

    public void loadGuests() {
        allGuests.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT g.id, g.full_name, g.phone, g.gender, " +
                "IFNULL(sb.name,'') AS branch, g.invited_by, " +
                "g.wants_membership, g.prayer_request, " +
                "DATE_FORMAT(g.visit_date,'%d %b %Y') AS visit_date, " +
                "g.status, IFNULL(s.session_name,'') AS session_name " +
                "FROM guests g " +
                "LEFT JOIN sub_branches sb ON sb.id = g.sub_branch_id " +
                "LEFT JOIN attendance_sessions s ON s.id = g.session_id " +
                "ORDER BY g.created_at DESC");
            while (rs.next()) {
                allGuests.add(new String[]{
                    rs.getString("id"),       rs.getString("full_name"),
                    rs.getString("phone"),    rs.getString("gender"),
                    rs.getString("branch"),   rs.getString("invited_by"),
                    rs.getString("wants_membership"), rs.getString("prayer_request"),
                    rs.getString("visit_date"), rs.getString("status"),
                    rs.getString("session_name")
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        applyFilter();
    }

    @FXML
    public void handleGuestSearch() { applyFilter(); }

    private void applyFilter() {
        String search = guestSearchField != null
            ? guestSearchField.getText().toLowerCase().trim() : "";
        String status = filterGuestStatus != null
            ? filterGuestStatus.getValue() : "All";

        List<String[]> filtered = new ArrayList<>();
        for (String[] g : allGuests) {
            if (!search.isEmpty()) {
                boolean match = g[1].toLowerCase().contains(search) ||
                    (g[2] != null && g[2].contains(search)) ||
                    (g[5] != null && g[5].toLowerCase().contains(search));
                if (!match) continue;
            }
            if (!"All".equals(status) && !status.equals(g[9])) continue;
            filtered.add(g);
        }
        guestsTable.setItems(FXCollections.observableArrayList(filtered));
        if (guestCountLabel != null)
            guestCountLabel.setText(filtered.size() + " guest(s)");
    }

    private void showGuestDetail(String[] g) {
        String status = g[9];
        DocumentViewer v = new DocumentViewer("Guest", g[1])
            .icon("fas-user-friends")
            .status(status, "Guest".equals(status) ? "badge-pending"
                          : "Converted".equals(status) ? "badge-active" : "badge-inactive")
            .width(640)
            .meta("Phone",            g[2])
            .meta("Gender",           g[3])
            .meta("Sub-Branch",       g[4])
            .meta("Invited By",       g[5])
            .meta("Wants Membership", "1".equals(g[6]) ? "Yes" : "No")
            .meta("Visit Date",       g[8])
            .meta("Session",          g[10])
            .section("Prayer Request for the Bishop", g[7], "No prayer request.");
        if ("Guest".equals(status)) {
            v.closingAction("Dismiss", "fas-user-times", "btn-danger", () -> dismissGuest(g));
            v.closingAction("Promote to Member", "fas-user-plus", "btn-primary", () -> promoteGuest(g));
        }
        v.show();
    }

    private VBox detailRow(String label, String value) {
        VBox box = new VBox(3);
        Label l = new Label(label.toUpperCase());
        l.setStyle("-fx-font-size:9px;-fx-font-weight:700;-fx-text-fill:#9099AA;");
        Label v = new Label(value != null && !value.isEmpty() ? value : "-");
        v.setStyle("-fx-font-size:13px;-fx-text-fill:#1E2130;");
        box.getChildren().addAll(l, v);
        return box;
    }

    /** Confirms, then sends the guest to Pending Review. Returns true if promoted. */
    private boolean promoteGuest(String[] g) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Promote Guest");
        confirm.setHeaderText("Send " + g[1] + " to Pending Review?");
        confirm.setContentText("This will add them to Pending Review for approval as a full member.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO pending_members " +
                "(full_name, phone, sub_branch_id, ministry_id, submitted_at, status) " +
                "VALUES (?, ?, NULL, NULL, NOW(), 'Pending')");
            ps.setString(1, g[1]);
            ps.setString(2, g[2] != null && !g[2].equals("-") ? g[2] : null);
            ps.executeUpdate();

            PreparedStatement upd = conn.prepareStatement(
                "UPDATE guests SET status='Converted' WHERE id=?");
            upd.setInt(1, Integer.parseInt(g[0]));
            upd.executeUpdate();

            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                "Promoted guest to Pending Review: " + g[1]);
            loadGuests();
            ToastManager.success(g[1] + " moved to Pending Review.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to promote guest: " + e.getMessage());
            return false;
        }
    }

    /** Confirms, then marks the guest Dismissed. Returns true if dismissed. */
    private boolean dismissGuest(String[] g) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Dismiss Guest");
        confirm.setHeaderText("Dismiss " + g[1] + "?");
        confirm.setContentText("They stay in the guest list, marked Dismissed.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "UPDATE guests SET status='Dismissed' WHERE id=?");
            ps.setInt(1, Integer.parseInt(g[0]));
            ps.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                "Dismissed guest: " + g[1]);
            loadGuests();
            ToastManager.success("Guest \"" + g[1] + "\" dismissed.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to dismiss guest: " + e.getMessage());
            return false;
        }
    }

    private String nvl(String s) { return s != null ? s : "-"; }
}
