package com.afmvfcc.utils;

import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.util.function.Consumer;

/**
 * Every table in the app follows the same pattern: a row has a single "View"
 * button (or double-click), which opens the record read-only. Edit, Delete and
 * any other actions live inside that view, so nothing changes by accident from
 * the list itself.
 */
public class TableActions {

    /** Puts a View button in {@code actionsCol} and opens the row on double-click. */
    public static <T> void viewOnly(TableView<T> table, TableColumn<T, Void> actionsCol, Consumer<T> onView) {
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("View");
            {
                viewBtn.getStyleClass().add("btn-secondary");
                viewBtn.setStyle("-fx-padding:4 12;-fx-font-size:11px;");
                viewBtn.setGraphic(Icons.of("fas-eye", 11, Icons.NAVY));
                viewBtn.setGraphicTextGap(6);
                viewBtn.setOnAction(e -> {
                    T item = getTableRow() != null ? getTableRow().getItem() : null;
                    if (item != null) onView.accept(item);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || getTableRow() == null || getTableRow().getItem() == null ? null : viewBtn);
            }
        });
        table.setRowFactory(tv -> {
            TableRow<T> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) onView.accept(row.getItem());
            });
            return row;
        });
    }
}
