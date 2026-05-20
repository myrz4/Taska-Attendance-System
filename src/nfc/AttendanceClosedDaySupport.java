package nfc;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

final class AttendanceClosedDaySupport {
    private static final Gson GSON = new Gson();

    private AttendanceClosedDaySupport() {
    }

    static DayStatus fetchDayStatus(LocalDate date) throws IOException {
        LocalDate resolvedDate = date == null ? LocalDate.now() : date;
        Map<String, Object> payload = new HashMap<>();
        payload.put("attendanceDate", resolvedDate.toString());
        FirebaseFunctionsClient.CallResult result = FirebaseFunctionsClient.callAttendanceGetDayStatus(
            FirestoreRest.projectId(),
            UserSession.getIdToken(),
            GSON.toJson(payload)
        );
        return parseDayStatusResult(resolvedDate, result);
    }

    static DayStatus safeFetchDayStatus(LocalDate date) {
        try {
            return fetchDayStatus(date);
        } catch (IOException ex) {
            return fallbackStatus(date);
        }
    }

    static DayStatus setDayClosed(LocalDate date, boolean closed) throws IOException {
        LocalDate resolvedDate = date == null ? LocalDate.now() : date;
        Map<String, Object> payload = new HashMap<>();
        payload.put("attendanceDate", resolvedDate.toString());
        payload.put("closed", closed);
        String actorName = currentActorName();
        if (!actorName.isBlank()) {
            payload.put("adminName", actorName);
        }

        FirebaseFunctionsClient.CallResult result = FirebaseFunctionsClient.callAttendanceAdminSetDayClosure(
            FirestoreRest.projectId(),
            UserSession.getIdToken(),
            GSON.toJson(payload)
        );
        return parseDayStatusResult(resolvedDate, result);
    }

    private static DayStatus parseDayStatusResult(LocalDate fallbackDate, FirebaseFunctionsClient.CallResult result) throws IOException {
        Map<?, ?> resultMap = BillingPolicyCallableSupport.parseCallableResultMap(result.rawBody);
        if (!result.ok) {
            throw new IOException(BillingPolicyCallableSupport.humanizeCallableReason(result.reason, resultMap));
        }
        if (resultMap == null || resultMap.isEmpty()) {
            return fallbackStatus(fallbackDate);
        }
        return parseDayStatusMap(fallbackDate, resultMap);
    }

    private static DayStatus parseDayStatusMap(LocalDate fallbackDate, Map<?, ?> result) {
        String dateKey = stringValue(result.get("dateKey"), fallbackDate == null ? "" : fallbackDate.toString());
        LocalDate resolvedDate = parseDate(dateKey, fallbackDate);
        boolean closed = booleanValue(result.get("closed"));
        boolean customClosed = booleanValue(result.get("customClosed"));
        boolean scheduleClosed = booleanValue(result.get("scheduleClosed"));
        String closureLabel = stringValue(result.get("closureLabel"), "");
        String closureReason = stringValue(result.get("closureReason"), "");
        String message = stringValue(result.get("message"), "");
        if (closed && message.isBlank()) {
            message = defaultClosedMessage(dateKey, customClosed, scheduleClosed, closureLabel);
        }
        return new DayStatus(resolvedDate, dateKey, closed, customClosed, scheduleClosed, closureLabel, closureReason, message);
    }

    static DayStatus fallbackStatus(LocalDate date) {
        LocalDate resolvedDate = date == null ? LocalDate.now() : date;
        if (resolvedDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return new DayStatus(
                resolvedDate,
                resolvedDate.toString(),
                true,
                false,
                true,
                "Sunday",
                "Sunday",
                "Taska is closed on Sunday. Attendance is unavailable for " + resolvedDate + "."
            );
        }
        return DayStatus.open(resolvedDate);
    }

    private static String currentActorName() {
        String name = UserSession.getName();
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        String email = UserSession.getEmail();
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        String username = UserSession.getUsername();
        return username == null ? "" : username.trim();
    }

    private static LocalDate parseDate(String dateKey, LocalDate fallbackDate) {
        String normalized = dateKey == null ? "" : dateKey.trim();
        if (normalized.isBlank()) {
            return fallbackDate == null ? LocalDate.now() : fallbackDate;
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException ignored) {
            return fallbackDate == null ? LocalDate.now() : fallbackDate;
        }
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return false;
        }
        return "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private static String stringValue(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String defaultClosedMessage(String dateKey, boolean customClosed, boolean scheduleClosed, String closureLabel) {
        String label = closureLabel == null ? "" : closureLabel.trim();
        if (customClosed && !label.isBlank()) {
            return "Taska is closed on " + dateKey + " (" + label + "). Attendance is unavailable for this date.";
        }
        if (scheduleClosed && !label.isBlank()) {
            return "Taska is closed on " + label + ". Attendance is unavailable for " + dateKey + ".";
        }
        return "Taska is closed on " + dateKey + ". Attendance is unavailable for this date.";
    }

    static final class DayStatus {
        private final LocalDate date;
        private final String dateKey;
        private final boolean closed;
        private final boolean customClosed;
        private final boolean scheduleClosed;
        private final String closureLabel;
        private final String closureReason;
        private final String message;

        DayStatus(
            LocalDate date,
            String dateKey,
            boolean closed,
            boolean customClosed,
            boolean scheduleClosed,
            String closureLabel,
            String closureReason,
            String message
        ) {
            this.date = date == null ? LocalDate.now() : date;
            this.dateKey = dateKey == null ? this.date.toString() : dateKey;
            this.closed = closed;
            this.customClosed = customClosed;
            this.scheduleClosed = scheduleClosed;
            this.closureLabel = closureLabel == null ? "" : closureLabel;
            this.closureReason = closureReason == null ? "" : closureReason;
            this.message = message == null ? "" : message;
        }

        static DayStatus open(LocalDate date) {
            LocalDate resolvedDate = date == null ? LocalDate.now() : date;
            return new DayStatus(resolvedDate, resolvedDate.toString(), false, false, false, "", "", "");
        }

        LocalDate date() {
            return date;
        }

        String dateKey() {
            return dateKey;
        }

        boolean closed() {
            return closed;
        }

        boolean customClosed() {
            return customClosed;
        }

        boolean scheduleClosed() {
            return scheduleClosed;
        }

        String closureLabel() {
            return closureLabel;
        }

        String closureReason() {
            return closureReason;
        }

        String message() {
            return message;
        }

        String actionButtonText() {
            return customClosed ? "Reopen Day" : "Set Closed Day";
        }
    }
}