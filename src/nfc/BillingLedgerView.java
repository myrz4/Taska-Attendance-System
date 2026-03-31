package nfc;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

public class BillingLedgerView extends VBox {
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();
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
    private final Button issueInvoicesButton = new Button("Issue Month Invoices");
    private final Button issueVisibleInvoicesButton = new Button("Issue Visible Parents");
    private final Button quickOverdueButton = new Button("Only Overdue");
    private final Button quickUnpaidCurrentMonthButton = new Button("Unpaid This Month");
    private final Button quickCurrentMonthButton = new Button("Current Month");
    private final Button quickCriticalFamiliesButton = new Button("Critical Families");
    private final Button quickNeedsReviewButton = new Button("Needs Review");
    private final Button quickAgeReviewButton = new Button("Age Review");
    private final Button quickOvertimeReviewButton = new Button("Overtime Review");
    private final Button resetFiltersButton = new Button("Reset Filters");
    private final Button focusParentButton = new Button("Focus Parent");
    private final Button parentUnpaidButton = new Button("Parent Unpaid");
    private final Button latestUnpaidInvoiceButton = new Button("Latest Unpaid Invoice");
    private final Button clearParentFocusButton = new Button("Clear Parent Focus");
    private final Button exportParentSummaryButton = new Button("Export PDF");
    private final Button exportParentSummaryOpenButton = new Button("Export PDF + Open");
    private final Button printParentSummaryButton = new Button("Export PDF + Print");
    private final Button exportVisibleParentsButton = new Button("Batch Export Visible PDFs");
    private final Button exportHtmlButton = new Button("Export PDF");
    private final Button exportHtmlOpenButton = new Button("Export PDF + Open");
    private final Button printHtmlButton = new Button("Export PDF + Print");
    private final Button markCashPaidButton = new Button("Mark Paid (Cash)");
    private final Label statusLabel = new Label("Loading billing ledger...");
    private final Label invoiceCountLabel = new Label("Invoices: 0");
    private final Label paidTotalLabel = new Label("Paid: RM0.00");
    private final Label outstandingTotalLabel = new Label("Outstanding: RM0.00");
    private final Label overdueCountLabel = new Label("Overdue: 0");
    private final Label ageReviewSummaryLabel = new Label("Age Review: 0 fam / 0 inv");
    private final Label overtimeReviewSummaryLabel = new Label("OT Review: 0 fam / 0 inv");
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
        setSpacing(12);
        setPadding(new Insets(12));

        Label title = new Label("Billing Ledger");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #1d2f24;");

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

        configureActionButtons(
            refreshButton,
            issueInvoicesButton,
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

        FlowPane filters = createWrapRow(
            new Label("Search:"), searchField,
            new Label("Status:"), statusFilter,
            new Label("Date Scope:"), dateScopeFilter,
            new Label("Family Risk:"), riskFilter,
            new Label("Review Type:"), reviewFilter,
            new Label("Sort:"), sortFilter,
            refreshButton
        );

        FlowPane quickFilters = createWrapRow(
            new Label("Quick Views:"),
            quickOverdueButton,
            quickUnpaidCurrentMonthButton,
            quickCurrentMonthButton,
            quickCriticalFamiliesButton,
            quickNeedsReviewButton,
            quickAgeReviewButton,
            quickOvertimeReviewButton,
            resetFiltersButton
        );

        FlowPane issueActions = createWrapRow(
            new Label("Issue Billing:"),
            new Label("Period:"), issuePeriodField,
            issueInvoicesButton,
            issueVisibleInvoicesButton
        );

        parentFocusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #4d5f55; -fx-font-weight: bold;");
        FlowPane parentActions = createWrapRow(
            new Label("Parent Actions:"),
            focusParentButton,
            parentUnpaidButton,
            latestUnpaidInvoiceButton,
            clearParentFocusButton,
            parentFocusLabel
        );

        styleBadge(invoiceCountLabel, "#fff4b4", "#5b4b08");
        styleBadge(paidTotalLabel, "#dff5dd", "#1f5d26");
        styleBadge(outstandingTotalLabel, "#ffe1dc", "#7d2218");
        styleBadge(overdueCountLabel, "#ffd9d4", "#8c1d13");
        styleBadge(ageReviewSummaryLabel, "#ffe8d2", "#8a4b00");
        styleBadge(overtimeReviewSummaryLabel, "#fff0bf", "#7a5200");
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
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #476150;");
        periodTotalsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #345041; -fx-font-weight: bold;");

        Region summarySpacer = new Region();
        HBox.setHgrow(summarySpacer, javafx.scene.layout.Priority.ALWAYS);
        HBox summaryRow = new HBox(10,
            invoiceCountLabel,
            paidTotalLabel,
            outstandingTotalLabel,
            overdueCountLabel,
            ageReviewSummaryLabel,
            overtimeReviewSummaryLabel,
            summarySpacer,
            statusLabel
        );
        summaryRow.setAlignment(Pos.CENTER_LEFT);

        VBox summaryBox = new VBox(6, summaryRow, periodTotalsLabel);

        buildParentSummaryTable();
        buildTable();

        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setPrefRowCount(18);
        detailsArea.setPromptText("Select an invoice row to view invoice items, due date, receipt, and payment details.");

        Label detailsTitle = new Label("Invoice and Receipt Details");
        detailsTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        FlowPane detailsHeader = createWrapRow(detailsTitle, markCashPaidButton, exportHtmlButton, exportHtmlOpenButton, printHtmlButton);

        VBox detailsBox = new VBox(8, detailsHeader, detailsArea);
        detailsBox.setPadding(new Insets(8, 0, 0, 0));
        detailsBox.setMinWidth(360);
        VBox.setVgrow(detailsArea, javafx.scene.layout.Priority.ALWAYS);

        Label parentSummaryTitle = new Label("Parent Summary");
        parentSummaryTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        parentSummaryTable.setPrefHeight(190);

        FlowPane parentSummaryHeader = createWrapRow(
            parentSummaryTitle,
            new Label("Sort Parents:"),
            parentSortFilter,
            exportVisibleParentsButton,
            exportParentSummaryButton,
            exportParentSummaryOpenButton,
            printParentSummaryButton
        );

        Label invoiceTableTitle = new Label("Invoices");
        invoiceTableTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        VBox leftPane = new VBox(10, parentSummaryHeader, parentSummaryTable, invoiceTableTitle, table);
        leftPane.setMinWidth(0);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(leftPane, detailsBox);
        splitPane.setDividerPositions(0.62);
        VBox.setVgrow(splitPane, javafx.scene.layout.Priority.ALWAYS);

        getChildren().addAll(title, filters, quickFilters, issueActions, parentActions, summaryBox, splitPane);

        reloadData();
    }

    public void showAgeReviewView() {
        parentSortFilter.setValue("Most Age Reviews");
        applyQuickFilter("All", "All Dates", null, "Needs Review", "Age Review");
    }

    public void showOvertimeReviewView() {
        parentSortFilter.setValue("Most Overtime Reviews");
        applyQuickFilter("All", "All Dates", null, "Needs Review", "Overtime Review");
    }

    private FlowPane createWrapRow(Node... nodes) {
        FlowPane row = new FlowPane();
        row.setHgap(10);
        row.setVgap(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(nodes);
        return row;
    }

    private void configureActionButtons(Button... buttons) {
        for (Button button : buttons) {
            button.setWrapText(true);
            button.setMinHeight(32);
            button.setMaxWidth(170);
        }
    }

    private void buildTable() {
        table.setItems(visibleRows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(50);

        TableColumn<LedgerRow, String> invoiceCol = new TableColumn<>("Invoice ID");
        invoiceCol.setCellValueFactory(new PropertyValueFactory<>("invoiceId"));
        invoiceCol.setPrefWidth(120);

        TableColumn<LedgerRow, String> parentCol = new TableColumn<>("Parent");
        parentCol.setCellValueFactory(new PropertyValueFactory<>("parentName"));
        parentCol.setPrefWidth(140);

        TableColumn<LedgerRow, String> childCol = new TableColumn<>("Student(s)");
        childCol.setCellValueFactory(new PropertyValueFactory<>("childName"));
        childCol.setPrefWidth(150);

        TableColumn<LedgerRow, String> periodCol = new TableColumn<>("Period");
        periodCol.setCellValueFactory(new PropertyValueFactory<>("period"));
        periodCol.setPrefWidth(95);

        TableColumn<LedgerRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(90);

        TableColumn<LedgerRow, String> dueCol = new TableColumn<>("Due Date");
        dueCol.setCellValueFactory(new PropertyValueFactory<>("dueDateText"));
        dueCol.setPrefWidth(105);

        TableColumn<LedgerRow, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(new PropertyValueFactory<>("totalText"));
        totalCol.setPrefWidth(105);

        TableColumn<LedgerRow, String> reviewCol = new TableColumn<>("Review");
        reviewCol.setCellValueFactory(new PropertyValueFactory<>("reviewFlag"));
        reviewCol.setPrefWidth(105);
        reviewCol.setCellFactory(column -> new TableCell<LedgerRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    setTooltip(null);
                    setStyle("");
                    return;
                }
                LedgerRow row = getTableRow() == null ? null : getTableRow().getItem();
                setText(item);
                String reviewReason = row == null ? null : row.getReviewReason();
                setTooltip(reviewReason == null || reviewReason.isBlank() ? null : new Tooltip(reviewReason));
                setStyle("-fx-background-color: rgba(255, 228, 181, 0.95); -fx-text-fill: #8a4b00; -fx-font-weight: bold; -fx-alignment: CENTER;");
            }
        });

