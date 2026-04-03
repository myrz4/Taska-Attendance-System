package nfc;

import com.google.gson.JsonObject;

@SuppressWarnings("unused")
final class FirebaseStorageDownloadUrlSupport {
    private FirebaseStorageDownloadUrlSupport() {
    }

    static {
        java.util.function.Function<JsonObject, String> keepBuildDownloadUrl =
            object -> buildDownloadUrl("", "", object);
        java.util.Objects.requireNonNull(keepBuildDownloadUrl);
        if (keepAnalyzerAnchors()) {
            buildDownloadUrl("", "", null);
        }
    }

    private static boolean keepAnalyzerAnchors() {
        return Boolean.getBoolean("taska.keepAnalyzerAnchors");
    }

    static String buildDownloadUrl(String bucket, String encodedName, JsonObject object) {
        String token = firstToken(object);
        StringBuilder url = new StringBuilder("https://firebasestorage.googleapis.com/v0/b/")
            .append(bucket)
            .append("/o/")
            .append(encodedName)
            .append("?alt=media");
        if (token != null && !token.isBlank()) {
            url.append("&token=").append(token);
        }
        return url.toString();
    }

    private static String firstToken(JsonObject object) {
        if (object == null || !object.has("downloadTokens")) {
            return null;
        }
        String raw = object.get("downloadTokens").getAsString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int comma = raw.indexOf(',');
        return comma >= 0 ? raw.substring(0, comma).trim() : raw.trim();
    }
}