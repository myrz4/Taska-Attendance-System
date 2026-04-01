package nfc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.util.Callback;

final class DailyReportTableSupport {
    private DailyReportTableSupport() {
    }

    static void setupTable(TableView<AttendanceRow> table) {
        DateTimeFormatter dbFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("hh:mm a");

        TableColumn<AttendanceRow, String> colId = new TableColumn<>("Child ID");
        colId.setCellValueFactory(data -> data.getValue().childIdProperty());
        colId.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-alignment: CENTER;");
        colId.setPrefWidth(110);

        TableColumn<AttendanceRow, String> colName = new TableColumn<>("Name");
        colName.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222;");
        colName.setCellValueFactory(data -> data.getValue().nameProperty());
        colName.setPrefWidth(120);

        TableColumn<AttendanceRow, String> colStatus = new TableColumn<>("Status");
        colStatus.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222;");
        colStatus.setCellValueFactory(data -> data.getValue().statusProperty());
        colStatus.setPrefWidth(90);

        TableColumn<AttendanceRow, String> colReason = new TableColumn<>("Reason");
        colReason.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222;");
        colReason.setCellValueFactory(data -> data.getValue().reasonProperty());
        colReason.setPrefWidth(148);

        TableColumn<AttendanceRow, String> checkInCol = new TableColumn<>("Check-In");
        checkInCol.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222;");
        checkInCol.setCellValueFactory(cellData -> cellData.getValue().checkInTimeProperty());
        checkInCol.setPrefWidth(135);
        checkInCol.setCellFactory(col -> new TableCell<AttendanceRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                TableRow<AttendanceRow> row = getTableRow();
                if (empty || item == null || row == null || row.getItem() == null) {
                    setText(null);
                    setStyle("");
                } else {
                    try {
                        LocalDateTime dt = LocalDateTime.parse(item, dbFormatter);
                        setText(dt.format(displayFormatter));
                    } catch (Exception e) {
                        setText(item);
                    }
                    setStyleForStatus(this, row.getItem().getStatus());
                }
            }
        });

        TableColumn<AttendanceRow, String> colCheckOut = new TableColumn<>("Check-out");
        colCheckOut.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222;");
        colCheckOut.setPrefWidth(135);
        colCheckOut.setCellValueFactory(data -> data.getValue().checkOutTimeProperty());
        colCheckOut.setCellFactory(col -> new TableCell<AttendanceRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                TableRow<AttendanceRow> row = getTableRow();
                if (empty || item == null || row == null || row.getItem() == null) {
                    setText(null);
                    setStyle("");
                } else {
                    try {
                        LocalDateTime dt = LocalDateTime.parse(item, dbFormatter);
                        setText(dt.format(displayFormatter));
                    } catch (Exception e) {
                        setText(item);
                    }
                    setStyleForStatus(this, row.getItem().getStatus());
                }
            }
        });

        Callback<TableColumn<AttendanceRow, String>, TableCell<AttendanceRow, String>> coloredCellFactory =
            col -> new TableCell<AttendanceRow, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    TableRow<AttendanceRow> row = getTableRow();
                    if (empty || item == null || row == null || row.getItem() == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        setStyleForStatus(this, row.getItem().getStatus());
                    }
                }
            };

        colName.setCellFactory(coloredCellFactory);
        colStatus.setCellFactory(coloredCellFactory);
        colReason.setCellFactory(coloredCellFactory);

        table.getColumns().clear();
        table.getColumns().addAll(Arrays.asList(colId, colName, colStatus, colReason, checkInCol, colCheckOut));
        table.setPrefHeight(320);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private static void setStyleForStatus(TableCell<AttendanceRow, String> cell, String status) {
        if ("attend".equalsIgnoreCase(status)) {
            cell.setStyle("-fx-background-color: #e8f5e9; -fx-text-fill: #2e7d32; -fx-font-weight: bold;");
        } else if ("absence".equalsIgnoreCase(status)) {
            cell.setStyle("-fx-background-color: #ffebee; -fx-text-fill: #c62828; -fx-font-weight: bold;");
        } else {
            cell.setStyle("");
        }
    }
}