        TableColumn<LedgerRow, String> reviewReasonCol = new TableColumn<>("Reason");
        reviewReasonCol.setCellValueFactory(new PropertyValueFactory<>("reviewReason"));
        reviewReasonCol.setPrefWidth(220);
        reviewReasonCol.setCellFactory(column -> new TableCell<LedgerRow, String>() {
            private final Label content = new Label();

            {
                content.setWrapText(true);
                content.setMaxWidth(210);
                content.setStyle("-fx-font-size: 11px; -fx-text-fill: #6d4b21;");
                setGraphic(content);
                setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
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
                LedgerRow row = getTableRow() == null ? null : getTableRow().getItem();
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

        TableColumn<LedgerRow, String> receiptCol = new TableColumn<>("Receipt");
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
        table.setRowFactory(ignored -> new TableRow<LedgerRow>() {
            @Override
            protected void updateItem(LedgerRow item, boolean empty) {
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
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
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
        parentSummaryTable.setItems(parentSummaryRows);
        parentSummaryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ParentSummaryRow, String> parentCol = new TableColumn<>("Parent");
        parentCol.setCellValueFactory(new PropertyValueFactory<>("parentName"));

        TableColumn<ParentSummaryRow, Number> invoiceCountCol = new TableColumn<>("Invoices");
        invoiceCountCol.setCellValueFactory(new PropertyValueFactory<>("invoiceCount"));

        TableColumn<ParentSummaryRow, Number> unpaidCountCol = new TableColumn<>("Unpaid");
        unpaidCountCol.setCellValueFactory(new PropertyValueFactory<>("unpaidCount"));

        TableColumn<ParentSummaryRow, Number> overdueCountCol = new TableColumn<>("Overdue");
        overdueCountCol.setCellValueFactory(new PropertyValueFactory<>("overdueCount"));

        TableColumn<ParentSummaryRow, String> riskCol = new TableColumn<>("Risk");
        riskCol.setCellValueFactory(new PropertyValueFactory<>("riskLevel"));
        riskCol.setCellFactory(column -> new TableCell<ParentSummaryRow, String>() {
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

        TableColumn<ParentSummaryRow, String> outstandingCol = new TableColumn<>("Outstanding");
        outstandingCol.setCellValueFactory(new PropertyValueFactory<>("outstandingText"));

        TableColumn<ParentSummaryRow, Number> ageReviewCol = new TableColumn<>("Age Rev");
        ageReviewCol.setCellValueFactory(new PropertyValueFactory<>("ageReviewCount"));

        TableColumn<ParentSummaryRow, Number> overtimeReviewCol = new TableColumn<>("OT Rev");
        overtimeReviewCol.setCellValueFactory(new PropertyValueFactory<>("overtimeReviewCount"));

        TableColumn<ParentSummaryRow, String> reviewCol = new TableColumn<>("Review");
        reviewCol.setCellValueFactory(new PropertyValueFactory<>("reviewStatus"));
        reviewCol.setCellFactory(column -> new TableCell<ParentSummaryRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    setTooltip(null);
                    setStyle("");
                    return;
                }
                ParentSummaryRow row = getTableRow() == null ? null : getTableRow().getItem();
                setText(item);
                String reviewSummary = row == null ? null : row.getReviewSummary();
                setTooltip(reviewSummary == null || reviewSummary.isBlank() ? null : new Tooltip(reviewSummary));
                setStyle("-fx-background-color: rgba(255, 228, 181, 0.95); -fx-text-fill: #8a4b00; -fx-font-weight: bold; -fx-alignment: CENTER;");
            }
        });

        TableColumn<ParentSummaryRow, String> lastPaidCol = new TableColumn<>("Last Payment");
        lastPaidCol.setCellValueFactory(new PropertyValueFactory<>("lastPaidText"));

        parentSummaryTable.getColumns().clear();
        parentSummaryTable.getColumns().add(parentCol);
        parentSummaryTable.getColumns().add(invoiceCountCol);
        parentSummaryTable.getColumns().add(unpaidCountCol);
        parentSummaryTable.getColumns().add(overdueCountCol);
        parentSummaryTable.getColumns().add(riskCol);
        parentSummaryTable.getColumns().add(outstandingCol);
        parentSummaryTable.getColumns().add(ageReviewCol);
        parentSummaryTable.getColumns().add(overtimeReviewCol);
        parentSummaryTable.getColumns().add(reviewCol);
        parentSummaryTable.getColumns().add(lastPaidCol);
        parentSummaryTable.setRowFactory(ignored -> new TableRow<ParentSummaryRow>() {
            @Override
            protected void updateItem(ParentSummaryRow item, boolean empty) {
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
        parentSummaryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            exportParentSummaryButton.setDisable(newValue == null);
            exportParentSummaryOpenButton.setDisable(newValue == null);
            printParentSummaryButton.setDisable(newValue == null);
            if (newValue == null) {
                return;
            }
            focusedParentId = newValue.getParentId();
            parentFocusLabel.setText("Parent Focus: " + nullSafe(newValue.getParentName()) + " (" + nullSafe(focusedParentId) + ")");
            clearParentFocusButton.setDisable(false);
            applyFilters();
        });
    }

    private void reloadData() {
        refreshButton.setDisable(true);
        statusLabel.setText("Loading billing ledger...");

        CompletableFuture
            .supplyAsync(this::loadRows)
            .whenComplete((rows, error) -> Platform.runLater(() -> {
                refreshButton.setDisable(false);
                if (error != null) {
                    statusLabel.setText("Billing ledger failed to load: " + rootMessage(error));
                    detailsArea.setText("Unable to load billing ledger.\n\n" + rootMessage(error));
                    return;
                }

                allRows = rows;
                applyFilters();
                statusLabel.setText("Loaded " + rows.size() + " invoices from " + countParents(rows) + " parent records.");
            }));
    }

    private void issueInvoicesForPeriod(boolean visibleOnly) {
        String period = issuePeriodField.getText() == null ? "" : issuePeriodField.getText().trim();
        if (!period.matches("\\d{4}-\\d{2}")) {
            showSimple("Invalid period", "Enter the billing period as yyyy-MM, for example 2026-03.");
            return;
        }

        List<String> parentIds = new ArrayList<>();
        if (visibleOnly) {
            for (ParentSummaryRow row : parentSummaryRows) {
                String parentId = row == null ? null : row.getParentId();
                if (parentId != null && !parentId.isBlank() && !parentIds.contains(parentId)) {
                    parentIds.add(parentId);
                }
            }
            if (parentIds.isEmpty()) {
                showSimple("No visible parents", "There are no visible parent summaries to issue invoices for.");
                return;
            }
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Issue Monthly Invoices");
        confirm.setHeaderText(visibleOnly ? "Issue invoices for visible parents?" : "Issue invoices for all parents?");
        confirm.setContentText((visibleOnly
            ? ("Period " + period + " for " + parentIds.size() + " visible parent(s).")
            : ("Period " + period + " for every parent record."))
            + "\n\nExisting invoices for the period will be skipped.");
        confirm.initOwner(getWindow());
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        refreshButton.setDisable(true);
        issueInvoicesButton.setDisable(true);
        issueVisibleInvoicesButton.setDisable(true);
        statusLabel.setText("Issuing invoices for " + period + "...");

        CompletableFuture
            .supplyAsync(() -> invokeInvoiceGeneration(period, parentIds))
            .whenComplete((result, error) -> Platform.runLater(() -> {
                refreshButton.setDisable(false);
                issueInvoicesButton.setDisable(false);
                issueVisibleInvoicesButton.setDisable(false);

                if (error != null) {
                    statusLabel.setText("Invoice issuance failed: " + rootMessage(error));
                    showError("Failed to issue monthly invoices", new Exception(rootMessage(error), error));
                    return;
                }

                statusLabel.setText("Invoice issuance finished for " + period + ".");
                showSimple("Invoice issuance complete", buildInvoiceGenerationSummary(result));
                reloadData();
            }));
    }

    private Map<?, ?> invokeInvoiceGeneration(String period, List<String> parentIds) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("period", period);
            if (parentIds != null && !parentIds.isEmpty()) {
                payload.put("parentIds", parentIds);
            }

            String projectId = FirestoreRest.projectId();
            String idToken = UserSession.getIdToken();
            FirebaseFunctionsClient.CallResult res = FirebaseFunctionsClient.callBillingAdminGenerateInvoicesForPeriod(
                projectId,
                idToken,
                GSON_PRETTY.toJson(payload)
            );
            Map<?, ?> result = parseCallableResultMap(res.rawBody);
            Object ok = result.get("ok");
            if (!(ok instanceof Boolean) || !((Boolean) ok)) {
                throw new IllegalStateException(firstNonBlank(stringValue(result.get("reason")), res.reason, "billing-admin-generate-failed"));
            }
            return result;
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String buildInvoiceGenerationSummary(Map<?, ?> result) {
        long created = longValueOrZero(result.get("createdCount"));
        long existing = longValueOrZero(result.get("existingCount"));
        long noChildren = longValueOrZero(result.get("skippedNoChildrenCount"));
        long noItems = longValueOrZero(result.get("skippedNoItemsCount"));
        long errors = longValueOrZero(result.get("errorCount"));

        StringBuilder sb = new StringBuilder();
        sb.append("Period: ").append(nullSafe(stringValue(result.get("period")))).append("\n");
        sb.append("Created invoices: ").append(created).append("\n");
        sb.append("Already existed: ").append(existing).append("\n");
        sb.append("Skipped (no linked children): ").append(noChildren).append("\n");
        sb.append("Skipped (no billable items): ").append(noItems).append("\n");
        sb.append("Errors: ").append(errors);
        return sb.toString();
    }

    private void applyQuickFilter(String status, String dateScope, String searchText) {
        applyQuickFilter(status, dateScope, searchText, "All Families", "All Reviews");
    }

    private void applyQuickFilter(String status, String dateScope, String searchText, String familyRisk) {
        applyQuickFilter(status, dateScope, searchText, familyRisk, "All Reviews");
    }

    private void applyQuickFilter(String status, String dateScope, String searchText, String familyRisk, String reviewType) {
        statusFilter.setValue(status);
        dateScopeFilter.setValue(dateScope);
        riskFilter.setValue(familyRisk);
        reviewFilter.setValue(reviewType);
        if (searchText != null) {
            searchField.setText(searchText);
        }
        applyFilters();
    }

    private void focusSelectedParent(boolean unpaidOnly) {
        LedgerRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            return;
        }
        focusedParentId = row.getParentId();
        parentFocusLabel.setText("Parent Focus: " + nullSafe(row.getParentName()) + " (" + nullSafe(focusedParentId) + ")");
        clearParentFocusButton.setDisable(false);
        if (unpaidOnly) {
            statusFilter.setValue("Unpaid");
        }
        applyFilters();
    }

    private void clearParentFocus() {
        focusedParentId = null;
        parentFocusLabel.setText("Parent Focus: none");
        clearParentFocusButton.setDisable(true);
        applyFilters();
    }

    private void openLatestUnpaidInvoiceForSelectedParent() {
        LedgerRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            return;
        }

        LedgerRow target = null;
        for (LedgerRow candidate : allRows) {
            if (!Objects.equals(row.getParentId(), candidate.getParentId())) {
                continue;
            }
            if (candidate.isPaid()) {
                continue;
            }
            if (target == null || compareInvoicePriority(candidate, target) < 0) {
                target = candidate;
            }
        }

        if (target == null) {
            showSimple("No unpaid invoice", "This parent has no unpaid invoice on record.");
            return;
        }

        focusedParentId = target.getParentId();
        parentFocusLabel.setText("Parent Focus: " + nullSafe(target.getParentName()) + " (" + nullSafe(focusedParentId) + ")");
        clearParentFocusButton.setDisable(false);
        statusFilter.setValue("Unpaid");
        dateScopeFilter.setValue("All Dates");
        applyFilters();
        selectInvoice(target.getInvoiceId());
    }

    private int compareInvoicePriority(LedgerRow left, LedgerRow right) {
        Comparator<LedgerRow> comparator = Comparator
            .comparing(LedgerRow::getSortDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(LedgerRow::getInvoiceId, Comparator.nullsLast(String::compareToIgnoreCase));
        return comparator.compare(left, right);
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
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            List<FsDocument> parents = client.listDocuments("parents");
            List<LedgerRow> loaded = new ArrayList<>();

            for (FsDocument parent : parents) {
                String parentId = parent.getId();
                String parentName = firstNonBlank(
                    parent.getString("parentName"),
                    parent.getString("name"),
                    parent.getString("fullName"),
                    parent.getString("username"),
                    parentId
                );

                List<FsDocument> invoices = client.listSubcollectionDocuments("parents", parentId, "invoices");
                if (invoices.isEmpty()) {
                    continue;
                }

                List<FsDocument> payments = client.listSubcollectionDocuments("parents", parentId, "payments");
                Map<String, List<FsDocument>> paymentsByInvoiceId = new HashMap<>();
                for (FsDocument payment : payments) {
                    String invoiceId = firstNonBlank(payment.getString("invoiceId"), parseDocId(payment.getString("invoiceRef")));
                    if (invoiceId == null || invoiceId.isBlank()) {
                        continue;
                    }
                    paymentsByInvoiceId.computeIfAbsent(invoiceId, ignored -> new ArrayList<>()).add(payment);
                }

                for (FsDocument invoice : invoices) {
                    FsDocument payment = latestPayment(paymentsByInvoiceId.get(invoice.getId()));
                    loaded.add(LedgerRow.from(parentId, parentName, invoice, payment));
                }
            }

            loaded.sort(Comparator
                .comparing(LedgerRow::getSortDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LedgerRow::getInvoiceId, Comparator.nullsLast(String::compareToIgnoreCase)));
            return loaded;
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void applyFilters() {
        String selectedStatus = firstNonBlank(statusFilter.getValue(), "All");
        String selectedReviewType = firstNonBlank(reviewFilter.getValue(), "All Reviews");
        String query = normalize(searchField.getText());

        List<LedgerRow> baseFiltered = new ArrayList<>();
        for (LedgerRow row : allRows) {
            if (focusedParentId != null && !focusedParentId.equals(row.getParentId())) {
                continue;
            }
            if (!"All".equalsIgnoreCase(selectedStatus) && !selectedStatus.equalsIgnoreCase(row.getStatus())) {
                continue;
            }
            if (!matchesDateScope(row, dateScopeFilter.getValue())) {
                continue;
            }
            if (!matchesReviewFilter(row, selectedReviewType)) {
                continue;
            }
            if (!query.isBlank() && !row.matches(query)) {
                continue;
            }
            baseFiltered.add(row);
        }

        List<ParentSummaryRow> summaries = buildParentSummaries(baseFiltered);
        List<ParentSummaryRow> visibleSummaries = filterParentSummariesByRisk(summaries, firstNonBlank(riskFilter.getValue(), "All Families"));
        Map<String, ParentSummaryRow> visibleParentIds = new LinkedHashMap<>();
        for (ParentSummaryRow summary : visibleSummaries) {
            visibleParentIds.put(summary.getParentId(), summary);
        }

        List<LedgerRow> filtered = new ArrayList<>();
        for (LedgerRow row : baseFiltered) {
            if (visibleParentIds.containsKey(row.getParentId())) {
                filtered.add(row);
            }
        }

        filtered.sort(buildSortComparator(firstNonBlank(sortFilter.getValue(), "Latest Activity")));

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
        List<ParentSummaryRow> summaries = buildParentSummaries(rows);
        List<ParentSummaryRow> visibleSummaries = filterParentSummariesByRisk(summaries, firstNonBlank(riskFilter.getValue(), "All Families"));
        parentSummaryRows.setAll(visibleSummaries);
        exportVisibleParentsButton.setDisable(visibleSummaries.isEmpty());
    }

    private List<ParentSummaryRow> buildParentSummaries(List<LedgerRow> rows) {
        Map<String, ParentSummaryAccumulator> byParent = new LinkedHashMap<>();
        for (LedgerRow row : rows) {
            String parentId = firstNonBlank(row.getParentId(), "-");
            ParentSummaryAccumulator accumulator = byParent.get(parentId);
            if (accumulator == null) {
                accumulator = new ParentSummaryAccumulator(parentId, firstNonBlank(row.getParentName(), "-"));
                byParent.put(parentId, accumulator);
            }
            accumulator.add(row);
        }

        List<ParentSummaryRow> summaries = new ArrayList<>();
        for (ParentSummaryAccumulator accumulator : byParent.values()) {
            summaries.add(accumulator.toRow());
        }

        summaries.sort(buildParentSummaryComparator(firstNonBlank(parentSortFilter.getValue(), "Highest Outstanding")));

        return summaries;
    }

    private List<ParentSummaryRow> filterParentSummariesByRisk(List<ParentSummaryRow> summaries, String riskMode) {
        String normalizedRisk = firstNonBlank(riskMode, "All Families");
        if ("All Families".equalsIgnoreCase(normalizedRisk)) {
            return summaries;
        }

        List<ParentSummaryRow> filtered = new ArrayList<>();
        for (ParentSummaryRow summary : summaries) {
            if ("Needs Review".equalsIgnoreCase(normalizedRisk)) {
                if (summary.getReviewCount() > 0) {
                    filtered.add(summary);
                }
                continue;
            }
            if (normalizedRisk.equalsIgnoreCase(summary.getRiskLevel())) {
                filtered.add(summary);
            }
        }
        return filtered;
    }

    private Comparator<ParentSummaryRow> buildParentSummaryComparator(String sortMode) {
        if ("Most Overdue Parents".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparingInt(ParentSummaryRow::getOverdueCount)
                .reversed()
                .thenComparing(Comparator.comparingLong(ParentSummaryRow::getOutstandingSen).reversed())
                .thenComparing(ParentSummaryRow::getParentName, String::compareToIgnoreCase);
        }
        if ("Most Unpaid Invoices".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparingInt(ParentSummaryRow::getUnpaidCount)
                .reversed()
                .thenComparing(Comparator.comparingLong(ParentSummaryRow::getOutstandingSen).reversed())
                .thenComparing(ParentSummaryRow::getParentName, String::compareToIgnoreCase);
        }
        if ("Most Age Reviews".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparingInt(ParentSummaryRow::getAgeReviewCount)
                .reversed()
                .thenComparing(Comparator.comparingInt(ParentSummaryRow::getReviewCount).reversed())
                .thenComparing(Comparator.comparingLong(ParentSummaryRow::getOutstandingSen).reversed())
                .thenComparing(ParentSummaryRow::getParentName, String::compareToIgnoreCase);
        }
        if ("Most Overtime Reviews".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparingInt(ParentSummaryRow::getOvertimeReviewCount)
                .reversed()
                .thenComparing(Comparator.comparingInt(ParentSummaryRow::getReviewCount).reversed())
                .thenComparing(Comparator.comparingLong(ParentSummaryRow::getOutstandingSen).reversed())
                .thenComparing(ParentSummaryRow::getParentName, String::compareToIgnoreCase);
        }
        if ("Latest Parent Payment".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparing(ParentSummaryRow::getLastPaidAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Comparator.comparingLong(ParentSummaryRow::getOutstandingSen).reversed())
                .thenComparing(ParentSummaryRow::getParentName, String::compareToIgnoreCase);
        }
        if ("Parent Name".equalsIgnoreCase(sortMode)) {
            return Comparator.comparing(ParentSummaryRow::getParentName, String::compareToIgnoreCase);
        }
        return Comparator
            .comparingLong(ParentSummaryRow::getOutstandingSen)
            .reversed()
            .thenComparing(Comparator.comparingInt(ParentSummaryRow::getOverdueCount).reversed())
            .thenComparing(ParentSummaryRow::getParentName, String::compareToIgnoreCase);
    }

    private void updateSummary(List<LedgerRow> rows) {
        long paid = 0L;
        long outstanding = 0L;
        int overdueCount = 0;
        int ageReviewInvoiceCount = 0;
        int overtimeReviewInvoiceCount = 0;
        Map<String, Boolean> ageReviewParents = new LinkedHashMap<>();
        Map<String, Boolean> overtimeReviewParents = new LinkedHashMap<>();
        Map<String, Long> outstandingByPeriod = new LinkedHashMap<>();
        for (LedgerRow row : rows) {
            if (row.isPaid()) {
                paid += row.getTotalSen();
            } else {
                outstanding += row.getTotalSen();
                String periodKey = firstNonBlank(row.getPeriod(), "Unknown");
                Long current = outstandingByPeriod.get(periodKey);
                outstandingByPeriod.put(periodKey, (current == null ? 0L : current) + row.getTotalSen());
            }
            if (row.isOverdue()) {
                overdueCount++;
            }
            if (row.isAgeOutOfPolicy()) {
                ageReviewInvoiceCount++;
                ageReviewParents.put(row.getParentId(), Boolean.TRUE);
            } else if (row.isManagementReviewRecommended()) {
                overtimeReviewInvoiceCount++;
                overtimeReviewParents.put(row.getParentId(), Boolean.TRUE);
            }
        }

        invoiceCountLabel.setText("Invoices: " + rows.size());
        paidTotalLabel.setText("Paid: " + formatMoney(paid));
        outstandingTotalLabel.setText("Outstanding: " + formatMoney(outstanding));
        overdueCountLabel.setText("Overdue: " + overdueCount);
        ageReviewSummaryLabel.setText("Age Review: " + ageReviewParents.size() + " fam / " + ageReviewInvoiceCount + " inv");
        overtimeReviewSummaryLabel.setText("OT Review: " + overtimeReviewParents.size() + " fam / " + overtimeReviewInvoiceCount + " inv");
        periodTotalsLabel.setText(buildPeriodTotalsText(outstandingByPeriod));
    }

    private Comparator<LedgerRow> buildSortComparator(String sortMode) {
        if ("Highest Outstanding".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparingLong((LedgerRow row) -> row.isPaid() ? 0L : row.getTotalSen())
                .reversed()
                .thenComparing(LedgerRow::getSortDate, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        if ("Most Overdue".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparingLong(LedgerRow::getOverdueDays)
                .reversed()
                .thenComparing(Comparator.comparingLong((LedgerRow row) -> row.isPaid() ? 0L : row.getTotalSen()).reversed())
                .thenComparing(LedgerRow::getSortDate, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        if ("Latest Payment".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparing(LedgerRow::getPaidAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LedgerRow::getSortDate, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return Comparator
            .comparing(LedgerRow::getSortDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(LedgerRow::getInvoiceId, Comparator.nullsLast(String::compareToIgnoreCase));
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
        StringBuilder sb = new StringBuilder();
        sb.append("Taska Zurah Billing Record\n");
        sb.append("Generated At: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        sb.append("Invoice ID: ").append(nullSafe(row.getInvoiceId())).append('\n');
        sb.append("Parent: ").append(nullSafe(row.getParentName())).append(" (ID: ").append(nullSafe(row.getParentId())).append(")\n");
    sb.append("Student(s): ").append(nullSafe(row.getChildDisplayName())).append('\n');
        sb.append("Period: ").append(nullSafe(row.getPeriod())).append('\n');
        sb.append("Status: ").append(nullSafe(row.getStatus())).append('\n');
        sb.append("Due Date: ").append(formatDate(row.getDueDate())).append('\n');
        if (row.isOverdue()) {
            sb.append("Overdue By: ").append(row.getOverdueDays()).append(" day(s)\n");
        }
        sb.append("Created At: ").append(formatDateTime(row.getCreatedAt())).append('\n');
        sb.append("Total: ").append(row.getTotalText()).append('\n');

        String receiptNo = row.getReceiptNo();
        if (!"-".equals(receiptNo)) {
            sb.append("Receipt No: ").append(receiptNo).append('\n');
        }

        String paidAt = formatDateTime(row.getPaidAt());
        if (!"-".equals(paidAt)) {
            sb.append("Paid At: ").append(paidAt).append('\n');
        }

        String method = firstNonBlank(
            row.invoice.getString("paidMethod"),
            row.payment == null ? null : row.payment.getString("method")
        );
        if (method != null) {
            sb.append("Payment Method: ").append(method).append('\n');
        }

        String bank = firstNonBlank(
            row.invoice.getString("paidBank"),
            row.payment == null ? null : row.payment.getString("bank")
        );
        if (bank != null) {
            sb.append("Bank: ").append(bank).append('\n');
        }

        String paymentId = firstNonBlank(
            row.invoice.getString("paidPaymentId"),
            row.payment == null ? null : row.payment.getId()
        );
        if (paymentId != null) {
            sb.append("Payment Record ID: ").append(paymentId).append('\n');
        }

        String provider = row.payment == null ? null : row.payment.getString("provider");
        if (provider != null) {
            sb.append("Provider: ").append(provider).append('\n');
        }

        sb.append('\n').append("Invoice Items").append('\n');
        appendInvoiceItems(sb, row.invoice.get("items"));

        List<String> policyNotes = invoicePolicyNotes(row.invoice);
        if (!policyNotes.isEmpty()) {
            sb.append('\n').append("Policy Notes").append('\n');
            for (String note : policyNotes) {
                sb.append("- ").append(note).append('\n');
            }
        }

        if (invoiceManagementReviewRecommended(row.invoice)) {
            sb.append('\n').append("Management Review").append('\n');
            sb.append("- ").append(nullSafe(invoiceReviewReason(row.invoice))).append('\n');
        }

        if (row.payment != null) {
            sb.append('\n').append("Payment Record").append('\n');
            sb.append("Status: ").append(nullSafe(firstNonBlank(row.payment.getString("status"), row.getStatus()))).append('\n');
            Long amountSen = row.payment.getLong("amountSen");
            if (amountSen != null) {
                sb.append("Amount: ").append(formatMoney(amountSen)).append('\n');
            }
            String gatewaySummary = row.payment.getString("gatewaySummary");
            if (gatewaySummary != null && !gatewaySummary.isBlank()) {
                sb.append("Gateway Summary: ").append(gatewaySummary).append('\n');
            }
        }

        sb.append('\n').append(buildParentSnapshot(row));

        return sb.toString();
    }

    private String buildParentSnapshot(LedgerRow selectedRow) {
        String parentId = selectedRow.getParentId();
        List<LedgerRow> parentRows = new ArrayList<>();
        for (LedgerRow row : allRows) {
            if (Objects.equals(parentId, row.getParentId())) {
                parentRows.add(row);
            }
        }

        long paidTotal = 0L;
        long outstandingTotal = 0L;
        int overdue = 0;
        int unpaid = 0;
        Date lastPaidAt = null;

        for (LedgerRow row : parentRows) {
            if (row.isPaid()) {
                paidTotal += row.getTotalSen();
                if (row.getPaidAt() != null && (lastPaidAt == null || row.getPaidAt().after(lastPaidAt))) {
                    lastPaidAt = row.getPaidAt();
                }
            } else {
                outstandingTotal += row.getTotalSen();
                unpaid++;
            }
            if (row.isOverdue()) {
                overdue++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Parent Billing Snapshot\n");
        sb.append("Invoices on record: ").append(parentRows.size()).append('\n');
        sb.append("Unpaid invoices: ").append(unpaid).append('\n');
        sb.append("Overdue invoices: ").append(overdue).append('\n');
        sb.append("Total paid: ").append(formatMoney(paidTotal)).append('\n');
        sb.append("Total outstanding: ").append(formatMoney(outstandingTotal)).append('\n');
        sb.append("Last payment date: ").append(formatDateTime(lastPaidAt)).append('\n');
        return sb.toString();
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

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Record Cash Payment");
        confirm.setHeaderText("Mark this invoice as paid by cash?");
        confirm.setContentText(
            "Invoice: " + nullSafe(row.getInvoiceId())
                + "\nParent: " + nullSafe(row.getParentName())
                + "\nAmount: " + row.getTotalText()
                + "\n\nThis will create a payment record and set the invoice status to Paid."
        );
        confirm.initOwner(getWindow());
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        markCashPaidButton.setDisable(true);
        refreshButton.setDisable(true);
        statusLabel.setText("Recording cash payment for invoice " + nullSafe(row.getInvoiceId()) + "...");

        CompletableFuture
            .runAsync(() -> recordCashPayment(row))
            .whenComplete((ignored, error) -> Platform.runLater(() -> {
                refreshButton.setDisable(false);
                if (error != null) {
                    statusLabel.setText("Cash payment failed: " + rootMessage(error));
                    showError("Failed to record cash payment", new Exception(rootMessage(error), error));
                    markCashPaidButton.setDisable(false);
                    return;
                }

                statusLabel.setText("Cash payment recorded for invoice " + nullSafe(row.getInvoiceId()) + ".");
                showSimple("Cash payment recorded", "The selected invoice is now marked as paid.");
                reloadData();
            }));
    }

    private void recordCashPayment(LedgerRow row) {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            Date now = new Date();
            String receiptNo = buildCashReceiptNo(row, now);

            Map<String, Object> payment = new LinkedHashMap<>();
            payment.put("provider", "cash");
            payment.put("status", "succeeded");
            payment.put("invoiceId", row.getInvoiceId());
            payment.put("currency", firstNonBlank(row.invoice.getString("currency"), "MYR"));
            payment.put("amountSen", row.getTotalSen());
            payment.put("method", "Cash");
            payment.put("bank", null);
            payment.put("receiptNo", receiptNo);
            payment.put("paidAt", now);
            payment.put("createdAt", now);
            payment.put("createdBy", Map.of(
                "username", firstNonBlank(UserSession.getUsername(), "unknown"),
                "name", firstNonBlank(UserSession.getName(), "unknown"),
                "kind", "admin-cash"
            ));

            FsDocument paymentDoc = client.addSubcollectionDocumentAutoId("parents", row.getParentId(), "payments", payment);

            Map<String, Object> invoicePatch = new LinkedHashMap<>();
            invoicePatch.put("status", "paid");
            invoicePatch.put("paidAt", now);
            invoicePatch.put("paidMethod", "Cash");
            invoicePatch.put("paidBank", "");
            invoicePatch.put("paidAmountSen", row.getTotalSen());
            invoicePatch.put("paidReceiptNo", receiptNo);
            invoicePatch.put("paidPaymentId", paymentDoc == null ? null : paymentDoc.getId());
            invoicePatch.put("paidProvider", "cash");
            invoicePatch.put("updatedAt", now);

            client.patchSubcollectionDocumentMerge("parents", row.getParentId(), "invoices", row.getInvoiceId(), invoicePatch);
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String buildCashReceiptNo(LedgerRow row, Date timestamp) {
        LocalDateTime issuedAt = timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        String invoiceToken = slug(row.getInvoiceId()).replace("-", "");
        if (invoiceToken.length() > 6) {
            invoiceToken = invoiceToken.substring(0, 6);
        }
        return "CASH-" + issuedAt.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + invoiceToken.toUpperCase(Locale.ROOT);
    }

    private void exportSelectedLedgerRowPdf(boolean openAfterExport, boolean printAfterExport) {
        LedgerRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            showSimple("No invoice selected", "Select an invoice row before exporting.");
            return;
        }

        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Invoice or Receipt (PDF)");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            fileChooser.setInitialFileName(exportBaseName(row) + ".pdf");

            File file = fileChooser.showSaveDialog(getWindow());
            if (file == null) {
                return;
            }

            writeLedgerRowPdf(file, row);

            boolean opened = openAfterExport && openExportedFile(file);
            boolean printed = printAfterExport && printExportedFile(file);
            showSimple("Exported", buildExportMessage("Billing PDF", file, openAfterExport, opened, printAfterExport, printed));
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

        List<LedgerRow> parentRows = rowsForParent(summary.getParentId(), visibleRows);
        if (parentRows.isEmpty()) {
            showSimple("No parent invoices", "The selected parent has no invoices in the current view.");
            return;
        }

        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Parent Billing Summary (PDF)");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            fileChooser.setInitialFileName(parentSummaryExportBaseName(summary) + ".pdf");

            File file = fileChooser.showSaveDialog(getWindow());
            if (file == null) {
                return;
            }

            writeParentSummaryPdf(file, summary, parentRows);

            boolean opened = openAfterExport && openExportedFile(file);
            boolean printed = printAfterExport && printExportedFile(file);
            showSimple("Exported", buildExportMessage("Parent billing PDF", file, openAfterExport, opened, printAfterExport, printed));
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
            javafx.stage.DirectoryChooser directoryChooser = new javafx.stage.DirectoryChooser();
            directoryChooser.setTitle("Choose Folder for Visible Parent PDF Export");
            File targetDirectory = directoryChooser.showDialog(getWindow());
            if (targetDirectory == null) {
                return;
            }

            String exportStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File exportDirectory = new File(targetDirectory, "billing-parent-pdfs-" + exportStamp);
            if (!exportDirectory.exists() && !exportDirectory.mkdirs()) {
                showSimple("Export failed", "Could not create the export folder:\n" + exportDirectory.getAbsolutePath());
                return;
            }

            int exportedCount = 0;
            for (ParentSummaryRow summary : parentSummaryRows) {
                List<LedgerRow> parentRows = rowsForParent(summary.getParentId(), visibleRows);
                if (parentRows.isEmpty()) {
                    continue;
                }

                File outputFile = new File(exportDirectory, parentSummaryExportBaseName(summary) + ".pdf");
                writeParentSummaryPdf(outputFile, summary, parentRows);
                exportedCount++;
            }

            showSimple("Batch export complete", "Exported " + exportedCount + " visible parent summaries to:\n" + exportDirectory.getAbsolutePath());
        } catch (Exception ex) {
            showError("Failed to export visible parent summaries PDF", ex);
        }
    }

    private void writeParentSummaryPdf(File file, ParentSummaryRow summary, List<LedgerRow> parentRows) throws Exception {
        Document document = new Document(PageSize.A4, 34, 34, 38, 34);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            PdfWriter.getInstance(document, out);
            document.open();

            java.awt.Color brandGold = new java.awt.Color(255, 203, 60);
            java.awt.Color brandGoldSoft = new java.awt.Color(255, 245, 217);
            java.awt.Color ink = new java.awt.Color(29, 42, 58);
            java.awt.Color muted = new java.awt.Color(120, 132, 150);
            java.awt.Color border = new java.awt.Color(228, 234, 242);
            java.awt.Color successBg = new java.awt.Color(234, 248, 239);
            java.awt.Color successText = new java.awt.Color(13, 122, 56);
            java.awt.Color dangerBg = new java.awt.Color(255, 240, 238);
            java.awt.Color dangerText = new java.awt.Color(197, 59, 42);
            java.awt.Color tableAlt = new java.awt.Color(248, 250, 252);

            List<LedgerRow> sortedRows = new ArrayList<>(parentRows);
            sortedRows.sort(buildSortComparator("Latest Activity"));

            addParentSummaryPdfHeader(document, summary, sortedRows, brandGoldSoft, ink, muted, successBg, dangerBg, successText, dangerText);
            addParentSummaryPdfMetrics(document, summary, sortedRows, ink, muted, border, brandGoldSoft, successBg, successText, dangerBg, dangerText);
            addParentSummaryPdfInvoices(document, sortedRows, brandGold, ink, muted, border, successBg, successText, dangerBg, dangerText, tableAlt);
            addParentSummaryPdfReview(document, summary, sortedRows, ink, muted, border, brandGoldSoft);
            addParentSummaryPdfFooter(document, summary, muted, ink);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    private void writeLedgerRowPdf(File file, LedgerRow row) throws Exception {
        Document document = new Document(PageSize.A4, 34, 34, 38, 34);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            PdfWriter.getInstance(document, out);
            document.open();

            java.awt.Color brandGold = new java.awt.Color(255, 203, 60);
            java.awt.Color brandGoldSoft = new java.awt.Color(255, 245, 217);
            java.awt.Color ink = new java.awt.Color(29, 42, 58);
            java.awt.Color muted = new java.awt.Color(120, 132, 150);
            java.awt.Color border = new java.awt.Color(228, 234, 242);
            java.awt.Color successBg = new java.awt.Color(234, 248, 239);
            java.awt.Color successText = new java.awt.Color(13, 122, 56);
            java.awt.Color dangerBg = new java.awt.Color(255, 240, 238);
            java.awt.Color dangerText = new java.awt.Color(197, 59, 42);
            java.awt.Color tableAlt = new java.awt.Color(248, 250, 252);

            addInvoicePdfHeader(document, row, brandGold, brandGoldSoft, ink, muted, successBg, successText, dangerBg, dangerText);
            addInvoicePdfIdentity(document, row, ink, muted, border);
            addInvoicePdfItems(document, row, brandGold, ink, muted, border, successBg, successText, dangerBg, dangerText, tableAlt);
            addInvoicePdfNotes(document, row, ink, muted, border, brandGoldSoft, successBg);
            addInvoicePdfFooter(document, row, muted, ink);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    private void addInvoicePdfHeader(Document document, LedgerRow row, java.awt.Color brandGold, java.awt.Color brandGoldSoft, java.awt.Color ink, java.awt.Color muted, java.awt.Color successBg, java.awt.Color successText, java.awt.Color dangerBg, java.awt.Color dangerText) throws Exception {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1.15f, 5.85f});
        header.setSpacingAfter(10f);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setBackgroundColor(brandGoldSoft);
        logoCell.setPadding(16f);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        try {
            java.net.URL resource = getClass().getResource("/nfc/logo.png");
            if (resource != null) {
                Image logo = Image.getInstance(resource);
                logo.scaleToFit(52, 52);
                logoCell.addElement(logo);
            }
        } catch (DocumentException | java.io.IOException ignored) {
            // Logo is optional in the PDF export.
        }

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setBackgroundColor(brandGoldSoft);
        titleCell.setPadding(16f);

        Paragraph eyebrow = new Paragraph("BILLING LEDGER EXPORT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, muted));
        eyebrow.setSpacingAfter(6f);
        titleCell.addElement(eyebrow);

        String title = row.isPaid() ? "Taska Zurah Payment Receipt" : "Taska Zurah Billing Invoice";
        Paragraph heading = new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, ink));
        heading.setSpacingAfter(4f);
        titleCell.addElement(heading);

        Paragraph subtitle = new Paragraph(
            nullSafe(row.getParentName()) + "  |  " + nullSafe(row.getChildDisplayName()) + "  |  " + nullSafe(row.getPeriod()),
            FontFactory.getFont(FontFactory.HELVETICA, 11, muted)
        );
        titleCell.addElement(subtitle);

        header.addCell(logoCell);
        header.addCell(titleCell);
        document.add(header);

        PdfPTable statusTable = new PdfPTable(2);
        statusTable.setWidthPercentage(100);
        statusTable.setWidths(new float[]{3.8f, 1.4f});
        statusTable.setSpacingAfter(12f);

        PdfPCell metaCell = new PdfPCell();
        metaCell.setBorder(Rectangle.NO_BORDER);
        metaCell.setPadding(10f);
        metaCell.setBackgroundColor(java.awt.Color.WHITE);
        metaCell.addElement(new Paragraph(
            "Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))
                + "\nInvoice ID: " + nullSafe(row.getInvoiceId()),
            FontFactory.getFont(FontFactory.HELVETICA, 10, muted)
        ));

        PdfPCell badgeCell = new PdfPCell(new Phrase("Status: " + nullSafe(row.getStatus()).toUpperCase(Locale.ROOT), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10,
            row.isPaid() ? successText : (row.isOverdue() ? dangerText : new java.awt.Color(154, 103, 0)))));
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeCell.setPadding(10f);
        badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        badgeCell.setBackgroundColor(row.isPaid() ? successBg : (row.isOverdue() ? dangerBg : brandGold));

        statusTable.addCell(metaCell);
        statusTable.addCell(badgeCell);
        document.add(statusTable);
    }

    private void addInvoicePdfIdentity(Document document, LedgerRow row, java.awt.Color ink, java.awt.Color muted, java.awt.Color border) throws Exception {
        PdfPTable cards = new PdfPTable(2);
        cards.setWidthPercentage(100);
        cards.setWidths(new float[]{1f, 1f});
        cards.setSpacingAfter(14f);

        cards.addCell(buildInvoicePdfCard("Parent", new String[][] {
            {"Name", nullSafe(row.getParentName())},
            {"Parent ID", nullSafe(row.getParentId())},
            {"Student(s)", nullSafe(row.getChildDisplayName())}
        }, ink, muted, border));

        String method = firstNonBlank(
            row.invoice.getString("paidMethod"),
            row.payment == null ? null : row.payment.getString("method")
        );
        String bank = firstNonBlank(
            row.invoice.getString("paidBank"),
            row.payment == null ? null : row.payment.getString("bank")
        );

        cards.addCell(buildInvoicePdfCard(row.isPaid() ? "Payment" : "Billing", new String[][] {
            {"Period", nullSafe(row.getPeriod())},
            {"Due Date", formatDate(row.getDueDate())},
            {"Paid At", formatDateTime(row.getPaidAt())},
            {"Receipt No", nullSafe(row.getReceiptNo())},
            {"Method", method == null ? "-" : method},
            {"Bank", bank == null ? "-" : bank}
        }, ink, muted, border));

        document.add(cards);
    }

    private PdfPCell buildInvoicePdfCard(String title, String[][] rows, java.awt.Color ink, java.awt.Color muted, java.awt.Color border) {
        PdfPCell card = new PdfPCell();
        card.setPadding(14f);
        card.setBorderColor(border);
        card.setBackgroundColor(java.awt.Color.WHITE);

        Paragraph cardTitle = new Paragraph(title.toUpperCase(Locale.ROOT), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, muted));
        cardTitle.setSpacingAfter(10f);
        card.addElement(cardTitle);

        PdfPTable inner = new PdfPTable(2);
        inner.setWidthPercentage(100);
        inner.setWidths(new float[]{1.2f, 1.8f});
        for (String[] row : rows) {
            PdfPCell labelCell = new PdfPCell(new Phrase(row[0], FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, muted)));
            labelCell.setBorder(Rectangle.NO_BORDER);
            labelCell.setPadding(6f);
            inner.addCell(labelCell);

            PdfPCell valueCell = new PdfPCell(new Phrase(row[1] == null ? "-" : row[1], FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink)));
            valueCell.setBorder(Rectangle.NO_BORDER);
            valueCell.setPadding(6f);
            inner.addCell(valueCell);
        }
        card.addElement(inner);
        return card;
    }

    private void addInvoicePdfItems(Document document, LedgerRow row, java.awt.Color brandGold, java.awt.Color ink, java.awt.Color muted, java.awt.Color border, java.awt.Color successBg, java.awt.Color successText, java.awt.Color dangerBg, java.awt.Color dangerText, java.awt.Color tableAlt) throws Exception {
        Paragraph sectionTitle = new Paragraph("Invoice Items", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, ink));
        sectionTitle.setSpacingAfter(8f);
        document.add(sectionTitle);

        PdfPTable itemsTable = new PdfPTable(3);
        itemsTable.setWidthPercentage(100);
        itemsTable.setWidths(new float[]{4.2f, 1f, 1.5f});
        itemsTable.setSpacingAfter(14f);

        addInvoicePdfHeaderCell(itemsTable, "Description", brandGold, ink, border, Element.ALIGN_LEFT);
        addInvoicePdfHeaderCell(itemsTable, "Qty", brandGold, ink, border, Element.ALIGN_CENTER);
        addInvoicePdfHeaderCell(itemsTable, "Amount", brandGold, ink, border, Element.ALIGN_RIGHT);

        appendInvoiceItemsPdf(itemsTable, row.invoice.get("items"), border, ink, muted, tableAlt);
        document.add(itemsTable);

        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(42);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setWidths(new float[]{1.3f, 1f});
        totals.setSpacingAfter(14f);

        addInvoicePdfTotalRow(totals, "Total Amount", row.getTotalText(), new java.awt.Color(255, 245, 217), ink, border);

        String provider = firstNonBlank(row.payment == null ? null : row.payment.getString("provider"), row.invoice.getString("paidProvider"));
        if (provider != null) {
            addInvoicePdfTotalRow(totals, "Provider", provider, row.isPaid() ? successBg : tableAlt, row.isPaid() ? successText : ink, border);
        }
        if (row.isOverdue()) {
            addInvoicePdfTotalRow(totals, "Overdue By", row.getOverdueDays() + " day(s)", dangerBg, dangerText, border);
        }

        document.add(totals);
    }

    private void addInvoicePdfHeaderCell(PdfPTable table, String text, java.awt.Color bg, java.awt.Color ink, java.awt.Color border, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink)));
        cell.setBackgroundColor(bg);
        cell.setBorderColor(border);
        cell.setPadding(8f);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private void appendInvoiceItemsPdf(PdfPTable table, Object rawItems, java.awt.Color border, java.awt.Color ink, java.awt.Color muted, java.awt.Color tableAlt) {
        if (!(rawItems instanceof List<?>)) {
            addInvoicePdfEmptyItemsRow(table, border, muted);
            return;
        }

        List<?> items = (List<?>) rawItems;
        if (items.isEmpty()) {
            addInvoicePdfEmptyItemsRow(table, border, muted);
            return;
        }

        int index = 0;
        for (Object item : items) {
            java.awt.Color rowBg = index % 2 == 0 ? java.awt.Color.WHITE : tableAlt;
            if (item instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) item;
                String title = firstNonBlank(
                    stringValue(map.get("title")),
                    stringValue(map.get("description")),
                    stringValue(map.get("label")),
                    stringValue(map.get("code")),
                    "Item " + (index + 1)
                );
                Long amountSen = longValue(map.get("amountSen"));
                Long quantity = longValue(map.get("qty"));
                if (quantity == null) {
                    quantity = longValue(map.get("quantity"));
                }

                addInvoicePdfBodyCell(table, title, rowBg, border, Element.ALIGN_LEFT, ink, false);
                addInvoicePdfBodyCell(table, quantity == null ? "1" : String.valueOf(quantity), rowBg, border, Element.ALIGN_CENTER, ink, false);
                addInvoicePdfBodyCell(table, amountSen == null ? "-" : formatMoney(amountSen), rowBg, border, Element.ALIGN_RIGHT, ink, true);
            } else if (item != null) {
                addInvoicePdfBodyCell(table, String.valueOf(item), rowBg, border, Element.ALIGN_LEFT, ink, false);
                addInvoicePdfBodyCell(table, "1", rowBg, border, Element.ALIGN_CENTER, ink, false);
                addInvoicePdfBodyCell(table, "-", rowBg, border, Element.ALIGN_RIGHT, ink, false);
            }
            index++;
        }
    }

    private void addInvoicePdfEmptyItemsRow(PdfPTable table, java.awt.Color border, java.awt.Color muted) {
        PdfPCell cell = new PdfPCell(new Phrase("No line items stored for this invoice.", FontFactory.getFont(FontFactory.HELVETICA, 10, muted)));
        cell.setColspan(3);
        cell.setPadding(12f);
        cell.setBorderColor(border);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addInvoicePdfBodyCell(PdfPTable table, String text, java.awt.Color bg, java.awt.Color border, int alignment, java.awt.Color color, boolean bold) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 10, bold ? Font.BOLD : Font.NORMAL, color);
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "-" : text, font));
        cell.setBackgroundColor(bg);
        cell.setBorderColor(border);
        cell.setPadding(8f);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private void addInvoicePdfTotalRow(PdfPTable table, String label, String value, java.awt.Color bg, java.awt.Color valueColor, java.awt.Color border) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new java.awt.Color(120, 132, 150))));
        labelCell.setBackgroundColor(bg);
        labelCell.setBorderColor(border);
        labelCell.setPadding(8f);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);

