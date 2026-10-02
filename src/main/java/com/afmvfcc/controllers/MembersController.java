package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.models.Member;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.Avatars;
import com.afmvfcc.utils.Dialogs;
import com.afmvfcc.utils.DocumentViewer;
import com.afmvfcc.utils.FormBuilder;
import com.afmvfcc.utils.Icons;
import com.afmvfcc.utils.TableActions;
import com.afmvfcc.utils.SessionManager;
import com.afmvfcc.utils.ToastManager;
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

    // Stat cards
    @FXML private Label statTotal, statActive, statPending, statGuests;

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
                if (rs.next()) showMember(mapMember(rs));
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

        colPhoto.setCellFactory(col -> new TableCell<Member, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                Member m = empty || getTableRow() == null ? null : getTableRow().getItem();
                setGraphic(m == null ? null : Avatars.of(m.getPhotoPath(), 30));
                setText(null);
            }
        });
        TableActions.viewOnly(membersTable, colActions, this::showMember);

        membersTable.setItems(allMembers);
    }

    private void setupFamiliesTable() {
        colFamilyName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        colFamilyMembers.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));

        TableActions.viewOnly(familiesTable, colFamilyActions,
            row -> openFamilyDetailDialog(Integer.parseInt(row[2]), row[0]));
    }

    private void setupPendingTable() {
        colPendingName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        colPendingPhone.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        colPendingBranch.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        colPendingMinistry.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[3]));
        colPendingDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[4]));

        TableActions.viewOnly(pendingTable, colPendingActions, this::showPending);
    }

    private void showPending(String[] row) {
        new DocumentViewer("Pending Member", row[0])
            .icon("fas-user-clock")
            .status("Awaiting approval", "badge-pending")
            .width(620)
            .meta("Phone",      row[1])
            .meta("Sub-Branch", row[2])
            .meta("Ministry",   row[3])
            .meta("Submitted",  row[4])
            .closingAction("Reject", "fas-times", "btn-danger", () -> rejectPending(Integer.parseInt(row[5]), row[0]))
            .closingAction("Approve as Member", "fas-check", "btn-primary",
                () -> approvePending(Integer.parseInt(row[5]), row[0], row[1], row[2]))
            .show();
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
        com.afmvfcc.utils.Icons.setWindowIcon(stage, "fas-home");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Family — " + familyName);

        // ── Header ──────────────────────────────────────────────
        HBox header = new HBox();
        header.setStyle("-fx-background-color:#FFFFFF;-fx-padding:16 28;" +
                "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        VBox headerText = new VBox(2);
        Label titleLbl = new Label(familyName);
        titleLbl.setStyle("-fx-font-size:18px;-fx-font-weight:700;-fx-text-fill:#1E2130;");
        Label subLbl = new Label(familyMembers.size() + " member" + (familyMembers.size() != 1 ? "s" : ""));
        subLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#9099AA;");
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
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color:#FFFFFF;-fx-padding:14 28;" +
                "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");
        Button deleteBtn = new Button("Delete Family");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setGraphic(Icons.of("fas-trash-alt", 13, Icons.RED));
        deleteBtn.setGraphicTextGap(8);
        deleteBtn.setOnAction(e -> { if (deleteFamilyById(familyId)) stage.close(); });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("btn-secondary");
        closeBtn.setOnAction(e -> stage.close());
        footer.getChildren().addAll(deleteBtn, spacer, closeBtn);

        // ── Root ─────────────────────────────────────────────────
        VBox root = new VBox(0, header, scroll, footer);
        root.setStyle("-fx-background-color:#F5F6FA;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Dialogs.ownByActiveWindow(stage);
        stage.setScene(Dialogs.fittedScene(root, scroll, 600));
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

        StackPane avatar = Avatars.of(m.getPhotoPath(), 44);

        // Info block
        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLbl = new Label(m.getFullName() + (m.isDeceased() ? "  †" : ""));
        nameLbl.setStyle("-fx-font-size:14px;-fx-font-weight:700;-fx-text-fill:#1E2130;");

        // Detail line: phone · sub-branch · gender
        HBox detailRow = new HBox(6);
        detailRow.setAlignment(Pos.CENTER_LEFT);
        boolean firstDetail = true;
        if (m.getPhone() != null && !m.getPhone().isBlank()) {
            detailRow.getChildren().add(detailChip("fas-phone", m.getPhone()));
            firstDetail = false;
        }
        if (m.getSubBranchName() != null && !m.getSubBranchName().isBlank()) {
            if (!firstDetail) detailRow.getChildren().add(detailDot());
            detailRow.getChildren().add(detailChip("fas-home", m.getSubBranchName()));
            firstDetail = false;
        }
        if (m.getGender() != null && !m.getGender().isBlank()) {
            if (!firstDetail) detailRow.getChildren().add(detailDot());
            Label genderLbl = new Label(m.getGender());
            genderLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#5A6275;");
            detailRow.getChildren().add(genderLbl);
        }

        // Ministry line
        String ministries = loadMinistriesForMember(m.getId());
        if (!ministries.equals("-")) {
            HBox minRow = new HBox(5);
            minRow.setAlignment(Pos.CENTER_LEFT);
            javafx.scene.Node minIcon = Icons.of("fas-church", 11, "#D4A017");
            Label minLbl = new Label(ministries);
            minLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#D4A017;-fx-font-weight:600;");
            minRow.getChildren().addAll(minIcon, minLbl);
            info.getChildren().addAll(nameLbl, detailRow, minRow);
        } else {
            info.getChildren().addAll(nameLbl, detailRow);
        }

        // Status badge
        Label statusBadge = new Label(m.isActive() ? "Active" : "Inactive");
        statusBadge.getStyleClass().add(m.isActive() ? "badge-active" : "badge-inactive");

        // View — opens the member's read-only profile (Edit is inside it)
        Button editBtn = new Button("View");
        editBtn.getStyleClass().add("btn-secondary");
        editBtn.setStyle("-fx-padding:6 14;-fx-font-size:11px;");
        editBtn.setGraphic(Icons.of("fas-eye", 11, Icons.NAVY));
        editBtn.setOnAction(e -> {
            showMember(m);
            nameLbl.setText(m.getFullName());
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
        refreshStats();
    }

    private void refreshStats() {
        statTotal.setText(String.valueOf(allMembers.size()));
        statActive.setText(String.valueOf(allMembers.stream().filter(Member::isActive).count()));
        statPending.setText(String.valueOf(pendingTable.getItems() != null ? pendingTable.getItems().size() : 0));
        try {
            ResultSet rs = DatabaseConnection.getConnection().createStatement()
                    .executeQuery("SELECT COUNT(*) FROM guests WHERE status='Guest'");
            statGuests.setText(rs.next() ? rs.getString(1) : "0");
        } catch (SQLException e) {
            e.printStackTrace();
        }
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
        refreshStats();
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

    private void openAddEditDialog(Member member) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/afmvfcc/fxml/add_edit_member.fxml"));
            VBox root = loader.load();
            AddEditMemberController ctrl = loader.getController();
            ctrl.setMember(member);
            ctrl.setOnSaved(() -> { loadMembers(); loadFamilies(); });
            Stage stage = new Stage();
            com.afmvfcc.utils.Icons.setWindowIcon(stage, member == null ? "fas-user-plus" : "fas-user-edit");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(member == null ? "Add New Member" : "Edit Member");
            Dialogs.ownByActiveWindow(stage);
            stage.setScene(Dialogs.fittedScene(root, ctrl.getBodyScroll(), 960));
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Read-only member profile. Departed members can be viewed but not edited from here. */
    private void showMember(Member m) {
        String ministries = loadMinistriesForMember(m.getId());
        String spouse = null;
        if (m.getSpouseMemberId() != null) {
            try {
                PreparedStatement ps = DatabaseConnection.getConnection()
                        .prepareStatement("SELECT full_name FROM members WHERE id=?");
                ps.setInt(1, m.getSpouseMemberId());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) spouse = rs.getString(1);
            } catch (SQLException e) { e.printStackTrace(); }
        }
        String dob = m.getDateOfBirth() == null ? null : m.getDateOfBirth().format(FMT) + "  (age " +
                java.time.Period.between(m.getDateOfBirth(), java.time.LocalDate.now()).getYears() + ")";
        String marital = m.getMaritalStatus() == null ? null
                : m.getMaritalStatus() + (spouse != null ? " to " + spouse : "");
        String nok = m.getNextOfKinName() == null || m.getNextOfKinName().isBlank() ? null
                : m.getNextOfKinName() + (m.getNextOfKinPhone() != null && !m.getNextOfKinPhone().isBlank()
                        ? "  \u00b7  " + m.getNextOfKinPhone() : "");

        DocumentViewer v = new DocumentViewer(m.isDeceased() ? "Faithful Departed" : "Member Profile", m.getFullName())
            .icon(m.isDeceased() ? "fas-dove" : "fas-id-card")
                .avatar(Avatars.of(m.getPhotoPath(), 76))
                .status(m.isDeceased() ? "Faithful Departed" : m.getStatusLabel(),
                        !m.isDeceased() && m.isActive() ? "badge-active" : "badge-inactive")
                .meta("Phone",          m.getPhone())
                .meta("Email",          m.getEmail())
                .meta("Gender",         m.getGender())
                .meta("Date of Birth",  dob)
                .meta("Sub-Branch",     m.getSubBranchName())
                .meta("Family",         m.getFamilyName())
                .meta("Ministries",     "-".equals(ministries) ? null : ministries)
                .meta("Availability",   m.getAvailabilityLabel())
                .meta("Marital Status", marital)
                .meta("Employment",     m.getEmploymentStatus())
                .meta("Date Joined",    m.getDateJoined()  != null ? m.getDateJoined().format(FMT)  : null)
                .meta("Baptism Date",   m.getBaptismDate() != null ? m.getBaptismDate().format(FMT) : null)
                .meta("Next of Kin",    nok)
                .section("Address", m.getAddress(), "No address recorded.")
                .action("Print Form", "fas-print", () -> DocumentExporter.exportMemberForm(m));
        if (!m.isDeceased()) {
            v.closingAction("Faithful Departed", "fas-dove", "btn-secondary", () -> markDeparted(m))
             .onEdit(() -> openAddEditDialog(m))
             .onDelete(() -> handleDelete(m));
        }
        v.show();
    }

    /** Opens the Faithful Departed dialog; returns true if the member was recorded as departed. */
    private boolean markDeparted(Member m) {
        boolean[] done = { false };
        String css = getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm();
        DeceasedMembersController.showMarkDeceasedDialog(m.getId(), m.getFullName(), () -> {
            done[0] = true;
            loadMembers();
            if (deceasedCtrl != null) deceasedCtrl.loadDeceased();
            javafx.application.Platform.runLater(() ->
                    CommemorationsController.showDeathAnnouncementDialog(m.getFullName()));
        }, css);
        return done[0];
    }

    /** Confirms, then soft-deletes the member. Returns true if deleted. */
    private boolean handleDelete(Member m) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Delete Member");
        confirm.setHeaderText("Delete " + m.getFullName() + "?");
        confirm.setContentText("This member will be soft-deleted and removed from all views.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE members SET is_deleted=1 WHERE id=?");
            ps.setInt(1, m.getId());
            ps.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    "Deleted member: " + m.getFullName());
            loadMembers();
            loadFamilies();
            ToastManager.success("Member deleted successfully.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to delete member: " + e.getMessage());
            return false;
        }
    }

    // =========================================================
    // PENDING ACTIONS
    // =========================================================

    /** Creates the member from the pending submission. Returns true on success. */
    private boolean approvePending(int pendingId, String name, String phone, String branch) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement getPending = conn.prepareStatement(
                    "SELECT * FROM pending_members WHERE id=?");
            getPending.setInt(1, pendingId);
            ResultSet pm = getPending.executeQuery();
            if (!pm.next()) return false;

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
            ToastManager.success(name + " approved and added to members.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to approve member: " + e.getMessage());
            return false;
        }
    }

    /** Confirms, then rejects the submission. Returns true if rejected. */
    private boolean rejectPending(int pendingId, String name) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Reject Submission");
        confirm.setHeaderText("Reject " + name + "?");
        confirm.setContentText("They will not be added as a member.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE pending_members SET status='Rejected', " +
                    "reviewed_by=?, reviewed_at=NOW() WHERE id=?");
            ps.setInt(1, SessionManager.getInstance().getCurrentUser().getId());
            ps.setInt(2, pendingId);
            ps.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    "Rejected pending member: " + name);
            loadPending();
            ToastManager.success(name + " rejected.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to reject submission: " + e.getMessage());
            return false;
        }
    }

    /** Confirms, then deletes the family (members are unlinked, not deleted). Returns true if deleted. */
    private boolean deleteFamilyById(int id) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Delete Family");
        confirm.setHeaderText("Delete this family?");
        confirm.setContentText("Members in this family will not be deleted, just unlinked.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        try {
            Connection conn = DatabaseConnection.getConnection();
            conn.createStatement().executeUpdate(
                    "UPDATE members SET family_id=NULL WHERE family_id=" + id);
            conn.createStatement().executeUpdate(
                    "DELETE FROM families WHERE id=" + id);
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    "Deleted family ID " + id);
            loadFamilies();
            ToastManager.success("Family deleted.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to delete family: " + e.getMessage());
            return false;
        }
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

    private HBox detailChip(String iconLiteral, String text) {
        HBox chip = new HBox(4);
        chip.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size:11px;-fx-text-fill:#5A6275;");
        chip.getChildren().addAll(Icons.of(iconLiteral, 10, "#9099AA"), lbl);
        return chip;
    }

    private Label detailDot() {
        Label dot = new Label("·");
        dot.setStyle("-fx-font-size:11px;-fx-text-fill:#9099AA;");
        return dot;
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
        FormBuilder.prompt("Create Family", "FAMILY NAME *", null, "Create Family", "fas-home").ifPresent(name -> {
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
        TableActions.viewOnly(subBranchTable, colSbActions, this::showSubBranch);
    }

    private void showSubBranch(String[] row) {
        int id = Integer.parseInt(row[3]);
        boolean active = "1".equals(row[2]);
        new DocumentViewer("Sub-Branch", row[0])
            .icon("fas-code-branch")
            .status(active ? "Active" : "Inactive", active ? "badge-active" : "badge-inactive")
            .width(620)
            .meta("Members", row[1])
            .section("Members", DocumentViewer.bulletList(
                memberNames("SELECT full_name FROM members WHERE sub_branch_id=? " +
                            "AND is_deleted=0 AND is_deceased=0 ORDER BY full_name", id),
                "No members in this sub-branch."))
            .closingAction(active ? "Deactivate" : "Activate", active ? "fas-toggle-off" : "fas-toggle-on",
                active ? "btn-danger" : "btn-secondary", () -> toggleSubBranch(id, row[2]))
            .editLabel("Rename")
            .onEdit(() -> editSubBranch(id, row[0]))
            .show();
    }

    private List<String> memberNames(String sql, int id) {
        List<String> names = new ArrayList<>();
        try {
            PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql);
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) names.add(rs.getString(1));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return names;
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
        FormBuilder.prompt("Add Sub-Branch", "SUB-BRANCH NAME *", null, "Add Sub-Branch", "fas-code-branch").ifPresent(name -> {
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
        FormBuilder.prompt("Rename Sub-Branch", "SUB-BRANCH NAME *", currentName, "Save Changes", "fas-code-branch").ifPresent(name -> {
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

    private boolean toggleSubBranch(int id, String currentStatus) {
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
            ToastManager.success("Sub-branch " + (newStatus == 1 ? "activated." : "deactivated."));
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
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
        TableActions.viewOnly(ministriesTable, colMinActions, this::showMinistry);
    }

    private void showMinistry(String[] row) {
        int id = Integer.parseInt(row[4]);
        boolean active = "1".equals(row[3]);
        new DocumentViewer("Ministry", row[0])
            .icon("fas-church")
            .status(active ? "Active" : "Inactive", active ? "badge-active" : "badge-inactive")
            .width(620)
            .meta("Members", row[2])
            .section("Description", row[1], "No description.")
            .section("Members", DocumentViewer.bulletList(
                memberNames("SELECT m.full_name FROM member_ministries mm JOIN members m ON m.id=mm.member_id " +
                            "WHERE mm.ministry_id=? AND m.is_deleted=0 AND m.is_deceased=0 ORDER BY m.full_name", id),
                "No members in this ministry."))
            .closingAction(active ? "Deactivate" : "Activate", active ? "fas-toggle-off" : "fas-toggle-on",
                active ? "btn-danger" : "btn-secondary", () -> toggleMinistry(id, row[3]))
            .onEdit(() -> editMinistry(id, row[0], row[1]))
            .show();
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
        FormBuilder f = new FormBuilder(id == 0 ? "Add Ministry" : "Edit Ministry").icon("fas-church");
        TextField nameField = FormBuilder.text("e.g. Youth Ministry");
        nameField.setText(existingName);
        TextArea descField = FormBuilder.area("What this ministry does (optional)", 80);
        descField.setText(existingDesc);
        f.section("Ministry")
         .field("MINISTRY NAME *", nameField)
         .field("DESCRIPTION", descField);

        Button saveBtn = f.saveButton(id == 0 ? "Add Ministry" : "Save Changes");
        saveBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) { f.showError("Ministry name is required."); return; }
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
                f.close();
                ToastManager.success(id == 0 ? "Ministry added." : "Ministry updated.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                f.showError("Could not save the ministry: " + ex.getMessage());
            }
        });
        f.show(520);
    }

    private boolean toggleMinistry(int id, String currentStatus) {
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
            ToastManager.success("Ministry " + (newStatus == 1 ? "activated." : "deactivated."));
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}