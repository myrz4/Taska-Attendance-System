package nfc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BillingPolicyAuditSupport {
    private BillingPolicyAuditSupport() {
    }

    static String normalizeAuditActionFilter(String actionFilter) {
        String normalized = String.valueOf(actionFilter == null ? "all" : actionFilter).trim().toLowerCase();
        return normalized.isEmpty() ? "all" : normalized;
    }

    static String auditExportFileName(String actionFilter, String extension) {
        return "billing-audit-" + normalizeAuditActionFilter(actionFilter)
            + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
            + extension;
    }

    static Map<String, Object> auditJsonPayload(BillingPolicyRemoteSupport.AuditLogSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        payload.put("actionFilter", normalizeAuditActionFilter(snapshot.actionFilter));
        payload.put("entryCount", snapshot.entries.size());
        payload.put("entries", snapshot.entries);
        return payload;
    }

    static List<Map<String, Object>> filterAuditEntries(List<?> entries, String actionFilter) {
        List<Map<String, Object>> filtered = new ArrayList<>();
        String normalizedFilter = normalizeAuditActionFilter(actionFilter);
        for (Object raw : entries) {
            if (!(raw instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) raw;
            String action = String.valueOf(entry.get("action") == null ? "" : entry.get("action")).trim();
            if (!"all".equals(normalizedFilter) && !normalizedFilter.equalsIgnoreCase(action)) {
                continue;
            }
            filtered.add(entry);
        }
        return filtered;
    }

    static String formatAuditEntries(List<?> entries, String actionFilter) {
        if (entries == null || entries.isEmpty()) {
            if ("all".equals(normalizeAuditActionFilter(actionFilter))) {
                return "No billing audit entries found.";
            }
            return "No billing audit entries found for action filter: " + normalizeAuditActionFilter(actionFilter);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Recent Billing Audit Entries\n");
        sb.append("===========================\n\n");
        sb.append("Filter: ").append(normalizeAuditActionFilter(actionFilter)).append("\n");
        sb.append("Entries: ").append(entries.size()).append("\n\n");
        for (Object raw : entries) {
            if (!(raw instanceof Map)) {
                continue;
            }
            Map<?, ?> entry = (Map<?, ?>) raw;
            String createdAt = String.valueOf(entry.get("createdAt") == null ? "" : entry.get("createdAt"));
            String action = String.valueOf(entry.get("action") == null ? "" : entry.get("action"));
            String version = String.valueOf(entry.get("version") == null ? "" : entry.get("version"));
            String catalogId = String.valueOf(entry.get("catalogId") == null ? "" : entry.get("catalogId"));
            String actorUid = String.valueOf(entry.get("actorUid") == null ? "" : entry.get("actorUid"));
            String actorRole = String.valueOf(entry.get("actorRole") == null ? "" : entry.get("actorRole"));
            String actorEmail = String.valueOf(entry.get("actorEmail") == null ? "" : entry.get("actorEmail"));
            String actorPhone = String.valueOf(entry.get("actorPhoneE164") == null ? "" : entry.get("actorPhoneE164"));
            String detailsSummary = formatAuditDetails(entry.get("details"));

            sb.append("Time: ").append(createdAt.isBlank() ? "-" : createdAt).append("\n");
            sb.append("Action: ").append(action.isBlank() ? "-" : action).append("\n");
            sb.append("Version: ").append(version.isBlank() ? "-" : version).append("\n");
            sb.append("Catalog ID: ").append(catalogId.isBlank() ? "-" : catalogId).append("\n");
            sb.append("Actor UID: ").append(actorUid.isBlank() ? "-" : actorUid).append("\n");
            sb.append("Actor Role: ").append(actorRole.isBlank() ? "-" : actorRole).append("\n");
            sb.append("Actor Email: ").append(actorEmail.isBlank() ? "-" : actorEmail).append("\n");
            sb.append("Actor Phone: ").append(actorPhone.isBlank() ? "-" : actorPhone).append("\n");
            sb.append("Details: ").append(detailsSummary).append("\n");
            sb.append("----------------------------------------\n");
        }
        return sb.toString();
    }

    private static String formatAuditDetails(Object detailsObj) {
        if (!(detailsObj instanceof Map)) {
            return "-";
        }
        Map<?, ?> details = (Map<?, ?>) detailsObj;
        List<String> parts = new ArrayList<>();
        Object rowCount = details.get("rowCount");
        if (rowCount != null) {
            parts.add("rows=" + rowCount);
        }
        Object defaultTransit = details.get("defaultTransitMonthlyCode");
        if (defaultTransit != null && !String.valueOf(defaultTransit).isBlank()) {
            parts.add("defaultTransit=" + String.valueOf(defaultTransit));
        }
        Object missing = details.get("missingRequiredCodes");
        if (missing instanceof List && !((List<?>) missing).isEmpty()) {
            parts.add("missing=" + joinList((List<?>) missing));
        }
        return parts.isEmpty() ? BillingPolicyCallableSupport.toPrettyJson(details) : String.join(", ", parts);
    }

    private static String joinList(List<?> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.valueOf(items.get(i)));
        }
        return sb.toString();
    }
}