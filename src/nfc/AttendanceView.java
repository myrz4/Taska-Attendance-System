package nfc;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class AttendanceView {
	private static AttendanceView currentInstance;

    private static void logError(String context, Exception error) {
        System.err.println("AttendanceView: " + context + " - " + error.getMessage());
    }
    private VBox root;
    private final TableView<AttendanceRecord> table;
    private final ObservableList<AttendanceRecord> masterRecords = FXCollections.observableArrayList();
    private final FilteredList<AttendanceRecord> filteredRecords = new FilteredList<>(masterRecords, p -> true);
    private PieChart chart; // ✅ make it global
    // ─── NEW: datePicker field ────────────────────────────────────────────────────
    private DatePicker datePicker;
    // ────────────────────────────────────────────────────────────────────────────────
    private static final DateTimeFormatter DB_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AttendanceDataSupport.AttendanceDataCache attendanceDataCache = AttendanceDataSupport.AttendanceDataCache.empty();
    private boolean active = false;
    private long lastChartUpdate = 0;
    private String selectedReasonFilter = "All";
    private String selectedAuditFilter = "All Records";

    public AttendanceView() {
        currentInstance = this;
        active = true;

        HBox dashboardHeader = AttendanceViewLayoutSupport.createDashboardHeader();
        
        root = new VBox(10);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #86d67f 0%, #76cc6e 100%);");

        AttendanceViewLayoutSupport.DateControls dateControls = AttendanceViewLayoutSupport.createDateControls(LocalDate.now());
        datePicker = dateControls.datePicker;
        Button loadBtn = dateControls.loadButton;
        loadBtn.setOnAction(e -> {
            LocalDate selectedDate = datePicker.getValue();
            loadStudents(selectedDate);
        });

        root.getChildren().add(dateControls.bar);

        AttendanceViewLayoutSupport.FilterToolbar filterToolbar = AttendanceViewLayoutSupport.createFilterToolbar();
        HBox header = filterToolbar.header;
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

        Button manualCheckInBtn = filterToolbar.manualCheckInBtn;
        manualCheckInBtn.setOnAction(e -> openOverrideDialog("MANUAL_CHECK_IN"));

        Button manualCheckOutBtn = filterToolbar.manualCheckOutBtn;
        manualCheckOutBtn.setOnAction(e -> openOverrideDialog("MANUAL_CHECK_OUT"));

        Button markAbsentBtn = filterToolbar.markAbsentBtn;
        markAbsentBtn.setOnAction(e -> openOverrideDialog("MARK_ABSENT"));

        Button editRecordBtn = filterToolbar.editRecordBtn;
        editRecordBtn.setOnAction(e -> openOverrideDialog("EDIT_RECORD"));

        Button reopenBtn = filterToolbar.reopenBtn;
        reopenBtn.setOnAction(e -> openOverrideDialog("REOPEN_RECORD"));

        Button viewAuditBtn = filterToolbar.viewAuditBtn;
        viewAuditBtn.setOnAction(e -> showAttendanceAuditDialog());

        table = new TableView<>();
        AttendanceTableSupport.configureTable(
            table,
            this::showAttendanceAuditDialog,
            () -> root == null || root.getScene() == null ? null : root.getScene().getWindow()
        );
        table.setItems(filteredRecords);

        Label chartTitle = new Label("Today's Attendance");
        chartTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        chart = new PieChart();

        Button refreshChart = AttendanceViewLayoutSupport.createActionButton("Refresh Chart");
        refreshChart.setOnAction(e -> updateChart(chart));

        root.getChildren().addAll(header, table, chartTitle, chart, refreshChart);

        BorderPane mainLayout = new BorderPane();
        mainLayout.setTop(dashboardHeader);
        mainLayout.setCenter(root);

        this.root = new VBox();
        this.root.getChildren().add(mainLayout);

        Platform.runLater(() -> {
            preloadData();
            loadStudents(datePicker.getValue());
        });

        datePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate != null) {
                loadStudents(newDate);
            }
        });
    }

    private void preloadData() {
        try {
            attendanceDataCache = AttendanceDataSupport.preloadChildren(attendanceDataCache);
        } catch (IOException | InterruptedException | IllegalStateException e) {
            logError("attendance preload failed", e);
        }
    }
    
    // ─── NEW: Revised loadStudents() with MIN/MAX ───────────────────────────────
    // Change method to accept date parameter
    private void loadStudents(LocalDate date) {
        masterRecords.clear();
        if (!UserSession.isLoggedIn()) return;

        try {
            AttendanceDataSupport.AttendanceLoadResult result = AttendanceDataSupport.loadRecords(date, attendanceDataCache);
            attendanceDataCache = result.cache;
            masterRecords.setAll(result.records);
        } catch (Exception e) {
            logError("load students failed", e);
        }

        table.refresh();
        updateChart(chart);
        applyTableFilters();
    }

    private void applyTableFilters() {
        filteredRecords.setPredicate(AttendanceSummarySupport.filterPredicate(selectedReasonFilter, selectedAuditFilter));
    }

    public void onShow() {
        active = true;
    }

    public void onHide() {
        active = false;
    }

    private AttendanceRecord selectedRecordOrAlert() {
        AttendanceRecord record = table.getSelectionModel().getSelectedItem();
        if (record == null) {
            new Alert(Alert.AlertType.WARNING, "Select a child record first.").showAndWait();
        }
        return record;
    }

    private void openOverrideDialog(String action) {
        AttendanceRecord record = selectedRecordOrAlert();
        if (record == null) return;

        if ("EDIT_RECORD".equals(action) && record.hasCheckOut() && !AttendanceOverrideDialogSupport.confirmCompletedRecordEdit(record)) {
            return;
        }

        AttendanceOverrideDialogSupport.OverrideDialogResult dialogResult = AttendanceOverrideDialogSupport.promptForOverride(action, record, datePicker.getValue());
        if (dialogResult == null) {
            return;
        }

        if (!AttendanceOverrideDialogSupport.confirmSubmit(action, record)) {
            return;
        }

        Map<String, Object> payload = AttendanceOverrideSupport.buildPayload(
            action,
            record,
            dialogResult.attendanceDate,
            dialogResult.reason,
            dialogResult.notes,
            UserSession.getName(),
            dialogResult.checkInText,
            dialogResult.checkOutText
        );

        CompletableFuture.runAsync(() -> {
            try {
                FirebaseFunctionsClient.CallResult result = AttendanceOverrideSupport.submitOverride(payload);
                Platform.runLater(() -> {
                    if (result.ok) {
                        attendanceDataCache = attendanceDataCache.clearAttendance();
                        loadStudents(dialogResult.attendanceDate);
                        new Alert(Alert.AlertType.INFORMATION, AttendanceOverrideSupport.overrideTitle(action) + " saved.").showAndWait();
                    } else {
                        new Alert(Alert.AlertType.ERROR, "Attendance action failed: " + result.reason).showAndWait();
                    }
                });
            } catch (IllegalArgumentException ex) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait());
            } catch (IOException ex) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Attendance action failed: " + ex.getMessage()).showAndWait());
            }
        });
    }

    private void showAttendanceAuditDialog() {
        AttendanceRecord record = selectedRecordOrAlert();
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

    public VBox getRoot() {
        return root;
    }

    public static void markPresentByChildId(int childId) {
        Platform.runLater(() -> {
            if (currentInstance != null) {
                for (AttendanceRecord record : currentInstance.masterRecords) {
                    if (record.getChildId() == childId) {
                        record.setPresent(true);
                        record.setCheckInFullTimestamp(LocalDateTime.now().format(DB_TIMESTAMP_FORMAT));
                        break;
                    }
                }

                // Reload attendance reasons and chart from database
                currentInstance.loadStudents(currentInstance.datePicker.getValue());   // ✅ refresh all data (so absent/present list updates)
                currentInstance.updateChart(currentInstance.chart);
                currentInstance.table.refresh();
            }
        });
    }

    private void updateChart(PieChart chart) {
        long now = System.currentTimeMillis();
        if (now - lastChartUpdate < 400) return;
        lastChartUpdate = now;
        try {
            Platform.runLater(() -> {
                AttendanceSummarySupport.AttendanceChartSnapshot snapshot = AttendanceSummarySupport.summarize(masterRecords);
                AttendanceSummarySupport.renderChart(chart, snapshot);
            });
        } catch (Exception e) {
            logError("update chart failed", e);
        }
    }

    public static void refreshUI() {
        if (currentInstance == null || !currentInstance.active) {
            System.out.println("⚠ AttendanceView not active yet");
            return;
        }

        // Refresh cached Firestore data off the UI thread, then update UI.
        CompletableFuture
            .runAsync(currentInstance::preloadData)
            .thenRun(() -> Platform.runLater(() -> {
                currentInstance.loadStudents(currentInstance.datePicker.getValue());
                if (currentInstance.chart != null) {
                    currentInstance.updateChart(currentInstance.chart);
                }
                currentInstance.table.refresh();
            }));
    }

    public static List<AttendanceRecord> getCurrentAttendanceState() {
        if (currentInstance != null) {
            return currentInstance.masterRecords;
        }
        return FXCollections.observableArrayList(); // fallback if view not open
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