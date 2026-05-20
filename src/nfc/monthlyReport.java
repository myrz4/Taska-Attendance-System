package nfc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class monthlyReport {
    private List<FsDocument> cachedAttendance = new ArrayList<>();
    private Map<String, StudentInfo> childCache = new HashMap<>();

    private final javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(18);
    private final javafx.scene.layout.BorderPane layout;
    private final ScrollPane scrollRoot;
    private final javafx.scene.control.TableView<StudentMonthlyAttendance> table = new javafx.scene.control.TableView<>();
    private final javafx.scene.control.ComboBox<String> monthDropdown = new javafx.scene.control.ComboBox<>();
    private final javafx.scene.control.ComboBox<Integer> yearDropdown = new javafx.scene.control.ComboBox<>();
    private final Label periodValue = metricValueLabel("-");
    private final Label reportCountValue = metricValueLabel("0");
    private final Label averageAttendanceValue = metricValueLabel("0%");
    private final Label goodPerformanceValue = metricValueLabel("0");

    public monthlyReport() {
        root.setPadding(new Insets(20, 18, 20, 18));
        root.setFillWidth(true);
        root.setAlignment(Pos.TOP_LEFT);
        root.setStyle("-fx-background-color: #7bcf74;");

        scrollRoot = new ScrollPane(root);
        scrollRoot.setFitToWidth(true);
        scrollRoot.setPannable(true);
        scrollRoot.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollRoot.setStyle("-fx-background: #7bcf74; -fx-background-color: #7bcf74;");

        layout = new BorderPane();
        layout.setTop(buildHeaderBar("Monthly Attendance Report"));
        layout.setCenter(scrollRoot);
        layout.setStyle("-fx-background-color: #7bcf74;");

        monthDropdown.getItems().addAll(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        );
        int currentMonthIndex = java.time.LocalDate.now().getMonthValue() - 1;
        monthDropdown.setValue(monthDropdown.getItems().get(currentMonthIndex));

        int currentYear = java.time.Year.now().getValue();
        for (int y = 2020; y <= currentYear; y++) {
            yearDropdown.getItems().add(y);
        }
        yearDropdown.setValue(currentYear);

        monthDropdown.setOnAction(e -> loadMonthlyReport());
        yearDropdown.setOnAction(e -> loadMonthlyReport());

        Label periodLabel = sectionEyebrow("REPORT PERIOD");
        VBox monthBlock = new VBox(6, periodLabel, monthDropdown);
        VBox yearBlock = new VBox(6, sectionEyebrow("YEAR"), yearDropdown);
        monthBlock.setAlignment(Pos.CENTER_LEFT);
        yearBlock.setAlignment(Pos.CENTER_LEFT);

        Label helperText = new Label("Browse all student summaries for the selected month, then open each record in the same polished preview format used by the daily report.");
        helperText.setWrapText(true);
        helperText.setStyle("-fx-font-size: 13px; -fx-text-fill: #5e6c7b;");

        Button refreshBtn = createPrimaryButton("Refresh Summary");
        refreshBtn.setOnAction(e -> loadMonthlyReport());

        FlowPane controlsRow = new FlowPane(14, 12, monthBlock, yearBlock, refreshBtn);
        controlsRow.setAlignment(Pos.CENTER_LEFT);
        controlsRow.setMaxWidth(Double.MAX_VALUE);

        FlowPane metrics = new FlowPane(12, 12,
            metricCard("Selected Period", periodValue, "#eef4ff", "#2854a3"),
            metricCard("Student Reports", reportCountValue, "#fff5d9", "#9a6700"),
            metricCard("Average Attendance", averageAttendanceValue, "#ebf8ef", "#167c47"),
            metricCard("Good Performance", goodPerformanceValue, "#fff0ee", "#c53b2a")
        );
        metrics.setAlignment(Pos.CENTER_LEFT);
        metrics.setMaxWidth(Double.MAX_VALUE);

        VBox controlsCard = new VBox(16, controlsRow, helperText, metrics);
        controlsCard.setPadding(new Insets(20));
        controlsCard.setMaxWidth(Double.MAX_VALUE);
        controlsCard.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 24; -fx-border-color: rgba(242,231,207,0.9); -fx-border-radius: 24;");

        MonthlyReportTableSupport.setupTable(
            table,
            () -> monthDropdown.getSelectionModel().getSelectedIndex() + 1,
            () -> yearDropdown.getValue(),
            monthDropdown::getValue,
            () -> cachedAttendance,
            this::showMonthlyReportPreview
        );

        table.setMaxWidth(Double.MAX_VALUE);
    table.setMinHeight(0);

        Label tableHelp = new Label("Each row opens a full preview for one child, with the monthly attendance timeline and summary cards ready for PDF export.");
        tableHelp.setStyle("-fx-font-size: 13px; -fx-text-fill: #5e6c7b;");

        VBox tableCard = new VBox(14, sectionTitle("Monthly Student Summaries"), tableHelp, table);
        tableCard.setPadding(new Insets(20));
        tableCard.setMaxWidth(Double.MAX_VALUE);
        tableCard.setStyle("-fx-background-color: rgba(255,255,255,0.96); -fx-background-radius: 24; -fx-border-color: rgba(230,235,242,0.95); -fx-border-radius: 24;");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        root.getChildren().addAll(controlsCard, tableCard);

        cachedAttendance = MonthlyReportDataSupport.preloadAttendance();
        childCache = MonthlyReportDataSupport.preloadChildren();

        loadMonthlyReport();
    }

    public void showMonthlyReportPreview(StudentInfo info, List<AttendanceRow> days, String month, String year,String attendancePercent, String performance) {
        MonthlyReportPreviewSupport.showMonthlyReportPreview(
            getClass(),
            info,
            days,
            month,
            year,
            attendancePercent,
            performance
        );
    }

    @SuppressWarnings("unused")
    static String formatReportTime(java.util.Date date) {
        if (date == null) return "-";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("hh:mm a");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        return sdf.format(date);
    }

    private void loadMonthlyReport() {
        int month = monthDropdown.getSelectionModel().getSelectedIndex() + 1;
        int year = yearDropdown.getValue();

        ObservableList<StudentMonthlyAttendance> rows = MonthlyReportDataSupport.buildMonthlyReportData(cachedAttendance, childCache, month, year);
        table.setItems(rows);
        updateSummary(rows);
        updateTableHeight(rows == null ? 0 : rows.size());
        System.out.println("✅ Monthly report loaded FAST for " + month + "/" + year);
    }

    public javafx.scene.Node getRoot() {
        return layout;
    }

    private void updateTableHeight(int rowCount) {
        double rowHeight = table.getFixedCellSize() > 0 ? table.getFixedCellSize() : 44;
        int visibleRows = Math.max(5, Math.min(Math.max(rowCount, 1), 9));
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

    private void updateSummary(ObservableList<StudentMonthlyAttendance> rows) {
        String month = monthDropdown.getValue();
        Integer year = yearDropdown.getValue();
        periodValue.setText((month == null || month.isBlank() ? "-" : month) + (year == null ? "" : " " + year));
        reportCountValue.setText(String.valueOf(rows == null ? 0 : rows.size()));

        int goodCount = 0;
        double totalAttendance = 0.0;
        int parsedAttendanceCount = 0;

        if (rows != null) {
            for (StudentMonthlyAttendance row : rows) {
                if (row == null) {
                    continue;
                }
                if ("Good".equalsIgnoreCase(row.getPerformance())) {
                    goodCount++;
                }
                double parsed = parsePercentage(row.getAttendancePercent());
                if (!Double.isNaN(parsed)) {
                    totalAttendance += parsed;
                    parsedAttendanceCount++;
                }
            }
        }

        double averageAttendance = parsedAttendanceCount == 0 ? 0.0 : totalAttendance / parsedAttendanceCount;
        averageAttendanceValue.setText(String.format(Locale.ENGLISH, "%.1f%%", averageAttendance));
        goodPerformanceValue.setText(String.valueOf(goodCount));
    }

    private static double parsePercentage(String value) {
        if (value == null || value.isBlank()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(value.replace("%", "").trim());
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private static VBox metricCard(String labelText, Label valueLabel, String bgColor, String valueColor) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #7b8798;");
        valueLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + valueColor + ";");

        VBox card = new VBox(6, label, valueLabel);
        card.setMinWidth(180);
        card.setPadding(new Insets(14, 18, 14, 18));
        card.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 18;");
        return card;
    }

    private static Label metricValueLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Poppins", FontWeight.BOLD, 20));
        return label;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Poppins", FontWeight.BOLD, 20));
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
        button.setFont(Font.font("Poppins", FontWeight.BOLD, 15));
        button.setStyle(
            "-fx-background-color: linear-gradient(to right, #ffcb3c 0%, #f2b72a 100%);"
                + "-fx-text-fill: #2b1f00;"
                + "-fx-padding: 12 20 12 20;"
                + "-fx-background-radius: 14;"
        );
        return button;
    }
}