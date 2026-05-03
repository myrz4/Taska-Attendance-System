package nfc;

import java.util.function.Consumer;

import javafx.collections.ObservableList;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;

@SuppressWarnings("unused")
final class BillingLedgerTableSupport {
    private BillingLedgerTableSupport() {}

    static void setupInvoiceTable(
        TableView<BillingLedgerView.LedgerRow> table,
        ObservableList<BillingLedgerView.LedgerRow> rows,
        Consumer<BillingLedgerView.LedgerRow> onSelectionChanged
    ) {
        table.setItems(rows);
        table.getStyleClass().add("app-data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(50);

        TableColumn<BillingLedgerView.LedgerRow, String> invoiceCol = new TableColumn<>("Invoice ID");
        invoiceCol.setCellValueFactory(new PropertyValueFactory<>("invoiceId"));
        invoiceCol.setPrefWidth(120);

        TableColumn<BillingLedgerView.LedgerRow, String> parentCol = new TableColumn<>("Parent");
        parentCol.setCellValueFactory(new PropertyValueFactory<>("parentName"));
        parentCol.setPrefWidth(140);

        TableColumn<BillingLedgerView.LedgerRow, String> childCol = new TableColumn<>("Student(s)");
        childCol.setCellValueFactory(new PropertyValueFactory<>("childName"));
        childCol.setPrefWidth(150);

        TableColumn<BillingLedgerView.LedgerRow, String> periodCol = new TableColumn<>("Period");
        periodCol.setCellValueFactory(new PropertyValueFactory<>("period"));
        periodCol.setPrefWidth(95);

        TableColumn<BillingLedgerView.LedgerRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(90);
        statusCol.setCellFactory(column -> new TableCell<BillingLedgerView.LedgerRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                BillingLedgerView.LedgerRow row = getTableRow().getItem();
                Label badge = AppThemeSupport.createChip(item, statusStyleClass(row));
                setGraphic(badge);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

        TableColumn<BillingLedgerView.LedgerRow, String> dueCol = new TableColumn<>("Due Date");
        dueCol.setCellValueFactory(new PropertyValueFactory<>("dueDateText"));
        dueCol.setPrefWidth(105);

        TableColumn<BillingLedgerView.LedgerRow, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(new PropertyValueFactory<>("totalText"));
        totalCol.setPrefWidth(105);

        TableColumn<BillingLedgerView.LedgerRow, String> reviewCol = new TableColumn<>("Review");
        reviewCol.setCellValueFactory(new PropertyValueFactory<>("reviewFlag"));
        reviewCol.setPrefWidth(105);
        reviewCol.setCellFactory(column -> new TableCell<BillingLedgerView.LedgerRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                BillingLedgerView.LedgerRow row = getTableRow() == null ? null : getTableRow().getItem();
                String reviewReason = row == null ? null : row.getReviewReason();
                setTooltip(reviewReason == null || reviewReason.isBlank() ? null : new Tooltip(reviewReason));
                setGraphic(AppThemeSupport.createChip(item, "app-chip-warning"));
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

        TableColumn<BillingLedgerView.LedgerRow, String> reviewReasonCol = new TableColumn<>("Reason");
        reviewReasonCol.setCellValueFactory(new PropertyValueFactory<>("reviewReason"));
        reviewReasonCol.setPrefWidth(220);
        reviewReasonCol.setCellFactory(column -> new TableCell<BillingLedgerView.LedgerRow, String>() {
            private final Label content = new Label();

            {
                content.setWrapText(true);
                content.setMaxWidth(210);
                content.setStyle("-fx-font-size: 11px; -fx-text-fill: #6d4b21;");
                setGraphic(content);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    content.setText(null);
                    setTooltip(null);
                    setGraphic(null);
                    return;
                }
                BillingLedgerView.LedgerRow row = getTableRow() == null ? null : getTableRow().getItem();
                String reviewReason = row == null ? null : row.getReviewReason();
                if (reviewReason == null || reviewReason.isBlank()) {
                    content.setText("-");
                    content.getStyleClass().setAll("app-muted-text");
                    setTooltip(null);
                } else {
                    content.setText(reviewReason);
                    content.getStyleClass().setAll("app-helper-text");
                    setTooltip(new Tooltip(reviewReason));
                }
                setGraphic(content);
            }
        });

        TableColumn<BillingLedgerView.LedgerRow, String> receiptCol = new TableColumn<>("Receipt");
        receiptCol.setCellValueFactory(new PropertyValueFactory<>("receiptNo"));
        receiptCol.setPrefWidth(120);

        table.getColumns().clear();
        table.getColumns().add(invoiceCol);
        table.getColumns().add(parentCol);
        table.getColumns().add(childCol);
        table.getColumns().add(periodCol);
        table.getColumns().add(statusCol);
        table.getColumns().add(dueCol);
        table.getColumns().add(totalCol);
        table.getColumns().add(reviewCol);
        table.getColumns().add(reviewReasonCol);
        table.getColumns().add(receiptCol);
        table.setRowFactory(ignored -> new TableRow<BillingLedgerView.LedgerRow>() {
            @Override
            protected void updateItem(BillingLedgerView.LedgerRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("row-paid", "row-warning", "row-danger", "row-review");
                if (empty || item == null) {
                    return;
                }
                if (item.isOverdue()) {
                    getStyleClass().add("row-danger");
                    return;
                }
                if (item.isPaid()) {
                    getStyleClass().add("row-paid");
                    return;
                }
                if ("Pending".equalsIgnoreCase(item.getStatus())) {
                    getStyleClass().add("row-warning");
                    return;
                }
                if (item.isManagementReviewRecommended()) {
                    getStyleClass().add("row-review");
                }
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> onSelectionChanged.accept(newValue));
    }

    static void setupParentSummaryTable(
        TableView<BillingLedgerView.ParentSummaryRow> table,
        ObservableList<BillingLedgerView.ParentSummaryRow> rows,
        Consumer<BillingLedgerView.ParentSummaryRow> onSelectionChanged
    ) {
        table.setItems(rows);
        table.getStyleClass().add("app-data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<BillingLedgerView.ParentSummaryRow, String> parentCol = new TableColumn<>("Parent");
        parentCol.setCellValueFactory(new PropertyValueFactory<>("parentName"));

        TableColumn<BillingLedgerView.ParentSummaryRow, Number> invoiceCountCol = new TableColumn<>("Invoices");
        invoiceCountCol.setCellValueFactory(new PropertyValueFactory<>("invoiceCount"));

        TableColumn<BillingLedgerView.ParentSummaryRow, Number> unpaidCountCol = new TableColumn<>("Unpaid");
        unpaidCountCol.setCellValueFactory(new PropertyValueFactory<>("unpaidCount"));

        TableColumn<BillingLedgerView.ParentSummaryRow, Number> overdueCountCol = new TableColumn<>("Overdue");
        overdueCountCol.setCellValueFactory(new PropertyValueFactory<>("overdueCount"));

        TableColumn<BillingLedgerView.ParentSummaryRow, String> riskCol = new TableColumn<>("Risk");
        riskCol.setCellValueFactory(new PropertyValueFactory<>("riskLevel"));
        riskCol.setCellFactory(column -> new TableCell<BillingLedgerView.ParentSummaryRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                if ("Critical".equalsIgnoreCase(item)) {
                    setGraphic(AppThemeSupport.createChip(item, "app-chip-danger"));
                    return;
                }
                if ("Watch".equalsIgnoreCase(item)) {
                    setGraphic(AppThemeSupport.createChip(item, "app-chip-warning"));
                    return;
                }
                setGraphic(AppThemeSupport.createChip(item, "app-chip-success"));
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

        TableColumn<BillingLedgerView.ParentSummaryRow, String> outstandingCol = new TableColumn<>("Outstanding");
        outstandingCol.setCellValueFactory(new PropertyValueFactory<>("outstandingText"));

        TableColumn<BillingLedgerView.ParentSummaryRow, Number> ageReviewCol = new TableColumn<>("Age Rev");
        ageReviewCol.setCellValueFactory(new PropertyValueFactory<>("ageReviewCount"));

        TableColumn<BillingLedgerView.ParentSummaryRow, Number> overtimeReviewCol = new TableColumn<>("OT Rev");
        overtimeReviewCol.setCellValueFactory(new PropertyValueFactory<>("overtimeReviewCount"));

        TableColumn<BillingLedgerView.ParentSummaryRow, String> reviewCol = new TableColumn<>("Review");
        reviewCol.setCellValueFactory(new PropertyValueFactory<>("reviewStatus"));
        reviewCol.setCellFactory(column -> new TableCell<BillingLedgerView.ParentSummaryRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                BillingLedgerView.ParentSummaryRow row = getTableRow() == null ? null : getTableRow().getItem();
                String reviewSummary = row == null ? null : row.getReviewSummary();
                setTooltip(reviewSummary == null || reviewSummary.isBlank() ? null : new Tooltip(reviewSummary));
                setGraphic(AppThemeSupport.createChip(item, "app-chip-warning"));
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

        TableColumn<BillingLedgerView.ParentSummaryRow, String> lastPaidCol = new TableColumn<>("Last Payment");
        lastPaidCol.setCellValueFactory(new PropertyValueFactory<>("lastPaidText"));

        table.getColumns().clear();
        table.getColumns().add(parentCol);
        table.getColumns().add(invoiceCountCol);
        table.getColumns().add(unpaidCountCol);
        table.getColumns().add(overdueCountCol);
        table.getColumns().add(riskCol);
        table.getColumns().add(outstandingCol);
        table.getColumns().add(ageReviewCol);
        table.getColumns().add(overtimeReviewCol);
        table.getColumns().add(reviewCol);
        table.getColumns().add(lastPaidCol);
        table.setRowFactory(ignored -> new TableRow<BillingLedgerView.ParentSummaryRow>() {
            @Override
            protected void updateItem(BillingLedgerView.ParentSummaryRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("row-paid", "row-warning", "row-danger", "row-review");
                if (empty || item == null) {
                    return;
                }
                if ("Critical".equalsIgnoreCase(item.getRiskLevel())) {
                    getStyleClass().add("row-danger");
                    return;
                }
                if ("Watch".equalsIgnoreCase(item.getRiskLevel())) {
                    getStyleClass().add("row-warning");
                    return;
                }
                if (item.getOutstandingSen() <= 0L) {
                    getStyleClass().add("row-paid");
                    return;
                }
                if (item.getReviewCount() > 0) {
                    getStyleClass().add("row-review");
                }
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> onSelectionChanged.accept(newValue));
    }

    private static String statusStyleClass(BillingLedgerView.LedgerRow row) {
        if (row == null) {
            return "app-chip-neutral";
        }
        if (row.isOverdue()) {
            return "app-chip-danger";
        }
        if (row.isPaid()) {
            return "app-chip-success";
        }
        if ("Pending".equalsIgnoreCase(row.getStatus())) {
            return "app-chip-warning";
        }
        return row.isManagementReviewRecommended() ? "app-chip-info" : "app-chip-neutral";
    }
}