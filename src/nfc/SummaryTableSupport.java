package nfc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

final class SummaryTableSupport {
    private SummaryTableSupport() {
    }

    static TextField createSearchField(String promptText) {
        TextField searchField = new TextField();
        searchField.setPromptText(promptText);
        searchField.getStyleClass().add("summary-search-field");
        searchField.setMaxWidth(Double.MAX_VALUE);
        return searchField;
    }

    static javafx.scene.control.Button createPrimaryButton(String text) {
        javafx.scene.control.Button button = new javafx.scene.control.Button(text);
        button.getStyleClass().add("primary-admin-button");
        return button;
    }

    static MenuButton createColumnChooser(String title, Collection<? extends TableColumn<?, ?>> optionalColumns) {
        MenuButton menuButton = new MenuButton(title);
        menuButton.getStyleClass().add("summary-toolbar-button");

        for (TableColumn<?, ?> column : optionalColumns) {
            if (column == null) {
                continue;
            }
            CheckMenuItem item = new CheckMenuItem(column.getText());
            item.setSelected(column.isVisible());
            item.selectedProperty().addListener((obs, oldValue, newValue) -> column.setVisible(newValue));
            column.visibleProperty().addListener((obs, oldValue, newValue) -> item.setSelected(newValue));
            menuButton.getItems().add(item);
        }
        return menuButton;
    }

    static <S> void configureSummaryTable(
        TableView<S> table,
        String emptyMessage,
        Function<S, String> rowSummaryProvider,
        Function<S, String> recordIdProvider,
        Function<S, Object> jsonProvider,
        Consumer<S> onOpen
    ) {
        if (!table.getStyleClass().contains("summary-table")) {
            table.getStyleClass().add("summary-table");
        }
        table.setPlaceholder(createEmptyState(emptyMessage));
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if ((event.isControlDown() || event.isMetaDown()) && event.getCode() == KeyCode.C) {
                copySelectedCell(table, rowSummaryProvider);
                event.consume();
            }
        });

        table.setRowFactory(tv -> createSummaryRow(rowSummaryProvider, recordIdProvider, jsonProvider, onOpen));
    }

    static String maskMiddle(String value, int keepLeft, int keepRight) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.length() <= Math.max(keepLeft + keepRight, 3)) {
            return "*".repeat(Math.max(4, normalized.length()));
        }
        String left = normalized.substring(0, Math.max(0, keepLeft));
        String right = normalized.substring(normalized.length() - Math.max(0, keepRight));
        int maskLength = Math.max(4, normalized.length() - left.length() - right.length());
        return left + "*".repeat(maskLength) + right;
    }

    static String compactListSummary(Collection<String> values, int visibleLimit) {
        if (values == null || values.isEmpty()) {
            return "-";
        }

        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isEmpty() && !cleaned.contains(normalized)) {
                cleaned.add(normalized);
            }
        }
        if (cleaned.isEmpty()) {
            return "-";
        }
        if (cleaned.size() <= visibleLimit) {
            return String.join(", ", cleaned);
        }

        List<String> visible = cleaned.subList(0, Math.max(1, visibleLimit));
        int remaining = cleaned.size() - visible.size();
        return String.join(", ", visible) + " +" + remaining + " more";
    }

    static String toPrettyJson(Object value) {
        if (value == null) {
            return "";
        }
        return BillingPolicyCallableSupport.toPrettyJson(value);
    }

    static String displayText(Object value) {
        String normalized = normalize(Objects.toString(value, ""));
        return normalized.isEmpty() ? "-" : normalized;
    }

    static String resolveStatus(Map<String, Object> data, String fallbackWhenMissing) {
        if (data == null) {
            return fallbackWhenMissing;
        }
        Object disabled = data.get("disabled");
        if (Boolean.TRUE.equals(disabled)) {
            return "Inactive";
        }
        Object active = data.get("active");
        if (active instanceof Boolean) {
            return Boolean.TRUE.equals(active) ? "Active" : "Inactive";
        }
        return fallbackWhenMissing;
    }

    private static Label createEmptyState(String message) {
        Label label = new Label(message == null || message.isBlank() ? "No records found." : message);
        label.getStyleClass().add("summary-empty-state");
        label.setWrapText(true);
        return label;
    }

    private static <S> TableRow<S> createSummaryRow(
        Function<S, String> rowSummaryProvider,
        Function<S, String> recordIdProvider,
        Function<S, Object> jsonProvider,
        Consumer<S> onOpen
    ) {
        TableRow<S> row = new TableRow<>();
        row.setOnMouseClicked(event -> {
            if (!row.isEmpty() && event.getClickCount() == 2 && onOpen != null) {
                onOpen.accept(row.getItem());
            }
        });
        row.setContextMenu(TableContextMenuFactory.create(
            row,
            () -> "",
            () -> rowSummaryProvider == null || row.isEmpty() ? "" : normalize(rowSummaryProvider.apply(row.getItem())),
            () -> recordIdProvider == null || row.isEmpty() ? "" : normalize(recordIdProvider.apply(row.getItem())),
            () -> jsonProvider == null || row.isEmpty() ? "" : toPrettyJson(jsonProvider.apply(row.getItem()))
        ));
        return row;
    }

    private static <S> void copySelectedCell(TableView<S> table, Function<S, String> rowSummaryProvider) {
        if (table == null || table.getItems() == null || table.getItems().isEmpty()) {
            return;
        }

        TablePosition<S, ?> position = focusedCell(table);
        if (position != null && position.getRow() >= 0 && position.getRow() < table.getItems().size() && position.getTableColumn() != null) {
            S rowItem = table.getItems().get(position.getRow());
            @SuppressWarnings("unchecked")
            TableColumn<S, Object> column = (TableColumn<S, Object>) position.getTableColumn();
            ObservableValue<Object> observable = column.getCellObservableValue(rowItem);
            Object value = observable == null ? null : observable.getValue();
            if (UiClipboardSupport.copyText(Objects.toString(value, ""), table, "Cell copied")) {
                return;
            }
        }

        S selected = table.getSelectionModel().getSelectedItem();
        if (selected != null && rowSummaryProvider != null) {
            UiClipboardSupport.copyText(rowSummaryProvider.apply(selected), table, "Row copied");
        }
    }

    private static <S> TablePosition<S, ?> focusedCell(TableView<S> table) {
        @SuppressWarnings("unchecked")
        TablePosition<S, ?> position = (TablePosition<S, ?>) table.getFocusModel().getFocusedCell();
        return position;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}