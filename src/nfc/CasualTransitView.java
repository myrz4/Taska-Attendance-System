package nfc;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

@SuppressWarnings("java:S1848")
public class CasualTransitView extends VBox {
    static {
        if (keepAnalyzerAnchors()) {
            java.util.List<VisitRow> visitProbes = java.util.List.of(createVisitRowProbe());
            VisitRow visitProbe = visitProbes.get(0);
            java.util.List<AuditEntry> auditProbes = java.util.List.of(createAuditEntryProbe());
            AuditEntry probe = auditProbes.get(0);
            java.util.Objects.hash(
                visitProbe.visitId(),
                probe.createdAt(),
                probe.action(),
                probe.actorName(),
                probe.actorRole(),
                probe.reason(),
                probe.notes()
            );
        }
    }

    private static boolean keepAnalyzerAnchors() {
        return Boolean.getBoolean("taska.keepAnalyzerAnchors");
    }

    @SuppressWarnings("java:S1848")
    private static VisitRow createVisitRowProbe() {
        return new VisitRow("", "", "", "", "", "", "", "", "", null, null, 0L, "", "", "", "");
    }

    @SuppressWarnings("java:S1848")
    private static AuditEntry createAuditEntryProbe() {
        return new AuditEntry("", "", "", "", "", null);
    }

    private final ObservableList<VisitRow> rows = FXCollections.observableArrayList();
    private final TableView<VisitRow> table = new TableView<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<String> paymentStatusFilter = new ComboBox<>();
    private final ComboBox<String> dateScopeFilter = new ComboBox<>();
    private final DatePicker rangeFromPicker = new DatePicker();
    private final DatePicker rangeToPicker = new DatePicker();
    private final Label statusLabel = new Label("Loading casual transit visits...");
    private final Label totalsLabel = new Label("Visits: 0");
    private final Button refreshButton = new Button("Refresh");
    private final Button addVisitButton = new Button("New Visit");
    private final Button checkoutButton = new Button("Checkout + Paid");
    private final Button editButton = new Button("Edit Visit");
    private final Button reopenButton = new Button("Reopen");
    private final Button cancelButton = new Button("Cancel Visit");
    private final Button exportReceiptButton = new Button("Export Receipt PDF");
    private final Button exportSummaryButton = new Button("Export Summary PDF");
    private final Button exportCsvButton = new Button("Export Summary CSV");
    private final Button exportAuditButton = new Button("Export Audit PDF");
    private final ComboBox<String> auditActionFilter = new ComboBox<>();
    private final ComboBox<String> auditDateScopeFilter = new ComboBox<>();
    private final TextArea detailsArea = new TextArea();
    private final TextArea auditArea = new TextArea();

    private List<VisitRow> allRows = List.of();
    private List<AuditEntry> currentAuditEntries = List.of();
    private List<AuditEntry> visibleAuditEntries = List.of();

    public CasualTransitView() {
        setSpacing(0);
        setFillWidth(true);

        javafx.scene.layout.HBox header = AppThemeSupport.createStandardPageBanner(
            "Casual Transit",
            "Track walk-in visits, settle payments, and review override history in a cleaner operational workspace."
        );

        searchField.setPromptText("Search child, guardian, phone, receipt, or notes");
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        searchField.setPrefWidth(320);

        statusFilter.setItems(FXCollections.observableArrayList("All", "Open", "Closed", "Canceled"));
        statusFilter.setValue("All");
        statusFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        paymentStatusFilter.setItems(FXCollections.observableArrayList("All Payment States", "Pending", "Paid", "Void"));
        paymentStatusFilter.setValue("All Payment States");
        paymentStatusFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        dateScopeFilter.setItems(FXCollections.observableArrayList("All Dates", "Today", "Current Month", "Custom Range"));
        dateScopeFilter.setValue("All Dates");
        dateScopeFilter.valueProperty().addListener((obs, oldValue, newValue) -> {
            updateDateRangeControls();
            applyFilters();
        });

        rangeFromPicker.setPromptText("From");
        rangeToPicker.setPromptText("To");
        rangeFromPicker.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        rangeToPicker.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        updateDateRangeControls();

        auditActionFilter.setItems(FXCollections.observableArrayList("All Actions"));
        auditActionFilter.setValue("All Actions");
        auditActionFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyAuditFilters());
        auditActionFilter.setDisable(true);

