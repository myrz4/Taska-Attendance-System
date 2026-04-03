package nfc;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

public final class RemoteImageFetcher {
    private RemoteImageFetcher() {
    }

    public static void downloadToFile(String url, Path target) throws IOException, InterruptedException {
        if (url == null || url.isBlank() || target == null) {
            return;
        }

        Files.createDirectories(target.getParent());

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url.trim()))
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build();

        Path tempFile = Files.createTempFile(target.getParent(), "img-", ".tmp");
        try {
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Image download failed: " + response.statusCode());
            }
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}