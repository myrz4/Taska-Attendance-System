package nfc;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.Map;
import com.google.gson.Gson;

public class FirebaseAuthClient {
    private static final String SIGN_IN_URL =
        "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=";

    private static final String SIGN_UP_URL =
        "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=";

    private static final String REFRESH_URL =
        "https://securetoken.googleapis.com/v1/token?key=";

    private static final Gson gson = new Gson();

    private static String apiKey() {
        // 1) JVM property
        String k = System.getProperty("FIREBASE_WEB_API_KEY");
        if (k != null && !k.isBlank()) return k.trim();

        // 2) Environment variable
        k = System.getenv("FIREBASE_WEB_API_KEY");
        if (k != null && !k.isBlank()) return k.trim();

        // 3) File in jar_files/ (safe to ship)
        k = FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "webApiKey");
        if (k != null && !k.isBlank()) return k.trim();

        throw new IllegalStateException(
            "Missing Firebase Web API key. Set FIREBASE_WEB_API_KEY env/Java property, or add jar_files/firebase.properties with webApiKey=<key>."
        );
    }

    public static FirebaseUser signInWithEmailPassword(String email, String password) throws IOException {
        return authCall(SIGN_IN_URL + apiKey(), email, password);
    }

    public static FirebaseUser signUpWithEmailPassword(String email, String password) throws IOException {
        return authCall(SIGN_UP_URL + apiKey(), email, password);
    }

    /**
     * Refresh an ID token so custom claims (e.g., role) are picked up.
     * Uses the Secure Token API: https://firebase.google.com/docs/reference/rest/auth#section-refresh-token
     */
    public static FirebaseUser refreshIdToken(String refreshToken) throws IOException {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Missing refreshToken");
        }

        URL url = new URL(REFRESH_URL + apiKey());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        String bodyReq = "grant_type=refresh_token&refresh_token=" + java.net.URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyReq.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines().reduce("", (a, b) -> a + b);

        if (code >= 200 && code < 300) {
            Map<?, ?> m = gson.fromJson(body, Map.class);
            FirebaseUser u = new FirebaseUser();
            // SecureToken uses snake_case keys
            u.idToken = (String) m.get("id_token");
            u.refreshToken = (String) m.get("refresh_token");
            u.localId = (String) m.get("user_id");
            return u;
        }

        String errCode = extractFirebaseErrorCode(body);
        if (errCode != null && !errCode.isBlank()) {
            throw new FirebaseAuthException(errCode, body);
        }
        throw new IOException("Token refresh failed: " + body);
    }

    private static FirebaseUser authCall(String endpoint, String email, String password) throws IOException {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        String json = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"returnSecureToken\":true}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines().reduce("", (a, b) -> a + b);

        if (code >= 200 && code < 300) {
            Map<?, ?> m = gson.fromJson(body, Map.class);
            FirebaseUser u = new FirebaseUser();
            u.idToken = (String) m.get("idToken");
            u.refreshToken = (String) m.get("refreshToken");
            u.localId = (String) m.get("localId");
            u.email = (String) m.get("email");
            return u;
        }

        String errCode = extractFirebaseErrorCode(body);
        if (errCode != null && !errCode.isBlank()) {
            throw new FirebaseAuthException(errCode, body);
        }
        throw new IOException("Auth failed: " + body);
    }

    @SuppressWarnings("unchecked")
    private static String extractFirebaseErrorCode(String body) {
        try {
            Map<?, ?> m = gson.fromJson(body, Map.class);
            Object err = m.get("error");
            if (!(err instanceof Map)) return null;
            Object msg = ((Map<?, ?>) err).get("message");
            return msg == null ? null : String.valueOf(msg);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static final class FirebaseAuthException extends IOException {
        public final String code;

        public FirebaseAuthException(String code, String rawBody) {
            super("Auth failed: " + code);
            this.code = code;
        }
    }

    public static class FirebaseUser {
        public String idToken;
        public String refreshToken;
        public String localId;
        public String email;
    }
}