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
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;

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
        java.net.URL logoResource = getClass().getResource("/nfc/logo.png");
        ImageView logo = new ImageView();
        if (logoResource != null) {
            logo.setImage(new Image(logoResource.toExternalForm()));
        }
        logo.setFitHeight(50);
        logo.setFitWidth(50);
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
            Document doc = new Document(PageSize.A4, 34, 34, 38, 34);
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(file);
                PdfWriter.getInstance(doc, out);
                doc.open();

                Color brandGold = new Color(255, 203, 60);
                Color brandGoldSoft = new Color(255, 245, 217);
                Color ink = new Color(29, 42, 58);
                Color muted = new Color(120, 132, 150);
                Color border = new Color(228, 234, 242);
                Color successBg = new Color(234, 248, 239);
                Color successText = new Color(13, 122, 56);
                Color dangerBg = new Color(255, 240, 238);
                Color dangerText = new Color(197, 59, 42);
                Color tableAlt = new Color(248, 250, 252);

                int attendCount = 0;
                int absentCount = 0;
                for (AttendanceRow row : rows) {
                    if ("attend".equalsIgnoreCase(row.getStatus())) {
                        attendCount++;
                    } else {
                        absentCount++;
                    }
                }

                PdfPTable header = new PdfPTable(2);
                header.setWidthPercentage(100);
                header.setWidths(new float[]{1.15f, 5.85f});
                header.setSpacingAfter(10f);

                PdfPCell logoCell = new PdfPCell();
                logoCell.setBorder(Rectangle.NO_BORDER);
                logoCell.setBackgroundColor(brandGoldSoft);
                logoCell.setPadding(16f);
                try {
                    java.net.URL logoResource = getClass().getResource("/nfc/logo.png");
                    if (logoResource != null) {
                        com.lowagie.text.Image logo = com.lowagie.text.Image.getInstance(logoResource);
                        logo.scaleToFit(52, 52);
                        logoCell.addElement(logo);
                    }
                } catch (BadElementException | IOException ignored) {
                    // Optional logo.
                }

                PdfPCell titleCell = new PdfPCell();
                titleCell.setBorder(Rectangle.NO_BORDER);
                titleCell.setBackgroundColor(brandGoldSoft);
                titleCell.setPadding(16f);
                Paragraph eyebrow = new Paragraph("DAILY ATTENDANCE EXPORT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, muted));
                eyebrow.setSpacingAfter(6f);
                titleCell.addElement(eyebrow);
                Paragraph title = new Paragraph("Taska Zurah Daily Attendance Report", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, ink));
                title.setSpacingAfter(4f);
                titleCell.addElement(title);
                String dateStr = date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH).toUpperCase(java.util.Locale.ROOT)
                    + "  |  " + date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
                titleCell.addElement(new Paragraph(dateStr, FontFactory.getFont(FontFactory.HELVETICA, 11, muted)));
                header.addCell(logoCell);
                header.addCell(titleCell);
                doc.add(header);

                PdfPTable metrics = new PdfPTable(4);
                metrics.setWidthPercentage(100);
                metrics.setWidths(new float[]{1.2f, 1f, 1f, 1.2f});
                metrics.setSpacingAfter(14f);
                metrics.addCell(buildDailyMetricCell("Date", date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), brandGoldSoft, ink, muted));
                metrics.addCell(buildDailyMetricCell("Students", String.valueOf(rows.size()), brandGoldSoft, ink, muted));
                metrics.addCell(buildDailyMetricCell("Attend", String.valueOf(attendCount), successBg, successText, muted));
                metrics.addCell(buildDailyMetricCell("Absence", String.valueOf(absentCount), dangerBg, dangerText, muted));
                doc.add(metrics);

                Paragraph sectionTitle = new Paragraph("Attendance Timeline", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, ink));
                sectionTitle.setSpacingAfter(8f);
                doc.add(sectionTitle);

                PdfPTable attendanceTable = new PdfPTable(7);
                attendanceTable.setWidthPercentage(100);
                attendanceTable.setWidths(new float[]{1.35f, 2.2f, 1.2f, 2f, 1.4f, 1.4f, 1.8f});
                attendanceTable.setSpacingAfter(14f);

                String[] columns = {"Child ID", "Name", "Status", "Reason", "Check-In", "Check-Out", "Remark"};
                for (String col : columns) {
                    PdfPCell cell = new PdfPCell(new Phrase(col, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink)));
                    cell.setBackgroundColor(brandGold);
                    cell.setBorderColor(border);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cell.setPadding(8f);
                    attendanceTable.addCell(cell);
                }

                if (rows.isEmpty()) {
                    PdfPCell emptyCell = new PdfPCell(new Phrase("No attendance records were found for the selected date.", FontFactory.getFont(FontFactory.HELVETICA, 10, muted)));
                    emptyCell.setColspan(7);
                    emptyCell.setPadding(12f);
                    emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    emptyCell.setBorderColor(border);
                    attendanceTable.addCell(emptyCell);
                } else {
                    for (int index = 0; index < rows.size(); index++) {
                        AttendanceRow row = rows.get(index);
                        Color rowBg = index % 2 == 0 ? Color.WHITE : tableAlt;
                        boolean attend = "attend".equalsIgnoreCase(row.getStatus());
                        Color statusBg = attend ? successBg : dangerBg;
                        Color statusColor = attend ? successText : dangerText;

                        addDailyPdfBodyCell(attendanceTable, row.childIdProperty().get(), rowBg, border, Element.ALIGN_CENTER, ink, false);
                        addDailyPdfBodyCell(attendanceTable, row.nameProperty().get(), rowBg, border, Element.ALIGN_LEFT, ink, false);
                        addDailyPdfBodyCell(attendanceTable, row.getStatus(), statusBg, border, Element.ALIGN_CENTER, statusColor, true);
                        addDailyPdfBodyCell(attendanceTable, row.getReason() == null ? "-" : row.getReason(), rowBg, border, Element.ALIGN_LEFT, ink, false);
                        addDailyPdfBodyCell(attendanceTable, formatTime(row.getCheckInTime()), rowBg, border, Element.ALIGN_CENTER, ink, false);
                        addDailyPdfBodyCell(attendanceTable, formatTime(row.getCheckOutTime()), rowBg, border, Element.ALIGN_CENTER, ink, false);
                        addDailyPdfBodyCell(attendanceTable, row.getRemark() == null || row.getRemark().isBlank() ? "-" : row.getRemark(), rowBg, border, Element.ALIGN_LEFT, ink, false);
                    }
                }
                doc.add(attendanceTable);

                LineSeparator line = new LineSeparator();
                line.setPercentage(100f);
                line.setLineWidth(0.8f);
                line.setLineColor(border);
                doc.add(Chunk.NEWLINE);
                doc.add(line);

                Paragraph footer = new Paragraph("Generated from the Taska Zurah attendance report module for daily attendance review and record keeping.", FontFactory.getFont(FontFactory.HELVETICA, 10, muted));
                footer.setSpacingBefore(8f);
                doc.add(footer);

                Paragraph sign = new Paragraph("Assigned Teacher", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink));
                sign.setAlignment(Element.ALIGN_RIGHT);
                sign.setSpacingBefore(16f);
                doc.add(sign);
            } finally {
                if (doc.isOpen()) {
                    doc.close();
                }
                if (out != null) {
                    out.close();
                }
            }
            new Alert(Alert.AlertType.INFORMATION, "PDF exported!").showAndWait();
        } catch (DocumentException | IOException e) {
            System.err.println("Failed to export daily PDF: " + e.getMessage());
            new Alert(Alert.AlertType.ERROR, "Failed to export PDF: " + e.getMessage()).showAndWait();
        }
    }

    private PdfPCell buildDailyMetricCell(String label, String value, Color bgColor, Color valueColor, Color muted) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(10f);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(bgColor);

        Paragraph labelParagraph = new Paragraph(label.toUpperCase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, muted));
        labelParagraph.setSpacingAfter(6f);
        cell.addElement(labelParagraph);

        Paragraph valueParagraph = new Paragraph(value == null ? "-" : value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, valueColor));
        cell.addElement(valueParagraph);
        return cell;
    }

    private void addDailyPdfBodyCell(PdfPTable table, String text, Color bgColor, Color borderColor, int alignment, Color textColor, boolean bold) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 10, bold ? Font.BOLD : Font.NORMAL, textColor);
        PdfPCell cell = new PdfPCell(new Phrase(text == null || text.isBlank() ? "-" : text, font));
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(borderColor);
        cell.setPadding(8f);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
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

        table.getColumns().clear();
        table.getColumns().addAll(Arrays.asList(colId, colName, colStatus, colReason, checkInCol, colCheckOut));
        table.setPrefHeight(320);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void loadAttendance(LocalDate date) {
        table.getItems().clear();

        try {
            ObservableList<AttendanceRow> rows = FXCollections.observableArrayList();

            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            java.util.Date startOfDay = java.util.Date.from(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
            java.util.List<FsDocument> docs = client.queryWhereEqual("attendance", "date", startOfDay);

            for (FsDocument doc : docs) {
                String childId = String.valueOf(doc.getString("childId"));
                String name = String.valueOf(doc.getString("name"));
                String reason = String.valueOf(doc.getString("reason"));
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
                    date
                ));
            }

            table.setItems(rows);
            System.out.println("✅ Loaded " + rows.size() + " records for " + date);

        } catch (IOException | InterruptedException | RuntimeException e) {
            System.err.println("❌ Failed to load attendance: " + e.getMessage());
        }
    }
}