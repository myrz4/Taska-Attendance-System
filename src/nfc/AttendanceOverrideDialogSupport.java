package nfc;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
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
        OverrideDialogResult probe = new OverrideDialogResult(null, null, null, null, null, null, null, null);
        java.util.Objects.requireNonNull(keepConfirmCompleted);
        java.util.Objects.requireNonNull(keepPrompt);
        java.util.Objects.requireNonNull(keepConfirmSubmit);
        java.util.Objects.hash(
            probe.attendanceDate,
            probe.reason,
            probe.notes,
            probe.checkInText,
            probe.checkOutText,
            probe.checkoutTeacherId,
            probe.checkoutTeacherName,
            probe.checkoutTeacherEmail
        );
    }

    @SuppressWarnings("java:S1144")
    static boolean confirmCompletedRecordEdit(AttendanceRecord record) {
        return AppThemeSupport.showConfirm(
            null,
            "Completed Record Warning",
            "This record already has a completed check-out.",
            "Editing it can affect billing, pickup history, and teacher overtime payroll. Continue?",
            AppThemeSupport.Tone.WARNING,
            "Continue",
            "Cancel"
        );
    }

    @SuppressWarnings("java:S1144")
    static OverrideDialogResult promptForOverride(String action, AttendanceRecord record, LocalDate currentDate) {
        return promptForOverride(action, record, currentDate, record == null ? String.valueOf(currentDate) : record.getName() + " on " + currentDate);
    }

    static OverrideDialogResult promptForOverride(String action, AttendanceRecord record, LocalDate currentDate, String subjectLabel) {
        Dialog<ButtonType> dialog = new Dialog<>();
        AppThemeSupport.prepareDialog(
            dialog,
            AttendanceOverrideSupport.overrideTitle(action),
            subjectLabel,
            "EDIT_RECORD".equals(action) ? AppThemeSupport.Tone.WARNING : AppThemeSupport.Tone.INFO
        );
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        AppThemeSupport.styleDialogButtons(dialog, ButtonType.OK);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getStyleClass().add("app-form-grid");

        DatePicker actionDatePicker = new DatePicker(currentDate);
        String initialCheckInText = record == null ? "" : AttendanceOverrideSupport.defaultTimeText(record.getCheckInFullTimestamp());
        String initialCheckOutText = record == null ? "" : AttendanceOverrideSupport.defaultTimeText(record.getCheckOutFullTimestamp());
        if ("MANUAL_CHECK_IN".equals(action) && initialCheckInText.isBlank()) {
            initialCheckInText = AttendanceOverrideSupport.currentTimeText();
        }
        if ("MANUAL_CHECK_OUT".equals(action) && initialCheckOutText.isBlank()) {
            initialCheckOutText = AttendanceOverrideSupport.currentTimeText();
        }
        TimeFieldControl checkInControl = new TimeFieldControl(initialCheckInText, "EDIT_RECORD".equals(action));
        TimeFieldControl checkOutControl = new TimeFieldControl(initialCheckOutText, "EDIT_RECORD".equals(action));
        ComboBox<TeacherChoice> checkoutTeacherCombo = new ComboBox<>();
        checkoutTeacherCombo.setMaxWidth(Double.MAX_VALUE);

        TextField reasonField = new TextField();
        TextArea notesArea = new TextArea();
        notesArea.setPrefRowCount(3);
        reasonField.setPromptText("Optional reason");
        notesArea.setPromptText("Optional notes");
        AppThemeSupport.styleControls(actionDatePicker, reasonField, notesArea);

        Label reasonHelper = new Label("Reason is optional. Add one when the change needs extra billing or audit context.");
        reasonHelper.getStyleClass().add("app-helper-text");
        reasonHelper.setWrapText(true);

        TeacherChoice initialTeacherChoice = null;
        if ("MANUAL_CHECK_OUT".equals(action) || "EDIT_RECORD".equals(action)) {
            List<TeacherChoice> teacherChoices = loadTeacherChoices(record);
            checkoutTeacherCombo.getItems().setAll(teacherChoices);
            initialTeacherChoice = selectInitialTeacherChoice(teacherChoices, record);
            checkoutTeacherCombo.setValue(initialTeacherChoice);
            checkoutTeacherCombo.setPromptText("Select teacher");
            AppThemeSupport.styleControls(checkoutTeacherCombo);
        }

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
            grid.add(new Label("Checkout handled by"), 0, row);
            grid.add(checkoutTeacherCombo, 1, row++);
        }

        GridPane notesGrid = new GridPane();
        notesGrid.setHgap(10);
        notesGrid.setVgap(10);
        notesGrid.getStyleClass().add("app-form-grid");
        notesGrid.add(new Label("Reason"), 0, 0);
        notesGrid.add(reasonField, 1, 0);
        notesGrid.add(reasonHelper, 1, 1);
        notesGrid.add(new Label("Notes"), 0, 2);
        notesGrid.add(notesArea, 1, 2);

        dialog.getDialogPane().setContent(
            AppThemeSupport.createDialogContent(
                AppThemeSupport.createFormSection(
                    "Attendance timing",
                    "Adjust the attendance date and the timestamps that should be kept on record.",
                    grid
                ),
                AppThemeSupport.createFormSection(
                    "Reason & notes",
                    "Provide enough context for other admins reviewing this change later.",
                    notesGrid
                )
            )
        );
        dialog.getDialogPane().setPrefWidth(560);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return null;
        }

        String reason = reasonField.getText() == null ? "" : reasonField.getText().trim();
        String notes = notesArea.getText() == null ? "" : notesArea.getText();
        String checkInText = checkInControl.timeText();
        String checkOutText = checkOutControl.timeText();
        TeacherChoice selectedTeacher = checkoutTeacherCombo.getValue();

        if ("MANUAL_CHECK_OUT".equals(action)) {
            if (selectedTeacher == null || selectedTeacher.teacherId().isBlank()) {
                AppThemeSupport.showWarning(null, "Teacher Required", "Select the teacher who handled this checkout.");
                return null;
            }
        }

        if ("EDIT_RECORD".equals(action)) {
            if (checkInText.isBlank() && checkOutText.isBlank() && notes.trim().isEmpty()) {
                AppThemeSupport.showWarning(null, "No Changes Detected", "Change at least one timestamp or add notes before saving.");
                return null;
            }
            if (!checkOutText.isBlank() && checkInText.isBlank()) {
                AppThemeSupport.showWarning(null, "Missing Check-In", "Check-out requires a check-in timestamp.");
                return null;
            }
            if (!checkInText.isBlank() && !checkOutText.isBlank()) {
                LocalTime checkInTime = LocalTime.parse(checkInText, TIME_TEXT_FORMAT);
                LocalTime checkOutTime = LocalTime.parse(checkOutText, TIME_TEXT_FORMAT);
                if (checkOutTime.isBefore(checkInTime)) {
                    AppThemeSupport.showWarning(null, "Invalid Time Range", "Check-out time cannot be earlier than check-in time.");
                    return null;
                }
            }

            boolean checkoutTeacherChanged = selectedTeacherChanged(initialTeacherChoice, selectedTeacher);
            boolean checkoutEditRequiresAudit = !checkOutText.isBlank() || checkoutTeacherChanged;
            if (checkoutEditRequiresAudit && (selectedTeacher == null || selectedTeacher.teacherId().isBlank())) {
                AppThemeSupport.showWarning(null, "Teacher Required", "Select the teacher who handled this checkout before saving the correction.");
                return null;
            }
        }

        return new OverrideDialogResult(
            actionDatePicker.getValue(),
            reason,
            notes,
            checkInText,
            checkOutText,
            selectedTeacher == null ? "" : selectedTeacher.teacherId(),
            selectedTeacher == null ? "" : selectedTeacher.teacherName(),
            selectedTeacher == null ? "" : selectedTeacher.teacherEmail()
        );
    }

    private static List<TeacherChoice> loadTeacherChoices(AttendanceRecord record) {
        List<TeacherChoice> choices = new ArrayList<>();
        try {
            for (Map<String, Object> row : TeacherDataSupport.loadTeachers()) {
                if (row == null) {
                    continue;
                }
                String teacherId = row.get("id") == null ? "" : String.valueOf(row.get("id")).trim();
                if (teacherId.isBlank()) {
                    continue;
                }
                String teacherName = row.get("name") == null ? "" : String.valueOf(row.get("name")).trim();
                String teacherEmail = row.get("email") == null ? "" : String.valueOf(row.get("email")).trim().toLowerCase();
                choices.add(new TeacherChoice(teacherId, teacherName, teacherEmail));
            }
        } catch (IOException | InterruptedException ex) {
            AppThemeSupport.showError(null, "Unable to Load Teachers", String.valueOf(ex.getMessage()));
        }

        if (record != null) {
            TeacherChoice snapshotChoice = new TeacherChoice(
                record.getCheckedOutByTeacherId(),
                record.getCheckedOutByTeacherName(),
                record.getCheckedOutByTeacherEmail()
            );
            if (!snapshotChoice.teacherId().isBlank() && choices.stream().noneMatch(choice -> choice.teacherId().equals(snapshotChoice.teacherId()))) {
                choices.add(0, snapshotChoice);
            }
        }
        return choices;
    }

    private static TeacherChoice selectInitialTeacherChoice(List<TeacherChoice> choices, AttendanceRecord record) {
        if (record == null || choices == null || choices.isEmpty()) {
            return null;
        }
        String teacherId = record.getCheckedOutByTeacherId();
        String teacherEmail = record.getCheckedOutByTeacherEmail();
        for (TeacherChoice choice : choices) {
            if (choice == null) {
                continue;
            }
            if (!teacherId.isBlank() && teacherId.equals(choice.teacherId())) {
                return choice;
            }
            if (!teacherEmail.isBlank() && teacherEmail.equalsIgnoreCase(choice.teacherEmail())) {
                return choice;
            }
        }
        return null;
    }

    private static boolean selectedTeacherChanged(TeacherChoice initialTeacherChoice, TeacherChoice selectedTeacher) {
        String initialTeacherId = initialTeacherChoice == null ? "" : initialTeacherChoice.teacherId();
        String selectedTeacherId = selectedTeacher == null ? "" : selectedTeacher.teacherId();
        String initialTeacherEmail = initialTeacherChoice == null ? "" : initialTeacherChoice.teacherEmail();
        String selectedTeacherEmail = selectedTeacher == null ? "" : selectedTeacher.teacherEmail();
        return !initialTeacherId.equals(selectedTeacherId)
            || !initialTeacherEmail.equalsIgnoreCase(selectedTeacherEmail);
    }

    @SuppressWarnings("java:S1144")
    static boolean confirmSubmit(String action, AttendanceRecord record) {
        return confirmSubmit(action, record == null ? AttendanceOverrideSupport.overrideTitle(action) : record.getName());
    }

    static boolean confirmSubmit(String action, String subjectLabel) {
        return AppThemeSupport.showConfirm(
            null,
            "Confirm Attendance Action",
            AttendanceOverrideSupport.overrideTitle(action),
            "Apply " + AttendanceOverrideSupport.overrideTitle(action) + " for " + subjectLabel + "?",
            AppThemeSupport.Tone.INFO,
            "Apply",
            "Cancel"
        );
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
                return Integer.valueOf(normalized);
            }
        });

        Spinner<Integer> spinner = new Spinner<>(valueFactory);
        spinner.setEditable(true);
        spinner.setPrefWidth(84);
        AppThemeSupport.styleControls(spinner);
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
            AppThemeSupport.styleSecondaryButtons(nowButton);

            container = new HBox(8);
            container.setAlignment(Pos.CENTER_LEFT);
            if (enabledToggle != null) {
                container.getChildren().add(enabledToggle);
            }
            container.getChildren().addAll(hourSpinner, new Label(":"), minuteSpinner, nowButton);

            if (allowEmpty) {
                Button clearButton = new Button("Clear");
                clearButton.setOnAction(event -> enabledToggle.setSelected(false));
                AppThemeSupport.styleGhostButtons(clearButton);
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
        final String checkoutTeacherId;
        final String checkoutTeacherName;
        final String checkoutTeacherEmail;

        OverrideDialogResult(
            LocalDate attendanceDate,
            String reason,
            String notes,
            String checkInText,
            String checkOutText,
            String checkoutTeacherId,
            String checkoutTeacherName,
            String checkoutTeacherEmail
        ) {
            this.attendanceDate = attendanceDate;
            this.reason = reason == null ? "" : reason;
            this.notes = notes == null ? "" : notes;
            this.checkInText = checkInText == null ? "" : checkInText;
            this.checkOutText = checkOutText == null ? "" : checkOutText;
            this.checkoutTeacherId = checkoutTeacherId == null ? "" : checkoutTeacherId;
            this.checkoutTeacherName = checkoutTeacherName == null ? "" : checkoutTeacherName;
            this.checkoutTeacherEmail = checkoutTeacherEmail == null ? "" : checkoutTeacherEmail;
        }
    }

    private static final class TeacherChoice {
        private final String teacherId;
        private final String teacherName;
        private final String teacherEmail;

        private TeacherChoice(String teacherId, String teacherName, String teacherEmail) {
            this.teacherId = teacherId == null ? "" : teacherId;
            this.teacherName = teacherName == null ? "" : teacherName;
            this.teacherEmail = teacherEmail == null ? "" : teacherEmail;
        }

        private String teacherId() {
            return teacherId;
        }

        private String teacherName() {
            return teacherName;
        }

        private String teacherEmail() {
            return teacherEmail;
        }

        @Override
        public String toString() {
            if (teacherName != null && !teacherName.isBlank()) {
                return teacherEmail == null || teacherEmail.isBlank()
                    ? teacherName
                    : teacherName + " (" + teacherEmail + ")";
            }
            return teacherEmail == null ? "" : teacherEmail;
        }
    }
}