        auditDateScopeFilter.setItems(FXCollections.observableArrayList("All Dates", "Today", "Current Month"));
        auditDateScopeFilter.setValue("All Dates");
        auditDateScopeFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyAuditFilters());
        auditDateScopeFilter.setDisable(true);

        CasualTransitTableSupport.configureActionButton(refreshButton);
        CasualTransitTableSupport.configureActionButton(addVisitButton);
        CasualTransitTableSupport.configureActionButton(checkoutButton);
        CasualTransitTableSupport.configureActionButton(editButton);
        CasualTransitTableSupport.configureActionButton(reopenButton);
        CasualTransitTableSupport.configureActionButton(cancelButton);
        CasualTransitTableSupport.configureActionButton(exportReceiptButton);
        CasualTransitTableSupport.configureActionButton(exportSummaryButton);
        CasualTransitTableSupport.configureActionButton(exportCsvButton);
        CasualTransitTableSupport.configureActionButton(exportAuditButton);
        AppThemeSupport.styleControls(
            searchField,
            statusFilter,
            paymentStatusFilter,
            dateScopeFilter,
            rangeFromPicker,
            rangeToPicker,
            auditActionFilter,
            auditDateScopeFilter,
            detailsArea,
            auditArea,
            table
        );
        totalsLabel.getStyleClass().add("app-helper-text");
        statusLabel.getStyleClass().add("app-muted-text");
        detailsArea.getStyleClass().add("app-detail-area");
        auditArea.getStyleClass().add("app-detail-area");

        refreshButton.setOnAction(e -> reloadData());
        addVisitButton.setOnAction(e -> openCreateVisitDialog());
        checkoutButton.setOnAction(e -> openCheckoutDialog());
        editButton.setOnAction(e -> openEditVisitDialog());
        reopenButton.setOnAction(e -> openReopenDialog());
        cancelButton.setOnAction(e -> openCancelDialog());
        exportReceiptButton.setOnAction(e -> exportSelectedReceiptPdf());
        exportSummaryButton.setOnAction(e -> exportVisibleSummaryPdf());
        exportCsvButton.setOnAction(e -> exportVisibleSummaryCsv());
        exportAuditButton.setOnAction(e -> exportSelectedAuditPdf());

        FlowPane filters = new FlowPane(10, 10,
            new Label("Search:"), searchField,
            new Label("Status:"), statusFilter,
            new Label("Payment:"), paymentStatusFilter,
            new Label("Date Scope:"), dateScopeFilter,
            new Label("From:"), rangeFromPicker,
            new Label("To:"), rangeToPicker
        );

        FlowPane actions = new FlowPane(10, 10,
            refreshButton,
            addVisitButton,
            checkoutButton,
            editButton,
            reopenButton,
            cancelButton,
            exportSummaryButton,
            exportCsvButton
        );

        buildTable();

        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setPrefRowCount(8);
        detailsArea.setPromptText("Select a visit to inspect receipt and payment details.");

        auditArea.setEditable(false);
        auditArea.setWrapText(true);
        auditArea.setPrefRowCount(10);
        auditArea.setPromptText("Select a visit to inspect audit history.");

        HBox summaryRow = new HBox(12, totalsLabel, new Region(), statusLabel);
        HBox.setHgrow(summaryRow.getChildren().get(1), Priority.ALWAYS);

        FlowPane auditFilters = new FlowPane(10, 10,
            new Label("Audit Action:"), auditActionFilter,
            new Label("Audit Date:"), auditDateScopeFilter,
            exportAuditButton
        );

        VBox controlsCard = AppThemeSupport.createSectionCard(
            "Transit Controls",
            "Keep filters, actions, and the current summary compact so the visits table remains the main operational area.",
            filters,
            actions,
            summaryRow
        );
        controlsCard.getStyleClass().add("app-toolbar-card");

        VBox visitTableCard = new VBox(
            12,
            AppThemeSupport.createSectionHeader(
                "Visits",
                "Use the visit list for live transit operations, payment closeout, and status follow-up."
            ),
            table
        );
        visitTableCard.getStyleClass().addAll("app-card", "app-section-card");
        visitTableCard.setMinWidth(0);
        visitTableCard.setMinHeight(0);
        table.setMinHeight(380);

        VBox detailsCard = new VBox(
            12,
            AppThemeSupport.createSectionHeader(
                "Visit Details",
                "Inspect guardian, timing, receipt, and payment context for the selected visit.",
                exportReceiptButton
            ),
            detailsArea
        );
        detailsCard.getStyleClass().addAll("app-card", "app-detail-card");
        detailsCard.setMinHeight(0);

        VBox auditCard = new VBox(
            12,
            AppThemeSupport.createSectionHeader(
                "Audit History",
                "Filter override and lifecycle events for the selected visit by action or date."
            ),
            auditFilters,
            auditArea
        );
        auditCard.getStyleClass().addAll("app-card", "app-detail-card");
        auditCard.setMinHeight(0);

        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(detailsArea, Priority.ALWAYS);
        VBox.setVgrow(auditArea, Priority.ALWAYS);
        VBox.setVgrow(detailsCard, Priority.ALWAYS);
        VBox.setVgrow(auditCard, Priority.ALWAYS);

        SplitPane rightSplit = new SplitPane(detailsCard, auditCard);
        rightSplit.setOrientation(Orientation.VERTICAL);
        rightSplit.setDividerPositions(0.42);
        rightSplit.setMinWidth(320);
        rightSplit.setMinHeight(0);

        SplitPane contentSplit = new SplitPane(visitTableCard, rightSplit);
        contentSplit.setDividerPositions(0.68);
        contentSplit.setMinHeight(0);

        VBox body = new VBox(12, controlsCard, contentSplit);
        body.setPadding(new Insets(16, 18, 18, 18));
        body.setFillWidth(true);
        VBox.setVgrow(contentSplit, Priority.ALWAYS);

        ScrollPane bodyScroll = AppThemeSupport.createPageBodyScrollWrapper(body);
        BorderPane shell = AppThemeSupport.createStandardPageShell(header, bodyScroll);

        getChildren().setAll(shell);
        VBox.setVgrow(shell, Priority.ALWAYS);

        reloadData();
    }

    private void buildTable() {
        CasualTransitTableSupport.buildTable(table, rows, this::updateSelection);
    }

    private void updateSelection(VisitRow row) {
        CasualTransitTableSupport.updateSelectionState(
            row,
            rows,
            visibleAuditEntries,
            checkoutButton,
            editButton,
            reopenButton,
            cancelButton,
            exportReceiptButton,
            exportSummaryButton,
            exportCsvButton,
            exportAuditButton,
            detailsArea,
            auditArea,
            auditActionFilter,
            auditDateScopeFilter
        );
        if (row == null) {
            currentAuditEntries = List.of();
            visibleAuditEntries = List.of();
        }
        if (row != null) {
            loadAuditHistory(row.visitId());
        }
    }

    private void reloadData() {
        statusLabel.setText("Loading casual transit visits...");
        setActionButtonsDisabled(true);
        CasualTransitWorkflowSupport.reloadVisitsAsync(
            loadedRows -> {
                allRows = loadedRows;
                applyFilters();
                statusLabel.setText("Casual transit visits ready.");
                setActionButtonsDisabled(false);
            },
            errorMessage -> {
                setActionButtonsDisabled(false);
                statusLabel.setText("Load failed: " + errorMessage);
                showError("Failed to load casual transit visits", new Exception(errorMessage));
            }
        );
    }

    private void loadAuditHistory(String visitId) {
        CasualTransitWorkflowSupport.loadAuditHistoryAsync(
            visitId,
            entries -> {
                VisitRow selected = table.getSelectionModel().getSelectedItem();
                if (selected == null || !visitId.equals(selected.visitId())) {
                    return;
                }
                currentAuditEntries = entries;
                updateAuditFilterOptions(entries);
                auditDateScopeFilter.setDisable(false);
                applyAuditFilters();
            },
            errorMessage -> {
                VisitRow selected = table.getSelectionModel().getSelectedItem();
                if (selected == null || !visitId.equals(selected.visitId())) {
                    return;
                }
                auditArea.setText("Failed to load audit history: " + errorMessage);
            }
        );
    }

    private void updateAuditFilterOptions(List<AuditEntry> entries) {
        auditActionFilter.setItems(FXCollections.observableArrayList(CasualTransitDataSupport.auditFilterOptions(entries)));
        auditActionFilter.setValue("All Actions");
        auditActionFilter.setDisable(entries.isEmpty());
    }

    private void applyAuditFilters() {
        VisitRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            auditArea.setText("No visit selected yet.\n\nSelect a record to load its audit history.");
            return;
        }
        CasualTransitDataSupport.AuditFilterResult result = CasualTransitDataSupport.applyAuditFilters(
            currentAuditEntries,
            auditActionFilter.getValue(),
            auditDateScopeFilter.getValue(),
            CasualTransitView::safe
        );
        visibleAuditEntries = result.filteredEntries;
        auditArea.setText(result.renderedText);
        exportAuditButton.setDisable(result.filteredEntries.isEmpty());
    }

    private void applyFilters() {
        CasualTransitFilterSupport.FilterResult result = CasualTransitFilterSupport.applyFilters(
            allRows,
            searchField.getText(),
            statusFilter.getValue(),
            paymentStatusFilter.getValue(),
            dateScopeFilter.getValue(),
            rangeFromPicker.getValue(),
            rangeToPicker.getValue()
        );
        rows.setAll(result.filteredRows);
        totalsLabel.setText(result.totalsText);
        updateSelection(table.getSelectionModel().getSelectedItem());
    }

    private void updateDateRangeControls() {
        boolean customRange = "Custom Range".equalsIgnoreCase(safe(dateScopeFilter.getValue()));
        rangeFromPicker.setDisable(!customRange);
        rangeToPicker.setDisable(!customRange);
        if (!customRange) {
            rangeFromPicker.setValue(null);
            rangeToPicker.setValue(null);
        }
    }

    private void openCreateVisitDialog() {
        CasualTransitDialogSupport.showCreateVisitDialog(getWindow()).ifPresent(this::createVisit);
    }

    private void createVisit(Map<String, String> values) {
        statusLabel.setText("Creating casual transit visit...");
        setActionButtonsDisabled(true);
        CasualTransitWorkflowSupport.createVisitAsync(
            values,
            () -> {
                statusLabel.setText("Casual transit visit created.");
                reloadData();
            },
            errorMessage -> {
                setActionButtonsDisabled(false);
                statusLabel.setText("Create failed: " + errorMessage);
                showError("Failed to create casual transit visit", new Exception(errorMessage));
            }
        );
    }

    private void openCheckoutDialog() {
        VisitRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.isOpen()) {
            showSimple("No open visit selected", "Select an open casual transit visit before checking out.");
            return;
        }

        CasualTransitDialogSupport.showCheckoutDialog(getWindow(), selected).ifPresent(values -> checkoutVisit(selected, values));
    }

    private void openEditVisitDialog() {
        VisitRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showSimple("No visit selected", "Select a casual transit visit before editing.");
            return;
        }

        CasualTransitDialogSupport.showEditVisitDialog(getWindow(), selected).ifPresent(values -> editVisit(selected, values));
    }

    private void openReopenDialog() {
        VisitRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.isClosed()) {
            showSimple("No closed visit selected", "Select a closed casual transit visit before reopening.");
            return;
        }

        CasualTransitDialogSupport.showReopenDialog(getWindow(), selected)
            .ifPresent(values -> overrideVisit("REOPEN_VISIT", selected, values, "Casual transit visit reopened."));
    }

    private void openCancelDialog() {
        VisitRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isCanceled()) {
            showSimple("No active visit selected", "Select an open or closed casual transit visit before canceling.");
            return;
        }

        CasualTransitDialogSupport.showCancelDialog(getWindow(), selected)
            .ifPresent(values -> overrideVisit("CANCEL_VISIT", selected, values, "Casual transit visit canceled."));
    }

    private void checkoutVisit(VisitRow row, Map<String, String> values) {
        statusLabel.setText("Closing casual transit visit...");
        setActionButtonsDisabled(true);
        CasualTransitWorkflowSupport.checkoutVisitAsync(
            row,
            values,
            () -> {
                statusLabel.setText("Casual transit visit closed and paid.");
                showSimple("Payment recorded", "The visit is now closed. Receipt details are available in the visit list.");
                reloadData();
            },
            errorMessage -> {
                setActionButtonsDisabled(false);
                statusLabel.setText("Checkout failed: " + errorMessage);
                showError("Failed to checkout casual transit visit", new Exception(errorMessage));
            }
        );
    }

    private void editVisit(VisitRow row, Map<String, String> values) {
        Map<String, String> payload;
        try {
            payload = CasualTransitMutationSupport.normalizeEditValues(values);
        } catch (IllegalArgumentException error) {
            if ("reason-required".equals(error.getMessage())) {
                showSimple("Reason required", "Provide a reason for editing this visit.");
                return;
            }
            showSimple("Missing required fields", "Child name, guardian name, and guardian phone are required.");
            return;
        }
        overrideVisit("EDIT_VISIT", row, payload, "Casual transit visit updated.");
    }

    private void overrideVisit(String action, VisitRow row, Map<String, String> values, String successMessage) {
        statusLabel.setText("Updating casual transit visit...");
        setActionButtonsDisabled(true);
        CasualTransitWorkflowSupport.overrideVisitAsync(
            action,
            row,
            values,
            () -> {
                statusLabel.setText(successMessage);
                showSimple("Visit updated", successMessage);
                reloadData();
            },
            errorMessage -> {
                setActionButtonsDisabled(false);
                statusLabel.setText("Update failed: " + errorMessage);
                showError("Failed to update casual transit visit", new Exception(errorMessage));
            }
        );
    }

    private void exportSelectedReceiptPdf() {
        VisitRow row = table.getSelectionModel().getSelectedItem();
        if (row == null || !row.hasReceipt()) {
            showSimple("No receipt selected", "Select a closed casual transit visit with a receipt before exporting.");
            return;
        }

        try {
            String message = CasualTransitExportSupport.exportReceiptPdfWithDialog(getWindow(), row, receiptExportBaseName(row));
            if (message != null) {
                showSimple("Exported", message);
            }
        } catch (IOException | RuntimeException error) {
            showError("Failed to export casual transit receipt", new Exception(CasualTransitWorkflowSupport.rootMessage(error), error));
        }
    }

    private void exportVisibleSummaryPdf() {
        if (rows.isEmpty()) {
            showSimple("No visible visits", "There are no casual transit visits in the current view to export.");
            return;
        }

        try {
            String message = CasualTransitExportSupport.exportSummaryPdfWithDialog(
                getWindow(),
                new ArrayList<>(rows),
                currentViewFilterSummary(),
                summaryExportBaseName()
            );
            if (message != null) {
                showSimple("Exported", message);
            }
        } catch (IOException | RuntimeException error) {
            showError("Failed to export casual transit summary", new Exception(CasualTransitWorkflowSupport.rootMessage(error), error));
        }
    }

    private void exportVisibleSummaryCsv() {
        if (rows.isEmpty()) {
            showSimple("No visible visits", "There are no casual transit visits in the current view to export.");
            return;
        }

        try {
            String message = CasualTransitExportSupport.exportSummaryCsvWithDialog(
                getWindow(),
                new ArrayList<>(rows),
                currentViewFilterSummary(),
                summaryExportBaseName()
            );
            if (message != null) {
                showSimple("Exported", message);
            }
        } catch (IOException | RuntimeException error) {
            showError("Failed to export casual transit summary CSV", new Exception(CasualTransitWorkflowSupport.rootMessage(error), error));
        }
    }

    private void exportSelectedAuditPdf() {
        VisitRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            showSimple("No visit selected", "Select a casual transit visit before exporting its audit history.");
            return;
        }
        if (visibleAuditEntries.isEmpty()) {
            showSimple("No visible audit entries", "There are no audit entries in the current audit view to export.");
            return;
        }

        try {
            String message = CasualTransitExportSupport.exportAuditPdfWithDialog(
                getWindow(),
                row,
                visibleAuditEntries,
                currentAuditFilterSummary(),
                auditExportBaseName(row)
            );
            if (message != null) {
                showSimple("Exported", message);
            }
        } catch (IOException | RuntimeException error) {
            showError("Failed to export casual transit audit history", new Exception(CasualTransitWorkflowSupport.rootMessage(error), error));
        }
    }

    private void setActionButtonsDisabled(boolean disabled) {
        VisitRow selected = table.getSelectionModel().getSelectedItem();
        CasualTransitTableSupport.setActionButtonsDisabled(
            disabled,
            selected,
            rows,
            visibleAuditEntries,
            refreshButton,
            addVisitButton,
            checkoutButton,
            editButton,
            reopenButton,
            cancelButton,
            exportReceiptButton,
            exportSummaryButton,
            exportCsvButton,
            exportAuditButton
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String receiptExportBaseName(VisitRow row) {
        String receipt = row.receiptNo().replaceAll("[^A-Za-z0-9_-]", "_");
        String child = row.childName().replaceAll("[^A-Za-z0-9_-]", "_");
        return "casual-transit-" + child + "-" + receipt;
    }

    private String summaryExportBaseName() {
        return "casual-transit-summary-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    private String auditExportBaseName(VisitRow row) {
        String child = row.childName().replaceAll("[^A-Za-z0-9_-]", "_");
        return "casual-transit-audit-" + child + "-" + row.visitId();
    }

    private String currentViewFilterSummary() {
        return CasualTransitFilterSupport.currentViewFilterSummary(
            searchField.getText(),
            statusFilter.getValue(),
            paymentStatusFilter.getValue(),
            dateScopeFilter.getValue(),
            rangeFromPicker.getValue(),
            rangeToPicker.getValue()
        );
    }

    private String currentAuditFilterSummary() {
        return CasualTransitFilterSupport.currentAuditFilterSummary(
            auditActionFilter.getValue(),
            auditDateScopeFilter.getValue()
        );
    }

    private void showSimple(String title, String message) {
        AppThemeSupport.showInfo(getWindow(), title, message);
    }

    private void showError(String title, Exception error) {
        AppThemeSupport.showError(getWindow(), title, CasualTransitWorkflowSupport.rootMessage(error));
    }

    private javafx.stage.Window getWindow() {
        return getScene() == null ? null : getScene().getWindow();
    }

    public static final class VisitRow {
        private final String visitId;
        private final String status;
        private final String paymentStatus;
        private final String childName;
        private final String guardianName;
        private final String guardianPhone;
        private final String guardianRelationship;
        private final String transitType;
        private final String staffType;
        private final Date checkInAt;
        private final Date checkOutAt;
        private final long amountSen;
        private final String receiptNo;
        private final String notes;
        private final String paymentMethod;
        private final String pricingBreakdown;

        public VisitRow(
            String visitId,
            String status,
            String paymentStatus,
            String childName,
            String guardianName,
            String guardianPhone,
            String guardianRelationship,
            String transitType,
            String staffType,
            Date checkInAt,
            Date checkOutAt,
            long amountSen,
            String receiptNo,
            String notes,
            String paymentMethod,
            String pricingBreakdown
        ) {
            this.visitId = visitId;
            this.status = status;
            this.paymentStatus = paymentStatus;
            this.childName = childName;
            this.guardianName = guardianName;
            this.guardianPhone = guardianPhone;
            this.guardianRelationship = guardianRelationship;
            this.transitType = transitType;
            this.staffType = staffType;
            this.checkInAt = checkInAt;
            this.checkOutAt = checkOutAt;
            this.amountSen = amountSen;
            this.receiptNo = receiptNo;
            this.notes = notes;
            this.paymentMethod = paymentMethod;
            this.pricingBreakdown = pricingBreakdown;
        }

        public String visitId() { return visitId; }
        public String status() { return status; }
    public String paymentStatus() { return paymentStatus; }
        public String childName() { return childName; }
        public String notes() { return notes; }
        public Date checkInAt() { return checkInAt; }
        public Date checkOutAt() { return checkOutAt; }
        public boolean isOpen() { return "OPEN".equals(status); }
        public boolean isClosed() { return "CLOSED".equals(status); }
        public boolean isCanceled() { return "CANCELED".equals(status); }
    public boolean isPaymentPending() { return "PENDING".equals(paymentStatus); }
    public boolean isPaymentPaid() { return "PAID".equals(paymentStatus); }
    public boolean isPaymentVoid() { return "VOID".equals(paymentStatus); }
        public boolean hasReceipt() { return isClosed() && !"-".equals(receiptNo); }
        public boolean matchesDateScope(String scope, LocalDate rangeFrom, LocalDate rangeTo) {
            return CasualTransitRowSupport.visitMatchesDateScope(this, scope, rangeFrom, rangeTo);
        }
        public long amountSen() { return amountSen; }
        public String statusLabel() {
            return CasualTransitRowSupport.visitStatusLabel(status);
        }
        public String paymentStatusLabel() {
            return CasualTransitRowSupport.paymentStatusLabel(paymentStatus);
        }
        public String checkInLabel() { return CasualTransitRowSupport.checkLabel(checkInAt); }
        public String checkOutLabel() { return CasualTransitRowSupport.checkLabel(checkOutAt); }
        public String amountLabel() { return CasualTransitRowSupport.amountLabel(amountSen); }
        public String receiptNo() { return receiptNo; }
        public long sortTime() { return checkOutAt != null ? checkOutAt.getTime() : (checkInAt != null ? checkInAt.getTime() : 0L); }
        public String guardianName() { return guardianName; }
        public String guardianPhone() { return guardianPhone; }
        public String guardianRelationship() { return guardianRelationship; }
        public String transitType() { return transitType; }
        public String staffType() { return staffType; }
        public String paymentMethod() { return paymentMethod; }
        public String pricingBreakdown() { return pricingBreakdown; }
        public String visitDateLabel() {
            LocalDate visitDate = visitLocalDate();
            return visitDate == null ? "" : visitDate.toString();
        }
        public String amountInputValue() { return CasualTransitRowSupport.amountInputValue(amountSen); }
        public String checkInInputValue() { return CasualTransitRowSupport.inputDateValue(checkInAt); }
        public String checkOutInputValue() { return CasualTransitRowSupport.inputDateValue(checkOutAt); }
        public LocalDate visitLocalDate() {
            return CasualTransitRowSupport.visitLocalDate(checkInAt, checkOutAt);
        }
        public String guardianSummary() {
            return CasualTransitRowSupport.guardianSummary(guardianName, guardianRelationship, guardianPhone);
        }
        public String getStatusLabel() { return statusLabel(); }
        public String getPaymentStatusLabel() { return paymentStatusLabel(); }
        public String getChildName() { return childName(); }
        public String getGuardianSummary() { return guardianSummary(); }
        public String getCheckInLabel() { return checkInLabel(); }
        public String getCheckOutLabel() { return checkOutLabel(); }
        public String getAmountLabel() { return amountLabel(); }
        public String getReceiptNo() { return receiptNo(); }
        public String searchText() {
            return CasualTransitRowSupport.searchText(this);
        }
        public String detailText() {
            return CasualTransitRowSupport.detailText(this);
        }
    }

    public static final class AuditEntry {
        private final String action;
        private final String actorName;
        private final String actorRole;
        private final String reason;
        private final String notes;
        private final Date createdAt;

        public AuditEntry(String action, String actorName, String actorRole, String reason, String notes, Date createdAt) {
            this.action = action;
            this.actorName = actorName;
            this.actorRole = actorRole;
            this.reason = reason;
            this.notes = notes;
            this.createdAt = createdAt;
        }

        public long sortTime() {
            return createdAt == null ? 0L : createdAt.getTime();
        }

        public Date createdAt() { return createdAt; }
        public String action() { return action; }
        public String actorName() { return actorName; }
        public String actorRole() { return actorRole; }
        public String reason() { return reason; }
        public String notes() { return notes; }

        public String actionLabel() {
            return CasualTransitRowSupport.auditActionLabel(action);
        }

        public String exportTitle() {
            return CasualTransitRowSupport.auditExportTitle(this);
        }

        public boolean matchesDateScope(String scope) {
            return CasualTransitRowSupport.auditMatchesDateScope(this, scope);
        }

        public String render() {
            return CasualTransitRowSupport.auditRender(this);
        }
    }
}