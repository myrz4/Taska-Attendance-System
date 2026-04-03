package nfc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Window;

@SuppressWarnings("unused")
final class AttendanceAuditSupport {
    private AttendanceAuditSupport() {}

    static {
        java.util.function.Supplier<AttendanceAuditSnapshot> keepFetchAttendanceAuditSnapshot =
            () -> fetchAttendanceAuditSnapshot(LocalDate.now(), null, "All Actions");
        java.util.function.Consumer<AttendanceAuditSnapshot> keepExportAttendanceAuditTxt =
            snapshot -> exportAttendanceAuditTxt(null, null, snapshot);
        AttendanceAuditSnapshot probe = new AttendanceAuditSnapshot("", "");
        java.util.Objects.requireNonNull(keepFetchAttendanceAuditSnapshot);
        java.util.Objects.requireNonNull(keepExportAttendanceAuditTxt);
        java.util.Objects.hash(probe.actionFilter, probe.formattedText);
    }

    static AttendanceAuditSnapshot fetchAttendanceAuditSnapshot(LocalDate attendanceDate, AttendanceRecord record, String actionFilter) {
        if (!UserSession.isLoggedIn()) {
            return new AttendanceAuditSnapshot(actionFilter, "Attendance audit unavailable: no active login session.");
        }

        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            List<FsDocument> rawEntries = client.queryWhereEqual("attendanceAudit", "attendanceId", attendanceIdFor(attendanceDate, record));
            List<Map<String, Object>> entries = new ArrayList<>();
            for (FsDocument doc : rawEntries) {
                Map<String, Object> entry = new LinkedHashMap<>(doc.fields());
                entry.put("_docId", doc.getId());
                entries.add(entry);
            }
            entries.sort(Comparator.comparingLong(AttendanceAuditSupport::auditSortTime).reversed());
            List<Map<String, Object>> filteredEntries = AttendanceAuditFormatSupport.filterAttendanceAuditEntries(entries, actionFilter);
            return new AttendanceAuditSnapshot(actionFilter, AttendanceAuditFormatSupport.formatAttendanceAuditEntries(attendanceDate, record, filteredEntries, actionFilter));
        } catch (IOException | InterruptedException ex) {
            return new AttendanceAuditSnapshot(actionFilter, "Attendance audit load failed: " + ex.getMessage());
        }
    }

    static String normalizeAttendanceAuditActionFilter(String actionFilter) {
        return AttendanceAuditFormatSupport.normalizeAttendanceAuditActionFilter(actionFilter);
    }

    static void exportAttendanceAuditTxt(Window owner, AttendanceRecord record, AttendanceAuditSnapshot snapshot) {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Attendance Audit Log (TXT)");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
            fileChooser.setInitialFileName(
                "attendance-audit-" + AttendanceAuditFormatSupport.sanitizeFilePart(record.getName()) + "-"
                    + normalizeAttendanceAuditActionFilter(snapshot.actionFilter) + "-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".txt"
            );

            File file = fileChooser.showSaveDialog(owner);
            if (file == null) {
                return;
            }

            try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
                out.print(snapshot.formattedText);
            }
            new Alert(Alert.AlertType.INFORMATION, "Audit log saved to:\n" + file.getAbsolutePath()).showAndWait();
        } catch (IOException | SecurityException ex) {
            new Alert(Alert.AlertType.ERROR, "Failed to export attendance audit: " + ex.getMessage()).showAndWait();
        }
    }

    private static long auditSortTime(Map<String, Object> entry) {
        Object createdAt = entry == null ? null : entry.get("createdAt");
        if (createdAt instanceof Date) {
            return ((Date) createdAt).getTime();
        }
        return 0L;
    }

    private static String attendanceIdFor(LocalDate attendanceDate, AttendanceRecord record) {
        return attendanceDate + "_" + record.getChildDocId();
    }

    @SuppressWarnings("unused")
    static final class AttendanceAuditSnapshot {
        final String actionFilter;
        final String formattedText;

        AttendanceAuditSnapshot(String actionFilter, String formattedText) {
            this.actionFilter = actionFilter;
            this.formattedText = formattedText;
        }
    }
}