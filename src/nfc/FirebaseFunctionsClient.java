package nfc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.Gson;

/**
 * Minimal Firebase callable-functions client (HTTPS onCall protocol).
 *
 * NOTE: This is intended for 1-2 simple callables; it is not a full SDK.
 */
public final class FirebaseFunctionsClient {
    private static final Gson gson = new Gson();

    private FirebaseFunctionsClient() {}

    public static CallResult callClaimTeacherRole(String projectId, String idToken) throws IOException {
        String url = "https://asia-southeast1-" + projectId + ".cloudfunctions.net/claimTeacherRole";
        return callCallable(url, idToken, "{}");
    }

    public static CallResult callBillingGetHealth(String projectId, String idToken) throws IOException {
        String url = "https://asia-southeast1-" + projectId + ".cloudfunctions.net/billingGetHealth";
        return callCallable(url, idToken, "{}");
    }

    public static CallResult callBillingAdminListCatalogs(String projectId, String idToken) throws IOException {
        String url = "https://asia-southeast1-" + projectId + ".cloudfunctions.net/billingAdminListCatalogs";
        return callCallable(url, idToken, "{}");
    }

    public static CallResult callBillingAdminSaveCatalog(String projectId, String idToken, String dataJsonObject) throws IOException {
        String url = "https://asia-southeast1-" + projectId + ".cloudfunctions.net/billingAdminSaveCatalog";
        return callCallable(url, idToken, dataJsonObject);
    }

    public static CallResult callBillingAdminActivateCatalog(String projectId, String idToken, String dataJsonObject) throws IOException {
        String url = "https://asia-southeast1-" + projectId + ".cloudfunctions.net/billingAdminActivateCatalog";
        return callCallable(url, idToken, dataJsonObject);
    }

    public static CallResult callBillingAdminListAudit(String projectId, String idToken, String dataJsonObject) throws IOException {
        String url = "https://asia-southeast1-" + projectId + ".cloudfunctions.net/billingAdminListAudit";
        return callCallable(url, idToken, dataJsonObject);
    }

    public static CallResult callBillingAdminGenerateInvoicesForPeriod(String projectId, String idToken, String dataJsonObject) throws IOException {
        String url = "https://asia-southeast1-" + projectId + ".cloudfunctions.net/billingAdminGenerateInvoicesForPeriod";
        return callCallable(url, idToken, dataJsonObject);
    }

    private static CallResult callCallable(String endpoint, String idToken, String dataJsonObject) throws IOException {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("Missing idToken");
        }

        URL url = new URL(endpoint);
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
            @SuppressWarnings("unchecked")
            Map<Object, Object> res = (Map<Object, Object>) resObj;

            Object ok = res.get("ok");
            boolean okBool = (ok instanceof Boolean) ? (Boolean) ok : false;
            String reason = res.get("reason") == null ? null : String.valueOf(res.get("reason"));
            return new CallResult(okBool, reason, body);
        } catch (Exception ignored) {
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
