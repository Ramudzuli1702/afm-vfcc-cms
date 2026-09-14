package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.models.InventoryItem;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.SessionManager;
import com.afmvfcc.utils.ToastManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class InventoryController {

    @FXML private Label statTotalItems;
    @FXML private Label statTotalValue;
    @FXML private Label statLowStock;
    @FXML private Label statNeedsRepair;

    @FXML private TextField  inventorySearchField;
    @FXML private ComboBox<String> categoryFilterCombo;
    @FXML private ComboBox<String> conditionFilterCombo;

    @FXML private TableView<InventoryItem>            inventoryTable;
    @FXML private TableColumn<InventoryItem, String>  colItemName, colCategory, colQuantity,
                                                        colCondition, colLocation, colCustodian, colValue;
    @FXML private TableColumn<InventoryItem, Void>     colActions;

    private static final String[] CATEGORIES = {
        "Musical Instruments", "Office Equipment", "Building Materials",
        "Furniture", "Electronics", "Other"
    };
    private static final String[] CONDITIONS = { "New", "Good", "Fair", "Needs Repair", "Damaged" };

    private final ObservableList<InventoryItem> allItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        categoryFilterCombo.getItems().add("All Categories");
        categoryFilterCombo.getItems().addAll(CATEGORIES);
        categoryFilterCombo.setValue("All Categories");

        conditionFilterCombo.getItems().add("All Conditions");
        conditionFilterCombo.getItems().addAll(CONDITIONS);
        conditionFilterCombo.setValue("All Conditions");

        setupTable();
        loadItems();
    }

    // ══════════════════════════════════════════════════════════
    // TABLE
    // ══════════════════════════════════════════════════════════

    private void setupTable() {
        colItemName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getItemName()));
        colCategory.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategory()));
        colQuantity.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getQuantity() + " " + nvl(d.getValue().getUnit())));
        colLocation.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getLocation())));
        colCustodian.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getCustodian())));
        colValue.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getPurchaseValue() != null ? "R" + d.getValue().getPurchaseValue().toPlainString() : "-"));

        colQuantity.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                InventoryItem it = getTableView().getItems().get(getIndex());
                setText(item);
                setStyle(it.isLowStock() ? "-fx-text-fill:#D94040;-fx-font-weight:700;" : "");
            }
        });

        colCondition.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCondition()));
        colCondition.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label(item);
                badge.getStyleClass().add(
                    ("Needs Repair".equals(item) || "Damaged".equals(item)) ? "badge-inactive" :
                    "Fair".equals(item) ? "badge-pending" : "badge-active");
                setGraphic(badge); setText(null);
            }
        });

        colActions.setCellFactory(col -> new TableCell<>() {
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
                        openItemDialog(getTableView().getItems().get(idx));
                });
                deleteBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        deleteItem(getTableView().getItems().get(idx));
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        inventoryTable.setItems(allItems);
    }

    private void loadItems() {
        allItems.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT * FROM inventory_items WHERE is_deleted=0 ORDER BY item_name ASC");
            while (rs.next()) allItems.add(mapItem(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        applyFilters();
        updateStats();
    }

    private void updateStats() {
        int total = allItems.size();
        BigDecimal totalValue = BigDecimal.ZERO;
        int lowStock = 0, needsRepair = 0;
        for (InventoryItem it : allItems) {
            if (it.getPurchaseValue() != null)
                totalValue = totalValue.add(it.getPurchaseValue().multiply(BigDecimal.valueOf(it.getQuantity())));
            if (it.isLowStock()) lowStock++;
            if (it.needsAttention()) needsRepair++;
        }
        statTotalItems.setText(String.valueOf(total));
        statTotalValue.setText("R" + totalValue.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString());
        statLowStock.setText(String.valueOf(lowStock));
        statNeedsRepair.setText(String.valueOf(needsRepair));
    }

    @FXML public void handleSearch() { applyFilters(); }
    @FXML public void handleFilter() { applyFilters(); }

    private void applyFilters() {
        String q = inventorySearchField != null ? inventorySearchField.getText().toLowerCase().trim() : "";
        String cat = categoryFilterCombo != null ? categoryFilterCombo.getValue() : "All Categories";
        String cond = conditionFilterCombo != null ? conditionFilterCombo.getValue() : "All Conditions";

        List<InventoryItem> filtered = new ArrayList<>();
        for (InventoryItem it : allItems) {
            if (!q.isEmpty() &&
                !it.getItemName().toLowerCase().contains(q) &&
                !nvl(it.getLocation()).toLowerCase().contains(q) &&
                !nvl(it.getCustodian()).toLowerCase().contains(q)) continue;
            if (cat != null && !"All Categories".equals(cat) && !cat.equals(it.getCategory())) continue;
            if (cond != null && !"All Conditions".equals(cond) && !cond.equals(it.getCondition())) continue;
            filtered.add(it);
        }
        inventoryTable.setItems(FXCollections.observableArrayList(filtered));
    }

    // ══════════════════════════════════════════════════════════
    // ADD / EDIT DIALOG
    // ══════════════════════════════════════════════════════════

    @FXML public void handleAddItem() { openItemDialog(null); }

    private void openItemDialog(InventoryItem existing) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(existing == null ? "Add Inventory Item" : "Edit: " + existing.getItemName());
        stage.setMinWidth(500);

        HBox header = new HBox();
        header.setStyle("-fx-background-color:#FFFFFF;-fx-padding:18 24;" +
            "-fx-border-color:#DDE1EA;-fx-border-width:0 0 1 0;");
        Label title = new Label(existing == null ? "Add Inventory Item" : "Edit Inventory Item");
        title.setStyle("-fx-font-size:16px;-fx-font-weight:700;-fx-text-fill:#1E2130;");
        header.getChildren().add(title);

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(14);
        form.setPadding(new Insets(22, 24, 8, 24));

        TextField nameField = field("e.g. Yamaha Keyboard");
        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.getStyleClass().add("form-combo");
        categoryCombo.getItems().addAll(CATEGORIES);
        categoryCombo.setValue(CATEGORIES[0]);
        categoryCombo.setMaxWidth(Double.MAX_VALUE);
        categoryCombo.setEditable(true);

        TextField quantityField = field("1");
        TextField unitField     = field("pcs");
        ComboBox<String> conditionCombo = new ComboBox<>();
        conditionCombo.getStyleClass().add("form-combo");
        conditionCombo.getItems().addAll(CONDITIONS);
        conditionCombo.setValue("Good");
        conditionCombo.setMaxWidth(Double.MAX_VALUE);

        TextField locationField  = field("e.g. Main Hall Storeroom");
        TextField custodianField = field("Person/department responsible (optional)");
        DatePicker purchaseDatePicker = new DatePicker();
        purchaseDatePicker.getStyleClass().add("form-date-picker");
        purchaseDatePicker.setMaxWidth(Double.MAX_VALUE);
        TextField valueField = field("e.g. 4500.00 (optional)");
        TextField lowStockField = field("Alert when qty falls to/below this (0 = off)");
        TextArea notesArea = new TextArea();
        notesArea.getStyleClass().add("form-textarea");
        notesArea.setPromptText("Optional notes...");
        notesArea.setPrefHeight(70);
        notesArea.setMaxWidth(Double.MAX_VALUE);

        if (existing != null) {
            nameField.setText(existing.getItemName());
            categoryCombo.setValue(existing.getCategory());
            quantityField.setText(String.valueOf(existing.getQuantity()));
            unitField.setText(nvl(existing.getUnit()));
            conditionCombo.setValue(existing.getCondition());
            locationField.setText(nvl(existing.getLocation()));
            custodianField.setText(nvl(existing.getCustodian()));
            if (existing.getPurchaseDate() != null) purchaseDatePicker.setValue(existing.getPurchaseDate());
            if (existing.getPurchaseValue() != null) valueField.setText(existing.getPurchaseValue().toPlainString());
            lowStockField.setText(String.valueOf(existing.getLowStockThreshold()));
            notesArea.setText(nvl(existing.getNotes()));
        } else {
            quantityField.setText("1");
            unitField.setText("pcs");
            lowStockField.setText("0");
        }

        String[] labels = {"ITEM NAME *", "CATEGORY *", "QUANTITY *", "UNIT", "CONDITION *",
            "LOCATION", "CUSTODIAN", "PURCHASE DATE", "VALUE (R)", "LOW STOCK ALERT", "NOTES"};
        javafx.scene.Node[] controls = {nameField, categoryCombo, quantityField, unitField, conditionCombo,
            locationField, custodianField, purchaseDatePicker, valueField, lowStockField, notesArea};

        for (int i = 0; i < labels.length; i++) {
            Label lbl = new Label(labels[i]);
            lbl.setStyle("-fx-font-size:10px;-fx-font-weight:700;-fx-text-fill:#9099AA;");
            form.add(lbl, 0, i);
            form.add(controls[i], 1, i);
            GridPane.setHgrow(controls[i], Priority.ALWAYS);
        }

        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill:#D94040;-fx-font-size:11px;");
        errLabel.setVisible(false);
        errLabel.setWrapText(true);
        form.add(errLabel, 0, labels.length, 2, 1);

        Button saveBtn   = new Button(existing == null ? "Add Item" : "Save Changes");
        saveBtn.getStyleClass().add("btn-primary");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        HBox footer = new HBox(10, cancelBtn, saveBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color:#FFFFFF;-fx-padding:14 24;" +
            "-fx-border-color:#DDE1EA;-fx-border-width:1 0 0 0;");

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#F5F6FA;");
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:transparent;-fx-background:transparent;" +
            "-fx-border-color:transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().addAll(header, scroll, footer);

        cancelBtn.setOnAction(e -> stage.close());

        saveBtn.setOnAction(e -> {
            errLabel.setVisible(false);
            String name = nameField.getText().trim();
            String category = categoryCombo.getValue() != null ? categoryCombo.getValue().trim() : "";
            String qtyText = quantityField.getText().trim();

            if (name.isEmpty() || category.isEmpty()) {
                errLabel.setText("Item name and category are required.");
                errLabel.setVisible(true); return;
            }
            int qty;
            try { qty = Integer.parseInt(qtyText); if (qty < 0) throw new NumberFormatException(); }
            catch (NumberFormatException nfe) {
                errLabel.setText("Quantity must be a whole number (0 or more).");
                errLabel.setVisible(true); return;
            }
            int lowStock;
            try { lowStock = lowStockField.getText().trim().isEmpty() ? 0 : Integer.parseInt(lowStockField.getText().trim()); }
            catch (NumberFormatException nfe) {
                errLabel.setText("Low stock alert must be a whole number.");
                errLabel.setVisible(true); return;
            }
            BigDecimal value = null;
            if (!valueField.getText().trim().isEmpty()) {
                try { value = new BigDecimal(valueField.getText().trim()); }
                catch (NumberFormatException nfe) {
                    errLabel.setText("Value must be a number, e.g. 4500.00");
                    errLabel.setVisible(true); return;
                }
            }

            try {
                Connection conn = DatabaseConnection.getConnection();
                if (existing == null) {
                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO inventory_items " +
                        "(item_name, category, quantity, unit, item_condition, location, custodian, " +
                        " purchase_date, purchase_value, low_stock_threshold, notes, created_by) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)");
                    ps.setString(1, name);
                    ps.setString(2, category);
                    ps.setInt(3, qty);
                    ps.setString(4, unitField.getText().trim());
                    ps.setString(5, conditionCombo.getValue());
                    ps.setString(6, locationField.getText().trim());
                    ps.setString(7, custodianField.getText().trim());
                    if (purchaseDatePicker.getValue() != null) ps.setDate(8, Date.valueOf(purchaseDatePicker.getValue()));
                    else ps.setNull(8, Types.DATE);
                    if (value != null) ps.setBigDecimal(9, value); else ps.setNull(9, Types.DECIMAL);
                    ps.setInt(10, lowStock);
                    ps.setString(11, notesArea.getText().trim());
                    ps.setInt(12, SessionManager.getInstance().getCurrentUser().getId());
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Added inventory item: " + name);
                    ToastManager.success("Item \"" + name + "\" added to inventory.");
                } else {
                    PreparedStatement ps = conn.prepareStatement(
                        "UPDATE inventory_items SET item_name=?, category=?, quantity=?, unit=?, " +
                        "item_condition=?, location=?, custodian=?, purchase_date=?, purchase_value=?, " +
                        "low_stock_threshold=?, notes=? WHERE id=?");
                    ps.setString(1, name);
                    ps.setString(2, category);
                    ps.setInt(3, qty);
                    ps.setString(4, unitField.getText().trim());
                    ps.setString(5, conditionCombo.getValue());
                    ps.setString(6, locationField.getText().trim());
                    ps.setString(7, custodianField.getText().trim());
                    if (purchaseDatePicker.getValue() != null) ps.setDate(8, Date.valueOf(purchaseDatePicker.getValue()));
                    else ps.setNull(8, Types.DATE);
                    if (value != null) ps.setBigDecimal(9, value); else ps.setNull(9, Types.DECIMAL);
                    ps.setInt(10, lowStock);
                    ps.setString(11, notesArea.getText().trim());
                    ps.setInt(12, existing.getId());
                    ps.executeUpdate();
                    AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                        "Updated inventory item: " + name);
                    ToastManager.success("Item \"" + name + "\" updated.");
                }
                loadItems();
                stage.close();
            } catch (SQLException ex) {
                errLabel.setText("Database error: " + ex.getMessage());
                errLabel.setVisible(true);
                ex.printStackTrace();
                ToastManager.error("Failed to save item: " + ex.getMessage());
            }
        });

        Scene scene = new Scene(root, 520, 640);
        scene.getStylesheets().add(
            getClass().getResource("/com/afmvfcc/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    // ══════════════════════════════════════════════════════════
    // DELETE
    // ══════════════════════════════════════════════════════════

    private void deleteItem(InventoryItem item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Delete Inventory Item");
        confirm.setHeaderText("Remove \"" + item.getItemName() + "\" from inventory?");
        confirm.setContentText("This item will no longer appear in the inventory list.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            try {
                Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                    "UPDATE inventory_items SET is_deleted=1 WHERE id=?");
                ps.setInt(1, item.getId());
                ps.executeUpdate();
                AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    "Deleted inventory item: " + item.getItemName());
                loadItems();
                ToastManager.success("Item \"" + item.getItemName() + "\" removed from inventory.");
            } catch (SQLException e) {
                e.printStackTrace();
                ToastManager.error("Failed to delete item: " + e.getMessage());
            }
        });
    }

    // ══════════════════════════════════════════════════════════
    // EXPORT
    // ══════════════════════════════════════════════════════════

    @FXML
    public void handleExport() {
        List<InventoryItem> rows = inventoryTable.getItems();
        if (rows.isEmpty()) {
            ToastManager.error("No inventory items to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Inventory");
        chooser.setInitialFileName("AFM_VFCC_Inventory_" + LocalDate.now() + ".xlsx");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        File out = chooser.showSaveDialog(inventoryTable.getScene().getWindow());
        if (out == null) return;

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Inventory");
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = {"Item", "Category", "Quantity", "Unit", "Condition",
                "Location", "Custodian", "Purchase Date", "Value (R)", "Notes"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int r = 1;
            for (InventoryItem it : rows) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(it.getItemName());
                row.createCell(1).setCellValue(it.getCategory());
                row.createCell(2).setCellValue(it.getQuantity());
                row.createCell(3).setCellValue(nvl(it.getUnit()));
                row.createCell(4).setCellValue(it.getCondition());
                row.createCell(5).setCellValue(nvl(it.getLocation()));
                row.createCell(6).setCellValue(nvl(it.getCustodian()));
                row.createCell(7).setCellValue(it.getPurchaseDate() != null ? it.getPurchaseDate().toString() : "");
                row.createCell(8).setCellValue(it.getPurchaseValue() != null ? it.getPurchaseValue().doubleValue() : 0);
                row.createCell(9).setCellValue(nvl(it.getNotes()));
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            try (FileOutputStream fos = new FileOutputStream(out)) {
                wb.write(fos);
            }
            ToastManager.success("Inventory exported to " + out.getName());
        } catch (Exception ex) {
            ex.printStackTrace();
            ToastManager.error("Export failed: " + ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════

    private TextField field(String prompt) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        f.getStyleClass().add("form-field");
        f.setMaxWidth(Double.MAX_VALUE);
        return f;
    }

    private String nvl(String s) { return s != null ? s : ""; }

    private InventoryItem mapItem(ResultSet rs) throws SQLException {
        InventoryItem it = new InventoryItem();
        it.setId(rs.getInt("id"));
        it.setItemName(rs.getString("item_name"));
        it.setCategory(rs.getString("category"));
        it.setQuantity(rs.getInt("quantity"));
        it.setUnit(rs.getString("unit"));
        it.setCondition(rs.getString("item_condition"));
        it.setLocation(rs.getString("location"));
        it.setCustodian(rs.getString("custodian"));
        Date pd = rs.getDate("purchase_date");
        if (pd != null) it.setPurchaseDate(pd.toLocalDate());
        BigDecimal val = rs.getBigDecimal("purchase_value");
        if (val != null) it.setPurchaseValue(val);
        it.setLowStockThreshold(rs.getInt("low_stock_threshold"));
        it.setNotes(rs.getString("notes"));
        return it;
    }
}
