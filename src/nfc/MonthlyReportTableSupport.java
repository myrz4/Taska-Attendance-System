package nfc;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

public final class MonthlyReportTableSupport {
    private MonthlyReportTableSupport() {
    }

    static {
        if (keepAnalyzerAnchors()) {
            setupTable(
                null,
                () -> 0,
                () -> 0,
                () -> "",
                () -> java.util.List.of(),
                (info, days, month, year, attendancePercent, performance) -> {
                }
            );
        }
    }

    private static boolean keepAnalyzerAnchors() {
        return Boolean.getBoolean("taska.keepAnalyzerAnchors");
    }

    public interface PreviewAction {
        void show(
            StudentInfo info,
            List<AttendanceRow> days,
            String month,
            String year,
            String attendancePercent,
            String performance
        );
    }

    public static void setupTable(
        TableView<StudentMonthlyAttendance> table,
        IntSupplier selectedMonth,
        IntSupplier selectedYear,
        Supplier<String> selectedMonthName,
        Supplier<List<FsDocument>> cachedAttendance,
        PreviewAction previewAction
    ) {
        TableColumn<StudentMonthlyAttendance, String> idCol = new TableColumn<>("Child ID");
        idCol.setCellValueFactory(data -> data.getValue().childIdProperty());
        idCol.setPrefWidth(180);
        idCol.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #253041;");

        TableColumn<StudentMonthlyAttendance, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());
        nameCol.setPrefWidth(220);
        nameCol.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #253041;");

        TableColumn<StudentMonthlyAttendance, String> percentCol = new TableColumn<>("Attendance");
        percentCol.setCellValueFactory(data -> data.getValue().attendancePercentProperty());
        percentCol.setPrefWidth(170);
        percentCol.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #253041;");

        TableColumn<StudentMonthlyAttendance, String> performanceCol = new TableColumn<>("Performance");
        performanceCol.setCellValueFactory(data -> data.getValue().performanceProperty());
        performanceCol.setPrefWidth(170);
        performanceCol.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #253041;");

        TableColumn<StudentMonthlyAttendance, Void> downloadCol = new TableColumn<>("Preview");
        downloadCol.setPrefWidth(120);
        downloadCol.setCellFactory(col -> new TableCell<StudentMonthlyAttendance, Void>() {
            private final Button btn = new Button("Open");

            {
                btn.setStyle(
                    "-fx-background-color: linear-gradient(to right, #ffcb3c 0%, #f2b72a 100%);"
                        + "-fx-text-fill: #2b1f00;"
                        + "-fx-font-size: 12px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-padding: 8 14 8 14;"
                        + "-fx-background-radius: 12;"
                );
                btn.setOnAction(e -> {
                    StudentMonthlyAttendance row = getTableView().getItems().get(getIndex());
                    StudentInfo info = new StudentInfo(
                        row.getChildId(),
                        row.getName(),
                        row.getParentName(),
                        row.getParentContact()
                    );

                    List<AttendanceRow> days = MonthlyReportDataSupport.getAttendanceRowsForStudentMonth(
                        cachedAttendance.get(),
                        row.getChildId(),
                        selectedMonth.getAsInt(),
                        selectedYear.getAsInt()
                    );

                    previewAction.show(
                        info,
                        days,
                        selectedMonthName.get(),
                        String.valueOf(selectedYear.getAsInt()),
                        row.getAttendancePercent(),
                        row.getPerformance()
                    );
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().clear();
        table.getColumns().addAll(Arrays.<TableColumn<StudentMonthlyAttendance, ?>>asList(
            idCol,
            nameCol,
            percentCol,
            performanceCol,
            downloadCol
        ));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No monthly attendance summaries found for the selected period."));
        table.setFixedCellSize(44);
        table.setStyle(
            "-fx-background-color: transparent;"
                + "-fx-border-color: transparent;"
                + "-fx-selection-bar: #ffe4a8;"
                + "-fx-selection-bar-non-focused: #ffeec7;"
        );

        table.setRowFactory(tv -> new TableRow<StudentMonthlyAttendance>() {
            @Override
            protected void updateItem(StudentMonthlyAttendance item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("-fx-background-color: transparent;");
                } else if ("Good".equalsIgnoreCase(item.getPerformance())) {
                    setStyle("-fx-background-color: #f8fffa; -fx-border-color: transparent transparent #e6f0ea transparent;");
                } else {
                    setStyle("-fx-background-color: #fff7f5; -fx-border-color: transparent transparent #f0dfdc transparent;");
                }
            }
        });

        percentCol.setCellFactory(col -> new TableCell<StudentMonthlyAttendance, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    StudentMonthlyAttendance attendance = getTableView().getItems().get(getIndex());
                    setStyle("Good".equalsIgnoreCase(attendance.getPerformance())
                        ? "-fx-text-fill: #087400; -fx-font-weight: bold;"
                        : "-fx-text-fill: #c62828; -fx-font-weight: bold;");
                }
            }
        });

        performanceCol.setCellFactory(col -> new TableCell<StudentMonthlyAttendance, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("Good".equalsIgnoreCase(item)
                        ? "-fx-text-fill: #087400; -fx-font-weight: bold;"
                        : "-fx-text-fill: #c62828; -fx-font-weight: bold;");
                }
            }
        });
    }
}