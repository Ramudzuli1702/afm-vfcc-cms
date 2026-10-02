package com.afmvfcc.controllers;

import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.Main;
import com.afmvfcc.models.BoardMeeting;
import com.afmvfcc.models.BoardTask;
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
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BoardController {

    // Board Members
    @FXML private TableView<String[]>            boardTable;
    @FXML private TableColumn<String[], String>  colBoardName, colBoardRole, colBoardPhone,
                                                  colBoardEmail, colBoardStart;
    @FXML private TableColumn<String[], Void>    colBoardActions;
    @FXML private TextField                      boardSearchField;

    // Meetings
    @FXML private TableView<BoardMeeting>           meetingsTable;
    @FXML private TableColumn<BoardMeeting, String> colMeetTitle, colMeetDate, colMeetLocation,
                                                     colMeetStatus, colMeetAttend;
    @FXML private TableColumn<BoardMeeting, Void>   colMeetActions;
    @FXML private TextField                         meetingSearchField;

    // Tasks
    @FXML private TableView<BoardTask>              tasksTable;
    @FXML private TableColumn<BoardTask, String>    colTaskTitle, colTaskAssignee, colTaskMeeting,
                                                     colTaskDue, colTaskStatus, colTaskReport;
    @FXML private TableColumn<BoardTask, Void>      colTaskActions;
    @FXML private TextField                         taskSearchField;
    @FXML private ComboBox<String>                  taskStatusFilter;

    // Stat cards
    @FXML private Label statBoardMembers, statUpcomingMeetings, statOpenTasks, statOverdueTasks;

    private ObservableList<String[]>     allBoard    = FXCollections.observableArrayList();
    private ObservableList<BoardMeeting> allMeetings = FXCollections.observableArrayList();
    private ObservableList<BoardTask>    allTasks    = FXCollections.observableArrayList();
    private final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @FXML
    public void initialize() {
        setupBoardTable();
        setupMeetingsTable();
        setupTasksTable();
        loadBoard();
        loadMeetings();
        loadTasks();
    }

    private void refreshStats() {
        statBoardMembers.setText(String.valueOf(allBoard.size()));
        statUpcomingMeetings.setText(String.valueOf(
            allMeetings.stream().filter(m -> !isCompleted(m)).count()));
        statOpenTasks.setText(String.valueOf(allTasks.stream().filter(t -> !t.isDone()).count()));
        statOverdueTasks.setText(String.valueOf(allTasks.stream().filter(BoardTask::isOverdue).count()));
    }

    private static boolean isCompleted(BoardMeeting m) { return "Completed".equals(m.getStatus()); }

    // ══════════════════════════════════════════════════════════
    // BOARD MEMBERS
    // ══════════════════════════════════════════════════════════

    private void setupBoardTable() {
        colBoardName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        Avatars.nameColumn(colBoardName, row -> row[6]);
        colBoardRole.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        colBoardPhone.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        colBoardEmail.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[3]));
        colBoardStart.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[4]));
        TableActions.viewOnly(boardTable, colBoardActions, this::showBoardMember);
        boardTable.setItems(allBoard);
    }

    private void loadBoard() {
        allBoard.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = """
                SELECT bm.id, m.full_name, bm.role_title,
                       m.phone, m.email, m.photo_path,
                       DATE_FORMAT(bm.start_date,'%d %b %Y') AS since
                FROM board_members bm
                JOIN members m ON m.id = bm.member_id
                WHERE bm.is_active = 1 AND m.is_active = 1
                ORDER BY m.full_name
                """;
            ResultSet rs = conn.createStatement().executeQuery(sql);
            while (rs.next()) {
                allBoard.add(new String[]{
                    rs.getString("full_name"),
                    rs.getString("role_title"),
                    rs.getString("phone") != null ? rs.getString("phone") : "—",
                    rs.getString("email") != null ? rs.getString("email") : "—",
                    rs.getString("since") != null ? rs.getString("since") : "—",
                    rs.getString("id"),
                    rs.getString("photo_path")
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        refreshStats();
    }

    @FXML public void handleBoardSearch() {
        String q = boardSearchField.getText().toLowerCase();
        if (q.isEmpty()) { boardTable.setItems(allBoard); return; }
        ObservableList<String[]> f = FXCollections.observableArrayList();
        for (String[] row : allBoard) {
            if (row[0].toLowerCase().contains(q) || row[1].toLowerCase().contains(q)) f.add(row);
        }
        boardTable.setItems(f);
    }

    @FXML public void handleAddBoardMember() { openBoardMemberDialog(null); }

    private void showBoardMember(String[] row) {
        new DocumentViewer("Board Member", row[0])
            .icon("fas-user-tie")
            .avatar(Avatars.of(row[6], 64))
            .status(row[1], "badge-inprogress")
            .width(640)
            .meta("Role / Title", row[1])
            .meta("On the board since", row[4])
            .meta("Phone", row[2])
            .meta("Email", row[3])
            .onEdit(() -> openBoardMemberDialog(row))
            .deleteLabel("Remove from Board")
            .onDelete(() -> removeBoardMember(Integer.parseInt(row[5])))
            .show();
    }

    private void openBoardMemberDialog(String[] existing) {
        FormBuilder f = new FormBuilder(existing == null ? "Add Board Member" : "Edit Board Member").icon("fas-user-tie");

        ComboBox<String> memberCombo = FormBuilder.combo("Search and select member...");
        memberCombo.setEditable(true);
        loadAllMembersInto(memberCombo);
        TextField roleField = FormBuilder.text("e.g. Chairman, Secretary");
        DatePicker startPicker = FormBuilder.date();
        startPicker.setValue(LocalDate.now());

        if (existing != null) {
            memberCombo.setValue(existing[0]);
            memberCombo.setDisable(true); // the role belongs to this member; remove + re-add to change person
            roleField.setText(existing[1]);
            try {
                startPicker.setValue(LocalDate.parse(existing[4],
                    DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)));
            } catch (Exception ignored) { /* "—" when no start date was recorded */ }
        }

        f.section("Board Member")
         .field("MEMBER *", memberCombo)
         .row("ROLE / TITLE *", roleField, "ON THE BOARD SINCE", startPicker);

        Button saveBtn = f.saveButton(existing == null ? "Add to Board" : "Save Changes");
        saveBtn.setOnAction(e -> {
            String member = memberCombo.getValue();
            String role   = roleField.getText().trim();
            if (member == null || member.isBlank() || role.isEmpty()) {
                f.showError("Please select a member and enter a role.");
                return;
            }
            LocalDate start = startPicker.getValue() != null ? startPicker.getValue() : LocalDate.now();
            try {
                Connection conn = DatabaseConnection.getConnection();
                int memberId = getMemberIdByName(conn, member);
                if (memberId <= 0) { f.showError("\"" + member + "\" is not an active member."); return; }

                if (existing == null) {
                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO board_members (member_id, role_title, start_date, is_active) VALUES (?,?,?,1)"
                    );
                    ps.setInt(1, memberId);
                    ps.setString(2, role);
                    ps.setDate(3, Date.valueOf(start));
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Added board member: " + member + " as " + role);
                } else {
                    PreparedStatement ps = conn.prepareStatement(
                        "UPDATE board_members SET role_title=?, start_date=? WHERE id=?"
                    );
                    ps.setString(1, role);
                    ps.setDate(2, Date.valueOf(start));
                    ps.setInt(3, Integer.parseInt(existing[5]));
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Updated board member: " + member + " role to " + role);
                }
                loadBoard();
                f.close();
                ToastManager.success(existing == null ? "Board member added successfully." : "Board member updated successfully.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                f.showError("Could not save board member: " + ex.getMessage());
                ToastManager.error("Failed to save board member: " + ex.getMessage());
            }
        });
        f.show(560);
    }

    /** Confirms, then ends the member's board term. Returns true if removed. */
    private boolean removeBoardMember(int boardId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Remove Board Member");
        confirm.setHeaderText("Remove this member from the board?");
        confirm.setContentText("Their term is ended today; their member record is kept.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "UPDATE board_members SET is_active=0, end_date=? WHERE id=?"
            );
            ps.setDate(1, Date.valueOf(LocalDate.now()));
            ps.setInt(2, boardId);
            ps.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                "Removed board member ID " + boardId);
            loadBoard();
            ToastManager.success("Board member removed successfully.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to remove board member: " + e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════
    // MEETINGS
    // ══════════════════════════════════════════════════════════

    private void setupMeetingsTable() {
        colMeetTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitle()));
        colMeetDate.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getMeetingDate() != null ? d.getValue().getMeetingDate().format(FMT) : "—"
        ));
        colMeetLocation.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getLocation() != null ? d.getValue().getLocation() : "—"
        ));
        colMeetStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        colMeetStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label b = new Label(item);
                b.getStyleClass().add("Upcoming".equals(item) ? "badge-pending" : "badge-completed");
                setGraphic(b); setText(null);
            }
        });
        colMeetAttend.setCellValueFactory(d -> {
            int count = getMeetingAttendeeCount(d.getValue().getId());
            return new SimpleStringProperty(String.valueOf(count));
        });
        TableActions.viewOnly(meetingsTable, colMeetActions, this::showMeetingDocument);
        meetingsTable.setItems(allMeetings);
    }

    private void showMeetingDocument(BoardMeeting m) {
        List<String> present   = new ArrayList<>();
        List<String> apologies = new ArrayList<>();
        loadAttendeeNames(m.getId(), present, apologies);
        List<BoardTask> tasks = allTasks.stream().filter(t -> t.getMeetingId() == m.getId()).toList();

        VBox files = new VBox(8);
        addDocLink(files, "Agenda document",  m.getAgendaDocPath());
        addDocLink(files, "Minutes document", m.getMinutesDocPath());
        if (files.getChildren().isEmpty()) files.getChildren().add(emptyNote("No documents uploaded."));

        VBox taskList = new VBox(6);
        for (BoardTask t : tasks) {
            Label badge = taskBadge(t);
            Label name  = new Label(t.getTitle());
            name.getStyleClass().add("doc-body");
            name.setWrapText(true);
            Label who = new Label(nvlDash(t.getAssignedName()) +
                (t.getDueDate() != null ? "  ·  due " + t.getDueDate().format(FMT) : ""));
            who.setStyle("-fx-font-size:12px;-fx-text-fill:#5A6275;");
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            HBox line = new HBox(10, new VBox(2, name, who), sp, badge);
            line.setAlignment(Pos.CENTER_LEFT);
            taskList.getChildren().add(line);
        }
        if (tasks.isEmpty()) taskList.getChildren().add(emptyNote("No action items raised at this meeting."));

        DocumentViewer[] viewer = new DocumentViewer[1];
        viewer[0] = new DocumentViewer("Board Meeting", m.getTitle())
            .icon("fas-landmark")
            .status(m.getStatus(), isCompleted(m) ? "badge-completed" : "badge-pending")
            .meta("Date",      m.getMeetingDate() != null ? m.getMeetingDate().format(FMT) : null)
            .meta("Location",  m.getLocation())
            .meta("Present",   present.isEmpty()   ? null : present.size()   + " board member(s)")
            .meta("Apologies", apologies.isEmpty() ? null : apologies.size() + " board member(s)")
            .section("Agenda",  m.getAgenda(),      "No agenda recorded.")
            .section("Minutes", m.getMinutesText(), "No minutes recorded.")
            .section("Attendance", new VBox(10,
                labelled("Present",   DocumentViewer.bulletList(present,   "Nobody marked present.")),
                labelled("Apologies", DocumentViewer.bulletList(apologies, "No apologies recorded."))))
            .section("Action Items", taskList)
            .section("Documents", files)
            .action("Export", "fas-file-export", () -> exportMinutes(m, present, apologies, tasks))
            .action("Add Task", "fas-plus", () -> viewer[0].closeThen(() -> {
                openTaskDialog(null, m.getId());
                showMeetingDocument(m);
            }))
            .onEdit(() -> openMeetingDialog(m))
            .onDelete(() -> deleteMeeting(m));
        viewer[0].show();
    }

    private void exportMinutes(BoardMeeting m, List<String> present, List<String> apologies,
                               List<BoardTask> tasks) {
        DocumentExporter.exportMeetingMinutes(new DocumentExporter.MinutesDoc(
            m.getTitle(),
            m.getMeetingDate() != null ? m.getMeetingDate().format(FMT) : "—",
            nvl(m.getLocation()), m.getStatus(),
            m.getAgenda(), m.getMinutesText(), present, apologies,
            tasks.stream().map(this::toTaskRow).toList()));
    }

    private void loadAttendeeNames(int meetingId, List<String> present, List<String> apologies) {
        try {
            PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(
                "SELECT m.full_name, a.attended, a.apology FROM board_meeting_attendees a " +
                "JOIN members m ON m.id = a.member_id WHERE a.meeting_id=? ORDER BY m.full_name");
            ps.setInt(1, meetingId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                if (rs.getInt("attended") == 1)     present.add(rs.getString("full_name"));
                else if (rs.getInt("apology") == 1) apologies.add(rs.getString("full_name"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void addDocLink(VBox box, String label, String path) {
        if (path == null || path.isEmpty()) return;
        Label name = new Label(label + ":  " + new File(path).getName());
        name.setStyle(fileNameStyle());
        Button open = openFileBtn("Open");
        open.setOnAction(e -> openFileExternally(path));
        HBox row = new HBox(10, name, open);
        row.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(row);
    }

    private VBox labelled(String label, javafx.scene.Node content) {
        Label l = new Label(label.toUpperCase());
        l.getStyleClass().add("form-label");
        return new VBox(4, l, content);
    }

    private Label emptyNote(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("doc-empty");
        return l;
    }

    private void loadMeetings() {
        allMeetings.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT * FROM board_meetings ORDER BY meeting_date DESC"
            );
            while (rs.next()) {
                BoardMeeting m = new BoardMeeting();
                m.setId(rs.getInt("id"));
                m.setTitle(rs.getString("title"));
                m.setMeetingDate(rs.getDate("meeting_date").toLocalDate());
                m.setLocation(rs.getString("location"));
                m.setAgenda(rs.getString("agenda"));
                m.setMinutesText(rs.getString("minutes_text"));
                m.setStatus(rs.getString("status"));
                // Gracefully handle columns added by the SQL patch
                try { m.setAgendaDocPath(rs.getString("agenda_doc_path")); }   catch (SQLException ignored) {}
                try { m.setMinutesDocPath(rs.getString("minutes_doc_path")); } catch (SQLException ignored) {}
                allMeetings.add(m);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        refreshStats();
    }

    @FXML public void handleMeetingSearch() {
        String q = meetingSearchField.getText().toLowerCase();
        if (q.isEmpty()) { meetingsTable.setItems(allMeetings); return; }
        ObservableList<BoardMeeting> f = FXCollections.observableArrayList();
        for (BoardMeeting m : allMeetings) {
            if (m.getTitle().toLowerCase().contains(q) ||
                (m.getLocation() != null && m.getLocation().toLowerCase().contains(q)))
                f.add(m);
        }
        meetingsTable.setItems(f);
    }

    @FXML public void handleAddMeeting() { openMeetingDialog(null); }

    private void openMeetingDialog(BoardMeeting existing) {
        Stage stage = new Stage();
        com.afmvfcc.utils.Icons.setWindowIcon(stage, "fas-landmark");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(existing == null ? "New Meeting" : "Edit Meeting: " + existing.getTitle());
        Dialogs.ownByActiveWindow(stage);
        stage.setWidth(720);
        stage.setMinHeight(680);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#F5F6FA;");

        // ── Header ─────────────────────────────────────────────
        Label titleLbl = new Label(existing == null ? "New Meeting" : "Edit Meeting");
        titleLbl.setStyle("-fx-font-size:18px;-fx-font-weight:700;-fx-text-fill:#1E2130;");
        Label hintLbl = new Label("Fields marked * are required. Attendance and documents are on the other tabs.");
        hintLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#9099AA;");
        VBox header = new VBox(2, titleLbl, hintLbl);
        header.setStyle("-fx-background-color:#FFFFFF;-fx-padding:16 28;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");

        // ══════════════════════════════════════════════════════
        // TAB PANE: Details | Attendance | Documents
        // ══════════════════════════════════════════════════════
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        // ── TAB 1: Details ─────────────────────────────────────
        VBox detailsContent = new VBox(14);
        detailsContent.setStyle("-fx-padding:20 24;-fx-background-color:#F5F6FA;");

        TextField  titleField    = new TextField();
        TextField  locationField = new TextField();
        DatePicker datePicker    = new DatePicker(LocalDate.now());
        ComboBox<String> statusCombo = new ComboBox<>();
        TextArea   agendaArea    = new TextArea();
        TextArea   minutesArea   = new TextArea();

        titleField.getStyleClass().add("form-field");    titleField.setPromptText("Meeting title");
        locationField.getStyleClass().add("form-field"); locationField.setPromptText("e.g. Church Hall");
        datePicker.getStyleClass().add("form-date-picker"); datePicker.setMaxWidth(Double.MAX_VALUE);
        statusCombo.getStyleClass().add("form-combo");
        statusCombo.getItems().addAll("Upcoming", "Completed");
        statusCombo.setValue("Upcoming"); statusCombo.setMaxWidth(Double.MAX_VALUE);
        agendaArea.getStyleClass().add("form-textarea"); agendaArea.setWrapText(true);
        agendaArea.setPromptText("Paste or type the agenda...");  agendaArea.setPrefHeight(110);
        minutesArea.getStyleClass().add("form-textarea"); minutesArea.setWrapText(true);
        minutesArea.setPromptText("Paste or type the minutes..."); minutesArea.setPrefHeight(140);

        VBox meetingSection = formSection("Meeting");
        meetingSection.getChildren().addAll(
            FormBuilder.labelled("TITLE *", titleField),
            equalColumns(FormBuilder.labelled("DATE *", datePicker),
                         FormBuilder.labelled("LOCATION", locationField),
                         FormBuilder.labelled("STATUS", statusCombo)));
        VBox agendaSection = formSection("Agenda");
        agendaSection.getChildren().add(agendaArea);
        VBox minutesSection = formSection("Minutes");
        minutesSection.getChildren().add(minutesArea);
        detailsContent.getChildren().addAll(meetingSection, agendaSection, minutesSection);
        ScrollPane detailsScroll = scrollWrap(detailsContent);
        Tab detailsTab = new Tab("Details", detailsScroll);

        // ── TAB 2: Attendance ──────────────────────────────────
        VBox attendanceContent = new VBox(0);
        attendanceContent.setStyle("-fx-padding:16 24 0 24;");

        Label attHint = new Label(
            "Tick PRESENT for members who attended. Tick APOLOGY for members who sent written apologies.");
        attHint.setStyle("-fx-text-fill:#5A6275;-fx-font-size:12px;");
        attHint.setWrapText(true);

        // Column headers — fixed widths must mirror the data rows exactly
        HBox attHeader = new HBox();
        attHeader.setStyle("-fx-background-color:#F0F2F5;-fx-padding:8 12 8 12;" +
                           "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        attHeader.setAlignment(Pos.CENTER_LEFT);
        Label hName    = headerLabel("BOARD MEMBER");
        Label hRole    = headerLabel("ROLE");
        Label hPresent = headerLabel("PRESENT");
        Label hApology = headerLabel("APOLOGY");
        HBox.setHgrow(hName, Priority.ALWAYS);
        hName.setMaxWidth(Double.MAX_VALUE);
        hRole.setMinWidth(180);   hRole.setPrefWidth(180);   hRole.setMaxWidth(180);
        hPresent.setMinWidth(80); hPresent.setPrefWidth(80); hPresent.setMaxWidth(80);
        hApology.setMinWidth(80); hApology.setPrefWidth(80); hApology.setMaxWidth(80);
        hPresent.setAlignment(javafx.geometry.Pos.CENTER);
        hApology.setAlignment(javafx.geometry.Pos.CENTER);
        attHeader.getChildren().addAll(hName, hRole, hPresent, hApology);

        VBox attRows = new VBox(0);
        List<String[]> boardMembers = loadActiveBoardMembers();
        List<CheckBox[]> attChecks  = new ArrayList<>();

        for (int i = 0; i < boardMembers.size(); i++) {
            String[] bm = boardMembers.get(i);
            CheckBox presentCb = new CheckBox();
            CheckBox apologyCb = new CheckBox();

            // Mutual exclusion
            presentCb.selectedProperty().addListener((obs, o, v) -> { if (v) apologyCb.setSelected(false); });
            apologyCb.selectedProperty().addListener((obs, o, v) -> { if (v) presentCb.setSelected(false); });

            if (existing != null && existing.getId() > 0) {
                int[] att = getAttendanceRecord(existing.getId(), Integer.parseInt(bm[0]));
                presentCb.setSelected(att[0] == 1);
                apologyCb.setSelected(att[1] == 1);
            }
            attChecks.add(new CheckBox[]{presentCb, apologyCb});

            HBox row = new HBox();
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding:10 12;-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;" +
                         "-fx-background-color:" + (i % 2 == 0 ? "#FFFFFF" : "#FAFAFA") + ";");
            Label nameLabel = new Label(bm[1]);
            nameLabel.setStyle("-fx-font-size:13px;-fx-text-fill:#1E2130;");
            HBox.setHgrow(nameLabel, Priority.ALWAYS);
            nameLabel.setMaxWidth(Double.MAX_VALUE);
            Label roleLabel = new Label(bm[2]);
            roleLabel.setStyle("-fx-font-size:12px;-fx-text-fill:#5A6275;");
            // Fixed widths mirror the header exactly
            roleLabel.setMinWidth(180);   roleLabel.setPrefWidth(180);   roleLabel.setMaxWidth(180);
            presentCb.setMinWidth(80);    presentCb.setPrefWidth(80);    presentCb.setMaxWidth(80);
            apologyCb.setMinWidth(80);    apologyCb.setPrefWidth(80);    apologyCb.setMaxWidth(80);
            presentCb.setAlignment(javafx.geometry.Pos.CENTER);
            apologyCb.setAlignment(javafx.geometry.Pos.CENTER);
            row.getChildren().addAll(nameLabel, roleLabel, presentCb, apologyCb);
            attRows.getChildren().add(row);
        }

        ScrollPane attScroll = new ScrollPane(attRows);
        attScroll.setFitToWidth(true);
        attScroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        VBox.setVgrow(attScroll, Priority.ALWAYS);

        Region attSpacer = new Region(); attSpacer.setMinHeight(10);
        attendanceContent.getChildren().addAll(attHint, attSpacer, attHeader, attScroll);
        Tab attendanceTab = new Tab("Attendance", attendanceContent);

        // ── TAB 3: Documents ───────────────────────────────────
        VBox docsContent = new VBox(20);
        docsContent.setStyle("-fx-padding:20 24;");

        Label docsHint = new Label(
            "Upload the agenda and/or minutes as an existing Word (.docx) or PDF file. " +
            "The file path is saved and you can open it directly from here at any time.");
        docsHint.setStyle("-fx-text-fill:#5A6275;-fx-font-size:12px;");
        docsHint.setWrapText(true);

        final String[] agendaDocPath  = { nvl(existing != null ? existing.getAgendaDocPath()  : null) };
        final String[] minutesDocPath = { nvl(existing != null ? existing.getMinutesDocPath() : null) };

        Label agendaFileLabel  = fileLabel(agendaDocPath[0],  "No agenda document uploaded");
        Label minutesFileLabel = fileLabel(minutesDocPath[0], "No minutes document uploaded");

        Button chooseAgendaBtn  = new Button("Upload Agenda Document...");
        Button chooseMinutesBtn = new Button("Upload Minutes Document...");
        Button openAgendaBtn    = openFileBtn("Open");
        Button openMinutesBtn   = openFileBtn("Open");
        chooseAgendaBtn.getStyleClass().add("btn-secondary");
        chooseMinutesBtn.getStyleClass().add("btn-secondary");

        openAgendaBtn.setVisible(!agendaDocPath[0].isEmpty());
        openAgendaBtn.setManaged(!agendaDocPath[0].isEmpty());
        openMinutesBtn.setVisible(!minutesDocPath[0].isEmpty());
        openMinutesBtn.setManaged(!minutesDocPath[0].isEmpty());

        chooseAgendaBtn.setOnAction(e -> {
            File f = showDocChooser(stage, "Select Agenda Document");
            if (f != null) {
                agendaDocPath[0] = f.getAbsolutePath();
                agendaFileLabel.setText(f.getName());
                agendaFileLabel.setStyle(fileNameStyle());
                openAgendaBtn.setVisible(true); openAgendaBtn.setManaged(true);
            }
        });
        chooseMinutesBtn.setOnAction(e -> {
            File f = showDocChooser(stage, "Select Minutes Document");
            if (f != null) {
                minutesDocPath[0] = f.getAbsolutePath();
                minutesFileLabel.setText(f.getName());
                minutesFileLabel.setStyle(fileNameStyle());
                openMinutesBtn.setVisible(true); openMinutesBtn.setManaged(true);
            }
        });
        openAgendaBtn.setOnAction(e  -> openFileExternally(agendaDocPath[0]));
        openMinutesBtn.setOnAction(e -> openFileExternally(minutesDocPath[0]));

        docsContent.getChildren().addAll(
            docsHint,
            docSection("AGENDA DOCUMENT",  "Agenda (.docx or .pdf)",
                       agendaFileLabel,  chooseAgendaBtn,  openAgendaBtn),
            docSection("MINUTES DOCUMENT", "Minutes (.docx or .pdf)",
                       minutesFileLabel, chooseMinutesBtn, openMinutesBtn)
        );
        Tab documentsTab = new Tab("Documents", docsContent);

        tabs.getTabs().addAll(detailsTab, attendanceTab, documentsTab);

        // Pre-fill for edit mode
        if (existing != null) {
            titleField.setText(existing.getTitle());
            locationField.setText(nvl(existing.getLocation()));
            datePicker.setValue(existing.getMeetingDate());
            statusCombo.setValue(existing.getStatus());
            agendaArea.setText(nvl(existing.getAgenda()));
            minutesArea.setText(nvl(existing.getMinutesText()));
        }

        // ── Footer ─────────────────────────────────────────────
        Button saveBtn   = new Button("Save Meeting");
        Button cancelBtn = new Button("Cancel");
        saveBtn.getStyleClass().add("btn-primary");
        cancelBtn.getStyleClass().add("btn-secondary");
        HBox footer = new HBox(10, cancelBtn, saveBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color:#FFFFFF;-fx-padding:14 24;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");

        root.getChildren().addAll(header, tabs, footer);
        cancelBtn.setOnAction(e -> stage.close());

        saveBtn.setOnAction(e -> {
            String t = titleField.getText().trim();
            if (t.isEmpty()) {
                tabs.getSelectionModel().select(detailsTab);
                showAlert("Validation", "Meeting title is required.");
                return;
            }
            try {
                Connection conn = DatabaseConnection.getConnection();
                int meetingId;

                if (existing == null) {
                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO board_meetings " +
                        "(title, meeting_date, location, agenda, minutes_text, status, " +
                        " agenda_doc_path, minutes_doc_path, created_by) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS
                    );
                    ps.setString(1, t);
                    ps.setDate(2,   Date.valueOf(datePicker.getValue()));
                    ps.setString(3, locationField.getText().trim());
                    ps.setString(4, agendaArea.getText().trim());
                    ps.setString(5, minutesArea.getText().trim());
                    ps.setString(6, statusCombo.getValue());
                    ps.setString(7, agendaDocPath[0].isEmpty()  ? null : agendaDocPath[0]);
                    ps.setString(8, minutesDocPath[0].isEmpty() ? null : minutesDocPath[0]);
                    ps.setInt(9,    SessionManager.getInstance().getCurrentUser().getId());
                    ps.executeUpdate();

                    ResultSet keys = ps.getGeneratedKeys();
                    meetingId = keys.next() ? keys.getInt(1) : -1;

                    // Auto-add to calendar
                    if (meetingId > 0) {
                        addMeetingToCalendar(conn, t, datePicker.getValue(),
                                             locationField.getText().trim());
                    }
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Created board meeting: " + t);
                } else {
                    PreparedStatement ps = conn.prepareStatement(
                        "UPDATE board_meetings SET title=?, meeting_date=?, location=?, " +
                        "agenda=?, minutes_text=?, status=?, " +
                        "agenda_doc_path=?, minutes_doc_path=? WHERE id=?"
                    );
                    ps.setString(1, t);
                    ps.setDate(2,   Date.valueOf(datePicker.getValue()));
                    ps.setString(3, locationField.getText().trim());
                    ps.setString(4, agendaArea.getText().trim());
                    ps.setString(5, minutesArea.getText().trim());
                    ps.setString(6, statusCombo.getValue());
                    ps.setString(7, agendaDocPath[0].isEmpty()  ? null : agendaDocPath[0]);
                    ps.setString(8, minutesDocPath[0].isEmpty() ? null : minutesDocPath[0]);
                    ps.setInt(9,    existing.getId());
                    ps.executeUpdate();
                    meetingId = existing.getId();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Updated board meeting: " + t);
                }

                // Save attendance
                if (meetingId > 0) saveAttendance(conn, meetingId, boardMembers, attChecks);

                loadMeetings();
                loadTasks();
                stage.close();
                ToastManager.success(existing == null ? "Meeting added successfully." : "Meeting updated successfully.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                ToastManager.error("Failed to save meeting: " + ex.getMessage());
            }
        });

        Scene scene = new Scene(root, 720, 680);
        scene.getStylesheets().add(getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    // ══════════════════════════════════════════════════════════
    // ATTENDANCE
    // ══════════════════════════════════════════════════════════

    private List<String[]> loadActiveBoardMembers() {
        List<String[]> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT bm.member_id, m.full_name, bm.role_title " +
                "FROM board_members bm JOIN members m ON m.id = bm.member_id " +
                "WHERE bm.is_active = 1 ORDER BY m.full_name"
            );
            while (rs.next()) list.add(new String[]{
                rs.getString("member_id"),
                rs.getString("full_name"),
                rs.getString("role_title")
            });
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /** Returns [attended, apology] defaulting to [0,0] if no record exists. */
    private int[] getAttendanceRecord(int meetingId, int memberId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT attended, apology FROM board_meeting_attendees " +
                "WHERE meeting_id=? AND member_id=? LIMIT 1"
            );
            ps.setInt(1, meetingId); ps.setInt(2, memberId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new int[]{ rs.getInt("attended"), rs.getInt("apology") };
        } catch (SQLException e) { e.printStackTrace(); }
        return new int[]{0, 0};
    }

    private void saveAttendance(Connection conn, int meetingId,
                                 List<String[]> members, List<CheckBox[]> checks)
            throws SQLException {
        PreparedStatement del = conn.prepareStatement(
            "DELETE FROM board_meeting_attendees WHERE meeting_id=?"
        );
        del.setInt(1, meetingId);
        del.executeUpdate();

        PreparedStatement ins = conn.prepareStatement(
            "INSERT INTO board_meeting_attendees (meeting_id, member_id, attended, apology) VALUES (?,?,?,?)"
        );
        for (int i = 0; i < members.size(); i++) {
            boolean present = checks.get(i)[0].isSelected();
            boolean apology = checks.get(i)[1].isSelected();
            if (!present && !apology) continue; // skip unmarked
            ins.setInt(1, meetingId);
            ins.setInt(2, Integer.parseInt(members.get(i)[0]));
            ins.setInt(3, present ? 1 : 0);
            ins.setInt(4, apology ? 1 : 0);
            ins.addBatch();
        }
        ins.executeBatch();
    }

    // ══════════════════════════════════════════════════════════
    // CALENDAR AUTO-INSERT
    // ══════════════════════════════════════════════════════════

    /**
     * Adds the meeting as a "Meeting" category event in the calendar.
     * Skips silently if an identical entry (same title + date + category) already exists.
     */
    private void addMeetingToCalendar(Connection conn, String title,
                                       LocalDate date, String location) {
        try {
            PreparedStatement check = conn.prepareStatement(
                "SELECT id FROM events WHERE title=? AND event_date=? AND category='Meeting' LIMIT 1"
            );
            check.setString(1, title);
            check.setDate(2, Date.valueOf(date));
            if (check.executeQuery().next()) return; // already present

            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO events (title, event_date, location, description, category, created_by) " +
                "VALUES (?,?,?,?,?,?)"
            );
            ps.setString(1, title);
            ps.setDate(2,   Date.valueOf(date));
            ps.setString(3, location.isEmpty() ? null : location);
            ps.setString(4, "Board meeting (auto-added from Board Meetings)");
            ps.setString(5, "Meeting");
            ps.setInt(6,    SessionManager.getInstance().getCurrentUser().getId());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ══════════════════════════════════════════════════════════
    // DOCUMENT HELPERS
    // ══════════════════════════════════════════════════════════

    private File showDocChooser(Stage owner, String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Documents (*.docx, *.doc, *.pdf)", "*.docx","*.doc","*.pdf"),
            new FileChooser.ExtensionFilter("Word Documents", "*.docx","*.doc"),
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        return fc.showOpenDialog(owner);
    }

    private void openFileExternally(String path) {
        if (path == null || path.isEmpty()) return;
        try { java.awt.Desktop.getDesktop().open(new File(path)); }
        catch (Exception e) { showAlert("Cannot Open", "Could not open:\n" + path); }
    }

    private Label fileLabel(String path, String placeholder) {
        boolean has = path != null && !path.isEmpty();
        Label l = new Label(has ? new File(path).getName() : placeholder);
        l.setStyle(has ? fileNameStyle() : placeholderStyle());
        l.setWrapText(true);
        return l;
    }

    private Button openFileBtn(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-secondary");
        b.setStyle("-fx-padding:4 12;-fx-font-size:11px;");
        return b;
    }

    private VBox docSection(String sectionLabel, String desc,
                             Label fileLabel, Button chooseBtn, Button openBtn) {
        VBox box = new VBox(10);
        box.setStyle("-fx-background-color:#FFFFFF;-fx-padding:16;" +
                     "-fx-border-color:#DDE1EA;-fx-border-radius:8;-fx-background-radius:8;");
        Label sl = new Label(sectionLabel);
        sl.setStyle("-fx-font-size:10px;-fx-font-weight:700;-fx-text-fill:#5A6275;");
        Label dl = new Label(desc);
        dl.setStyle("-fx-font-size:11px;-fx-text-fill:#9099AA;");
        HBox btnRow = new HBox(8, chooseBtn, openBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(sl, dl, fileLabel, btnRow);
        return box;
    }

    private String fileNameStyle()    { return "-fx-font-size:12px;-fx-text-fill:#1E2130;-fx-font-weight:600;"; }
    private String placeholderStyle() { return "-fx-font-size:12px;-fx-text-fill:#9099AA;-fx-font-style:italic;"; }

    // ══════════════════════════════════════════════════════════
    // DELETE MEETING
    // ══════════════════════════════════════════════════════════

    /** Confirms, then deletes. Returns true if the meeting was removed. */
    private boolean deleteMeeting(BoardMeeting m) {
        if (m == null) return false;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Delete Meeting");
        confirm.setHeaderText("Delete \"" + m.getTitle() + "\"?");
        confirm.setContentText("Its attendance and documents links are removed too. " +
                               "Tasks raised at this meeting are kept but unlinked.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        try {
            PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement("DELETE FROM board_meetings WHERE id=?");
            ps.setInt(1, m.getId());
            ps.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                "Deleted board meeting: " + m.getTitle());
            loadMeetings();
            loadTasks();
            ToastManager.success("Meeting deleted.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to delete meeting: " + e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════
    // TASKS
    // ══════════════════════════════════════════════════════════

    private void setupTasksTable() {
        taskStatusFilter.getItems().addAll("All", "Open", "In Progress", "Overdue", "Done");
        taskStatusFilter.setValue("All");

        colTaskTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitle()));
        colTaskAssignee.setCellValueFactory(d -> new SimpleStringProperty(nvlDash(d.getValue().getAssignedName())));
        Avatars.nameColumn(colTaskAssignee, BoardTask::getAssigneePhoto);
        colTaskMeeting.setCellValueFactory(d -> new SimpleStringProperty(nvlDash(d.getValue().getMeetingTitle())));
        colTaskDue.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getDueDate() != null ? d.getValue().getDueDate().format(FMT) : "—"));
        colTaskStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDisplayStatus()));
        colTaskStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                BoardTask t = empty ? null : getTableRow().getItem();
                setText(null);
                setGraphic(t == null ? null : taskBadge(t));
            }
        });
        colTaskReport.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getReport() != null && !d.getValue().getReport().isBlank() ? "Received" : "—"));
        TableActions.viewOnly(tasksTable, colTaskActions, this::openTask);
        tasksTable.setItems(allTasks);
    }

    private void loadTasks() {
        allTasks.clear();
        try {
            ResultSet rs = DatabaseConnection.getConnection().createStatement().executeQuery("""
                SELECT t.*, m.full_name AS assignee, m.photo_path AS assignee_photo, bm.title AS meeting_title
                FROM board_tasks t
                LEFT JOIN members m         ON m.id  = t.assigned_member_id
                LEFT JOIN board_meetings bm ON bm.id = t.meeting_id
                ORDER BY (t.status = 'Done'), t.due_date IS NULL, t.due_date, t.created_at DESC
                """);
            while (rs.next()) {
                BoardTask t = new BoardTask();
                t.setId(rs.getInt("id"));
                t.setTitle(rs.getString("title"));
                t.setDescription(rs.getString("description"));
                t.setAssignedMemberId(rs.getInt("assigned_member_id"));
                t.setAssignedName(rs.getString("assignee"));
                t.setAssigneePhoto(rs.getString("assignee_photo"));
                t.setMeetingId(rs.getInt("meeting_id"));
                t.setMeetingTitle(rs.getString("meeting_title"));
                if (rs.getDate("due_date") != null) t.setDueDate(rs.getDate("due_date").toLocalDate());
                t.setStatus(rs.getString("status"));
                t.setReport(rs.getString("report"));
                if (rs.getTimestamp("reported_at") != null)
                    t.setReportedAt(rs.getTimestamp("reported_at").toLocalDateTime());
                if (rs.getTimestamp("completed_at") != null)
                    t.setCompletedAt(rs.getTimestamp("completed_at").toLocalDateTime());
                if (rs.getTimestamp("created_at") != null)
                    t.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                allTasks.add(t);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        handleTaskFilter();
        refreshStats();
    }

    @FXML public void handleTaskFilter() {
        String q      = taskSearchField.getText() == null ? "" : taskSearchField.getText().toLowerCase();
        String status = taskStatusFilter.getValue();
        ObservableList<BoardTask> f = FXCollections.observableArrayList();
        for (BoardTask t : allTasks) {
            if (!q.isEmpty()
                && !t.getTitle().toLowerCase().contains(q)
                && !(t.getAssignedName() != null && t.getAssignedName().toLowerCase().contains(q))
                && !(t.getMeetingTitle() != null && t.getMeetingTitle().toLowerCase().contains(q)))
                continue;
            if (status != null && !"All".equals(status) && !status.equals(t.getDisplayStatus())) continue;
            f.add(t);
        }
        tasksTable.setItems(f);
    }

    @FXML public void handleAddTask() { openTaskDialog(null, 0); }

    @FXML public void handleExportTasks() {
        DocumentExporter.exportBoardTasks(tasksTable.getItems().stream().map(this::toTaskRow).toList());
    }

    private DocumentExporter.TaskRow toTaskRow(BoardTask t) {
        return new DocumentExporter.TaskRow(
            t.getTitle(), nvlDash(t.getAssignedName()), nvlDash(t.getMeetingTitle()),
            t.getDueDate() != null ? t.getDueDate().format(FMT) : "—",
            t.getDisplayStatus(), nvlDash(t.getReport()));
    }

    private void openTask(BoardTask t) {
        if (t == null) return;

        DateTimeFormatter stamp = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
        new DocumentViewer("Board Task", t.getTitle())
            .icon("fas-tasks")
            .status(t.getDisplayStatus(), taskBadgeClass(t.getDisplayStatus()))
            .width(720)
            .meta("Assigned To", t.getAssignedName())
            .meta("Status",      t.getDisplayStatus())
            .meta("Raised At",   t.getMeetingTitle())
            .meta("Due Date",    t.getDueDate()    != null ? t.getDueDate().format(FMT)      : null)
            .meta("Completed",   t.getCompletedAt() != null ? t.getCompletedAt().format(stamp) : null)
            .section("Description", t.getDescription(), "No description.")
            .section("Progress Report", t.getReport(), "No report has been submitted yet.")
            .onEdit(() -> openTaskDialog(t, 0))
            .onDelete(() -> deleteTask(t))
            .show();
    }

    /**
     * Add/edit a task. {@code presetMeetingId} pre-selects the meeting when the
     * task is raised from a meeting's document view (0 = none).
     */
    private void openTaskDialog(BoardTask existing, int presetMeetingId) {
        FormBuilder f = new FormBuilder(existing == null ? "New Task" : "Edit Task").icon("fas-tasks");

        TextField titleField = new TextField();
        titleField.getStyleClass().add("form-field");
        titleField.setPromptText("What needs to be done?");

        TextArea descArea = new TextArea();
        descArea.getStyleClass().add("form-textarea");
        descArea.setWrapText(true);
        descArea.setPrefHeight(80);
        descArea.setPromptText("Details, expectations, context...");

        ComboBox<String> assigneeCombo = new ComboBox<>();
        assigneeCombo.getStyleClass().add("form-combo");
        assigneeCombo.setEditable(true);
        assigneeCombo.setPromptText("Search and select member...");
        loadAllMembersInto(assigneeCombo);

        // Meeting picker — label → id, in table order (most recent first)
        java.util.Map<String, Integer> meetingIds = new java.util.LinkedHashMap<>();
        meetingIds.put("— Not linked to a meeting —", 0);
        for (BoardMeeting m : allMeetings)
            meetingIds.put(m.getTitle() + "  (" + (m.getMeetingDate() != null ? m.getMeetingDate().format(FMT) : "—") + ")",
                           m.getId());
        ComboBox<String> meetingCombo = new ComboBox<>(FXCollections.observableArrayList(meetingIds.keySet()));
        meetingCombo.getStyleClass().add("form-combo");
        int selectedMeeting = existing != null ? existing.getMeetingId() : presetMeetingId;
        meetingCombo.setValue(meetingIds.entrySet().stream()
            .filter(en -> en.getValue() == selectedMeeting).map(java.util.Map.Entry::getKey)
            .findFirst().orElse("— Not linked to a meeting —"));

        DatePicker duePicker = new DatePicker(LocalDate.now().plusWeeks(2));
        duePicker.getStyleClass().add("form-date-picker");

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getStyleClass().add("form-combo");
        statusCombo.getItems().addAll("Open", "In Progress", "Done");
        statusCombo.setValue("Open");

        TextArea reportArea = new TextArea();
        reportArea.getStyleClass().add("form-textarea");
        reportArea.setWrapText(true);
        reportArea.setPrefHeight(110);
        reportArea.setPromptText("Feedback from the assigned person — progress, outcome or problems (optional).");

        if (existing != null) {
            titleField.setText(existing.getTitle());
            descArea.setText(nvl(existing.getDescription()));
            assigneeCombo.setValue(existing.getAssignedName());
            duePicker.setValue(existing.getDueDate());
            statusCombo.setValue(existing.getStatus());
            reportArea.setText(nvl(existing.getReport()));
        }

        f.section("Task")
         .field("TASK *", titleField)
         .field("DESCRIPTION", descArea);
        f.section("Assignment")
         .row("ASSIGNED TO *", assigneeCombo, "RAISED AT MEETING", meetingCombo)
         .row("DUE DATE", duePicker, "STATUS", statusCombo);
        f.section("Progress Report")
         .field("REPORT FROM THE ASSIGNED PERSON", reportArea);
        if (existing != null && existing.getReportedAt() != null)
            f.hint("Report last updated " +
                existing.getReportedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")));

        Button saveBtn = f.saveButton(existing == null ? "Add Task" : "Save Changes");
        saveBtn.setOnAction(e -> {
            String title    = titleField.getText().trim();
            String assignee = assigneeCombo.getValue();
            if (title.isEmpty() || assignee == null || assignee.isBlank()) {
                f.showError("Please enter the task and choose who it is assigned to.");
                return;
            }
            try {
                Connection conn = DatabaseConnection.getConnection();
                int memberId = getMemberIdByName(conn, assignee);
                if (memberId <= 0) { f.showError("\"" + assignee + "\" is not an active member."); return; }
                int meetingId   = meetingIds.getOrDefault(meetingCombo.getValue(), 0);
                String status   = statusCombo.getValue();
                String report   = reportArea.getText().trim();
                boolean reportChanged = existing == null ? !report.isEmpty() : !report.equals(nvl(existing.getReport()).trim());

                PreparedStatement ps;
                if (existing == null) {
                    ps = conn.prepareStatement(
                        "INSERT INTO board_tasks (title, description, assigned_member_id, meeting_id, due_date, " +
                        "status, report, reported_at, completed_at, created_by) " +
                        "VALUES (?,?,?,?,?,?,?, IF(?, NOW(), NULL), IF(?='Done', NOW(), NULL), ?)");
                } else {
                    ps = conn.prepareStatement(
                        "UPDATE board_tasks SET title=?, description=?, assigned_member_id=?, meeting_id=?, due_date=?, " +
                        "status=?, report=?, reported_at=IF(?, NOW(), reported_at), " +
                        "completed_at=CASE WHEN ?='Done' THEN COALESCE(completed_at, NOW()) ELSE NULL END " +
                        "WHERE id=?");
                }
                ps.setString(1, title);
                ps.setString(2, descArea.getText().trim());
                ps.setInt(3, memberId);
                if (meetingId > 0) ps.setInt(4, meetingId); else ps.setNull(4, Types.INTEGER);
                if (duePicker.getValue() != null) ps.setDate(5, Date.valueOf(duePicker.getValue()));
                else ps.setNull(5, Types.DATE);
                ps.setString(6, status);
                ps.setString(7, report.isEmpty() ? null : report);
                ps.setBoolean(8, reportChanged);
                ps.setString(9, status);
                if (existing == null) ps.setInt(10, SessionManager.getInstance().getCurrentUser().getId());
                else                  ps.setInt(10, existing.getId());
                ps.executeUpdate();

                AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    (existing == null ? "Created board task: " : "Updated board task: ") +
                    title + " (assigned to " + assignee + ", " + status + ")");
                loadTasks();
                f.close();
                ToastManager.success(existing == null ? "Task added." : "Task updated.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                ToastManager.error("Failed to save task: " + ex.getMessage());
            }
        });

        f.show(640);
    }

    /** Confirms, then deletes. Returns true if the task was removed. */
    private boolean deleteTask(BoardTask t) {
        if (t == null) return false;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Delete Task");
        confirm.setHeaderText("Delete task \"" + t.getTitle() + "\"?");
        confirm.setContentText("Its progress report is deleted too. This cannot be undone.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        try {
            PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement("DELETE FROM board_tasks WHERE id=?");
            ps.setInt(1, t.getId());
            ps.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                "Deleted board task: " + t.getTitle());
            loadTasks();
            ToastManager.success("Task deleted.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to delete task: " + e.getMessage());
            return false;
        }
    }

    private Label taskBadge(BoardTask t) {
        Label b = new Label(t.getDisplayStatus());
        b.getStyleClass().add(taskBadgeClass(t.getDisplayStatus()));
        b.setMinWidth(Region.USE_PREF_SIZE);
        return b;
    }

    private static String taskBadgeClass(String displayStatus) {
        return switch (displayStatus) {
            case "Done"        -> "badge-completed";
            case "In Progress" -> "badge-inprogress";
            case "Overdue"     -> "badge-overdue";
            default            -> "badge-pending";
        };
    }

    // ══════════════════════════════════════════════════════════
    // SHARED HELPERS
    // ══════════════════════════════════════════════════════════

    private VBox makeRow(String label, javafx.scene.Node field) {
        VBox box = new VBox(6);
        Label l = new Label(label);
        l.getStyleClass().add("form-label");
        box.getChildren().addAll(l, field);
        if (field instanceof Control) ((Control) field).setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /** White titled section card, matching FormBuilder forms. */
    private VBox formSection(String title) {
        Label t = new Label(title.toUpperCase());
        t.getStyleClass().add("form-section-title");
        VBox box = new VBox(12, t);
        box.getStyleClass().add("form-section");
        return box;
    }

    private GridPane equalColumns(javafx.scene.Node... cells) {
        GridPane g = new GridPane();
        g.setHgap(14);
        for (int i = 0; i < cells.length; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / cells.length);
            g.getColumnConstraints().add(c);
            g.add(cells[i], i, 0);
        }
        return g;
    }

    private Label headerLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:10px;-fx-font-weight:700;-fx-text-fill:#5A6275;");
        return l;
    }

    private ScrollPane scrollWrap(javafx.scene.Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        return sp;
    }

    private String nvl(String s) { return s != null ? s : ""; }

    private String nvlDash(String s) { return s != null && !s.isBlank() ? s : "—"; }

    private void loadAllMembersInto(ComboBox<String> combo) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT full_name FROM members WHERE is_deleted=0 AND is_active=1 ORDER BY full_name"
            );
            while (rs.next()) combo.getItems().add(rs.getString("full_name"));
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private int getMemberIdByName(Connection conn, String name) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
            "SELECT id FROM members WHERE full_name=? AND is_deleted=0 AND is_active=1 LIMIT 1"
        );
        ps.setString(1, name);
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getInt("id") : -1;
    }

    private int getMeetingAttendeeCount(int meetingId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM board_meeting_attendees WHERE meeting_id=? AND attended=1"
            );
            ps.setInt(1, meetingId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    private void showAlert(String title, String message) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(message);
        a.showAndWait();
    }
}
