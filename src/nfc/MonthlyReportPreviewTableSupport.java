package nfc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

final class MonthlyReportPreviewTableSupport {
    private MonthlyReportPreviewTableSupport() {
    }

    static {
        java.util.function.Function<List<AttendanceRow>, TableView<AttendanceRow>> keepBuildAttendanceTable =
            MonthlyReportPreviewTableSupport::buildAttendanceTable;
        java.util.Objects.requireNonNull(keepBuildAttendanceTable);
    }

    static TableView<AttendanceRow> buildAttendanceTable(List<AttendanceRow> days) {
        TableColumn<AttendanceRow, String> dateCol = new TableColumn<>("DATE");
        dateCol.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getDate() != null
                ? data.getValue().getDate().format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                : "")
        );
        dateCol.setPrefWidth(150);

        TableColumn<AttendanceRow, String> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
        statusCol.setPrefWidth(120);

        TableColumn<AttendanceRow, String> checkInCol = new TableColumn<>("CHECK-IN");
        checkInCol.setCellValueFactory(data -> new SimpleStringProperty(formatTime(data.getValue().getCheckInTime())));
        checkInCol.setPrefWidth(130);

        TableColumn<AttendanceRow, String> checkOutCol = new TableColumn<>("CHECK-OUT");
        checkOutCol.setCellValueFactory(data -> new SimpleStringProperty(formatTime(data.getValue().getCheckOutTime())));
        checkOutCol.setPrefWidth(130);

        TableColumn<AttendanceRow, String> reasonCol = new TableColumn<>("REASON");
        reasonCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getReason()));
        reasonCol.setPrefWidth(220);

        TableView<AttendanceRow> attendanceTable = new TableView<>(FXCollections.observableArrayList(days));
        attendanceTable.getColumns().setAll(Arrays.<TableColumn<AttendanceRow, ?>>asList(
            dateCol,
            statusCol,
            checkInCol,
            checkOutCol,
            reasonCol
        ));
        attendanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        attendanceTable.setPrefHeight(360);
        attendanceTable.setPlaceholder(new Label("No attendance records available for this month."));
        attendanceTable.setFixedCellSize(38);
        attendanceTable.setStyle(
            "-fx-background-color: transparent;"
                + "-fx-border-color: transparent;"
                + "-fx-selection-bar: #ffe4a8;"
                + "-fx-selection-bar-non-focused: #ffeec7;"
        );
        attendanceTable.setRowFactory(tableView -> new TableRow<AttendanceRow>() {
            @Override
            protected void updateItem(AttendanceRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("-fx-background-color: transparent;");
                } else if ("absence".equalsIgnoreCase(item.getStatus())) {
                    setStyle("-fx-background-color: #fff6f3; -fx-border-color: transparent transparent #f3e3df transparent;");
                } else {
                    setStyle("-fx-background-color: #ffffff; -fx-border-color: transparent transparent #edf1f7 transparent;");
                }
            }
        });
        return attendanceTable;
    }

    private static String formatTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return "";
        }
        try {
            DateTimeFormatter input = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            DateTimeFormatter output = DateTimeFormatter.ofPattern("h:mm a");
            LocalDateTime dt = LocalDateTime.parse(timeStr, input);
            return dt.format(output);
        } catch (DateTimeParseException e) {
            return timeStr;
        }
    }
}