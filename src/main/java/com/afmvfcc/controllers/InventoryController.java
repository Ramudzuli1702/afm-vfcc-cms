package com.afmvfcc.controllers;

import com.afmvfcc.Main;
import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.models.InventoryItem;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.Dialogs;
import com.afmvfcc.utils.DocumentViewer;
import com.afmvfcc.utils.FormBuilder;
import com.afmvfcc.utils.SessionManager;
import com.afmvfcc.utils.TableActions;
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

        TableActions.viewOnly(inventoryTable, colActions, this::showItem);

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

    private void showItem(InventoryItem it) {
        String qty = it.getQuantity() + (it.getUnit() != null && !it.getUnit().isBlank() ? " " + it.getUnit() : "");
        String status = it.getCondition();
        new DocumentViewer("Inventory Item", it.getItemName())
            .icon("fas-boxes")
            .status(status, ("Needs Repair".equals(status) || "Damaged".equals(status)) ? "badge-inactive"
                          : "Fair".equals(status) ? "badge-pending" : "badge-active")
            .width(720)
            .meta("Category",      it.getCategory())
            .meta("Quantity",      qty + (it.isLowStock() ? "  (low stock)" : ""))
            .meta("Location",      it.getLocation())
            .meta("Custodian",     it.getCustodian())
            .meta("Purchase Date", it.getPurchaseDate() != null
                ? it.getPurchaseDate().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")) : null)
            .meta("Value",         it.getPurchaseValue() != null ? "R" + it.getPurchaseValue().toPlainString() : null)
            .meta("Low Stock Alert", it.getLowStockThreshold() > 0 ? "At " + it.getLowStockThreshold() + " or fewer" : "Off")
            .section("Notes", it.getNotes(), "No notes.")
            .onEdit(() -> openItemDialog(it))
            .onDelete(() -> deleteItem(it))
            .show();
    }

    private void openItemDialog(InventoryItem existing) {
        FormBuilder f = new FormBuilder(existing == null ? "Add Inventory Item" : "Edit Inventory Item").icon("fas-boxes");

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
        TextField custodianField = field("Person or department (optional)");
        DatePicker purchaseDatePicker = new DatePicker();
        purchaseDatePicker.getStyleClass().add("form-date-picker");
        purchaseDatePicker.setMaxWidth(Double.MAX_VALUE);
        TextField valueField = field("e.g. 4500.00 (optional)");
        TextField lowStockField = field("0 = off");
        TextArea notesArea = new TextArea();
        notesArea.getStyleClass().add("form-textarea");
        notesArea.setWrapText(true);
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

        f.section("Item")
         .field("ITEM NAME *", nameField)
         .row("CATEGORY *", categoryCombo, "CONDITION *", conditionCombo)
         .row("QUANTITY *", quantityField, "UNIT", unitField, "LOW STOCK ALERT", lowStockField)
         .hint("Low stock alert: you're warned when the quantity falls to this number (0 = off).");
        f.section("Location")
         .row("LOCATION", locationField, "CUSTODIAN", custodianField);
        f.section("Purchase")
         .row("PURCHASE DATE", purchaseDatePicker, "VALUE (R)", valueField);
        f.section("Notes")
         .field("NOTES", notesArea);

        Button saveBtn = f.saveButton(existing == null ? "Add Item" : "Save Changes");

        saveBtn.setOnAction(e -> {
            f.clearError();
            String name = nameField.getText().trim();
            String category = categoryCombo.getValue() != null ? categoryCombo.getValue().trim() : "";
            String qtyText = quantityField.getText().trim();

            if (name.isEmpty() || category.isEmpty()) {
                f.showError("Item name and category are required."); return;
            }
            int qty;
            try { qty = Integer.parseInt(qtyText); if (qty < 0) throw new NumberFormatException(); }
            catch (NumberFormatException nfe) {
                f.showError("Quantity must be a whole number (0 or more)."); return;
            }
            int lowStock;
            try { lowStock = lowStockField.getText().trim().isEmpty() ? 0 : Integer.parseInt(lowStockField.getText().trim()); }
            catch (NumberFormatException nfe) {
                f.showError("Low stock alert must be a whole number."); return;
            }
            BigDecimal value = null;
            if (!valueField.getText().trim().isEmpty()) {
                try { value = new BigDecimal(valueField.getText().trim()); }
                catch (NumberFormatException nfe) {
                    f.showError("Value must be a number, e.g. 4500.00"); return;
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
                f.close();
            } catch (SQLException ex) {
                f.showError("Database error: " + ex.getMessage());
                ex.printStackTrace();
                ToastManager.error("Failed to save item: " + ex.getMessage());
            }
        });

        f.show(620);
    }

    // ══════════════════════════════════════════════════════════
    // DELETE
    // ══════════════════════════════════════════════════════════

    /** Confirms, then removes the item. Returns true if it was removed. */
    private boolean deleteItem(InventoryItem item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Main.applyStyles(confirm.getDialogPane());
        confirm.setTitle("Delete Inventory Item");
        confirm.setHeaderText("Remove \"" + item.getItemName() + "\" from inventory?");
        confirm.setContentText("This item will no longer appear in the inventory list.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
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
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            ToastManager.error("Failed to delete item: " + e.getMessage());
            return false;
        }
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
