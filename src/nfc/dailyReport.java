package nfc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class dailyReport {
    private final VBox root;
    private final BorderPane layout;
    private final ScrollPane scrollRoot;
    private final DatePicker datePicker;
    private final TableView<AttendanceRow> table;
    private final Label selectedDateValue = metricValueLabel("-");
    private final Label totalRecordsValue = metricValueLabel("0");
    private final Label presentValue = metricValueLabel("0");
    private final Label absentValue = metricValueLabel("0");

    public dailyReport() {
        root = new VBox(18);
        root.setFillWidth(true);
        root.setAlignment(Pos.TOP_LEFT);
        root.setPadding(new Insets(20, 18, 20, 18));
        root.setStyle("-fx-background-color: #7bcf74;");

        scrollRoot = new ScrollPane(root);
        scrollRoot.setFitToWidth(true);
        scrollRoot.setPannable(true);
        scrollRoot.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollRoot.setStyle("-fx-background: #7bcf74; -fx-background-color: #7bcf74;");

        layout = new BorderPane();
        layout.setTop(buildHeaderBar("Daily Attendance Report"));
        layout.setCenter(scrollRoot);
        layout.setStyle("-fx-background-color: #7bcf74;");

        datePicker = new DatePicker(java.time.LocalDate.now());
        datePicker.setPrefWidth(160);
        datePicker.valueProperty().addListener((obs, old, selected) -> loadAttendance(selected));

        table = new TableView<>();
        DailyReportTableSupport.setupTable(table);
        table.setMaxWidth(Double.MAX_VALUE);
        table.setMinHeight(0);

        Button previewBtn = createPrimaryButton("Preview Daily Report");
        previewBtn.setOnAction(e -> {
            java.time.LocalDate selectedDate = datePicker.getValue();
            showDailyReportPreview(selectedDate, FXCollections.observableArrayList(table.getItems()));
        });

        VBox filterBlock = new VBox(6, sectionEyebrow("REPORT DATE"), datePicker);
        filterBlock.setAlignment(Pos.CENTER_LEFT);

        Label helperText = new Label("Choose a date to refresh the table, then open the preview with the same polished export format used by the monthly report.");
        helperText.setWrapText(true);
        helperText.setStyle("-fx-font-size: 13px; -fx-text-fill: #5e6c7b;");

        FlowPane controlsRow = new FlowPane(14, 12, filterBlock, previewBtn);
        controlsRow.setAlignment(Pos.CENTER_LEFT);
        controlsRow.setMaxWidth(Double.MAX_VALUE);

        FlowPane metrics = new FlowPane(12, 12,
            metricCard("Selected Date", selectedDateValue, "#eef4ff", "#2854a3"),
            metricCard("Total Records", totalRecordsValue, "#fff5d9", "#9a6700"),
            metricCard("Present", presentValue, "#ebf8ef", "#167c47"),
            metricCard("Absent", absentValue, "#fff0ee", "#c53b2a")
        );
        metrics.setAlignment(Pos.CENTER_LEFT);
        metrics.setMaxWidth(Double.MAX_VALUE);

        VBox controlsCard = new VBox(16, controlsRow, helperText, metrics);
        controlsCard.setPadding(new Insets(20));
        controlsCard.setMaxWidth(Double.MAX_VALUE);
        controlsCard.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 24; -fx-border-color: rgba(242,231,207,0.9); -fx-border-radius: 24;");

        Label tableHelp = new Label("Attendance records fill the full workspace so you can review the day before generating the report.");
        tableHelp.setStyle("-fx-font-size: 13px; -fx-text-fill: #5e6c7b;");

        VBox tableCard = new VBox(14, sectionTitle("Attendance Timeline"), tableHelp, table);
        tableCard.setPadding(new Insets(20));
        tableCard.setMaxWidth(Double.MAX_VALUE);
        tableCard.setStyle("-fx-background-color: rgba(255,255,255,0.96); -fx-background-radius: 24; -fx-border-color: rgba(230,235,242,0.95); -fx-border-radius: 24;");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        loadAttendance(java.time.LocalDate.now());

        root.getChildren().addAll(controlsCard, tableCard);
    }

    private void showDailyReportPreview(java.time.LocalDate date, ObservableList<AttendanceRow> rows) {
        DailyReportPreviewSupport.showDailyReportPreview(getClass(), date, rows);
    }

    public Node getRoot() {
        return layout;
    }

    private void loadAttendance(java.time.LocalDate date) {
        table.getItems().clear();
        ObservableList<AttendanceRow> rows = DailyReportDataSupport.loadAttendance(date);
        table.setItems(rows);
        updateSummary(date, rows);
        updateTableHeight(rows == null ? 0 : rows.size());
    }

    private void updateTableHeight(int rowCount) {
        double rowHeight = table.getFixedCellSize() > 0 ? table.getFixedCellSize() : 44;
        int visibleRows = Math.max(4, Math.min(Math.max(rowCount, 1), 8));
        double headerHeight = 52;
        table.setPrefHeight(headerHeight + (visibleRows * rowHeight));
    }

    private HBox buildHeaderBar(String titleText) {
        HBox headerBar = new HBox(18);
        headerBar.setAlignment(Pos.CENTER_LEFT);
        headerBar.setPrefHeight(70);
        headerBar.setMaxWidth(Double.MAX_VALUE);
        headerBar.setStyle(
            "-fx-background-color: #2e8b57, #FECF4D;"
                + "-fx-background-insets: 0, 0 0 3 0;"
                + "-fx-background-radius: 0, 0;"
        );

        ImageView honeyPot = new ImageView(ImageLoader.loadSafe("bee-buku.png"));
        honeyPot.setFitWidth(54);
        honeyPot.setFitHeight(54);

        Label headerTitle = new Label(titleText);
        headerTitle.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 44));
        headerTitle.setStyle("-fx-text-fill: #181818;");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        headerBar.getChildren().addAll(honeyPot, headerTitle, headerSpacer);
        return headerBar;
    }

    private void updateSummary(LocalDate date, ObservableList<AttendanceRow> rows) {
        selectedDateValue.setText(date == null
            ? "-"
            : date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)));
        totalRecordsValue.setText(String.valueOf(rows == null ? 0 : rows.size()));

        int presentCount = 0;
        int absentCount = 0;
        if (rows != null) {
            for (AttendanceRow row : rows) {
                if (row == null) {
                    continue;
                }
                if ("attend".equalsIgnoreCase(row.getStatus())) {
                    presentCount++;
                } else if ("absence".equalsIgnoreCase(row.getStatus())) {
                    absentCount++;
                }
            }
        }
        presentValue.setText(String.valueOf(presentCount));
        absentValue.setText(String.valueOf(absentCount));
    }

    private static VBox metricCard(String labelText, Label valueLabel, String bgColor, String valueColor) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #7b8798;");
        valueLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + valueColor + ";");

        VBox card = new VBox(6, label, valueLabel);
        card.setMinWidth(170);
        card.setPadding(new Insets(14, 18, 14, 18));
        card.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 18;");
        return card;
    }

    private static Label metricValueLabel(String text) {
        Label label = new Label(text);
        label.setFont(javafx.scene.text.Font.font("Poppins", FontWeight.BOLD, 20));
        return label;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setFont(javafx.scene.text.Font.font("Poppins", FontWeight.BOLD, 20));
        label.setStyle("-fx-text-fill: #1d2a3a;");
        return label;
    }

    private static Label sectionEyebrow(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #c28519;");
        return label;
    }

    private static Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.setFont(javafx.scene.text.Font.font("Poppins", FontWeight.BOLD, 15));
        button.setStyle(
            "-fx-background-color: linear-gradient(to right, #ffcb3c 0%, #f2b72a 100%);"
                + "-fx-text-fill: #2b1f00;"
                + "-fx-padding: 12 20 12 20;"
                + "-fx-background-radius: 14;"
        );
        return button;
    }
}