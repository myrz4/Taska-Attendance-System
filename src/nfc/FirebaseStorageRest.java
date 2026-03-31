package nfc;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Minimal Firebase Storage REST client authenticated using a Firebase Auth ID token.
 *
 * Uses the JSON API:
 * POST https://firebasestorage.googleapis.com/v0/b/{bucket}/o?uploadType=media&name={object}
 */
public final class FirebaseStorageRest {
    private FirebaseStorageRest() {
    }

    public static String bucket() {
        String b = FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "storageBucket");
        if (b != null && !b.isBlank()) return b.trim();

        String projectId = FirestoreRest.projectId();
        // Prefer the bucket style already present in your exported DB for teacher/memory photos.
        return projectId + ".firebasestorage.app";
    }

    public static String uploadPublicDownloadUrl(String objectName, byte[] bytes, String contentType)
        throws IOException, InterruptedException {

        Objects.requireNonNull(objectName, "objectName");
        Objects.requireNonNull(bytes, "bytes");
        String token = UserSession.getIdToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Missing idToken. Please log in again.");
        }

        String bucket = bucket();
        String encodedName = URLEncoder.encode(objectName, StandardCharsets.UTF_8);

        String url = "https://firebasestorage.googleapis.com/v0/b/"
            + bucket
            + "/o?uploadType=media&name="
            + encodedName;

        HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/json")
            .header("Content-Type", (contentType == null || contentType.isBlank())
                ? "application/octet-stream"
                : contentType)
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Storage upload failed: " + resp.statusCode() + " " + resp.body());
        }

        JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
        String downloadTokens = obj.has("downloadTokens") ? obj.get("downloadTokens").getAsString() : null;
        String tokenPart = null;
        if (downloadTokens != null && !downloadTokens.isBlank()) {
            int idx = downloadTokens.indexOf(',');
            tokenPart = (idx >= 0) ? downloadTokens.substring(0, idx) : downloadTokens;
            tokenPart = tokenPart.trim();
        }

        if (tokenPart == null || tokenPart.isBlank()) {
            // The object still uploaded; it may be protected by Storage rules.
            // Return the object URL without token so caller can still store it.
            return "https://firebasestorage.googleapis.com/v0/b/"
                + bucket
                + "/o/"
                + encodedName
                + "?alt=media";
        }

        return "https://firebasestorage.googleapis.com/v0/b/"
            + bucket
            + "/o/"
            + encodedName
            + "?alt=media&token="
            + URLEncoder.encode(tokenPart, StandardCharsets.UTF_8);
    }
}
