package nfc;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;

import com.lowagie.text.pdf.draw.LineSeparator;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Date;

// 🔥 Firebase Firestore imports
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;

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
        datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(160);
        datePicker.valueProperty().addListener((obs, old, selected) -> loadAttendance(selected));
        dateRow.getChildren().addAll(dateLabel, datePicker);

        // Table
        table = new TableView<>();
        setupTable();

        // Load for today on startup
        loadAttendance(LocalDate.now());

        root.getChildren().addAll(title, dateRow, table);

        // Generate ALL button (UI only)
        Button generateAllBtn = new Button("Generate ALL");
        generateAllBtn.setFont(javafx.scene.text.Font.font("Poppins", 16));
        generateAllBtn.setStyle("-fx-background-color:#FFCB3C;-fx-background-radius:18;-fx-font-weight:bold;");
        generateAllBtn.setOnAction(e -> {
            LocalDate selectedDate = datePicker.getValue();
            showDailyReportPreview(selectedDate, FXCollections.observableArrayList(table.getItems()));
        });

        VBox.setMargin(generateAllBtn, new Insets(24, 0, 0, 0));
        root.getChildren().add(generateAllBtn);
    }

    private void showDailyReportPreview(LocalDate date, ObservableList<AttendanceRow> rows) {
        Stage previewStage = new Stage();
        previewStage.setTitle("Preview Daily Attendance Report");

        VBox layout = new VBox(16);
        layout.setPadding(new Insets(24));
        layout.setAlignment(Pos.TOP_CENTER);

        // BEE CALIPH HEADER (can use logo image if you want)
        Image logoImg;
        try {
            logoImg = new Image(getClass().getResourceAsStream("/nfc/logo.png"));
        } catch (Exception e) {
            logoImg = null; // fallback or placeholder
        }
        ImageView logo = new ImageView(logoImg);
        logo.setFitHeight(50); logo.setFitWidth(50);
        Label header = new Label("TASKA ZURAH DAILY ATTENDANCE REPORT");
        header.setFont(javafx.scene.text.Font.font("Poppins", FontWeight.BOLD, 22));
        header.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");

        // Date info
        String dayOfWeek = date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
        Label dateInfo = new Label(dayOfWeek + ", " + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        dateInfo.setFont(javafx.scene.text.Font.font("Poppins", FontWeight.BOLD, 16));

        // Table preview
        TableView<AttendanceRow> previewTable = new TableView<>();

        for (AttendanceRow r : rows) {
            System.out.println("🕒 PREVIEW ROW: " + r.getName() +
                " | CheckIn=" + r.getCheckInTime() +
                " | CheckOut=" + r.getCheckOutTime());
        }

        previewTable.setItems(rows); 
        previewTable.setEditable(true);

        // ✅ Define Child ID column first before styling
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
        colReason.setPrefWidth(80);
        TableColumn<AttendanceRow, String> colCheckOut = new TableColumn<>("Check Out");
        colCheckOut.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        colCheckOut.setCellValueFactory(data -> data.getValue().checkOutTimeProperty());
        
        TableColumn<AttendanceRow, String> colRemark = new TableColumn<>("Remark");
        colRemark.setCellValueFactory(data -> data.getValue().remarkProperty());
        colRemark.setCellFactory(TextFieldTableCell.forTableColumn());
        colRemark.setOnEditCommit(event -> {
            AttendanceRow row = event.getRowValue();
            row.setRemark(event.getNewValue());
        });
        
        
       
        previewTable.getColumns().addAll(colId, colName, colStatus, colReason, colCheckIn, colCheckOut, colRemark);

        Button exportBtn = new Button("Export as PDF");
        exportBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold;");
        exportBtn.setOnAction(ev -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save PDF");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            fileChooser.setInitialFileName("daily_report_" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf");
            File file = fileChooser.showSaveDialog(previewStage);
            if (file != null) {
                generateDailyPDF(file, date, rows);
            }
        });

        layout.getChildren().addAll(logo, header, dateInfo, previewTable, exportBtn);

        Scene scene = new Scene(layout, 860, 600);
        previewStage.setScene(scene);
        previewStage.show();
    }

    private void generateDailyPDF(File file, LocalDate date, ObservableList<AttendanceRow> rows) {
        try {
            Document doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, new FileOutputStream(file));
            doc.open();

            // Top yellow header background with logo and title
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{1f, 6f});

            // Logo cell
            com.lowagie.text.Image logo = com.lowagie.text.Image.getInstance(getClass().getResource("/nfc/logo.png"));
            logo.scaleToFit(70, 70);
            PdfPCell logoCell = new PdfPCell(logo, false);
            logoCell.setBackgroundColor(new Color(255, 203, 60));
            logoCell.setBorder(PdfPCell.NO_BORDER);
            logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

            // Title cell
            com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, new Color(36, 36, 36));
            Paragraph title = new Paragraph("TASKA ZURAH DAILY REPORT", titleFont);
            PdfPCell titleCell = new PdfPCell(title);
            titleCell.setBackgroundColor(new Color(255, 203, 60));
            titleCell.setBorder(PdfPCell.NO_BORDER);
            titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            titleCell.setPaddingLeft(16f);

            headerTable.addCell(logoCell);
            headerTable.addCell(titleCell);
            doc.add(headerTable);
            doc.add(Chunk.NEWLINE);

            // Date (large and bold)
            com.lowagie.text.Font dateFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
            String dateStr = date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH).toUpperCase()
                    + " , " + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            Paragraph dateInfo = new Paragraph(dateStr, dateFont);
            dateInfo.setSpacingAfter(10f);
            doc.add(dateInfo);

            // Table with modern borders and fixed widths
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            float[] widths = {1.3f, 2.5f, 1.8f, 2.4f, 2.0f, 2.0f, 2.6f};
            table.setWidths(widths);

            String[] columns = {"CHILD ID", "NAME", "STATUS", "REASON", "CHECK-IN", "CHECK-OUT", "REMARK"};
            for (String col : columns) {
                PdfPCell cell = new PdfPCell(new Phrase(col, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK)));
                cell.setBackgroundColor(new Color(250, 202, 60));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(6f);
                table.addCell(cell);
            }

            for (AttendanceRow r : rows) {
                // Child ID
                PdfPCell idCell = new PdfPCell(new Phrase(String.valueOf(r.childIdProperty().get()), FontFactory.getFont(FontFactory.HELVETICA, 12, Color.BLACK)));
                idCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(idCell);

                // Name
                PdfPCell nameCell = new PdfPCell(new Phrase(r.nameProperty().get(), FontFactory.getFont(FontFactory.HELVETICA, 12, Color.BLACK)));
                nameCell.setPaddingLeft(5f);
                table.addCell(nameCell);

                // Status with bold and color
                Font statusFont;
                Color color;
                if ("attend".equalsIgnoreCase(r.getStatus())) {
                    color = new Color(0, 135, 38);
                } else {
                    color = new Color(180, 0, 0);
                }
                statusFont = new Font(Font.HELVETICA, 12, Font.BOLD, color);

                PdfPCell statusCell = new PdfPCell(new Phrase(r.getStatus(), statusFont));
                statusCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                if ("attend".equalsIgnoreCase(r.getStatus())) {
                    statusCell.setBackgroundColor(new Color(220, 255, 220));
                } else {
                    statusCell.setBackgroundColor(new Color(255, 235, 235));
                }
                table.addCell(statusCell);

                // Reason
                PdfPCell reasonCell = new PdfPCell(new Phrase(r.getReason() != null ? r.getReason() : "", FontFactory.getFont(FontFactory.HELVETICA, 12, Color.BLACK)));
                table.addCell(reasonCell);

                // Check-in
                PdfPCell checkInCell = new PdfPCell(new Phrase(r.getCheckInTime(), FontFactory.getFont(FontFactory.HELVETICA, 12, Color.BLACK)));
                table.addCell(checkInCell);

                // Check-out
                PdfPCell checkOutCell = new PdfPCell(new Phrase(r.getCheckOutTime(), FontFactory.getFont(FontFactory.HELVETICA, 12, Color.BLACK)));
                table.addCell(checkOutCell);

                // Editable Remark (already set by user in preview)
                PdfPCell remarkCell = new PdfPCell(new Phrase(r.getRemark() != null ? r.getRemark() : "", FontFactory.getFont(FontFactory.HELVETICA, 12, Color.BLACK)));
                table.addCell(remarkCell);
            }
            doc.add(table);

       
         // Spacer with fixed height to push the signature down (experiment to fit your layout)
            PdfPTable spacer = new PdfPTable(1);
            spacer.setWidthPercentage(100);
            spacer.getDefaultCell().setBorder(PdfPCell.NO_BORDER);
            spacer.getDefaultCell().setFixedHeight(180); // Try 180, 200, etc. until you like the position
            spacer.addCell("");
            doc.add(spacer);

            // Signature footer, bottom right
            Paragraph sign = new Paragraph("ASSIGNED TEACHER", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK));
            sign.setAlignment(Element.ALIGN_RIGHT);
            doc.add(sign);

            doc.close();
            new Alert(Alert.AlertType.INFORMATION, "PDF exported!").showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to export PDF: " + e.getMessage()).showAndWait();
        }
    }


    // Helper to format time like "7:05 am" if value is not empty or null
    private String formatTime(String time) {
        try {
            if (time == null || time.trim().isEmpty()) return "";
            LocalTime t = LocalTime.parse(time.substring(11)); // assumes format "yyyy-MM-dd HH:mm:ss"
            return t.format(DateTimeFormatter.ofPattern("h:mm a"));
        } catch (Exception e) {
            return time != null ? time : "";
        }
    }

    // ✅ Format timestamp as 12-hour Malaysia time
    private String formatTime(Date date) {
        if (date == null) return "-";
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("hh:mm a");
        formatter.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        return formatter.format(date);
    }

    public Node getRoot() {
        return root;
    }

    // Table setup
    private void setupTable() {
        DateTimeFormatter dbFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("hh:mm a");

        TableColumn<AttendanceRow, String> colId = new TableColumn<>("Child ID");
        colId.setCellValueFactory(data -> data.getValue().childIdProperty());
        colId.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-alignment: CENTER;");
        colId.setPrefWidth(110);

        Callback<TableColumn<AttendanceRow, Integer>, TableCell<AttendanceRow, Integer>> intCellFactory =
            col -> new TableCell<AttendanceRow, Integer>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    TableRow<AttendanceRow> row = getTableRow();
                    if (empty || item == null || row == null || row.getItem() == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item.toString());
                        String status = row.getItem().getStatus();
                        if ("attend".equalsIgnoreCase(status)) {
                            setStyle("-fx-background-color: #e8f5e9; -fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                        } else if ("absence".equalsIgnoreCase(status)) {
                            setStyle("-fx-background-color: #ffebee; -fx-text-fill: #c62828; -fx-font-weight: bold;");
                        } else {
                            setStyle("");
                        }
                    }
                }
            };

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
                        setText(item); // fallback if parsing fails
                    }
                    String status = row.getItem().getStatus();
                    if ("attend".equalsIgnoreCase(status)) {
                        setStyle("-fx-background-color: #e8f5e9; -fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                    } else if ("absence".equalsIgnoreCase(status)) {
                        setStyle("-fx-background-color: #ffebee; -fx-text-fill: #c62828; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
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
                        setText(item); // fallback
                    }
                    String status = row.getItem().getStatus();
                    if ("attend".equalsIgnoreCase(status)) {
                        setStyle("-fx-background-color: #e8f5e9; -fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                    } else if ("absence".equalsIgnoreCase(status)) {
                        setStyle("-fx-background-color: #ffebee; -fx-text-fill: #c62828; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // Helper for all other columns to apply the color
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
                        String status = row.getItem().getStatus();
                        if ("attend".equalsIgnoreCase(status)) {
                            setStyle("-fx-background-color: #e8f5e9; -fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                        } else if ("absence".equalsIgnoreCase(status)) {
                            setStyle("-fx-background-color: #ffebee; -fx-text-fill: #c62828; -fx-font-weight: bold;");
                        } else {
                            setStyle("");
                        }
                    }
                }
            };

        colName.setCellFactory(coloredCellFactory);
        colStatus.setCellFactory(coloredCellFactory);
        colReason.setCellFactory(coloredCellFactory);

        table.getColumns().setAll(colId, colName, colStatus, colReason, checkInCol, colCheckOut);
        table.setPrefHeight(320);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void loadAttendance(LocalDate date) {
        table.getItems().clear();

        try {
            Firestore db = FirestoreService.db();
            ApiFuture<QuerySnapshot> future = db.collection("attendance").get();
            QuerySnapshot snapshots = future.get();

            ObservableList<AttendanceRow> rows = FXCollections.observableArrayList();

            for (DocumentSnapshot doc : snapshots.getDocuments()) {
                // Extract 'date' field
                Object dateObj = doc.get("date");
                if (dateObj == null) continue;

                LocalDate docDate = null;
                if (dateObj instanceof com.google.cloud.Timestamp ts) {
                    docDate = ts.toDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                } else if (dateObj instanceof String s) {
                    try {
                        // Convert from format like "Thu Oct 30 2025 00:00:00 GMT+0800"
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", java.util.Locale.ENGLISH);
                        java.util.Date parsed = sdf.parse(s);
                        docDate = parsed.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                    } catch (Exception ignored) { }
                }

                if (docDate == null || !docDate.equals(date)) continue;

                // Extract other fields
                String childId = String.valueOf(doc.get("childId"));
                String name = String.valueOf(doc.get("name"));
                String reason = String.valueOf(doc.get("reason"));
                boolean present = Boolean.TRUE.equals(doc.getBoolean("isPresent"));
                String status = present ? "attend" : "absence";
                // ✅ Use only new Firestore format: check_in_time / check_out_time
                java.util.Date checkInDate = doc.getDate("check_in_time");
                java.util.Date checkOutDate = doc.getDate("check_out_time");

                String checkIn = (checkInDate != null) ? formatTime(checkInDate) : "-";
                String checkOut = (checkOutDate != null) ? formatTime(checkOutDate) : "-";

                rows.add(new AttendanceRow(
                    childId,  // String now
                    name,
                    status,
                    reason != null ? reason : "",
                    checkIn != null ? checkIn : "",
                    checkOut != null ? checkOut : "",
                    docDate
                ));
            }

            table.setItems(rows);
            System.out.println("✅ Loaded " + rows.size() + " records for " + date);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ Failed to load attendance: " + e.getMessage());
        }
    }
}