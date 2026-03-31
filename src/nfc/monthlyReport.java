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
import java.util.Date;

public class monthlyReport {
    private List<FsDocument> cachedAttendance = new ArrayList<>();
    private final Map<String, StudentInfo> childCache = new HashMap<>();
	private StudentInfo getStudentInfoFromDB(String childId) {
        String sql = "SELECT child_id, name, parent_name, parent_contact FROM children WHERE child_id = ?";
        System.out.println("⚠️ Skipped MySQL query — Firestore integration coming soon.");
        return new StudentInfo(childId, "Unknown", "Unknown", "Unknown");
    }

    private List<AttendanceRow> getAttendanceRowsForStudentMonth(String childId, int month, int year) {
        List<AttendanceRow> list = new ArrayList<>();

        try {
            Map<LocalDate, AttendanceRow> groupedByDate = new LinkedHashMap<>();

            for (FsDocument doc : cachedAttendance) {
                String idStr = String.valueOf(doc.getString("childId"));
                if (!idStr.equals(childId)) continue;

                Date dateObj = doc.getDate("date");
                if (dateObj == null) continue;
                LocalDate docDate = dateObj.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();

                if (docDate.getMonthValue() != month || docDate.getYear() != year) continue;

                if (!groupedByDate.containsKey(docDate)) {
                    String name = String.valueOf(doc.getString("name"));
                    String reason = String.valueOf(doc.getString("reason"));
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
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            cachedAttendance = new ArrayList<>(client.listDocuments("attendance"));
            System.out.println("✅ Attendance cached (REST): " + cachedAttendance.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void preloadChildren() {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            List<FsDocument> snap = client.listDocuments("children");
            for (FsDocument doc : snap) {
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
            System.out.println("✅ Children cached (REST): " + childCache.size());
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
            logoImg = new Image(getClass().getResourceAsStream("/nfc/logo.png"));
        } catch (Exception e) {
            logoImg = null; // fallback or placeholder
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
        
        Label performanceLabel = new Label(
            "Performance: " + performance.toUpperCase()
        );
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
        checkInCol.setCellValueFactory(data -> new SimpleStringProperty(
            formatTime(data.getValue().getCheckInTime()))
        );
        checkInCol.setPrefWidth(130);

        TableColumn<AttendanceRow, String> checkOutCol = new TableColumn<>("CHECK-OUT");
        checkOutCol.setCellValueFactory(data -> new SimpleStringProperty(
            formatTime(data.getValue().getCheckOutTime()))
        );
        checkOutCol.setPrefWidth(130);

        TableColumn<AttendanceRow, String> reasonCol = new TableColumn<>("REASON");
        reasonCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getReason()));
        reasonCol.setPrefWidth(220);

        TableView<AttendanceRow> attendanceTable = new TableView<>(FXCollections.observableArrayList(days));
        attendanceTable.getColumns().addAll(dateCol, statusCol, checkInCol, checkOutCol, reasonCol);
        attendanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        attendanceTable.setPrefHeight(360);
        attendanceTable.setPlaceholder(new Label("No attendance records available for this month."));
        attendanceTable.setFixedCellSize(38);
        attendanceTable.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: transparent;" +
            "-fx-selection-bar: #ffe4a8;" +
            "-fx-selection-bar-non-focused: #ffeec7;"
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

        VBox tableCard = new VBox(14, sectionTitle("Attendance Timeline"), attendanceTable);
        tableCard.setPadding(new Insets(20));
        tableCard.setStyle("-fx-background-color: white; -fx-background-radius: 22; -fx-border-color: #e6ebf2; -fx-border-radius: 22;");

        Button saveBtn = new Button("Save as PDF");
        saveBtn.setStyle(
            "-fx-background-color: linear-gradient(to right, #ffcb3c 0%, #f2b72a 100%);" +
            "-fx-text-fill: #2b1f00;" +
            "-fx-font-size: 15px;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 12 22 12 22;" +
            "-fx-background-radius: 14;"
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
                        file.getAbsolutePath(), "src/nfc/logo.png", info, days, month, year, attendancePercent, performance
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

        HBox actionBar = new HBox(saveBtn);
        actionBar.setAlignment(Pos.CENTER_RIGHT);

        page.getChildren().addAll(header, infoCard, metrics, tableCard, actionBar);

        ScrollPane scrollPane = new ScrollPane(page);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #f6f8fc; -fx-background-color: #f6f8fc;");

        previewStage.setScene(new Scene(scrollPane, 980, 760));
        previewStage.show();
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #1d2a3a;");
        return label;
    }

    private Label reportFieldLabel(String text) {
        Label label = new Label(text.toUpperCase());
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #8a94a6;");
        return label;
    }

    private Label reportFieldValue(String text) {
        Label label = new Label(text == null || text.isBlank() ? "-" : text);
        label.setStyle("-fx-font-size: 15px; -fx-font-weight: 600; -fx-text-fill: #253041;");
        label.setWrapText(true);
        return label;
    }

    private VBox metricCard(String labelText, String valueText, String bgColor, String valueColor) {
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

        for (FsDocument doc : cachedAttendance) {
            java.util.Date dateObj = doc.getDate("date");
            if (dateObj == null) continue;

            LocalDate docDate = dateObj.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();

            if (docDate.getYear() != year || docDate.getMonthValue() != month) {
                continue;
            }

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