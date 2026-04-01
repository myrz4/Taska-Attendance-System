package nfc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

final class BillingPolicyCallableSupport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BillingPolicyCallableSupport() {
    }

    static Map<?, ?> parseCallableResultMap(String rawBody) {
        Map<?, ?> top = GSON.fromJson(rawBody, Map.class);
        Object resultObj = (top instanceof Map) ? top.get("result") : null;
        if (resultObj instanceof Map) {
            return (Map<?, ?>) resultObj;
        }
        return Collections.emptyMap();
    }

    static String humanizeCallableReason(Object reasonObj, Map<?, ?> result) {
        String message = result == null ? "" : String.valueOf(result.get("message") == null ? "" : result.get("message")).trim();
        if (!message.isEmpty()) {
            return message;
        }

        String reason = String.valueOf(reasonObj == null ? "" : reasonObj).trim();
        if (reason.isEmpty() && result != null && result.get("reason") != null) {
            reason = String.valueOf(result.get("reason")).trim();
        }

        if ("admin-only".equalsIgnoreCase(reason)) {
            return "admin access required";
        }
        if ("invalid-catalog".equalsIgnoreCase(reason)) {
            Object healthObj = result == null ? null : result.get("health");
            if (healthObj instanceof Map) {
                Object missing = ((Map<?, ?>) healthObj).get("missingRequiredCodes");
                if (missing instanceof List && !((List<?>) missing).isEmpty()) {
                    return "invalid catalog: missing required codes";
                }
            }
            return "invalid catalog";
        }
        if ("missing-version".equalsIgnoreCase(reason)) {
            return "version is required";
        }
        if ("missing-catalogId".equalsIgnoreCase(reason)) {
            return "catalog selection is required";
        }
        if ("catalog-not-found".equalsIgnoreCase(reason)) {
            return "selected catalog no longer exists";
        }
        if ("http-401".equalsIgnoreCase(reason) || "unauthenticated".equalsIgnoreCase(reason)) {
            return "login expired, please sign in again";
        }
        if ("http-403".equalsIgnoreCase(reason) || "permission-denied".equalsIgnoreCase(reason)) {
            return "permission denied for this account";
        }
        return reason.isBlank() ? "unknown backend error" : reason;
    }

    static IllegalStateException callableFailure(String fallbackReason, Map<?, ?> result) {
        return new IllegalStateException(humanizeCallableReason(fallbackReason, result));
    }

    static String toPrettyJson(Object value) {
        return GSON.toJson(value);
    }
}