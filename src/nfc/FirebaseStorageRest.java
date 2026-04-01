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
        return FirebaseStorageDownloadUrlSupport.buildDownloadUrl(bucket, encodedName, obj);
    }
}
