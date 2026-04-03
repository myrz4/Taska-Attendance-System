package nfc;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unused")
final class BillingLedgerPaymentSupport {
    private BillingLedgerPaymentSupport() {}

    @SuppressWarnings("unused")
    static void recordCashPayment(BillingLedgerView.LedgerRow row) {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            Date now = new Date();
            String receiptNo = buildCashReceiptNo(row, now);

            Map<String, Object> payment = new LinkedHashMap<>();
            payment.put("provider", "cash");
            payment.put("status", "succeeded");
            payment.put("invoiceId", row.getInvoiceId());
            payment.put("currency", firstNonBlank(row.getInvoiceDocument().getString("currency"), "MYR"));
            payment.put("amountSen", row.getTotalSen());
            payment.put("method", "Cash");
            payment.put("bank", null);
            payment.put("receiptNo", receiptNo);
            payment.put("paidAt", now);
            payment.put("createdAt", now);
            payment.put("createdBy", Map.of(
                "username", firstNonBlank(UserSession.getUsername(), "unknown"),
                "name", firstNonBlank(UserSession.getName(), "unknown"),
                "kind", "admin-cash"
            ));

            FsDocument paymentDoc = client.addSubcollectionDocumentAutoId("parents", row.getParentId(), "payments", payment);

            Map<String, Object> invoicePatch = new LinkedHashMap<>();
            invoicePatch.put("status", "paid");
            invoicePatch.put("paidAt", now);
            invoicePatch.put("paidMethod", "Cash");
            invoicePatch.put("paidBank", "");
            invoicePatch.put("paidAmountSen", row.getTotalSen());
            invoicePatch.put("paidReceiptNo", receiptNo);
            invoicePatch.put("paidPaymentId", paymentDoc == null ? null : paymentDoc.getId());
            invoicePatch.put("paidProvider", "cash");
            invoicePatch.put("updatedAt", now);

            client.patchSubcollectionDocumentMerge("parents", row.getParentId(), "invoices", row.getInvoiceId(), invoicePatch);
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String buildCashReceiptNo(BillingLedgerView.LedgerRow row, Date timestamp) {
        LocalDateTime issuedAt = timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        String invoiceToken = slug(row.getInvoiceId()).replace("-", "");
        if (invoiceToken.length() > 6) {
            invoiceToken = invoiceToken.substring(0, 6);
        }
        return "CASH-" + issuedAt.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + invoiceToken.toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String slug(String value) {
        if (value == null || value.isBlank()) {
            return "record";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}