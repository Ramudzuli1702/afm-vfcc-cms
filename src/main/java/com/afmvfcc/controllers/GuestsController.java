package com.afmvfcc.controllers;

import com.afmvfcc.Main;
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

        colGuestActions.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn    = new Button("View");
            private final Button promoteBtn = new Button("Promote");
            private final Button dismissBtn = new Button("Dismiss");
            private final HBox   box        = new HBox(5, viewBtn, promoteBtn, dismissBtn);
            {
                viewBtn.getStyleClass().add("btn-secondary");
                viewBtn.setStyle("-fx-padding:3 8;-fx-font-size:10px;");
                promoteBtn.getStyleClass().add("btn-primary");
                promoteBtn.setStyle("-fx-padding:3 8;-fx-font-size:10px;");
                dismissBtn.getStyleClass().add("btn-danger");
                dismissBtn.setStyle("-fx-padding:3 8;-fx-font-size:10px;");
                box.setAlignment(Pos.CENTER_LEFT);
                viewBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        showGuestDetail(getTableView().getItems().get(idx));
                });
                promoteBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        promoteGuest(getTableView().getItems().get(idx));
                });
                dismissBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        dismissGuest(getTableView().getItems().get(idx));
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                    String status = getTableView().getItems().get(getIndex())[9];
                    boolean isGuest = "Guest".equals(status);
                    promoteBtn.setVisible(isGuest); promoteBtn.setManaged(isGuest);
                    dismissBtn.setVisible(isGuest); dismissBtn.setManaged(isGuest);
                }
                setGraphic(empty ? null : box);
            }
        });

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
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Guest Details - " + g[1]);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#F5F6FA;");

        HBox header = new HBox();
        header.setStyle("-fx-background-color:#FFFFFF;-fx-padding:18 24;" +
            "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        Label titleLbl = new Label(g[1]);
        titleLbl.setStyle("-fx-font-size:17px;-fx-font-weight:700;-fx-text-fill:#1E2130;");
        header.getChildren().add(titleLbl);

        VBox body = new VBox(12);
        body.setStyle("-fx-padding:20 24;");
        body.getChildren().addAll(
            detailRow("Phone",            g[2]),
            detailRow("Gender",           g[3]),
            detailRow("Sub-Branch",       g[4]),
            detailRow("Invited By",       g[5]),
            detailRow("Wants Membership", "1".equals(g[6]) ? "Yes" : "No"),
            detailRow("Visit Date",       g[8]),
            detailRow("Session",          g[10])
        );

        if (g[7] != null && !g[7].isEmpty()) {
            VBox prayBox = new VBox(6);
            prayBox.setStyle("-fx-background-color:#FFF8E8;-fx-border-color:#F0C040;" +
                "-fx-border-radius:8;-fx-background-radius:8;-fx-border-width:1;-fx-padding:12;");
            Label prayLabel = new Label("PRAYER REQUEST FOR THE BISHOP");
            prayLabel.setStyle("-fx-font-size:9px;-fx-font-weight:700;" +
                "-fx-text-fill:#B5862A;-fx-letter-spacing:1px;");
            Label prayText = new Label(g[7]);
            prayText.setStyle("-fx-text-fill:#1E2130;-fx-font-size:13px;");
            prayText.setWrapText(true);
            prayBox.getChildren().addAll(prayLabel, prayText);
            body.getChildren().add(prayBox);
        }

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("btn-secondary");
        HBox footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color:#FFFFFF;-fx-padding:14 24;" +
            "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");
        closeBtn.setOnAction(e -> stage.close());

        root.getChildren().addAll(header, body, footer);
        Scene scene = new Scene(root, 460, 500);
        scene.getStylesheets().add(
            getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
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

    private void promoteGuest(String[] g) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Promote Guest");
        confirm.setHeaderText("Send " + g[1] + " to Pending Review?");
        confirm.setContentText("This will add them to Pending Review for approval as a full member.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
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

            } catch (SQLException e) {
                e.printStackTrace();
                ToastManager.error("Failed to promote guest: " + e.getMessage());
            }
        });
    }

    private void dismissGuest(String[] g) {
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
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to dismiss guest: " + e.getMessage());
        }
    }

    private String nvl(String s) { return s != null ? s : "-"; }
}
