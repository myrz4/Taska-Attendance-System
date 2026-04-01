package nfc;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

final class MonthlyReportPreviewSupport {
    private MonthlyReportPreviewSupport() {
    }

    static void showMonthlyReportPreview(
        Class<?> resourceAnchor,
        StudentInfo info,
        List<AttendanceRow> days,
        String month,
        String year,
        String attendancePercent,
        String performance
    ) {
        Stage previewStage = new Stage();
        previewStage.setTitle("Monthly Attendance Report - " + info.childName);

        int presentCount = 0;
        int absentCount = 0;
        for (AttendanceRow row : days) {
            if ("attend".equalsIgnoreCase(row.getStatus())) {
                presentCount++;
            } else {
                absentCount++;
            }
        }

        VBox page = new VBox(18);
        page.setPadding(new Insets(24));
        page.setStyle("-fx-background-color: linear-gradient(to bottom, #fffaf1 0%, #f6f8fc 100%);");

        Image logoImg;
        try {
            logoImg = new Image(resourceAnchor.getResourceAsStream("/nfc/logo.png"));
        } catch (Exception e) {
            logoImg = null;
        }
        ImageView logo = new ImageView(logoImg);
        logo.setFitHeight(58);
        logo.setFitWidth(58);
        logo.setPreserveRatio(true);

        Label eyebrow = new Label("MONTHLY STUDENT REPORT");
        eyebrow.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #c28519;");

        Label title = new Label("Taska Zurah Attendance Summary");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #1d2a3a;");

        Label subtitle = new Label(info.childName + "  •  " + month.toUpperCase() + " " + year);
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #6b7280;");

        VBox titleBox = new VBox(4, eyebrow, title, subtitle);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Label badge = new Label("Attendance Report");
        badge.setStyle("-fx-background-color: #fff1cc; -fx-text-fill: #9a6700; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 8 14 8 14; -fx-background-radius: 999;");

        HBox header = new HBox(16, logo, titleBox, headerSpacer, badge);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: white; -fx-background-radius: 24; -fx-border-color: #f2e7cf; -fx-border-radius: 24;");

        GridPane infoPane = new GridPane();
        infoPane.setHgap(18);
        infoPane.setVgap(12);
        infoPane.add(reportFieldLabel("Child ID"), 0, 0);
        infoPane.add(reportFieldValue(info.childId), 1, 0);
        infoPane.add(reportFieldLabel("Month"), 2, 0);
        infoPane.add(reportFieldValue(month.toUpperCase()), 3, 0);
        infoPane.add(reportFieldLabel("Name"), 0, 1);
        infoPane.add(reportFieldValue(info.childName), 1, 1);
        infoPane.add(reportFieldLabel("Year"), 2, 1);
        infoPane.add(reportFieldValue(year), 3, 1);
        infoPane.add(reportFieldLabel("Parent Name"), 0, 2);
        infoPane.add(reportFieldValue(info.parentName), 1, 2);
        infoPane.add(reportFieldLabel("Contact Number"), 2, 2);
        infoPane.add(reportFieldValue(info.parentContact), 3, 2);
        infoPane.getColumnConstraints().addAll(
            new ColumnConstraints(110),
            new ColumnConstraints(220),
            new ColumnConstraints(120),
            new ColumnConstraints(220)
        );

        VBox infoCard = new VBox(12, sectionTitle("Student Details"), infoPane);
        infoCard.setPadding(new Insets(20));
        infoCard.setStyle("-fx-background-color: white; -fx-background-radius: 22; -fx-border-color: #e6ebf2; -fx-border-radius: 22;");

        Label performanceLabel = new Label("Performance: " + performance.toUpperCase());
        performanceLabel.setFont(Font.font("Poppins", FontWeight.BOLD, 16));
        if ("Good".equalsIgnoreCase(performance)) {
            performanceLabel.setStyle(
                "-fx-background-color: #eaf8ef; -fx-text-fill: #0d7a38; -fx-padding: 12 16 12 16; -fx-background-radius: 16;"
            );
        } else {
            performanceLabel.setStyle(
                "-fx-background-color: #fff0ee; -fx-text-fill: #c53b2a; -fx-padding: 12 16 12 16; -fx-background-radius: 16;"
            );
        }

        HBox metrics = new HBox(12,
            metricCard("Attendance", attendancePercent, "#fff5d9", "#9a6700"),
            metricCard("Present Days", String.valueOf(presentCount), "#ebf8ef", "#167c47"),
            metricCard("Absent Days", String.valueOf(absentCount), "#fff0ee", "#c53b2a"),
            performanceLabel
        );
        metrics.setAlignment(Pos.CENTER_LEFT);

        TableView<AttendanceRow> attendanceTable = MonthlyReportPreviewTableSupport.buildAttendanceTable(days);

        VBox tableCard = new VBox(14, sectionTitle("Attendance Timeline"), attendanceTable);
        tableCard.setPadding(new Insets(20));
        tableCard.setStyle("-fx-background-color: white; -fx-background-radius: 22; -fx-border-color: #e6ebf2; -fx-border-radius: 22;");

        Button saveBtn = new Button("Save as PDF");
        saveBtn.setStyle(
            "-fx-background-color: linear-gradient(to right, #ffcb3c 0%, #f2b72a 100%);"
                + "-fx-text-fill: #2b1f00;"
                + "-fx-font-size: 15px;"
                + "-fx-font-weight: bold;"
                + "-fx-padding: 12 22 12 22;"
                + "-fx-background-radius: 14;"
        );
        saveBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save PDF");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            fileChooser.setInitialFileName(info.childName + "_report.pdf");
            File file = fileChooser.showSaveDialog(previewStage);

            if (file != null) {
                try {
                    PDFReport.generateStudentMonthlyReport(
                        file.getAbsolutePath(),
                        "src/nfc/logo.png",
                        info,
                        days,
                        month,
                        year,
                        attendancePercent,
                        performance
                    );
                    Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, "PDF saved!").showAndWait());
                } catch (Exception ex) {
                    System.err.println("monthlyReport: failed to generate PDF - " + ex.getMessage());
                    new Alert(Alert.AlertType.ERROR, "Failed to generate PDF: " + ex.getMessage()).showAndWait();
                }
            }
        });

        HBox actionBar = new HBox(saveBtn);
        actionBar.setAlignment(Pos.CENTER_RIGHT);

        page.getChildren().addAll(header, infoCard, metrics, tableCard, actionBar);

        ScrollPane scrollPane = new ScrollPane(page);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #f6f8fc; -fx-background-color: #f6f8fc;");

        previewStage.setScene(new Scene(scrollPane, 980, 760));
        previewStage.show();
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #1d2a3a;");
        return label;
    }

    private static Label reportFieldLabel(String text) {
        Label label = new Label(text.toUpperCase());
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #8a94a6;");
        return label;
    }

    private static Label reportFieldValue(String text) {
        Label label = new Label(text == null || text.isBlank() ? "-" : text);
        label.setStyle("-fx-font-size: 15px; -fx-font-weight: 600; -fx-text-fill: #253041;");
        label.setWrapText(true);
        return label;
    }

    private static VBox metricCard(String labelText, String valueText, String bgColor, String valueColor) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #7b8798;");

        Label value = new Label(valueText == null || valueText.isBlank() ? "-" : valueText);
        value.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + valueColor + ";");

        VBox card = new VBox(6, label, value);
        card.setMinWidth(155);
        card.setPadding(new Insets(14, 18, 14, 18));
        card.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 18;");
        return card;
    }

}