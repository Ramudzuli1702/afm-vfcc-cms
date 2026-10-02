package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.models.Event;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.Dialogs;
import com.afmvfcc.utils.DocumentViewer;
import com.afmvfcc.utils.FormBuilder;
import com.afmvfcc.utils.SessionManager;
import com.afmvfcc.utils.ToastManager;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CalendarController {

    @FXML private Label         monthYearLabel;
    @FXML private GridPane      calendarGrid;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private Label statMonth, statWeek, statUpcoming, statServices;

    private YearMonth currentMonth = YearMonth.now();
    private final List<Event> allEvents = new ArrayList<>();
    private final DateTimeFormatter FMT      = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private final DateTimeFormatter FMT_DISP = DateTimeFormatter.ofPattern("MMMM yyyy");
    private final DateTimeFormatter FMT_MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy");

    @FXML
    public void initialize() {
        categoryFilter.getItems().addAll("All","Service","Meeting","Outreach","Youth","Other");
        categoryFilter.setValue("All");
        categoryFilter.setOnAction(e -> refreshCalendar());
        loadEvents();
    }

    // -- NAVIGATION --------------------------------------------

    @FXML public void handlePrevMonth() { currentMonth = currentMonth.minusMonths(1); refreshCalendar(); }
    @FXML public void handleNextMonth() { currentMonth = currentMonth.plusMonths(1); refreshCalendar(); }
    @FXML public void handleToday()     { currentMonth = YearMonth.now();             refreshCalendar(); }
    @FXML public void handleAddEvent()  { openEventDialog(null); }

    // -- PRINT EVENTS ------------------------------------------

    @FXML
    public void handlePrintEvents() {
        FormBuilder f = new FormBuilder("Print / Export Events", "Choose which events to include.").icon("fas-print");

        // Range selector
        ToggleGroup rangeGroup = new ToggleGroup();
        RadioButton rbMonth   = new RadioButton("Current month (" + currentMonth.format(FMT_MONTH_YEAR) + ")");
        RadioButton rbYear    = new RadioButton("Current year (" + currentMonth.getYear() + ")");
        RadioButton rbCustom  = new RadioButton("Custom date range");
        rbMonth.setToggleGroup(rangeGroup);
        rbYear.setToggleGroup(rangeGroup);
        rbCustom.setToggleGroup(rangeGroup);
        rbMonth.setSelected(true);
        rbMonth.setStyle("-fx-font-size:13px;-fx-text-fill:#1E2130;");
        rbYear.setStyle("-fx-font-size:13px;-fx-text-fill:#1E2130;");
        rbCustom.setStyle("-fx-font-size:13px;-fx-text-fill:#1E2130;");

        DatePicker fromPicker = new DatePicker(currentMonth.atDay(1));
        DatePicker toPicker   = new DatePicker(currentMonth.atEndOfMonth());
        fromPicker.getStyleClass().add("form-date-picker");
        toPicker.getStyleClass().add("form-date-picker");
        fromPicker.setMaxWidth(Double.MAX_VALUE);
        toPicker.setMaxWidth(Double.MAX_VALUE);

        GridPane dateRangeRow = new GridPane();
        dateRangeRow.setHgap(14);
        ColumnConstraints half = new ColumnConstraints();
        half.setPercentWidth(50);
        dateRangeRow.getColumnConstraints().addAll(half, half);
        dateRangeRow.add(FormBuilder.labelled("FROM", fromPicker), 0, 0);
        dateRangeRow.add(FormBuilder.labelled("TO", toPicker), 1, 0);
        dateRangeRow.setDisable(true);

        rbCustom.selectedProperty().addListener((obs, o, n) -> dateRangeRow.setDisable(!n));

        // Category filter for print
        ComboBox<String> catCombo = new ComboBox<>();
        catCombo.getStyleClass().add("form-combo");
        catCombo.getItems().addAll("All","Service","Meeting","Outreach","Youth","Other");
        catCombo.setValue("All");
        catCombo.setMaxWidth(Double.MAX_VALUE);

        f.section("Range")
         .node(new VBox(10, rbMonth, rbYear, rbCustom))
         .node(dateRangeRow);
        f.section("Filter")
         .field("CATEGORY", catCombo);

        Button printBtn = f.saveButton("Export");

        printBtn.setOnAction(e -> {
            LocalDate from, to;
            if (rbMonth.isSelected()) {
                from = currentMonth.atDay(1);
                to   = currentMonth.atEndOfMonth();
            } else if (rbYear.isSelected()) {
                from = LocalDate.of(currentMonth.getYear(), 1, 1);
                to   = LocalDate.of(currentMonth.getYear(), 12, 31);
            } else {
                from = fromPicker.getValue();
                to   = toPicker.getValue();
                if (from == null || to == null || from.isAfter(to)) {
                    f.showError("Please choose a valid date range (the From date must be on or before the To date).");
                    return;
                }
            }
            String cat = catCombo.getValue();
            f.close();
            exportEvents(from, to, cat);
        });

        f.show(540);
    }

    private void exportEvents(LocalDate from, LocalDate to, String category) {
        List<Event> filtered = new ArrayList<>();
        for (Event ev : allEvents) {
            if (ev.getEventDate() == null) continue;
            if (ev.getEventDate().isBefore(from) || ev.getEventDate().isAfter(to)) continue;
            if (!"All".equals(category) && !category.equals(ev.getCategory())) continue;
            filtered.add(ev);
        }
        filtered.sort(Comparator.comparing(Event::getEventDate));

        if (filtered.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                "No events found for the selected range and category.");
            Main.applyStyles(a.getDialogPane());
            a.showAndWait();
            return;
        }

        String rangeLabel = from.format(FMT) + " to " + to.format(FMT);
        DocumentExporter.exportEvents(filtered, rangeLabel, category);
    }

    // -- LOAD DATA ---------------------------------------------

    private void loadEvents() {
        allEvents.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT * FROM events ORDER BY event_date ASC"
            );
            while (rs.next()) {
                Event e = new Event();
                e.setId(rs.getInt("id"));
                e.setTitle(rs.getString("title"));
                if (rs.getDate("event_date") != null)
                    e.setEventDate(rs.getDate("event_date").toLocalDate());
                if (rs.getTime("event_time") != null)
                    e.setEventTime(rs.getTime("event_time").toLocalTime());
                e.setLocation(rs.getString("location"));
                e.setDescription(rs.getString("description"));
                e.setCategory(rs.getString("category"));
                allEvents.add(e);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        refreshCalendar();
    }

    // -- BUILD CALENDAR GRID -----------------------------------

    private void refreshStats() {
        LocalDate today = LocalDate.now();
        long month = 0, week = 0, upcoming = 0, services = 0;
        for (Event ev : allEvents) {
            LocalDate d = ev.getEventDate();
            if (d == null) continue;
            boolean inMonth = YearMonth.from(d).equals(currentMonth);
            if (inMonth) month++;
            if (inMonth && "Service".equals(ev.getCategory())) services++;
            if (!d.isBefore(today)) {
                upcoming++;
                if (!d.isAfter(today.plusDays(7))) week++;
            }
        }
        statMonth.setText(String.valueOf(month));
        statWeek.setText(String.valueOf(week));
        statUpcoming.setText(String.valueOf(upcoming));
        statServices.setText(String.valueOf(services));
    }

    private void refreshCalendar() {
        refreshStats();
        monthYearLabel.setText(currentMonth.format(FMT_DISP));
        calendarGrid.getChildren().clear();
        calendarGrid.getRowConstraints().clear();

        String catFilter = categoryFilter.getValue();

        String[] days = {"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
        for (int col = 0; col < 7; col++) {
            Label hdr = new Label(days[col]);
            hdr.setStyle("-fx-font-size:11px;-fx-font-weight:700;-fx-text-fill:#9099AA;" +
                         "-fx-alignment:CENTER;-fx-padding:6 0;");
            hdr.setMaxWidth(Double.MAX_VALUE);
            hdr.setAlignment(Pos.CENTER);
            calendarGrid.add(hdr, col, 0);
        }

        int firstDayOfWeek = currentMonth.atDay(1).getDayOfWeek().getValue() % 7;
        int daysInMonth    = currentMonth.lengthOfMonth();
        int rows = (int) Math.ceil((firstDayOfWeek + daysInMonth) / 7.0);

        for (int r = 0; r <= rows; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setMinHeight(r == 0 ? 28 : 90);
            rc.setPrefHeight(r == 0 ? 28 : 90);
            calendarGrid.getRowConstraints().add(rc);
        }

        Map<LocalDate, List<Event>> eventMap = new HashMap<>();
        for (Event ev : allEvents) {
            if (ev.getEventDate() == null) continue;
            if (!"All".equals(catFilter) && !catFilter.equals(ev.getCategory())) continue;
            eventMap.computeIfAbsent(ev.getEventDate(), k -> new ArrayList<>()).add(ev);
        }

        LocalDate today = LocalDate.now();
        int gridCol = firstDayOfWeek;
        int gridRow = 1;

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = currentMonth.atDay(day);
            List<Event> dayEvents = eventMap.getOrDefault(date, Collections.emptyList());
            VBox cell = buildDayCell(day, date, dayEvents, today);
            calendarGrid.add(cell, gridCol, gridRow);
            gridCol++;
            if (gridCol == 7) { gridCol = 0; gridRow++; }
        }
    }

    private VBox buildDayCell(int day, LocalDate date, List<Event> events, LocalDate today) {
        VBox cell = new VBox(2);
        cell.setPadding(new Insets(4));
        cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        boolean isToday   = date.equals(today);
        boolean isPast    = date.isBefore(today);
        boolean isWeekend = date.getDayOfWeek().getValue() >= 6;

        String bg = isToday   ? "#EEF5FF" :
                    isPast    ? "#EEF0F5" :
                    isWeekend ? "#FAFAFA"  : "#FFFFFF";

        cell.setStyle("-fx-background-color:" + bg + ";" +
                      "-fx-border-color:#DDE1EA;-fx-border-width:0.5;" +
                      (isPast ? "-fx-opacity:0.6;" : ""));

        Label dayLbl = new Label(String.valueOf(day));
        dayLbl.setStyle(
            "-fx-font-size:12px;" +
            "-fx-font-weight:" + (isToday ? "700" : "500") + ";" +
            "-fx-background-color:" + (isToday ? "#3A86C8" : "transparent") + ";" +
            "-fx-text-fill:" + (isToday ? "#FFFFFF" : isPast ? "#9099AA" : isWeekend ? "#9099AA" : "#5A6275") + ";" +
            "-fx-background-radius:50%;-fx-padding:1 5;"
        );
        cell.getChildren().add(dayLbl);

        int shown = 0;
        for (Event ev : events) {
            if (shown >= 3) {
                Label more = new Label("+" + (events.size() - 3) + " more");
                more.setStyle("-fx-font-size:9px;-fx-text-fill:#9099AA;-fx-padding:1 4;");
                cell.getChildren().add(more);
                break;
            }
            Label chip = new Label(ev.getTitle());
            chip.setMaxWidth(Double.MAX_VALUE);
            String chipColor = switch (ev.getCategory() != null ? ev.getCategory() : "Other") {
                case "Service"  -> "#3A86C8";
                case "Meeting"  -> "#D94040";
                case "Outreach" -> "#2E8B57";
                case "Youth"    -> "#8B5CF6";
                default         -> "#C07800";
            };
            chip.setStyle("-fx-background-color:" + chipColor + "22;" +
                          "-fx-text-fill:" + chipColor + ";" +
                          "-fx-font-size:9px;-fx-font-weight:600;" +
                          "-fx-padding:1 5;-fx-background-radius:3;-fx-cursor:hand;");
            final Event evRef = ev;
            chip.setOnMouseClicked(e -> showEvent(evRef));
            chip.setTooltip(new Tooltip(
                ev.getTitle() + (ev.getEventTime() != null
                    ? " at " + ev.getEventTime().toString().substring(0,5) : "") +
                (ev.getLocation() != null ? "\n" + ev.getLocation() : "")
            ));
            cell.getChildren().add(chip);
            shown++;
        }

        cell.setOnMouseClicked(e -> {
            if (e.getTarget() == cell && !isPast) openEventDialogOnDate(date);
        });
        return cell;
    }

    // -- EVENT VIEW -------------------------------------------

    private void showEvent(Event ev) {
        String when = ev.getEventDate() != null
            ? ev.getEventDate().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")) : null;
        new DocumentViewer("Calendar Event", ev.getTitle())
            .icon("fas-calendar-day")
            .status(ev.getCategory() != null ? ev.getCategory() : "Other", "badge-inprogress")
            .width(640)
            .meta("Date",     when)
            .meta("Time",     ev.getEventTime() != null ? ev.getEventTime().toString().substring(0, 5) : "All day")
            .meta("Location", ev.getLocation())
            .meta("Category", ev.getCategory())
            .section("Description", ev.getDescription(), "No description.")
            .onEdit(() -> openEventDialog(ev))
            .onDelete(() -> deleteEvent(ev))
            .show();
    }

    // -- EVENT DIALOG -----------------------------------------

    private void openEventDialogOnDate(LocalDate date) {
        Event blank = new Event();
        blank.setId(-1);
        blank.setEventDate(date);
        openEventDialog(blank);
    }

    private void openEventDialog(Event existing) {
        FormBuilder f = new FormBuilder(existing == null || existing.getId() <= 0 ? "Add Event" : "Edit Event").icon("fas-calendar-plus");

        TextField titleField = new TextField();
        titleField.getStyleClass().add("form-field");
        titleField.setPromptText("Event title");

        boolean isNewEvent = existing == null || existing.getId() <= 0;

        DatePicker datePicker = new DatePicker(
            existing != null && existing.getEventDate() != null
                ? existing.getEventDate() : LocalDate.now()
        );
        datePicker.getStyleClass().add("form-date-picker");
        datePicker.setMaxWidth(Double.MAX_VALUE);

        // New events can't be dated in the past — existing (already past-dated)
        // events remain freely editable, e.g. to fix a typo after the fact.
        if (isNewEvent) {
            datePicker.setDayCellFactory(picker -> new DateCell() {
                @Override
                public void updateItem(LocalDate item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item != null && item.isBefore(LocalDate.now())) {
                        setDisable(true);
                        setStyle("-fx-background-color:#F0F1F4;-fx-text-fill:#C0C5CF;");
                    }
                }
            });
        }

        TextField timeField = new TextField();
        timeField.getStyleClass().add("form-field");
        timeField.setPromptText("HH:MM (optional)");

        TextField locationField = new TextField();
        locationField.getStyleClass().add("form-field");
        locationField.setPromptText("Location (optional)");

        ComboBox<String> catCombo = new ComboBox<>();
        catCombo.getStyleClass().add("form-combo");
        catCombo.getItems().addAll("Service","Meeting","Outreach","Youth","Other");
        catCombo.setValue("Service");
        catCombo.setMaxWidth(Double.MAX_VALUE);

        TextArea descArea = new TextArea();
        descArea.getStyleClass().add("form-textarea");
        descArea.setWrapText(true);
        descArea.setPromptText("Description (optional)");
        descArea.setPrefHeight(80);
        descArea.setMaxWidth(Double.MAX_VALUE);

        if (existing != null && existing.getId() > 0) {
            titleField.setText(existing.getTitle() != null ? existing.getTitle() : "");
            datePicker.setValue(existing.getEventDate());
            if (existing.getEventTime() != null)
                timeField.setText(existing.getEventTime().toString().substring(0,5));
            locationField.setText(existing.getLocation() != null ? existing.getLocation() : "");
            if (existing.getCategory() != null) catCombo.setValue(existing.getCategory());
            descArea.setText(existing.getDescription() != null ? existing.getDescription() : "");
        }

        f.section("Event")
         .field("TITLE *", titleField)
         .row("CATEGORY", catCombo, "LOCATION", locationField);
        f.section("When")
         .row("DATE *", datePicker, "TIME (HH:MM)", timeField);
        if (isNewEvent) f.hint("New events can't be added on a past date.");
        f.section("Details")
         .field("DESCRIPTION", descArea);

        Button saveBtn = f.saveButton(existing != null && existing.getId() > 0 ? "Save Changes" : "Add Event");

        saveBtn.setOnAction(e -> {
            String t = titleField.getText().trim();
            if (t.isEmpty()) { f.showError("Please enter a title for the event."); return; }
            if (datePicker.getValue() == null) { f.showError("Please choose the date."); return; }
            if (isNewEvent && datePicker.getValue().isBefore(LocalDate.now())) {
                f.showError("Events can't be added on a past date. Please choose today or a future date.");
                return;
            }
            String timeText = timeField.getText().trim();
            if (!timeText.isEmpty() && !timeText.matches("([01]?\\d|2[0-3]):[0-5]\\d")) {
                f.showError("Time must be in 24-hour HH:MM format, e.g. 09:30 or 18:00.");
                return;
            }

            try {
                Connection conn = DatabaseConnection.getConnection();
                Time time = null;
                if (!timeField.getText().trim().isEmpty()) {
                    try { time = Time.valueOf(timeField.getText().trim() + ":00"); }
                    catch (Exception ignore) {}
                }
                if (existing == null || existing.getId() <= 0) {
                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO events (title, event_date, event_time, location, description, category, created_by) " +
                        "VALUES (?,?,?,?,?,?,?)"
                    );
                    ps.setString(1, t);
                    ps.setDate(2, java.sql.Date.valueOf(datePicker.getValue()));
                    if (time != null) ps.setTime(3, time); else ps.setNull(3, Types.TIME);
                    ps.setString(4, locationField.getText().trim());
                    ps.setString(5, descArea.getText().trim());
                    ps.setString(6, catCombo.getValue());
                    ps.setInt(7, SessionManager.getInstance().getCurrentUser().getId());
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(), "Created event: " + t);
                } else {
                    PreparedStatement ps = conn.prepareStatement(
                        "UPDATE events SET title=?, event_date=?, event_time=?, " +
                        "location=?, description=?, category=? WHERE id=?"
                    );
                    ps.setString(1, t);
                    ps.setDate(2, java.sql.Date.valueOf(datePicker.getValue()));
                    if (time != null) ps.setTime(3, time); else ps.setNull(3, Types.TIME);
                    ps.setString(4, locationField.getText().trim());
                    ps.setString(5, descArea.getText().trim());
                    ps.setString(6, catCombo.getValue());
                    ps.setInt(7, existing.getId());
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(), "Updated event: " + t);
                }
                loadEvents();
                f.close();
                ToastManager.success(isNewEvent ? "Event added successfully." : "Event updated successfully.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                ToastManager.error("Failed to save event: " + ex.getMessage());
            }
        });

        f.show(560);
    }

    /** Confirms, then deletes the event. Returns true if deleted. */
    private boolean deleteEvent(Event e) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Delete Event");
        confirm.setHeaderText("Delete \"" + e.getTitle() + "\"?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        try {
            PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement("DELETE FROM events WHERE id=?");
            ps.setInt(1, e.getId());
            ps.executeUpdate();
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                "Deleted event: " + e.getTitle());
            loadEvents();
            ToastManager.success("Event deleted successfully.");
            return true;
        } catch (SQLException ex) {
            ex.printStackTrace();
            ToastManager.error("Failed to delete event: " + ex.getMessage());
            return false;
        }
    }

    private VBox row(String label, javafx.scene.Node field) {
        VBox b = new VBox(6);
        Label l = new Label(label);
        l.getStyleClass().add("form-label");
        b.getChildren().addAll(l, field);
        if (field instanceof Control) ((Control) field).setMaxWidth(Double.MAX_VALUE);
        return b;
    }
}