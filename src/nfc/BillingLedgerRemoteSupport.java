package nfc;

import java.io.IOException;
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
}