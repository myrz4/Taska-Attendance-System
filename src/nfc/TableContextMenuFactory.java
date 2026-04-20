package nfc;

import java.util.function.Supplier;

import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

final class TableContextMenuFactory {
    private TableContextMenuFactory() {
    }

    static ContextMenu create(
        Node owner,
        Supplier<String> cellValueSupplier,
        Supplier<String> rowSummarySupplier,
        Supplier<String> recordIdSupplier,
        Supplier<String> jsonSupplier
    ) {
        MenuItem copyCellItem = new MenuItem("Copy Cell");
        MenuItem copyRowItem = new MenuItem("Copy Row Summary");
        MenuItem copyIdItem = new MenuItem("Copy Record ID");
        MenuItem copyJsonItem = new MenuItem("Copy as JSON");

        ContextMenu menu = new ContextMenu(
            copyCellItem,
            copyRowItem,
            copyIdItem,
            new SeparatorMenuItem(),
            copyJsonItem
        );

        menu.setOnShowing(event -> {
            configureItem(copyCellItem, owner, cellValueSupplier, "Cell copied");
            configureItem(copyRowItem, owner, rowSummarySupplier, "Row copied");
            configureItem(copyIdItem, owner, recordIdSupplier, "Record ID copied");
            configureItem(copyJsonItem, owner, jsonSupplier, "JSON copied");
        });
        return menu;
    }

    private static void configureItem(
        MenuItem item,
        Node owner,
        Supplier<String> valueSupplier,
        String feedbackMessage
    ) {
        String value = safeValue(valueSupplier);
        item.setDisable(value.isEmpty());
        item.setOnAction(event -> UiClipboardSupport.copyText(value, owner, feedbackMessage));
    }

    private static String safeValue(Supplier<String> supplier) {
        if (supplier == null) {
            return "";
        }
        String value = supplier.get();
        return value == null ? "" : value.trim();
    }
}