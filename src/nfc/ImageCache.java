package nfc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.scene.image.Image;

/**
 * Simple disk cache for remote image URLs.
 *
 * - If a URL has already been downloaded, loads from local file.
 * - Otherwise returns an Image from the remote URL (backgroundLoading=true)
 *   and downloads to disk in the background for faster subsequent runs.
 */
public final class ImageCache {

    private static final Path CACHE_DIR = initCacheDir();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "image-cache");
        t.setDaemon(true);
        return t;
    });

    private ImageCache() {}

    private static Path initCacheDir() {
        try {
            // Use user home to keep it simple and consistent.
            Path dir = Paths.get(System.getProperty("user.home"), ".taskazurah", "image_cache");
            Files.createDirectories(dir);
            return dir;
        } catch (Exception e) {
            // Fallback to current directory.
            return Paths.get("image_cache");
        }
    }

    public static boolean isRemoteUrl(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("http://") || t.startsWith("https://");
    }

    public static Image loadCachedOrRemote(String url, double requestedWidth, double requestedHeight) {
        if (!isRemoteUrl(url)) {
            return new Image(String.valueOf(url), requestedWidth, requestedHeight, true, true, true);
        }

        Path cached = cachedPathForUrl(url);
        if (cached != null && Files.exists(cached)) {
            return new Image(cached.toUri().toString(), requestedWidth, requestedHeight, true, true, true);
        }

        // Trigger background download to populate cache.
        prefetch(url);

        // Still show remote while downloading; JavaFX will load in background.
        return new Image(url.trim(), requestedWidth, requestedHeight, true, true, true);
    }

    public static void prefetch(String url) {
        if (!isRemoteUrl(url)) return;
        String normalized = url.trim();
        if (normalized.isEmpty()) return;

        Path cached = cachedPathForUrl(normalized);
        if (cached == null) return;
        if (Files.exists(cached)) return;

        if (!IN_FLIGHT.add(normalized)) return;

        CompletableFuture.runAsync(() -> {
            try {
                RemoteImageFetcher.downloadToFile(normalized, cached);
            } catch (Exception ignored) {
            } finally {
                IN_FLIGHT.remove(normalized);
            }
        }, EXEC);
    }

    private static Path cachedPathForUrl(String url) {
        try {
            String u = url.trim();
            if (u.isEmpty()) return null;

            String ext = guessExtension(u);
            String hash = sha256Hex(u);
            return CACHE_DIR.resolve(hash + ext);
        } catch (Exception e) {
            return null;
        }
    }

    private static String guessExtension(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        // Basic heuristic; tokenized Firebase URLs still carry object name extension.
        if (lower.contains(".png")) return ".png";
        if (lower.contains(".webp")) return ".webp";
        if (lower.contains(".gif")) return ".gif";
        if (lower.contains(".jpg")) return ".jpg";
        if (lower.contains(".jpeg")) return ".jpeg";
        return ".img";
    }

    private static String sha256Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] dig = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(dig.length * 2);
        for (byte b : dig) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
