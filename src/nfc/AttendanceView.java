package nfc;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.util.Duration;

public class AttendanceView {
	private static AttendanceView currentInstance;
    private static volatile boolean pendingRefresh = false;
    private static volatile boolean pendingChildrenRefresh = false;
    private static final String EMPTY_ATTENDANCE_FINGERPRINT = "<empty>";

    private static void logError(String context, Exception error) {
        System.err.println("AttendanceView: " + context + " - " + error.getMessage());
    }
    private javafx.scene.layout.VBox root;
    private final javafx.scene.control.TableView<AttendanceRecord> table;
    private final javafx.collections.ObservableList<AttendanceRecord> masterRecords = javafx.collections.FXCollections.observableArrayList();
    private final javafx.collections.transformation.FilteredList<AttendanceRecord> filteredRecords = new javafx.collections.transformation.FilteredList<>(masterRecords, p -> true);
    private javafx.scene.chart.PieChart chart;
    // ─── NEW: datePicker field ────────────────────────────────────────────────────
    private DatePicker datePicker;
    // ────────────────────────────────────────────────────────────────────────────────
    private static final java.time.format.DateTimeFormatter DB_TIMESTAMP_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AttendanceDataSupport.AttendanceDataCache attendanceDataCache = AttendanceDataSupport.AttendanceDataCache.empty();
    private boolean active = false;
    private boolean attendanceLoadInFlight = false;
    private long lastChartUpdate = 0;
    private String selectedReasonFilter = "All";
    private String selectedAuditFilter = "All Records";
    private String lastAttendanceFingerprint = EMPTY_ATTENDANCE_FINGERPRINT;
    private final java.util.concurrent.atomic.AtomicLong attendanceLoadVersion = new java.util.concurrent.atomic.AtomicLong();
    private final Label selectionCountLabel;
    private final Timeline realtimeRefreshTimeline;
    private final java.util.List<javafx.scene.control.Control> attendanceActionControls = new java.util.ArrayList<>();
    private final Label tablePlaceholderLabel = new Label("No attendance records for this date.");
    private Button manageClosedDayButton;
    private Button refreshChartButton;
    private Label chartTitleLabel;
    private Label closedDayBanner;
    private AttendanceClosedDaySupport.DayStatus selectedDayStatus = AttendanceClosedDaySupport.DayStatus.open(java.time.LocalDate.now());

