package nfc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

final class AttendanceViewLayoutSupport {
    private AttendanceViewLayoutSupport() {
    }

    static HBox createDashboardHeader() {
        HBox dashboardHeader = new HBox(18);
        dashboardHeader.setAlignment(Pos.CENTER_LEFT);
        dashboardHeader.setPrefHeight(70);
        dashboardHeader.setMaxWidth(Double.MAX_VALUE);
        dashboardHeader.setStyle(
            "-fx-background-color: #2e8b57, #FECF4D;"
                + "-fx-background-insets: 0, 0 0 3 0;"
                + "-fx-background-radius: 0, 0;"
        );

        ImageView honeyPot = new ImageView(ImageLoader.loadSafe("hive2.png"));
        honeyPot.setFitWidth(54);
        honeyPot.setFitHeight(54);

        Label clockLabel = new Label();
        clockLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 0 16px;");
        Timeline clock = new Timeline(
            new KeyFrame(Duration.seconds(1), event -> {
                String now = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                    + " | " + LocalTime.now().withNano(0);
                clockLabel.setText(now);
            })
        );
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();

        Label dashboardTitle = new Label("Attendance");
        dashboardTitle.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 44));

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        dashboardHeader.getChildren().addAll(honeyPot, dashboardTitle, headerSpacer, clockLabel);
        return dashboardHeader;
    }

    static DateControls createDateControls(LocalDate initialDate) {
        DatePicker datePicker = new DatePicker(initialDate == null ? LocalDate.now() : initialDate);
        Button loadButton = createActionButton("Load Date");
        loadButton.setPrefHeight(50);

        Region dateSpacer = new Region();
        HBox.setHgrow(dateSpacer, Priority.ALWAYS);

        Label selectDateLabel = new Label("Select Date:");
        selectDateLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        HBox dateBar = new HBox(10, selectDateLabel, datePicker, loadButton, dateSpacer);
        dateBar.setAlignment(Pos.CENTER_LEFT);
        return new DateControls(dateBar, datePicker, loadButton);
    }

    static FilterToolbar createFilterToolbar() {
        ComboBox<String> reasonDropdown = new ComboBox<>();
        reasonDropdown.getItems().addAll("All", "Permission", "Sick", "Unexcused", "Other...");
        reasonDropdown.setValue("All");
        styleDropdown(reasonDropdown);

        ComboBox<String> auditDropdown = new ComboBox<>();
        auditDropdown.getItems().addAll("All Records", "Corrected Only");
        auditDropdown.setValue("All Records");
        styleDropdown(auditDropdown);

        Button clearFilter = createActionButton("Clear");
        Button manualCheckInBtn = createActionButton("Manual Check-In");
        Button manualCheckOutBtn = createActionButton("Manual Check-Out");
        Button markAbsentBtn = createActionButton("Mark Absent");
        Button editRecordBtn = createActionButton("Edit Record");
        Button reopenBtn = createActionButton("Reopen Record");
        Button viewAuditBtn = createActionButton("View Audit");

        HBox header = new HBox(
            10,
            manualCheckInBtn,
            manualCheckOutBtn,
            markAbsentBtn,
            editRecordBtn,
            reopenBtn,
            viewAuditBtn,
            reasonDropdown,
            auditDropdown,
            clearFilter
        );
        header.setPadding(new Insets(10));
        header.setAlignment(Pos.CENTER_LEFT);

        return new FilterToolbar(
            header,
            reasonDropdown,
            auditDropdown,
            clearFilter,
            manualCheckInBtn,
            manualCheckOutBtn,
            markAbsentBtn,
            editRecordBtn,
            reopenBtn,
            viewAuditBtn
        );
    }

    static Button createActionButton(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        button.setPrefHeight(44);
        return button;
    }

    private static void styleDropdown(ComboBox<String> dropdown) {
        dropdown.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        dropdown.setPrefHeight(50);
        dropdown.focusedProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue) {
                dropdown.show();
            }
        });
        dropdown.setOnMouseEntered(event -> dropdown.show());
    }

    static final class DateControls {
        final HBox bar;
        final DatePicker datePicker;
        final Button loadButton;

        DateControls(HBox bar, DatePicker datePicker, Button loadButton) {
            this.bar = bar;
            this.datePicker = datePicker;
            this.loadButton = loadButton;
        }
    }

    static final class FilterToolbar {
        final HBox header;
        final ComboBox<String> reasonDropdown;
        final ComboBox<String> auditDropdown;
        final Button clearFilter;
        final Button manualCheckInBtn;
        final Button manualCheckOutBtn;
        final Button markAbsentBtn;
        final Button editRecordBtn;
        final Button reopenBtn;
        final Button viewAuditBtn;

        FilterToolbar(
            HBox header,
            ComboBox<String> reasonDropdown,
            ComboBox<String> auditDropdown,
            Button clearFilter,
            Button manualCheckInBtn,
            Button manualCheckOutBtn,
            Button markAbsentBtn,
            Button editRecordBtn,
            Button reopenBtn,
            Button viewAuditBtn
        ) {
            this.header = header;
            this.reasonDropdown = reasonDropdown;
            this.auditDropdown = auditDropdown;
            this.clearFilter = clearFilter;
            this.manualCheckInBtn = manualCheckInBtn;
            this.manualCheckOutBtn = manualCheckOutBtn;
            this.markAbsentBtn = markAbsentBtn;
            this.editRecordBtn = editRecordBtn;
            this.reopenBtn = reopenBtn;
            this.viewAuditBtn = viewAuditBtn;
        }
    }
}