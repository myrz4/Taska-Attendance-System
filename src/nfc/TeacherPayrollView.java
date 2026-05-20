package nfc;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public class TeacherPayrollView extends VBox {
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ms-MY"));

    private final TextField periodField = new TextField();
    private final TextField searchField = SummaryTableSupport.createSearchField("Search teacher name, email, or payroll status");
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final Button refreshButton = new Button("Refresh");
    private final Button generateButton = SummaryTableSupport.createPrimaryButton("Generate Month");
    private final Button markReviewedButton = new Button("Mark Reviewed");
    private final Button markPaidButton = new Button("Mark Paid");
    private final Label teacherCountValue = new Label("0");
    private final Label baseTotalValue = new Label("RM0.00");
    private final Label overtimeTotalValue = new Label("RM0.00");
    private final Label payTotalValue = new Label("RM0.00");
    private final Label policyLabel = new Label("Policy: -");
    private final Label statusLabel = new Label("Load a payroll month to review salary and overtime.");
    private final TableView<TeacherPayrollRemoteSupport.PayrollRecord> table = new TableView<>();
    private final ObservableList<TeacherPayrollRemoteSupport.PayrollRecord> masterRows = FXCollections.observableArrayList();
    private final FilteredList<TeacherPayrollRemoteSupport.PayrollRecord> filteredRows = new FilteredList<>(masterRows, row -> true);
    private final SortedList<TeacherPayrollRemoteSupport.PayrollRecord> sortedRows = new SortedList<>(filteredRows);
    private final TextArea detailsArea = new TextArea();
    private final Map<String, List<TeacherPayrollRemoteSupport.PayrollRecord>> teacherHistoryCache = new HashMap<>();

    private boolean loading;
    private long detailRequestVersion;
    private boolean pendingAttendanceRefresh;
    private String pendingAttendancePeriod = "";

    public TeacherPayrollView() {
        setSpacing(0);
        setFillWidth(true);

        Label subtitleAction = new Label();
        subtitleAction.getStyleClass().add("app-helper-text");

        HBox header = AppThemeSupport.createStandardPageBanner(
            "Salary & Overtime",
            "Generate monthly teacher payroll from base salary plus the shared overtime policy used by attendance and billing.",
            subtitleAction
        );

        periodField.setPromptText("yyyy-MM");
        periodField.setText(LocalDate.now().withDayOfMonth(1).minusMonths(1).format(PERIOD_FORMAT));
        periodField.setPrefColumnCount(7);
        periodField.setPrefWidth(96);

        statusFilter.setItems(FXCollections.observableArrayList("All", "Pending Review", "Reviewed", "Paid"));
        statusFilter.setValue("All");
        statusFilter.setPrefWidth(140);
        statusFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        refreshButton.setOnAction(event -> loadSummary(false));
        generateButton.setOnAction(event -> generatePayroll());
        markReviewedButton.setOnAction(event -> markSelectedReviewed());
        markPaidButton.setOnAction(event -> markSelectedPaid());
        markReviewedButton.setDisable(true);
        markPaidButton.setDisable(true);

        AppThemeSupport.styleControls(periodField, searchField, statusFilter, refreshButton, generateButton, markReviewedButton, markPaidButton);

        FlowPane controlRow = new FlowPane(12, 12,
            labeledControl("Month", periodField),
            labeledControl("Status", statusFilter),
            labeledControl("Search", searchField),
            refreshButton,
            generateButton,
            markReviewedButton,
            markPaidButton
        );
        controlRow.setPadding(new Insets(0, 0, 4, 0));

        VBox controlsCard = AppThemeSupport.createSectionCard(
            "Payroll Controls",
            "Generate one closed month at a time, then review and mark the selected payroll as paid.",
            controlRow,
            policyLabel
        );

        FlowPane kpiStrip = new FlowPane(12, 12,
            AppThemeSupport.createKpiCard("Teachers", teacherCountValue),
            AppThemeSupport.createKpiCard("Base Salary", baseTotalValue),
            AppThemeSupport.createKpiCard("Overtime", overtimeTotalValue),
            AppThemeSupport.createKpiCard("Total Payroll", payTotalValue)
        );
        kpiStrip.setPrefWrapLength(920);

        configureTable();
        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setPrefRowCount(18);
        detailsArea.setText("Select a payroll row to inspect the monthly breakdown.");

        VBox tableCard = AppThemeSupport.createSectionCard(
            "Monthly Payroll",
            "Each row is a generated monthly payroll record for one active teacher.",
            table
        );
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox detailsCard = AppThemeSupport.createSectionCard(
            "Details",
            "Inspect the selected teacher's base salary, overtime blocks, payout status, and daily overtime breakdown.",
            detailsArea
        );
        VBox.setVgrow(detailsArea, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(tableCard, detailsCard);
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.setDividerPositions(0.58);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        VBox body = new VBox(16,
            controlsCard,
            kpiStrip,
            splitPane,
            statusLabel
        );
        body.setPadding(new Insets(18));
        body.setFillWidth(true);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        BorderPane shell = AppThemeSupport.createStandardPageShell(
            header,
            AppThemeSupport.createPageBodyScrollWrapper(body)
        );

        getChildren().setAll(shell);
        VBox.setVgrow(shell, Priority.ALWAYS);

        loadSummary(true);
    }

    public void onShow() {
        loadSummary(true);
    }

    public void refreshAfterAttendanceMutation(String affectedPeriod) {
        String displayedPeriod = displayedPeriod();
        if (affectedPeriod != null && !affectedPeriod.isBlank() && !affectedPeriod.equals(displayedPeriod)) {
            return;
        }
        if (loading) {
            pendingAttendanceRefresh = true;
            pendingAttendancePeriod = affectedPeriod == null ? "" : affectedPeriod;
            return;
        }
        loadSummary(false);
    }

    private void configureTable() {
        TableColumn<TeacherPayrollRemoteSupport.PayrollRecord, String> teacherCol = new TableColumn<>("Teacher");
        teacherCol.setPrefWidth(220);
        teacherCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().teacherName));

        TableColumn<TeacherPayrollRemoteSupport.PayrollRecord, String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(120);
        statusCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(displayStatus(data.getValue().status)));

        TableColumn<TeacherPayrollRemoteSupport.PayrollRecord, String> baseCol = new TableColumn<>("Base Salary");
        baseCol.setPrefWidth(130);
        baseCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(formatMoney(data.getValue().baseSalarySen)));

        TableColumn<TeacherPayrollRemoteSupport.PayrollRecord, String> overtimeCol = new TableColumn<>("Overtime");
        overtimeCol.setPrefWidth(120);
        overtimeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(formatMoney(data.getValue().overtimeTotalSen)));

        TableColumn<TeacherPayrollRemoteSupport.PayrollRecord, String> totalCol = new TableColumn<>("Total Pay");
        totalCol.setPrefWidth(130);
        totalCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(formatMoney(data.getValue().totalPaySen)));

        TableColumn<TeacherPayrollRemoteSupport.PayrollRecord, String> dayCountCol = new TableColumn<>("OT Days");
        dayCountCol.setPrefWidth(90);
        dayCountCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.valueOf(data.getValue().overtimeDayCount)));

        table.getColumns().clear();
        table.getColumns().add(teacherCol);
        table.getColumns().add(statusCol);
        table.getColumns().add(baseCol);
        table.getColumns().add(overtimeCol);
        table.getColumns().add(totalCol);
        table.getColumns().add(dayCountCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(AppThemeSupport.createEmptyState("No payroll records", "Generate or refresh a month to load payroll records."));
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            updateActionState(newValue);
            updateDetails(newValue);
        });
        sortedRows.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedRows);
    }

    private VBox labeledControl(String labelText, javafx.scene.Node control) {
        Label label = new Label(labelText == null ? "" : labelText);
        label.getStyleClass().add("app-form-label");
        VBox box = new VBox(6, label, control);
        VBox.setVgrow(control, Priority.NEVER);
        if (control instanceof TextField) {
            ((TextField) control).setMaxWidth(Double.MAX_VALUE);
        }
        if (control instanceof ComboBox<?>) {
            ((ComboBox<?>) control).setMaxWidth(Double.MAX_VALUE);
        }
        return box;
    }

    private void loadSummary(boolean generateIfMissing) {
        String period = normalizedPeriod();
        if (period == null) {
            return;
        }
        String selectedPayrollId = selectedPayrollId();
        setBusy(true, generateIfMissing ? "Generating payroll..." : "Loading payroll summary...");
        CompletableFuture.supplyAsync(() -> TeacherPayrollRemoteSupport.loadSummary(period, generateIfMissing))
            .whenComplete((summary, error) -> Platform.runLater(() -> {
                if (error != null) {
                    setBusy(false, "Payroll summary failed to load.");
                    AppThemeSupport.showError(window(), "Payroll Load Failed", rootMessage(error));
                    return;
                }
                applySummary(summary, selectedPayrollId);
                setBusy(false, summary.message);
            }));
    }

    private void generatePayroll() {
        String period = normalizedPeriod();
        if (period == null) {
            return;
        }
        setBusy(true, "Generating payroll...");
        CompletableFuture.supplyAsync(() -> TeacherPayrollRemoteSupport.generateMonthlyPayroll(period))
            .whenComplete((summary, error) -> Platform.runLater(() -> {
                if (error != null) {
                    setBusy(false, "Payroll generation failed.");
                    AppThemeSupport.showError(window(), "Payroll Generation Failed", rootMessage(error));
                    return;
                }
                applySummary(summary, selectedPayrollId());
                setBusy(false, summary.message);
            }));
    }

    private void markSelectedReviewed() {
        TeacherPayrollRemoteSupport.PayrollRecord selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!AppThemeSupport.showConfirm(
            window(),
            "Mark Payroll Reviewed",
            "Review the selected monthly payroll",
            "Mark payroll for " + selected.teacherName + " as reviewed?",
            AppThemeSupport.Tone.INFO,
            "Mark Reviewed",
            "Cancel"
        )) {
            return;
        }

        setBusy(true, "Marking payroll as reviewed...");
        CompletableFuture.supplyAsync(() -> TeacherPayrollRemoteSupport.markReviewed(selected.payrollId, ""))
            .whenComplete((record, error) -> Platform.runLater(() -> {
                if (error != null) {
                    setBusy(false, "Review action failed.");
                    AppThemeSupport.showError(window(), "Mark Reviewed Failed", rootMessage(error));
                    return;
                }
                loadSummary(false);
            }));
    }

    private void markSelectedPaid() {
        TeacherPayrollRemoteSupport.PayrollRecord selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog(selected.paymentReference);
        dialog.initOwner(window());
        dialog.setTitle("Mark Payroll Paid");
        dialog.setHeaderText("Record payment reference for " + selected.teacherName);
        dialog.setContentText("Payment Reference:");
        Optional<String> response = dialog.showAndWait();
        if (!response.isPresent()) {
            return;
        }

        String paymentReference = response.get() == null ? "" : response.get().trim();
        setBusy(true, "Marking payroll as paid...");
        CompletableFuture.supplyAsync(() -> TeacherPayrollRemoteSupport.markPaid(selected.payrollId, paymentReference, ""))
            .whenComplete((record, error) -> Platform.runLater(() -> {
                if (error != null) {
                    setBusy(false, "Mark paid action failed.");
                    AppThemeSupport.showError(window(), "Mark Paid Failed", rootMessage(error));
                    return;
                }
                loadSummary(false);
            }));
    }

    private void applySummary(TeacherPayrollRemoteSupport.PayrollSummary summary, String selectedPayrollId) {
        teacherHistoryCache.clear();
        masterRows.setAll(summary.payrolls);
        teacherCountValue.setText(String.valueOf(summary.teacherCount));
        baseTotalValue.setText(formatMoney(summary.totalBaseSen));
        overtimeTotalValue.setText(formatMoney(summary.totalOvertimeSen));
        payTotalValue.setText(formatMoney(summary.totalPaySen));

        if (summary.policy != null) {
            policyLabel.setText(String.format(
                Locale.ROOT,
                "Policy: %s | Weekday %s / 30 min after %s | Saturday %s / 30 min after %s",
                blankFallback(summary.policy.policyVersion, "-") ,
                formatMoney(summary.policy.weekdayHalfHourRateSen),
                blankFallback(summary.policy.weekdayClosingTimeLabel, "-"),
                formatMoney(summary.policy.saturdayHalfHourRateSen),
                blankFallback(summary.policy.saturdayClosingTimeLabel, "-")
            ));
        } else {
            policyLabel.setText("Policy: -");
        }

        applyFilters();
        reselect(selectedPayrollId);
        if (table.getSelectionModel().getSelectedItem() == null && !masterRows.isEmpty()) {
            table.getSelectionModel().selectFirst();
        }
        updateActionState(table.getSelectionModel().getSelectedItem());
        updateDetails(table.getSelectionModel().getSelectedItem());
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String status = statusFilter.getValue() == null ? "All" : statusFilter.getValue();
        filteredRows.setPredicate(row -> {
            if (row == null) {
                return false;
            }
            boolean matchesStatus;
            switch (String.valueOf(status)) {
                case "Pending Review":
                    matchesStatus = "PENDING_REVIEW".equalsIgnoreCase(row.status);
                    break;
                case "Reviewed":
                    matchesStatus = "REVIEWED".equalsIgnoreCase(row.status);
                    break;
                case "Paid":
                    matchesStatus = "PAID".equalsIgnoreCase(row.status);
                    break;
                default:
                    matchesStatus = true;
                    break;
            }
            if (!matchesStatus) {
                return false;
            }
            if (search.isEmpty()) {
                return true;
            }
            return row.teacherName.toLowerCase(Locale.ROOT).contains(search)
                || row.teacherEmail.toLowerCase(Locale.ROOT).contains(search)
                || displayStatus(row.status).toLowerCase(Locale.ROOT).contains(search);
        });
    }

    private void updateDetails(TeacherPayrollRemoteSupport.PayrollRecord row) {
        if (row == null) {
            detailRequestVersion += 1;
            detailsArea.setText("Select a payroll row to inspect the monthly breakdown.");
            return;
        }

        List<TeacherPayrollRemoteSupport.PayrollRecord> history = teacherHistoryCache.get(row.teacherId);
        boolean loadingHistory = history == null && !row.teacherId.isBlank();
        detailsArea.setText(buildDetailsText(row, history == null ? Collections.emptyList() : history, loadingHistory, ""));
        if (loadingHistory) {
            loadTeacherHistory(row);
        }
    }

    private void loadTeacherHistory(TeacherPayrollRemoteSupport.PayrollRecord row) {
        if (row == null || row.teacherId == null || row.teacherId.isBlank()) {
            return;
        }

        final long requestVersion = ++detailRequestVersion;
        final String teacherId = row.teacherId;
        final String payrollId = row.payrollId;
        CompletableFuture.supplyAsync(() -> TeacherPayrollRemoteSupport.loadTeacherHistory(teacherId, 12))
            .whenComplete((history, error) -> Platform.runLater(() -> {
                TeacherPayrollRemoteSupport.PayrollRecord selected = table.getSelectionModel().getSelectedItem();
                if (requestVersion != detailRequestVersion || selected == null) {
                    return;
                }
                if (!teacherId.equals(selected.teacherId) || !payrollId.equals(selected.payrollId)) {
                    return;
                }
                if (error != null) {
                    detailsArea.setText(buildDetailsText(selected, Collections.emptyList(), false, rootMessage(error)));
                    return;
                }
                teacherHistoryCache.put(teacherId, history == null ? Collections.emptyList() : history);
                detailsArea.setText(buildDetailsText(selected, teacherHistoryCache.get(teacherId), false, ""));
            }));
    }

    private String buildDetailsText(
        TeacherPayrollRemoteSupport.PayrollRecord row,
        List<TeacherPayrollRemoteSupport.PayrollRecord> teacherHistory,
        boolean loadingHistory,
        String historyError
    ) {
        StringBuilder details = new StringBuilder();
        appendPayrollDetails(details, row, true);
        details.append('\n');
        details.append("Teacher Monthly History").append('\n');

        if (historyError != null && !historyError.isBlank()) {
            details.append("- Unable to load teacher month history: ").append(historyError);
            return details.toString();
        }
        if (loadingHistory) {
            details.append("- Loading teacher month history...");
            return details.toString();
        }
        if (teacherHistory == null || teacherHistory.isEmpty()) {
            details.append("- No payroll history available for this teacher.");
            return details.toString();
        }

        for (TeacherPayrollRemoteSupport.PayrollRecord historyRow : teacherHistory) {
            appendHistoryDetails(details, historyRow, row.payrollId.equals(historyRow.payrollId));
        }
        return details.toString();
    }

    private void appendPayrollDetails(StringBuilder details, TeacherPayrollRemoteSupport.PayrollRecord row, boolean includeBreakdown) {
        details.append("Teacher: ").append(row.teacherName);
        if (!row.teacherEmail.isBlank()) {
            details.append(" (").append(row.teacherEmail).append(")");
        }
        details.append('\n');
        details.append("Payroll Month: ").append(blankFallback(row.periodLabel, blankFallback(row.period, "-"))).append('\n');
        details.append("Status: ").append(displayStatus(row.status)).append('\n');
        details.append("Base Salary: ").append(formatMoney(row.baseSalarySen)).append('\n');
        details.append("Overtime: ").append(formatMoney(row.overtimeTotalSen)).append(" | Days: ").append(row.overtimeDayCount)
            .append(" | Blocks: ").append(row.totalBlocks).append('\n');
        details.append("Total Pay: ").append(formatMoney(row.totalPaySen)).append('\n');
        details.append("Weekday Blocks: ").append(row.weekdayBlocks).append(" | Saturday Blocks: ").append(row.saturdayBlocks).append('\n');
        details.append("Joined Date: ").append(blankFallback(row.joinedDate, "-")).append('\n');
        details.append("Reviewed At: ").append(displayDateTime(row.reviewedAt)).append('\n');
        details.append("Paid At: ").append(displayDateTime(row.paidAt)).append('\n');
        details.append("Payment Reference: ").append(blankFallback(row.paymentReference, "-")).append('\n');
        if (!row.paymentNote.isBlank()) {
            details.append("Payment Note: ").append(row.paymentNote).append('\n');
        }
        if (!includeBreakdown) {
            return;
        }
        details.append('\n');
        details.append("Daily Overtime Breakdown").append('\n');
        appendDailyEntries(details, row.overtimeEntries);
    }

    private void appendHistoryDetails(StringBuilder details, TeacherPayrollRemoteSupport.PayrollRecord row, boolean selected) {
        details.append(selected ? "* " : "- ")
            .append(blankFallback(row.periodLabel, blankFallback(row.period, "-")))
            .append(" | ")
            .append(displayStatus(row.status))
            .append(" | Base ")
            .append(formatMoney(row.baseSalarySen))
            .append(" | OT ")
            .append(formatMoney(row.overtimeTotalSen))
            .append(" | Total ")
            .append(formatMoney(row.totalPaySen))
            .append(" | OT Days ")
            .append(row.overtimeDayCount)
            .append(" | Blocks ")
            .append(row.totalBlocks)
            .append('\n');
        if (row.overtimeEntries.isEmpty()) {
            details.append("    - No overtime days recorded for this month.").append('\n');
            return;
        }
        for (TeacherPayrollRemoteSupport.DailyEntry entry : row.overtimeEntries) {
            details.append("    - ")
                .append(blankFallback(entry.dateKey, "-"))
                .append(" | ")
                .append(blankFallback(entry.dayType, "-"))
                .append(" | ")
                .append(entry.blocks)
                .append(" x 30 min | ")
                .append(formatMoney(entry.totalSen))
                .append(" | latest checkout ")
                .append(displayDateTime(entry.latestCheckoutAt))
                .append(" | closing ")
                .append(blankFallback(entry.closingTimeLabel, "-"));
            if (!entry.latestChildName.isBlank()) {
                details.append(" | child ").append(entry.latestChildName);
            }
            details.append('\n');
        }
    }

    private void appendDailyEntries(StringBuilder details, List<TeacherPayrollRemoteSupport.DailyEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            details.append("- No overtime days recorded for this month.");
            return;
        }
        for (TeacherPayrollRemoteSupport.DailyEntry entry : entries) {
            details.append("- ").append(blankFallback(entry.dateKey, "-")).append(" | ")
                .append(blankFallback(entry.dayType, "-")).append(" | ")
                .append(entry.blocks).append(" x 30 min | ")
                .append(formatMoney(entry.totalSen)).append(" | latest checkout ")
                .append(displayDateTime(entry.latestCheckoutAt)).append(" | closing ")
                .append(blankFallback(entry.closingTimeLabel, "-"));
            if (!entry.latestChildName.isBlank()) {
                details.append(" | child ").append(entry.latestChildName);
            }
            details.append('\n');
        }
    }

    private void updateActionState(TeacherPayrollRemoteSupport.PayrollRecord row) {
        if (row == null || loading) {
            markReviewedButton.setDisable(true);
            markPaidButton.setDisable(true);
            return;
        }
        markReviewedButton.setDisable("REVIEWED".equalsIgnoreCase(row.status) || "PAID".equalsIgnoreCase(row.status));
        markPaidButton.setDisable("PAID".equalsIgnoreCase(row.status));
    }

    private void setBusy(boolean busy, String message) {
        loading = busy;
        refreshButton.setDisable(busy);
        generateButton.setDisable(busy);
        periodField.setDisable(busy);
        searchField.setDisable(busy);
        statusFilter.setDisable(busy);
        updateActionState(table.getSelectionModel().getSelectedItem());
        statusLabel.setText(message == null ? "" : message);
        if (!busy) {
            flushPendingAttendanceRefresh();
        }
    }

    private void flushPendingAttendanceRefresh() {
        if (!pendingAttendanceRefresh || loading) {
            return;
        }

        String affectedPeriod = pendingAttendancePeriod;
        pendingAttendanceRefresh = false;
        pendingAttendancePeriod = "";

        String displayedPeriod = displayedPeriod();
        if (!affectedPeriod.isBlank() && !affectedPeriod.equals(displayedPeriod)) {
            return;
        }
        Platform.runLater(() -> loadSummary(false));
    }

    private void reselect(String payrollId) {
        if (payrollId == null || payrollId.isBlank()) {
            return;
        }
        for (TeacherPayrollRemoteSupport.PayrollRecord row : masterRows) {
            if (payrollId.equals(row.payrollId)) {
                table.getSelectionModel().select(row);
                table.scrollTo(row);
                return;
            }
        }
    }

    private String selectedPayrollId() {
        TeacherPayrollRemoteSupport.PayrollRecord selected = table.getSelectionModel().getSelectedItem();
        return selected == null ? "" : selected.payrollId;
    }

    private String normalizedPeriod() {
        String raw = periodField.getText() == null ? "" : periodField.getText().trim();
        if (raw.matches("\\d{4}-\\d{2}")) {
            return raw;
        }
        AppThemeSupport.showWarning(window(), "Invalid Month", "Use YYYY-MM, for example 2026-05.");
        return null;
    }

    private String displayedPeriod() {
        String raw = periodField.getText() == null ? "" : periodField.getText().trim();
        return raw.matches("\\d{4}-\\d{2}") ? raw : "";
    }

    private Window window() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private String formatMoney(long sen) {
        return MONEY_FORMAT.format(sen / 100.0);
    }

    private String displayStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "PENDING_REVIEW":
                return "Pending Review";
            case "REVIEWED":
                return "Reviewed";
            case "PAID":
                return "Paid";
            default:
                return normalized.isEmpty() ? "-" : normalized;
        }
    }

    private String displayDateTime(String iso) {
        if (iso == null || iso.isBlank()) {
            return "-";
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(iso), ZoneId.systemDefault()).format(DATE_TIME_FORMAT);
        } catch (Exception ignored) {
            return iso;
        }
    }

    private String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? "Unexpected error." : message;
    }
}