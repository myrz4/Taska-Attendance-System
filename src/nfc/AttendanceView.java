package nfc;

import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.chart.PieChart;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javafx.beans.property.*;

import java.awt.Desktop;
import java.io.IOException;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.Date;

public class AttendanceView {

	private static AttendanceView currentInstance;

    private static void logError(String context, Exception error) {
        System.err.println("AttendanceView: " + context + " - " + error.getMessage());
        error.printStackTrace(System.err);
    }
    private VBox root;
    private TableView<AttendanceRecord> table;
    private ObservableList<AttendanceRecord> masterRecords = FXCollections.observableArrayList();
    private FilteredList<AttendanceRecord> filteredRecords = new FilteredList<>(masterRecords, p -> true);
    private PieChart chart; // ✅ make it global
    // ─── NEW: datePicker field ────────────────────────────────────────────────────
    private DatePicker datePicker;
    // ────────────────────────────────────────────────────────────────────────────────
    private static final DateTimeFormatter DB_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a");

    private List<FsDocument> cachedChildren = new ArrayList<>();
    private LocalDate cachedAttendanceDate;
    private List<FsDocument> cachedAttendanceForDate = new ArrayList<>();
    private boolean active = false;
    private long lastChartUpdate = 0;

    public AttendanceView() {
        currentInstance = this;
        active = true;

        HBox dashboardHeader = new HBox(18);
        dashboardHeader.setAlignment(Pos.CENTER_LEFT);
        dashboardHeader.setPrefHeight(70);
        dashboardHeader.setMaxWidth(Double.MAX_VALUE); // Full width
        dashboardHeader.setStyle(
            "-fx-background-color: #2e8b57, #FECF4D;"
            + "-fx-background-insets: 0, 0 0 3 0;"
            + "-fx-background-radius: 0, 0;"
        );
        ImageView honeyPot = new ImageView(ImageLoader.loadSafe("hive2.png"));
        honeyPot.setFitWidth(54);
        honeyPot.setFitHeight(54);

        // Clock (put it to the far right)
        Label clockLabel = new Label();
        clockLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 0 16px;");
        Timeline clock = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> {
                String now = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                    + " | " + LocalTime.now().withNano(0);
                clockLabel.setText(now);
            })
        );
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();

        Label dashboardTitle = new Label("Attendance");
        dashboardTitle.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 44));
        dashboardTitle.setTextFill(Color.web("#181818"));

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        dashboardHeader.getChildren().addAll(honeyPot, dashboardTitle, headerSpacer, clockLabel);
        
        root = new VBox(10);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #86d67f 0%, #76cc6e 100%);");

        // ─── 3) Create date‐picker row ───────────────────────────────────
        datePicker = new DatePicker(LocalDate.now());
        Button loadBtn = new Button("Load Date");
        loadBtn.setOnAction(e -> {
            LocalDate selectedDate = datePicker.getValue();
            loadStudents(selectedDate);
        });
        
        loadBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        loadBtn.setPrefHeight(50);
        
        // spacer to push date controls to the right
        Region dateSpacer = new Region();
        HBox.setHgrow(dateSpacer, Priority.ALWAYS);

        Label selectDateLabel = new Label("Select Date:");
        selectDateLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");        
        HBox dateBar = new HBox(10,
            selectDateLabel,
            datePicker,
            loadBtn,
            dateSpacer    // pushes everything left of it to the right edge
        );
        dateBar.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().add(dateBar);

        // Header bar
        HBox header = new HBox(10);
        header.setPadding(new Insets(10));
        header.setAlignment(Pos.CENTER_RIGHT);

        CheckBox attendanceCheckbox = new CheckBox("Mark All Present");
        attendanceCheckbox.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222;");
        attendanceCheckbox.setSelected(false); // force unticked on load

        attendanceCheckbox.setOnAction(e -> {
            for (AttendanceRecord record : filteredRecords) {
                boolean nowPresent = attendanceCheckbox.isSelected();
                if (nowPresent) {
                    String nowTime = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    record.setCheckInFullTimestamp(nowTime);  // Use setCheckInFullTimestamp here!
                } else {
                    record.setCheckInFullTimestamp("");  // Clear full timestamp when unchecked
                }
            }
            table.refresh();
            loadStudents(datePicker.getValue());
        });

       // Button viewReportsButton = new Button("View Reports");
       // viewReportsButton.setOnAction(e -> this.showAllReportsWindow());

        ComboBox<String> reasonDropdown = new ComboBox<>();
        reasonDropdown.getItems().addAll("All", "Permission", "Sick", "Unexcused", "Other...");
        reasonDropdown.setValue("All");
        reasonDropdown.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        reasonDropdown.setPrefHeight(50);
        reasonDropdown.setOnAction(e -> {
            String selected = reasonDropdown.getValue();
            filteredRecords.setPredicate(record -> {
                if ("All".equals(selected)) return true;
                if ("Other...".equals(selected)) return "Other...".equals(record.getReason());
                return selected.equals(record.getReason());
            });
        });

        // Add these two listeners to auto-show dropdown
        reasonDropdown.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                reasonDropdown.show();
            }
        });

        reasonDropdown.setOnMouseEntered(e -> reasonDropdown.show());

        Button clearFilter = new Button("Clear");
        clearFilter.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        clearFilter.setPrefHeight(50);
        clearFilter.setOnAction(e -> {
            reasonDropdown.setValue("All");
            filteredRecords.setPredicate(p -> true);
        });

        ComboBox<String> bulkReasonDropdown = new ComboBox<>();
            bulkReasonDropdown.getItems().addAll("Permission", "Sick", "Unexcused", "Other...");
            bulkReasonDropdown.setValue("Permission");
            bulkReasonDropdown.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");

        Button applyReasonBtn = new Button("Apply Reason");
        applyReasonBtn.setOnAction(e -> {
            String selectedReason = bulkReasonDropdown.getValue();
            for (AttendanceRecord record : filteredRecords) {
                if (!record.isPresent()) {
                    record.setReason(selectedReason);
                }
            }
            table.refresh();
        });

        header.getChildren().addAll(bulkReasonDropdown);
        header.getChildren().addAll(attendanceCheckbox, reasonDropdown, clearFilter);

        // ─── Table setup ────────────────────────────────────────────────────────────
        table = new TableView<>();
        table.setEditable(true);

        TableColumn<AttendanceRecord, String> nameCol = new TableColumn<>("Name");
        nameCol.setPrefWidth(80);
        nameCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());

        TableColumn<AttendanceRecord, Boolean> presentCol = new TableColumn<>("Manual In");
        presentCol.setPrefWidth(90);
        presentCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        presentCol.setCellValueFactory(cellData -> cellData.getValue().presentProperty());
        presentCol.setCellFactory(col -> new TableCell<AttendanceRecord, Boolean>() {
            private final CheckBox checkBox = new CheckBox();

            {
                setAlignment(Pos.CENTER);
                checkBox.setOnAction(e -> {
                    AttendanceRecord record = getTableView().getItems().get(getIndex());
                    boolean newVal = checkBox.isSelected();
                    record.setPresent(newVal);

                    if (newVal) {
                        record.setCheckInFullTimestamp(
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        );
                    } else {
                        record.setCheckInFullTimestamp("");
                    }

                    // ✅ only local toggle — not saving yet
                    getTableView().refresh();
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    AttendanceRecord record = getTableView().getItems().get(getIndex());
                    checkBox.setSelected(record.isPresent());
                    setGraphic(checkBox);
                }
            }
        });
        presentCol.setEditable(true);

        TableColumn<AttendanceRecord, String> reasonCol = new TableColumn<>("Reason");
        reasonCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        reasonCol.setMinWidth(100);
        reasonCol.setCellFactory(ComboBoxTableCell.forTableColumn("", "Permission", "Sick", "Unexcused", "Other..."));
      //reasonCol.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");

        reasonCol.setCellValueFactory(data -> data.getValue().reasonProperty());


        TableColumn<AttendanceRecord, String> inCol  = new TableColumn<>("Check-In");
        inCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        inCol.setPrefWidth(90);
        inCol.setCellValueFactory(data -> data.getValue().checkInTimeProperty());
        inCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText("");
                } else {
                    try {
                        LocalDateTime dt = LocalDateTime.parse(item, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        setText(dt.format(DateTimeFormatter.ofPattern("hh:mm a")));
                    } catch (Exception e) {
                        setText(item);
                    }
                }
            }
        });
        
        TableColumn<AttendanceRecord, Boolean> manualCheckOutCol = new TableColumn<>("Manual Out");
        manualCheckOutCol.setMinWidth(90);
        manualCheckOutCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        manualCheckOutCol.setCellValueFactory(cellData -> cellData.getValue().manualCheckOutProperty());
        manualCheckOutCol.setCellFactory(col -> new TableCell<AttendanceRecord, Boolean>() {
            private final CheckBox checkBox = new CheckBox();

            {
                setAlignment(Pos.CENTER);
                checkBox.setOnAction(e -> {
                    AttendanceRecord record = getTableView().getItems().get(getIndex());
                    boolean newVal = checkBox.isSelected();
                    record.setManualCheckOut(newVal);

                    if (newVal) {
                        record.setCheckOutFullTimestamp(
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        );
                    } else {
                        record.setCheckOutFullTimestamp("");
                    }

                    // ✅ only local toggle — not saving yet
                    getTableView().refresh();
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    AttendanceRecord record = getTableView().getItems().get(getIndex());
                    checkBox.setSelected(record.isManualCheckOut());
                    setGraphic(checkBox);
                }
            }
        });
        manualCheckOutCol.setEditable(true);

        TableColumn<AttendanceRecord, String> outCol = new TableColumn<>("Check-Out");
        outCol.setPrefWidth(95);
        outCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        outCol.setCellValueFactory(c -> c.getValue().checkOutTimeProperty());
        outCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText("");
                } else {
                    try {
                        LocalDateTime dateTime = LocalDateTime.parse(item, DB_TIMESTAMP_FORMAT);
                        setText(dateTime.format(DISPLAY_TIME_FORMAT));
                    } catch (Exception e) {
                        setText(item);  // fallback to raw string if parse fails
                    }
                }
            }
        });

        TableColumn<AttendanceRecord, Void> uploadCol = new TableColumn<>("Upload Letter");
        uploadCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        uploadCol.setCellFactory(new Callback<>() {
            @Override
            public TableCell<AttendanceRecord, Void> call(final TableColumn<AttendanceRecord, Void> param) {
                return new TableCell<>() {
                    private final Button btn = new Button("Upload");
                    {
                        btn.setOnAction(event -> {
                            AttendanceRecord record = getTableView().getItems().get(getIndex());
                            FileChooser fileChooser = new FileChooser();
                            File selectedFile = fileChooser.showOpenDialog(null);
                            if (selectedFile != null) {
                                record.setReasonLetterFile(selectedFile);
                            }
                        });
                    }
                    @Override
                    public void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        setGraphic(empty ? null : btn);
                    }
                };
            }
        });
        
        TableColumn<AttendanceRecord, Void> viewCol = new TableColumn<>("View Letter");
        viewCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        viewCol.setCellFactory(new Callback<>() {
            @Override
            public TableCell<AttendanceRecord, Void> call(final TableColumn<AttendanceRecord, Void> param) {
                return new TableCell<>() {
                    private final Button viewBtn = new Button("View");

                    {
                        viewBtn.setOnAction(event -> {
                            AttendanceRecord record = getTableView().getItems().get(getIndex());
                            File reasonLetterFile = record.getReasonLetterFile();

                            if (reasonLetterFile != null && reasonLetterFile.exists()) {
                                try {
                                    Desktop.getDesktop().open(reasonLetterFile);
                                } catch (IOException ex) {
                                    new Alert(Alert.AlertType.ERROR, "Unable to open file: " + ex.getMessage()).showAndWait();
                                }
                            } else {
                                new Alert(Alert.AlertType.WARNING, "No reason letter available to view.").showAndWait();
                            }
                        });
                    }

                    @Override
                    public void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        setGraphic(empty ? null : viewBtn);
                    }
                };
            }
        });

        table.getColumns().addAll(
        	    nameCol, presentCol,inCol, outCol, manualCheckOutCol,reasonCol, uploadCol, viewCol
        );
        table.setItems(filteredRecords);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.setRowFactory(tv -> new TableRow<AttendanceRecord>() {
            @Override
            protected void updateItem(AttendanceRecord item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.isPresent()) {
                    setStyle("-fx-background-color: #e8f5e9; -fx-text-fill: #2e7d32;"); // Soft green row, green text
                } else {
                    setStyle("-fx-background-color: #ffebee; -fx-text-fill: #c62828;"); // Soft red row, red text
                }
            }
        });

        // ────────────────────────────────────────────────────────────────────────────

        Label chartTitle = new Label("Today's Attendance");
        chartTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Button saveBtn = new Button("Save Attendance");
        saveBtn.setOnAction(e -> saveAttendance());
        saveBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        saveBtn.setPrefHeight(50);

        chart = new PieChart();

        Button refreshChart = new Button("Refresh Chart");
        refreshChart.setOnAction(e -> updateChart(chart, datePicker.getValue()));
        refreshChart.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        refreshChart.setPrefHeight(50);

        root.getChildren().addAll(header, table, saveBtn, chartTitle, chart, refreshChart);
        
        BorderPane mainLayout = new BorderPane();
        mainLayout.setTop(dashboardHeader);
        mainLayout.setCenter(root);
        
        this.root = new VBox();
        this.root.getChildren().add(mainLayout);

        Platform.runLater(() -> {
            preloadData();                    // loads Firestore ONCE
            loadStudents(datePicker.getValue()); // auto-load table + pie chart
        });

        datePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate != null) {
                loadStudents(newDate);
            }
        });
    }

    private void preloadData() {
        if (!UserSession.isLoggedIn()) {
            cachedChildren = List.of();
            return;
        }

        try {
            if (cachedChildren != null && !cachedChildren.isEmpty()) {
                return;
            }

            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            cachedChildren = client.listDocuments("children");
            System.out.println("⚡ Attendance preload complete (REST)");
        } catch (Exception e) {
            logError("attendance preload failed", e);
        }
    }

    private List<FsDocument> getAttendanceDocsForDate(LocalDate date) throws Exception {
        if (date == null) return List.of();
        if (!UserSession.isLoggedIn()) return List.of();

        if (cachedAttendanceDate != null && cachedAttendanceDate.equals(date)
                && cachedAttendanceForDate != null) {
            return cachedAttendanceForDate;
        }

        FirestoreRestClient client = FirestoreRest.forCurrentUser();
        Date startOfDay = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<FsDocument> docs = client.queryWhereEqual("attendance", "date", startOfDay);

        cachedAttendanceDate = date;
        cachedAttendanceForDate = docs != null ? docs : List.of();
        return cachedAttendanceForDate;
    }
    
    // ─── NEW: Revised loadStudents() with MIN/MAX ───────────────────────────────
    // Change method to accept date parameter
    private void loadStudents(LocalDate date) {
        masterRecords.clear();
        if (!UserSession.isLoggedIn()) return;

        try {
            // ✅ USE CACHED DATA (FAST)
            if (cachedChildren == null || cachedChildren.isEmpty()) {
                preloadData();
            }
            List<FsDocument> children = cachedChildren;
            List<FsDocument> attendanceDocs = getAttendanceDocsForDate(date);

            // Map numeric child_id -> nfc_uid (for legacy attendance docs)
            Map<Long, String> childIdToNfcUid = new HashMap<>();
            Map<Long, String> childIdToChildDocId = new HashMap<>();
            Map<String, String> nfcUidToChildDocId = new HashMap<>();
            Set<String> allChildDocIds = new HashSet<>();
            for (FsDocument child : children) {
                allChildDocIds.add(child.getId());
                Long numericId = child.getLong("child_id");
                String uid = child.getString("nfc_uid");
                if (numericId != null && uid != null && !uid.isBlank()) {
                    childIdToNfcUid.put(numericId, uid);
                }
                if (numericId != null) {
                    childIdToChildDocId.put(numericId, child.getId());
                }
                if (uid != null && !uid.isBlank()) {
                    nfcUidToChildDocId.put(uid, child.getId());
                }
            }

            Map<String, FsDocument> attendanceMap = new HashMap<>();

            // ✅ Attendance docs are already queried for the selected date
            for (FsDocument doc : attendanceDocs) {

                // Canonical schema going forward: attendance.childId = children/{childId} docId
                // Back-compat: older docs may still store NFC UID in attendance.childId.
                String keyChildDocId = doc.getString("childId");

                // Legacy schema: numeric "child_id"; translate to child docId
                if (keyChildDocId == null || keyChildDocId.isBlank()) {
                    Long numericChildId = doc.getLong("child_id");
                    if (numericChildId != null) {
                        keyChildDocId = childIdToChildDocId.get(numericChildId);
                        if (keyChildDocId == null || keyChildDocId.isBlank()) {
                            String legacyNfc = childIdToNfcUid.get(numericChildId);
                            if (legacyNfc != null && !legacyNfc.isBlank()) {
                                keyChildDocId = nfcUidToChildDocId.get(legacyNfc);
                            }
                        }
                    }
                }

                // If attendance.childId contains an NFC UID, map it to child docId.
                if (keyChildDocId != null && !keyChildDocId.isBlank() && !allChildDocIds.contains(keyChildDocId)) {
                    String mapped = nfcUidToChildDocId.get(keyChildDocId);
                    if (mapped != null && !mapped.isBlank()) {
                        keyChildDocId = mapped;
                    }
                }

                if (keyChildDocId != null && !keyChildDocId.isBlank()) {
                    attendanceMap.put(keyChildDocId, doc);
                }
            }

            System.out.println("✅ Attendance docs loaded for " + date);
            System.out.println("✅ Matching records found: " + attendanceMap.size());

            // ✅ 4. Match attendance to children list
            for (FsDocument c : children) {
                String childDocId = c.getId();
                String nfcUid = c.getString("nfc_uid");
                String name = c.getString("name");
                AttendanceRecord record = new AttendanceRecord(childDocId, name, nfcUid);

                FsDocument att = attendanceMap.get(childDocId);
                if (att != null) {
                    // Keep displayed name in sync with latest child doc
                    record.nameProperty().set(name);

                    Boolean presentFlag = att.getBoolean("isPresent");
                    if (presentFlag == null) presentFlag = att.getBoolean("is_present");
                    if (presentFlag == null) presentFlag = att.getBoolean("isPresent");
                    record.setPresent(Boolean.TRUE.equals(presentFlag));

                    java.util.Date in = att.getDate("check_in_time");
                    java.util.Date out = att.getDate("check_out_time");

                    // ✅ If check-in exists → tick “Manual In”
                    if (in != null) {
                        record.setCheckInFullTimestamp(
                            LocalDateTime.ofInstant(in.toInstant(), ZoneId.systemDefault())
                                .format(DB_TIMESTAMP_FORMAT)
                        );
                        record.setPresent(true);
                    }

                    // ✅ If check-out exists → tick “Manual Out”
                    if (out != null) {
                        record.setCheckOutFullTimestamp(
                            LocalDateTime.ofInstant(out.toInstant(), ZoneId.systemDefault())
                                .format(DB_TIMESTAMP_FORMAT)
                        );
                        record.setManualCheckOut(true);
                    }

                    record.setReason(
                        att.getString("reason") != null ? att.getString("reason") : "Default"
                    );
                }
                masterRecords.add(record);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        table.refresh();
        updateChart(chart, date);
    }

    public void onShow() {
        active = true;
    }

    public void onHide() {
        active = false;
    }

    private void saveAttendance() {
        LocalDate selectedDate = datePicker.getValue();

        // ✅ Store real Timestamp-compatible date (not stringified GMT)
        Date firestoreDate = Date.from(selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        String dateString = selectedDate.toString(); // e.g. "2025-10-28"

        System.out.println("🟡 Saving attendance for date: " + dateString);

        CompletableFuture.runAsync(() -> {
            int savedCount = 0;

            FirestoreRestClient client;
            try {
                client = FirestoreRest.forCurrentUser();
            } catch (Exception e) {
                Platform.runLater(() ->
                        new Alert(Alert.AlertType.ERROR,
                                "❌ Save failed: " + e.getMessage()).showAndWait());
                return;
            }

            for (AttendanceRecord record : filteredRecords) {
                try {
                    // ✅ Use selectedDate in document ID
                    String docId = dateString + "_" + record.getChildDocId().trim();

                    Map<String, Object> data = new HashMap<>();

                    data.put("childId", record.getChildDocId().trim());
                    data.put("childRef", new FirestoreRestClient.ReferenceValue(
                            client.referenceValue("children", record.getChildDocId().trim())));
                    if (record.getNfcUid() != null && !record.getNfcUid().trim().isEmpty()) {
                        data.put("nfc_uid", record.getNfcUid().trim());
                    }
                    data.put("name", record.getName());
                    data.put("date", firestoreDate);


                    // Times always exist (null allowed)
                    data.put("check_in_time",
                        record.getCheckInFullTimestamp() != null && !record.getCheckInFullTimestamp().isEmpty()
                            ? Date.from(LocalDateTime.parse(record.getCheckInFullTimestamp(), DB_TIMESTAMP_FORMAT)
                                    .atZone(ZoneId.systemDefault()).toInstant())
                            : null);

                    data.put("check_out_time",
                        record.getCheckOutFullTimestamp() != null && !record.getCheckOutFullTimestamp().isEmpty()
                            ? Date.from(LocalDateTime.parse(record.getCheckOutFullTimestamp(), DB_TIMESTAMP_FORMAT)
                                    .atZone(ZoneId.systemDefault()).toInstant())
                            : null);

                    boolean hasCheckIn =
                            record.getCheckInFullTimestamp() != null
                            && !record.getCheckInFullTimestamp().isEmpty();

                    boolean present =
                            record.isPresent() || hasCheckIn;

                    String checkinMethod;
                    if (record.isPresent()) {
                        checkinMethod = "MANUAL";
                    } else if (hasCheckIn) {
                        checkinMethod = "NFC";
                    } else {
                        checkinMethod = "NONE";
                    }

                    data.put("isPresent", present);
                    data.put("manual_in", record.isPresent());
                    data.put("manual_out", record.isManualCheckOut());
                    data.put("checkin_method", checkinMethod);

                    // Reason always exist
                    data.put("reason", (record.getReason() == null || record.getReason().isBlank())
                            ? "Default"
                            : record.getReason());

                        client.patchDocumentMerge("attendance", docId, data);
                    savedCount++;
                    System.out.println("✅ Queued save for: " + record.getName() + " (" + record.getChildDocId() + ")");

                } catch (Exception ex) {
                    System.err.println("❌ Failed to queue record for: "
                            + record.getName() + " → " + ex.getMessage());
                }
            }

            try {
                int finalCount = savedCount;
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.INFORMATION,
                            "✅ Attendance saved for " + finalCount + " students on " + dateString)
                            .showAndWait();

                    // Invalidate attendance cache and reload table + chart
                    cachedAttendanceDate = null;
                    cachedAttendanceForDate = new ArrayList<>();
                    loadStudents(datePicker.getValue());
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() ->
                        new Alert(Alert.AlertType.ERROR,
                                "❌ Save failed: " + e.getMessage()).showAndWait());
            }
        });
    }

    public VBox getRoot() {
        return root;
    }

    public static void markPresentByChildId(int childId) {
        Platform.runLater(() -> {
            if (currentInstance != null) {

                boolean found = false;
                for (AttendanceRecord record : currentInstance.masterRecords) {
                    if (record.getChildId() == childId) {
                        record.setPresent(true);
                        record.setCheckInFullTimestamp(LocalDateTime.now().format(DB_TIMESTAMP_FORMAT));
                        found = true;
                        break;
                    }
                }

                // Reload attendance reasons and chart from database
                currentInstance.loadStudents(currentInstance.datePicker.getValue());   // ✅ refresh all data (so absent/present list updates)
                currentInstance.updateChart(currentInstance.chart, currentInstance.datePicker.getValue());
                currentInstance.table.refresh();
            }
        });
    }

    private void updateChart(PieChart chart, LocalDate date) {
        long now = System.currentTimeMillis();
        if (now - lastChartUpdate < 400) return;
        lastChartUpdate = now;
        try {
            List<AttendanceRecord> records = masterRecords;
            if (records == null || records.isEmpty()) {
                chart.setData(FXCollections.observableArrayList());
                System.out.println("📊 No local attendance data for chart update.");
                return;
            }

            int totalChildren = records.size();
            int presentCount = 0;
            for (AttendanceRecord rec : records) {
                boolean isPresent = rec.isPresent() ||
                        (rec.getCheckInTime() != null && !rec.getCheckInTime().isEmpty());
                if (isPresent) presentCount++;
            }

            int absentCount = Math.max(0, totalChildren - presentCount);
            int total = totalChildren;

            final int finalPresent = presentCount;
            final int finalAbsent = absentCount;
            final int finalTotal = total;

            PieChart.Data presentData = new PieChart.Data(
                    "Present (" + finalPresent + " | " + (finalTotal > 0 ? (finalPresent * 100 / finalTotal) : 0) + "%)",
                    finalPresent);
            PieChart.Data absentData = new PieChart.Data(
                    "Absent (" + finalAbsent + " | " + (finalTotal > 0 ? (finalAbsent * 100 / finalTotal) : 0) + "%)",
                    finalAbsent);

            Platform.runLater(() -> {
                chart.setData(FXCollections.observableArrayList(presentData, absentData));

                if (presentData.getNode() != null)
                    Tooltip.install(presentData.getNode(), new Tooltip(finalPresent + " students present"));
                if (absentData.getNode() != null)
                    Tooltip.install(absentData.getNode(), new Tooltip(finalAbsent + " students absent"));
            });

            System.out.println("📊 Pie Chart (local state) >> Present=" + finalPresent + " | Absent=" + finalAbsent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void refreshUI() {
        if (currentInstance == null || !currentInstance.active) {
            System.out.println("⚠ AttendanceView not active yet");
            return;
        }

        // Refresh cached Firestore data off the UI thread, then update UI.
        CompletableFuture
            .runAsync(() -> {
                try {
                    currentInstance.preloadData();
                } catch (Exception ignored) {
                }
            })
            .thenRun(() -> Platform.runLater(() -> {
                currentInstance.loadStudents(currentInstance.datePicker.getValue());
                if (currentInstance.chart != null) {
                    currentInstance.updateChart(currentInstance.chart, currentInstance.datePicker.getValue());
                }
                currentInstance.table.refresh();
            }));
    }

    public static List<AttendanceRecord> getCurrentAttendanceState() {
        if (currentInstance != null) {
            return currentInstance.masterRecords;
        }
        return FXCollections.observableArrayList(); // fallback if view not open
    }

    // ✅ Static helper so NFCReader can trigger pie chart refresh
    public static void updateChartFromStatic() {
        Platform.runLater(() -> {
            if (currentInstance != null && currentInstance.active && currentInstance.chart != null) {
                currentInstance.updateChart(currentInstance.chart, currentInstance.datePicker.getValue());
                currentInstance.table.refresh();
            } else {
                System.out.println("⚠ Pie chart not available — Attendance tab might be closed.");
            }
        });
    }

    private static String formatTime(Date d) {
        return d.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime()
                .withNano(0)
                .toString();
    }
}