package nfc;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Firestore REST client (v1) authenticated using a Firebase Auth ID token.
 * This respects Firestore Security Rules.
 */
public final class FirestoreRestClient {
    private static final Gson gson = new Gson();

    private final String projectId;
    private final String idToken;
    private final HttpClient http;

    public FirestoreRestClient(String projectId, String idToken) {
        this.projectId = Objects.requireNonNull(projectId, "projectId");
        this.idToken = Objects.requireNonNull(idToken, "idToken");
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    public List<FsDocument> listDocuments(String collectionId) throws IOException, InterruptedException {
        return listDocumentsFromUrl(collectionUrl(collectionId));
    }

    public List<FsDocument> listSubcollectionDocuments(String parentCollectionId, String parentDocId, String subcollectionId)
        throws IOException, InterruptedException {

        return listDocumentsFromUrl(subcollectionUrl(parentCollectionId, parentDocId, subcollectionId));
    }

    private List<FsDocument> listDocumentsFromUrl(String collectionUrl) throws IOException, InterruptedException {
        List<FsDocument> out = new ArrayList<>();

        String pageToken = null;
        do {
            StringBuilder url = new StringBuilder(collectionUrl);
            url.append("?pageSize=1000");
            if (pageToken != null && !pageToken.isBlank()) {
                url.append("&pageToken=").append(URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
            }

            JsonObject body = getJson(url.toString());
            if (body.has("documents") && body.get("documents").isJsonArray()) {
                for (JsonElement el : body.getAsJsonArray("documents")) {
                    if (!el.isJsonObject()) continue;
                    FsDocument d = parseDocument(el.getAsJsonObject());
                    if (d != null) out.add(d);
                }
            }
            pageToken = body.has("nextPageToken") ? body.get("nextPageToken").getAsString() : null;
        } while (pageToken != null && !pageToken.isBlank());

        return out;
    }

    public FsDocument getDocument(String collectionId, String docId) throws IOException, InterruptedException {
        String url = documentUrl(collectionId, docId);
        HttpResponse<String> resp = send("GET", url, null);
        if (resp.statusCode() == 404) return null;
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Firestore GET failed: " + resp.statusCode() + " " + resp.body());
        }
        JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
        return parseDocument(obj);
    }

    /**
     * Creates a document with a server-assigned ID (auto ID).
     * Returns the created document (including its generated id).
     */
    public FsDocument addDocumentAutoId(String collectionId, Map<String, Object> fields)
        throws IOException, InterruptedException {

        String url = collectionUrl(collectionId);
        JsonObject doc = new JsonObject();
        doc.add("fields", toFields(fields));

        HttpResponse<String> resp = send("POST", url, gson.toJson(doc));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Firestore POST failed: " + resp.statusCode() + " " + resp.body());
        }

        JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
        return parseDocument(obj);
    }

    public FsDocument addSubcollectionDocumentAutoId(
        String parentCollectionId,
        String parentDocId,
        String subcollectionId,
        Map<String, Object> fields)
        throws IOException, InterruptedException {

        String url = subcollectionUrl(parentCollectionId, parentDocId, subcollectionId);
        JsonObject doc = new JsonObject();
        doc.add("fields", toFields(fields));

        HttpResponse<String> resp = send("POST", url, gson.toJson(doc));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Firestore POST failed: " + resp.statusCode() + " " + resp.body());
        }

        JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
        return parseDocument(obj);
    }

    /**
     * Creates a document with an explicit document ID.
     * This uses Firestore REST create semantics and will fail if the document already exists.
     */
    public FsDocument createDocumentWithId(String collectionId, String docId, Map<String, Object> fields)
        throws IOException, InterruptedException {

        String url = collectionUrl(collectionId)
            + "?documentId="
            + URLEncoder.encode(Objects.requireNonNull(docId, "docId"), StandardCharsets.UTF_8);

        JsonObject doc = new JsonObject();
        doc.add("fields", toFields(fields));

        HttpResponse<String> resp = send("POST", url, gson.toJson(doc));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Firestore create failed: " + resp.statusCode() + " " + resp.body());
        }

        JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
        return parseDocument(obj);
    }

    public List<FsDocument> runQuery(JsonObject structuredQuery) throws IOException, InterruptedException {
        String url = baseUrl() + ":runQuery";
        JsonObject req = new JsonObject();
        req.add("structuredQuery", structuredQuery);

        HttpResponse<String> resp = send("POST", url, gson.toJson(req));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Firestore runQuery failed: " + resp.statusCode() + " " + resp.body());
        }

        JsonElement parsed = JsonParser.parseString(resp.body());
        if (!parsed.isJsonArray()) return List.of();

        List<FsDocument> out = new ArrayList<>();
        JsonArray arr = parsed.getAsJsonArray();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject row = el.getAsJsonObject();
            if (!row.has("document") || !row.get("document").isJsonObject()) continue;
            FsDocument doc = parseDocument(row.getAsJsonObject("document"));
            if (doc != null) out.add(doc);
        }
        return out;
    }

    public List<FsDocument> queryWhereEqual(String collectionId, String fieldPath, Object value)
        throws IOException, InterruptedException {

        JsonObject field = new JsonObject();
        field.addProperty("fieldPath", fieldPath);

        JsonObject filter = new JsonObject();
        filter.add("field", field);
        filter.addProperty("op", "EQUAL");
        filter.add("value", toValue(value));

        JsonObject where = new JsonObject();
        where.add("fieldFilter", filter);

        JsonObject from = new JsonObject();
        from.addProperty("collectionId", collectionId);

        JsonArray fromArr = new JsonArray();
        fromArr.add(from);

        JsonObject q = new JsonObject();
        q.add("from", fromArr);
        q.add("where", where);
        q.addProperty("limit", 2000);

        return runQuery(q);
    }

    public void patchDocumentMerge(String collectionId, String docId, Map<String, Object> fields)
        throws IOException, InterruptedException {

        String url = documentUrl(collectionId, docId);
        if (fields != null && !fields.isEmpty()) {
            StringBuilder sb = new StringBuilder(url);
            sb.append("?");
            boolean first = true;
            for (String k : fields.keySet()) {
                if (!first) sb.append("&");
                first = false;
                sb.append("updateMask.fieldPaths=")
                  .append(URLEncoder.encode(k, StandardCharsets.UTF_8));
            }
            url = sb.toString();
        }

        JsonObject doc = new JsonObject();
        doc.add("fields", toFields(fields));

        HttpResponse<String> resp = send("PATCH", url, gson.toJson(doc));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Firestore PATCH failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    public void patchSubcollectionDocumentMerge(
        String parentCollectionId,
        String parentDocId,
        String subcollectionId,
        String docId,
        Map<String, Object> fields)
        throws IOException, InterruptedException {

        String url = subcollectionDocumentUrl(parentCollectionId, parentDocId, subcollectionId, docId);
        if (fields != null && !fields.isEmpty()) {
            StringBuilder sb = new StringBuilder(url);
            sb.append("?");
            boolean first = true;
            for (String k : fields.keySet()) {
                if (!first) sb.append("&");
                first = false;
                sb.append("updateMask.fieldPaths=")
                  .append(URLEncoder.encode(k, StandardCharsets.UTF_8));
            }
            url = sb.toString();
        }

        JsonObject doc = new JsonObject();
        doc.add("fields", toFields(fields));

        HttpResponse<String> resp = send("PATCH", url, gson.toJson(doc));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Firestore PATCH failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    public void deleteDocument(String collectionId, String docId) throws IOException, InterruptedException {
        String url = documentUrl(collectionId, docId);
        HttpResponse<String> resp = send("DELETE", url, null);
        if (resp.statusCode() == 404) return;
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Firestore DELETE failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    public String referenceValue(String collectionId, String docId) {
        return "projects/" + projectId + "/databases/(default)/documents/" + collectionId + "/" + docId;
    }

    private String baseUrl() {
        return "https://firestore.googleapis.com/v1/projects/" + projectId + "/databases/(default)/documents";
    }

    private String collectionUrl(String collectionId) {
        return baseUrl() + "/" + collectionId;
    }

    private String documentUrl(String collectionId, String docId) {
        return baseUrl() + "/" + collectionId + "/" + docId;
    }

    private String subcollectionUrl(String parentCollectionId, String parentDocId, String subcollectionId) {
        return baseUrl() + "/" + parentCollectionId + "/" + parentDocId + "/" + subcollectionId;
    }

    private String subcollectionDocumentUrl(String parentCollectionId, String parentDocId, String subcollectionId, String docId) {
        return subcollectionUrl(parentCollectionId, parentDocId, subcollectionId) + "/" + docId;
    }

    private JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpResponse<String> resp = send("GET", url, null);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Firestore GET failed: " + resp.statusCode() + " " + resp.body());
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private HttpResponse<String> send(String method, String url, String jsonBody) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(40))
            .header("Authorization", "Bearer " + idToken)
            .header("Accept", "application/json");

        if (jsonBody != null) {
            b.header("Content-Type", "application/json; charset=utf-8");
        }

        switch (method) {
            case "GET" -> b.GET();
            case "POST" -> b.POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody, StandardCharsets.UTF_8));
            case "PATCH" -> b.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody, StandardCharsets.UTF_8));
            case "DELETE" -> b.DELETE();
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        }

        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private FsDocument parseDocument(JsonObject doc) {
        if (doc == null || !doc.has("name")) return null;
        String name = doc.get("name").getAsString();
        String id = name.substring(name.lastIndexOf('/') + 1);

        Map<String, Object> fields = new HashMap<>();
        if (doc.has("fields") && doc.get("fields").isJsonObject()) {
            JsonObject fs = doc.getAsJsonObject("fields");
            for (Map.Entry<String, JsonElement> e : fs.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                fields.put(e.getKey(), fromValue(e.getValue().getAsJsonObject()));
            }
        }

        return new FsDocument(id, fields);
    }

    private JsonObject toFields(Map<String, Object> fields) {
        JsonObject out = new JsonObject();
        if (fields == null) return out;
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            out.add(e.getKey(), toValue(e.getValue()));
        }
        return out;
    }

    private JsonObject toValue(Object value) {
        JsonObject v = new JsonObject();

        if (value == null) {
            v.addProperty("nullValue", "NULL_VALUE");
            return v;
        }
        if (value instanceof String s) {
            v.addProperty("stringValue", s);
            return v;
        }
        if (value instanceof Boolean b) {
            v.addProperty("booleanValue", b);
            return v;
        }
        if (value instanceof Integer i) {
            v.addProperty("integerValue", String.valueOf(i.longValue()));
            return v;
        }
        if (value instanceof Long l) {
            v.addProperty("integerValue", String.valueOf(l));
            return v;
        }
        if (value instanceof Double d) {
            v.addProperty("doubleValue", d);
            return v;
        }
        if (value instanceof Float f) {
            v.addProperty("doubleValue", f.doubleValue());
            return v;
        }
        if (value instanceof List list) {
            JsonArray values = new JsonArray();
            for (Object o : list) {
                values.add(toValue(o));
            }

            JsonObject arrayValue = new JsonObject();
            arrayValue.add("values", values);
            v.add("arrayValue", arrayValue);
            return v;
        }
        if (value instanceof Map map) {
            JsonObject fields = new JsonObject();
            for (Object k : map.keySet()) {
                if (k == null) continue;
                fields.add(String.valueOf(k), toValue(map.get(k)));
            }

            JsonObject mapValue = new JsonObject();
            mapValue.add("fields", fields);
            v.add("mapValue", mapValue);
            return v;
        }
        if (value instanceof Date d) {
            v.addProperty("timestampValue", DateTimeFormatter.ISO_INSTANT.format(d.toInstant()));
            return v;
        }
        if (value instanceof Instant i) {
            v.addProperty("timestampValue", DateTimeFormatter.ISO_INSTANT.format(i));
            return v;
        }
        if (value instanceof ReferenceValue r) {
            v.addProperty("referenceValue", r.value);
            return v;
        }

        // Fallback to string representation (keeps client robust)
        v.addProperty("stringValue", String.valueOf(value));
        return v;
    }

    private Object fromValue(JsonObject v) {
        if (v == null) return null;

        if (v.has("nullValue")) return null;
        if (v.has("stringValue")) return v.get("stringValue").getAsString();
        if (v.has("booleanValue")) return v.get("booleanValue").getAsBoolean();

        if (v.has("integerValue")) {
            try {
                return Long.parseLong(v.get("integerValue").getAsString());
            } catch (Exception ignored) {
                return null;
            }
        }

        if (v.has("doubleValue")) {
            try {
                return v.get("doubleValue").getAsDouble();
            } catch (Exception ignored) {
                return null;
            }
        }

        if (v.has("timestampValue")) {
            try {
                Instant i = Instant.parse(v.get("timestampValue").getAsString());
                return Date.from(i);
            } catch (Exception ignored) {
                return null;
            }
        }

        if (v.has("referenceValue")) {
            return v.get("referenceValue").getAsString();
        }

        if (v.has("arrayValue") && v.get("arrayValue").isJsonObject()) {
            JsonObject av = v.getAsJsonObject("arrayValue");
            if (!av.has("values") || !av.get("values").isJsonArray()) return List.of();
            List<Object> out = new ArrayList<>();
            for (JsonElement el : av.getAsJsonArray("values")) {
                if (!el.isJsonObject()) continue;
                out.add(fromValue(el.getAsJsonObject()));
            }
            return out;
        }

        if (v.has("mapValue") && v.get("mapValue").isJsonObject()) {
            JsonObject mv = v.getAsJsonObject("mapValue");
            if (!mv.has("fields") || !mv.get("fields").isJsonObject()) return Map.of();

            Map<String, Object> out = new LinkedHashMap<>();
            JsonObject fs = mv.getAsJsonObject("fields");
            for (Map.Entry<String, JsonElement> e : fs.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                out.put(e.getKey(), fromValue(e.getValue().getAsJsonObject()));
            }
            return out;
        }

        return null;
    }

    public static final class ReferenceValue {
        private final String value;

        public ReferenceValue(String value) {
            this.value = Objects.requireNonNull(value, "value");
        }
    }
}
