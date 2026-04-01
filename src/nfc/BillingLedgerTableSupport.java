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
                    setTooltip(null);
                    setStyle("");
                    return;
                }
                BillingLedgerView.LedgerRow row = getTableRow() == null ? null : getTableRow().getItem();
                setText(item);
                String reviewReason = row == null ? null : row.getReviewReason();
                setTooltip(reviewReason == null || reviewReason.isBlank() ? null : new Tooltip(reviewReason));
                setStyle("-fx-background-color: rgba(255, 228, 181, 0.95); -fx-text-fill: #8a4b00; -fx-font-weight: bold; -fx-alignment: CENTER;");
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
                    content.setStyle("-fx-font-size: 11px; -fx-text-fill: #7a7a7a;");
                    setTooltip(null);
                } else {
                    content.setText(reviewReason);
                    content.setStyle("-fx-font-size: 11px; -fx-text-fill: #6d4b21;");
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
                if (empty || item == null) {
                    setStyle("");
                    return;
                }
                if (item.isOverdue()) {
                    setStyle("-fx-background-color: rgba(255, 214, 209, 0.85);");
                    return;
                }
                if (item.isPaid()) {
                    setStyle("-fx-background-color: rgba(220, 245, 221, 0.75);");
                    return;
                }
                if ("Pending".equalsIgnoreCase(item.getStatus())) {
                    setStyle("-fx-background-color: rgba(255, 244, 180, 0.80);");
                    return;
                }
                if (item.isManagementReviewRecommended()) {
                    setStyle("-fx-background-color: rgba(255, 228, 181, 0.65);");
                    return;
                }
                setStyle("");
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
                    setStyle("");
                    return;
                }
                setText(item);
                if ("Critical".equalsIgnoreCase(item)) {
                    setStyle("-fx-background-color: rgba(255, 217, 212, 0.95); -fx-text-fill: #8c1d13; -fx-font-weight: bold; -fx-alignment: CENTER;");
                    return;
                }
                if ("Watch".equalsIgnoreCase(item)) {
                    setStyle("-fx-background-color: rgba(255, 244, 180, 0.95); -fx-text-fill: #7a5200; -fx-font-weight: bold; -fx-alignment: CENTER;");
                    return;
                }
                setStyle("-fx-background-color: rgba(220, 245, 221, 0.90); -fx-text-fill: #1f5d26; -fx-font-weight: bold; -fx-alignment: CENTER;");
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
                    setTooltip(null);
                    setStyle("");
                    return;
                }
                BillingLedgerView.ParentSummaryRow row = getTableRow() == null ? null : getTableRow().getItem();
                setText(item);
                String reviewSummary = row == null ? null : row.getReviewSummary();
                setTooltip(reviewSummary == null || reviewSummary.isBlank() ? null : new Tooltip(reviewSummary));
                setStyle("-fx-background-color: rgba(255, 228, 181, 0.95); -fx-text-fill: #8a4b00; -fx-font-weight: bold; -fx-alignment: CENTER;");
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
                if (empty || item == null) {
                    setStyle("");
                    return;
                }
                if ("Critical".equalsIgnoreCase(item.getRiskLevel())) {
                    setStyle("-fx-background-color: rgba(255, 217, 212, 0.55);");
                    return;
                }
                if ("Watch".equalsIgnoreCase(item.getRiskLevel())) {
                    setStyle("-fx-background-color: rgba(255, 244, 180, 0.50);");
                    return;
                }
                if (item.getOutstandingSen() <= 0L) {
                    setStyle("-fx-background-color: rgba(220, 245, 221, 0.40);");
                    return;
                }
                if (item.getReviewCount() > 0) {
                    setStyle("-fx-background-color: rgba(255, 228, 181, 0.45);");
                    return;
                }
                setStyle("");
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> onSelectionChanged.accept(newValue));
    }
}