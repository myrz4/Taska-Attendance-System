package nfc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

final class BillingPolicyCatalogRemoteSupport {
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private BillingPolicyCatalogRemoteSupport() {
    }

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

    static final class CatalogDescriptor {
        final String id;
        final String version;
        final boolean active;
        final Map<String, Object> doc;

        CatalogDescriptor(String id, String version, boolean active, Map<String, Object> doc) {
            this.id = id;
            this.version = version;
            this.active = active;
            this.doc = doc;
        }
    }
}