        PdfPCell valueCell = new PdfPCell(new Phrase(value == null ? "-" : value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, valueColor)));
        valueCell.setBackgroundColor(bg);
        valueCell.setBorderColor(border);
        valueCell.setPadding(8f);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addInvoicePdfNotes(Document document, LedgerRow row, java.awt.Color ink, java.awt.Color muted, java.awt.Color border, java.awt.Color brandGoldSoft, java.awt.Color successBg) throws Exception {
        List<String> policyNotes = invoicePolicyNotes(row.invoice);
        boolean hasReview = invoiceManagementReviewRecommended(row.invoice);

        PdfPTable section = new PdfPTable(2);
        section.setWidthPercentage(100);
        section.setWidths(new float[]{1f, 1f});
        section.setSpacingAfter(12f);

        section.addCell(buildInvoicePdfCard("Parent Snapshot", new String[][] {
            {"Invoices on record", String.valueOf(rowsForParent(row.getParentId(), allRows).size())},
            {"Unpaid invoices", buildSnapshotValue(row, "unpaid")},
            {"Overdue invoices", buildSnapshotValue(row, "overdue")},
            {"Total paid", buildSnapshotValue(row, "paidTotal")},
            {"Total outstanding", buildSnapshotValue(row, "outstandingTotal")},
            {"Last payment date", buildSnapshotValue(row, "lastPaidDate")}
        }, ink, muted, border));

        if (!policyNotes.isEmpty() || hasReview) {
            List<String[]> noteRows = new ArrayList<>();
            for (String note : policyNotes) {
                noteRows.add(new String[] {"Policy", note});
            }
            if (hasReview) {
                noteRows.add(new String[] {firstNonBlank(row.getReviewFlag(), "Review"), nullSafe(row.getReviewReason())});
            }
            section.addCell(buildInvoicePdfCard("Policy and Review", noteRows.toArray(new String[0][]), ink, muted, border));
        } else {
            PdfPCell noteCard = new PdfPCell();
            noteCard.setPadding(14f);
            noteCard.setBorderColor(border);
            noteCard.setBackgroundColor(brandGoldSoft);
            Paragraph title = new Paragraph("POLICY AND REVIEW", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, muted));
            title.setSpacingAfter(10f);
            noteCard.addElement(title);
            noteCard.addElement(new Paragraph("No manual review flags were recorded for this invoice.", FontFactory.getFont(FontFactory.HELVETICA, 10, ink)));
            section.addCell(noteCard);
        }

        if (row.payment != null) {
            PdfPCell paymentCard = buildInvoicePdfCard("Payment Record", new String[][] {
                {"Status", nullSafe(firstNonBlank(row.payment.getString("status"), row.getStatus()))},
                {"Amount", row.payment.getLong("amountSen") == null ? "-" : formatMoney(row.payment.getLong("amountSen"))},
                {"Provider", nullSafe(row.payment.getString("provider"))},
                {"Gateway Summary", nullSafe(firstNonBlank(row.payment.getString("gatewaySummary"), "-"))}
            }, ink, muted, border);
            paymentCard.setColspan(2);
            paymentCard.setBackgroundColor(row.isPaid() ? successBg : brandGoldSoft);
            section.addCell(paymentCard);
        }

        document.add(section);
    }

    private String buildSnapshotValue(LedgerRow selectedRow, String key) {
        String parentId = selectedRow.getParentId();
        List<LedgerRow> parentRows = rowsForParent(parentId, allRows);
        long paidTotal = 0L;
        long outstandingTotal = 0L;
        int overdue = 0;
        int unpaid = 0;
        Date lastPaidAt = null;

        for (LedgerRow row : parentRows) {
            if (row.isPaid()) {
                paidTotal += row.getTotalSen();
                if (row.getPaidAt() != null && (lastPaidAt == null || row.getPaidAt().after(lastPaidAt))) {
                    lastPaidAt = row.getPaidAt();
                }
            } else {
                outstandingTotal += row.getTotalSen();
                unpaid++;
            }
            if (row.isOverdue()) {
                overdue++;
            }
        }

        switch (key) {
            case "unpaid":
                return String.valueOf(unpaid);
            case "overdue":
                return String.valueOf(overdue);
            case "paidTotal":
                return formatMoney(paidTotal);
            case "outstandingTotal":
                return formatMoney(outstandingTotal);
            case "lastPaidDate":
                return formatDateTime(lastPaidAt);
            default:
                return "-";
        }
    }

    private void addInvoicePdfFooter(Document document, LedgerRow row, java.awt.Color muted, java.awt.Color ink) throws Exception {
        LineSeparator line = new LineSeparator();
        line.setPercentage(100f);
        line.setLineWidth(0.8f);
        line.setLineColor(new java.awt.Color(228, 234, 242));
        document.add(Chunk.NEWLINE);
        document.add(line);

        Paragraph footer = new Paragraph(
            row.isPaid()
                ? "Generated from the Taska Zurah billing ledger as a payment receipt record."
                : "Generated from the Taska Zurah billing ledger as an invoice snapshot for collection and follow-up.",
            FontFactory.getFont(FontFactory.HELVETICA, 10, muted)
        );
        footer.setSpacingBefore(8f);
        document.add(footer);

        Paragraph sign = new Paragraph("Taska Zurah Admin Billing Ledger", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink));
        sign.setAlignment(Element.ALIGN_RIGHT);
        sign.setSpacingBefore(16f);
        document.add(sign);
    }

    private void addParentSummaryPdfHeader(Document document, ParentSummaryRow summary, List<LedgerRow> sortedRows, java.awt.Color brandGoldSoft, java.awt.Color ink, java.awt.Color muted, java.awt.Color successBg, java.awt.Color dangerBg, java.awt.Color successText, java.awt.Color dangerText) throws Exception {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1.15f, 5.85f});
        header.setSpacingAfter(10f);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setBackgroundColor(brandGoldSoft);
        logoCell.setPadding(16f);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        try {
            java.net.URL resource = getClass().getResource("/nfc/logo.png");
            if (resource != null) {
                Image logo = Image.getInstance(resource);
                logo.scaleToFit(52, 52);
                logoCell.addElement(logo);
            }
        } catch (DocumentException | java.io.IOException ignored) {
            // Optional logo.
        }

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setBackgroundColor(brandGoldSoft);
        titleCell.setPadding(16f);

        Paragraph eyebrow = new Paragraph("BILLING LEDGER EXPORT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, muted));
        eyebrow.setSpacingAfter(6f);
        titleCell.addElement(eyebrow);

        Paragraph heading = new Paragraph("Taska Zurah Parent Billing Summary", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, ink));
        heading.setSpacingAfter(4f);
        titleCell.addElement(heading);

        Paragraph subtitle = new Paragraph(
            nullSafe(summary.getParentName()) + "  |  " + buildChildNamesText(sortedRows),
            FontFactory.getFont(FontFactory.HELVETICA, 11, muted)
        );
        titleCell.addElement(subtitle);

        header.addCell(logoCell);
        header.addCell(titleCell);
        document.add(header);

        PdfPTable statusTable = new PdfPTable(2);
        statusTable.setWidthPercentage(100);
        statusTable.setWidths(new float[]{3.7f, 1.5f});
        statusTable.setSpacingAfter(12f);

        PdfPCell metaCell = new PdfPCell();
        metaCell.setBorder(Rectangle.NO_BORDER);
        metaCell.setPadding(10f);
        metaCell.setBackgroundColor(java.awt.Color.WHITE);
        metaCell.addElement(new Paragraph(
            "Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))
                + "\nParent ID: " + nullSafe(summary.getParentId())
                + "\nFilter Scope: " + buildCurrentFilterDescription(),
            FontFactory.getFont(FontFactory.HELVETICA, 10, muted)
        ));

        String risk = firstNonBlank(summary.getRiskLevel(), "Clear");
        boolean critical = "Critical".equalsIgnoreCase(risk);
        PdfPCell badgeCell = new PdfPCell(new Phrase(
            "Risk: " + risk.toUpperCase(Locale.ROOT),
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, critical ? dangerText : successText)
        ));
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeCell.setPadding(10f);
        badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        badgeCell.setBackgroundColor(critical ? dangerBg : successBg);

        statusTable.addCell(metaCell);
        statusTable.addCell(badgeCell);
        document.add(statusTable);
    }

    private void addParentSummaryPdfMetrics(Document document, ParentSummaryRow summary, List<LedgerRow> sortedRows, java.awt.Color ink, java.awt.Color muted, java.awt.Color border, java.awt.Color brandGoldSoft, java.awt.Color successBg, java.awt.Color successText, java.awt.Color dangerBg, java.awt.Color dangerText) throws Exception {
        long paidTotal = 0L;
        long outstandingTotal = 0L;
        int paidCount = 0;
        int unpaidCount = 0;
        int overdueCount = 0;

        for (LedgerRow row : sortedRows) {
            if (row.isPaid()) {
                paidTotal += row.getTotalSen();
                paidCount++;
            } else {
                outstandingTotal += row.getTotalSen();
                unpaidCount++;
            }
            if (row.isOverdue()) {
                overdueCount++;
            }
        }

        PdfPTable cards = new PdfPTable(2);
        cards.setWidthPercentage(100);
        cards.setWidths(new float[]{1f, 1f});
        cards.setSpacingAfter(14f);

        cards.addCell(buildInvoicePdfCard("Parent Overview", new String[][] {
            {"Parent", nullSafe(summary.getParentName())},
            {"Children in View", buildChildNamesText(sortedRows)},
            {"Last Payment", nullSafe(summary.getLastPaidText())},
            {"Review Status", firstNonBlank(summary.getReviewStatus(), "-")}
        }, ink, muted, border));

        PdfPCell metricsCard = new PdfPCell();
        metricsCard.setPadding(14f);
        metricsCard.setBorderColor(border);
        metricsCard.setBackgroundColor(java.awt.Color.WHITE);
        Paragraph title = new Paragraph("SUMMARY METRICS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, muted));
        title.setSpacingAfter(10f);
        metricsCard.addElement(title);

        PdfPTable metrics = new PdfPTable(2);
        metrics.setWidthPercentage(100);
        metrics.setWidths(new float[]{1f, 1f});
        metrics.addCell(buildParentMetricCell("Paid", String.valueOf(paidCount), successBg, successText, muted));
        metrics.addCell(buildParentMetricCell("Unpaid", String.valueOf(unpaidCount), dangerBg, dangerText, muted));
        metrics.addCell(buildParentMetricCell("Outstanding", formatMoney(outstandingTotal), brandGoldSoft, ink, muted));
        metrics.addCell(buildParentMetricCell("Overdue", String.valueOf(overdueCount), overdueCount > 0 ? dangerBg : successBg, overdueCount > 0 ? dangerText : successText, muted));
        metrics.addCell(buildParentMetricCell("Paid Total", formatMoney(paidTotal), successBg, successText, muted));
        metrics.addCell(buildParentMetricCell("Review Alerts", String.valueOf(summary.getReviewCount()), brandGoldSoft, ink, muted));
        metricsCard.addElement(metrics);
        cards.addCell(metricsCard);

        document.add(cards);
    }

    private PdfPCell buildParentMetricCell(String label, String value, java.awt.Color bgColor, java.awt.Color valueColor, java.awt.Color muted) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(10f);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(bgColor);

        Paragraph labelParagraph = new Paragraph(label.toUpperCase(Locale.ROOT), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, muted));
        labelParagraph.setSpacingAfter(6f);
        cell.addElement(labelParagraph);

        Paragraph valueParagraph = new Paragraph(value == null ? "-" : value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, valueColor));
        cell.addElement(valueParagraph);
        return cell;
    }

    private void addParentSummaryPdfInvoices(Document document, List<LedgerRow> sortedRows, java.awt.Color brandGold, java.awt.Color ink, java.awt.Color muted, java.awt.Color border, java.awt.Color successBg, java.awt.Color successText, java.awt.Color dangerBg, java.awt.Color dangerText, java.awt.Color tableAlt) throws Exception {
        Paragraph sectionTitle = new Paragraph("Visible Invoices", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, ink));
        sectionTitle.setSpacingAfter(8f);
        document.add(sectionTitle);

        PdfPTable invoicesTable = new PdfPTable(6);
        invoicesTable.setWidthPercentage(100);
        invoicesTable.setWidths(new float[]{2.3f, 1.7f, 1.2f, 1.4f, 1.5f, 1.3f});
        invoicesTable.setSpacingAfter(14f);

        addInvoicePdfHeaderCell(invoicesTable, "Invoice ID", brandGold, ink, border, Element.ALIGN_LEFT);
        addInvoicePdfHeaderCell(invoicesTable, "Student(s)", brandGold, ink, border, Element.ALIGN_LEFT);
        addInvoicePdfHeaderCell(invoicesTable, "Period", brandGold, ink, border, Element.ALIGN_CENTER);
        addInvoicePdfHeaderCell(invoicesTable, "Status", brandGold, ink, border, Element.ALIGN_CENTER);
        addInvoicePdfHeaderCell(invoicesTable, "Due Date", brandGold, ink, border, Element.ALIGN_CENTER);
        addInvoicePdfHeaderCell(invoicesTable, "Amount", brandGold, ink, border, Element.ALIGN_RIGHT);

        if (sortedRows.isEmpty()) {
            PdfPCell emptyCell = new PdfPCell(new Phrase("No visible invoices for this parent summary.", FontFactory.getFont(FontFactory.HELVETICA, 10, muted)));
            emptyCell.setColspan(6);
            emptyCell.setPadding(12f);
            emptyCell.setBorderColor(border);
            emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            invoicesTable.addCell(emptyCell);
        } else {
            for (int index = 0; index < sortedRows.size(); index++) {
                LedgerRow row = sortedRows.get(index);
                java.awt.Color rowBg = index % 2 == 0 ? java.awt.Color.WHITE : tableAlt;
                java.awt.Color statusBg = row.isPaid() ? successBg : (row.isOverdue() ? dangerBg : brandGold);
                java.awt.Color statusColor = row.isPaid() ? successText : (row.isOverdue() ? dangerText : ink);

                addInvoicePdfBodyCell(invoicesTable, nullSafe(row.getInvoiceId()), rowBg, border, Element.ALIGN_LEFT, ink, false);
                addInvoicePdfBodyCell(invoicesTable, nullSafe(row.getChildDisplayName()), rowBg, border, Element.ALIGN_LEFT, ink, false);
                addInvoicePdfBodyCell(invoicesTable, nullSafe(row.getPeriod()), rowBg, border, Element.ALIGN_CENTER, ink, false);
                addInvoicePdfBodyCell(invoicesTable, nullSafe(row.getStatus()).toUpperCase(Locale.ROOT), statusBg, border, Element.ALIGN_CENTER, statusColor, true);
                addInvoicePdfBodyCell(invoicesTable, formatDate(row.getDueDate()), rowBg, border, Element.ALIGN_CENTER, ink, false);
                addInvoicePdfBodyCell(invoicesTable, row.getTotalText(), rowBg, border, Element.ALIGN_RIGHT, ink, true);
            }
        }

        document.add(invoicesTable);
    }

    private void addParentSummaryPdfReview(Document document, ParentSummaryRow summary, List<LedgerRow> sortedRows, java.awt.Color ink, java.awt.Color muted, java.awt.Color border, java.awt.Color brandGoldSoft) throws Exception {
        List<String> reviewReasons = collectReviewReasons(sortedRows);
        PdfPTable cards = new PdfPTable(2);
        cards.setWidthPercentage(100);
        cards.setWidths(new float[]{1f, 1f});
        cards.setSpacingAfter(12f);

        cards.addCell(buildInvoicePdfCard("Review Summary", new String[][] {
            {"Risk Level", firstNonBlank(summary.getRiskLevel(), "-")},
            {"Management Review", String.valueOf(summary.getReviewCount())},
            {"Age Review", String.valueOf(summary.getAgeReviewCount())},
            {"OT Review", String.valueOf(summary.getOvertimeReviewCount())}
        }, ink, muted, border));

        if (!reviewReasons.isEmpty()) {
            List<String[]> rows = new ArrayList<>();
            for (String reason : reviewReasons) {
                rows.add(new String[] {"Review", reason});
            }
            cards.addCell(buildInvoicePdfCard("Review Reasons", rows.toArray(new String[0][]), ink, muted, border));
        } else {
            PdfPCell noteCard = new PdfPCell();
            noteCard.setPadding(14f);
            noteCard.setBorderColor(border);
            noteCard.setBackgroundColor(brandGoldSoft);
            Paragraph title = new Paragraph("REVIEW REASONS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, muted));
            title.setSpacingAfter(10f);
            noteCard.addElement(title);
            noteCard.addElement(new Paragraph("No manual review reasons were recorded for the visible invoices.", FontFactory.getFont(FontFactory.HELVETICA, 10, ink)));
            cards.addCell(noteCard);
        }

        document.add(cards);
    }

    private void addParentSummaryPdfFooter(Document document, ParentSummaryRow summary, java.awt.Color muted, java.awt.Color ink) throws Exception {
        LineSeparator line = new LineSeparator();
        line.setPercentage(100f);
        line.setLineWidth(0.8f);
        line.setLineColor(new java.awt.Color(228, 234, 242));
        document.add(Chunk.NEWLINE);
        document.add(line);

        Paragraph footer = new Paragraph(
            "Generated from the Taska Zurah billing ledger as a parent-level billing summary for follow-up and review.",
            FontFactory.getFont(FontFactory.HELVETICA, 10, muted)
        );
        footer.setSpacingBefore(8f);
        document.add(footer);

        Paragraph sign = new Paragraph(firstNonBlank(summary.getParentName(), "Taska Zurah Parent Summary"), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink));
        sign.setAlignment(Element.ALIGN_RIGHT);
        sign.setSpacingBefore(16f);
        document.add(sign);
    }

    private boolean openExportedFile(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        try {
            Desktop.getDesktop().open(file);
            return true;
        } catch (IOException | IllegalArgumentException | SecurityException ignored) {
            return false;
        }
    }

    private boolean printExportedFile(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.PRINT)) {
            return false;
        }
        try {
            desktop.print(file);
            return true;
        } catch (IOException | IllegalArgumentException | SecurityException ignored) {
            return false;
        }
    }

    private String buildExportMessage(String exportLabel, File file, boolean openRequested, boolean opened, boolean printRequested, boolean printed) {
        StringBuilder sb = new StringBuilder();
        sb.append(exportLabel).append(" saved to:\n").append(file.getAbsolutePath());
        if (openRequested) {
            sb.append(opened ? "\n\nOpened with the default application." : "\n\nOpen was requested, but the OS did not launch a viewer.");
        }
        if (printRequested) {
            sb.append(printed ? "\n\nSent to the OS print handler." : "\n\nPrint was requested, but this machine did not expose a printable handler for the file.");
        }
        return sb.toString();
    }

    private String exportBaseName(LedgerRow row) {
        String receipt = "-".equals(row.getReceiptNo()) ? "unpaid" : slug(row.getReceiptNo());
        return "billing-" + slug(row.getInvoiceId()) + "-" + receipt + "-"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
    }

    private List<String> collectReviewReasons(List<LedgerRow> rows) {
        List<String> reasons = new ArrayList<>();
        for (LedgerRow row : rows) {
            if (!row.isManagementReviewRecommended()) {
                continue;
            }
            String reviewReason = firstNonBlank(row.getReviewReason());
            if (reviewReason != null && !reasons.contains(reviewReason)) {
                reasons.add(reviewReason);
            }
        }
        return reasons;
    }

    private String buildCurrentFilterDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(firstNonBlank(statusFilter.getValue(), "All"));
        sb.append(" | Date Scope: ").append(firstNonBlank(dateScopeFilter.getValue(), "All Dates"));
        sb.append(" | Family Risk: ").append(firstNonBlank(riskFilter.getValue(), "All Families"));
        sb.append(" | Review Type: ").append(firstNonBlank(reviewFilter.getValue(), "All Reviews"));
        sb.append(" | Search: ").append(searchField.getText() == null || searchField.getText().isBlank() ? "none" : searchField.getText().trim());
        sb.append(" | Parent Focus: ").append(focusedParentId == null || focusedParentId.isBlank() ? "none" : focusedParentId);
        return sb.toString();
    }

    private boolean matchesReviewFilter(LedgerRow row, String reviewType) {
        String selectedReviewType = firstNonBlank(reviewType, "All Reviews");
        if ("All Reviews".equalsIgnoreCase(selectedReviewType)) {
            return true;
        }
        if ("Review Only".equalsIgnoreCase(selectedReviewType)) {
            return row.isManagementReviewRecommended();
        }
        if ("Age Review".equalsIgnoreCase(selectedReviewType)) {
            return row.isAgeOutOfPolicy();
        }
        if ("Overtime Review".equalsIgnoreCase(selectedReviewType)) {
            return row.isManagementReviewRecommended() && !row.isAgeOutOfPolicy();
        }
        if ("No Review".equalsIgnoreCase(selectedReviewType)) {
            return !row.isManagementReviewRecommended();
        }
        return true;
    }

    private String buildChildNamesText(List<LedgerRow> rows) {
        List<String> childNames = new ArrayList<>();
        for (LedgerRow row : rows) {
            for (String childName : row.getChildNames()) {
                String normalized = firstNonBlank(childName, "-");
                if (!childNames.contains(normalized)) {
                    childNames.add(normalized);
                }
            }
        }
        return childNames.isEmpty() ? "-" : String.join(", ", childNames);
    }

    private List<LedgerRow> rowsForParent(String parentId, List<LedgerRow> rows) {
        List<LedgerRow> parentRows = new ArrayList<>();
        for (LedgerRow row : rows) {
            if (Objects.equals(parentId, row.getParentId())) {
                parentRows.add(row);
            }
        }
        return parentRows;
    }

    private void appendInvoiceItems(StringBuilder sb, Object rawItems) {
        if (!(rawItems instanceof List<?>)) {
            sb.append("- No line items stored for this invoice.\n");
            return;
        }

        List<?> items = (List<?>) rawItems;
        if (items.isEmpty()) {
            sb.append("- No line items stored for this invoice.\n");
            return;
        }

        int index = 1;
        for (Object item : items) {
            if (item instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) item;
                String title = firstNonBlank(
                    stringValue(map.get("title")),
                    stringValue(map.get("description")),
                    stringValue(map.get("label")),
                    stringValue(map.get("code")),
                    "Item " + index
                );
                Long amountSen = longValue(map.get("amountSen"));
                Long quantity = longValue(map.get("qty"));
                if (quantity == null) {
                    quantity = longValue(map.get("quantity"));
                }

                sb.append(index).append(". ").append(title);
                if (quantity != null && quantity > 1) {
                    sb.append(" x").append(quantity);
                }
                if (amountSen != null) {
                    sb.append(" - ").append(formatMoney(amountSen));
                }
                sb.append('\n');
            } else if (item != null) {
                sb.append(index).append(". ").append(item).append('\n');
            }
            index++;
        }
    }

    private List<String> invoicePolicyNotes(FsDocument invoice) {
        Object billingMeta = invoice == null ? null : invoice.get("billingMeta");
        if (!(billingMeta instanceof Map<?, ?>)) {
            return List.of();
        }

        Object raw = ((Map<?, ?>) billingMeta).get("policyNotes");
        if (!(raw instanceof List<?>)) {
            return List.of();
        }

        List<String> notes = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            String text = stringValue(item);
            if (text != null) {
                notes.add(text);
            }
        }
        return notes;
    }

    private boolean invoiceManagementReviewRecommended(FsDocument invoice) {
        Object billingMeta = invoice == null ? null : invoice.get("billingMeta");
        if (!(billingMeta instanceof Map<?, ?>)) {
            return false;
        }

        Object raw = ((Map<?, ?>) billingMeta).get("managementReviewRecommended");
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        return "true".equalsIgnoreCase(String.valueOf(raw));
    }

    private boolean invoiceAgeOutOfPolicy(FsDocument invoice) {
        Object billingMeta = invoice == null ? null : invoice.get("billingMeta");
        if (!(billingMeta instanceof Map<?, ?>)) {
            return false;
        }

        Object raw = ((Map<?, ?>) billingMeta).get("ageOutOfPolicy");
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        return "true".equalsIgnoreCase(String.valueOf(raw));
    }

    private String invoiceReviewReason(FsDocument invoice) {
        if (!invoiceManagementReviewRecommended(invoice)) {
            return "";
        }
        if (invoiceAgeOutOfPolicy(invoice)) {
            return "Child age is outside the PDF fee range and this invoice should be reviewed manually.";
        }
        return "Late-night overtime exceeded the policy threshold and should be reviewed by management.";
    }

    private FsDocument latestPayment(List<FsDocument> payments) {
        if (payments == null || payments.isEmpty()) {
            return null;
        }

        return payments.stream()
            .filter(Objects::nonNull)
            .max(Comparator.comparing(this::paymentSortDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .orElse(null);
    }

    private Date paymentSortDate(FsDocument payment) {
        return firstDate(
            payment.getDate("paidAt"),
            payment.getDate("completedAt"),
            payment.getDate("updatedAt"),
            payment.getDate("createdAt")
        );
    }

    private int countParents(List<LedgerRow> rows) {
        return (int) rows.stream().map(LedgerRow::getParentId).filter(Objects::nonNull).distinct().count();
    }

    private void styleBadge(Label label, String bgColor, String textColor) {
        label.setStyle(
            "-fx-background-color: " + bgColor + ";" +
            "-fx-text-fill: " + textColor + ";" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 6 12 6 12;" +
            "-fx-background-radius: 999;"
        );
    }

    private void configureInteractiveBadge(Label label, String tooltipText, Runnable action) {
        label.setStyle(label.getStyle() + "-fx-cursor: hand;");
        label.setTooltip(new Tooltip(tooltipText));
        label.setOnMouseClicked(event -> action.run());
    }

    private String formatMoney(long sen) {
        return MONEY_FORMAT.format(sen / 100.0);
    }

    private boolean matchesDateScope(LedgerRow row, String scope) {
        String normalized = firstNonBlank(scope, "All Dates");
        if ("All Dates".equalsIgnoreCase(normalized)) {
            return true;
        }

        LocalDate invoiceDate = row.getRelevantDate();
        if (invoiceDate == null) {
            return false;
        }

        LocalDate today = LocalDate.now();
        if ("Current Month".equalsIgnoreCase(normalized)) {
            return invoiceDate.getYear() == today.getYear() && invoiceDate.getMonthValue() == today.getMonthValue();
        }
        if ("Current Year".equalsIgnoreCase(normalized)) {
            return invoiceDate.getYear() == today.getYear();
        }
        return true;
    }

    private String buildPeriodTotalsText(Map<String, Long> outstandingByPeriod) {
        if (outstandingByPeriod.isEmpty()) {
            return "Periods: no unpaid invoices in the current filter.";
        }

        StringBuilder sb = new StringBuilder("Outstanding by period: ");
        boolean first = true;
        for (Map.Entry<String, Long> entry : outstandingByPeriod.entrySet()) {
            if (!first) {
                sb.append(" | ");
            }
            first = false;
            sb.append(entry.getKey()).append(" ").append(formatMoney(entry.getValue()));
        }
        return sb.toString();
    }

    private String parentRiskLevel(int unpaidCount, int overdueCount, long outstandingSen) {
        if (overdueCount >= 2 || outstandingSen >= PARENT_RISK_CRITICAL_OUTSTANDING_SEN || unpaidCount >= 4) {
            return "Critical";
        }
        if (overdueCount >= 1 || outstandingSen >= PARENT_RISK_WATCH_OUTSTANDING_SEN || unpaidCount >= 2) {
            return "Watch";
        }
        return "Clear";
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

    private String parseDocId(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        int idx = reference.lastIndexOf('/');
        return idx >= 0 && idx + 1 < reference.length() ? reference.substring(idx + 1) : reference;
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private Date firstDate(Date... values) {
        for (Date value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
                if (value instanceof String) {
                    return Long.valueOf(((String) value).trim());
                }
                return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long longValueOrZero(Object value) {
        Long parsed = longValue(value);
        return parsed == null ? 0L : parsed;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Map<?, ?> parseCallableResultMap(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return Map.of();
        }
        Map<?, ?> outer = GSON_PRETTY.fromJson(rawBody, Map.class);
        if (outer == null) {
            return Map.of();
        }
        Object result = outer.get("result");
        if (result instanceof Map<?, ?>) {
            return (Map<?, ?>) result;
        }
        return outer;
    }

    private String slug(String value) {
        if (value == null || value.isBlank()) {
            return "record";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String parentSummaryExportBaseName(ParentSummaryRow summary) {
        return "parent-summary-" + slug(summary.getParentName()) + "-" + slug(summary.getParentId()) + "-"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
    }

    private Window getWindow() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private void showSimple(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(getWindow());
        alert.showAndWait();
    }

    private void showError(String context, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Billing Ledger Error");
        alert.setHeaderText(context);
        alert.setContentText(rootMessage(ex));
        alert.initOwner(getWindow());
        alert.showAndWait();
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
            Date createdAt = firstDateStatic(invoice.getDate("createdAt"), invoice.getDate("updatedAt"), dueDate);
            Date paidAt = firstDateStatic(
                invoice.getDate("paidAt"),
                payment == null ? null : payment.getDate("paidAt"),
                payment == null ? null : payment.getDate("completedAt"),
                payment == null ? null : payment.getDate("createdAt")
            );
            long totalSen = longValueOrZero(invoice.getLong("totalSen"));
            String status = resolveStatus(invoice.getString("status"), payment == null ? null : payment.getString("status"), dueDate);
            String receiptNo = firstNonBlankStatic(
                invoice.getString("paidReceiptNo"),
                payment == null ? null : payment.getString("receiptNo"),
                payment == null ? null : payment.getString("providerReceiptNo"),
                "-"
            );
            boolean managementReviewRecommended = invoiceManagementReviewRecommendedStatic(invoice);
            boolean ageOutOfPolicy = invoiceAgeOutOfPolicyStatic(invoice);
            String reviewReason = invoiceReviewReasonStatic(invoice);
            String period = firstNonBlankStatic(invoice.getString("period"), "-");
            List<String> childNames = extractChildNames(invoice);
            String childName = childNames.isEmpty()
                ? firstNonBlankStatic(invoice.getString("childName"), invoice.getString("childId"), "-")
                : String.join(", ", childNames);

            return new LedgerRow(
                parentId,
                invoice.getId(),
                firstNonBlankStatic(parentName, "-"),
                childName,
                childNames,
                period,
                status,
                formatDateStatic(dueDate),
                formatMoneyStatic(totalSen),
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

        public Date getSortDate() {
            return firstDateStatic(dueDate, createdAt, paidAt);
        }

        public LocalDate getRelevantDate() {
            if (period != null && period.matches("\\d{4}-\\d{2}")) {
                try {
                    return LocalDate.parse(period + "-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                } catch (java.time.format.DateTimeParseException ignored) {
                    // Fall back to stored timestamps if the billing period text is malformed.
                }
            }
            Date value = firstDateStatic(dueDate, createdAt, paidAt);
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

        private static List<String> extractChildNames(FsDocument invoice) {
            List<String> names = new ArrayList<>();
            Object rawChildNames = invoice.get("childNames");
            if (rawChildNames instanceof List<?>) {
                for (Object rawName : (List<?>) rawChildNames) {
                    String name = firstNonBlankStatic(stringValueStatic(rawName));
                    if (name != null && !names.contains(name)) {
                        names.add(name);
                    }
                }
            }

            String childNameSummary = firstNonBlankStatic(invoice.getString("childName"), invoice.getString("childId"));
            if (names.isEmpty() && childNameSummary != null) {
                names.add(childNameSummary);
            }
            return names;
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }

        private static String stringValueStatic(Object value) {
            if (value == null) {
                return null;
            }
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : text;
        }

        private static String resolveStatus(String invoiceStatus, String paymentStatus, Date dueDate) {
            String raw = firstNonBlankStatic(paymentStatus, invoiceStatus, "unknown");
            String normalized = raw.toLowerCase(Locale.ROOT);
            if ("paid".equals(normalized) || "succeeded".equals(normalized)) {
                return "Paid";
            }
            if ("failed".equals(normalized)) {
                return "Failed";
            }
            if ("pending".equals(normalized)) {
                return "Pending";
            }
            if ("unpaid".equals(normalized) || "open".equals(normalized) || "unknown".equals(normalized)) {
                if (dueDate != null && dueDate.before(new Date())) {
                    return "Overdue";
                }
                return "Unpaid";
            }
            return normalized.isEmpty()
                ? "Unpaid"
                : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
        }

        private static String formatMoneyStatic(long sen) {
            return MONEY_FORMAT.format(sen / 100.0);
        }

        private static boolean invoiceManagementReviewRecommendedStatic(FsDocument invoice) {
            Object billingMeta = invoice == null ? null : invoice.get("billingMeta");
            if (!(billingMeta instanceof Map<?, ?>)) {
                return false;
            }
            Object raw = ((Map<?, ?>) billingMeta).get("managementReviewRecommended");
            if (raw instanceof Boolean) {
                return (Boolean) raw;
            }
            return "true".equalsIgnoreCase(String.valueOf(raw));
        }

        private static boolean invoiceAgeOutOfPolicyStatic(FsDocument invoice) {
            Object billingMeta = invoice == null ? null : invoice.get("billingMeta");
            if (!(billingMeta instanceof Map<?, ?>)) {
                return false;
            }
            Object raw = ((Map<?, ?>) billingMeta).get("ageOutOfPolicy");
            if (raw instanceof Boolean) {
                return (Boolean) raw;
            }
            return "true".equalsIgnoreCase(String.valueOf(raw));
        }

        private static String invoiceReviewReasonStatic(FsDocument invoice) {
            if (!invoiceManagementReviewRecommendedStatic(invoice)) {
                return "";
            }
            if (invoiceAgeOutOfPolicyStatic(invoice)) {
                return "Child age is outside the PDF fee range and this invoice should be reviewed manually.";
            }
            return "Late-night overtime exceeded the policy threshold and should be reviewed by management.";
        }

        private static String formatDateStatic(Date date) {
            if (date == null) {
                return "-";
            }
            return DATE_FORMAT.format(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        }

        private static Date firstDateStatic(Date... values) {
            for (Date value : values) {
                if (value != null) {
                    return value;
                }
            }
            return null;
        }

        private static String firstNonBlankStatic(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
            return null;
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

        private ParentSummaryRow(
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

    private final class ParentSummaryAccumulator {
        private final String parentId;
        private final String parentName;
        private int invoiceCount;
        private int unpaidCount;
        private int overdueCount;
        private long outstandingSen;
        private int reviewCount;
        private int ageReviewCount;
        private int overtimeReviewCount;
        private final List<String> reviewReasons = new ArrayList<>();
        private Date lastPaidAt;

        private ParentSummaryAccumulator(String parentId, String parentName) {
            this.parentId = parentId;
            this.parentName = parentName;
        }

        private void add(LedgerRow row) {
            invoiceCount++;
            if (row.isPaid()) {
                if (row.getPaidAt() != null && (lastPaidAt == null || row.getPaidAt().after(lastPaidAt))) {
                    lastPaidAt = row.getPaidAt();
                }
            } else {
                unpaidCount++;
                outstandingSen += row.getTotalSen();
            }
            if (row.isOverdue()) {
                overdueCount++;
            }
            if (row.isManagementReviewRecommended()) {
                reviewCount++;
                if (row.isAgeOutOfPolicy()) {
                    ageReviewCount++;
                } else {
                    overtimeReviewCount++;
                }
                String reviewReason = firstNonBlank(row.getReviewReason());
                if (reviewReason != null && !reviewReasons.contains(reviewReason)) {
                    reviewReasons.add(reviewReason);
                }
            }
        }

        private ParentSummaryRow toRow() {
            List<String> reviewSummaryParts = new ArrayList<>();
            if (ageReviewCount > 0) {
                reviewSummaryParts.add(ageReviewCount + " age");
            }
            if (overtimeReviewCount > 0) {
                reviewSummaryParts.add(overtimeReviewCount + " overtime");
            }
            if (!reviewReasons.isEmpty()) {
                reviewSummaryParts.add(String.join("; ", reviewReasons));
            }
            return new ParentSummaryRow(
                parentId,
                parentName,
                invoiceCount,
                unpaidCount,
                overdueCount,
                parentRiskLevel(unpaidCount, overdueCount, outstandingSen),
                outstandingSen,
                formatMoney(outstandingSen),
                reviewCount,
                ageReviewCount,
                overtimeReviewCount,
                reviewCount > 0 ? (reviewCount == 1 ? "Needs Review" : reviewCount + " reviews") : "",
                String.join(" | ", reviewSummaryParts),
                formatDateTime(lastPaidAt),
                lastPaidAt
            );
        }
    }
}