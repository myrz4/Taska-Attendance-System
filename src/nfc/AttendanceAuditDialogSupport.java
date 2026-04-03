package nfc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

@SuppressWarnings("unused")
final class AttendanceAuditDialogSupport {
    private static final List<String> ATTENDANCE_AUDIT_ACTIONS = Arrays.asList(
        "all",
        "CHECK_IN",
        "CHECK_OUT",
        "MANUAL_CHECK_IN",
        "MANUAL_CHECK_OUT",
        "MARK_ABSENT",
        "EDIT_RECORD",
        "REOPEN_RECORD"
    );

    private AttendanceAuditDialogSupport() {}

    static void showAuditDialog(Window owner, LocalDate attendanceDate, AttendanceRecord record) {
        if (record == null) {
            return;
        }

        Alert auditDialog = new Alert(Alert.AlertType.INFORMATION);
        auditDialog.setTitle("Attendance Audit");
        auditDialog.setHeaderText("Attendance Audit for " + record.getName() + " on " + attendanceDate);

        ComboBox<String> actionFilter = new ComboBox<>(FXCollections.observableArrayList(ATTENDANCE_AUDIT_ACTIONS));
        actionFilter.setValue("all");
        actionFilter.setPrefWidth(180);

        Button refreshBtn = new Button("Refresh");
        Button exportTxtBtn = new Button("Export Audit TXT");

        TextArea body = new TextArea("Loading attendance audit entries...");
        body.setEditable(false);
        body.setWrapText(false);
        body.setPrefColumnCount(96);
        body.setPrefRowCount(24);
        body.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

        HBox controls = new HBox(8,
            new Label("Action:"),
            actionFilter,
            refreshBtn,
            exportTxtBtn
        );
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, controls, body);
        AtomicReference<AttendanceAuditSupport.AttendanceAuditSnapshot> currentSnapshot = new AtomicReference<>(
            new AttendanceAuditSupport.AttendanceAuditSnapshot("all", "Loading attendance audit entries...")
        );

        Runnable refreshAction = () -> refreshAttendanceAuditLog(attendanceDate, record, body, currentSnapshot, AttendanceAuditSupport.normalizeAttendanceAuditActionFilter(actionFilter.getValue()));
        refreshBtn.setOnAction(e -> refreshAction.run());
        actionFilter.setOnAction(e -> refreshAction.run());
        exportTxtBtn.setOnAction(e -> AttendanceAuditSupport.exportAttendanceAuditTxt(owner, record, currentSnapshot.get()));

        auditDialog.getDialogPane().setContent(content);
        auditDialog.getDialogPane().setMinWidth(860);
        auditDialog.show();

        refreshAttendanceAuditLog(attendanceDate, record, body, currentSnapshot, "all");
    }

    private static void refreshAttendanceAuditLog(
        LocalDate attendanceDate,
        AttendanceRecord record,
        TextArea body,
        AtomicReference<AttendanceAuditSupport.AttendanceAuditSnapshot> currentSnapshot,
        String actionFilter
    ) {
        body.setText("Loading attendance audit entries...");
        CompletableFuture.runAsync(() -> {
            AttendanceAuditSupport.AttendanceAuditSnapshot snapshot = AttendanceAuditSupport.fetchAttendanceAuditSnapshot(attendanceDate, record, actionFilter);
            Platform.runLater(() -> {
                currentSnapshot.set(snapshot);
                body.setText(snapshot.formattedText);
            });
        });
    }
}