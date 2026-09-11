package com.afmvfcc.controllers;

import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.utils.AnnouncementsService;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.EmailService;
import com.afmvfcc.utils.SessionManager;
import com.afmvfcc.utils.SmsService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
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
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CommunicationsController {

    // Compose
    @FXML private ComboBox<String> recipientGroup;
    @FXML private ComboBox<String> channelCombo;
    @FXML private TextField        subjectField;
    @FXML private TextArea         messageArea;
    @FXML private Label            statusLabel;

    // Sent log
    @FXML private TableView<String[]>           sentTable;
    @FXML private TableColumn<String[], String> colSentType, colSentTo,
                                                 colSentSubject, colSentDate;
    @FXML private TableColumn<String[], Void>   colSentActions;

    // ── Announcements tab ──────────────────────────────────────
    @FXML private TextArea announcementText;
    @FXML private CheckBox sendToWhatsApp;
    @FXML private CheckBox sendToFacebook;
    @FXML private Label attachmentLabel;
    @FXML private Label announcementStatus;
    @FXML private TableView<String[]> announcementTable;
    @FXML private TableColumn<String[], String> colAnnTitle, colAnnDate,
                                                   colAnnChannels, colAnnPostedBy, colAnnStatus;
    @FXML private TableColumn<String[], Void> colAnnActions;

    // State
    private File selectedAttachment;
    private final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    @FXML
    public void initialize() {
        loadRecipientGroups();

        channelCombo.getItems().addAll("Email", "SMS");
        channelCombo.setValue("Email");
        channelCombo.valueProperty().addListener((obs, old, val) ->
            subjectField.setDisable("SMS".equals(val))
        );

        setupSentTable();
        loadSentLog();

        setupAnnouncementTable();
        loadAnnouncements();
    }

    // ── RECIPIENT GROUPS ───────────────────────────────────────
    private void loadRecipientGroups() {
        recipientGroup.getItems().clear();
        recipientGroup.getItems().addAll(
            "All Active Members",
            "Full Time Members",
            "Part Time Members",
            "Board Members"
        );
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet ministries = conn.createStatement().executeQuery(
                "SELECT name FROM ministries WHERE is_active=1 ORDER BY name"
            );
            while (ministries.next())
                recipientGroup.getItems().add("Ministry: " + ministries.getString("name"));

            ResultSet branches = conn.createStatement().executeQuery(
                "SELECT name FROM sub_branches WHERE is_active=1 ORDER BY name"
            );
            while (branches.next())
                recipientGroup.getItems().add("Sub-branch: " + branches.getString("name"));

        } catch (SQLException e) { e.printStackTrace(); }

        if (!recipientGroup.getItems().isEmpty())
            recipientGroup.setValue(recipientGroup.getItems().get(0));
    }

    // ── VIEW RECIPIENTS BUTTON ─────────────────────────────────
    @FXML
    public void handleViewRecipients() {
        String group   = recipientGroup.getValue();
        String channel = channelCombo.getValue();
        if (group == null) {
            showStatus("Please select a recipient group first.", false);
            return;
        }
        List<String[]> candidates = getRecipients(group);
        if (candidates.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                "No recipients found for the selected group.");
            a.showAndWait();
            return;
        }
        showRecipientsOnlyDialog(candidates, channel, group);
    }

    private void showRecipientsOnlyDialog(List<String[]> candidates, String channel, String group) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Recipients in \"" + group + "\"  (" + candidates.size() + ")");
        stage.setMinWidth(560);
        stage.setMinHeight(500);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#F5F6FA;");

        HBox header = new HBox();
        header.setStyle("-fx-background-color:#FFFFFF;-fx-padding:16 24;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        VBox ht = new VBox(3);
        Label t = new Label("Recipients — " + group);
        t.setStyle("-fx-font-size:16px;-fx-font-weight:700;-fx-text-fill:#1E2130;");
        Label s = new Label(candidates.size() + " member(s) in this group");
        s.setStyle("-fx-font-size:12px;-fx-text-fill:#5A6275;");
        ht.getChildren().addAll(t, s);
        header.getChildren().add(ht);

        // Search
        TextField search = new TextField();
        search.setPromptText("Search by name or contact...");
        search.getStyleClass().add("form-field");
        HBox toolbar = new HBox(search);
        toolbar.setStyle("-fx-padding:10 24 8 24;");
        HBox.setHgrow(search, Priority.ALWAYS);

        // Column headers
        HBox colHdr = new HBox();
        colHdr.setStyle("-fx-background-color:#F0F2F5;-fx-padding:7 24;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        colHdr.setAlignment(Pos.CENTER_LEFT);
        Label hName = colHeaderLabel("NAME");
        HBox.setHgrow(hName, Priority.ALWAYS);
        hName.setMaxWidth(Double.MAX_VALUE);
        Label hCont = colHeaderLabel("Email".equals(channel) ? "EMAIL ADDRESS" : "PHONE NUMBER");
        hCont.setMinWidth(240);
        Label hStat = colHeaderLabel("CONTACT");
        hStat.setMinWidth(80);
        colHdr.getChildren().addAll(hName, hCont, hStat);

        VBox rowBox = new VBox(0);
        rebuildReadOnlyRows(rowBox, candidates, channel, "");
        search.textProperty().addListener((obs, o, q) ->
            rebuildReadOnlyRows(rowBox, candidates, channel, q));

        ScrollPane scroll = new ScrollPane(rowBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("btn-primary");
        closeBtn.setOnAction(e -> stage.close());
        HBox footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color:#FFFFFF;-fx-padding:12 24;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");

        root.getChildren().addAll(header, toolbar, colHdr, scroll, footer);

        Scene scene = new Scene(root, 600, 540);
        scene.getStylesheets().add(
            getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    private void rebuildReadOnlyRows(VBox rowBox, List<String[]> candidates,
                                      String channel, String query) {
        rowBox.getChildren().clear();
        String lq = query.toLowerCase();
        int[] idx = {0};
        for (String[] r : candidates) {
            String contact = "Email".equals(channel) ? r[1] : r[2];
            if (!lq.isEmpty()
                && !r[0].toLowerCase().contains(lq)
                && (contact == null || !contact.toLowerCase().contains(lq))) continue;

            boolean hasContact = contact != null && !contact.trim().isEmpty()
                && ("Email".equals(channel) ? contact.contains("@") : !contact.isBlank());

            HBox row = new HBox();
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding:8 24;-fx-border-color:#DDE1EA;" +
                         "-fx-border-width:0 0 1 0;-fx-background-color:" +
                         (idx[0] % 2 == 0 ? "#FFFFFF" : "#FAFAFA") + ";");

            Label nameLabel = new Label(r[0]);
            nameLabel.setStyle("-fx-font-size:13px;-fx-text-fill:#1E2130;");
            HBox.setHgrow(nameLabel, Priority.ALWAYS);
            nameLabel.setMaxWidth(Double.MAX_VALUE);

            String contactText = hasContact ? contact : "(no contact on record)";
            Label contactLabel = new Label(contactText);
            contactLabel.setMinWidth(240);
            contactLabel.setStyle(hasContact
                ? "-fx-font-size:12px;-fx-text-fill:#5A6275;"
                : "-fx-font-size:11px;-fx-text-fill:#C9A84C;-fx-font-style:italic;");

            Label statusLabel = new Label(hasContact ? "✓" : "✗");
            statusLabel.setMinWidth(80);
            statusLabel.setStyle(hasContact
                ? "-fx-text-fill:#2E7D4F;-fx-font-weight:700;"
                : "-fx-text-fill:#D94040;-fx-font-weight:700;");

            row.getChildren().addAll(nameLabel, contactLabel, statusLabel);
            rowBox.getChildren().add(row);
            idx[0]++;
        }
    }

    // ── SEND — opens preview dialog first ─────────────────────
    @FXML
    public void handleSend() {
        String group   = recipientGroup.getValue();
        String channel = channelCombo.getValue();
        String subject = subjectField.getText().trim();
        String message = messageArea.getText().trim();

        if (message.isEmpty()) {
            showStatus("Message body cannot be empty.", false);
            return;
        }
        if ("Email".equals(channel) && subject.isEmpty()) {
            showStatus("Email subject is required.", false);
            return;
        }

        List<String[]> candidates = getRecipients(group);
        if (candidates.isEmpty()) {
            showStatus("No recipients found for the selected group.", false);
            return;
        }

        List<String[]> confirmed = showRecipientPreviewDialog(candidates, channel, subject, message);
        if (confirmed == null) return;

        if (confirmed.isEmpty()) {
            showStatus("No recipients selected. Message not sent.", false);
            return;
        }

        dispatchMessages(confirmed, channel, group, subject, message);
    }

    // ── RECIPIENT PREVIEW DIALOG
    private List<String[]> showRecipientPreviewDialog(List<String[]> candidates,
                                                       String channel,
                                                       String subject,
                                                       String message) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Review Recipients — " + candidates.size() + " found");
        stage.setMinWidth(620);
        stage.setMinHeight(540);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#F5F6FA;");

        HBox header = new HBox();
        header.setStyle("-fx-background-color:#FFFFFF;-fx-padding:16 24;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        VBox headerText = new VBox(3);
        Label titleLbl = new Label("Review Recipients");
        titleLbl.setStyle("-fx-font-size:16px;-fx-font-weight:700;-fx-text-fill:#1E2130;");
        Label subtitleLbl = new Label(
            candidates.size() + " recipient(s) found. Uncheck anyone you want to exclude."
        );
        subtitleLbl.setStyle("-fx-font-size:12px;-fx-text-fill:#5A6275;");
        headerText.getChildren().addAll(titleLbl, subtitleLbl);
        header.getChildren().add(headerText);

        HBox toolbar = new HBox(8);
        toolbar.setStyle("-fx-padding:10 24 8 24;-fx-background-color:#F5F6FA;");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        TextField searchField = new TextField();
        searchField.setPromptText("Search by name or contact...");
        searchField.getStyleClass().add("form-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button selectAllBtn   = new Button("Select All");
        Button deselectAllBtn = new Button("Deselect All");
        selectAllBtn.getStyleClass().add("btn-secondary");
        deselectAllBtn.getStyleClass().add("btn-secondary");
        selectAllBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
        deselectAllBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");

        Label countLabel = new Label();
        countLabel.setStyle("-fx-font-size:11px;-fx-text-fill:#5A6275;-fx-min-width:80;");

        toolbar.getChildren().addAll(searchField, selectAllBtn, deselectAllBtn, countLabel);

        HBox colHeader = new HBox();
        colHeader.setStyle("-fx-background-color:#F0F2F5;-fx-padding:7 24;" +
                           "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        colHeader.setAlignment(Pos.CENTER_LEFT);

        Label hChk = colHeaderLabel("\u2714");
        hChk.setMinWidth(40); hChk.setPrefWidth(40); hChk.setMaxWidth(40);

        Label hName = colHeaderLabel("NAME");
        HBox.setHgrow(hName, Priority.ALWAYS);
        hName.setMaxWidth(Double.MAX_VALUE);

        Label hCont = colHeaderLabel("Email".equals(channel) ? "EMAIL ADDRESS" : "PHONE NUMBER");
        hCont.setMinWidth(240); hCont.setPrefWidth(240); hCont.setMaxWidth(240);

        colHeader.getChildren().addAll(hChk, hName, hCont);

        Map<String[], CheckBox> checkMap = new LinkedHashMap<>();
        for (String[] r : candidates) {
            String contact = "Email".equals(channel) ? r[1] : r[2];
            boolean hasContact = contact != null && !contact.trim().isEmpty()
                && ("Email".equals(channel) ? contact.contains("@") : !contact.isBlank());
            CheckBox cb = new CheckBox();
            cb.setSelected(hasContact);
            checkMap.put(r, cb);
        }

        Runnable updateCount = () -> {
            long sel = checkMap.values().stream().filter(CheckBox::isSelected).count();
            countLabel.setText(sel + " / " + candidates.size() + " selected");
        };
        checkMap.values().forEach(cb -> cb.selectedProperty().addListener((o, a, b) -> updateCount.run()));
        updateCount.run();

        VBox rowBox = new VBox(0);
        rebuildRows(rowBox, checkMap, channel, "");

        searchField.textProperty().addListener((obs, old, q) ->
            rebuildRows(rowBox, checkMap, channel, q));

        selectAllBtn.setOnAction(e -> {
            checkMap.forEach((r, cb) -> {
                String contact = "Email".equals(channel) ? r[1] : r[2];
                if (contact != null && !contact.isBlank()) cb.setSelected(true);
            });
        });
        deselectAllBtn.setOnAction(e -> checkMap.values().forEach(cb -> cb.setSelected(false)));

        ScrollPane scroll = new ScrollPane(rowBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox previewBox = new VBox(4);
        previewBox.setStyle("-fx-background-color:#FFFFFF;-fx-padding:10 24;" +
                            "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");
        if ("Email".equals(channel) && !subject.isEmpty()) {
            Label sl = new Label("Subject: " + subject);
            sl.setStyle("-fx-font-size:11px;-fx-font-weight:600;-fx-text-fill:#1E2130;");
            previewBox.getChildren().add(sl);
        }
        String preview = message.length() > 130 ? message.substring(0, 130) + "…" : message;
        Label ml = new Label(preview);
        ml.setStyle("-fx-font-size:11px;-fx-text-fill:#5A6275;");
        ml.setWrapText(true);
        previewBox.getChildren().add(ml);

        final boolean[] cancelled = {true};
        final List<String[]> result = new ArrayList<>();

        Button sendBtn   = new Button("Send to Selected");
        Button cancelBtn = new Button("Cancel");
        sendBtn.getStyleClass().add("btn-primary");
        cancelBtn.getStyleClass().add("btn-secondary");

        HBox footer = new HBox(10, cancelBtn, sendBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color:#FFFFFF;-fx-padding:12 24;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");

        cancelBtn.setOnAction(e -> stage.close());
        sendBtn.setOnAction(e -> {
            checkMap.forEach((r, cb) -> { if (cb.isSelected()) result.add(r); });
            cancelled[0] = false;
            stage.close();
        });

        root.getChildren().addAll(header, toolbar, colHeader, scroll, previewBox, footer);

        Scene scene = new Scene(root, 660, 580);
        scene.getStylesheets().add(
            getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();

        return cancelled[0] ? null : result;
    }

    private void rebuildRows(VBox rowBox, Map<String[], CheckBox> checkMap,
                             String channel, String query) {
        rowBox.getChildren().clear();
        String lq = query.toLowerCase();
        int[] idx = {0};
        checkMap.forEach((r, cb) -> {
            String contact = "Email".equals(channel) ? r[1] : r[2];
            if (!lq.isEmpty()
                && !r[0].toLowerCase().contains(lq)
                && (contact == null || !contact.toLowerCase().contains(lq))) return;
            boolean hasContact = contact != null && !contact.trim().isEmpty()
                && ("Email".equals(channel) ? contact.contains("@") : !contact.isBlank());
            rowBox.getChildren().add(
                buildRecipientRow(r[0], contact, hasContact, cb, idx[0]++));
        });
    }

    private HBox buildRecipientRow(String name, String contact,
                                    boolean hasContact, CheckBox cb, int index) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding:8 24;-fx-border-color:#DDE1EA;" +
                     "-fx-border-width:0 0 1 0;-fx-background-color:" +
                     (index % 2 == 0 ? "#FFFFFF" : "#FAFAFA") + ";");
        row.setOnMouseClicked(e -> cb.setSelected(!cb.isSelected()));

        HBox cbWrap = new HBox(cb);
        cbWrap.setMinWidth(40); cbWrap.setPrefWidth(40); cbWrap.setMaxWidth(40);
        cbWrap.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size:13px;-fx-text-fill:#1E2130;");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        String contactText = hasContact ? contact : "(no contact on record)";
        Label contactLabel = new Label(contactText);
        contactLabel.setMinWidth(240); contactLabel.setPrefWidth(240); contactLabel.setMaxWidth(240);
        contactLabel.setStyle(hasContact
            ? "-fx-font-size:12px;-fx-text-fill:#5A6275;"
            : "-fx-font-size:11px;-fx-text-fill:#C9A84C;-fx-font-style:italic;");

        row.getChildren().addAll(cbWrap, nameLabel, contactLabel);
        return row;
    }

    private Label colHeaderLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:10px;-fx-font-weight:700;-fx-text-fill:#5A6275;");
        return l;
    }

    // ── DISPATCH ──────────────────────────────────────────────
    private void dispatchMessages(List<String[]> recipients, String channel,
                                   String group, String subject, String message) {
        showStatus("Sending to " + recipients.size() + " recipient(s)…", true);

        Task<int[]> task = new Task<>() {
            @Override
            protected int[] call() {
                int success = 0, failed = 0;
                for (String[] r : recipients) {
                    String error = "Email".equals(channel)
                        ? sendEmail(r[1], subject, message)
                        : sendSms(r[2], message);
                    if (error == null) success++; else failed++;
                }
                return new int[]{success, failed};
            }
        };

        task.setOnSucceeded(e -> {
            int[] res   = task.getValue();
            int success = res[0], failed = res[1];
            int total   = success + failed;

            logCommunication(channel, group, subject, message, total, success);
            AuditLogger.log(
                SessionManager.getInstance().getCurrentUser().getId(),
                "Sent " + channel + " to '" + group + "' — " +
                success + "/" + total + " delivered.");

            if (failed == 0)
                showStatus("✓ Sent to all " + success + " recipient(s).", true);
            else
                showStatus(success + " sent, " + failed + " failed. Check addresses/settings.", false);

            loadSentLog();
            messageArea.clear();
            subjectField.clear();
        });

        task.setOnFailed(e ->
            showStatus("Send failed: " + task.getException().getMessage(), false));
        new Thread(task).start();
    }

    private String sendEmail(String email, String subject, String body) {
        if (email == null || email.trim().isEmpty() || !email.contains("@")) return null;
        return EmailService.send(email.trim(), subject, body);
    }

    private String sendSms(String phone, String message) {
        if (phone == null || phone.trim().isEmpty()) return null;
        return SmsService.send(phone.trim(), message);
    }

    // ── RECIPIENTS ────────────────────────────────────────────
    private List<String[]> getRecipients(String group) {
        List<String[]> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = buildRecipientQuery(group);
            if (sql == null) return list;
            ResultSet rs = conn.createStatement().executeQuery(sql);
            while (rs.next()) {
                list.add(new String[]{
                    rs.getString("full_name"),
                    rs.getString("email") != null ? rs.getString("email") : "",
                    rs.getString("phone") != null ? rs.getString("phone") : ""
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    private String buildRecipientQuery(String group) {
        if (group == null) return null;

        String base        = "SELECT m.full_name, m.email, m.phone FROM members m ";
        String aliveFilter = "m.is_deleted = 0 AND m.is_deceased = 0";

        if (group.startsWith("Ministry: ")) {
            String name = group.substring("Ministry: ".length()).replace("'", "''");
            return base +
                "JOIN member_ministries mm ON mm.member_id = m.id " +
                "JOIN ministries mi ON mi.id = mm.ministry_id " +
                "WHERE mi.name = '" + name + "' AND " + aliveFilter;
        }

        if (group.startsWith("Sub-branch: ")) {
            String name = group.substring("Sub-branch: ".length()).replace("'", "''");
            return base +
                "JOIN sub_branches sb ON sb.id = m.sub_branch_id " +
                "WHERE sb.name = '" + name + "' AND " + aliveFilter;
        }

        return switch (group) {
            case "All Active Members" ->
                base + "WHERE " + aliveFilter + " AND m.is_active = 1";
            case "Full Time Members"  ->
                base + "WHERE " + aliveFilter + " AND m.is_full_time = 1";
            case "Part Time Members"  ->
                base + "WHERE " + aliveFilter + " AND m.is_full_time = 0 AND m.is_active = 1";
            case "Board Members"      ->
                base + "JOIN board_members bm ON bm.member_id = m.id " +
                "WHERE bm.is_active = 1 AND " + aliveFilter;
            default ->
                base + "WHERE " + aliveFilter + " AND m.is_active = 1";
        };
    }

    // ── SENT LOG ──────────────────────────────────────────────
    private void setupSentTable() {
        colSentType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        colSentTo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        colSentSubject.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        colSentDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[3]));

        if (colSentActions != null) {
            colSentActions.setCellFactory(col -> new TableCell<String[], Void>() {
                private final Button viewBtn  = new Button("View");
                private final Button namesBtn = new Button("Recipients");
                private final HBox   box      = new HBox(6, viewBtn, namesBtn);
                {
                    viewBtn.getStyleClass().add("btn-secondary");
                    viewBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                    namesBtn.getStyleClass().add("btn-secondary");
                    namesBtn.setStyle("-fx-padding:4 10;-fx-font-size:11px;");
                    box.setAlignment(Pos.CENTER_LEFT);
                    viewBtn.setOnAction(e -> {
                        int idx = getIndex();
                        if (idx >= 0 && idx < getTableView().getItems().size())
                            openSentMessageDialog(getTableView().getItems().get(idx));
                    });
                    namesBtn.setOnAction(e -> {
                        int idx = getIndex();
                        if (idx >= 0 && idx < getTableView().getItems().size()) {
                            String[] row = getTableView().getItems().get(idx);
                            // row[1] = recipient group name, row[0] = channel
                            List<String[]> recs = getRecipients(row[1]);
                            showRecipientsOnlyDialog(recs, row[0], row[1]);
                        }
                    });
                }
                @Override protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : box);
                }
            });
        }

        sentTable.setRowFactory(tv -> {
            TableRow<String[]> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty())
                    openSentMessageDialog(row.getItem());
            });
            return row;
        });

        sentTable.setItems(FXCollections.observableArrayList());
    }

    private void loadSentLog() {
        ObservableList<String[]> log = FXCollections.observableArrayList();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT channel, recipient_group, subject, sent_at, " +
                "message_body, recipient_count " +
                "FROM communications ORDER BY sent_at DESC LIMIT 50"
            );
            while (rs.next()) {
                log.add(new String[]{
                    rs.getString("channel"),
                    rs.getString("recipient_group"),
                    rs.getString("subject")      != null ? rs.getString("subject")      : "—",
                    rs.getTimestamp("sent_at")   != null
                        ? rs.getTimestamp("sent_at").toLocalDateTime().format(FMT)      : "—",
                    rs.getString("message_body") != null ? rs.getString("message_body") : "",
                    rs.getString("recipient_count") != null
                        ? rs.getString("recipient_count")                               : "0"
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        sentTable.setItems(log);
    }

    private void openSentMessageDialog(String[] row) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Sent Message");
        stage.setWidth(560);
        stage.setMinHeight(400);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#F5F6FA;");

        HBox header = new HBox();
        header.setStyle("-fx-background-color:#FFFFFF;-fx-padding:16 24;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        VBox headerText = new VBox(3);
        Label titleLbl = new Label("Email".equals(row[0]) ? "Email Message" : "SMS Message");
        titleLbl.setStyle("-fx-font-size:16px;-fx-font-weight:700;-fx-text-fill:#1E2130;");
        Label dateLbl = new Label("Sent: " + row[3] + "   |   " + row[5] + " recipient(s)");
        dateLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#9099AA;");
        headerText.getChildren().addAll(titleLbl, dateLbl);
        header.getChildren().add(headerText);

        VBox meta = new VBox(10);
        meta.setStyle("-fx-padding:16 24 8 24;-fx-background-color:#F5F6FA;");
        meta.getChildren().add(metaField("CHANNEL", row[0]));
        meta.getChildren().add(metaField("SENT TO GROUP", row[1]));
        if (!"—".equals(row[2]) && !row[2].isEmpty()) {
            meta.getChildren().add(metaField("SUBJECT", row[2]));
        }

        VBox bodyBox = new VBox(6);
        bodyBox.setStyle("-fx-padding:0 24 16 24;");
        Label bodyLbl = new Label("MESSAGE");
        bodyLbl.setStyle("-fx-font-size:10px;-fx-font-weight:700;-fx-text-fill:#5A6275;");

        TextArea bodyArea = new TextArea(row[4]);
        bodyArea.setEditable(false);
        bodyArea.setWrapText(true);
        bodyArea.setPrefHeight(200);
        bodyArea.setStyle("-fx-background-color:#FFFFFF;-fx-border-color:#DDE1EA;" +
                          "-fx-border-radius:6;-fx-background-radius:6;" +
                          "-fx-font-size:13px;-fx-text-fill:#1E2130;");
        VBox.setVgrow(bodyArea, Priority.ALWAYS);
        bodyBox.getChildren().addAll(bodyLbl, bodyArea);

        Button viewRecipientsBtn = new Button("View Recipients");
        viewRecipientsBtn.getStyleClass().add("btn-secondary");
        viewRecipientsBtn.setOnAction(e -> {
            List<String[]> recs = getRecipients(row[1]);
            showRecipientsOnlyDialog(recs, row[0], row[1]);
        });

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("btn-primary");
        closeBtn.setOnAction(e -> stage.close());
        HBox footer = new HBox(10, viewRecipientsBtn, closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color:#FFFFFF;-fx-padding:12 24;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");

        root.getChildren().addAll(header, meta, bodyBox, footer);
        VBox.setVgrow(bodyBox, Priority.ALWAYS);

        Scene scene = new Scene(root, 560, 480);
        scene.getStylesheets().add(
            getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    private void logCommunication(String channel, String group, String subject,
                                   String message, int total, int sent) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO communications " +
                "(channel, recipient_group, subject, message_body, sent_by, recipient_count) " +
                "VALUES (?,?,?,?,?,?)"
            );
            ps.setString(1, channel);
            ps.setString(2, group);
            ps.setString(3, subject.isEmpty() ? null : subject);
            ps.setString(4, message);
            ps.setInt(5, SessionManager.getInstance().getCurrentUser().getId());
            ps.setInt(6, total);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ── ANNOUNCEMENTS TAB ─────────────────────────────────────

    @FXML
    public void handleAttachFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Attachment");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Supported",
                "*.pdf", "*.docx", "*.doc", "*.jpg", "*.jpeg", "*.png", "*.mp4", "*.mov"),
            new FileChooser.ExtensionFilter("PDF Documents", "*.pdf"),
            new FileChooser.ExtensionFilter("Word Documents", "*.docx", "*.doc"),
            new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"),
            new FileChooser.ExtensionFilter("Videos", "*.mp4", "*.mov")
        );
        File file = chooser.showOpenDialog(announcementText.getScene().getWindow());
        if (file != null) {
            selectedAttachment = file;
            long kb = file.length() / 1024;
            String size = kb > 1024 ? (kb / 1024) + " MB" : kb + " KB";
            attachmentLabel.setText("📎 " + file.getName() + " (" + size + ")");
            attachmentLabel.setStyle("-fx-text-fill:#1B3A6B;-fx-font-size:12px;");
        }
    }

    @FXML
    public void handleRemoveAttachment() {
        selectedAttachment = null;
        attachmentLabel.setText("No attachment");
        attachmentLabel.setStyle("-fx-text-fill:#9099AA;-fx-font-size:12px;");
    }

    @FXML
    public void handlePostAnnouncement() {
        String text = announcementText.getText().trim();
        if (text.isEmpty()) {
            showAnnStatus("Please write an announcement first.", false);
            return;
        }
        if (!sendToWhatsApp.isSelected() && !sendToFacebook.isSelected()) {
            showAnnStatus("Select at least one channel (WhatsApp or Facebook).", false);
            return;
        }

        boolean toWA = sendToWhatsApp.isSelected();
        boolean toFB = sendToFacebook.isSelected();
        File attach = selectedAttachment;

        showAnnStatus("Posting announcement...", true);

        Task<String[]> task = new Task<>() {
            @Override
            protected String[] call() {
                String waResult = null, fbResult = null;
                String waPostId = null, fbPostId = null;

                if (toWA) {
                    AnnouncementsService.PostResult result;
                    if (attach != null) {
                        result = AnnouncementsService.sendWhatsAppWithAttachment(text, attach);
                    } else {
                        result = AnnouncementsService.sendWhatsApp(text);
                    }
                    waResult = result.success ? "✓ Sent" : "✗ " + result.message;
                    waPostId = result.postId;
                }

                if (toFB) {
                    AnnouncementsService.PostResult result;
                    if (attach != null) {
                        String fname = attach.getName().toLowerCase();
                        boolean isImage = fname.endsWith(".jpg") || fname.endsWith(".jpeg")
                                       || fname.endsWith(".png");
                        if (isImage) {
                            result = AnnouncementsService.postToFacebookWithImage(text, attach);
                        } else {
                            result = AnnouncementsService.postToFacebookWithDocument(text, attach);
                        }
                    } else {
                        result = AnnouncementsService.postToFacebook(text);
                    }
                    fbResult = result.success ? "✓ Posted" : "✗ " + result.message;
                    fbPostId = result.postId;
                }
                return new String[]{ waResult, fbResult, waPostId, fbPostId };
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            String[] results = task.getValue();
            String waResult = results[0], fbResult = results[1];
            String waPostId = results[2], fbPostId = results[3];

            StringBuilder status = new StringBuilder();
            if (waResult != null) status.append("WhatsApp: ").append(waResult);
            if (fbResult != null) {
                if (status.length() > 0) status.append(" | ");
                status.append("Facebook: ").append(fbResult);
            }

            boolean anySuccess = (waResult != null && waResult.startsWith("✓"))
                              || (fbResult != null && fbResult.startsWith("✓"));
            showAnnStatus(status.toString(), anySuccess);

            List<String> channels = new ArrayList<>();
            if (toWA) channels.add("WhatsApp");
            if (toFB) channels.add("Facebook");
            String channelStr = String.join(", ", channels);

            List<String> statuses = new ArrayList<>();
            if (waResult != null) statuses.add("WA: " + (waResult.startsWith("✓") ? "Sent" : "Failed"));
            if (fbResult != null) statuses.add("FB: " + (fbResult.startsWith("✓") ? "Posted" : "Failed"));
            String dbStatus = String.join(" | ", statuses);

            saveAnnouncement(text, channelStr,
                attach != null ? attach.getAbsolutePath() : null,
                attach != null ? attach.getName() : null,
                waPostId, fbPostId, dbStatus);

            AuditLogger.log(
                SessionManager.getInstance().getCurrentUser().getId(),
                "Posted announcement to " + channelStr + ": " +
                    text.substring(0, Math.min(80, text.length())) + "...");

            announcementText.clear();
            handleRemoveAttachment();
            sendToWhatsApp.setSelected(false);
            sendToFacebook.setSelected(false);
            loadAnnouncements();
        }));

        task.setOnFailed(e -> Platform.runLater(() ->
            showAnnStatus("Error: " + task.getException().getMessage(), false)));

        new Thread(task).start();
    }

    private void setupAnnouncementTable() {
        colAnnTitle.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue()[0].length() > 60 ? d.getValue()[0].substring(0, 60) + "…" : d.getValue()[0]));
        colAnnDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        colAnnChannels.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        colAnnPostedBy.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[3]));
        colAnnStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[4]));

        colAnnActions.setCellFactory(col -> new TableCell<String[], Void>() {
            private final Button viewBtn = new Button("View");
            {
                viewBtn.getStyleClass().add("btn-secondary");
                viewBtn.setStyle("-fx-padding:4 12;-fx-font-size:11px;");
                viewBtn.setOnAction(e -> {
                    if (getTableRow() != null && getTableRow().getItem() != null)
                        openAnnouncementDialog(getTableRow().getItem());
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : viewBtn);
            }
        });

        announcementTable.setRowFactory(tv -> {
            TableRow<String[]> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty())
                    openAnnouncementDialog(row.getItem());
            });
            return row;
        });

        announcementTable.setItems(FXCollections.observableArrayList());
    }

    private void loadAnnouncements() {
        ObservableList<String[]> rows = FXCollections.observableArrayList();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT a.announcement_text, a.posted_at, a.channels, " +
                "u.full_name, a.status, a.attachment_name, a.id " +
                "FROM announcements a " +
                "LEFT JOIN users u ON u.id = a.posted_by " +
                "ORDER BY a.posted_at DESC");
            while (rs.next()) {
                rows.add(new String[]{
                    rs.getString("announcement_text") != null ? rs.getString("announcement_text") : "",
                    rs.getTimestamp("posted_at") != null
                        ? rs.getTimestamp("posted_at").toLocalDateTime().format(FMT) : "—",
                    rs.getString("channels") != null ? rs.getString("channels") : "—",
                    rs.getString("full_name") != null ? rs.getString("full_name") : "—",
                    rs.getString("status") != null ? rs.getString("status") : "—",
                    rs.getString("attachment_name") != null ? rs.getString("attachment_name") : "",
                    String.valueOf(rs.getInt("id"))
                });
            }
        } catch (SQLException e) {
            System.err.println("Announcements table not ready: " + e.getMessage());
        }
        announcementTable.setItems(rows);
    }

    private void openAnnouncementDialog(String[] row) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Announcement");
        stage.setWidth(560);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#F5F6FA;");

        HBox header = new HBox();
        header.setStyle("-fx-background-color:#FFFFFF;-fx-padding:16 24;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        VBox ht = new VBox(3);
        Label t = new Label("Announcement");
        t.setStyle("-fx-font-size:16px;-fx-font-weight:700;-fx-text-fill:#1E2130;");
        Label d = new Label("Posted: " + row[1] + " | Channels: " + row[2]);
        d.setStyle("-fx-font-size:11px;-fx-text-fill:#9099AA;");
        ht.getChildren().addAll(t, d);
        header.getChildren().add(ht);

        VBox meta = new VBox(10);
        meta.setStyle("-fx-padding:16 24 8 24;");
        meta.getChildren().add(metaField("POSTED BY", row[3]));
        meta.getChildren().add(metaField("STATUS", row[4]));
        if (!row[5].isEmpty())
            meta.getChildren().add(metaField("ATTACHMENT", row[5]));

        VBox bodyBox = new VBox(6);
        bodyBox.setStyle("-fx-padding:0 24 16 24;");
        Label bl = new Label("ANNOUNCEMENT TEXT");
        bl.setStyle("-fx-font-size:10px;-fx-font-weight:700;-fx-text-fill:#5A6275;");
        TextArea ba = new TextArea(row[0]);
        ba.setEditable(false);
        ba.setWrapText(true);
        ba.setPrefHeight(200);
        ba.setStyle("-fx-background-color:#FFFFFF;-fx-border-color:#DDE1EA;" +
                    "-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;");
        VBox.setVgrow(ba, Priority.ALWAYS);
        bodyBox.getChildren().addAll(bl, ba);

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("btn-primary");
        closeBtn.setOnAction(e -> stage.close());
        HBox footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color:#FFFFFF;-fx-padding:12 24;" +
                        "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");

        root.getChildren().addAll(header, meta, bodyBox, footer);
        VBox.setVgrow(bodyBox, Priority.ALWAYS);

        Scene scene = new Scene(root, 560, 500);
        scene.getStylesheets().add(
            getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    private void saveAnnouncement(String text, String channels,
                                   String attachPath, String attachName,
                                   String waPostId, String fbPostId, String status) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO announcements " +
                "(announcement_text, channels, attachment_path, attachment_name, " +
                " whatsapp_post_id, facebook_post_id, status, posted_by) " +
                "VALUES (?,?,?,?,?,?,?,?)");
            ps.setString(1, text);
            ps.setString(2, channels);
            ps.setString(3, attachPath);
            ps.setString(4, attachName);
            ps.setString(5, waPostId);
            ps.setString(6, fbPostId);
            ps.setString(7, status);
            ps.setInt(8, SessionManager.getInstance().getCurrentUser().getId());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ── HELPERS ───────────────────────────────────────────────
    private VBox metaField(String label, String value) {
        VBox box = new VBox(3);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size:10px;-fx-font-weight:700;-fx-text-fill:#5A6275;");
        Label val = new Label(value);
        val.setStyle("-fx-font-size:13px;-fx-text-fill:#1E2130;");
        val.setWrapText(true);
        box.getChildren().addAll(lbl, val);
        return box;
    }

    private void showStatus(String msg, boolean success) {
        statusLabel.setText(msg);
        statusLabel.setStyle(success
            ? "-fx-text-fill:#2E7D4F;-fx-font-size:12px;-fx-font-weight:600;"
            : "-fx-text-fill:#D94040;-fx-font-size:12px;-fx-font-weight:600;");
        statusLabel.setVisible(true);
    }

    private void showAnnStatus(String msg, boolean success) {
        announcementStatus.setText(msg);
        announcementStatus.setStyle(success
            ? "-fx-text-fill:#2E7D4F;-fx-font-size:12px;-fx-font-weight:600;"
            : "-fx-text-fill:#D94040;-fx-font-size:12px;-fx-font-weight:600;");
        announcementStatus.setVisible(true);
        announcementStatus.setManaged(true);
    }
}