    public AttendanceView() {
        currentInstance = this;
        active = true;

        javafx.scene.layout.HBox dashboardHeader = AttendanceViewLayoutSupport.createDashboardHeader();
        
        root = new javafx.scene.layout.VBox(10);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #86d67f 0%, #76cc6e 100%);");

        AttendanceViewLayoutSupport.DateControls dateControls = AttendanceViewLayoutSupport.createDateControls(java.time.LocalDate.now());
        datePicker = dateControls.datePicker;
        Button loadBtn = dateControls.loadButton;
        manageClosedDayButton = dateControls.closedDayButton;
        loadBtn.setOnAction(e -> {
            java.time.LocalDate selectedDate = datePicker.getValue();
            loadStudents(selectedDate);
        });
        manageClosedDayButton.setDisable(!UserSession.isAdmin());
        manageClosedDayButton.setOnAction(e -> handleClosedDayToggle());

        root.getChildren().add(dateControls.bar);

        closedDayBanner = new Label();
        closedDayBanner.setWrapText(true);
        closedDayBanner.setVisible(false);
        closedDayBanner.setManaged(false);
        closedDayBanner.setStyle("-fx-background-color: rgba(255, 248, 214, 0.95); -fx-text-fill: #6b4f00; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10 14 10 14; -fx-background-radius: 16px; -fx-border-color: rgba(161, 117, 0, 0.25); -fx-border-radius: 16px;");
        root.getChildren().add(closedDayBanner);

        AttendanceViewLayoutSupport.FilterToolbar filterToolbar = AttendanceViewLayoutSupport.createFilterToolbar();
        javafx.scene.layout.HBox header = filterToolbar.header;
        ComboBox<String> reasonDropdown = filterToolbar.reasonDropdown;
        reasonDropdown.setOnAction(e -> {
            selectedReasonFilter = reasonDropdown.getValue();
            applyTableFilters();
        });

        ComboBox<String> auditDropdown = filterToolbar.auditDropdown;
        auditDropdown.setOnAction(e -> {
            selectedAuditFilter = auditDropdown.getValue();
            applyTableFilters();
        });

        Button clearFilter = filterToolbar.clearFilter;
        clearFilter.setOnAction(e -> {
            reasonDropdown.setValue("All");
            auditDropdown.setValue("All Records");
            selectedReasonFilter = "All";
            selectedAuditFilter = "All Records";
            applyTableFilters();
        });

        selectionCountLabel = filterToolbar.selectionCountLabel;
        filterToolbar.selectAllBtn.setOnAction(e -> setSelectedVisibleRecords(true));
        filterToolbar.clearSelectionBtn.setOnAction(e -> clearSelectedRecords());

        Button manualCheckInBtn = filterToolbar.manualCheckInBtn;
        manualCheckInBtn.setOnAction(e -> openOverrideDialog("MANUAL_CHECK_IN"));

        Button manualCheckOutBtn = filterToolbar.manualCheckOutBtn;
        manualCheckOutBtn.setOnAction(e -> openOverrideDialog("MANUAL_CHECK_OUT"));

        Button markAbsentBtn = filterToolbar.markAbsentBtn;
        markAbsentBtn.setOnAction(e -> openOverrideDialog("MARK_ABSENT"));

        Button editRecordBtn = filterToolbar.editRecordBtn;
        editRecordBtn.setOnAction(e -> openOverrideDialog("EDIT_RECORD"));

        Button viewAuditBtn = filterToolbar.viewAuditBtn;
        viewAuditBtn.setOnAction(e -> showAttendanceAuditDialog());

        attendanceActionControls.add(filterToolbar.selectAllBtn);
        attendanceActionControls.add(filterToolbar.clearSelectionBtn);
        attendanceActionControls.add(filterToolbar.manualCheckInBtn);
        attendanceActionControls.add(filterToolbar.manualCheckOutBtn);
        attendanceActionControls.add(filterToolbar.markAbsentBtn);
        attendanceActionControls.add(filterToolbar.editRecordBtn);
        attendanceActionControls.add(filterToolbar.viewAuditBtn);
        attendanceActionControls.add(filterToolbar.reasonDropdown);
        attendanceActionControls.add(filterToolbar.auditDropdown);
        attendanceActionControls.add(filterToolbar.clearFilter);

        table = new javafx.scene.control.TableView<>();
        AttendanceTableSupport.configureTable(
            table,
            this::showAttendanceAuditDialog,
            () -> root == null || root.getScene() == null ? null : root.getScene().getWindow()
        );
        table.setItems(filteredRecords);
        tablePlaceholderLabel.setWrapText(true);
        tablePlaceholderLabel.setStyle("-fx-text-fill: #6b4f00; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 16 12 16 12;");
        table.setPlaceholder(tablePlaceholderLabel);

        chartTitleLabel = new Label("Today's Attendance");
        chartTitleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        chart = new javafx.scene.chart.PieChart();
        chart.setAnimated(true);
        chart.setLabelsVisible(true);
        chart.setLegendVisible(true);
        chart.setPrefHeight(320);
        chart.setMinHeight(320);
        javafx.scene.layout.VBox.setMargin(chart, new Insets(12, 0, 4, 0));
        javafx.scene.layout.VBox.setMargin(chartTitleLabel, new Insets(8, 0, 0, 0));

        refreshChartButton = AttendanceViewLayoutSupport.createActionButton("Refresh Chart");
        refreshChartButton.setOnAction(e -> updateChart(chart));
        javafx.scene.layout.VBox.setMargin(refreshChartButton, new Insets(0, 0, 8, 0));

        root.getChildren().addAll(header, table, chartTitleLabel, chart, refreshChartButton);

        javafx.scene.layout.BorderPane mainLayout = new javafx.scene.layout.BorderPane();
        mainLayout.setTop(dashboardHeader);
        mainLayout.setCenter(root);

        this.root = new javafx.scene.layout.VBox();
        this.root.setFillWidth(true);
        this.root.setStyle("-fx-background-color: linear-gradient(to bottom right, #86d67f 0%, #76cc6e 100%);");
        javafx.scene.layout.VBox.setVgrow(mainLayout, javafx.scene.layout.Priority.ALWAYS);
        this.root.getChildren().add(mainLayout);

        realtimeRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(2), event -> {
            if (!active || datePicker == null) {
                return;
            }
            java.time.LocalDate selectedDate = datePicker.getValue();
            if (selectedDate != null && java.time.LocalDate.now().equals(selectedDate)) {
                requestAttendanceRefresh(selectedDate);
            }
        }));
        realtimeRefreshTimeline.setCycleCount(Timeline.INDEFINITE);

        applyDayStatusUi(selectedDayStatus);

        Platform.runLater(() -> loadStudents(datePicker.getValue()));

        datePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate != null) {
                loadStudents(newDate);
            }
            updateRealtimeRefreshState();
        });

        updateRealtimeRefreshState();
    }

    // ─── NEW: Revised loadStudents() with MIN/MAX ───────────────────────────────
    // Change method to accept date parameter
    private void loadStudents(java.time.LocalDate date) {
        if (!UserSession.isLoggedIn() || date == null) {
            masterRecords.clear();
            selectedDayStatus = AttendanceClosedDaySupport.DayStatus.open(date == null ? java.time.LocalDate.now() : date);
            applyDayStatusUi(selectedDayStatus);
            lastAttendanceFingerprint = EMPTY_ATTENDANCE_FINGERPRINT;
            updateSelectionCountLabel();
            table.refresh();
            updateChart(chart);
            applyTableFilters();
            return;
        }

        if (attendanceLoadInFlight) {
            pendingRefresh = true;
            return;
        }

        pendingRefresh = false;
        attendanceLoadInFlight = true;
        final long requestVersion = attendanceLoadVersion.incrementAndGet();
        final AttendanceDataSupport.AttendanceDataCache cacheSnapshot = attendanceDataCache;

        java.util.concurrent.CompletableFuture
            .supplyAsync(() -> {
                AttendanceClosedDaySupport.DayStatus dayStatus = AttendanceClosedDaySupport.safeFetchDayStatus(date);
                if (dayStatus.closed()) {
                    AttendanceDataSupport.AttendanceDataCache clearedCache = cacheSnapshot == null
                        ? AttendanceDataSupport.AttendanceDataCache.empty()
                        : cacheSnapshot.clearAttendance();
                    return AttendanceLoadSnapshot.closed(dayStatus, clearedCache);
                }
                try {
                    return AttendanceLoadSnapshot.open(dayStatus, AttendanceDataSupport.loadRecords(date, cacheSnapshot));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .whenComplete((result, error) -> Platform.runLater(() -> {
                attendanceLoadInFlight = false;
                if (requestVersion != attendanceLoadVersion.get()) {
                    return;
                }

                if (error != null) {
                    logError("load students failed", error instanceof Exception ? (Exception) error : new RuntimeException(error));
                    if (pendingRefresh) {
                        requestPendingAttendanceRefresh(date);
                    }
                    return;
                }

                selectedDayStatus = result.dayStatus;
                applyDayStatusUi(result.dayStatus);
                attendanceDataCache = result.cache;
                String nextFingerprint = fingerprintRecords(result.records);
                if (nextFingerprint.equals(lastAttendanceFingerprint)) {
                    if (pendingRefresh) {
                        requestPendingAttendanceRefresh(date);
                    }
                    return;
                }

                java.util.Set<String> selectedChildIds = selectedChildDocIds();
                restoreSelectionState(result.records, selectedChildIds);
                lastAttendanceFingerprint = nextFingerprint;
                masterRecords.setAll(result.records);
                attachSelectionListeners(result.records);
                updateSelectionCountLabel();
                table.refresh();
                updateChart(chart);
                applyTableFilters();

                if (pendingRefresh) {
                    requestPendingAttendanceRefresh(date);
                }
            }));
    }

    private void applyDayStatusUi(AttendanceClosedDaySupport.DayStatus dayStatus) {
        AttendanceClosedDaySupport.DayStatus resolvedStatus = dayStatus == null
            ? AttendanceClosedDaySupport.DayStatus.open(datePicker == null ? java.time.LocalDate.now() : datePicker.getValue())
            : dayStatus;
        selectedDayStatus = resolvedStatus;

        boolean closed = resolvedStatus.closed();
        if (closed) {
            clearSelectedRecords();
        }
        setAttendanceActionsDisabled(closed);

        tablePlaceholderLabel.setText(closed
            ? resolvedStatus.message()
            : "No attendance records for this date.");

        if (closedDayBanner != null) {
            closedDayBanner.setText(closed ? resolvedStatus.message() : "");
            closedDayBanner.setVisible(closed);
            closedDayBanner.setManaged(closed);
        }

        if (chartTitleLabel != null) {
            chartTitleLabel.setText(closed ? "Attendance Unavailable" : "Today's Attendance");
        }

        if (chart != null) {
            chart.setVisible(!closed);
            chart.setManaged(!closed);
            if (closed) {
                chart.setData(javafx.collections.FXCollections.observableArrayList());
            }
        }

        if (refreshChartButton != null) {
            refreshChartButton.setDisable(closed);
            refreshChartButton.setVisible(!closed);
            refreshChartButton.setManaged(!closed);
        }

        if (manageClosedDayButton != null) {
            manageClosedDayButton.setText(resolvedStatus.actionButtonText());
            manageClosedDayButton.setDisable(!UserSession.isAdmin());
        }
    }

    private void setAttendanceActionsDisabled(boolean disabled) {
        for (javafx.scene.control.Control control : attendanceActionControls) {
            if (control != null) {
                control.setDisable(disabled);
            }
        }
    }

    private void handleClosedDayToggle() {
        if (datePicker == null || !UserSession.isAdmin()) {
            return;
        }

        java.time.LocalDate selectedDate = datePicker.getValue();
        if (selectedDate == null) {
            return;
        }

        AttendanceClosedDaySupport.DayStatus currentStatus = selectedDayStatus == null
            ? AttendanceClosedDaySupport.safeFetchDayStatus(selectedDate)
            : selectedDayStatus;
        boolean closeDay = !currentStatus.customClosed();
        javafx.stage.Window owner = root == null || root.getScene() == null ? null : root.getScene().getWindow();
        String title = closeDay ? "Set Closed Day" : "Reopen Day";
        String message = closeDay
            ? "Close attendance for " + selectedDate + "? The attendance list will be blank and check-in will be blocked for this date."
            : "Reopen attendance for " + selectedDate + "? The attendance list will load normally for this date again.";

        if (!AppThemeSupport.showConfirm(owner, title, null, message, AppThemeSupport.Tone.WARNING, title, "Cancel")) {
            return;
        }

        manageClosedDayButton.setDisable(true);
        java.util.concurrent.CompletableFuture
            .supplyAsync(() -> {
                try {
                    return AttendanceClosedDaySupport.setDayClosed(selectedDate, closeDay);
                } catch (java.io.IOException ex) {
                    throw new java.io.UncheckedIOException(ex);
                }
            })
            .whenComplete((status, error) -> Platform.runLater(() -> {
                if (error != null) {
                    Throwable failure = error;
                    if (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) {
                        failure = failure.getCause();
                    }
                    if (failure instanceof java.io.UncheckedIOException && failure.getCause() != null) {
                        failure = failure.getCause();
                    }
                    manageClosedDayButton.setDisable(!UserSession.isAdmin());
                    AppThemeSupport.showError(owner, title, String.valueOf(failure.getMessage()));
                    return;
                }

                selectedDayStatus = status;
                loadStudents(selectedDate);
                AppThemeSupport.showToast(
                    owner,
                    closeDay ? "Closed Day Saved" : "Day Reopened",
                    closeDay
                        ? selectedDate + " is now closed for attendance."
                        : selectedDate + " is available for attendance again.",
                    AppThemeSupport.Tone.SUCCESS
                );
            }));
    }

    private void applyTableFilters() {
        filteredRecords.setPredicate(AttendanceSummarySupport.filterPredicate(selectedReasonFilter, selectedAuditFilter));
    }

    public void onShow() {
        active = true;
        updateRealtimeRefreshState();
        if (pendingRefresh) {
            requestPendingAttendanceRefresh(datePicker == null ? null : datePicker.getValue());
        }
    }

    public void onHide() {
        active = false;
        updateRealtimeRefreshState();
    }

    private void updateRealtimeRefreshState() {
        boolean shouldRefresh = active
            && datePicker != null
            && java.time.LocalDate.now().equals(datePicker.getValue());

        if (shouldRefresh) {
            if (realtimeRefreshTimeline.getStatus() != Animation.Status.RUNNING) {
                realtimeRefreshTimeline.play();
            }
            return;
        }

        realtimeRefreshTimeline.stop();
    }

    private java.util.List<AttendanceRecord> checkedRecords() {
        java.util.List<AttendanceRecord> selected = new java.util.ArrayList<>();
        for (AttendanceRecord record : masterRecords) {
            if (record != null && record.isSelected()) {
                selected.add(record);
            }
        }
        return selected;
    }

    private java.util.Set<String> selectedChildDocIds() {
        java.util.Set<String> selectedIds = new java.util.HashSet<>();
        for (AttendanceRecord record : masterRecords) {
            if (record != null && record.isSelected() && record.getChildDocId() != null && !record.getChildDocId().isBlank()) {
                selectedIds.add(record.getChildDocId());
            }
        }
        return selectedIds;
    }

    private void restoreSelectionState(java.util.List<AttendanceRecord> records, java.util.Set<String> selectedChildIds) {
        if (records == null || selectedChildIds == null || selectedChildIds.isEmpty()) {
            return;
        }
        for (AttendanceRecord record : records) {
            if (record != null && selectedChildIds.contains(record.getChildDocId())) {
                record.setSelected(true);
            }
        }
    }

    private String fingerprintRecords(java.util.List<AttendanceRecord> records) {
        if (records == null || records.isEmpty()) {
            return EMPTY_ATTENDANCE_FINGERPRINT;
        }

        StringBuilder fingerprint = new StringBuilder(records.size() * 64);
        for (AttendanceRecord record : records) {
            if (record == null) {
                continue;
            }
            fingerprint
                .append(record.getChildDocId()).append('|')
                .append(record.getStatusLabel()).append('|')
                .append(record.getCheckInFullTimestamp()).append('|')
                .append(record.getCheckOutFullTimestamp()).append('|')
                .append(record.getReason()).append('|')
                .append(record.getManualEditReason()).append('|')
                .append(record.getUpdatedBy()).append('|')
                .append(record.getCheckInMethod()).append('|')
                .append(record.getCheckOutMethod())
                .append('\n');
        }
        return fingerprint.toString();
    }

    private java.util.List<AttendanceRecord> selectedRecordsOrAlert() {
        if (selectedDayStatus != null && selectedDayStatus.closed()) {
            AppThemeSupport.showWarning(
                root == null || root.getScene() == null ? null : root.getScene().getWindow(),
                "Closed Day",
                selectedDayStatus.message()
            );
            return java.util.List.of();
        }

        java.util.List<AttendanceRecord> selected = checkedRecords();
        if (!selected.isEmpty()) {
            return selected;
        }

        AttendanceRecord record = table.getSelectionModel().getSelectedItem();
        if (record != null) {
            return java.util.List.of(record);
        }

        new Alert(Alert.AlertType.WARNING, "Select at least one child record first.").showAndWait();
        return java.util.List.of();
    }

    private AttendanceRecord singleRecordOrAlert(String action) {
        java.util.List<AttendanceRecord> records = selectedRecordsOrAlert();
        if (records.isEmpty()) {
            return null;
        }
        if (records.size() > 1) {
            new Alert(Alert.AlertType.WARNING, AttendanceOverrideSupport.overrideTitle(action) + " supports only one child at a time.").showAndWait();
            return null;
        }
        return records.get(0);
    }

    private void setSelectedVisibleRecords(boolean selected) {
        for (AttendanceRecord record : filteredRecords) {
            if (record != null) {
                record.setSelected(selected);
            }
        }
        updateSelectionCountLabel();
    }

    private void clearSelectedRecords() {
        for (AttendanceRecord record : masterRecords) {
            if (record != null) {
                record.setSelected(false);
            }
        }
        updateSelectionCountLabel();
    }

    private void attachSelectionListeners(java.util.List<AttendanceRecord> records) {
        if (records == null) {
            return;
        }
        for (AttendanceRecord record : records) {
            if (record != null) {
                record.selectedProperty().addListener((obs, oldValue, newValue) -> updateSelectionCountLabel());
            }
        }
    }

    private void updateSelectionCountLabel() {
        if (selectionCountLabel == null) {
            return;
        }
        selectionCountLabel.setText("Selected: " + checkedRecords().size());
    }

    private void requestAttendanceRefresh(java.time.LocalDate date) {
        requestAttendanceRefresh(date, false);
    }

    private void requestAttendanceRefresh(java.time.LocalDate date, boolean clearChildren) {
        if (clearChildren) {
            attendanceDataCache = attendanceDataCache.clearChildren();
        } else {
            attendanceDataCache = attendanceDataCache.clearAttendance();
        }
        loadStudents(date != null ? date : (datePicker == null ? null : datePicker.getValue()));
    }

    private void requestPendingAttendanceRefresh(java.time.LocalDate date) {
        boolean clearChildren = pendingChildrenRefresh;
        pendingRefresh = false;
        pendingChildrenRefresh = false;
        requestAttendanceRefresh(date, clearChildren);
    }

    private boolean supportsBulkOverride(String action) {
        return "MANUAL_CHECK_IN".equals(action)
            || "MANUAL_CHECK_OUT".equals(action)
            || "MARK_ABSENT".equals(action);
    }

    private void openOverrideDialog(String action) {
        java.util.List<AttendanceRecord> records = selectedRecordsOrAlert();
        if (records.isEmpty()) return;

        if (!supportsBulkOverride(action) && records.size() > 1) {
            new Alert(Alert.AlertType.WARNING, AttendanceOverrideSupport.overrideTitle(action) + " supports only one child at a time.").showAndWait();
            return;
        }

        AttendanceRecord record = records.get(0);

        if ("EDIT_RECORD".equals(action) && record.hasCheckOut() && !AttendanceOverrideDialogSupport.confirmCompletedRecordEdit(record)) {
            return;
        }

        String subjectLabel = records.size() == 1
            ? record.getName() + " on " + datePicker.getValue()
            : records.size() + " selected children on " + datePicker.getValue();
        AttendanceOverrideDialogSupport.OverrideDialogResult dialogResult = AttendanceOverrideDialogSupport.promptForOverride(
            action,
            records.size() == 1 ? record : null,
            datePicker.getValue(),
            subjectLabel
        );
        if (dialogResult == null) {
            return;
        }

        String confirmTarget = records.size() == 1 ? record.getName() : records.size() + " selected children";
        if (!AttendanceOverrideDialogSupport.confirmSubmit(action, confirmTarget)) {
            return;
        }

        java.util.concurrent.CompletableFuture
            .supplyAsync(() -> applyOverrideAction(action, records, dialogResult))
            .whenComplete((result, error) -> Platform.runLater(() -> {
                if (error != null) {
                    Throwable failure = error;
                    if (error instanceof java.util.concurrent.CompletionException && error.getCause() != null) {
                        failure = error.getCause();
                    }
                    new Alert(Alert.AlertType.ERROR, "Attendance action failed: " + failure.getMessage()).showAndWait();
                    return;
                }

                if (result.failures.isEmpty()) {
                    if (result.successCount > 0) {
                        requestAttendanceRefresh(dialogResult.attendanceDate);
                        FirestoreService.refreshAfterAttendanceMutation(dialogResult.attendanceDate);
                    }
                    new Alert(Alert.AlertType.INFORMATION, AttendanceOverrideSupport.overrideTitle(action) + " saved for " + result.successCount + " child(ren).").showAndWait();
                    return;
                }

                String failureText = String.join("\n", result.failures);
                if (result.successCount > 0) {
                    requestAttendanceRefresh(dialogResult.attendanceDate);
                    FirestoreService.refreshAfterAttendanceMutation(dialogResult.attendanceDate);
                    new Alert(Alert.AlertType.WARNING,
                        AttendanceOverrideSupport.overrideTitle(action) + " saved for " + result.successCount + " child(ren), but some updates failed:\n\n" + failureText
                    ).showAndWait();
                } else {
                    new Alert(Alert.AlertType.ERROR,
                        "Attendance action failed for all selected children:\n\n" + failureText
                    ).showAndWait();
                }
            }));
    }

    private BulkOverrideResult applyOverrideAction(
        String action,
        java.util.List<AttendanceRecord> records,
        AttendanceOverrideDialogSupport.OverrideDialogResult dialogResult
    ) {
        java.util.List<java.util.concurrent.CompletableFuture<RecordOverrideResult>> futures = new java.util.ArrayList<>();
        for (AttendanceRecord record : records) {
            futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> applyOverrideActionForRecord(action, record, dialogResult)));
        }

        java.util.List<String> failures = new java.util.ArrayList<>();
        int successCount = 0;

        for (java.util.concurrent.CompletableFuture<RecordOverrideResult> future : futures) {
            RecordOverrideResult recordResult = future.join();
            if (recordResult.success) {
                successCount++;
            } else {
                failures.add(recordResult.failureMessage);
            }
        }

        return new BulkOverrideResult(successCount, failures);
    }

    private RecordOverrideResult applyOverrideActionForRecord(
        String action,
        AttendanceRecord record,
        AttendanceOverrideDialogSupport.OverrideDialogResult dialogResult
    ) {
        try {
            java.util.Map<String, Object> payload = AttendanceOverrideSupport.buildPayload(
                action,
                record,
                dialogResult.attendanceDate,
                dialogResult.reason,
                dialogResult.notes,
                UserSession.getName(),
                dialogResult.checkInText,
                dialogResult.checkOutText,
                dialogResult.checkoutTeacherId,
                dialogResult.checkoutTeacherName,
                dialogResult.checkoutTeacherEmail
            );
            FirebaseFunctionsClient.CallResult result = AttendanceOverrideSupport.submitOverride(payload);
            if (result.ok) {
                return RecordOverrideResult.success();
            }
            return RecordOverrideResult.failure(record.getName() + ": " + result.reason);
        } catch (IllegalArgumentException | java.io.IOException ex) {
            return RecordOverrideResult.failure(record.getName() + ": " + ex.getMessage());
        }
    }

    private void showAttendanceAuditDialog() {
        AttendanceRecord record = singleRecordOrAlert("VIEW_AUDIT");
        if (record == null) {
            return;
        }

        showAttendanceAuditDialog(record);
    }

    private void showAttendanceAuditDialog(AttendanceRecord record) {
        if (record == null) {
            return;
        }
        AttendanceAuditDialogSupport.showAuditDialog(root == null || root.getScene() == null ? null : root.getScene().getWindow(), datePicker.getValue(), record);
    }

    private static final class AttendanceLoadSnapshot {
        final AttendanceClosedDaySupport.DayStatus dayStatus;
        final AttendanceDataSupport.AttendanceDataCache cache;
        final java.util.List<AttendanceRecord> records;

        private AttendanceLoadSnapshot(
            AttendanceClosedDaySupport.DayStatus dayStatus,
            AttendanceDataSupport.AttendanceDataCache cache,
            java.util.List<AttendanceRecord> records
        ) {
            this.dayStatus = dayStatus;
            this.cache = cache == null ? AttendanceDataSupport.AttendanceDataCache.empty() : cache;
            this.records = records == null ? java.util.List.of() : records;
        }

        static AttendanceLoadSnapshot open(AttendanceClosedDaySupport.DayStatus dayStatus, AttendanceDataSupport.AttendanceLoadResult result) {
            return new AttendanceLoadSnapshot(dayStatus, result == null ? null : result.cache, result == null ? java.util.List.of() : result.records);
        }

        static AttendanceLoadSnapshot closed(AttendanceClosedDaySupport.DayStatus dayStatus, AttendanceDataSupport.AttendanceDataCache cache) {
            return new AttendanceLoadSnapshot(dayStatus, cache, java.util.List.of());
        }
    }

    private static final class BulkOverrideResult {
        final int successCount;
        final java.util.List<String> failures;

        private BulkOverrideResult(int successCount, java.util.List<String> failures) {
            this.successCount = successCount;
            this.failures = failures == null ? java.util.List.of() : failures;
        }
    }

    private static final class RecordOverrideResult {
        final boolean success;
        final String failureMessage;

        private RecordOverrideResult(boolean success, String failureMessage) {
            this.success = success;
            this.failureMessage = failureMessage == null ? "" : failureMessage;
        }

        private static RecordOverrideResult success() {
            return new RecordOverrideResult(true, "");
        }

        private static RecordOverrideResult failure(String failureMessage) {
            return new RecordOverrideResult(false, failureMessage);
        }
    }

    public javafx.scene.layout.VBox getRoot() {
        return root;
    }

    public static void markPresentFromNfcScan(String childDocId, String childName, String nfcUid) {
        Platform.runLater(() -> {
            if (currentInstance != null) {
                java.time.LocalDate selectedDate = currentInstance.datePicker == null ? null : currentInstance.datePicker.getValue();
                if (selectedDate != null && !java.time.LocalDate.now().equals(selectedDate)) {
                    return;
                }

                String normalizedUid = NFCAttendanceSupport.normalizeUid(nfcUid);
                boolean updated = false;
                for (AttendanceRecord record : currentInstance.masterRecords) {
                    if (record == null) {
                        continue;
                    }

                    boolean childDocMatch = childDocId != null
                        && !childDocId.isBlank()
                        && java.util.Objects.equals(record.getChildDocId(), childDocId);
                    boolean uidMatch = !normalizedUid.isBlank()
                        && java.util.Objects.equals(NFCAttendanceSupport.normalizeUid(record.getNfcUid()), normalizedUid);
                    boolean nameMatch = childName != null
                        && !childName.isBlank()
                        && childName.equalsIgnoreCase(record.getName());

                    if (childDocMatch || uidMatch || nameMatch) {
                        if (childName != null && !childName.isBlank()) {
                            record.nameProperty().set(childName);
                        }
                        record.setPresent(true);
                        record.setCheckInFullTimestamp(java.time.LocalDateTime.now().format(DB_TIMESTAMP_FORMAT));
                        record.setCheckInMethod("NFC");
                        updated = true;
                        break;
                    }
                }

                if (updated) {
                    currentInstance.updateChart(currentInstance.chart);
                    currentInstance.table.refresh();
                    return;
                }

                currentInstance.requestAttendanceRefresh(selectedDate);
            }
        });
    }

    private void updateChart(javafx.scene.chart.PieChart chart) {
        long now = System.currentTimeMillis();
        if (now - lastChartUpdate < 400) return;
        lastChartUpdate = now;
        try {
            Platform.runLater(() -> {
                if (selectedDayStatus != null && selectedDayStatus.closed()) {
                    chart.setData(javafx.collections.FXCollections.observableArrayList());
                    return;
                }
                AttendanceSummarySupport.AttendanceChartSnapshot snapshot = AttendanceSummarySupport.summarize(masterRecords);
                AttendanceSummarySupport.renderChart(chart, snapshot);
            });
        } catch (Exception e) {
            logError("update chart failed", e);
        }
    }

    public static void refreshUI() {
        if (currentInstance == null) {
            System.out.println("⚠ AttendanceView not active yet");
            pendingRefresh = true;
            return;
        }
        if (!currentInstance.active) {
            pendingRefresh = true;
            System.out.println("ℹ AttendanceView refresh deferred until the tab is shown.");
            return;
        }

        Platform.runLater(() -> {
            currentInstance.requestAttendanceRefresh(currentInstance.datePicker.getValue());
            if (currentInstance.chart != null) {
                currentInstance.updateChart(currentInstance.chart);
            }
            currentInstance.table.refresh();
        });
    }

    public static void refreshRoster() {
        if (currentInstance == null) {
            pendingRefresh = true;
            pendingChildrenRefresh = true;
            return;
        }
        if (!currentInstance.active) {
            pendingRefresh = true;
            pendingChildrenRefresh = true;
            return;
        }

        Platform.runLater(() -> {
            if (currentInstance.attendanceLoadInFlight) {
                pendingRefresh = true;
                pendingChildrenRefresh = true;
                return;
            }

            pendingChildrenRefresh = false;
            currentInstance.requestAttendanceRefresh(
                currentInstance.datePicker == null ? null : currentInstance.datePicker.getValue(),
                true
            );
        });
    }

    public static java.util.List<AttendanceRecord> getCurrentAttendanceState() {
        if (currentInstance != null) {
            return currentInstance.masterRecords;
        }
        return javafx.collections.FXCollections.observableArrayList();
    }

    // ✅ Static helper so NFCReader can trigger pie chart refresh
    public static void updateChartFromStatic() {
        Platform.runLater(() -> {
            if (currentInstance != null && currentInstance.active && currentInstance.chart != null) {
                currentInstance.updateChart(currentInstance.chart);
                currentInstance.table.refresh();
            } else {
                System.out.println("⚠ Pie chart not available — Attendance tab might be closed.");
            }
        });
    }
}