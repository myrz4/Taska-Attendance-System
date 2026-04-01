package nfc;

import java.util.List;
import java.util.function.Consumer;

import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;

final class CasualTransitTableSupport {
    private CasualTransitTableSupport() {
    }

    static void buildTable(
        TableView<CasualTransitView.VisitRow> table,
        ObservableList<CasualTransitView.VisitRow> rows,
        Consumer<CasualTransitView.VisitRow> onSelectionChanged
    ) {
        table.setItems(rows);

        TableColumn<CasualTransitView.VisitRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("statusLabel"));

        TableColumn<CasualTransitView.VisitRow, String> paymentStatusCol = new TableColumn<>("Payment");
        paymentStatusCol.setCellValueFactory(new PropertyValueFactory<>("paymentStatusLabel"));

        TableColumn<CasualTransitView.VisitRow, String> childCol = new TableColumn<>("Child");
        childCol.setCellValueFactory(new PropertyValueFactory<>("childName"));

        TableColumn<CasualTransitView.VisitRow, String> guardianCol = new TableColumn<>("Guardian");
        guardianCol.setCellValueFactory(new PropertyValueFactory<>("guardianSummary"));

        TableColumn<CasualTransitView.VisitRow, String> checkInCol = new TableColumn<>("Check In");
        checkInCol.setCellValueFactory(new PropertyValueFactory<>("checkInLabel"));

        TableColumn<CasualTransitView.VisitRow, String> checkOutCol = new TableColumn<>("Check Out");
        checkOutCol.setCellValueFactory(new PropertyValueFactory<>("checkOutLabel"));

        TableColumn<CasualTransitView.VisitRow, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amountLabel"));

        TableColumn<CasualTransitView.VisitRow, String> receiptCol = new TableColumn<>("Receipt");
        receiptCol.setCellValueFactory(new PropertyValueFactory<>("receiptNo"));

        for (TableColumn<CasualTransitView.VisitRow, String> column : List.of(
            statusCol,
            paymentStatusCol,
            childCol,
            guardianCol,
            checkInCol,
            checkOutCol,
            amountCol,
            receiptCol
        )) {
            column.setStyle("-fx-alignment: CENTER;");
            column.setCellFactory(col -> new TableCell<CasualTransitView.VisitRow, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    setAlignment(Pos.CENTER);
                }
            });
        }

        table.getColumns().clear();
        table.getColumns().add(statusCol);
        table.getColumns().add(paymentStatusCol);
        table.getColumns().add(childCol);
        table.getColumns().add(guardianCol);
        table.getColumns().add(checkInCol);
        table.getColumns().add(checkOutCol);
        table.getColumns().add(amountCol);
        table.getColumns().add(receiptCol);
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> onSelectionChanged.accept(newValue));
    }

    static void configureActionButton(Button button) {
        button.setStyle("-fx-background-color: #FFCB3C; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 24px;");
    }

    static void updateSelectionState(
        CasualTransitView.VisitRow row,
        ObservableList<CasualTransitView.VisitRow> rows,
        List<CasualTransitView.AuditEntry> visibleAuditEntries,
        Button checkoutButton,
        Button editButton,
        Button reopenButton,
        Button cancelButton,
        Button exportReceiptButton,
        Button exportSummaryButton,
        Button exportCsvButton,
        Button exportAuditButton,
        TextArea detailsArea,
        TextArea auditArea,
        ComboBox<String> auditActionFilter,
        ComboBox<String> auditDateScopeFilter
    ) {
        checkoutButton.setDisable(row == null || !row.isOpen());
        editButton.setDisable(row == null);
        reopenButton.setDisable(row == null || !row.isClosed());
        cancelButton.setDisable(row == null || row.isCanceled());
        exportReceiptButton.setDisable(row == null || !row.hasReceipt());
        exportSummaryButton.setDisable(rows.isEmpty());
        exportCsvButton.setDisable(rows.isEmpty());
        exportAuditButton.setDisable(row == null || visibleAuditEntries.isEmpty());
        detailsArea.setText(row == null ? "" : row.detailText());
        auditArea.setText(row == null ? "" : "Loading audit history...");
        if (row == null) {
            auditActionFilter.getItems().setAll("All Actions");
            auditActionFilter.setValue("All Actions");
            auditActionFilter.setDisable(true);
            auditDateScopeFilter.setValue("All Dates");
            auditDateScopeFilter.setDisable(true);
        }
    }

    static void setActionButtonsDisabled(
        boolean disabled,
        CasualTransitView.VisitRow selected,
        ObservableList<CasualTransitView.VisitRow> rows,
        List<CasualTransitView.AuditEntry> visibleAuditEntries,
        Button refreshButton,
        Button addVisitButton,
        Button checkoutButton,
        Button editButton,
        Button reopenButton,
        Button cancelButton,
        Button exportReceiptButton,
        Button exportSummaryButton,
        Button exportCsvButton,
        Button exportAuditButton
    ) {
        refreshButton.setDisable(disabled);
        addVisitButton.setDisable(disabled);
        checkoutButton.setDisable(disabled || selected == null || !selected.isOpen());
        editButton.setDisable(disabled || selected == null);
        reopenButton.setDisable(disabled || selected == null || !selected.isClosed());
        cancelButton.setDisable(disabled || selected == null || selected.isCanceled());
        exportReceiptButton.setDisable(disabled || selected == null || !selected.hasReceipt());
        exportSummaryButton.setDisable(disabled || rows.isEmpty());
        exportCsvButton.setDisable(disabled || rows.isEmpty());
        exportAuditButton.setDisable(disabled || selected == null || visibleAuditEntries.isEmpty());
    }
}