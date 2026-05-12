package nfc;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@SuppressWarnings("unused")
final class BillingLedgerRemoteSupport {
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private BillingLedgerRemoteSupport() {}

    static Map<?, ?> generateInvoicesForPeriod(String period, List<String> parentIds) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("period", period);
            if (parentIds != null && !parentIds.isEmpty()) {
                payload.put("parentIds", parentIds);
            }

            String projectId = FirestoreRest.projectId();
            String idToken = UserSession.getIdToken();
            FirebaseFunctionsClient.CallResult res = FirebaseFunctionsClient.callBillingAdminGenerateInvoicesForPeriod(
                projectId,
                idToken,
                GSON_PRETTY.toJson(payload)
            );
            Map<?, ?> result = BillingPolicyCallableSupport.parseCallableResultMap(res.rawBody);
            Object ok = result.get("ok");
            if (!(ok instanceof Boolean) || !((Boolean) ok)) {
                throw BillingPolicyCallableSupport.callableFailure(res.reason, result);
            }
            return result;
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static void assertInvoiceBatchSucceeded(Map<?, ?> result) {
        if (result == null) {
            throw new IllegalStateException("Invoice generation returned no result.");
        }

        int createdCount = intValue(result.get("createdCount"));
        int existingCount = intValue(result.get("existingCount"));
        int skippedNoChildrenCount = intValue(result.get("skippedNoChildrenCount"));
        int skippedNoItemsCount = intValue(result.get("skippedNoItemsCount"));
        int errorCount = intValue(result.get("errorCount"));

        if (createdCount > 0 || existingCount > 0) {
            return;
        }

        if (errorCount <= 0 && skippedNoChildrenCount <= 0 && skippedNoItemsCount <= 0) {
            return;
        }

        throw new IllegalStateException(firstBatchFailureMessage(result));
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static String firstBatchFailureMessage(Map<?, ?> result) {
        List<?> results = result.get("results") instanceof List<?> ? (List<?>) result.get("results") : Collections.emptyList();
        for (Object rawEntry : results) {
            if (!(rawEntry instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> entry = (Map<?, ?>) rawEntry;
            String message = stringValue(entry.get("message"));
            if (!message.isBlank()) {
                return message;
            }
            String reason = stringValue(entry.get("reason"));
            if (!reason.isBlank()) {
                return reason;
            }
        }

        if (intValue(result.get("skippedNoChildrenCount")) > 0) {
            return "No linked children were found for the selected parent records.";
        }
        if (intValue(result.get("skippedNoItemsCount")) > 0) {
            return "No billable items were found for the selected billing period.";
        }
        return "Invoice generation did not create or refresh any invoices.";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}