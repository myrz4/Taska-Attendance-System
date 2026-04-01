package nfc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class monthlyReport {
    private List<FsDocument> cachedAttendance = new ArrayList<>();
    private Map<String, StudentInfo> childCache = new HashMap<>();

    private final VBox root = new VBox(12);
    private final TableView<StudentMonthlyAttendance> table = new TableView<>();
    private final ComboBox<String> monthDropdown = new ComboBox<>();
    private final ComboBox<Integer> yearDropdown = new ComboBox<>();

    public monthlyReport() {
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color:linear-gradient(to bottom right, #86d67f 0%, #76cc6e 100%);");

        Label title = new Label("Monthly Attendance Report");
        title.setFont(Font.font("Poppins", FontWeight.BOLD, 24));

        // Month Dropdown
        monthDropdown.getItems().addAll(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        );
        int currentMonthIndex = java.time.LocalDate.now().getMonthValue() - 1; // 0-based for ComboBox
        monthDropdown.setValue(monthDropdown.getItems().get(currentMonthIndex));


        // Year Dropdown (e.g., from 2020 to current year)
        int currentYear = java.time.Year.now().getValue();
        for (int y = 2020; y <= currentYear; y++) {
            yearDropdown.getItems().add(y);
        }
        yearDropdown.setValue(currentYear);

        // Add listeners to auto refresh on selection change
        monthDropdown.setOnAction(e -> loadMonthlyReport());
        yearDropdown.setOnAction(e -> loadMonthlyReport());

        HBox controls = new HBox(15, new Label("Select Month:"), monthDropdown,
                                     new Label("Year:"), yearDropdown);
        controls.setAlignment(Pos.CENTER);

        MonthlyReportTableSupport.setupTable(
            table,
            () -> monthDropdown.getSelectionModel().getSelectedIndex() + 1,
            () -> yearDropdown.getValue(),
            monthDropdown::getValue,
            () -> cachedAttendance,
            this::showMonthlyReportPreview
        );
        
         root.getChildren().addAll(title, controls, table);

        // 🔥 LOAD DATA ONCE
        cachedAttendance = MonthlyReportDataSupport.preloadAttendance();
        childCache = MonthlyReportDataSupport.preloadChildren();

        // Initial load (FAST)
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

    static String formatReportTime(java.util.Date date) {
        if (date == null) return "-";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("hh:mm a");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        return sdf.format(date);
    }

    private void loadMonthlyReport() {
        int month = monthDropdown.getSelectionModel().getSelectedIndex() + 1;
        int year = yearDropdown.getValue();

        table.setItems(MonthlyReportDataSupport.buildMonthlyReportData(cachedAttendance, childCache, month, year));
        System.out.println("✅ Monthly report loaded FAST for " + month + "/" + year);
    }

    public VBox getRoot() {
        return root;
    }
    // You will need to create this StudentMonthlyAttendance model class with properties
}