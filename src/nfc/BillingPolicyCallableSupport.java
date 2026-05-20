package nfc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@SuppressWarnings("unused")
final class BillingPolicyCallableSupport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BillingPolicyCallableSupport() {
    }

    static Map<?, ?> parseCallableResultMap(String rawBody) {
        Object parsed = parseJsonValue(rawBody);
        if (parsed instanceof Map<?, ?>) {
            return unwrapCallableEnvelope((Map<?, ?>) parsed);
        }
        if (parsed instanceof String) {
            String text = String.valueOf(parsed).trim();
            if (!text.isEmpty()) {
                return Collections.singletonMap("message", text);
            }
        }
        return Collections.emptyMap();
    }

    private static Object parseJsonValue(String rawBody) {
        String text = rawBody == null ? "" : rawBody.trim();
        if (text.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return GSON.fromJson(text, Object.class);
        } catch (RuntimeException ex) {
            return text;
        }
    }

    private static Map<?, ?> unwrapCallableEnvelope(Map<?, ?> top) {
        if (top == null || top.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<?, ?> nested = nestedMap(top.get("result"));
        if (!nested.isEmpty()) {
            return nested;
        }

        nested = nestedMap(top.get("data"));
        if (!nested.isEmpty()) {
            return nested;
        }

        nested = nestedMap(top.get("error"));
        if (!nested.isEmpty()) {
            return nested;
        }

        return top;
    }

    private static Map<?, ?> nestedMap(Object value) {
        if (value instanceof Map<?, ?>) {
            return (Map<?, ?>) value;
        }
        if (value instanceof String) {
            Object parsed = parseJsonValue(String.valueOf(value));
            if (parsed instanceof Map<?, ?>) {
                return unwrapCallableEnvelope((Map<?, ?>) parsed);
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return Collections.singletonMap("message", text);
            }
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
        if ("http-404".equalsIgnoreCase(reason)) {
            return "callable endpoint not found; deploy the latest backend functions";
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