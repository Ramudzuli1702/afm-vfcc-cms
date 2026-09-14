package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.models.AttendanceSession;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.NetworkUtils;
import com.afmvfcc.utils.QrCodeGenerator;
import com.afmvfcc.utils.SessionManager;
import com.afmvfcc.utils.ToastManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AttendanceController {

    // ── Take Attendance tab controls ──────────────────────────
    @FXML private ComboBox<String>  sessionCombo;
    @FXML private ComboBox<String>  attendanceMinistryCombo;
    @FXML private ComboBox<String>  presenceFilterCombo;
    @FXML private VBox              attendanceMembersBox;
    @FXML private Label             presentCountLabel;
    @FXML private TextField         attendanceSearchField;
    @FXML private HBox              syncBar;
    @FXML private Label             syncStatusLabel;
    @FXML private Label             ipAddressLabel;
    @FXML private ImageView         attendanceQrThumb;

    // ── Session history tab controls ─────────────────────────
    @FXML private TabPane                                  attendanceTabs;
    @FXML private TableView<AttendanceSession>             sessionsTable;
    @FXML private TableColumn<AttendanceSession, String>   colSessName, colSessDate,
                                                            colSessMinistry, colSessPresent,
                                                            colSessTotal, colSessStatus;
    @FXML private TableColumn<AttendanceSession, Void>     colSessActions;
    @FXML private TextField                                sessionSearchField;

    // ── State ─────────────────────────────────────────────────
    private int     currentSessionId     = -1;
    private boolean currentSessionClosed = false;

    /**
     * Ministry name associated with the currently-selected session.
     */
    private String currentSessionMinistryName = null;

    private final Map<Integer, CheckBox> memberCheckboxes = new LinkedHashMap<>();
    private final Map<Integer, String>   memberNames      = new LinkedHashMap<>();
    private final Map<Integer, String>   memberMinistries = new LinkedHashMap<>();

    private final ObservableList<AttendanceSession> allSessions =
        FXCollections.observableArrayList();

    private final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ══════════════════════════════════════════════════════════
    // INIT
    // ══════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        loadMinistryFilter();
        setupPresenceFilter();
        loadSessionCombo();
        setupSessionsTable();
        loadSessionHistory();
        detectAndShowIp();
    }

    private void setupPresenceFilter() {
        if (presenceFilterCombo != null) {
            presenceFilterCombo.getItems().addAll("All", "Present", "Absent");
            presenceFilterCombo.setValue("All");
        }
    }

    // ══════════════════════════════════════════════════════════
    // SESSION COMBO
    // ══════════════════════════════════════════════════════════

    private void loadSessionCombo() {
        sessionCombo.getItems().clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT id, session_name, session_date, is_closed " +
                "FROM attendance_sessions " +
                "ORDER BY session_date DESC, id DESC LIMIT 50"
            );
            while (rs.next()) {
                boolean closed = rs.getInt("is_closed") == 1;
                String suffix  = closed ? " \uD83D\uDD12" : ""; 
                sessionCombo.getItems().add(
                    rs.getString("session_name") + " - " +
                    rs.getDate("session_date").toLocalDate().format(FMT) +
                    " [id:" + rs.getInt("id") + "]" + suffix
                );
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @FXML
    public void handleSessionSelected() {
        String selected = sessionCombo.getValue();
        if (selected == null) return;
        try {
            int start = selected.lastIndexOf("[id:") + 4;
            int end   = selected.indexOf("]", start);
            currentSessionId = Integer.parseInt(selected.substring(start, end));

            // loadMembersForAttendance populates currentSessionMinistryName as a side-effect
            loadMembersForAttendance();

            if (attendanceMinistryCombo != null) {
                String targetMinistry = (currentSessionMinistryName != null)
                    ? currentSessionMinistryName : "All";
                if (attendanceMinistryCombo.getItems().contains(targetMinistry)) {
                    attendanceMinistryCombo.setValue(targetMinistry);
                } else {
                    attendanceMinistryCombo.setValue("All");
                }

                renderMembersList(
                    attendanceSearchField != null ? attendanceSearchField.getText() : null);
            }

            syncBar.setVisible(true);
            syncBar.setManaged(true);
            String sessName = selected.split(" - ")[0];
            syncStatusLabel.setText(currentSessionClosed
                ? "\uD83D\uDD12 Viewing closed session: " + sessName
                : "Session active: " + sessName);

        } catch (Exception e) { e.printStackTrace(); }
    }

    // ══════════════════════════════════════════════════════════
    // NEW SESSION DIALOG
    // ══════════════════════════════════════════════════════════

    @FXML
    public void handleNewSession() {
        Stage stage = new Stage();
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.setTitle("New Attendance Session");

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#F5F6FA;");

        HBox header = new HBox();
        header.setStyle("-fx-background-color:#FFFFFF;-fx-padding:18 24;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        Label titleLbl = new Label("New Attendance Session");
        titleLbl.setStyle("-fx-font-size:16px;-fx-font-weight:700;-fx-text-fill:#1E2130;");
        header.getChildren().add(titleLbl);

        VBox body = new VBox(14);
        body.setStyle("-fx-padding:20 24;-fx-background-color:#F5F6FA;");

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Sunday Service");
        nameField.getStyleClass().add("form-field");
        nameField.setMaxWidth(Double.MAX_VALUE);

        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.getStyleClass().add("form-date-picker");
        datePicker.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> ministryCombo = new ComboBox<>();
        ministryCombo.getStyleClass().add("form-combo");
        ministryCombo.setPromptText("All ministries (optional)");
        ministryCombo.setMaxWidth(Double.MAX_VALUE);
        loadMinistriesInto(ministryCombo);

        body.getChildren().addAll(
            makeFormRow("SESSION NAME *", nameField),
            makeFormRow("DATE *",         datePicker),
            makeFormRow("MINISTRY",       ministryCombo)
        );

        Button createBtn = new Button("Create Session");
        Button cancelBtn = new Button("Cancel");
        createBtn.getStyleClass().add("btn-primary");
        cancelBtn.getStyleClass().add("btn-secondary");
        HBox footer = new HBox(10, cancelBtn, createBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color:#FFFFFF;-fx-padding:14 24;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");

        ScrollPane bodyScroll = new ScrollPane(body);
        bodyScroll.setFitToWidth(true);
        bodyScroll.setStyle("-fx-background-color:transparent;-fx-background:transparent;" +
                            "-fx-border-color:transparent;");
        VBox.setVgrow(bodyScroll, Priority.ALWAYS);

        root.getChildren().addAll(header, bodyScroll, footer);
        cancelBtn.setOnAction(e -> stage.close());

        createBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) return;
            try {
                Connection conn = DatabaseConnection.getConnection();
                int ministryId = getMinistryId(conn, ministryCombo.getValue());
                PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO attendance_sessions " +
                    "(session_name, session_date, ministry_id, created_by) VALUES (?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, name);
                ps.setDate(2, java.sql.Date.valueOf(datePicker.getValue()));
                if (ministryId > 0) ps.setInt(3, ministryId); else ps.setNull(3, Types.INTEGER);
                ps.setInt(4, SessionManager.getInstance().getCurrentUser().getId());
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) currentSessionId = keys.getInt(1);
                AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    "Created attendance session: " + name);
                loadSessionCombo();
                // Select the newly-created session in the combo
                for (String item : sessionCombo.getItems()) {
                    if (item.contains("[id:" + currentSessionId + "]")) {
                        sessionCombo.setValue(item);
                        break;
                    }
                }
                loadMembersForAttendance();
                loadSessionHistory();
                stage.close();
                ToastManager.success("Session \"" + name + "\" created.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                ToastManager.error("Failed to create session: " + ex.getMessage());
            }
        });

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 480, 340);
        scene.setFill(javafx.scene.paint.Color.web("#F5F6FA"));
        scene.getStylesheets().add(
            getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    // ══════════════════════════════════════════════════════════
    // CLOSE SESSION
    // ══════════════════════════════════════════════════════════

    @FXML
    public void handleCloseSession() {
        if (currentSessionId < 0) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Please select a session first.");
            Main.applyStyles(a.getDialogPane()); a.showAndWait(); return;
        }
        if (currentSessionClosed) {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "This session is already closed.");
            Main.applyStyles(a.getDialogPane()); a.showAndWait(); return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Close Session");
        confirm.setHeaderText("Close this attendance session?");
        confirm.setContentText("The session will be marked as completed. " +
            "Attendance records will be locked — you can still view them but not edit them.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                        "UPDATE attendance_sessions SET is_closed=1 WHERE id=?");
                    ps.setInt(1, currentSessionId);
                    ps.executeUpdate();
                    AuditLogger.log(
                        SessionManager.getInstance().getCurrentUser().getId(),
                        "Closed attendance session ID: " + currentSessionId);
                    currentSessionClosed = true;
                    memberCheckboxes.values().forEach(cb -> cb.setDisable(true));
                    syncStatusLabel.setText("\uD83D\uDD12 Session closed.");
                    loadSessionCombo();
                    loadSessionHistory();

                    for (String item : sessionCombo.getItems()) {
                        if (item.contains("[id:" + currentSessionId + "]")) {
                            sessionCombo.setValue(item);
                            break;
                        }
                    }
                    ToastManager.success("Session closed successfully.");
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    ToastManager.error("Failed to close session: " + ex.getMessage());
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════
    // MEMBERS LIST
    // ══════════════════════════════════════════════════════════

    private void loadMembersForAttendance() {
        memberCheckboxes.clear();
        memberNames.clear();
        memberMinistries.clear();
        currentSessionClosed    = false;
        currentSessionMinistryName = null;
        if (currentSessionId < 0) return;

        try {
            Connection conn = DatabaseConnection.getConnection();

            PreparedStatement metaPs = conn.prepareStatement(
                "SELECT ase.is_closed, mi.name AS ministry_name " +
                "FROM attendance_sessions ase " +
                "LEFT JOIN ministries mi ON mi.id = ase.ministry_id " +
                "WHERE ase.id = ?");
            metaPs.setInt(1, currentSessionId);
            ResultSet metaRs = metaPs.executeQuery();
            if (metaRs.next()) {
                currentSessionClosed = metaRs.getInt("is_closed") == 1;
                currentSessionMinistryName = metaRs.getString("ministry_name");
            }

            // Load already-present member IDs
            Set<Integer> presentIds = new HashSet<>();
            PreparedStatement psP = conn.prepareStatement(
                "SELECT member_id FROM attendance_records " +
                "WHERE session_id=? AND is_present=1");
            psP.setInt(1, currentSessionId);
            ResultSet rsP = psP.executeQuery();
            while (rsP.next()) presentIds.add(rsP.getInt("member_id"));

            // Load all active members
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT m.id, m.full_name, " +
                "IFNULL(GROUP_CONCAT(mi.name ORDER BY mi.name SEPARATOR ', '),'-') AS ministries " +
                "FROM members m " +
                "LEFT JOIN member_ministries mm ON mm.member_id = m.id " +
                "LEFT JOIN ministries mi ON mi.id = mm.ministry_id " +
                "WHERE m.is_deleted=0 AND m.is_active=1 AND m.is_deceased=0 " +
                "GROUP BY m.id, m.full_name ORDER BY m.full_name ASC"
            );
            while (rs.next()) {
                int id = rs.getInt("id");
                memberNames.put(id, rs.getString("full_name"));
                memberMinistries.put(id, rs.getString("ministries"));
                CheckBox cb = new CheckBox();
                cb.setSelected(presentIds.contains(id));
                cb.setDisable(currentSessionClosed);
                if (!currentSessionClosed) {
                    final int memberId = id;
                    cb.selectedProperty().addListener((obs, o, n) -> saveRecord(memberId, n));
                }
                memberCheckboxes.put(id, cb);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        renderMembersList(attendanceSearchField != null ? attendanceSearchField.getText() : null);
    }

    private void renderMembersList(String filter) {
        attendanceMembersBox.getChildren().clear();
        int present = 0, total = 0;

        String presenceFilter = (presenceFilterCombo != null && presenceFilterCombo.getValue() != null)
            ? presenceFilterCombo.getValue() : "All";
        String ministryFilter = (attendanceMinistryCombo != null)
            ? attendanceMinistryCombo.getValue() : "All";

        for (Map.Entry<Integer, CheckBox> entry : memberCheckboxes.entrySet()) {
            int    id    = entry.getKey();
            String name  = memberNames.get(id);
            String minis = memberMinistries.get(id);
            CheckBox cb  = entry.getValue();
            boolean isPresent = cb.isSelected();

            // Text search
            if (filter != null && !filter.isEmpty() &&
                !name.toLowerCase().contains(filter.toLowerCase())) continue;

            // Ministry filter
            if (ministryFilter != null && !"All".equals(ministryFilter) &&
                !minis.contains(ministryFilter)) continue;

            // Presence filter
            if ("Present".equals(presenceFilter) && !isPresent) continue;
            if ("Absent".equals(presenceFilter)  &&  isPresent) continue;

            total++;
            if (isPresent) present++;

            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color:" + (total % 2 == 0 ? "#FFFFFF" : "#FAFBFD") + ";" +
                         "-fx-background-radius:8;-fx-padding:10 16;");

            VBox info = new VBox(2);
            Label nameLabel  = new Label(name);
            nameLabel.setStyle("-fx-text-fill:#1E2130;-fx-font-size:13px;-fx-font-weight:700;");
            Label minisLabel = new Label(minis);
            minisLabel.setStyle("-fx-text-fill:#5A6275;-fx-font-size:11px;");
            info.getChildren().addAll(nameLabel, minisLabel);

            HBox spacer = new HBox();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label presLabel = new Label(isPresent ? "PRESENT" : "ABSENT");
            presLabel.getStyleClass().add(isPresent ? "badge-active" : "badge-inactive");

            cb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                presLabel.setText(newVal ? "PRESENT" : "ABSENT");
                presLabel.getStyleClass().setAll(newVal ? "badge-active" : "badge-inactive");
                updatePresentCount();
            });

            if (!currentSessionClosed) {
                row.setOnMouseClicked(e -> cb.setSelected(!cb.isSelected()));
            }
            row.getChildren().addAll(cb, info, spacer, presLabel);
            attendanceMembersBox.getChildren().add(row);
        }

        if (currentSessionClosed && !memberCheckboxes.isEmpty()) {
            // Show a read-only banner at the top
            Label banner = new Label("\uD83D\uDD12  This session is closed — records are read-only.");
            banner.setStyle("-fx-background-color:#FDF3E0;-fx-text-fill:#7A4F00;" +
                            "-fx-font-size:12px;-fx-font-weight:600;-fx-padding:8 16;" +
                            "-fx-background-radius:8;");
            banner.setMaxWidth(Double.MAX_VALUE);
            attendanceMembersBox.getChildren().add(0, banner);
        }

        presentCountLabel.setText("Present: " + present + "  /  Total: " + total);
    }

    private void updatePresentCount() {
        long present = memberCheckboxes.values().stream().filter(CheckBox::isSelected).count();
        presentCountLabel.setText("Present: " + present + "  /  Total: " + memberCheckboxes.size());
    }

    private void saveRecord(int memberId, boolean isPresent) {
        if (currentSessionId < 0 || currentSessionClosed) return;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO attendance_records (session_id, member_id, is_present) " +
                "VALUES (?,?,?) ON DUPLICATE KEY UPDATE is_present=?"
            );
            ps.setInt(1, currentSessionId);
            ps.setInt(2, memberId);
            ps.setInt(3, isPresent ? 1 : 0);
            ps.setInt(4, isPresent ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @FXML public void handleMinistryFilter()   { renderMembersList(attendanceSearchField.getText()); }
    @FXML public void handlePresenceFilter()   { renderMembersList(attendanceSearchField.getText()); }
    @FXML public void handleAttendanceSearch() { renderMembersList(attendanceSearchField.getText()); }
    @FXML public void handleMarkAllPresent()   { memberCheckboxes.values().forEach(cb -> cb.setSelected(true)); }
    @FXML public void handleClearAll()         { memberCheckboxes.values().forEach(cb -> cb.setSelected(false)); }

    // ══════════════════════════════════════════════════════════
    // EXPORT
    // ══════════════════════════════════════════════════════════

    /** Export ALL members (present + absent) */
    @FXML
    public void handleExportExcel() { exportAttendance(false); }

    /** Export PRESENT members only */
    @FXML
    public void handleExportPresentOnly() { exportAttendance(true); }

    private void exportAttendance(boolean presentOnly) {
        if (currentSessionId < 0) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Please select a session first.");
            Main.applyStyles(a.getDialogPane()); a.showAndWait(); return;
        }

        List<DocumentExporter.AttendanceRow> rows = new ArrayList<>();
        for (Map.Entry<Integer, CheckBox> entry : memberCheckboxes.entrySet()) {
            int id = entry.getKey();
            boolean isPresent = entry.getValue().isSelected();
            if (presentOnly && !isPresent) continue;
            rows.add(new DocumentExporter.AttendanceRow(
                memberNames.get(id),
                memberMinistries.get(id),
                isPresent ? "Present" : "Absent"
            ));
        }

        String label = sessionCombo.getValue() != null ? sessionCombo.getValue() : "";
        String sessionName = label.contains(" - ")
            ? label.substring(0, label.indexOf(" - ")) : label;
        String sessionDate = label.contains(" - ") && label.contains(" [id:")
            ? label.substring(label.indexOf(" - ") + 3, label.lastIndexOf(" [id:"))
            : LocalDate.now().format(FMT);
        if (presentOnly) sessionName += " (Present Only)";

        DocumentExporter.exportAttendance(sessionName, sessionDate, rows);
    }

    // Android merge placeholder
    @FXML
    public void handleMergeAndroid() {
        Alert a = new Alert(Alert.AlertType.INFORMATION,
            "Android merge will be available once the Android app is configured.");
        Main.applyStyles(a.getDialogPane()); a.showAndWait();
    }

    // ══════════════════════════════════════════════════════════
    // SESSION HISTORY TABLE
    // ══════════════════════════════════════════════════════════

    private void setupSessionsTable() {
        colSessName.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getSessionName()));
        colSessDate.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getSessionDate() != null
                ? d.getValue().getSessionDate().format(FMT) : "-"));
        colSessMinistry.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getMinistryName() != null
                ? d.getValue().getMinistryName() : "All"));
        colSessPresent.setCellValueFactory(d ->
            new SimpleStringProperty(String.valueOf(getPresentCount(d.getValue().getId()))));
        colSessTotal.setCellValueFactory(d ->
            new SimpleStringProperty(String.valueOf(getTotalCount(d.getValue().getId()))));

        // Status badge column
        if (colSessStatus != null) {
            colSessStatus.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().isClosed() ? "Closed" : "Open"));
            colSessStatus.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setGraphic(null); return; }
                    Label badge = new Label(item);
                    badge.getStyleClass().add(
                        "Closed".equals(item) ? "badge-inactive" : "badge-active");
                    setGraphic(badge); setText(null);
                }
            });
        }

        // Actions column: View | Close | Delete
        colSessActions.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn   = new Button("View");
            private final Button closeBtn  = new Button("Close");
            private final Button deleteBtn = new Button("Delete");
            private final HBox   box       = new HBox(6, viewBtn, closeBtn, deleteBtn);
            {
                viewBtn.getStyleClass().add("btn-secondary");
                viewBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                closeBtn.getStyleClass().add("btn-primary");
                closeBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                deleteBtn.getStyleClass().add("btn-danger");
                deleteBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                box.setAlignment(Pos.CENTER_LEFT);

                viewBtn.setOnAction(e -> {
                    AttendanceSession s = safeItem();
                    if (s != null) selectSession(s.getId(), s.getSessionName());
                });
                closeBtn.setOnAction(e -> {
                    AttendanceSession s = safeItem();
                    if (s != null) closeSessionInHistory(s);
                });
                deleteBtn.setOnAction(e -> {
                    AttendanceSession s = safeItem();
                    if (s != null) deleteSession(s);
                });
            }

            private AttendanceSession safeItem() {
                int idx = getIndex();
                return (idx >= 0 && idx < getTableView().getItems().size())
                    ? getTableView().getItems().get(idx) : null;
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty) {
                    AttendanceSession s = safeItem();
                    if (s != null) {
                        boolean closed = s.isClosed();
                        closeBtn.setDisable(closed);
                        closeBtn.setText(closed ? "\uD83D\uDD12" : "Close");
                    }
                }
                setGraphic(empty ? null : box);
            }
        });

        sessionsTable.setItems(allSessions);
    }

    private void closeSessionInHistory(AttendanceSession s) {
        if (s.isClosed()) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Close Session");
        confirm.setHeaderText("Close \"" + s.getSessionName() + "\"?");
        confirm.setContentText("Attendance records will be locked.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                        "UPDATE attendance_sessions SET is_closed=1 WHERE id=?");
                    ps.setInt(1, s.getId());
                    ps.executeUpdate();
                    AuditLogger.log(
                        SessionManager.getInstance().getCurrentUser().getId(),
                        "Closed attendance session: " + s.getSessionName());
                    if (currentSessionId == s.getId()) {
                        currentSessionClosed = true;
                        memberCheckboxes.values().forEach(cb -> cb.setDisable(true));
                        syncStatusLabel.setText("\uD83D\uDD12 Session closed.");
                    }
                    loadSessionCombo();
                    loadSessionHistory();
                    ToastManager.success("Session \"" + s.getSessionName() + "\" closed.");
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    ToastManager.error("Failed to close session: " + ex.getMessage());
                }
            }
        });
    }

    private void loadSessionHistory() {
        allSessions.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT ase.*, mi.name AS ministry_name " +
                "FROM attendance_sessions ase " +
                "LEFT JOIN ministries mi ON mi.id = ase.ministry_id " +
                "ORDER BY ase.session_date DESC, ase.id DESC"
            );
            while (rs.next()) {
                AttendanceSession s = new AttendanceSession();
                s.setId(rs.getInt("id"));
                s.setSessionName(rs.getString("session_name"));
                s.setSessionDate(rs.getDate("session_date").toLocalDate());
                s.setMinistryId(rs.getInt("ministry_id"));
                s.setMinistryName(rs.getString("ministry_name"));
                // Safe read — column may not exist on very old DBs before migration
                try { s.setClosed(rs.getInt("is_closed") == 1); }
                catch (SQLException ignored) { s.setClosed(false); }
                allSessions.add(s);
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @FXML
    public void handleSessionSearch() {
        String q = sessionSearchField.getText().toLowerCase();
        if (q.isEmpty()) { sessionsTable.setItems(allSessions); return; }
        ObservableList<AttendanceSession> filtered = FXCollections.observableArrayList();
        for (AttendanceSession s : allSessions) {
            if (s.getSessionName().toLowerCase().contains(q) ||
                (s.getMinistryName() != null && s.getMinistryName().toLowerCase().contains(q)))
                filtered.add(s);
        }
        sessionsTable.setItems(filtered);
    }

    private void selectSession(int id, String name) {
        currentSessionId = id;
        // Set combo selection — triggers handleSessionSelected which auto-applies ministry filter
        for (String item : sessionCombo.getItems()) {
            if (item.contains("[id:" + id + "]")) {
                sessionCombo.setValue(item);
                break;
            }
        }
        loadMembersForAttendance();
        syncBar.setVisible(true);
        syncBar.setManaged(true);
        syncStatusLabel.setText("Viewing: " + name);
        if (attendanceTabs != null) attendanceTabs.getSelectionModel().selectFirst();
    }

    private void deleteSession(AttendanceSession s) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Delete Session");
        confirm.setHeaderText("Delete session: " + s.getSessionName() + "?");
        confirm.setContentText("All attendance records for this session will also be deleted.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    Connection conn = DatabaseConnection.getConnection();
                    conn.createStatement().executeUpdate(
                        "DELETE FROM attendance_records WHERE session_id=" + s.getId());
                    conn.createStatement().executeUpdate(
                        "DELETE FROM attendance_sessions WHERE id=" + s.getId());
                    AuditLogger.log(
                        SessionManager.getInstance().getCurrentUser().getId(),
                        "Deleted attendance session: " + s.getSessionName());
                    if (currentSessionId == s.getId()) {
                        currentSessionId = -1;
                        currentSessionClosed = false;
                        attendanceMembersBox.getChildren().clear();
                        memberCheckboxes.clear();
                    }
                    loadSessionCombo();
                    loadSessionHistory();
                    ToastManager.success("Session \"" + s.getSessionName() + "\" deleted.");
                } catch (SQLException e) {
                    e.printStackTrace();
                    ToastManager.error("Failed to delete session: " + e.getMessage());
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════

    private void loadMinistryFilter() {
        attendanceMinistryCombo.getItems().clear();
        attendanceMinistryCombo.getItems().add("All");
        loadMinistriesInto(attendanceMinistryCombo);
        attendanceMinistryCombo.setValue("All");
    }

    private void loadMinistriesInto(ComboBox<String> combo) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT name FROM ministries WHERE is_active=1 ORDER BY name");
            while (rs.next()) combo.getItems().add(rs.getString("name"));
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private int getMinistryId(Connection conn, String name) {
        if (name == null) return 0;
        try {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM ministries WHERE name=?");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("id") : 0;
        } catch (SQLException e) { return 0; }
    }

    private int getPresentCount(int sessionId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM attendance_records " +
                "WHERE session_id=? AND is_present=1");
            ps.setInt(1, sessionId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    private int getTotalCount(int sessionId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM attendance_records WHERE session_id=?");
            ps.setInt(1, sessionId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    private VBox makeFormRow(String label, javafx.scene.Node field) {
        VBox box = new VBox(5);
        Label l = new Label(label);
        l.getStyleClass().add("form-label");
        box.getChildren().addAll(l, field);
        if (field instanceof Control) ((Control) field).setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static final int APP_SERVER_PORT = 8080;
    private String currentQrPayload = null;

    private void detectAndShowIp() {
        if (ipAddressLabel == null) return;
        NetworkUtils.ServerAddress addr = NetworkUtils.findServerAddress();
        if (addr == null) {
            ipAddressLabel.setText("No network connection");
            currentQrPayload = null;
            if (attendanceQrThumb != null) attendanceQrThumb.setImage(null);
            return;
        }
        ipAddressLabel.setText(addr.ip + ":" + APP_SERVER_PORT);
        currentQrPayload = "afmvfcc://connect?ip=" + addr.ip + "&port=" + APP_SERVER_PORT;
        if (attendanceQrThumb != null) {
            attendanceQrThumb.setImage(QrCodeGenerator.generate(currentQrPayload, 160));
        }
    }

    @FXML
    public void handleEnlargeQr() {
        if (currentQrPayload == null) {
            ToastManager.error("No active network connection to connect the app to.");
            return;
        }
        Stage stage = new Stage();
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.setTitle("Connect the Mobile App");

        ImageView bigQr = new ImageView(QrCodeGenerator.generate(currentQrPayload, 360));
        bigQr.setFitWidth(320);
        bigQr.setFitHeight(320);

        Label ipLabel = new Label(ipAddressLabel.getText());
        ipLabel.setStyle("-fx-font-size:18px;-fx-font-weight:800;-fx-text-fill:#1E2130;" +
                          "-fx-font-family:'Courier New';");
        Label hint = new Label("Scan this from the Android app's Server Settings screen.");
        hint.setStyle("-fx-font-size:12px;-fx-text-fill:#5A6275;");
        hint.setWrapText(true);

        VBox root = new VBox(14, bigQr, ipLabel, hint);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding:28;-fx-background-color:#FFFFFF;");

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 380, 420);
        scene.getStylesheets().add(
            getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }
}