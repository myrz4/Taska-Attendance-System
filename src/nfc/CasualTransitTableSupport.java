package nfc;

import java.util.List;
import java.util.function.Consumer;

import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;

@SuppressWarnings("unused")
final class CasualTransitTableSupport {
    private CasualTransitTableSupport() {
    }

    static {
        Runnable keepBuildTable = () -> buildTable(null, null, row -> {
        });
        java.util.function.Consumer<Button> keepConfigureActionButton = CasualTransitTableSupport::configureActionButton;
        Runnable keepUpdateSelectionState = () -> updateSelectionState(
            null,
            null,
            List.<CasualTransitView.AuditEntry>of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        Runnable keepSetActionButtonsDisabled = () -> setActionButtonsDisabled(
            false,
            null,
            null,
            List.<CasualTransitView.AuditEntry>of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        java.util.Objects.requireNonNull(keepBuildTable);
        java.util.Objects.requireNonNull(keepConfigureActionButton);
        java.util.Objects.requireNonNull(keepUpdateSelectionState);
        java.util.Objects.requireNonNull(keepSetActionButtonsDisabled);
    }

    static void buildTable(
        TableView<CasualTransitView.VisitRow> table,
        ObservableList<CasualTransitView.VisitRow> rows,
        Consumer<CasualTransitView.VisitRow> onSelectionChanged
    ) {
        table.setItems(rows);
        table.getStyleClass().add("app-data-table");
        table.setFixedCellSize(48);

        TableColumn<CasualTransitView.VisitRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("statusLabel"));
        statusCol.setCellFactory(col -> new TableCell<CasualTransitView.VisitRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                CasualTransitView.VisitRow row = getTableRow().getItem();
                setGraphic(AppThemeSupport.createChip(item, statusChipClass(row.status())));
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

        TableColumn<CasualTransitView.VisitRow, String> paymentStatusCol = new TableColumn<>("Payment");
        paymentStatusCol.setCellValueFactory(new PropertyValueFactory<>("paymentStatusLabel"));
        paymentStatusCol.setCellFactory(col -> new TableCell<CasualTransitView.VisitRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                CasualTransitView.VisitRow row = getTableRow().getItem();
                setGraphic(AppThemeSupport.createChip(item, paymentChipClass(row.paymentStatus())));
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

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
        childCol.setStyle("-fx-alignment: CENTER-LEFT;");
        guardianCol.setStyle("-fx-alignment: CENTER-LEFT;");
        amountCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        table.setRowFactory(ignored -> new TableRow<CasualTransitView.VisitRow>() {
            @Override
            protected void updateItem(CasualTransitView.VisitRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("row-paid", "row-warning", "row-danger", "row-review");
                if (empty || item == null) {
                    return;
                }
                if ("Canceled".equalsIgnoreCase(item.status())) {
                    getStyleClass().add("row-danger");
                    return;
                }
                if ("Closed".equalsIgnoreCase(item.status())) {
                    getStyleClass().add("row-paid");
                    return;
                }
                if ("Pending".equalsIgnoreCase(item.paymentStatus())) {
                    getStyleClass().add("row-warning");
                }
            }
        });

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
        AppThemeSupport.styleToolbarButtons(button);
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
        detailsArea.setText(row == null
            ? "No visit selected yet.\n\nChoose a casual transit record to inspect guardian details, pricing, receipt, and notes."
            : row.detailText());
        auditArea.setText(row == null
            ? "No visit selected yet.\n\nSelect a record to load its audit history."
            : "Loading audit history...");
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

    private static String statusChipClass(String status) {
        if ("Closed".equalsIgnoreCase(status)) {
            return "app-chip-success";
        }
        if ("Canceled".equalsIgnoreCase(status)) {
            return "app-chip-danger";
        }
        if ("Open".equalsIgnoreCase(status)) {
            return "app-chip-info";
        }
        return "app-chip-neutral";
    }

    private static String paymentChipClass(String paymentStatus) {
        if ("Paid".equalsIgnoreCase(paymentStatus)) {
            return "app-chip-success";
        }
        if ("Pending".equalsIgnoreCase(paymentStatus)) {
            return "app-chip-warning";
        }
        if ("Void".equalsIgnoreCase(paymentStatus)) {
            return "app-chip-danger";
        }
        return "app-chip-neutral";
    }
}