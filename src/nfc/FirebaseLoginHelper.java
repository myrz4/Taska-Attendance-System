package nfc;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * FirebaseLoginHelper
 * Verifies email/password login using Firebase Auth REST API.
 */
public class FirebaseLoginHelper {

    // Replace with your Firebase Web API Key
    private static final String API_KEY = "AIzaSyBiuQTwMUfk-rpgp3I6GZ2-AZ6viNjaZq0";

    public static boolean verifyPassword(String email, String password) {
        try {
            String endpoint = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + API_KEY;
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String payload = String.format("{\"email\":\"%s\",\"password\":\"%s\",\"returnSecureToken\":true}", email, password);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes());
            }

            if (conn.getResponseCode() == 200) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    return json.has("idToken"); // ✅ Auth success
                }
            } else {
                try (InputStreamReader reader = new InputStreamReader(conn.getErrorStream())) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    System.err.println("[FirebaseLoginHelper] Error: " + json);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}