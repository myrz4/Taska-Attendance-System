package nfc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("all")
final class BillingPolicyHealthSupport {
    private BillingPolicyHealthSupport() {
    }

    @SuppressWarnings("unused")
    static BillingPolicyRemoteSupport.RemoteHealthSnapshot unavailableSnapshot(String message) {
        return new BillingPolicyRemoteSupport.RemoteHealthSnapshot(false, false, message, "-", "-", "-", "unknown", "unknown");
    }

    @SuppressWarnings("unused")
    static BillingPolicyRemoteSupport.RemoteHealthSnapshot errorSnapshot(Exception ex) {
        return new BillingPolicyRemoteSupport.RemoteHealthSnapshot(
            false,
            false,
            "Live Backend: ERROR (" + ex.getMessage() + ")",
            "-",
            "-",
            "-",
            "unknown",
            "unknown"
        );
    }

    @SuppressWarnings("unused")
    static BillingPolicyRemoteSupport.RemoteHealthSnapshot buildSnapshot(boolean ok, String reason, Map<?, ?> result) {
        Map<?, ?> health = (result != null && result.get("health") instanceof Map)
            ? (Map<?, ?>) result.get("health")
            : Collections.emptyMap();

        Object validObj = health.get("isValid");
        boolean valid = validObj instanceof Boolean && (Boolean) validObj;

        String version = String.valueOf(health.get("version") == null ? "" : health.get("version"));
        String resolvedTransit = String.valueOf(health.get("resolvedDefaultTransitCode") == null ? "" : health.get("resolvedDefaultTransitCode"));
        String rowCount = String.valueOf(health.get("rowCount") == null ? "0" : health.get("rowCount"));
        String gatewaySummary = describePaymentGateway(health.get("paymentGateway"));

        List<?> missing = (health.get("missingRequiredCodes") instanceof List)
            ? (List<?>) health.get("missingRequiredCodes")
            : Collections.emptyList();
        String missingSummary = missing.isEmpty() ? "none" : joinList(missing);

        StringBuilder sb = new StringBuilder();
        sb.append("Live Backend: ").append(ok ? "OK" : "FAILED");
        if (reason != null && !reason.isBlank()) {
            sb.append(" (").append(reason).append(")");
        }
        sb.append("\n");
        sb.append("Version: ").append(version).append("\n");
        sb.append("Rows: ").append(rowCount).append("\n");
        sb.append("Resolved Default Transit: ").append(resolvedTransit).append("\n");
        sb.append("Payment Mode: ").append(gatewaySummary).append("\n");
        sb.append("Catalog Valid: ").append(valid ? "YES" : "NO");

        if (!missing.isEmpty()) {
            sb.append("\nMissing Required Codes: ").append(missingSummary);
        }

        return new BillingPolicyRemoteSupport.RemoteHealthSnapshot(
            ok,
            valid,
            sb.toString(),
            version,
            rowCount,
            resolvedTransit,
            missingSummary,
            gatewaySummary
        );
    }

    private static String describePaymentGateway(Object rawGateway) {
        if (!(rawGateway instanceof Map<?, ?>)) {
            return "unknown";
        }
        Map<?, ?> gateway = (Map<?, ?>) rawGateway;
        String provider = String.valueOf(gateway.get("provider") == null ? "dummy" : gateway.get("provider")).trim();
        String mode = String.valueOf(gateway.get("mode") == null ? "dummy" : gateway.get("mode")).trim();
        boolean allowRealProvider = Boolean.TRUE.equals(gateway.get("allowRealProvider"));
        if ("dummy".equalsIgnoreCase(provider)) {
            return "in-app payment flow";
        }
        if (!allowRealProvider) {
            return provider + " blocked, in-app payment flow active";
        }
        return provider + " / " + mode;
    }

    private static String joinList(List<?> items) {
        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < items.size(); index++) {
            if (index > 0) {
                sb.append(", ");
            }
            sb.append(String.valueOf(items.get(index)));
        }
        return sb.toString();
    }
}