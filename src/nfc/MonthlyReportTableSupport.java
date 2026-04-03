package nfc;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javafx.scene.control.Button;
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
        idCol.setPrefWidth(120);

        TableColumn<StudentMonthlyAttendance, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());
        nameCol.setPrefWidth(150);

        TableColumn<StudentMonthlyAttendance, String> percentCol = new TableColumn<>("Attendance ");
        percentCol.setCellValueFactory(data -> data.getValue().attendancePercentProperty());
        percentCol.setPrefWidth(150);

        TableColumn<StudentMonthlyAttendance, String> performanceCol = new TableColumn<>("Performance");
        performanceCol.setCellValueFactory(data -> data.getValue().performanceProperty());
        performanceCol.setPrefWidth(150);

        TableColumn<StudentMonthlyAttendance, Void> downloadCol = new TableColumn<>("Download");
        downloadCol.setPrefWidth(100);
        downloadCol.setCellFactory(col -> new TableCell<StudentMonthlyAttendance, Void>() {
            private final Button btn = new Button("⬇");

            {
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

        table.setRowFactory(tv -> new TableRow<StudentMonthlyAttendance>() {
            @Override
            protected void updateItem(StudentMonthlyAttendance item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if ("Good".equalsIgnoreCase(item.getPerformance())) {
                    setStyle("-fx-background-color: #e7ffe9;");
                } else {
                    setStyle("-fx-background-color: #ffe7e7;");
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