package nfc;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class BillingPolicyRemoteSupport {
    private BillingPolicyRemoteSupport() {}

    static RemoteHealthSnapshot fetchRemoteHealthSummary() {
        String projectId = FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "projectId");
        String idToken = UserSession.getIdToken();
        if (projectId == null || projectId.isBlank()) {
            return BillingPolicyHealthSupport.unavailableSnapshot(
                "Live Backend: unavailable (missing projectId in firebase.properties)."
            );
        }
        if (idToken == null || idToken.isBlank()) {
            return BillingPolicyHealthSupport.unavailableSnapshot(
                "Live Backend: unavailable (missing login token)."
            );
        }

        try {
            FirebaseFunctionsClient.CallResult res = FirebaseFunctionsClient.callBillingGetHealth(projectId.trim(), idToken);
            Map<?, ?> result = BillingPolicyCallableSupport.parseCallableResultMap(res.rawBody);
            String reason = BillingPolicyCallableSupport.humanizeCallableReason(res.reason, result);
            return BillingPolicyHealthSupport.buildSnapshot(res.ok, reason, result);
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            return BillingPolicyHealthSupport.errorSnapshot(ex);
        }
    }

    static AuditLogSnapshot fetchAuditLogSnapshot(String actionFilter) {
        String projectId = FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "projectId");
        String idToken = UserSession.getIdToken();
        if (projectId == null || projectId.isBlank()) {
            return new AuditLogSnapshot(actionFilter, Collections.emptyList(),
                "Audit log unavailable: missing projectId in firebase.properties.");
        }
        if (idToken == null || idToken.isBlank()) {
            return new AuditLogSnapshot(actionFilter, Collections.emptyList(),
                "Audit log unavailable: missing login token.");
        }

        try {
            FirebaseFunctionsClient.CallResult res = FirebaseFunctionsClient.callBillingAdminListAudit(
                projectId.trim(),
                idToken,
                BillingPolicyCallableSupport.toPrettyJson(Collections.singletonMap("limit", 100))
            );
            Map<?, ?> result = BillingPolicyCallableSupport.parseCallableResultMap(res.rawBody);
            if (!(result.get("ok") instanceof Boolean) || !((Boolean) result.get("ok"))) {
                throw BillingPolicyCallableSupport.callableFailure(res.reason, result);
            }
            List<?> rawEntries = (result.get("entries") instanceof List)
                ? (List<?>) result.get("entries")
                : Collections.emptyList();
            List<Map<String, Object>> filteredEntries = BillingPolicyAuditSupport.filterAuditEntries(rawEntries, actionFilter);
            return new AuditLogSnapshot(actionFilter, filteredEntries, BillingPolicyAuditSupport.formatAuditEntries(filteredEntries, actionFilter));
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            return new AuditLogSnapshot(actionFilter, Collections.emptyList(),
                "Audit log load failed: " + ex.getMessage());
        }
    }

    static String normalizeAuditActionFilter(String actionFilter) {
        return BillingPolicyAuditSupport.normalizeAuditActionFilter(actionFilter);
    }

    static String auditExportFileName(String actionFilter, String extension) {
        return BillingPolicyAuditSupport.auditExportFileName(actionFilter, extension);
    }

    static Map<String, Object> auditJsonPayload(AuditLogSnapshot snapshot) {
        return BillingPolicyAuditSupport.auditJsonPayload(snapshot);
    }

    static final class RemoteHealthSnapshot {
        final boolean ok;
        final boolean valid;
        final String summary;
        final String version;
        final String rowCount;
        final String resolvedTransit;
        final String missingSummary;
        final String gatewaySummary;

        RemoteHealthSnapshot(boolean ok, boolean valid, String summary, String version, String rowCount, String resolvedTransit, String missingSummary, String gatewaySummary) {
            this.ok = ok;
            this.valid = valid;
            this.summary = summary;
            this.version = version;
            this.rowCount = rowCount;
            this.resolvedTransit = resolvedTransit;
            this.missingSummary = missingSummary;
            this.gatewaySummary = gatewaySummary;
        }
    }

    static final class AuditLogSnapshot {
        final String actionFilter;
        final List<Map<String, Object>> entries;
        final String formattedText;

        AuditLogSnapshot(String actionFilter, List<Map<String, Object>> entries, String formattedText) {
            this.actionFilter = actionFilter;
            this.entries = entries;
            this.formattedText = formattedText;
        }
    }
}