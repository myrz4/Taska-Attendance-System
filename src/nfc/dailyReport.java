package nfc;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.FontWeight;

public class dailyReport {
    private final VBox root;
    private final DatePicker datePicker;
    private final TableView<AttendanceRow> table;

    public dailyReport() {
        root = new VBox(24);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(36, 20, 20, 20));
        root.setStyle("-fx-background-color:linear-gradient(to bottom right, #86d67f 0%, #76cc6e 100%);");

        Label title = new Label("Daily Attendance Report");
        title.setFont(javafx.scene.text.Font.font("Poppins", FontWeight.BOLD, 28));
        title.setStyle("-fx-text-fill: #222;");

        // Date picker
        HBox dateRow = new HBox(8);
        dateRow.setAlignment(Pos.CENTER_LEFT);
        Label dateLabel = new Label("Select Date:");
        dateLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        datePicker = new DatePicker(java.time.LocalDate.now());
        datePicker.setPrefWidth(160);
        datePicker.valueProperty().addListener((obs, old, selected) -> loadAttendance(selected));
        dateRow.getChildren().addAll(dateLabel, datePicker);

        // Table
        table = new TableView<>();
        DailyReportTableSupport.setupTable(table);

        // Load for today on startup
        loadAttendance(java.time.LocalDate.now());

        root.getChildren().addAll(title, dateRow, table);

        // Generate ALL button (UI only)
        Button generateAllBtn = new Button("Generate ALL");
        generateAllBtn.setFont(javafx.scene.text.Font.font("Poppins", 16));
        generateAllBtn.setStyle("-fx-background-color:#FFCB3C;-fx-background-radius:18;-fx-font-weight:bold;");
        generateAllBtn.setOnAction(e -> {
            java.time.LocalDate selectedDate = datePicker.getValue();
            showDailyReportPreview(selectedDate, FXCollections.observableArrayList(table.getItems()));
        });

        VBox.setMargin(generateAllBtn, new Insets(24, 0, 0, 0));
        root.getChildren().add(generateAllBtn);
    }

    private void showDailyReportPreview(java.time.LocalDate date, ObservableList<AttendanceRow> rows) {
        DailyReportPreviewSupport.showDailyReportPreview(getClass(), date, rows);
    }

    public Node getRoot() {
        return root;
    }

    private void loadAttendance(java.time.LocalDate date) {
        table.getItems().clear();
        table.setItems(DailyReportDataSupport.loadAttendance(date));
    }
}