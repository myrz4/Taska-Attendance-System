package nfc;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
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
                    FsDocument d = FirestoreRestDocumentSupport.parseDocument(el.getAsJsonObject());
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
        return FirestoreRestDocumentSupport.parseDocument(obj);
    }

    /**
     * Creates a document with a server-assigned ID (auto ID).
     * Returns the created document (including its generated id).
     */
    public FsDocument addDocumentAutoId(String collectionId, Map<String, Object> fields)
        throws IOException, InterruptedException {

        String url = collectionUrl(collectionId);
        JsonObject doc = FirestoreRestMutationSupport.createDocumentBody(fields);

        HttpResponse<String> resp = send("POST", url, gson.toJson(doc));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Firestore POST failed: " + resp.statusCode() + " " + resp.body());
        }

        return FirestoreRestMutationSupport.parseDocumentResponse(resp.body());
    }

    public FsDocument addSubcollectionDocumentAutoId(
        String parentCollectionId,
        String parentDocId,
        String subcollectionId,
        Map<String, Object> fields)
        throws IOException, InterruptedException {

        String url = subcollectionUrl(parentCollectionId, parentDocId, subcollectionId);
        JsonObject doc = FirestoreRestMutationSupport.createDocumentBody(fields);

        HttpResponse<String> resp = send("POST", url, gson.toJson(doc));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Firestore POST failed: " + resp.statusCode() + " " + resp.body());
        }

        return FirestoreRestMutationSupport.parseDocumentResponse(resp.body());
    }

    /**
     * Creates a document with an explicit document ID.
     * This uses Firestore REST create semantics and will fail if the document already exists.
     */
    public FsDocument createDocumentWithId(String collectionId, String docId, Map<String, Object> fields)
        throws IOException, InterruptedException {

        String url = FirestoreRestMutationSupport.createDocumentWithIdUrl(collectionUrl(collectionId), docId);

        JsonObject doc = FirestoreRestMutationSupport.createDocumentBody(fields);

        HttpResponse<String> resp = send("POST", url, gson.toJson(doc));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Firestore create failed: " + resp.statusCode() + " " + resp.body());
        }

        return FirestoreRestMutationSupport.parseDocumentResponse(resp.body());
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
            FsDocument doc = FirestoreRestDocumentSupport.parseDocument(row.getAsJsonObject("document"));
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
        filter.add("value", FirestoreRestValueSupport.toValue(value));

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

        String url = FirestoreRestMutationSupport.appendUpdateMask(documentUrl(collectionId, docId), fields);

        JsonObject doc = FirestoreRestMutationSupport.createDocumentBody(fields);

        HttpResponse<String> resp = send("PATCH", url, gson.toJson(doc));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Firestore PATCH failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    public void patchDocumentMergeDeletingFields(
        String collectionId,
        String docId,
        Map<String, Object> fields,
        List<String> deleteFieldPaths)
        throws IOException, InterruptedException {

        List<String> deletePaths = deleteFieldPaths == null ? Collections.emptyList() : deleteFieldPaths;
        if (deletePaths.isEmpty()) {
            patchDocumentMerge(collectionId, docId, fields);
            return;
        }

        LinkedHashSet<String> updateMaskPaths = new LinkedHashSet<>();
        if (fields != null) {
            updateMaskPaths.addAll(fields.keySet());
        }
        for (String deleteFieldPath : deletePaths) {
            if (deleteFieldPath != null && !deleteFieldPath.isBlank()) {
                updateMaskPaths.add(deleteFieldPath);
            }
        }

        String url = FirestoreRestMutationSupport.appendUpdateMask(documentUrl(collectionId, docId), updateMaskPaths);
        JsonObject doc = FirestoreRestMutationSupport.createDocumentBody(fields == null ? Collections.emptyMap() : fields);

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

        String url = FirestoreRestMutationSupport.appendUpdateMask(
            subcollectionDocumentUrl(parentCollectionId, parentDocId, subcollectionId, docId),
            fields
        );

        JsonObject doc = FirestoreRestMutationSupport.createDocumentBody(fields);

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
            case "GET":
                b.GET();
                break;
            case "POST":
                b.POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody, StandardCharsets.UTF_8));
                break;
            case "PATCH":
                b.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody, StandardCharsets.UTF_8));
                break;
            case "DELETE":
                b.DELETE();
                break;
            default:
                throw new IllegalArgumentException("Unsupported method: " + method);
        }

        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unused")
    public static final class ReferenceValue {
        private final String value;

        public ReferenceValue(String value) {
            this.value = Objects.requireNonNull(value, "value");
        }

        String value() {
            return value;
        }
    }
}
