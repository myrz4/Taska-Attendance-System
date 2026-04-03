package nfc;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class FirestoreRestDocumentSupport {
    private FirestoreRestDocumentSupport() {
    }

    static {
        java.util.function.Function<JsonObject, FsDocument> keepParse = FirestoreRestDocumentSupport::parseDocument;
        java.util.Objects.requireNonNull(keepParse);
    }

    static FsDocument parseDocument(JsonObject doc) {
        if (doc == null || !doc.has("name")) {
            return null;
        }
        String name = doc.get("name").getAsString();
        String id = name.substring(name.lastIndexOf('/') + 1);

        Map<String, Object> fields = new HashMap<>();
        if (doc.has("fields") && doc.get("fields").isJsonObject()) {
            JsonObject fs = doc.getAsJsonObject("fields");
            for (Map.Entry<String, JsonElement> entry : fs.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                fields.put(entry.getKey(), FirestoreRestValueSupport.fromValue(entry.getValue().getAsJsonObject()));
            }
        }

        return new FsDocument(id, fields);
    }
}