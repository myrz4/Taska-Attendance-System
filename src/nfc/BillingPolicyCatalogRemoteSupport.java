package nfc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@SuppressWarnings("all")
final class BillingPolicyCatalogRemoteSupport {
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private BillingPolicyCatalogRemoteSupport() {
    }

    @SuppressWarnings("unused")
    static List<CatalogDescriptor> loadCatalogs() throws IOException {
        String projectId = FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "projectId");
        String idToken = UserSession.getIdToken();
        FirebaseFunctionsClient.CallResult res = FirebaseFunctionsClient.callBillingAdminListCatalogs(projectId, idToken);
        Map<?, ?> result = BillingPolicyCallableSupport.parseCallableResultMap(res.rawBody);
        if (!(result.get("ok") instanceof Boolean) || !((Boolean) result.get("ok"))) {
            throw BillingPolicyCallableSupport.callableFailure(res.reason, result);
        }

        List<?> docs = (result.get("catalogs") instanceof List)
            ? (List<?>) result.get("catalogs")
            : Collections.emptyList();
        List<CatalogDescriptor> items = new ArrayList<>();
        for (Object raw : docs) {
            if (!(raw instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> document = (Map<String, Object>) raw;
            String id = String.valueOf(document.get("id") == null ? "" : document.get("id"));
            String version = String.valueOf(document.get("version") == null ? "" : document.get("version"));
            boolean active = Boolean.TRUE.equals(document.get("active"));
            items.add(new CatalogDescriptor(id, version, active, document));
        }
        return items;
    }

    @SuppressWarnings("unused")
    static String saveCatalog(String version, Map<String, Map<String, Long>> workingTable, String defaultTransitCode) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", version);
        payload.put("table", workingTable);
        payload.put("defaultTransitMonthlyCode", defaultTransitCode);

        String projectId = FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "projectId");
        String idToken = UserSession.getIdToken();
        FirebaseFunctionsClient.CallResult res = FirebaseFunctionsClient.callBillingAdminSaveCatalog(
            projectId,
            idToken,
            GSON_PRETTY.toJson(payload)
        );
        Map<?, ?> result = BillingPolicyCallableSupport.parseCallableResultMap(res.rawBody);
        if (!(result.get("ok") instanceof Boolean) || !((Boolean) result.get("ok"))) {
            throw BillingPolicyCallableSupport.callableFailure(res.reason, result);
        }
        return String.valueOf(result.get("catalogId") == null ? "" : result.get("catalogId"));
    }

    @SuppressWarnings("unused")
    static String activateCatalog(String catalogId, String defaultTransitCode) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("catalogId", catalogId);
        payload.put("defaultTransitMonthlyCode", defaultTransitCode);

        String projectId = FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "projectId");
        String idToken = UserSession.getIdToken();
        FirebaseFunctionsClient.CallResult res = FirebaseFunctionsClient.callBillingAdminActivateCatalog(
            projectId,
            idToken,
            GSON_PRETTY.toJson(payload)
        );
        Map<?, ?> result = BillingPolicyCallableSupport.parseCallableResultMap(res.rawBody);
        if (!(result.get("ok") instanceof Boolean) || !((Boolean) result.get("ok"))) {
            throw BillingPolicyCallableSupport.callableFailure(res.reason, result);
        }
        return String.valueOf(result.get("catalogId") == null ? "" : result.get("catalogId"));
    }

    @SuppressWarnings("unused")
    static ChildMetadataBackfillResult backfillChildMetadata(ChildMetadataBackfillRequest request) throws IOException {
        ChildMetadataBackfillRequest normalizedRequest = request == null
            ? new ChildMetadataBackfillRequest("", 200, false, true, "", Collections.emptyList())
            : request;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("limit", normalizedRequest.limit);
        if (!normalizedRequest.period.isBlank()) {
            payload.put("period", normalizedRequest.period);
        }
        if (normalizedRequest.force) {
            payload.put("force", true);
        }
        if (!normalizedRequest.startAfterId.isBlank()) {
            payload.put("startAfterId", normalizedRequest.startAfterId);
        }
        if (!normalizedRequest.childIds.isEmpty()) {
            payload.put("childIds", normalizedRequest.childIds);
        }

        String projectId = FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "projectId");
        String idToken = UserSession.getIdToken();
        nfc.FirebaseFunctionsClient.CallResult res = nfc.FirebaseFunctionsClient.callBillingAdminBackfillChildMetadata(
            projectId,
            idToken,
            GSON_PRETTY.toJson(payload)
        );
        Map<?, ?> result = BillingPolicyCallableSupport.parseCallableResultMap(res.rawBody);
        if (!(result.get("ok") instanceof Boolean) || !((Boolean) result.get("ok"))) {
            throw BillingPolicyCallableSupport.callableFailure(res.reason, result);
        }
        return new ChildMetadataBackfillResult(
            String.valueOf(result.get("period") == null ? "" : result.get("period")),
            String.valueOf(result.get("activeCatalogVersion") == null ? "" : result.get("activeCatalogVersion")),
            asInt(result.get("scannedCount")),
            asInt(result.get("patchedCount")),
            asInt(result.get("unchangedCount")),
            asInt(result.get("skippedMigratedCount")),
            asInt(result.get("failedCount")),
            Boolean.TRUE.equals(result.get("hasMore")),
            String.valueOf(result.get("nextStartAfterId") == null ? "" : result.get("nextStartAfterId"))
        );
    }

    private static int asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    @SuppressWarnings("unused")
    public static final class ChildMetadataBackfillRequest {
        final String period;
        final int limit;
        final boolean force;
        final boolean runAllPages;
        final String startAfterId;
        final List<String> childIds;

        ChildMetadataBackfillRequest(String period, int limit, boolean force, boolean runAllPages, String startAfterId, List<String> childIds) {
            this.period = period == null ? "" : period.trim();
            this.limit = Math.max(1, Math.min(200, limit <= 0 ? 200 : limit));
            this.force = force;
            this.startAfterId = startAfterId == null ? "" : startAfterId.trim();
            this.childIds = Collections.unmodifiableList(normalizeChildIds(childIds, this.limit));
            this.runAllPages = runAllPages && this.childIds.isEmpty();
        }

        ChildMetadataBackfillRequest withStartAfterId(String nextStartAfterId) {
            return new ChildMetadataBackfillRequest(period, limit, force, runAllPages, nextStartAfterId, childIds);
        }

        boolean hasChildIds() {
            return !childIds.isEmpty();
        }

        private static List<String> normalizeChildIds(List<String> rawChildIds, int limit) {
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            if (rawChildIds == null) {
                return new ArrayList<>();
            }
            for (String rawChildId : rawChildIds) {
                String childId = rawChildId == null ? "" : rawChildId.trim();
                if (childId.isEmpty()) {
                    continue;
                }
                ids.add(childId);
                if (ids.size() >= limit) {
                    break;
                }
            }
            return new ArrayList<>(ids);
        }
    }

    @SuppressWarnings("unused")
    static final class CatalogDescriptor {
        @SuppressWarnings("unused")
        final String id;
        @SuppressWarnings("unused")
        final String version;
        @SuppressWarnings("unused")
        final boolean active;
        @SuppressWarnings("unused")
        final Map<String, Object> doc;

        CatalogDescriptor(String id, String version, boolean active, Map<String, Object> doc) {
            this.id = id;
            this.version = version;
            this.active = active;
            this.doc = doc;
        }
    }

    @SuppressWarnings("unused")
    static final class ChildMetadataBackfillResult {
        final String period;
        final String activeCatalogVersion;
        final int scannedCount;
        final int patchedCount;
        final int unchangedCount;
        final int skippedMigratedCount;
        final int failedCount;
        final boolean hasMore;
        final String nextStartAfterId;

        ChildMetadataBackfillResult(
            String period,
            String activeCatalogVersion,
            int scannedCount,
            int patchedCount,
            int unchangedCount,
            int skippedMigratedCount,
            int failedCount,
            boolean hasMore,
            String nextStartAfterId
        ) {
            this.period = period == null ? "" : period;
            this.activeCatalogVersion = activeCatalogVersion == null ? "" : activeCatalogVersion;
            this.scannedCount = scannedCount;
            this.patchedCount = patchedCount;
            this.unchangedCount = unchangedCount;
            this.skippedMigratedCount = skippedMigratedCount;
            this.failedCount = failedCount;
            this.hasMore = hasMore;
            this.nextStartAfterId = nextStartAfterId == null ? "" : nextStartAfterId;
        }
    }
}