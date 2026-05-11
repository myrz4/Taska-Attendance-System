package nfc;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import com.lowagie.text.DocumentException;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

@SuppressWarnings("unused")
final class DailyReportPreviewSupport {
    private DailyReportPreviewSupport() {
    }

    static void showDailyReportPreview(Class<?> resourceAnchor, LocalDate date, ObservableList<AttendanceRow> rows) {
        Stage previewStage = new Stage();
        previewStage.setTitle("Preview Daily Attendance Report");

        VBox layout = new VBox(16);
        layout.setPadding(new Insets(24));
        layout.setAlignment(Pos.TOP_CENTER);

        java.net.URL logoResource = resourceAnchor.getResource("/nfc/logo.png");
        ImageView logo = new ImageView();
        if (logoResource != null) {
            logo.setImage(new Image(logoResource.toExternalForm()));
        }
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        logo.setFitWidth(170);

        Label header = new Label("TASKA ZURAH DAILY ATTENDANCE REPORT");
        header.setFont(javafx.scene.text.Font.font("Poppins", javafx.scene.text.FontWeight.BOLD, 22));
        header.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");

        String dayOfWeek = date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
        Label dateInfo = new Label(dayOfWeek + ", " + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        dateInfo.setFont(javafx.scene.text.Font.font("Poppins", javafx.scene.text.FontWeight.BOLD, 16));

        TableView<AttendanceRow> previewTable = new TableView<>();
        previewTable.setItems(rows);
        previewTable.setEditable(true);

        TableColumn<AttendanceRow, String> colId = new TableColumn<>("Child ID");
        colId.setCellValueFactory(data -> data.getValue().childIdProperty());
        colId.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold; -fx-background-color: transparent; -fx-font-size: 13px; -fx-text-fill: #222;");
        colId.setPrefWidth(100);

        TableColumn<AttendanceRow, String> colName = new TableColumn<>("Name");
        colName.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        colName.setCellValueFactory(data -> data.getValue().nameProperty());
        colName.setPrefWidth(80);

        TableColumn<AttendanceRow, String> colStatus = new TableColumn<>("Status");
        colStatus.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
        colStatus.setPrefWidth(80);

        TableColumn<AttendanceRow, String> colReason = new TableColumn<>("Reason");
        colReason.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        colReason.setCellValueFactory(data -> data.getValue().reasonProperty());
        colReason.setPrefWidth(80);

        TableColumn<AttendanceRow, String> colCheckIn = new TableColumn<>("Check In");
        colCheckIn.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        colCheckIn.setCellValueFactory(data -> data.getValue().checkInTimeProperty());
        colCheckIn.setPrefWidth(80);

        TableColumn<AttendanceRow, String> colCheckOut = new TableColumn<>("Check Out");
        colCheckOut.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        colCheckOut.setCellValueFactory(data -> {
            AttendanceRow row = data.getValue();
            String time = row.getCheckOutTime();
            if (row.getCheckOutDate() != null && row.getDate() != null && row.getCheckOutDate().isAfter(row.getDate())) {
                time += " (next day)";
            }
            return new SimpleStringProperty(time);
        });
        colCheckOut.setPrefWidth(80);

        TableColumn<AttendanceRow, String> colRemark = new TableColumn<>("Remark");
        colRemark.setCellValueFactory(data -> data.getValue().remarkProperty());
        colRemark.setCellFactory(TextFieldTableCell.forTableColumn());
        colRemark.setOnEditCommit(event -> {
            AttendanceRow row = event.getRowValue();
            row.setRemark(event.getNewValue());
        });

        previewTable.getColumns().addAll(Arrays.asList(colId, colName, colStatus, colReason, colCheckIn, colCheckOut, colRemark));

        Button exportBtn = new Button("Export as PDF");
        exportBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold;");
        exportBtn.setOnAction(ev -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save PDF");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            fileChooser.setInitialFileName("daily_report_" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf");
            File file = fileChooser.showSaveDialog(previewStage);
            if (file != null) {
                generateDailyPDF(resourceAnchor, file, date, rows);
            }
        });

        layout.getChildren().addAll(logo, header, dateInfo, previewTable, exportBtn);

        Scene scene = new Scene(layout, 860, 600);
        previewStage.setScene(scene);
        previewStage.show();
    }

    static void generateDailyPDF(Class<?> resourceAnchor, File file, LocalDate date, ObservableList<AttendanceRow> rows) {
        try {
            DailyReportPdfSupport.generateDailyPdf(resourceAnchor, file, date, rows);
            new Alert(Alert.AlertType.INFORMATION, "PDF exported!").showAndWait();
        } catch (DocumentException | IOException e) {
            System.err.println("Failed to export daily PDF: " + e.getMessage());
            new Alert(Alert.AlertType.ERROR, "Failed to export PDF: " + e.getMessage()).showAndWait();
        }
    }

    static String formatReportTime(Date date) {
        if (date == null) {
            return "-";
        }
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("hh:mm a");
        formatter.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        return formatter.format(date);
    }
}