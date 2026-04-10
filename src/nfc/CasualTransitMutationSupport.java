package nfc;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;

public final class CasualTransitMutationSupport {
    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter INPUT_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private CasualTransitMutationSupport() {}

    public static void createVisit(Map<String, String> values) {
        try {
            String payload = GSON.toJson(Map.of(
                "childName", safe(values.get("childName")),
                "transitType", safe(values.get("transitType")),
                "staffType", safe(values.get("staffType")),
                "guardianName", safe(values.get("guardianName")),
                "guardianPhone", safe(values.get("guardianPhone")),
                "guardianRelationship", safe(values.get("guardianRelationship")),
                "notes", safe(values.get("notes")),
                "adminName", adminName()
            ));
            FirebaseFunctionsClient.CallResult result = FirebaseFunctionsClient.callCasualTransitCreateVisit(
                FirestoreRest.projectId(),
                UserSession.getIdToken(),
                payload
            );
            ensureOk(result);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    public static void checkoutVisit(CasualTransitView.VisitRow row, Map<String, String> values) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("visitId", row.visitId());
            payload.put("paymentMethod", safe(values.get("paymentMethod")));
            payload.put("notes", safe(values.get("notes")));
            payload.put("adminName", adminName());
            String amount = safe(values.get("amount"));
            if (!amount.isBlank()) {
                payload.put("amountSen", parseRmToSen(amount));
            }
            FirebaseFunctionsClient.CallResult result = FirebaseFunctionsClient.callCasualTransitCheckoutVisit(
                FirestoreRest.projectId(),
                UserSession.getIdToken(),
                GSON.toJson(payload)
            );
            ensureOk(result);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    public static Map<String, String> normalizeEditValues(Map<String, String> values) {
        String reason = safe(values.get("reason"));
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason-required");
        }
        String childName = safe(values.get("childName"));
        String guardianName = safe(values.get("guardianName"));
        String guardianPhone = safe(values.get("guardianPhone"));
        if (childName.isBlank() || guardianName.isBlank() || guardianPhone.isBlank()) {
            throw new IllegalArgumentException("missing-required-fields");
        }
        Map<String, String> payload = new LinkedHashMap<>(values);
        payload.put("reason", reason);
        payload.put("childName", childName);
        payload.put("guardianName", guardianName);
        payload.put("guardianPhone", guardianPhone);
        return payload;
    }

    public static void overrideVisit(String action, CasualTransitView.VisitRow row, Map<String, String> values) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("action", action);
            payload.put("visitId", row.visitId());
            payload.put("reason", safe(values.get("reason")));
            payload.put("notes", safe(values.get("notes")));
            payload.put("adminName", adminName());
            putIfNotBlank(payload, "childName", values.get("childName"));
            putIfNotBlank(payload, "guardianName", values.get("guardianName"));
            putIfNotBlank(payload, "guardianPhone", values.get("guardianPhone"));
            putIfNotBlank(payload, "guardianRelationship", values.get("guardianRelationship"));
            putIfNotBlank(payload, "paymentMethod", values.get("paymentMethod"));
            putIfNotBlank(payload, "checkInAt", toIsoInstant(values.get("checkInAt")));
            putIfNotBlank(payload, "checkOutAt", toIsoInstant(values.get("checkOutAt")));
            String amount = safe(values.get("amount"));
            if (!amount.isBlank()) {
                payload.put("amountSen", parseRmToSen(amount));
            }

            FirebaseFunctionsClient.CallResult result = FirebaseFunctionsClient.callCasualTransitAdminOverride(
                FirestoreRest.projectId(),
                UserSession.getIdToken(),
                GSON.toJson(payload)
            );
            ensureOk(result);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    private static void ensureOk(FirebaseFunctionsClient.CallResult result) throws IOException {
        if (!result.ok) {
            throw new IOException(result.reason == null ? result.rawBody : result.reason);
        }
    }

    private static String adminName() {
        String name = safe(UserSession.getName());
        return name.isBlank() ? safe(UserSession.getUsername()) : name;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static void putIfNotBlank(Map<String, Object> map, String key, String value) {
        String safeValue = safe(value);
        if (!safeValue.isBlank()) {
            map.put(key, safeValue);
        }
    }

    private static long parseRmToSen(String amount) {
        String value = safe(amount);
        if (value.isBlank()) {
            throw new IllegalArgumentException("missing-amount");
        }
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed) || parsed < 0) {
            throw new IllegalArgumentException("invalid-amount");
        }
        return Math.round(parsed * 100.0d);
    }

    private static String toIsoInstant(String value) {
        String safeValue = safe(value);
        if (safeValue.isBlank()) return "";
        LocalDateTime localDateTime = LocalDateTime.parse(safeValue, INPUT_DATE_TIME_FORMAT);
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toString();
    }
}