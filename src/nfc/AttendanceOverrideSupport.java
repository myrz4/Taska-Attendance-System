package nfc;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

@SuppressWarnings("java:S1144")
final class AttendanceOverrideSupport {
    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter DB_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FLEX_TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

    private AttendanceOverrideSupport() {}

    static {
        java.util.function.Function<String, String> keepTitle = AttendanceOverrideSupport::overrideTitle;
        java.util.function.Function<String, String> keepDefaultTime = AttendanceOverrideSupport::defaultTimeText;
        java.util.function.Supplier<String> keepCurrentTime = AttendanceOverrideSupport::currentTimeText;
        java.util.function.BiFunction<LocalDate, String, String> keepIsoTimestamp = AttendanceOverrideSupport::buildIsoTimestamp;
        java.util.function.Function<AttendanceRecord, Map<String, Object>> keepPayload =
            record -> buildPayload("", record, LocalDate.now(), "", "", "", "", "", null);
        java.util.function.Function<Map<String, Object>, FirebaseFunctionsClient.CallResult> keepSubmit = payload -> {
            try {
                return submitOverride(payload);
            } catch (IOException error) {
                throw new java.io.UncheckedIOException(error);
            }
        };
        java.util.Objects.requireNonNull(keepTitle);
        java.util.Objects.requireNonNull(keepDefaultTime);
        java.util.Objects.requireNonNull(keepCurrentTime);
        java.util.Objects.requireNonNull(keepIsoTimestamp);
        java.util.Objects.requireNonNull(keepPayload);
        java.util.Objects.requireNonNull(keepSubmit);
    }

    @SuppressWarnings("java:S1144")
    static String overrideTitle(String action) {
        switch (action) {
            case "MANUAL_CHECK_IN":
                return "Manual Check-In";
            case "MANUAL_CHECK_OUT":
                return "Manual Check-Out";
            case "MARK_ABSENT":
                return "Mark Absent";
            case "EDIT_RECORD":
                return "Edit Attendance Record";
            case "REOPEN_RECORD":
                return "Reopen Attendance Record";
            default:
                return "Attendance Override";
        }
    }

    @SuppressWarnings("java:S1144")
    static String defaultTimeText(String fullTimestamp) {
        if (fullTimestamp == null || fullTimestamp.isBlank()) return "";
        try {
            return LocalDateTime.parse(fullTimestamp, DB_TIMESTAMP_FORMAT)
                .toLocalTime()
                .format(TIME_FORMAT);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    @SuppressWarnings("java:S1144")
    static String currentTimeText() {
        return LocalTime.now().withSecond(0).withNano(0).format(TIME_FORMAT);
    }

    static String buildIsoTimestamp(LocalDate date, String timeText) {
        try {
            LocalTime time = LocalTime.parse(timeText.trim(), FLEX_TIME_FORMAT);
            return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toString();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Use 24-hour time like 08:30 or 17:45.");
        }
    }

    @SuppressWarnings("java:S1144")
    static Map<String, Object> buildPayload(
        String action,
        AttendanceRecord record,
        LocalDate attendanceDate,
        String reason,
        String notes,
        String adminName,
        String checkInText,
        String checkOutText,
        LocalDate checkOutDate
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        payload.put("childId", record.getChildDocId());
        payload.put("attendanceDate", attendanceDate.toString());
        payload.put("reason", reason == null ? "" : reason.trim());
        payload.put("notes", notes == null ? "" : notes.trim());
        payload.put("adminName", adminName == null ? "" : adminName.trim());

        if (("MANUAL_CHECK_IN".equals(action) || "EDIT_RECORD".equals(action)) && checkInText != null && !checkInText.trim().isEmpty()) {
            payload.put("checkInAt", buildIsoTimestamp(attendanceDate, checkInText.trim()));
        }
        LocalDate checkOutActualDate = checkOutDate == null ? attendanceDate : checkOutDate;
        if (checkOutActualDate != null && !checkOutActualDate.equals(attendanceDate)) {
            payload.put("checkOutDate", checkOutActualDate.toString());
        }
        if (("MANUAL_CHECK_OUT".equals(action) || "EDIT_RECORD".equals(action)) && checkOutText != null && !checkOutText.trim().isEmpty()) {
            payload.put("checkOutAt", buildIsoTimestamp(checkOutActualDate, checkOutText.trim()));
        }
        return payload;
    }

    @SuppressWarnings("java:S1144")
    static FirebaseFunctionsClient.CallResult submitOverride(Map<String, Object> payload) throws IOException {
        return FirebaseFunctionsClient.callAttendanceAdminOverride(
            FirestoreRest.projectId(),
            UserSession.getIdToken(),
            GSON.toJson(payload)
        );
    }
}
