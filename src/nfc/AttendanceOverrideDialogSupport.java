package nfc;

import java.time.LocalDate;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

final class AttendanceOverrideDialogSupport {
    private AttendanceOverrideDialogSupport() {}

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
        TextField checkInField = new TextField(AttendanceOverrideSupport.defaultTimeText(record.getCheckInFullTimestamp()));
        TextField checkOutField = new TextField(AttendanceOverrideSupport.defaultTimeText(record.getCheckOutFullTimestamp()));
        if ("MANUAL_CHECK_IN".equals(action) && checkInField.getText().isBlank()) {
            checkInField.setText(AttendanceOverrideSupport.currentTimeText());
        }
        if ("MANUAL_CHECK_OUT".equals(action) && checkOutField.getText().isBlank()) {
            checkOutField.setText(AttendanceOverrideSupport.currentTimeText());
        }

        TextField reasonField = new TextField();
        TextArea notesArea = new TextArea();
        notesArea.setPrefRowCount(3);
        reasonField.setPromptText("Required reason");
        notesArea.setPromptText("Optional notes");
        checkInField.setPromptText("HH:mm");
        checkOutField.setPromptText("HH:mm");

        int row = 0;
        grid.add(new Label("Attendance Date"), 0, row);
        grid.add(actionDatePicker, 1, row++);

        if ("MANUAL_CHECK_IN".equals(action) || "EDIT_RECORD".equals(action)) {
            grid.add(new Label("Check-In Time"), 0, row);
            grid.add(checkInField, 1, row++);
        }
        if ("MANUAL_CHECK_OUT".equals(action) || "EDIT_RECORD".equals(action)) {
            grid.add(new Label("Check-Out Time"), 0, row);
            grid.add(checkOutField, 1, row++);
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

        return new OverrideDialogResult(
            actionDatePicker.getValue(),
            reason,
            notesArea.getText(),
            checkInField.getText(),
            checkOutField.getText()
        );
    }

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