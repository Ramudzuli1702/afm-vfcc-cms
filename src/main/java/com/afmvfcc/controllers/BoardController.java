package com.afmvfcc.controllers;

import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.models.BoardMeeting;
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

    private ObservableList<String[]>     allBoard    = FXCollections.observableArrayList();
    private ObservableList<BoardMeeting> allMeetings = FXCollections.observableArrayList();
    private final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @FXML
    public void initialize() {
        setupBoardTable();
        setupMeetingsTable();
        loadBoard();
        loadMeetings();
    }

    // ══════════════════════════════════════════════════════════
    // BOARD MEMBERS
    // ══════════════════════════════════════════════════════════

    private void setupBoardTable() {
        colBoardName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        colBoardRole.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        colBoardPhone.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        colBoardEmail.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[3]));
        colBoardStart.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[4]));
        colBoardActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn   = new Button("Edit");
            private final Button removeBtn = new Button("Remove");
            private final HBox   box       = new HBox(6, editBtn, removeBtn);
            {
                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                removeBtn.getStyleClass().add("btn-danger");
                removeBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                box.setAlignment(Pos.CENTER_LEFT);
                editBtn.setOnAction(e -> openBoardMemberDialog(
                    getTableView().getItems().get(getIndex())));
                removeBtn.setOnAction(e -> removeBoardMember(
                    Integer.parseInt(getTableView().getItems().get(getIndex())[5])));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
        boardTable.setItems(allBoard);
    }

    private void loadBoard() {
        allBoard.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = """
                SELECT bm.id, m.full_name, bm.role_title,
                       m.phone, m.email,
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
                    rs.getString("id")
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
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

    private void openBoardMemberDialog(String[] existing) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(existing == null ? "Add Board Member" : "Edit Board Member");

        VBox root = new VBox(16);
        root.setStyle("-fx-background-color:#F5F6FA;-fx-padding:24;");

        ComboBox<String> memberCombo = new ComboBox<>();
        memberCombo.getStyleClass().add("form-combo");
        memberCombo.setEditable(true);
        memberCombo.setPromptText("Search and select member...");
        loadAllMembersInto(memberCombo);

        TextField roleField = new TextField();
        roleField.getStyleClass().add("form-field");
        roleField.setPromptText("e.g. Chairman, Secretary");

        DatePicker startPicker = new DatePicker(LocalDate.now());
        startPicker.getStyleClass().add("form-date-picker");

        if (existing != null) {
            memberCombo.setValue(existing[0]);
            roleField.setText(existing[1]);
        }

        Button saveBtn   = new Button("Save");
        Button cancelBtn = new Button("Cancel");
        saveBtn.getStyleClass().add("btn-primary");
        cancelBtn.getStyleClass().add("btn-secondary");
        HBox btns = new HBox(10, cancelBtn, saveBtn);
        btns.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(
            makeRow("MEMBER *",       memberCombo),
            makeRow("ROLE / TITLE *", roleField),
            makeRow("START DATE",     startPicker),
            btns
        );

        cancelBtn.setOnAction(e -> stage.close());
        saveBtn.setOnAction(e -> {
            String member = memberCombo.getValue();
            String role   = roleField.getText().trim();
            if (member == null || role.isEmpty()) {
                showAlert("Validation", "Please select a member and enter a role.");
                return;
            }
            try {
                Connection conn = DatabaseConnection.getConnection();
                int memberId = getMemberIdByName(conn, member);
                if (memberId <= 0) { showAlert("Error", "Member not found."); return; }

                if (existing == null) {
                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO board_members (member_id, role_title, start_date, is_active) VALUES (?,?,?,1)"
                    );
                    ps.setInt(1, memberId);
                    ps.setString(2, role);
                    ps.setDate(3, Date.valueOf(startPicker.getValue()));
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Added board member: " + member + " as " + role);
                } else {
                    PreparedStatement ps = conn.prepareStatement(
                        "UPDATE board_members SET role_title=?, start_date=? WHERE id=?"
                    );
                    ps.setString(1, role);
                    ps.setDate(2, Date.valueOf(startPicker.getValue()));
                    ps.setInt(3, Integer.parseInt(existing[5]));
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Updated board member: " + member + " role to " + role);
                }
                loadBoard();
                stage.close();
                ToastManager.success(existing == null ? "Board member added successfully." : "Board member updated successfully.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                showAlert("Database Error", "Could not save board member:\n" + ex.getMessage());
                ToastManager.error("Failed to save board member: " + ex.getMessage());
            }
        });

        Scene scene = new Scene(root, 500, 320);
        scene.getStylesheets().add(getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    private void removeBoardMember(int boardId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Board Member");
        confirm.setHeaderText("Remove this member from the board?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
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
                } catch (SQLException e) {
                    e.printStackTrace();
                    ToastManager.error("Failed to remove board member: " + e.getMessage());
                }
            }
        });
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
        colMeetActions.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn   = new Button("View / Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox   box       = new HBox(6, viewBtn, deleteBtn);
            {
                viewBtn.getStyleClass().add("btn-secondary");
                viewBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                deleteBtn.getStyleClass().add("btn-danger");
                deleteBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                box.setAlignment(Pos.CENTER_LEFT);
                viewBtn.setOnAction(e -> openMeetingDialog(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> deleteMeeting(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
        meetingsTable.setItems(allMeetings);
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
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(existing == null ? "New Meeting" : "Edit Meeting: " + existing.getTitle());
        stage.setWidth(720);
        stage.setMinHeight(680);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#F5F6FA;");

        // ── Header ─────────────────────────────────────────────
        HBox header = new HBox();
        header.setStyle("-fx-background-color:#FFFFFF;-fx-padding:18 24;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        Label titleLbl = new Label(existing == null ? "New Meeting" : "Edit Meeting");
        titleLbl.setStyle("-fx-font-size:18px;-fx-font-weight:700;-fx-text-fill:#1E2130;");
        header.getChildren().add(titleLbl);

        // ══════════════════════════════════════════════════════
        // TAB PANE: Details | Attendance | Documents
        // ══════════════════════════════════════════════════════
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        // ── TAB 1: Details ─────────────────────────────────────
        VBox detailsContent = new VBox(16);
        detailsContent.setStyle("-fx-padding:20 24;");

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
        agendaArea.getStyleClass().add("form-textarea");
        agendaArea.setPromptText("Paste or type the agenda...");  agendaArea.setPrefHeight(110);
        minutesArea.getStyleClass().add("form-textarea");
        minutesArea.setPromptText("Paste or type the minutes..."); minutesArea.setPrefHeight(140);

        detailsContent.getChildren().addAll(
            makeRow("TITLE *",   titleField),
            makeRow("DATE *",    datePicker),
            makeRow("LOCATION",  locationField),
            makeRow("STATUS",    statusCombo),
            makeRow("AGENDA",    agendaArea),
            makeRow("MINUTES",   minutesArea)
        );
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

    private void deleteMeeting(BoardMeeting m) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Meeting");
        confirm.setHeaderText("Delete \"" + m.getTitle() + "\"?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    DatabaseConnection.getConnection().createStatement()
                        .executeUpdate("DELETE FROM board_meetings WHERE id=" + m.getId());
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Deleted board meeting: " + m.getTitle());
                    loadMeetings();
                } catch (SQLException e) { e.printStackTrace(); }
            }
        });
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
