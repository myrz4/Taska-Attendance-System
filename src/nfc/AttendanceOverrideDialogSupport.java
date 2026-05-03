package nfc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

@SuppressWarnings({"java:S1144", "java:S1068"})
final class AttendanceOverrideDialogSupport {
    private static final DateTimeFormatter TIME_TEXT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FLEX_TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

    private AttendanceOverrideDialogSupport() {}

    static {
        java.util.function.Predicate<AttendanceRecord> keepConfirmCompleted = AttendanceOverrideDialogSupport::confirmCompletedRecordEdit;
        java.util.function.Function<AttendanceRecord, OverrideDialogResult> keepPrompt =
            record -> promptForOverride("", record, LocalDate.now());
        java.util.function.BiFunction<String, AttendanceRecord, Boolean> keepConfirmSubmit = AttendanceOverrideDialogSupport::confirmSubmit;
        OverrideDialogResult probe = new OverrideDialogResult(null, null, null, null, null);
        java.util.Objects.requireNonNull(keepConfirmCompleted);
        java.util.Objects.requireNonNull(keepPrompt);
        java.util.Objects.requireNonNull(keepConfirmSubmit);
        java.util.Objects.hash(
            probe.attendanceDate,
            probe.reason,
            probe.notes,
            probe.checkInText,
            probe.checkOutText
        );
    }

    @SuppressWarnings("java:S1144")
    static boolean confirmCompletedRecordEdit(AttendanceRecord record) {
        Alert strongWarning = new Alert(
            Alert.AlertType.WARNING,
            "This attendance record is already completed. Editing it can affect billing and pickup history. Continue?",
            ButtonType.OK,
            ButtonType.CANCEL
        );
        strongWarning.setHeaderText("Completed Record Warning");
        return strongWarning.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    @SuppressWarnings("java:S1144")
    static OverrideDialogResult promptForOverride(String action, AttendanceRecord record, LocalDate currentDate) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(AttendanceOverrideSupport.overrideTitle(action));
        dialog.setHeaderText(record.getName() + " on " + currentDate);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        DatePicker actionDatePicker = new DatePicker(currentDate);
        String initialCheckInText = AttendanceOverrideSupport.defaultTimeText(record.getCheckInFullTimestamp());
        String initialCheckOutText = AttendanceOverrideSupport.defaultTimeText(record.getCheckOutFullTimestamp());
        if ("MANUAL_CHECK_IN".equals(action) && initialCheckInText.isBlank()) {
            initialCheckInText = AttendanceOverrideSupport.currentTimeText();
        }
        if ("MANUAL_CHECK_OUT".equals(action) && initialCheckOutText.isBlank()) {
            initialCheckOutText = AttendanceOverrideSupport.currentTimeText();
        }
        TimeFieldControl checkInControl = new TimeFieldControl(initialCheckInText, "EDIT_RECORD".equals(action));
        TimeFieldControl checkOutControl = new TimeFieldControl(initialCheckOutText, "EDIT_RECORD".equals(action));

        TextField reasonField = new TextField();
        TextArea notesArea = new TextArea();
        notesArea.setPrefRowCount(3);
        reasonField.setPromptText("Required reason");
        notesArea.setPromptText("Optional notes");

        int row = 0;
        grid.add(new Label("Attendance Date"), 0, row);
        grid.add(actionDatePicker, 1, row++);

        if ("MANUAL_CHECK_IN".equals(action) || "EDIT_RECORD".equals(action)) {
            grid.add(new Label("Check-In Time"), 0, row);
            grid.add(checkInControl.node(), 1, row++);
        }
        if ("MANUAL_CHECK_OUT".equals(action) || "EDIT_RECORD".equals(action)) {
            grid.add(new Label("Check-Out Time"), 0, row);
            grid.add(checkOutControl.node(), 1, row++);
        }

        grid.add(new Label("Reason"), 0, row);
        grid.add(reasonField, 1, row++);
        grid.add(new Label("Notes"), 0, row);
        grid.add(notesArea, 1, row);

        dialog.getDialogPane().setContent(grid);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return null;
        }

        String reason = reasonField.getText() == null ? "" : reasonField.getText().trim();
        if (reason.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Reason is required for manual attendance actions.").showAndWait();
            return null;
        }

        String notes = notesArea.getText() == null ? "" : notesArea.getText();
        String checkInText = checkInControl.timeText();
        String checkOutText = checkOutControl.timeText();

        if ("EDIT_RECORD".equals(action)) {
            if (checkInText.isBlank() && checkOutText.isBlank() && notes.trim().isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Change at least one timestamp or add notes before saving.").showAndWait();
                return null;
            }
            if (!checkOutText.isBlank() && checkInText.isBlank()) {
                new Alert(Alert.AlertType.WARNING, "Check-out requires a check-in timestamp.").showAndWait();
                return null;
            }
            if (!checkInText.isBlank() && !checkOutText.isBlank()) {
                LocalTime checkInTime = LocalTime.parse(checkInText, TIME_TEXT_FORMAT);
                LocalTime checkOutTime = LocalTime.parse(checkOutText, TIME_TEXT_FORMAT);
                if (checkOutTime.isBefore(checkInTime)) {
                    new Alert(Alert.AlertType.WARNING, "Check-out time cannot be earlier than check-in time.").showAndWait();
                    return null;
                }
            }
        }

