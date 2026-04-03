package nfc;

import com.google.gson.Gson;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unused")
final class AttendanceAuditFormatSupport {
    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter AUDIT_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private AttendanceAuditFormatSupport() {
    }

    static String normalizeAttendanceAuditActionFilter(String actionFilter) {
        String normalized = String.valueOf(actionFilter == null ? "all" : actionFilter).trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? "all" : normalized;
    }

    static List<Map<String, Object>> filterAttendanceAuditEntries(List<Map<String, Object>> entries, String actionFilter) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedFilter = normalizeAttendanceAuditActionFilter(actionFilter);
        if ("all".equals(normalizedFilter)) {
            return entries;
        }

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            String action = String.valueOf(entry.get("action") == null ? "" : entry.get("action")).trim();
            if (normalizedFilter.equalsIgnoreCase(action)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    @SuppressWarnings("unused")
    static String formatAttendanceAuditEntries(LocalDate attendanceDate, AttendanceRecord record, List<Map<String, Object>> entries, String actionFilter) {
        if (entries == null || entries.isEmpty()) {
            if ("all".equals(normalizeAttendanceAuditActionFilter(actionFilter))) {
                return "No attendance audit entries found for this record.";
            }
            return "No attendance audit entries found for action filter: " + normalizeAttendanceAuditActionFilter(actionFilter);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Attendance Audit Entries\n");
        sb.append("========================\n\n");
        sb.append("Child: ").append(record.getName()).append("\n");
        sb.append("Attendance ID: ").append(attendanceIdFor(attendanceDate, record)).append("\n");
        sb.append("Filter: ").append(normalizeAttendanceAuditActionFilter(actionFilter)).append("\n");
        sb.append("Entries: ").append(entries.size()).append("\n\n");

        for (Map<String, Object> entry : entries) {
            String actorName = firstNonBlank(
                stringValue(entry.get("actorName")),
                extractNestedString(entry, "actor", "displayName")
            );
            String actorRole = firstNonBlank(
                stringValue(entry.get("actorRole")),
                extractNestedString(entry, "actor", "role")
            );

            sb.append("Time: ").append(formatAuditValue(entry.get("createdAt"))).append("\n");
            sb.append("Action: ").append(formatAuditAction(entry.get("action"))).append("\n");
            sb.append("Method: ").append(formatAuditMethod(entry.get("method"))).append("\n");
            sb.append("Actor: ").append(actorName.isBlank() ? "-" : actorName).append("\n");
            sb.append("Role: ").append(actorRole.isBlank() ? "-" : actorRole).append("\n");
            sb.append("Reason: ").append(displayAuditString(entry.get("reason"))).append("\n");
            sb.append(formatAttendanceAuditDetails(entry.get("details")));
            sb.append("----------------------------------------\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unused")
    static String sanitizeFilePart(String text) {
        String value = stringValue(text).replaceAll("[^A-Za-z0-9_-]", "_");
        return value.isBlank() ? "attendance" : value;
    }

    private static String formatAttendanceAuditDetails(Object detailsObj) {
        if (!(detailsObj instanceof Map<?, ?>)) {
            return "Details: -\n";
        }
        Map<?, ?> details = (Map<?, ?>) detailsObj;
        List<String> parts = new ArrayList<>();
        addAuditDetail(parts, "Child", details.get("childName"));
        addAuditDetail(parts, "Date", details.get("attendanceDate"));
        addAuditTransition(parts, "Status", details.get("previousStatus"), details.get("nextStatus"));
        addAuditTransition(parts, "Check-In", details.get("previousCheckInAt"), details.get("nextCheckInAt"));
        addAuditTransition(parts, "Check-Out", details.get("previousCheckOutAt"), details.get("nextCheckOutAt"));
        addAuditDetail(parts, "Parent", details.get("parentName"));
        addAuditDetail(parts, "Parent Phone", details.get("parentPhone"));
        addAuditDetail(parts, "Representative", details.get("representativeName"));
        addAuditDetail(parts, "Relationship", details.get("representativeRole"));
        addAuditDetail(parts, "Pickup Token", details.get("tokenValue"));
        addAuditDetail(parts, "NFC UID", details.get("nfcUid"));
        addAuditDetail(parts, "Notes", details.get("notes"));

        if (parts.isEmpty()) {
            return "Details: " + GSON.toJson(details) + "\n";
        }

        StringBuilder sb = new StringBuilder("Details:\n");
        for (String part : parts) {
            sb.append("  - ").append(part).append("\n");
        }
        return sb.toString();
    }

    private static void addAuditDetail(List<String> parts, String label, Object value) {
        String rendered = formatAuditValue(value);
        if (!rendered.isBlank() && !"-".equals(rendered)) {
            parts.add(label + ": " + rendered);
        }
    }

    private static void addAuditTransition(List<String> parts, String label, Object previousValue, Object nextValue) {
        String previousText = formatAuditValue(previousValue);
        String nextText = formatAuditValue(nextValue);
        if ("-".equals(previousText) && "-".equals(nextText)) {
            return;
        }
        parts.add(label + ": " + previousText + " -> " + nextText);
    }

    private static String formatAuditValue(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof Date) {
            return LocalDateTime.ofInstant(((Date) value).toInstant(), ZoneId.systemDefault()).format(AUDIT_DISPLAY_FORMAT);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return "-";
        }
        try {
            return LocalDateTime.ofInstant(Date.from(java.time.Instant.parse(text)).toInstant(), ZoneId.systemDefault()).format(AUDIT_DISPLAY_FORMAT);
        } catch (RuntimeException ignored) {
            return text;
        }
    }

    private static String formatAuditAction(Object value) {
        String text = stringValue(value).toUpperCase(Locale.ROOT);
        switch (text) {
            case "CHECK_IN":
                return "Check-In";
            case "CHECK_OUT":
                return "Check-Out";
            case "MANUAL_CHECK_IN":
                return "Manual Check-In";
            case "MANUAL_CHECK_OUT":
                return "Manual Check-Out";
            case "MARK_ABSENT":
                return "Mark Absent";
            case "EDIT_RECORD":
                return "Edit Record";
            case "REOPEN_RECORD":
                return "Reopen Record";
            default:
                return displayAuditString(value);
        }
    }

    private static String formatAuditMethod(Object value) {
        String text = stringValue(value).toUpperCase(Locale.ROOT);
        switch (text) {
            case "NFC":
                return "NFC tap";
            case "PARENT_QR":
                return "Parent QR";
            case "MANUAL":
                return "Admin manual";
            default:
                return displayAuditString(value);
        }
    }

    private static String displayAuditString(Object value) {
        String text = stringValue(value);
        return text.isBlank() ? "-" : text;
    }

    private static String attendanceIdFor(LocalDate attendanceDate, AttendanceRecord record) {
        return attendanceDate + "_" + record.getChildDocId();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String extractNestedString(Map<String, Object> source, String parentKey, String childKey) {
        if (source == null || parentKey == null || childKey == null) {
            return "";
        }
        Object parent = source.get(parentKey);
        if (!(parent instanceof Map<?, ?>)) {
            return "";
        }
        Map<?, ?> nested = (Map<?, ?>) parent;
        Object value = nested.get(childKey);
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}