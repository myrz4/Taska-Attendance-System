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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

@SuppressWarnings("unused")
final class DailyReportPreviewSupport {
    private DailyReportPreviewSupport() {
    }

    static void showDailyReportPreview(Class<?> resourceAnchor, LocalDate date, ObservableList<AttendanceRow> rows) {
        Stage previewStage = new Stage();
        previewStage.setTitle("Preview Daily Attendance Report");

        int presentCount = 0;
        int absentCount = 0;
        for (AttendanceRow row : rows) {
            if ("attend".equalsIgnoreCase(row.getStatus())) {
                presentCount++;
            } else {
                absentCount++;
            }
        }

        String attendanceRate = rows.isEmpty()
            ? "0%"
            : String.format(java.util.Locale.ENGLISH, "%.0f%%", (presentCount * 100.0) / rows.size());

        VBox page = new VBox(18);
        page.setPadding(new Insets(24));
        page.setStyle("-fx-background-color: linear-gradient(to bottom, #fffaf1 0%, #f6f8fc 100%);");

        java.net.URL logoResource = resourceAnchor.getResource("/nfc/logo.png");
        ImageView logo = new ImageView();
        if (logoResource != null) {
            logo.setImage(new Image(logoResource.toExternalForm()));
        }
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        logo.setFitWidth(58);
        logo.setFitHeight(58);

        Label eyebrow = new Label("DAILY ATTENDANCE REPORT");
        eyebrow.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #c28519;");

        Label title = new Label("Taska Zurah Attendance Summary");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #1d2a3a;");

        String dayOfWeek = date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
        Label subtitle = new Label(dayOfWeek + "  •  " + date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #6b7280;");

        VBox titleBox = new VBox(4, eyebrow, title, subtitle);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Label badge = new Label("Daily Preview");
        badge.setStyle("-fx-background-color: #fff1cc; -fx-text-fill: #9a6700; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 8 14 8 14; -fx-background-radius: 999;");

        HBox header = new HBox(16, logo, titleBox, headerSpacer, badge);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: white; -fx-background-radius: 24; -fx-border-color: #f2e7cf; -fx-border-radius: 24;");

        HBox metrics = new HBox(12,
            metricCard("Total Records", String.valueOf(rows.size()), "#eef4ff", "#2854a3"),
            metricCard("Present", String.valueOf(presentCount), "#ebf8ef", "#167c47"),
            metricCard("Absent", String.valueOf(absentCount), "#fff0ee", "#c53b2a"),
            metricCard("Attendance Rate", attendanceRate, "#fff5d9", "#9a6700")
        );
        metrics.setAlignment(Pos.CENTER_LEFT);

        TableView<AttendanceRow> previewTable = new TableView<>();
        previewTable.setItems(rows);
        previewTable.setEditable(true);
        previewTable.setFixedCellSize(40);
        previewTable.setPlaceholder(new Label("No attendance records found for the selected date."));
        previewTable.setStyle(
            "-fx-background-color: transparent;"
                + "-fx-border-color: transparent;"
                + "-fx-selection-bar: #ffe4a8;"
                + "-fx-selection-bar-non-focused: #ffeec7;"
        );

        TableColumn<AttendanceRow, String> colId = new TableColumn<>("CHILD ID");
        colId.setCellValueFactory(data -> data.getValue().childIdProperty());
        colId.setStyle("-fx-alignment: CENTER_LEFT; -fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #253041;");
        colId.setPrefWidth(150);

        TableColumn<AttendanceRow, String> colName = new TableColumn<>("NAME");
        colName.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #253041;");
        colName.setCellValueFactory(data -> data.getValue().nameProperty());
        colName.setPrefWidth(170);

        TableColumn<AttendanceRow, String> colStatus = new TableColumn<>("STATUS");
        colStatus.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #253041;");
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
        colStatus.setPrefWidth(120);

        TableColumn<AttendanceRow, String> colReason = new TableColumn<>("REASON");
        colReason.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #253041;");
        colReason.setCellValueFactory(data -> data.getValue().reasonProperty());
        colReason.setPrefWidth(210);

        TableColumn<AttendanceRow, String> colCheckIn = new TableColumn<>("CHECK-IN");
        colCheckIn.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #253041;");
        colCheckIn.setCellValueFactory(data -> data.getValue().checkInTimeProperty());
        colCheckIn.setPrefWidth(130);

        TableColumn<AttendanceRow, String> colCheckOut = new TableColumn<>("CHECK-OUT");
        colCheckOut.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #253041;");
        colCheckOut.setCellValueFactory(data -> {
            AttendanceRow row = data.getValue();
            String time = row.getCheckOutTime();
            if (row.getCheckOutDate() != null && row.getDate() != null && row.getCheckOutDate().isAfter(row.getDate())) {
                time += " (next day)";
            }
            return new SimpleStringProperty(time);
        });
        colCheckOut.setPrefWidth(145);

        TableColumn<AttendanceRow, String> colRemark = new TableColumn<>("REMARK");
        colRemark.setCellValueFactory(data -> data.getValue().remarkProperty());
        colRemark.setCellFactory(TextFieldTableCell.forTableColumn());
        colRemark.setPrefWidth(220);
        colRemark.setOnEditCommit(event -> {
            AttendanceRow row = event.getRowValue();
            row.setRemark(event.getNewValue());
        });

        previewTable.getColumns().addAll(Arrays.asList(colId, colName, colStatus, colReason, colCheckIn, colCheckOut, colRemark));
        previewTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        previewTable.setRowFactory(tableView -> new TableRow<AttendanceRow>() {
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

        Label helperText = new Label("Review the attendance data and update remarks before exporting the PDF.");
        helperText.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");

        VBox tableCard = new VBox(14, sectionTitle("Attendance Timeline"), helperText, previewTable);
        tableCard.setPadding(new Insets(20));
        tableCard.setStyle("-fx-background-color: white; -fx-background-radius: 22; -fx-border-color: #e6ebf2; -fx-border-radius: 22;");

        Button exportBtn = new Button("Save as PDF");
        exportBtn.setStyle(
            "-fx-background-color: linear-gradient(to right, #ffcb3c 0%, #f2b72a 100%);"
                + "-fx-text-fill: #2b1f00;"
                + "-fx-font-size: 15px;"
                + "-fx-font-weight: bold;"
                + "-fx-padding: 12 22 12 22;"
                + "-fx-background-radius: 14;"
        );
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

        HBox actionBar = new HBox(exportBtn);
        actionBar.setAlignment(Pos.CENTER_RIGHT);

        page.getChildren().addAll(header, metrics, tableCard, actionBar);

        ScrollPane scrollPane = new ScrollPane(page);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #f6f8fc; -fx-background-color: #f6f8fc;");

        Scene scene = new Scene(scrollPane, 1080, 760);
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

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #1d2a3a;");
        return label;
    }

    private static VBox metricCard(String labelText, String valueText, String bgColor, String valueColor) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #7b8798;");

        Label value = new Label(valueText == null || valueText.isBlank() ? "-" : valueText);
        value.setFont(Font.font("Poppins", FontWeight.BOLD, 20));
        value.setStyle("-fx-text-fill: " + valueColor + ";");

        VBox card = new VBox(6, label, value);
        card.setMinWidth(170);
        card.setPadding(new Insets(14, 18, 14, 18));
        card.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 18;");
        return card;
    }
}