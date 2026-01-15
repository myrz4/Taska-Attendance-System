package nfc;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.Map;
import com.google.gson.Gson;

public class FirebaseAuthClient {
    private static final String API_KEY = "YOUR_WEB_API_KEY_HERE"; // from Firebase Console → Project settings → General → Web API key
    private static final String SIGN_IN_URL =
        "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=";

    private static final Gson gson = new Gson();

    public static FirebaseUser signInWithEmailPassword(String email, String password) throws IOException {
        URL url = new URL(SIGN_IN_URL + API_KEY);
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
                .lines().reduce("", (a,b)->a+b);

        if (code >= 200 && code < 300) {
            Map<?,?> m = gson.fromJson(body, Map.class);
            FirebaseUser u = new FirebaseUser();
            u.idToken = (String) m.get("idToken");
            u.refreshToken = (String) m.get("refreshToken");
            u.localId = (String) m.get("localId");
            u.email = (String) m.get("email");
            return u;
        } else {
            throw new IOException("Auth failed: " + body);
        }
    }

    public static class FirebaseUser {
        public String idToken;
        public String refreshToken;
        public String localId;
        public String email;
    }
}