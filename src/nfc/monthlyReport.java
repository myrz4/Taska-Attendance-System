package nfc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;

import javafx.scene.control.TextField; // ✅ Use this
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.image.Image;

// 🔥 Firebase Firestore imports
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;

public class monthlyReport {
    private List<QueryDocumentSnapshot> cachedAttendance = new ArrayList<>();
    private final Map<String, StudentInfo> childCache = new HashMap<>();
	private StudentInfo getStudentInfoFromDB(String childId) {
        String sql = "SELECT child_id, name, parent_name, parent_contact FROM children WHERE child_id = ?";
        System.out.println("⚠️ Skipped MySQL query — Firestore integration coming soon.");
        return new StudentInfo(childId, "Unknown", "Unknown", "Unknown");
    }

    private List<AttendanceRow> getAttendanceRowsForStudentMonth(String childId, int month, int year) {
        List<AttendanceRow> list = new ArrayList<>();

        try {
            Firestore db = FirestoreService.db();
            ApiFuture<QuerySnapshot> future = db.collection("attendance")
                                                .whereEqualTo("childId", childId)
                                                .get();
            QuerySnapshot snapshots = future.get();

            Map<LocalDate, AttendanceRow> groupedByDate = new LinkedHashMap<>();

            for (DocumentSnapshot doc : snapshots.getDocuments()) {
                Object dateObj = doc.get("date");
                if (dateObj == null) continue;

                LocalDate docDate = null;
                if (dateObj instanceof com.google.cloud.Timestamp ts) {
                    docDate = ts.toDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                } else if (dateObj instanceof String s) {
                    try {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                            "EEE MMM dd yyyy HH:mm:ss 'GMT'Z", java.util.Locale.ENGLISH
                        );
                        java.util.Date parsed = sdf.parse(s);
                        docDate = parsed.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                    } catch (Exception ignored) {}
                }

                if (docDate == null || docDate.getMonthValue() != month || docDate.getYear() != year)
                    continue;

                // Skip duplicate dates — one per day only
                String idStr = String.valueOf(doc.get("childId"));
                if (!idStr.equals(childId)) continue;

                // Skip duplicate dates — one per day only
                if (!groupedByDate.containsKey(docDate)) {
                    String name = String.valueOf(doc.get("name"));
                    String reason = String.valueOf(doc.get("reason"));
                    boolean present = Boolean.TRUE.equals(doc.getBoolean("isPresent"));
                    String status = present ? "attend" : "absence";

                    java.util.Date checkInDate = doc.getDate("check_in_time");
                    java.util.Date checkOutDate = doc.getDate("check_out_time");

                    String checkIn = (checkInDate != null) ? formatTime(checkInDate) : "-";
                    String checkOut = (checkOutDate != null) ? formatTime(checkOutDate) : "-";

                    groupedByDate.put(docDate, new AttendanceRow(
                        idStr, name, status,
                        (reason != null ? reason : ""),
                        checkIn, checkOut, docDate
                    ));
                }
            }

            // ✅ Convert grouped data to list
            list.addAll(groupedByDate.values());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ Error loading attendance rows: " + e.getMessage());
        }
        return list;
    }

    private void preloadAttendance() {
        try {
            Firestore db = FirestoreService.db();
            ApiFuture<QuerySnapshot> future = db.collection("attendance").get();
            cachedAttendance = new ArrayList<>(future.get().getDocuments());
            System.out.println("✅ Attendance cached: " + cachedAttendance.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void preloadChildren() {
        try {
            Firestore db = FirestoreService.db();
            QuerySnapshot snap = db.collection("children").get().get();

            for (DocumentSnapshot doc : snap.getDocuments()) {
                childCache.put(
                    doc.getId(),
                    new StudentInfo(
                        doc.getId(),
                        doc.getString("name"),
                        doc.getString("parentName"),
                        doc.getString("parentContact")
                    )
                );
            }
            System.out.println("✅ Children cached: " + childCache.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private VBox root;
    private TableView<StudentMonthlyAttendance> table;
    private ComboBox<String> monthDropdown;
    private ComboBox<Integer> yearDropdown;

    public monthlyReport() {
        root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color:linear-gradient(to bottom right, #86d67f 0%, #76cc6e 100%);");

        Label title = new Label("Monthly Attendance Report");
        title.setFont(Font.font("Poppins", FontWeight.BOLD, 24));

        // Month Dropdown
        monthDropdown = new ComboBox<>();
        monthDropdown.getItems().addAll(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        );
        int currentMonthIndex = java.time.LocalDate.now().getMonthValue() - 1; // 0-based for ComboBox
        monthDropdown.setValue(monthDropdown.getItems().get(currentMonthIndex));


        // Year Dropdown (e.g., from 2020 to current year)
        yearDropdown = new ComboBox<>();
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

        // Table Setup
        table = new TableView<>();
        setupTable();
        
         root.getChildren().addAll(title, controls, table);

        // 🔥 LOAD DATA ONCE
        preloadAttendance();
        preloadChildren();

        // Initial load (FAST)
        loadMonthlyReport();
    }

    private void setupTable() {
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
        downloadCol.setPrefWidth(140);
        downloadCol.setCellFactory(col -> new TableCell<StudentMonthlyAttendance, Void>() {
            private final Button btn = new Button("⬇");
            {
                btn.setOnAction(e -> {
                    StudentMonthlyAttendance row = getTableView().getItems().get(getIndex());

                    // ✅ Use actual values from row, not placeholder DB call
                    StudentInfo info = new StudentInfo(
                        row.getChildId(),
                        row.getName(),
                        row.getParentName(),
                        row.getParentContact()
                    );

                    int selectedMonth = monthDropdown.getSelectionModel().getSelectedIndex() + 1;
                    int selectedYear = yearDropdown.getValue();
                    String selectedMonthName = monthDropdown.getValue();

                    List<AttendanceRow> days = getAttendanceRowsForStudentMonth(row.getChildId(), selectedMonth, selectedYear);

                    showMonthlyReportPreview(
                        info,
                        days,
                        selectedMonthName,
                        String.valueOf(selectedYear),
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

        downloadCol.setPrefWidth(100);


        table.getColumns().addAll(idCol, nameCol,percentCol, performanceCol, downloadCol);
        
        table.setRowFactory(tv -> new TableRow<StudentMonthlyAttendance>() {
            @Override
            protected void updateItem(StudentMonthlyAttendance item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if ("Good".equalsIgnoreCase(item.getPerformance())) {
                    setStyle("-fx-background-color: #e7ffe9;"); // green
                } else {
                    setStyle("-fx-background-color: #ffe7e7;"); // red
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
                    StudentMonthlyAttendance att = getTableView().getItems().get(getIndex());
                    setStyle("Good".equalsIgnoreCase(att.getPerformance()) ?
                        "-fx-text-fill: #087400; -fx-font-weight: bold;" :
                        "-fx-text-fill: #c62828; -fx-font-weight: bold;");
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
                    setStyle("Good".equalsIgnoreCase(item) ?
                        "-fx-text-fill: #087400; -fx-font-weight: bold;" :
                        "-fx-text-fill: #c62828; -fx-font-weight: bold;");
                }
            }
        });
    }

    public void showMonthlyReportPreview(StudentInfo info, List<AttendanceRow> days, String month, String year,String attendancePercent, String performance) {
        Stage previewStage = new Stage();
        previewStage.setTitle("Monthly Attendance Report - " + info.childName);

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(20));

        // Logo and Title
        Image logoImg;
        try {
            logoImg = new Image(getClass().getResourceAsStream("/nfc/logo.png"));
        } catch (Exception e) {
            logoImg = null; // fallback or placeholder
        }
        ImageView logo = new ImageView(logoImg);
        logo.setFitHeight(50); logo.setFitWidth(50);

        Label title = new Label("TASKA ZURAH STUDENT REPORT");
        title.setStyle("-fx-font-size: 22px; -fx-text-fill: orange; -fx-font-weight: bold;");

      

        GridPane infoPane = new GridPane();
        infoPane.setVgap(4);
        infoPane.setHgap(6);
        infoPane.addRow(0, new Label("CHILD ID:"), new Label(info.childId));
        infoPane.addRow(1, new Label("NAME:"), new Label(info.childName));
        infoPane.addRow(2, new Label("PARENT NAME:"), new Label(info.parentName));
        infoPane.addRow(3, new Label("CONTACT NUMBER:"), new Label(info.parentContact)); 
        infoPane.addRow(4, new Label("MONTH:"), new Label(month.toUpperCase()));
        infoPane.addRow(5, new Label("YEAR:"), new Label(year));
        
        Label performanceLabel = new Label(
                "Attendance: " + attendancePercent + "   |   Performance: " + performance.toUpperCase()
            );
            performanceLabel.setFont(Font.font("Poppins", FontWeight.BOLD, 15));
            if ("Good".equalsIgnoreCase(performance)) {
                performanceLabel.setStyle(
                    "-fx-background-color: #e7ffe9; -fx-text-fill: #087400; -fx-padding: 10 0 10 10; -fx-background-radius: 8;"
                );
            } else {
                performanceLabel.setStyle(
                    "-fx-background-color: #ffe7e7; -fx-text-fill: #c62828; -fx-padding: 10 0 10 10; -fx-background-radius: 8;"
                );
            }
            performanceLabel.setMaxWidth(Double.MAX_VALUE);

        // TableView for Attendance (read-only or editable as you wish)
        TableColumn<AttendanceRow, String> dateCol = new TableColumn<>("DATE");
        dateCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getDate() != null
                ? data.getValue().getDate().format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                : "")
        );
        dateCol.setPrefWidth(100);

        TableColumn<AttendanceRow, String> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
        statusCol.setPrefWidth(80);

        TableColumn<AttendanceRow, String> checkInCol = new TableColumn<>("CHECK-IN");
        checkInCol.setCellValueFactory(data -> new SimpleStringProperty(
            formatTime(data.getValue().getCheckInTime()))
        );
        checkInCol.setPrefWidth(80);

        TableColumn<AttendanceRow, String> checkOutCol = new TableColumn<>("CHECK-OUT");
        checkOutCol.setCellValueFactory(data -> new SimpleStringProperty(
            formatTime(data.getValue().getCheckOutTime()))
        );
        checkOutCol.setPrefWidth(80);

        TableColumn<AttendanceRow, String> reasonCol = new TableColumn<>("REASON");
        reasonCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getReason()));
        reasonCol.setPrefWidth(100);

        TableView<AttendanceRow> attendanceTable = new TableView<>(FXCollections.observableArrayList(days));
        
		// Add columns to table
        attendanceTable.getColumns().addAll(dateCol, statusCol, checkInCol, checkOutCol, reasonCol);

        // Optional: autosize to content
        attendanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        attendanceTable.setPrefHeight(200);

        // Button to Save as PDF
        Button saveBtn = new Button("Save as PDF");
        saveBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save PDF");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            fileChooser.setInitialFileName(info.childName + "_report.pdf");
            File file = fileChooser.showSaveDialog(previewStage);

            if (file != null) {
                try {
                    PDFReport.generateStudentMonthlyReport(
                        file.getAbsolutePath(), "src/nfc/logo.png", info, days, month, year
                    );
                    Platform.runLater(() -> 
                        new Alert(Alert.AlertType.INFORMATION, "PDF saved!").showAndWait()
                    );
                } catch (Exception ex) {
                    ex.printStackTrace();
                    new Alert(Alert.AlertType.ERROR, "Failed to generate PDF: " + ex.getMessage()).showAndWait();
                }
            }
        });


        layout.getChildren().addAll(logo, title, infoPane, performanceLabel, attendanceTable, saveBtn);
        previewStage.setScene(new Scene(layout, 650, 600));
        previewStage.show();
    }

    private String formatTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return "";
        try {
            DateTimeFormatter input = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            DateTimeFormatter output = DateTimeFormatter.ofPattern("h:mm a");
            LocalDateTime dt = LocalDateTime.parse(timeStr, input);
            return dt.format(output);
        } catch (Exception e) {
            // If already formatted or invalid, return as is
            return timeStr;
        }
    }

    private String formatTime(java.util.Date date) {
        if (date == null) return "-";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("hh:mm a");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        return sdf.format(date);
    }

    private void loadMonthlyReport() {
        int month = monthDropdown.getSelectionModel().getSelectedIndex() + 1;
        int year = yearDropdown.getValue();

        ObservableList<StudentMonthlyAttendance> data = FXCollections.observableArrayList();

        Map<String, Integer> totalDays = new HashMap<>();
        Map<String, Integer> presentDays = new HashMap<>();

        for (DocumentSnapshot doc : cachedAttendance) {
            Object dateObj = doc.get("date");
            if (dateObj == null) continue;

            LocalDate docDate = null;
            if (dateObj instanceof com.google.cloud.Timestamp ts) {
                docDate = ts.toDate().toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate();
            }

            if (docDate == null || docDate.getYear() != year || docDate.getMonthValue() != month)
                continue;

            String childId = doc.getString("childId");
            boolean present = Boolean.TRUE.equals(doc.getBoolean("isPresent"));

            totalDays.put(childId, totalDays.getOrDefault(childId, 0) + 1);
            if (present) {
                presentDays.put(childId, presentDays.getOrDefault(childId, 0) + 1);
            }
        }

        for (String childId : totalDays.keySet()) {
            int total = totalDays.get(childId);
            int present = presentDays.getOrDefault(childId, 0);
            double percent = total > 0 ? (present * 100.0 / total) : 0;

            StudentInfo info = childCache.get(childId);
            if (info == null) continue;

            StudentMonthlyAttendance record = new StudentMonthlyAttendance(
                childId,
                info.childName,
                String.format("%.2f%%", percent),
                percent >= 80 ? "Good" : "Poor"
            );

            record.setParentName(info.parentName);
            record.setParentContact(info.parentContact);
            data.add(record);
        }

        table.setItems(data);
        System.out.println("✅ Monthly report loaded FAST for " + month + "/" + year);
    }

    public VBox getRoot() {
        return root;
    }
    // You will need to create this StudentMonthlyAttendance model class with properties
}