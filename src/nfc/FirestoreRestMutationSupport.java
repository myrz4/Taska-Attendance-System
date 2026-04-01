package nfc;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class FirestoreRestMutationSupport {
    private FirestoreRestMutationSupport() {
    }

    static String createDocumentWithIdUrl(String collectionUrl, String docId) {
        return collectionUrl
            + "?documentId="
            + URLEncoder.encode(Objects.requireNonNull(docId, "docId"), StandardCharsets.UTF_8);
    }

    static String appendUpdateMask(String url, Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return url;
        }

        StringBuilder sb = new StringBuilder(url);
        sb.append("?");
        boolean first = true;
        for (String key : fields.keySet()) {
            if (!first) {
                sb.append("&");
            }
            first = false;
            sb.append("updateMask.fieldPaths=")
                .append(URLEncoder.encode(key, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    static JsonObject createDocumentBody(Map<String, Object> fields) {
        JsonObject doc = new JsonObject();
        doc.add("fields", FirestoreRestValueSupport.toFields(fields));
        return doc;
    }

    static FsDocument parseDocumentResponse(String responseBody) {
        JsonObject obj = JsonParser.parseString(responseBody).getAsJsonObject();
        return FirestoreRestDocumentSupport.parseDocument(obj);
    }
}