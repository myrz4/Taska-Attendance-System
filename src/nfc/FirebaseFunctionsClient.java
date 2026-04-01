package nfc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

/**
 * Minimal Firebase callable-functions client (HTTPS onCall protocol).
 *
 * NOTE: This is intended for 1-2 simple callables; it is not a full SDK.
 */
public final class FirebaseFunctionsClient {
    private static final Gson gson = new Gson();
    private static final String REGION = "asia-southeast1";

    private FirebaseFunctionsClient() {}

    public static CallResult callClaimTeacherRole(String projectId, String idToken) throws IOException {
        return callCallableByName(projectId, idToken, "claimTeacherRole", "{}");
    }

    public static CallResult callBillingGetHealth(String projectId, String idToken) throws IOException {
        return callCallableByName(projectId, idToken, "billingGetHealth", "{}");
    }

    public static CallResult callBillingAdminListCatalogs(String projectId, String idToken) throws IOException {
        return callCallableByName(projectId, idToken, "billingAdminListCatalogs", "{}");
    }

    public static CallResult callBillingAdminSaveCatalog(String projectId, String idToken, String dataJsonObject) throws IOException {
        return callCallableByName(projectId, idToken, "billingAdminSaveCatalog", dataJsonObject);
    }

    public static CallResult callBillingAdminActivateCatalog(String projectId, String idToken, String dataJsonObject) throws IOException {
        return callCallableByName(projectId, idToken, "billingAdminActivateCatalog", dataJsonObject);
    }

    public static CallResult callBillingAdminListAudit(String projectId, String idToken, String dataJsonObject) throws IOException {
        return callCallableByName(projectId, idToken, "billingAdminListAudit", dataJsonObject);
    }

    public static CallResult callBillingAdminGenerateInvoicesForPeriod(String projectId, String idToken, String dataJsonObject) throws IOException {
        return callCallableByName(projectId, idToken, "billingAdminGenerateInvoicesForPeriod", dataJsonObject);
    }

    public static CallResult callAttendanceNfcCheckIn(String projectId, String idToken, String dataJsonObject) throws IOException {
        return callCallableByName(projectId, idToken, "attendanceNfcCheckIn", dataJsonObject);
    }

    public static CallResult callAttendanceCheckoutWithParentQr(String projectId, String idToken, String dataJsonObject) throws IOException {
        return callCallableByName(projectId, idToken, "attendanceCheckoutWithParentQr", dataJsonObject);
    }

    public static CallResult callAttendanceAdminOverride(String projectId, String idToken, String dataJsonObject) throws IOException {
        return callCallableByName(projectId, idToken, "attendanceAdminOverride", dataJsonObject);
    }

    public static CallResult callCasualTransitCreateVisit(String projectId, String idToken, String dataJsonObject) throws IOException {
        return callCallableByName(projectId, idToken, "casualTransitCreateVisit", dataJsonObject);
    }

    public static CallResult callCasualTransitCheckoutVisit(String projectId, String idToken, String dataJsonObject) throws IOException {
        return callCallableByName(projectId, idToken, "casualTransitCheckoutVisit", dataJsonObject);
    }

    public static CallResult callCasualTransitAdminOverride(String projectId, String idToken, String dataJsonObject) throws IOException {
        return callCallableByName(projectId, idToken, "casualTransitAdminOverride", dataJsonObject);
    }

    private static CallResult callCallableByName(String projectId, String idToken, String functionName, String dataJsonObject) throws IOException {
        List<String> candidateProjectIds = candidateProjectIds(projectId, idToken);
        CallResult last404 = null;

        for (String candidateProjectId : candidateProjectIds) {
            String endpoint = callableEndpoint(candidateProjectId, functionName);
            CallResult result = callCallable(endpoint, idToken, dataJsonObject);
            if (!"http-404".equals(result.reason)) {
                return result;
            }
            last404 = result;
        }

        if (last404 != null) {
            return new CallResult(
                false,
                "http-404",
                "Callable endpoint not found after trying projects " + candidateProjectIds + ". Last response: " + last404.rawBody
            );
        }

        return new CallResult(false, "http-404", "Callable endpoint not found.");
    }

    private static List<String> candidateProjectIds(String explicitProjectId, String idToken) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        addProjectId(ids, explicitProjectId);
        addProjectId(ids, FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "projectId"));
        addProjectId(ids, JwtUtils.extractStringClaim(idToken, "aud"));

        String issuer = JwtUtils.extractStringClaim(idToken, "iss");
        if (issuer != null && !issuer.isBlank()) {
            int idx = issuer.lastIndexOf('/');
            if (idx >= 0 && idx + 1 < issuer.length()) {
                addProjectId(ids, issuer.substring(idx + 1));
            }
        }

        return new ArrayList<>(ids);
    }

    private static void addProjectId(LinkedHashSet<String> ids, String projectId) {
        if (projectId == null) {
            return;
        }
        String normalized = projectId.trim();
        if (!normalized.isBlank()) {
            ids.add(normalized);
        }
    }

    private static String callableEndpoint(String projectId, String functionName) {
        return "https://" + REGION + "-" + projectId + ".cloudfunctions.net/" + functionName;
    }

    private static CallResult callCallable(String endpoint, String idToken, String dataJsonObject) throws IOException {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("Missing idToken");
        }

        URL url = URI.create(endpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);

        // Callable request payload is {"data": <object>}
        String payload = "{\"data\":" + (dataJsonObject == null ? "{}" : dataJsonObject) + "}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines().reduce("", (a, b) -> a + b);

        if (code < 200 || code >= 300) {
            return new CallResult(false, "http-" + code, body);
        }

        // Expected: {"result": { ... }}
        try {
            Map<?, ?> m = gson.fromJson(body, Map.class);
            Object resObj = (m != null) ? m.get("result") : null;
            if (!(resObj instanceof Map)) {
                // Some environments may return the result map at top-level.
                resObj = m;
            }
            if (!(resObj instanceof Map)) {
                return new CallResult(true, null, body);
            }
            @SuppressWarnings("unchecked")
            Map<Object, Object> res = (Map<Object, Object>) resObj;

            Object ok = res.get("ok");
            boolean okBool = (ok instanceof Boolean) ? (Boolean) ok : false;
            String reason = res.get("reason") == null ? null : String.valueOf(res.get("reason"));
            return new CallResult(okBool, reason, body);
        } catch (ClassCastException | IllegalStateException ignored) {
            // If parsing fails, treat 200 as success but return raw body.
            return new CallResult(true, null, body);
        }
    }

    public static final class CallResult {
        public final boolean ok;
        public final String reason;
        public final String rawBody;

        public CallResult(boolean ok, String reason, String rawBody) {
            this.ok = ok;
            this.reason = reason;
            this.rawBody = rawBody;
        }
    }
}
