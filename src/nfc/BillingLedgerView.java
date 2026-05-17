package nfc;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public class BillingLedgerView extends VBox {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ms-MY"));
    private static final long PARENT_RISK_WATCH_OUTSTANDING_SEN = 20_000L;
    private static final long PARENT_RISK_CRITICAL_OUTSTANDING_SEN = 50_000L;

    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<String> dateScopeFilter = new ComboBox<>();
    private final ComboBox<String> sortFilter = new ComboBox<>();
    private final ComboBox<String> riskFilter = new ComboBox<>();
    private final ComboBox<String> reviewFilter = new ComboBox<>();
    private final ComboBox<String> parentSortFilter = new ComboBox<>();
    private final TextField issuePeriodField = new TextField();
    private final Button refreshButton = new Button("Refresh");
    private final Button issueInvoicesButton = new Button("Issue Period");
    private final Button issueNextMonthButton = new Button("Issue Next Month");
    private final Button issueVisibleInvoicesButton = new Button("Issue Visible");
    private final Button quickOverdueButton = new Button("Only Overdue");
    private final Button quickUnpaidCurrentMonthButton = new Button("Unpaid Month");
    private final Button quickCurrentMonthButton = new Button("Current Month");
    private final Button quickCriticalFamiliesButton = new Button("Critical");
    private final Button quickNeedsReviewButton = new Button("Needs Review");
    private final Button quickAgeReviewButton = new Button("Age Review");
    private final Button quickOvertimeReviewButton = new Button("Overtime Review");
    private final Button resetFiltersButton = new Button("Reset Filters");
    private final Button focusParentButton = new Button("Focus Parent");
    private final Button parentUnpaidButton = new Button("Parent Unpaid");
    private final Button latestUnpaidInvoiceButton = new Button("Latest Unpaid");
    private final Button clearParentFocusButton = new Button("Clear Focus");
    private final Button exportParentSummaryButton = new Button("PDF");
    private final Button exportParentSummaryOpenButton = new Button("Open PDF");
    private final Button printParentSummaryButton = new Button("Print PDF");
    private final Button exportVisibleParentsButton = new Button("Batch PDFs");
    private final Button exportHtmlButton = new Button("PDF");
    private final Button exportHtmlOpenButton = new Button("Open PDF");
    private final Button printHtmlButton = new Button("Print PDF");
    private final Button markCashPaidButton = new Button("Cash Paid");
    private final Label statusLabel = new Label("Loading billing ledger...");
    private final Label invoiceCountLabel = new Label("0");
    private final Label paidTotalLabel = new Label("RM0.00");
    private final Label outstandingTotalLabel = new Label("RM0.00");
    private final Label overdueCountLabel = new Label("0");
    private final Label ageReviewSummaryLabel = new Label("0 fam / 0 inv");
    private final Label overtimeReviewSummaryLabel = new Label("0 fam / 0 inv");
    private final Label periodTotalsLabel = new Label("Periods: -");
    private final Label parentFocusLabel = new Label("Parent Focus: none");
    private final TableView<ParentSummaryRow> parentSummaryTable = new TableView<>();
    private final ObservableList<ParentSummaryRow> parentSummaryRows = FXCollections.observableArrayList();
    private final TableView<LedgerRow> table = new TableView<>();
    private final ObservableList<LedgerRow> visibleRows = FXCollections.observableArrayList();
    private final TextArea detailsArea = new TextArea();

    private List<LedgerRow> allRows = List.of();
    private String focusedParentId;

    public BillingLedgerView() {
        setSpacing(0);
        setFillWidth(true);

        javafx.scene.layout.HBox header = AppThemeSupport.createStandardPageBanner(
            "Billing Ledger",
            "Track invoices, family risk, receipts, and review work in one cleaner billing workflow."
        );

        searchField.setPromptText("Search parent, student(s), invoice ID, receipt, or period");
        searchField.setPrefWidth(340);
        searchField.setMinWidth(280);
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        statusFilter.setItems(FXCollections.observableArrayList("All", "Unpaid", "Overdue", "Pending", "Paid", "Failed"));
        statusFilter.setPrefWidth(120);
        statusFilter.setValue("All");
        statusFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        dateScopeFilter.setItems(FXCollections.observableArrayList("All Dates", "Current Month", "Current Year"));
        dateScopeFilter.setPrefWidth(120);
        dateScopeFilter.setValue("All Dates");
        dateScopeFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        sortFilter.setItems(FXCollections.observableArrayList(
            "Latest Activity",
            "Highest Outstanding",
            "Most Overdue",
            "Latest Payment"
        ));
        sortFilter.setPrefWidth(170);
        sortFilter.setValue("Latest Activity");
        sortFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        riskFilter.setItems(FXCollections.observableArrayList("All Families", "Critical", "Watch", "Clear", "Needs Review"));
        riskFilter.setPrefWidth(135);
        riskFilter.setValue("All Families");
        riskFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        reviewFilter.setItems(FXCollections.observableArrayList("All Reviews", "Review Only", "Age Review", "Overtime Review", "No Review"));
        reviewFilter.setPrefWidth(140);
        reviewFilter.setValue("All Reviews");
        reviewFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        issuePeriodField.setPromptText("yyyy-MM");
        issuePeriodField.setText(LocalDate.now().format(PERIOD_FORMAT));
        issuePeriodField.setPrefColumnCount(7);
        issuePeriodField.setPrefWidth(90);

        parentSortFilter.setItems(FXCollections.observableArrayList(
            "Highest Outstanding",
            "Most Overdue Parents",
            "Most Unpaid Invoices",
            "Most Age Reviews",
            "Most Overtime Reviews",
            "Latest Parent Payment",
            "Parent Name"
        ));
        parentSortFilter.setPrefWidth(170);
        parentSortFilter.setValue("Highest Outstanding");
        parentSortFilter.valueProperty().addListener((obs, oldValue, newValue) -> updateParentSummary(visibleRows));

        refreshButton.setOnAction(e -> reloadData());
        issueInvoicesButton.setOnAction(e -> issueInvoicesForPeriod(false));
        issueNextMonthButton.setOnAction(e -> issueNextMonthInvoices());
        issueVisibleInvoicesButton.setOnAction(e -> issueInvoicesForPeriod(true));
        quickOverdueButton.setOnAction(e -> applyQuickFilter("Overdue", "All Dates", null));
        quickUnpaidCurrentMonthButton.setOnAction(e -> applyQuickFilter("Unpaid", "Current Month", null));
        quickCurrentMonthButton.setOnAction(e -> applyQuickFilter("All", "Current Month", null));
        quickCriticalFamiliesButton.setOnAction(e -> applyQuickFilter("All", "All Dates", null, "Critical"));
        quickNeedsReviewButton.setOnAction(e -> applyQuickFilter("All", "All Dates", null, "Needs Review"));
        quickAgeReviewButton.setOnAction(e -> {
            parentSortFilter.setValue("Most Age Reviews");
            applyQuickFilter("All", "All Dates", null, "Needs Review", "Age Review");
        });
        quickOvertimeReviewButton.setOnAction(e -> {
            parentSortFilter.setValue("Most Overtime Reviews");
            applyQuickFilter("All", "All Dates", null, "Needs Review", "Overtime Review");
        });
        resetFiltersButton.setOnAction(e -> applyQuickFilter("All", "All Dates", ""));
        focusParentButton.setOnAction(e -> focusSelectedParent(false));
        parentUnpaidButton.setOnAction(e -> focusSelectedParent(true));
        latestUnpaidInvoiceButton.setOnAction(e -> openLatestUnpaidInvoiceForSelectedParent());
        clearParentFocusButton.setOnAction(e -> clearParentFocus());
        exportParentSummaryButton.setOnAction(e -> exportSelectedParentSummaryPdf(false, false));
        exportParentSummaryOpenButton.setOnAction(e -> exportSelectedParentSummaryPdf(true, false));
        printParentSummaryButton.setOnAction(e -> exportSelectedParentSummaryPdf(false, true));
        exportVisibleParentsButton.setOnAction(e -> exportVisibleParentSummariesPdf());
        exportHtmlButton.setOnAction(e -> exportSelectedLedgerRowPdf(false, false));
        exportHtmlOpenButton.setOnAction(e -> exportSelectedLedgerRowPdf(true, false));
        printHtmlButton.setOnAction(e -> exportSelectedLedgerRowPdf(false, true));
        markCashPaidButton.setOnAction(e -> markSelectedInvoicePaidByCash());
        focusParentButton.setDisable(true);
        parentUnpaidButton.setDisable(true);
        latestUnpaidInvoiceButton.setDisable(true);
        clearParentFocusButton.setDisable(true);
        exportParentSummaryButton.setDisable(true);
        exportParentSummaryOpenButton.setDisable(true);
        printParentSummaryButton.setDisable(true);
        exportVisibleParentsButton.setDisable(true);
        exportHtmlButton.setDisable(true);
        exportHtmlOpenButton.setDisable(true);
        printHtmlButton.setDisable(true);
        markCashPaidButton.setDisable(true);

        BillingPolicyUiSupport.configureActionButtons(152,
            refreshButton,
            issueInvoicesButton,
            issueNextMonthButton,
            issueVisibleInvoicesButton,
            quickOverdueButton,
            quickUnpaidCurrentMonthButton,
            quickCurrentMonthButton,
            quickCriticalFamiliesButton,
            quickNeedsReviewButton,
            quickAgeReviewButton,
            quickOvertimeReviewButton,
            resetFiltersButton,
            focusParentButton,
            parentUnpaidButton,
            latestUnpaidInvoiceButton,
            clearParentFocusButton,
            markCashPaidButton,
            exportParentSummaryButton,
            exportParentSummaryOpenButton,
            printParentSummaryButton,
            exportVisibleParentsButton,
            exportHtmlButton,
            exportHtmlOpenButton,
            printHtmlButton
        );
        applyToolbarTooltips();
        AppThemeSupport.styleControls(
            searchField,
            statusFilter,
            dateScopeFilter,
            sortFilter,
            riskFilter,
            reviewFilter,
            parentSortFilter,
            issuePeriodField,
            detailsArea,
            parentSummaryTable,
            table
        );
        detailsArea.getStyleClass().add("app-detail-area");
        statusLabel.getStyleClass().add("app-muted-text");
        periodTotalsLabel.getStyleClass().add("app-helper-text");
        parentFocusLabel.getStyleClass().add("app-muted-text");

        FlowPane filters = BillingPolicyUiSupport.createWrapRow(10, 8,
            new Label("Search:"), searchField,
            new Label("Status:"), statusFilter,
            new Label("Date Scope:"), dateScopeFilter,
            new Label("Family Risk:"), riskFilter,
            new Label("Review Type:"), reviewFilter,
            new Label("Sort:"), sortFilter,
            refreshButton
        );

        FlowPane quickFilters = BillingPolicyUiSupport.createWrapRow(10, 8,
            new Label("Quick Views:"),
            quickOverdueButton,
            quickUnpaidCurrentMonthButton,
            quickCurrentMonthButton,
            quickCriticalFamiliesButton,
            quickNeedsReviewButton,
            resetFiltersButton
        );

        FlowPane issueActions = BillingPolicyUiSupport.createWrapRow(10, 8,
            new Label("Issue Billing:"),
            new Label("Period:"), issuePeriodField,
            issueInvoicesButton,
            issueNextMonthButton,
            issueVisibleInvoicesButton
        );

        parentFocusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #4d5f55; -fx-font-weight: bold;");
        FlowPane parentActions = BillingPolicyUiSupport.createWrapRow(10, 8,
            new Label("Parent Actions:"),
            focusParentButton,
            parentUnpaidButton,
            latestUnpaidInvoiceButton,
            clearParentFocusButton,
            parentFocusLabel
        );
        VBox currentViewMeta = new VBox(4, periodTotalsLabel, statusLabel);

        configureInteractiveBadge(
            ageReviewSummaryLabel,
            "Show age-review invoices and sort parents by age-review exposure.",
            () -> {
                parentSortFilter.setValue("Most Age Reviews");
                applyQuickFilter("All", "All Dates", null, "Needs Review", "Age Review");
            }
        );
        configureInteractiveBadge(
            overtimeReviewSummaryLabel,
            "Show overtime-review invoices and sort parents by overtime-review exposure.",
            () -> {
                parentSortFilter.setValue("Most Overtime Reviews");
                applyQuickFilter("All", "All Dates", null, "Needs Review", "Overtime Review");
            }
        );

        buildParentSummaryTable();
        buildTable();

        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setPrefRowCount(18);
        detailsArea.setPromptText("Select an invoice row to view invoice items, due date, receipt, and payment details.");

        FlowPane detailActions = BillingPolicyUiSupport.createWrapRow(
            10,
            8,
            markCashPaidButton,
            exportHtmlButton,
            exportHtmlOpenButton,
            printHtmlButton
        );

        VBox detailsBox = new VBox(
            12,
            AppThemeSupport.createSectionHeader(
                "Invoice & Receipt Details",
                "Inspect invoice metadata, policy notes, payment context, and the parent billing snapshot."
            ),
            detailActions,
            detailsArea
        );
        detailsBox.getStyleClass().addAll("app-card", "app-detail-card");
        detailsBox.setMinWidth(360);
        detailsBox.setMinHeight(0);
        VBox.setVgrow(detailsArea, javafx.scene.layout.Priority.ALWAYS);

        parentSummaryTable.setMinHeight(220);
        parentSummaryTable.setPrefHeight(260);

        FlowPane parentSummaryActions = BillingPolicyUiSupport.createWrapRow(
            10,
            8,
            new Label("Sort:"),
            parentSortFilter,
            exportVisibleParentsButton,
            exportParentSummaryButton,
            exportParentSummaryOpenButton,
            printParentSummaryButton
        );

        VBox parentSummaryCard = new VBox(
            12,
            AppThemeSupport.createSectionHeader(
                "Parent Summary",
                "Review family-level outstanding balances, risk, and management-review pressure."
            ),
            parentSummaryActions,
            parentSummaryTable
        );
        parentSummaryCard.getStyleClass().addAll("app-card", "app-section-card");
        parentSummaryCard.setMinHeight(0);
        VBox.setVgrow(parentSummaryTable, javafx.scene.layout.Priority.ALWAYS);

        VBox invoiceCard = new VBox(
            12,
            AppThemeSupport.createSectionHeader(
                "Invoices",
                "Use the invoice list for daily collection, PDF exports, and cash settlement follow-up."
            ),
            table
        );
        invoiceCard.getStyleClass().addAll("app-card", "app-section-card");
        invoiceCard.setMinHeight(0);
        table.setMinHeight(260);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        FlowPane kpiStrip = new FlowPane(
            12,
            12,
            AppThemeSupport.createKpiCard("Invoices", invoiceCountLabel),
            AppThemeSupport.createKpiCard("Paid Amount", paidTotalLabel, "app-kpi-success"),
            AppThemeSupport.createKpiCard("Outstanding", outstandingTotalLabel, "app-kpi-danger"),
            AppThemeSupport.createKpiCard("Overdue", overdueCountLabel, "app-kpi-danger"),
            AppThemeSupport.createKpiCard("Age Review", ageReviewSummaryLabel, "app-kpi-warning"),
            AppThemeSupport.createKpiCard("OT Review", overtimeReviewSummaryLabel, "app-kpi-warning")
        );

        VBox filterCard = AppThemeSupport.createSectionCard(
            "Filters & Quick Views",
            "Keep search and quick follow-up views visible without stealing height from the main billing workspace.",
            filters,
            quickFilters
        );
        filterCard.getStyleClass().add("app-toolbar-card");
        filterCard.setMinWidth(0);

        VBox actionCard = AppThemeSupport.createSectionCard(
            "Billing Actions",
            "Issue invoices, focus one family, and keep the current view context nearby while the main workspace stays large.",
            issueActions,
            parentActions,
            currentViewMeta
        );
        actionCard.getStyleClass().add("app-toolbar-card");
        actionCard.setMinWidth(0);

        HBox helperRow = new HBox(12, filterCard, actionCard);
        helperRow.setFillHeight(true);
        HBox.setHgrow(filterCard, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(actionCard, javafx.scene.layout.Priority.ALWAYS);

        SplitPane leftWorkflow = new SplitPane(parentSummaryCard, invoiceCard);
        leftWorkflow.setOrientation(Orientation.VERTICAL);
        leftWorkflow.setDividerPositions(0.4);
        leftWorkflow.setMinHeight(0);

        SplitPane splitPane = new SplitPane(leftWorkflow, detailsBox);
        splitPane.setDividerPositions(0.56);
        splitPane.setMinHeight(0);

        VBox body = new VBox(12, helperRow, kpiStrip, splitPane);
        body.setPadding(new Insets(16, 18, 18, 18));
        body.setFillWidth(true);
        VBox.setVgrow(splitPane, javafx.scene.layout.Priority.ALWAYS);

        ScrollPane bodyScroll = AppThemeSupport.createPageBodyScrollWrapper(body);
        BorderPane shell = AppThemeSupport.createStandardPageShell(header, bodyScroll);

        getChildren().setAll(shell);
        VBox.setVgrow(shell, javafx.scene.layout.Priority.ALWAYS);

        reloadData();
    }

    public void onShow() {
        reloadData();
    }

    private void applyToolbarTooltips() {
        refreshButton.setTooltip(new Tooltip("Reload the billing ledger from Firestore."));
        issueInvoicesButton.setTooltip(new Tooltip("Issue invoices for the period shown beside this button."));
        issueNextMonthButton.setTooltip(new Tooltip("Set the billing period to the next month and issue invoices for that month."));
        issueVisibleInvoicesButton.setTooltip(new Tooltip("Issue invoices only for the parents currently visible in this view."));
        quickOverdueButton.setTooltip(new Tooltip("Show overdue invoices only."));
        quickUnpaidCurrentMonthButton.setTooltip(new Tooltip("Show unpaid invoices for the current month only."));
        quickCurrentMonthButton.setTooltip(new Tooltip("Filter the ledger to the current month."));
        quickCriticalFamiliesButton.setTooltip(new Tooltip("Show families with critical outstanding balances."));
        quickNeedsReviewButton.setTooltip(new Tooltip("Show invoices that need management review."));
        resetFiltersButton.setTooltip(new Tooltip("Reset all billing filters to the default view."));
        focusParentButton.setTooltip(new Tooltip("Focus the ledger on the currently selected parent."));
        parentUnpaidButton.setTooltip(new Tooltip("Show unpaid invoices for the selected parent."));
        latestUnpaidInvoiceButton.setTooltip(new Tooltip("Open the latest unpaid invoice for the selected parent."));
        clearParentFocusButton.setTooltip(new Tooltip("Clear the current parent focus and return to the full ledger."));
        exportVisibleParentsButton.setTooltip(new Tooltip("Export PDF summaries for all parents visible in the current view."));
        exportParentSummaryButton.setTooltip(new Tooltip("Export the selected parent summary to PDF."));
        exportParentSummaryOpenButton.setTooltip(new Tooltip("Export the selected parent summary to PDF and open it."));
        printParentSummaryButton.setTooltip(new Tooltip("Export the selected parent summary to PDF and print it."));
        exportHtmlButton.setTooltip(new Tooltip("Export the selected invoice details to PDF."));
        exportHtmlOpenButton.setTooltip(new Tooltip("Export the selected invoice details to PDF and open it."));
        printHtmlButton.setTooltip(new Tooltip("Export the selected invoice details to PDF and print it."));
        markCashPaidButton.setTooltip(new Tooltip("Mark the selected invoice as paid by cash."));
    }

    public void showAgeReviewView() {
        parentSortFilter.setValue("Most Age Reviews");
        BillingLedgerCommandSupport.applyQuickFilter(
            statusFilter,
            dateScopeFilter,
            riskFilter,
            reviewFilter,
            searchField,
            this::applyFilters,
            "All",
            "All Dates",
            null,
            "Needs Review",
            "Age Review"
        );
    }

    public void showOvertimeReviewView() {
        parentSortFilter.setValue("Most Overtime Reviews");
        BillingLedgerCommandSupport.applyQuickFilter(
            statusFilter,
            dateScopeFilter,
            riskFilter,
            reviewFilter,
            searchField,
            this::applyFilters,
            "All",
            "All Dates",
            null,
            "Needs Review",
            "Overtime Review"
        );
    }

    private void buildTable() {
        BillingLedgerTableSupport.setupInvoiceTable(table, visibleRows, newValue -> {
            exportHtmlButton.setDisable(newValue == null);
            exportHtmlOpenButton.setDisable(newValue == null);
            printHtmlButton.setDisable(newValue == null);
            markCashPaidButton.setDisable(newValue == null || newValue.isPaid());
            focusParentButton.setDisable(newValue == null);
            parentUnpaidButton.setDisable(newValue == null);
            latestUnpaidInvoiceButton.setDisable(newValue == null);
            showDetails(newValue);
        });
    }

    private void buildParentSummaryTable() {
        BillingLedgerTableSupport.setupParentSummaryTable(parentSummaryTable, parentSummaryRows, newValue -> {
            exportParentSummaryButton.setDisable(newValue == null);
            exportParentSummaryOpenButton.setDisable(newValue == null);
            printParentSummaryButton.setDisable(newValue == null);
            if (newValue == null) {
                return;
            }
            focusedParentId = newValue.getParentId();
            parentFocusLabel.setText("Parent Focus: " + BillingLedgerValueSupport.nullSafe(newValue.getParentName()) + " (" + BillingLedgerValueSupport.nullSafe(focusedParentId) + ")");
            clearParentFocusButton.setDisable(false);
            applyFilters();
        });
    }

    private void reloadData() {
        refreshButton.setDisable(true);
        statusLabel.setText("Loading billing ledger...");

        BillingLedgerAsyncSupport.reloadRowsAsync(
            this::loadRows,
            rows -> {
                refreshButton.setDisable(false);
                allRows = rows;
                applyFilters();
                statusLabel.setText("Loaded " + rows.size() + " invoices from " + countParents(rows) + " parent records.");
            },
            error -> {
                refreshButton.setDisable(false);
                statusLabel.setText("Billing ledger failed to load: " + BillingLedgerMessageSupport.rootMessage(error));
                detailsArea.setText("Unable to load billing ledger.\n\n" + BillingLedgerMessageSupport.rootMessage(error));
            }
        );
    }

    private void issueInvoicesForPeriod(boolean visibleOnly) {
        BillingLedgerCommandSupport.PreparedInvoiceIssue prepared;
        try {
            prepared = BillingLedgerCommandSupport.prepareInvoiceIssue(issuePeriodField.getText(), parentSummaryRows, visibleOnly);
        } catch (IllegalArgumentException ex) {
            showSimple("Invalid period", "Enter the billing period as yyyy-MM, for example 2026-03.");
            return;
        } catch (IllegalStateException ex) {
            showSimple("No visible parents", "There are no visible parent summaries to issue invoices for.");
            return;
        }

        if (!AppThemeSupport.showConfirm(
            getWindow(),
            "Issue Monthly Invoices",
            BillingLedgerCommandSupport.buildInvoiceIssueHeader(prepared),
            BillingLedgerCommandSupport.buildInvoiceIssueMessage(prepared),
            AppThemeSupport.Tone.WARNING,
            "Issue Invoices",
            "Cancel"
        )) {
            return;
        }

        refreshButton.setDisable(true);
        issueInvoicesButton.setDisable(true);
        issueVisibleInvoicesButton.setDisable(true);
        statusLabel.setText("Issuing invoices for " + prepared.period + "...");

        BillingLedgerAsyncSupport.issueInvoicesAsync(
            prepared.period,
            prepared.parentIds,
            result -> {
                refreshButton.setDisable(false);
                issueInvoicesButton.setDisable(false);
                issueVisibleInvoicesButton.setDisable(false);

                statusLabel.setText("Invoice issuance finished for " + prepared.period + ".");
                AppThemeSupport.showToast(getWindow(), "Invoice Issuance Complete", "Billing run for " + prepared.period + " finished.", AppThemeSupport.Tone.SUCCESS);
                showSimple("Invoice issuance complete", BillingLedgerMessageSupport.buildInvoiceGenerationSummary(result));
                reloadData();
            },
            error -> {
                refreshButton.setDisable(false);
                issueInvoicesButton.setDisable(false);
                issueVisibleInvoicesButton.setDisable(false);
                statusLabel.setText("Invoice issuance failed: " + BillingLedgerMessageSupport.rootMessage(error));
                showError("Failed to issue monthly invoices", new Exception(BillingLedgerMessageSupport.rootMessage(error), error));
            }
        );
    }

    private void issueNextMonthInvoices() {
        issuePeriodField.setText(nextIssuePeriodText());
        issueInvoicesForPeriod(false);
    }

    private String nextIssuePeriodText() {
        LocalDate baseDate = LocalDate.now().withDayOfMonth(1);
        String currentPeriod = BillingLedgerValueSupport.firstNonBlank(issuePeriodField.getText());
        if (currentPeriod != null && currentPeriod.matches("\\d{4}-\\d{2}")) {
            try {
                baseDate = LocalDate.parse(currentPeriod + "-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (java.time.format.DateTimeParseException ignored) {
                baseDate = LocalDate.now().withDayOfMonth(1);
            }
        }
        return baseDate.plusMonths(1).format(PERIOD_FORMAT);
    }

    private void applyQuickFilter(String status, String dateScope, String searchText) {
        applyQuickFilter(status, dateScope, searchText, "All Families", "All Reviews");
    }

    private void applyQuickFilter(String status, String dateScope, String searchText, String familyRisk) {
        applyQuickFilter(status, dateScope, searchText, familyRisk, "All Reviews");
    }

    private void applyQuickFilter(String status, String dateScope, String searchText, String familyRisk, String reviewType) {
        BillingLedgerCommandSupport.applyQuickFilter(
            statusFilter,
            dateScopeFilter,
            riskFilter,
            reviewFilter,
            searchField,
            this::applyFilters,
            status,
            dateScope,
            searchText,
            familyRisk,
            reviewType
        );
    }

    private void focusSelectedParent(boolean unpaidOnly) {
        LedgerRow row = table.getSelectionModel().getSelectedItem();
        BillingLedgerCommandSupport.FocusResult result = BillingLedgerCommandSupport.focusSelectedParent(
            row,
            unpaidOnly,
            selectedRow -> BillingLedgerInteractionSupport.buildParentFocusLabel(selectedRow, BillingLedgerValueSupport::nullSafe)
        );
        if (result == null) {
            return;
        }
        focusedParentId = result.parentId;
        parentFocusLabel.setText(result.focusLabel);
        clearParentFocusButton.setDisable(false);
        if (result.statusOverride != null) {
            statusFilter.setValue(result.statusOverride);
        }
        applyFilters();
    }

    private void clearParentFocus() {
        BillingLedgerCommandSupport.FocusResult result = BillingLedgerCommandSupport.clearParentFocus(
            BillingLedgerInteractionSupport.buildParentFocusLabel(null, BillingLedgerValueSupport::nullSafe)
        );
        focusedParentId = result.parentId;
        parentFocusLabel.setText(result.focusLabel);
        clearParentFocusButton.setDisable(result.clearFocus);
        applyFilters();
    }

    private void openLatestUnpaidInvoiceForSelectedParent() {
        LedgerRow row = table.getSelectionModel().getSelectedItem();
        BillingLedgerCommandSupport.LatestUnpaidResult result = BillingLedgerCommandSupport.latestUnpaidSelection(
            row,
            allRows,
            selectedRow -> BillingLedgerInteractionSupport.buildParentFocusLabel(selectedRow, BillingLedgerValueSupport::nullSafe)
        );
        if (result == null) {
            return;
        }
        if (!result.found) {
            showSimple("No unpaid invoice", "This parent has no unpaid invoice on record.");
            return;
        }

        focusedParentId = result.parentId;
        parentFocusLabel.setText(result.focusLabel);
        clearParentFocusButton.setDisable(false);
        statusFilter.setValue("Unpaid");
        dateScopeFilter.setValue("All Dates");
        applyFilters();
        selectInvoice(result.target.getInvoiceId());
    }

    private void selectInvoice(String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) {
            return;
        }
        for (LedgerRow row : visibleRows) {
            if (invoiceId.equals(row.getInvoiceId())) {
                table.getSelectionModel().select(row);
                table.scrollTo(row);
                return;
            }
        }
    }

    private List<LedgerRow> loadRows() {
        return BillingLedgerDataSupport.loadRows();
    }

    private void applyFilters() {
        BillingLedgerFilterSupport.FilterResult result = BillingLedgerFilterSupport.applyFilters(
            allRows,
            focusedParentId,
            BillingLedgerValueSupport.firstNonBlank(statusFilter.getValue(), "All"),
            dateScopeFilter.getValue(),
            BillingLedgerValueSupport.firstNonBlank(reviewFilter.getValue(), "All Reviews"),
            BillingLedgerValueSupport.firstNonBlank(riskFilter.getValue(), "All Families"),
            BillingLedgerValueSupport.normalize(searchField.getText()),
            BillingLedgerValueSupport.firstNonBlank(sortFilter.getValue(), "Latest Activity"),
            BillingLedgerValueSupport.firstNonBlank(parentSortFilter.getValue(), "Highest Outstanding"),
            PARENT_RISK_WATCH_OUTSTANDING_SEN,
            PARENT_RISK_CRITICAL_OUTSTANDING_SEN,
            this::formatMoney,
            this::formatDateTime
        );

        List<LedgerRow> filtered = result.filteredRows;
        List<ParentSummaryRow> visibleSummaries = result.visibleSummaries;

        visibleRows.setAll(filtered);
        parentSummaryRows.setAll(visibleSummaries);
        exportVisibleParentsButton.setDisable(visibleSummaries.isEmpty());
        updateSummary(filtered);

        if (filtered.isEmpty()) {
            detailsArea.setText("No invoices match the current search, status, or family risk filters.");
            table.getSelectionModel().clearSelection();
            return;
        }

        if (!filtered.contains(table.getSelectionModel().getSelectedItem())) {
            table.getSelectionModel().selectFirst();
        }
    }

    private void updateParentSummary(List<LedgerRow> rows) {
        List<ParentSummaryRow> visibleSummaries = BillingLedgerFilterSupport.updateParentSummary(
            rows,
            BillingLedgerValueSupport.firstNonBlank(parentSortFilter.getValue(), "Highest Outstanding"),
            BillingLedgerValueSupport.firstNonBlank(riskFilter.getValue(), "All Families"),
            PARENT_RISK_WATCH_OUTSTANDING_SEN,
            PARENT_RISK_CRITICAL_OUTSTANDING_SEN,
            this::formatMoney,
            this::formatDateTime
        );
        parentSummaryRows.setAll(visibleSummaries);
        exportVisibleParentsButton.setDisable(visibleSummaries.isEmpty());
    }

    private void updateSummary(List<LedgerRow> rows) {
        BillingLedgerSummarySupport.SummarySnapshot snapshot = BillingLedgerSummarySupport.summarizeVisibleRows(rows, this::formatMoney);
        invoiceCountLabel.setText(metricValue(snapshot.invoiceCountText));
        paidTotalLabel.setText(metricValue(snapshot.paidTotalText));
        outstandingTotalLabel.setText(metricValue(snapshot.outstandingTotalText));
        overdueCountLabel.setText(metricValue(snapshot.overdueCountText));
        ageReviewSummaryLabel.setText(metricValue(snapshot.ageReviewSummaryText));
        overtimeReviewSummaryLabel.setText(metricValue(snapshot.overtimeReviewSummaryText));
        periodTotalsLabel.setText(snapshot.periodTotalsText);
    }

    private void showDetails(LedgerRow row) {
        if (row == null) {
            detailsArea.clear();
            return;
        }

        detailsArea.setText(buildDetailsText(row));
        detailsArea.positionCaret(0);
    }

    private String buildDetailsText(LedgerRow row) {
        return BillingLedgerDetailSupport.buildDetailsText(
            row,
            allRows,
            BillingLedgerValueSupport::nullSafe,
            this::formatDate,
            this::formatDateTime,
            this::formatMoney,
            this::paymentProvider,
            this::isDummyPayment,
            this::formatProviderLabel,
            this::formatPaymentMethod,
            BillingLedgerValueSupport::firstNonBlank
        );
    }

    private void markSelectedInvoicePaidByCash() {
        LedgerRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            showSimple("No invoice selected", "Select an invoice row before recording a cash payment.");
            return;
        }
        if (row.isPaid()) {
            showSimple("Already paid", "The selected invoice is already marked as paid.");
            return;
        }

        if (!AppThemeSupport.showConfirm(
            getWindow(),
            "Record Cash Payment",
            "Mark this invoice as paid by cash?",
            BillingLedgerCommandSupport.buildCashPaymentConfirmation(row, BillingLedgerValueSupport::nullSafe),
            AppThemeSupport.Tone.INFO,
            "Mark Paid",
            "Cancel"
        )) {
            return;
        }

        markCashPaidButton.setDisable(true);
        refreshButton.setDisable(true);
        statusLabel.setText("Recording cash payment for invoice " + BillingLedgerValueSupport.nullSafe(row.getInvoiceId()) + "...");

        BillingLedgerAsyncSupport.recordCashPaymentAsync(
            row,
            () -> {
                refreshButton.setDisable(false);
                statusLabel.setText("Cash payment recorded for invoice " + BillingLedgerValueSupport.nullSafe(row.getInvoiceId()) + ".");
                AppThemeSupport.showToast(getWindow(), "Cash Payment Recorded", "Invoice " + BillingLedgerValueSupport.nullSafe(row.getInvoiceId()) + " is now marked as paid.", AppThemeSupport.Tone.SUCCESS);
                showSimple("Cash payment recorded", "The selected invoice is now marked as paid.");
                reloadData();
            },
            error -> {
                refreshButton.setDisable(false);
                statusLabel.setText("Cash payment failed: " + BillingLedgerMessageSupport.rootMessage(error));
                showError("Failed to record cash payment", new Exception(BillingLedgerMessageSupport.rootMessage(error), error));
                markCashPaidButton.setDisable(false);
            }
        );
    }

    private void exportSelectedLedgerRowPdf(boolean openAfterExport, boolean printAfterExport) {
        LedgerRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            showSimple("No invoice selected", "Select an invoice row before exporting.");
            return;
        }

        try {
            String message = BillingLedgerPdfSupport.exportLedgerRowPdfWithDialog(
                getWindow(),
                row,
                allRows,
                getClass(),
                exportBaseName(row),
                openAfterExport,
                printAfterExport,
                BillingLedgerValueSupport::nullSafe,
                this::formatDate,
                this::formatDateTime,
                this::formatMoney,
                this::paymentProvider,
                this::formatProviderLabel,
                this::formatPaymentMethod,
                this::isDummyPayment
            );

            if (message != null) {
                showSimple("Exported", message);
            }
        } catch (Exception ex) {
            showError("Failed to export billing PDF", ex);
        }
    }

    private void exportSelectedParentSummaryPdf(boolean openAfterExport, boolean printAfterExport) {
        ParentSummaryRow summary = parentSummaryTable.getSelectionModel().getSelectedItem();
        if (summary == null) {
            showSimple("No parent selected", "Select a parent summary row before exporting.");
            return;
        }

        List<LedgerRow> parentRows = BillingLedgerDetailSupport.rowsForParent(summary.getParentId(), visibleRows);
        if (parentRows.isEmpty()) {
            showSimple("No parent invoices", "The selected parent has no invoices in the current view.");
            return;
        }

        try {
            String message = BillingLedgerPdfSupport.exportParentSummaryPdfWithDialog(
                getWindow(),
                summary,
                parentRows,
                getClass(),
                buildCurrentFilterDescription(),
                parentSummaryExportBaseName(summary),
                openAfterExport,
                printAfterExport,
                this::formatMoney,
                BillingLedgerValueSupport::nullSafe,
                this::formatDate
            );
            if (message != null) {
                showSimple("Exported", message);
            }
        } catch (Exception ex) {
            showError("Failed to export parent billing PDF", ex);
        }
    }

    private void exportVisibleParentSummariesPdf() {
        if (parentSummaryRows.isEmpty()) {
            showSimple("No visible parents", "There are no visible parent summaries to export under the current filters.");
            return;
        }

        try {
            BillingLedgerPdfSupport.BatchParentExportResult result = BillingLedgerPdfSupport.exportVisibleParentSummariesPdfWithDialog(
                getWindow(),
                new ArrayList<>(parentSummaryRows),
                new ArrayList<>(visibleRows),
                getClass(),
                buildCurrentFilterDescription(),
                this::formatMoney,
                BillingLedgerValueSupport::nullSafe,
                this::formatDate,
                this::parentSummaryExportBaseName
            );
            if (result != null) {
                showSimple(
                    "Batch export complete",
                    "Exported " + result.exportedCount + " visible parent summaries to:\n" + result.exportDirectory.getAbsolutePath()
                );
            }
        } catch (Exception ex) {
            showError("Failed to export visible parent summaries PDF", ex);
        }
    }

    private String exportBaseName(LedgerRow row) {
        String receipt = "-".equals(row.getReceiptNo()) ? "unpaid" : BillingLedgerValueSupport.slug(row.getReceiptNo());
        return "billing-" + BillingLedgerValueSupport.slug(row.getInvoiceId()) + "-" + receipt + "-"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
    }

    private String buildCurrentFilterDescription() {
        return BillingLedgerFilterSupport.buildCurrentFilterDescription(
            statusFilter.getValue(),
            dateScopeFilter.getValue(),
            riskFilter.getValue(),
            reviewFilter.getValue(),
            searchField.getText(),
            focusedParentId
        );
    }

    private int countParents(List<LedgerRow> rows) {
        return (int) rows.stream().map(LedgerRow::getParentId).filter(Objects::nonNull).distinct().count();
    }

    private String paymentProvider(LedgerRow row) {
        if (row == null) {
            return null;
        }
        return BillingLedgerValueSupport.firstNonBlank(row.payment == null ? null : row.payment.getString("provider"), row.invoice.getString("paidProvider"));
    }

    private boolean isDummyPayment(LedgerRow row) {
        String provider = paymentProvider(row);
        return provider != null && "dummy".equalsIgnoreCase(provider);
    }

    private String formatProviderLabel(String provider) {
        String value = BillingLedgerValueSupport.firstNonBlank(provider);
        if (value == null) {
            return null;
        }
        if ("dummy".equalsIgnoreCase(value)) {
            return "In-App Demo Payment";
        }
        if ("stripe".equalsIgnoreCase(value)) {
            return "Stripe Test Mode";
        }
        return value;
    }

    private String formatPaymentMethod(String method) {
        String value = BillingLedgerValueSupport.firstNonBlank(method);
        if (value == null) {
            return null;
        }
        return value;
    }

    private void configureInteractiveBadge(Label label, String tooltipText, Runnable action) {
        if (!label.getStyleClass().contains("app-chip-info")) {
            label.getStyleClass().add("app-chip-info");
        }
        label.setStyle("-fx-cursor: hand;");
        label.setTooltip(new Tooltip(tooltipText));
        label.setOnMouseClicked(event -> action.run());
    }

    private String metricValue(String text) {
        if (text == null) {
            return "-";
        }
        int separator = text.indexOf(':');
        return separator >= 0 && separator + 1 < text.length() ? text.substring(separator + 1).trim() : text.trim();
    }

    private String formatMoney(long sen) {
        return MONEY_FORMAT.format(sen / 100.0);
    }

    private String formatDate(Date date) {
        if (date == null) {
            return "-";
        }
        return DATE_FORMAT.format(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }

    private String formatDateTime(Date date) {
        if (date == null) {
            return "-";
        }
        return DATE_TIME_FORMAT.format(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    private String parentSummaryExportBaseName(ParentSummaryRow summary) {
        return "parent-summary-" + BillingLedgerValueSupport.slug(summary.getParentName()) + "-" + BillingLedgerValueSupport.slug(summary.getParentId()) + "-"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
    }

    private Window getWindow() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private void showSimple(String title, String message) {
        AppThemeSupport.showInfo(getWindow(), title, message);
    }

    private void showError(String context, Exception ex) {
        AppThemeSupport.showError(getWindow(), context, BillingLedgerMessageSupport.rootMessage(ex));
    }

    public static final class LedgerRow {
        private final String parentId;
        private final String invoiceId;
        private final String parentName;
        private final String childName;
        private final List<String> childNames;
        private final String period;
        private final String status;
        private final String dueDateText;
        private final String totalText;
        private final boolean managementReviewRecommended;
        private final boolean ageOutOfPolicy;
        private final String reviewReason;
        private final String receiptNo;
        private final long totalSen;
        private final Date dueDate;
        private final Date createdAt;
        private final Date paidAt;
        private final FsDocument invoice;
        private final FsDocument payment;

        private LedgerRow(
            String parentId,
            String invoiceId,
            String parentName,
            String childName,
            List<String> childNames,
            String period,
            String status,
            String dueDateText,
            String totalText,
            boolean managementReviewRecommended,
            boolean ageOutOfPolicy,
            String reviewReason,
            String receiptNo,
            long totalSen,
            Date dueDate,
            Date createdAt,
            Date paidAt,
            FsDocument invoice,
            FsDocument payment
        ) {
            this.parentId = parentId;
            this.invoiceId = invoiceId;
            this.parentName = parentName;
            this.childName = childName;
            this.childNames = childNames;
            this.period = period;
            this.status = status;
            this.dueDateText = dueDateText;
            this.totalText = totalText;
            this.managementReviewRecommended = managementReviewRecommended;
            this.ageOutOfPolicy = ageOutOfPolicy;
            this.reviewReason = reviewReason;
            this.receiptNo = receiptNo;
            this.totalSen = totalSen;
            this.dueDate = dueDate;
            this.createdAt = createdAt;
            this.paidAt = paidAt;
            this.invoice = invoice;
            this.payment = payment;
        }

        public static LedgerRow from(String parentId, String parentName, FsDocument invoice, FsDocument payment) {
            Date dueDate = invoice.getDate("dueDate");
            Date createdAt = BillingLedgerRowSupport.firstDate(invoice.getDate("createdAt"), invoice.getDate("updatedAt"), dueDate);
            Date paidAt = BillingLedgerRowSupport.firstDate(
                invoice.getDate("paidAt"),
                payment == null ? null : payment.getDate("paidAt"),
                payment == null ? null : payment.getDate("completedAt"),
                payment == null ? null : payment.getDate("createdAt")
            );
            long totalSen = BillingLedgerValueSupport.longValueOrZero(invoice.getLong("totalSen"));
            String status = BillingLedgerRowSupport.resolveStatus(invoice.getString("status"), payment == null ? null : payment.getString("status"), dueDate);
            String receiptNo = BillingLedgerRowSupport.firstNonBlank(
                invoice.getString("paidReceiptNo"),
                payment == null ? null : payment.getString("receiptNo"),
                payment == null ? null : payment.getString("providerReceiptNo"),
                "-"
            );
            boolean managementReviewRecommended = BillingLedgerRowSupport.invoiceManagementReviewRecommended(invoice);
            boolean ageOutOfPolicy = BillingLedgerRowSupport.invoiceAgeOutOfPolicy(invoice);
            String reviewReason = BillingLedgerRowSupport.invoiceReviewReason(invoice);
            String period = BillingLedgerRowSupport.firstNonBlank(invoice.getString("period"), "-");
            List<String> childNames = BillingLedgerRowSupport.extractChildNames(invoice);
            String childName = childNames.isEmpty()
                ? BillingLedgerRowSupport.firstNonBlank(invoice.getString("childName"), invoice.getString("childId"), "-")
                : String.join(", ", childNames);

            return new LedgerRow(
                parentId,
                invoice.getId(),
                BillingLedgerRowSupport.firstNonBlank(parentName, "-"),
                childName,
                childNames,
                period,
                status,
                BillingLedgerRowSupport.formatDate(dueDate),
                BillingLedgerRowSupport.formatMoney(totalSen),
                managementReviewRecommended,
                ageOutOfPolicy,
                reviewReason,
                receiptNo,
                totalSen,
                dueDate,
                createdAt,
                paidAt,
                invoice,
                payment
            );
        }

        public String getParentId() {
            return parentId;
        }

        public String getInvoiceId() {
            return invoiceId;
        }

        public String getParentName() {
            return parentName;
        }

        public String getChildName() {
            return childName;
        }

        public String getChildDisplayName() {
            return childName;
        }

        public List<String> getChildNames() {
            return childNames;
        }

        public String getPeriod() {
            return period;
        }

        public String getStatus() {
            return status;
        }

        public String getDueDateText() {
            return dueDateText;
        }

        public String getTotalText() {
            return totalText;
        }

        public boolean isManagementReviewRecommended() {
            return managementReviewRecommended;
        }

        public String getReviewFlag() {
            if (!managementReviewRecommended) {
                return "";
            }
            return ageOutOfPolicy ? "Age Review" : "Review";
        }

        public String getReviewReason() {
            return reviewReason;
        }

        public boolean isAgeOutOfPolicy() {
            return ageOutOfPolicy;
        }

        public String getReceiptNo() {
            return receiptNo;
        }

        public String getChildCoverageKey() {
            return BillingLedgerRowSupport.firstNonBlank(invoice.getString("childCoverageKey"));
        }

        public String getPaidPaymentId() {
            return BillingLedgerRowSupport.firstNonBlank(
                invoice.getString("paidPaymentId"),
                payment == null ? null : payment.getId(),
                payment == null ? null : payment.getString("paymentId")
            );
        }

        public long getTotalSen() {
            return totalSen;
        }

        public Date getDueDate() {
            return dueDate;
        }

        public Date getCreatedAt() {
            return createdAt;
        }

        public Date getPaidAt() {
            return paidAt;
        }

        @SuppressWarnings("unused")
        FsDocument getInvoiceDocument() {
            return invoice;
        }

        @SuppressWarnings("unused")
        FsDocument getPaymentDocument() {
            return payment;
        }

        public Date getSortDate() {
            return BillingLedgerRowSupport.firstDate(dueDate, createdAt, paidAt);
        }

        public LocalDate getRelevantDate() {
            if (period != null && period.matches("\\d{4}-\\d{2}")) {
                try {
                    return LocalDate.parse(period + "-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                } catch (java.time.format.DateTimeParseException ignored) {
                    // Fall back to stored timestamps if the billing period text is malformed.
                }
            }
            Date value = BillingLedgerRowSupport.firstDate(dueDate, createdAt, paidAt);
            if (value == null) {
                return null;
            }
            return value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }

        public boolean isPaid() {
            return "Paid".equalsIgnoreCase(status);
        }

        public boolean isOverdue() {
            return "Overdue".equalsIgnoreCase(status);
        }

        public long getOverdueDays() {
            if (!isOverdue() || dueDate == null) {
                return 0L;
            }
            LocalDate due = dueDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate today = LocalDate.now();
            long days = java.time.temporal.ChronoUnit.DAYS.between(due, today);
            return Math.max(days, 0L);
        }

        public boolean matches(String query) {
            String haystack = String.join(" ",
                safe(invoiceId),
                safe(parentName),
                safe(childName),
                safe(String.join(" ", childNames)),
                safe(period),
                safe(status),
                managementReviewRecommended ? "review" : "",
                ageOutOfPolicy ? "age review" : "",
                safe(reviewReason),
                safe(receiptNo)
            ).toLowerCase(Locale.ROOT);
            return haystack.contains(query);
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }

    public static final class ParentSummaryRow {
        private final String parentId;
        private final String parentName;
        private final int invoiceCount;
        private final int unpaidCount;
        private final int overdueCount;
        private final String riskLevel;
        private final long outstandingSen;
        private final String outstandingText;
        private final int reviewCount;
        private final int ageReviewCount;
        private final int overtimeReviewCount;
        private final String reviewStatus;
        private final String reviewSummary;
        private final String lastPaidText;
        private final Date lastPaidAt;

        @SuppressWarnings("unused")
        ParentSummaryRow(
            String parentId,
            String parentName,
            int invoiceCount,
            int unpaidCount,
            int overdueCount,
            String riskLevel,
            long outstandingSen,
            String outstandingText,
            int reviewCount,
            int ageReviewCount,
            int overtimeReviewCount,
            String reviewStatus,
            String reviewSummary,
            String lastPaidText,
            Date lastPaidAt
        ) {
            this.parentId = parentId;
            this.parentName = parentName;
            this.invoiceCount = invoiceCount;
            this.unpaidCount = unpaidCount;
            this.overdueCount = overdueCount;
            this.riskLevel = riskLevel;
            this.outstandingSen = outstandingSen;
            this.outstandingText = outstandingText;
            this.reviewCount = reviewCount;
            this.ageReviewCount = ageReviewCount;
            this.overtimeReviewCount = overtimeReviewCount;
            this.reviewStatus = reviewStatus;
            this.reviewSummary = reviewSummary;
            this.lastPaidText = lastPaidText;
            this.lastPaidAt = lastPaidAt;
        }

        public String getParentId() {
            return parentId;
        }

        public String getParentName() {
            return parentName;
        }

        public int getInvoiceCount() {
            return invoiceCount;
        }

        public int getUnpaidCount() {
            return unpaidCount;
        }

        public int getOverdueCount() {
            return overdueCount;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public long getOutstandingSen() {
            return outstandingSen;
        }

        public String getOutstandingText() {
            return outstandingText;
        }

        public int getReviewCount() {
            return reviewCount;
        }

        public int getAgeReviewCount() {
            return ageReviewCount;
        }

        public int getOvertimeReviewCount() {
            return overtimeReviewCount;
        }

        public String getReviewStatus() {
            return reviewStatus;
        }

        public String getReviewSummary() {
            return reviewSummary;
        }

        public String getLastPaidText() {
            return lastPaidText;
        }

        public Date getLastPaidAt() {
            return lastPaidAt;
        }
    }

}