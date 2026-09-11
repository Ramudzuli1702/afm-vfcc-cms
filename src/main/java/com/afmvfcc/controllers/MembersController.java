package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.models.Member;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MembersController {

    // Members table
    @FXML private TableView<Member>   membersTable;
    @FXML private TableColumn<Member, String> colName, colPhone, colSubBranch,
            colMinistries, colAvailability, colStatus;
    @FXML private TableColumn<Member, Void>   colPhoto, colActions;

    // Families table
    @FXML private TableView<String[]> familiesTable;

    // Sub-branches table
    @FXML private TableView<String[]>  subBranchTable;
    @FXML private TableColumn<String[], String> colSbName, colSbMembers, colSbStatus;
    @FXML private TableColumn<String[], Void>   colSbActions;

    // Ministries table
    @FXML private TableView<String[]>  ministriesTable;

    // Guests tab
    @FXML private TableView<String[]>  guestsTable;
    @FXML private TableColumn<String[], String> colGuestName, colGuestPhone, colGuestGender,
            colGuestBranch, colGuestInvited, colGuestMembership, colGuestDate, colGuestStatus;
    @FXML private TableColumn<String[], Void>   colGuestActions;
    @FXML private TextField    guestSearchField;
    @FXML private ComboBox<String> filterGuestStatus;
    @FXML private Label        guestCountLabel;
    private GuestsController   guestsCtrl;

    @FXML private TableColumn<String[], String> colMinName, colMinDesc, colMinMembers, colMinStatus;
    @FXML private TableColumn<String[], Void>   colMinActions;
    @FXML private TableColumn<String[], String> colFamilyName, colFamilyMembers;
    @FXML private TableColumn<String[], Void>   colFamilyActions;

    // Pending table
    @FXML private TableView<String[]> pendingTable;
    @FXML private TableColumn<String[], String> colPendingName, colPendingPhone,
            colPendingBranch, colPendingMinistry, colPendingDate;
    @FXML private TableColumn<String[], Void>   colPendingActions;

    // Faithful Departed tab
    @FXML private TableView<String[]>  deceasedTable;
    @FXML private TableColumn<String[], String> colDecName, colDecDod, colDecSubBranch,
            colDecObituary, colDecRecorded;
    @FXML private TableColumn<String[], Void>   colDecActions;
    @FXML private TextField deceasedSearchField;
    @FXML private Tab       faithfulDepartedTab;
    private DeceasedMembersController deceasedCtrl;

    // Controls
    @FXML private TextField    searchField;
    @FXML private ComboBox<String> filterStatus, filterSubBranch, filterMinistry, filterGender;
    @FXML private Label        memberCountLabel;
    @FXML private HBox         pendingBanner;
    @FXML private Label        pendingBannerLabel;
    @FXML private TabPane      memberTabs;
    @FXML private Tab          pendingTab;

    private final ObservableList<Member> allMembers = FXCollections.observableArrayList();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // =========================================================
    // INIT
    // =========================================================

    @FXML
    public void initialize() {
        setupMembersTable();
        setupFamiliesTable();
        setupSubBranchesTable();
        setupMinistriesTable();
        setupPendingTable();

        guestsCtrl = new GuestsController();
        guestsCtrl.guestsTable       = guestsTable;
        guestsCtrl.colGuestName      = colGuestName;
        guestsCtrl.colGuestPhone     = colGuestPhone;
        guestsCtrl.colGuestGender    = colGuestGender;
        guestsCtrl.colGuestBranch    = colGuestBranch;
        guestsCtrl.colGuestInvited   = colGuestInvited;
        guestsCtrl.colGuestMembership = colGuestMembership;
        guestsCtrl.colGuestDate      = colGuestDate;
        guestsCtrl.colGuestStatus    = colGuestStatus;
        guestsCtrl.colGuestActions   = colGuestActions;
        guestsCtrl.guestSearchField  = guestSearchField;
        guestsCtrl.filterGuestStatus = filterGuestStatus;
        guestsCtrl.guestCountLabel   = guestCountLabel;
        guestsCtrl.initialize();
        if (guestSearchField != null)
            guestSearchField.setOnKeyReleased(e -> guestsCtrl.handleGuestSearch());

        deceasedCtrl = new DeceasedMembersController();
        deceasedCtrl.deceasedTable    = deceasedTable;
        deceasedCtrl.colDecName       = colDecName;
        deceasedCtrl.colDecDod        = colDecDod;
        deceasedCtrl.colDecSubBranch  = colDecSubBranch;
        deceasedCtrl.colDecObituary   = colDecObituary;
        deceasedCtrl.colDecRecorded   = colDecRecorded;
        deceasedCtrl.colDecActions    = colDecActions;
        deceasedCtrl.deceasedSearchField = deceasedSearchField;
        deceasedCtrl.onViewMember = memberId -> {
            try {
                Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT m.*, sb.name AS sub_branch_name, f.family_name " +
                    "FROM members m " +
                    "LEFT JOIN sub_branches sb ON sb.id = m.sub_branch_id " +
                    "LEFT JOIN families f ON f.id = m.family_id " +
                    "WHERE m.id = ?");
                ps.setInt(1, memberId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) openReadOnlyDialog(mapMember(rs));
            } catch (SQLException ex) { ex.printStackTrace(); }
        };
        deceasedCtrl.initialize();
        if (deceasedSearchField != null)
            deceasedSearchField.setOnKeyReleased(e -> deceasedCtrl.handleDeceasedSearch());

        loadFilters();
        loadMembers();
        loadFamilies();
        loadSubBranches();
        loadMinistries();
        loadPending();
    }

    // =========================================================
    // TABLE SETUP
    // =========================================================

    private void setupMembersTable() {
        colName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colSubBranch.setCellValueFactory(new PropertyValueFactory<>("subBranchName"));
        colMinistries.setCellValueFactory(d -> new SimpleStringProperty(loadMinistriesForMember(d.getValue().getId())));
        colAvailability.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAvailabilityLabel()));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatusLabel()));

        colStatus.setCellFactory(col -> new TableCell<Member, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label(item);
                badge.getStyleClass().add(item.equals("Active") ? "badge-active" : "badge-inactive");
                setGraphic(badge); setText(null);
            }
        });

        colAvailability.setCellFactory(col -> new TableCell<Member, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label(item);
                badge.getStyleClass().add(item.equals("Full Time") ? "badge-active" : "badge-pending");
                setGraphic(badge); setText(null);
            }
        });

        colActions.setCellFactory(col -> new TableCell<Member, Void>() {
            private final Button editBtn   = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox   box       = new HBox(6, editBtn, deleteBtn);
            {
                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                deleteBtn.getStyleClass().add("btn-danger");
                deleteBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                box.setAlignment(Pos.CENTER_LEFT);
                editBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        openAddEditDialog(getTableView().getItems().get(idx));
                });
                deleteBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        handleDelete(getTableView().getItems().get(idx));
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        membersTable.setItems(allMembers);
    }

    private void setupFamiliesTable() {
        colFamilyName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        colFamilyMembers.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));

        colFamilyActions.setCellFactory(col -> new TableCell<String[], Void>() {
            private final Button viewBtn   = new Button("View");
            private final Button deleteBtn = new Button("Delete");
            private final HBox   box       = new HBox(6, viewBtn, deleteBtn);
            {
                viewBtn.getStyleClass().add("btn-secondary");
                viewBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                deleteBtn.getStyleClass().add("btn-danger");
                deleteBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                box.setAlignment(Pos.CENTER_LEFT);

                viewBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        String[] row = getTableView().getItems().get(idx);
                        openFamilyDetailDialog(Integer.parseInt(row[2]), row[0]);
                    }
                });
                deleteBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        deleteFamilyById(Integer.parseInt(getTableView().getItems().get(idx)[2]));
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        // Double-click a row to open family detail
        familiesTable.setRowFactory(tv -> {
            TableRow<String[]> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    String[] data = row.getItem();
                    openFamilyDetailDialog(Integer.parseInt(data[2]), data[0]);
                }
            });
            return row;
        });
    }

    private void setupPendingTable() {
        colPendingName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        colPendingPhone.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        colPendingBranch.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        colPendingMinistry.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[3]));
        colPendingDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[4]));

        colPendingActions.setCellFactory(col -> new TableCell<String[], Void>() {
            private final Button approveBtn = new Button("Approve");
            private final Button rejectBtn  = new Button("Reject");
            private final HBox   box        = new HBox(6, approveBtn, rejectBtn);
            {
                approveBtn.getStyleClass().add("btn-primary");
                approveBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                rejectBtn.getStyleClass().add("btn-danger");
                rejectBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                approveBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        String[] row = getTableView().getItems().get(idx);
                        approvePending(Integer.parseInt(row[5]), row[0], row[1], row[2]);
                    }
                });
                rejectBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        rejectPending(Integer.parseInt(getTableView().getItems().get(idx)[5]));
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    // =========================================================
    // FAMILY DETAIL DIALOG
    // =========================================================

    /**
     * Opens a modal dialog showing all members of the given family.
     * Each member card has a "View / Edit" button that opens the
     * standard add_edit_member dialog.
     */
    private void openFamilyDetailDialog(int familyId, String familyName) {
        List<Member> familyMembers = loadFamilyMembers(familyId);

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Family — " + familyName);

        // ── Header ──────────────────────────────────────────────
        HBox header = new HBox();
        header.setStyle("-fx-background-color:#FFFFFF;-fx-padding:18 24;" +
                "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        VBox headerText = new VBox(2);
        Label titleLbl = new Label(familyName);
        titleLbl.setStyle("-fx-font-size:17px;-fx-font-weight:700;-fx-text-fill:#1E2130;");
        Label subLbl = new Label(familyMembers.size() + " member" + (familyMembers.size() != 1 ? "s" : ""));
        subLbl.setStyle("-fx-font-size:12px;-fx-text-fill:#9099AA;");
        headerText.getChildren().addAll(titleLbl, subLbl);
        header.getChildren().add(headerText);

        // ── Member cards ─────────────────────────────────────────
        VBox cardContainer = new VBox(10);
        cardContainer.setPadding(new Insets(16));

        if (familyMembers.isEmpty()) {
            Label empty = new Label("No members in this family yet.");
            empty.setStyle("-fx-text-fill:#9099AA;-fx-font-size:13px;");
            cardContainer.getChildren().add(empty);
        } else {
            for (Member m : familyMembers) {
                cardContainer.getChildren().add(buildMemberCard(m, stage));
            }
        }

        ScrollPane scroll = new ScrollPane(cardContainer);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:transparent;-fx-background:transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // ── Footer ───────────────────────────────────────────────
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color:#FFFFFF;-fx-padding:12 24;" +
                "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");
        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("btn-secondary");
        closeBtn.setOnAction(e -> stage.close());
        footer.getChildren().add(closeBtn);

        // ── Root ─────────────────────────────────────────────────
        VBox root = new VBox(0, header, scroll, footer);
        root.setStyle("-fx-background-color:#F5F6FA;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Scene scene = new Scene(root, 560, 540);
        scene.setFill(Color.web("#F5F6FA"));
        scene.getStylesheets().add(
                getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();

        // Refresh in case any member was edited
        loadMembers();
        loadFamilies();
    }

    /**
     * Builds a single member card for the family detail dialog.
     */
    private HBox buildMemberCard(Member m, Stage parentStage) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color:#FFFFFF;-fx-background-radius:10;" +
                "-fx-border-color:#DDE1EA;-fx-border-radius:10;-fx-padding:14 16;");

        // Avatar / Initials
        Label avatar = new Label(m.getFullName().substring(0, 1).toUpperCase());
        avatar.setStyle("-fx-background-color:#E8EAF6;-fx-background-radius:24;" +
                "-fx-min-width:44;-fx-min-height:44;-fx-max-width:44;-fx-max-height:44;" +
                "-fx-alignment:center;-fx-font-weight:700;-fx-font-size:16px;" +
                "-fx-text-fill:#1A237E;");

        // Info block
        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLbl = new Label(m.getFullName() + (m.isDeceased() ? "  †" : ""));
        nameLbl.setStyle("-fx-font-size:14px;-fx-font-weight:700;-fx-text-fill:#1E2130;");

        // Detail line: phone · sub-branch · gender
        List<String> details = new ArrayList<>();
        if (m.getPhone() != null && !m.getPhone().isBlank())
            details.add("📞 " + m.getPhone());
        if (m.getSubBranchName() != null && !m.getSubBranchName().isBlank())
            details.add("🏠 " + m.getSubBranchName());
        if (m.getGender() != null && !m.getGender().isBlank())
            details.add(m.getGender());

        Label detailLbl = new Label(String.join("   ·   ", details));
        detailLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#5A6275;");

        // Ministry line
        String ministries = loadMinistriesForMember(m.getId());
        if (!ministries.equals("-")) {
            Label minLbl = new Label("⛪ " + ministries);
            minLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#D4A017;-fx-font-weight:600;");
            info.getChildren().addAll(nameLbl, detailLbl, minLbl);
        } else {
            info.getChildren().addAll(nameLbl, detailLbl);
        }

        // Status badge
        Label statusBadge = new Label(m.isActive() ? "Active" : "Inactive");
        statusBadge.getStyleClass().add(m.isActive() ? "badge-active" : "badge-inactive");

        // View/Edit button — opens the full member dialog
        Button editBtn = new Button(m.isDeceased() ? "View" : "View / Edit");
        editBtn.getStyleClass().add("btn-secondary");
        editBtn.setStyle("-fx-padding:6 14;-fx-font-size:11px;");
        editBtn.setOnAction(e -> {
            if (m.isDeceased()) openReadOnlyDialog(m);
            else {
                openAddEditDialog(m);
                nameLbl.setText(m.getFullName());
            }
        });

        card.getChildren().addAll(avatar, info, statusBadge, editBtn);
        return card;
    }

    /**
     * Loads all non-deleted members of a given family from the database.
     */
    private List<Member> loadFamilyMembers(int familyId) {
        List<Member> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT m.*, sb.name AS sub_branch_name, f.family_name " +
                    "FROM members m " +
                    "LEFT JOIN sub_branches sb ON sb.id = m.sub_branch_id " +
                    "LEFT JOIN families f ON f.id = m.family_id " +
                    "WHERE m.family_id = ? AND m.is_deleted = 0 " +
                    "ORDER BY m.is_deceased ASC, m.full_name ASC");
            ps.setInt(1, familyId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                list.add(mapMember(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // =========================================================
    // DATA LOADING
    // =========================================================

    private void loadFilters() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            filterStatus.setItems(FXCollections.observableArrayList(
                    "All", "Active", "Inactive", "Full Time", "Part Time"));
            filterStatus.setValue("All");

            List<String> branches = new ArrayList<>();
            branches.add("All");
            ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT name FROM sub_branches WHERE is_active=1 ORDER BY name");
            while (rs.next()) branches.add(rs.getString("name"));
            filterSubBranch.setItems(FXCollections.observableArrayList(branches));
            filterSubBranch.setValue("All");

            List<String> ministries = new ArrayList<>();
            ministries.add("All");
            rs = conn.createStatement()
                    .executeQuery("SELECT name FROM ministries WHERE is_active=1 ORDER BY name");
            while (rs.next()) ministries.add(rs.getString("name"));
            filterMinistry.setItems(FXCollections.observableArrayList(ministries));
            filterMinistry.setValue("All");

            filterGender.setItems(FXCollections.observableArrayList("All", "Male", "Female"));
            filterGender.setValue("All");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadMembers() {
        allMembers.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT m.*, sb.name AS sub_branch_name, f.family_name " +
                    "FROM members m " +
                    "LEFT JOIN sub_branches sb ON sb.id = m.sub_branch_id " +
                    "LEFT JOIN families f ON f.id = m.family_id " +
                    "WHERE m.is_deleted = 0 AND m.is_deceased = 0 " +
                    "ORDER BY m.full_name ASC");
            while (rs.next()) allMembers.add(mapMember(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        applyFilters();
    }

    private void loadFamilies() {
        ObservableList<String[]> families = FXCollections.observableArrayList();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT id, family_name FROM families ORDER BY family_name");
            while (rs.next()) {
                int id = rs.getInt("id");
                families.add(new String[] {
                        rs.getString("family_name"),
                        loadFamilyMemberNames(conn, id),
                        String.valueOf(id)
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        familiesTable.setItems(families);
    }

    private String loadFamilyMemberNames(Connection conn, int famId) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT full_name, is_deceased FROM members " +
                "WHERE family_id=? AND is_deleted=0 ORDER BY full_name");
        ps.setInt(1, famId);
        ResultSet rs = ps.executeQuery();
        List<String> names = new ArrayList<>();
        while (rs.next())
            names.add(rs.getString("full_name") + (rs.getInt("is_deceased") == 1 ? " †" : ""));
        return names.isEmpty() ? "No members" : String.join(", ", names);
    }

    private void loadPending() {
        ObservableList<String[]> pending = FXCollections.observableArrayList();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT pm.id, pm.full_name, pm.phone, " +
                    "IFNULL(sb.name,'Unknown') AS branch, " +
                    "IFNULL(mi.name,'Unknown') AS ministry, " +
                    "DATE_FORMAT(pm.submitted_at,'%d %b %Y') AS submitted " +
                    "FROM pending_members pm " +
                    "LEFT JOIN sub_branches sb ON sb.id = pm.sub_branch_id " +
                    "LEFT JOIN ministries mi ON mi.id = pm.ministry_id " +
                    "WHERE pm.status='Pending' ORDER BY pm.submitted_at ASC");
            while (rs.next()) {
                pending.add(new String[] {
                        rs.getString("full_name"),
                        rs.getString("phone") != null ? rs.getString("phone") : "-",
                        rs.getString("branch"),
                        rs.getString("ministry"),
                        rs.getString("submitted") != null ? rs.getString("submitted") : "-",
                        rs.getString("id")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        pendingTable.setItems(pending);
        int count = pending.size();
        if (count > 0) {
            pendingBanner.setVisible(true);
            pendingBanner.setManaged(true);
            pendingBannerLabel.setText(count + " new member(s) pending approval");
            pendingBadge(count);
        } else {
            pendingBanner.setVisible(false);
            pendingBanner.setManaged(false);
        }
    }

    // =========================================================
    // SEARCH AND FILTER
    // =========================================================

    @FXML public void handleSearch() { applyFilters(); }
    @FXML public void handleFilter() { applyFilters(); }

    private void applyFilters() {
        String search   = searchField.getText().toLowerCase().trim();
        String status   = filterStatus.getValue();
        String branch   = filterSubBranch.getValue();
        String ministry = filterMinistry.getValue();
        String gender   = filterGender.getValue();

        List<Member> filtered = new ArrayList<>();
        for (Member m : allMembers) {
            if (!search.isEmpty() &&
                    !m.getFullName().toLowerCase().contains(search) &&
                    !(m.getPhone() != null && m.getPhone().contains(search)) &&
                    !(m.getEmail() != null && m.getEmail().toLowerCase().contains(search)))
                continue;
            if (status != null && !"All".equals(status)) {
                if ("Active".equals(status)    && !m.isActive())   continue;
                if ("Inactive".equals(status)  && m.isActive())    continue;
                if ("Full Time".equals(status) && !m.isFullTime()) continue;
                if ("Part Time".equals(status) && m.isFullTime())  continue;
            }
            if (branch != null && !"All".equals(branch) &&
                    (m.getSubBranchName() == null || !m.getSubBranchName().equals(branch)))
                continue;
            if (ministry != null && !"All".equals(ministry) &&
                    !loadMinistriesForMember(m.getId()).contains(ministry))
                continue;
            if (gender != null && !"All".equals(gender) &&
                    (m.getGender() == null || !m.getGender().equalsIgnoreCase(gender)))
                continue;
            filtered.add(m);
        }
        membersTable.setItems(FXCollections.observableArrayList(filtered));
        memberCountLabel.setText(filtered.size() + " member(s)");
    }

    // =========================================================
    // CRUD ACTIONS
    // =========================================================

    @FXML public void handleAdd() { openAddEditDialog(null); }

    @FXML
    public void handleTableClick(javafx.scene.input.MouseEvent e) {
        if (e.getClickCount() == 2) {
            Member selected = membersTable.getSelectionModel().getSelectedItem();
            if (selected != null) openAddEditDialog(selected);
        }
    }

    private void openAddEditDialog(Member member) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/afmvfcc/fxml/add_edit_member.fxml"));
            VBox root = loader.load();
            AddEditMemberController ctrl = loader.getController();
            ctrl.setMember(member);
            ctrl.setOnSaved(this::loadMembers);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(member == null ? "Add New Member" : "Edit Member");
            Scene scene = new Scene(root, 700, 720);
            scene.setFill(Color.web("#F5F6FA"));
            scene.getStylesheets().add(
                    getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openReadOnlyDialog(Member member) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/afmvfcc/fxml/add_edit_member.fxml"));
            VBox root = loader.load();
            AddEditMemberController ctrl = loader.getController();
            ctrl.setMember(member);
            ctrl.setReadOnly();
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("View Member — " + member.getFullName());
            Scene scene = new Scene(root, 700, 720);
            scene.setFill(Color.web("#F5F6FA"));
            scene.getStylesheets().add(
                    getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDelete(Member m) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Delete Member");
        confirm.setHeaderText("Delete " + m.getFullName() + "?");
        confirm.setContentText("This member will be soft-deleted and removed from all views.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE members SET is_deleted=1 WHERE id=?");
                    ps.setInt(1, m.getId());
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                            "Deleted member: " + m.getFullName());
                    loadMembers();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // =========================================================
    // PENDING ACTIONS
    // =========================================================

    private void approvePending(int pendingId, String name, String phone, String branch) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement getPending = conn.prepareStatement(
                    "SELECT * FROM pending_members WHERE id=?");
            getPending.setInt(1, pendingId);
            ResultSet pm = getPending.executeQuery();
            if (!pm.next()) return;

            PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO members (full_name, phone, sub_branch_id, " +
                    "is_active, is_full_time, is_deleted) VALUES (?,?,?,1,1,0)",
                    Statement.RETURN_GENERATED_KEYS);
            ins.setString(1, pm.getString("full_name"));
            ins.setString(2, pm.getString("phone"));
            if (pm.getObject("sub_branch_id") != null) ins.setInt(3, pm.getInt("sub_branch_id"));
            else ins.setNull(3, Types.INTEGER);
            ins.executeUpdate();

            ResultSet keys = ins.getGeneratedKeys();
            if (keys.next()) {
                int newMemberId = keys.getInt(1);
                if (pm.getObject("ministry_id") != null) {
                    PreparedStatement mmPs = conn.prepareStatement(
                            "INSERT INTO member_ministries (member_id, ministry_id) VALUES (?,?)");
                    mmPs.setInt(1, newMemberId);
                    mmPs.setInt(2, pm.getInt("ministry_id"));
                    mmPs.executeUpdate();
                }
            }

            PreparedStatement updPs = conn.prepareStatement(
                    "UPDATE pending_members SET status='Approved', " +
                    "reviewed_by=?, reviewed_at=NOW() WHERE id=?");
            updPs.setInt(1, SessionManager.getInstance().getCurrentUser().getId());
            updPs.setInt(2, pendingId);
            updPs.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    "Approved pending member: " + name);
            loadMembers();
            loadPending();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void rejectPending(int pendingId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE pending_members SET status='Rejected', " +
                    "reviewed_by=?, reviewed_at=NOW() WHERE id=?");
            ps.setInt(1, SessionManager.getInstance().getCurrentUser().getId());
            ps.setInt(2, pendingId);
            ps.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    "Rejected pending member ID " + pendingId);
            loadPending();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void deleteFamilyById(int id) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Delete Family");
        confirm.setHeaderText("Delete this family?");
        confirm.setContentText("Members in this family will not be deleted, just unlinked.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    Connection conn = DatabaseConnection.getConnection();
                    conn.createStatement().executeUpdate(
                            "UPDATE members SET family_id=NULL WHERE family_id=" + id);
                    conn.createStatement().executeUpdate(
                            "DELETE FROM families WHERE id=" + id);
                    loadFamilies();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // =========================================================
    // PRINT
    // =========================================================

    @FXML public void handlePrint() { MemberPdfExporter.exportAll(); }

    // =========================================================
    // FAITHFUL DEPARTED
    // =========================================================

    @FXML public void handleDeceasedSearch() {
        if (deceasedCtrl != null) deceasedCtrl.handleDeceasedSearch();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private Member mapMember(ResultSet rs) throws SQLException {
        Member m = new Member();
        m.setId(rs.getInt("id"));
        m.setFullName(rs.getString("full_name"));
        m.setGender(rs.getString("gender"));
        m.setAddress(rs.getString("address"));
        m.setPhone(rs.getString("phone"));
        m.setEmail(rs.getString("email"));
        m.setSubBranchId(rs.getInt("sub_branch_id"));
        m.setSubBranchName(rs.getString("sub_branch_name"));
        m.setFamilyId(rs.getInt("family_id") == 0 ? null : rs.getInt("family_id"));
        m.setFamilyName(rs.getString("family_name"));
        m.setPhotoPath(rs.getString("photo_path"));
        m.setFullTime(rs.getInt("is_full_time") == 1);
        m.setActive(rs.getInt("is_active") == 1);
        m.setDeleted(rs.getInt("is_deleted") == 1);
        if (rs.getDate("date_of_birth") != null)
            m.setDateOfBirth(rs.getDate("date_of_birth").toLocalDate());
        if (rs.getDate("baptism_date") != null)
            m.setBaptismDate(rs.getDate("baptism_date").toLocalDate());
        m.setMaritalStatus(rs.getString("marital_status"));
        m.setSpouseMember(rs.getInt("is_spouse_member") == 1);
        if (rs.getObject("spouse_member_id") != null)
            m.setSpouseMemberId(rs.getInt("spouse_member_id"));
        m.setEmploymentStatus(rs.getString("employment_status"));
        if (rs.getDate("date_joined") != null)
            m.setDateJoined(rs.getDate("date_joined").toLocalDate());
        m.setNextOfKinName(rs.getString("next_of_kin_name"));
        m.setNextOfKinPhone(rs.getString("next_of_kin_phone"));
        // Map is_deceased if present
        try { m.setDeceased(rs.getInt("is_deceased") == 1); } catch (SQLException ignored) {}
        return m;
    }

    private String loadMinistriesForMember(int memberId) {
        try {
            PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(
                    "SELECT m.name FROM ministries m " +
                    "JOIN member_ministries mm ON mm.ministry_id = m.id " +
                    "WHERE mm.member_id=?");
            ps.setInt(1, memberId);
            ResultSet rs = ps.executeQuery();
            List<String> names = new ArrayList<>();
            while (rs.next()) names.add(rs.getString("name"));
            return names.isEmpty() ? "-" : String.join(", ", names);
        } catch (SQLException e) {
            return "-";
        }
    }

    private void pendingBadge(int count) {
        if (pendingTab != null)
            pendingTab.setText("Pending (" + count + ")");
    }

    @FXML
    public void handleAddFamily() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Family");
        dialog.setHeaderText("Create a new family group");
        dialog.setContentText("Family name:");
        Main.applyStyles(dialog.getDialogPane());
        dialog.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) return;
            try {
                Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO families (family_name) VALUES (?)");
                ps.setString(1, name.trim());
                ps.executeUpdate();
                AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Created family: " + name.trim());
                loadFamilies();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @FXML public void showPendingTab() {
        if (memberTabs != null) memberTabs.getSelectionModel().select(pendingTab);
    }

    // =========================================================
    // SUB-BRANCHES
    // =========================================================

    private void setupSubBranchesTable() {
        colSbName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        colSbMembers.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        colSbStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        colSbStatus.setCellFactory(col -> new TableCell<String[], String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label b = new Label("1".equals(item) ? "Active" : "Inactive");
                b.getStyleClass().add("1".equals(item) ? "badge-active" : "badge-inactive");
                setGraphic(b); setText(null);
            }
        });
        colSbActions.setCellFactory(col -> new TableCell<String[], Void>() {
            private final Button editBtn   = new Button("Edit");
            private final Button toggleBtn = new Button("Deactivate");
            private final HBox   box       = new HBox(6, editBtn, toggleBtn);
            {
                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                toggleBtn.getStyleClass().add("btn-danger");
                toggleBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                editBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        String[] row = getTableView().getItems().get(idx);
                        editSubBranch(Integer.parseInt(row[3]), row[0]);
                    }
                });
                toggleBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        String[] row = getTableView().getItems().get(idx);
                        toggleSubBranch(Integer.parseInt(row[3]), row[2]);
                    }
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                    String status = getTableView().getItems().get(getIndex())[2];
                    toggleBtn.setText("1".equals(status) ? "Deactivate" : "Activate");
                    toggleBtn.getStyleClass().setAll("1".equals(status) ? "btn-danger" : "btn-secondary");
                }
                setGraphic(empty ? null : box);
            }
        });
    }

    private void loadSubBranches() {
        if (subBranchTable == null) return;
        ObservableList<String[]> items = FXCollections.observableArrayList();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT sb.id, sb.name, sb.is_active, " +
                    "(SELECT COUNT(*) FROM members m " +
                    " WHERE m.sub_branch_id=sb.id AND m.is_deleted=0 AND m.is_deceased=0) AS cnt " +
                    "FROM sub_branches sb ORDER BY sb.name");
            while (rs.next()) {
                items.add(new String[] {
                        rs.getString("name"), rs.getString("cnt"),
                        rs.getString("is_active"), rs.getString("id")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        subBranchTable.setItems(items);
    }

    @FXML
    public void handleAddSubBranch() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Add Sub-Branch");
        dlg.setHeaderText("Create a new sub-branch");
        dlg.setContentText("Sub-branch name:");
        Main.applyStyles(dlg.getDialogPane());
        dlg.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) return;
            try {
                Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO sub_branches (name, is_active) VALUES (?,1)");
                ps.setString(1, name.trim());
                ps.executeUpdate();
                AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Created sub-branch: " + name.trim());
                loadSubBranches();
                loadFilters();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void editSubBranch(int id, String currentName) {
        TextInputDialog dlg = new TextInputDialog(currentName);
        dlg.setTitle("Edit Sub-Branch");
        dlg.setHeaderText("Rename sub-branch");
        dlg.setContentText("New name:");
        Main.applyStyles(dlg.getDialogPane());
        dlg.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) return;
            try {
                Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE sub_branches SET name=? WHERE id=?");
                ps.setString(1, name.trim());
                ps.setInt(2, id);
                ps.executeUpdate();
                AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Renamed sub-branch ID " + id + " to: " + name.trim());
                loadSubBranches();
                loadFilters();
                loadMembers();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void toggleSubBranch(int id, String currentStatus) {
        int newStatus = "1".equals(currentStatus) ? 0 : 1;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE sub_branches SET is_active=? WHERE id=?");
            ps.setInt(1, newStatus);
            ps.setInt(2, id);
            ps.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    (newStatus == 1 ? "Activated" : "Deactivated") + " sub-branch ID " + id);
            loadSubBranches();
            loadFilters();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // =========================================================
    // MINISTRIES
    // =========================================================

    private void setupMinistriesTable() {
        colMinName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        colMinDesc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1] != null ? d.getValue()[1] : "-"));
        colMinMembers.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        colMinStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[3]));
        colMinStatus.setCellFactory(col -> new TableCell<String[], String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label b = new Label("1".equals(item) ? "Active" : "Inactive");
                b.getStyleClass().add("1".equals(item) ? "badge-active" : "badge-inactive");
                setGraphic(b); setText(null);
            }
        });
        colMinActions.setCellFactory(col -> new TableCell<String[], Void>() {
            private final Button editBtn   = new Button("Edit");
            private final Button toggleBtn = new Button("Deactivate");
            private final HBox   box       = new HBox(6, editBtn, toggleBtn);
            {
                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                toggleBtn.getStyleClass().add("btn-danger");
                toggleBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                editBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        String[] row = getTableView().getItems().get(idx);
                        editMinistry(Integer.parseInt(row[4]), row[0], row[1]);
                    }
                });
                toggleBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        String[] row = getTableView().getItems().get(idx);
                        toggleMinistry(Integer.parseInt(row[4]), row[3]);
                    }
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                    String status = getTableView().getItems().get(getIndex())[3];
                    toggleBtn.setText("1".equals(status) ? "Deactivate" : "Activate");
                    toggleBtn.getStyleClass().setAll("1".equals(status) ? "btn-danger" : "btn-secondary");
                }
                setGraphic(empty ? null : box);
            }
        });
    }

    private void loadMinistries() {
        if (ministriesTable == null) return;
        ObservableList<String[]> items = FXCollections.observableArrayList();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT mi.id, mi.name, mi.description, mi.is_active, " +
                    "(SELECT COUNT(*) FROM member_ministries mm " +
                    " JOIN members m ON mm.member_id=m.id " +
                    " WHERE mm.ministry_id=mi.id AND m.is_deleted=0 AND m.is_deceased=0) AS cnt " +
                    "FROM ministries mi ORDER BY mi.name");
            while (rs.next()) {
                items.add(new String[] {
                        rs.getString("name"), rs.getString("description"),
                        rs.getString("cnt"), rs.getString("is_active"), rs.getString("id")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        ministriesTable.setItems(items);
    }

    @FXML public void handleAddMinistry() { openMinistryDialog(0, "", ""); }

    private void editMinistry(int id, String name, String desc) {
        openMinistryDialog(id, name, desc != null ? desc : "");
    }

    private void openMinistryDialog(int id, String existingName, String existingDesc) {
        Stage stage = new Stage();
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.setTitle(id == 0 ? "Add Ministry" : "Edit Ministry");

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#F5F6FA;");

        HBox header = new HBox();
        header.setStyle("-fx-background-color:#FFFFFF;-fx-padding:18 24;" +
                "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        Label titleLbl = new Label(id == 0 ? "Add Ministry" : "Edit Ministry");
        titleLbl.setStyle("-fx-font-size:16px;-fx-font-weight:700;-fx-text-fill:#1E2130;");
        header.getChildren().add(titleLbl);

        VBox body = new VBox(14);
        body.setStyle("-fx-padding:20 24;");

        TextField nameField = new TextField(existingName);
        nameField.getStyleClass().add("form-field");
        nameField.setPromptText("Ministry name");
        nameField.setMaxWidth(Double.MAX_VALUE);

        TextArea descField = new TextArea(existingDesc);
        descField.getStyleClass().add("form-textarea");
        descField.setPromptText("Description (optional)");
        descField.setPrefHeight(80);
        descField.setMaxWidth(Double.MAX_VALUE);

        VBox nameRow = new VBox(6);
        Label nameLbl = new Label("MINISTRY NAME *");
        nameLbl.getStyleClass().add("form-label");
        nameRow.getChildren().addAll(nameLbl, nameField);

        VBox descRow = new VBox(6);
        Label descLbl = new Label("DESCRIPTION");
        descLbl.getStyleClass().add("form-label");
        descRow.getChildren().addAll(descLbl, descField);

        body.getChildren().addAll(nameRow, descRow);

        Button saveBtn   = new Button(id == 0 ? "Add Ministry" : "Save Changes");
        Button cancelBtn = new Button("Cancel");
        saveBtn.getStyleClass().add("btn-primary");
        cancelBtn.getStyleClass().add("btn-secondary");
        HBox footer = new HBox(10, cancelBtn, saveBtn);
        footer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color:#FFFFFF;-fx-padding:14 24;" +
                "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");

        root.getChildren().addAll(header, body, footer);
        cancelBtn.setOnAction(e -> stage.close());

        saveBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) return;
            try {
                Connection conn = DatabaseConnection.getConnection();
                if (id == 0) {
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO ministries (name, description, is_active) VALUES (?,?,1)");
                    ps.setString(1, name);
                    ps.setString(2, descField.getText().trim());
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                            "Created ministry: " + name);
                } else {
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE ministries SET name=?, description=? WHERE id=?");
                    ps.setString(1, name);
                    ps.setString(2, descField.getText().trim());
                    ps.setInt(3, id);
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                            "Updated ministry: " + name);
                }
                loadMinistries();
                loadFilters();
                stage.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 460, 320);
        scene.setFill(Color.web("#F5F6FA"));
        scene.getStylesheets().add(
                getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    private void toggleMinistry(int id, String currentStatus) {
        int newStatus = "1".equals(currentStatus) ? 0 : 1;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ministries SET is_active=? WHERE id=?");
            ps.setInt(1, newStatus);
            ps.setInt(2, id);
            ps.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    (newStatus == 1 ? "Activated" : "Deactivated") + " ministry ID " + id);
            loadMinistries();
            loadFilters();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}