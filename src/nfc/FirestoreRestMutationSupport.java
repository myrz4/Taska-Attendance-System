package nfc;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@SuppressWarnings("all")
final class FirestoreRestMutationSupport {
    private FirestoreRestMutationSupport() {
    }

    @SuppressWarnings("unused")
    static String createDocumentWithIdUrl(String collectionUrl, String docId) {
        return collectionUrl
            + "?documentId="
            + URLEncoder.encode(Objects.requireNonNull(docId, "docId"), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unused")
    static String appendUpdateMask(String url, Map<String, Object> fields) {
        return appendUpdateMask(url, fields == null ? null : fields.keySet());
    }

    @SuppressWarnings("unused")
    static String appendUpdateMask(String url, Iterable<String> fieldPaths) {
        if (fieldPaths == null) {
            return url;
        }

        StringBuilder sb = new StringBuilder(url);
        boolean first = true;
        for (String key : fieldPaths) {
            if (key == null || key.isBlank()) {
                continue;
            }
            sb.append(first ? "?" : "&");
            first = false;
            sb.append("updateMask.fieldPaths=")
                .append(URLEncoder.encode(key, StandardCharsets.UTF_8));
        }
        return first ? url : sb.toString();
    }

    @SuppressWarnings("unused")
    static JsonObject createDocumentBody(Map<String, Object> fields) {
        JsonObject doc = new JsonObject();
        doc.add("fields", FirestoreRestValueSupport.toFields(fields));
        return doc;
    }

    @SuppressWarnings("unused")
    static FsDocument parseDocumentResponse(String responseBody) {
        JsonObject obj = JsonParser.parseString(responseBody).getAsJsonObject();
        return FirestoreRestDocumentSupport.parseDocument(obj);
    }
}