        return new OverrideDialogResult(
            actionDatePicker.getValue(),
            reason,
            notes,
            checkInText,
            checkOutText
        );
    }

    @SuppressWarnings("java:S1144")
    static boolean confirmSubmit(String action, AttendanceRecord record) {
        Alert confirm = new Alert(
            Alert.AlertType.CONFIRMATION,
            "Apply " + AttendanceOverrideSupport.overrideTitle(action) + " for " + record.getName() + "?",
            ButtonType.OK,
            ButtonType.CANCEL
        );
        confirm.setHeaderText("Confirm Attendance Action");
        return confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static LocalTime parseTimeText(String timeText) {
        if (timeText == null || timeText.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(timeText.trim(), FLEX_TIME_FORMAT);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Spinner<Integer> createTimeSpinner(int min, int max, int initialValue) {
        SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initialValue);
        valueFactory.setConverter(new StringConverter<Integer>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : String.format("%02d", value);
            }

            @Override
            public Integer fromString(String text) {
                String normalized = text == null ? "" : text.trim();
                if (normalized.isEmpty()) {
                    return 0;
                }
                return Integer.parseInt(normalized);
            }
        });

        Spinner<Integer> spinner = new Spinner<>(valueFactory);
        spinner.setEditable(true);
        spinner.setPrefWidth(84);
        return spinner;
    }

    private static void commitSpinnerEditor(Spinner<Integer> spinner) {
        if (!spinner.isEditable() || spinner.getValueFactory() == null) {
            return;
        }

        SpinnerValueFactory<Integer> valueFactory = spinner.getValueFactory();
        String text = spinner.getEditor().getText();
        try {
            valueFactory.setValue(valueFactory.getConverter().fromString(text));
        } catch (RuntimeException ex) {
            spinner.getEditor().setText(valueFactory.getConverter().toString(valueFactory.getValue()));
        }
    }

    private static final class TimeFieldControl {
        private final HBox container;
        private final CheckBox enabledToggle;
        private final Spinner<Integer> hourSpinner;
        private final Spinner<Integer> minuteSpinner;

        private TimeFieldControl(String initialTimeText, boolean allowEmpty) {
            LocalTime initialTime = parseTimeText(initialTimeText);
            if (initialTime == null) {
                initialTime = LocalTime.now().withSecond(0).withNano(0);
            }

            enabledToggle = allowEmpty ? new CheckBox("Set") : null;
            if (enabledToggle != null) {
                enabledToggle.setSelected(initialTimeText != null && !initialTimeText.isBlank());
            }

            hourSpinner = createTimeSpinner(0, 23, initialTime.getHour());
            minuteSpinner = createTimeSpinner(0, 59, initialTime.getMinute());

            Button nowButton = new Button("Now");
            nowButton.setOnAction(event -> applyTime(LocalTime.now().withSecond(0).withNano(0)));

            container = new HBox(8);
            container.setAlignment(Pos.CENTER_LEFT);
            if (enabledToggle != null) {
                container.getChildren().add(enabledToggle);
            }
            container.getChildren().addAll(hourSpinner, new Label(":"), minuteSpinner, nowButton);

            if (allowEmpty) {
                Button clearButton = new Button("Clear");
                clearButton.setOnAction(event -> enabledToggle.setSelected(false));
                container.getChildren().add(clearButton);
            }

            updateSpinnerState();
            if (enabledToggle != null) {
                enabledToggle.selectedProperty().addListener((obs, oldValue, selected) -> {
                    if (selected && (initialTimeText == null || initialTimeText.isBlank())) {
                        applyTime(LocalTime.now().withSecond(0).withNano(0));
                    }
                    updateSpinnerState();
                });
            }
        }

        private void applyTime(LocalTime time) {
            hourSpinner.getValueFactory().setValue(time.getHour());
            minuteSpinner.getValueFactory().setValue(time.getMinute());
            if (enabledToggle != null) {
                enabledToggle.setSelected(true);
            }
        }

        private void updateSpinnerState() {
            boolean disable = enabledToggle != null && !enabledToggle.isSelected();
            hourSpinner.setDisable(disable);
            minuteSpinner.setDisable(disable);
        }

        private HBox node() {
            return container;
        }

        private String timeText() {
            if (enabledToggle != null && !enabledToggle.isSelected()) {
                return "";
            }
            commitSpinnerEditor(hourSpinner);
            commitSpinnerEditor(minuteSpinner);
            return LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue()).format(TIME_TEXT_FORMAT);
        }
    }

    static final class OverrideDialogResult {
        final LocalDate attendanceDate;
        final String reason;
        final String notes;
        final String checkInText;
        final String checkOutText;

        OverrideDialogResult(LocalDate attendanceDate, String reason, String notes, String checkInText, String checkOutText) {
            this.attendanceDate = attendanceDate;
            this.reason = reason == null ? "" : reason;
            this.notes = notes == null ? "" : notes;
            this.checkInText = checkInText == null ? "" : checkInText;
            this.checkOutText = checkOutText == null ? "" : checkOutText;
        }
    }
}