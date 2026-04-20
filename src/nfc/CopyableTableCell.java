package nfc;

import java.util.function.Function;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.text.TextAlignment;

class CopyableTableCell<S> extends TableCell<S, String> {
    private final Function<S, String> fullValueProvider;
    private final Function<String, String> displayFormatter;
    private final Function<S, String> rowSummaryProvider;
    private final Function<S, String> recordIdProvider;
    private final Function<S, String> jsonProvider;
    private final Pos alignment;
    private final Tooltip tooltip = new Tooltip();
    private String currentFullValue = "";

    CopyableTableCell(
        Function<S, String> fullValueProvider,
        Function<String, String> displayFormatter,
        Function<S, String> rowSummaryProvider,
        Function<S, String> recordIdProvider,
        Function<S, String> jsonProvider,
        Pos alignment,
        boolean wrapText
    ) {
        this.fullValueProvider = fullValueProvider;
        this.displayFormatter = displayFormatter == null ? value -> value : displayFormatter;
        this.rowSummaryProvider = rowSummaryProvider;
        this.recordIdProvider = recordIdProvider;
        this.jsonProvider = jsonProvider;
        this.alignment = alignment == null ? Pos.CENTER_LEFT : alignment;

        getStyleClass().add("summary-copy-cell");
        setWrapText(wrapText);
        setTextAlignment(TextAlignment.LEFT);
        setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !isEmpty()) {
                UiClipboardSupport.copyText(currentFullValue, this, "Copied");
            }
        });
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        S rowItem = currentRowItem();
        if (empty || rowItem == null) {
            currentFullValue = "";
            setText(null);
            setTooltip(null);
            setContextMenu(null);
            return;
        }

        currentFullValue = normalize(fullValueProvider == null ? item : fullValueProvider.apply(rowItem));
        String displayValue = normalize(displayFormatter.apply(currentFullValue));
        setText(displayValue.isEmpty() ? "-" : displayValue);
        setAlignment(alignment);
        setTooltip(currentFullValue.isEmpty() || currentFullValue.equals(displayValue) ? null : tooltipFor(currentFullValue));
        setContextMenu(TableContextMenuFactory.create(
            this,
            () -> currentFullValue,
            () -> rowSummaryProvider == null ? "" : normalize(rowSummaryProvider.apply(rowItem)),
            () -> recordIdProvider == null ? "" : normalize(recordIdProvider.apply(rowItem)),
            () -> jsonProvider == null ? "" : normalize(jsonProvider.apply(rowItem))
        ));
    }

    protected S currentRowItem() {
        return getTableRow() == null ? null : getTableRow().getItem();
    }

    private Tooltip tooltipFor(String value) {
        tooltip.setText(value);
        return tooltip;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}