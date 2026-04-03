package nfc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BillingLedgerDataSupport {
    private BillingLedgerDataSupport() {}

    public static List<BillingLedgerView.LedgerRow> loadRows() {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            List<FsDocument> parents = client.listDocuments("parents");
            List<BillingLedgerView.LedgerRow> loaded = new ArrayList<>();

            for (FsDocument parent : parents) {
                String parentId = parent.getId();
                String parentName = firstNonBlank(
                    parent.getString("parentName"),
                    parent.getString("name"),
                    parent.getString("fullName"),
                    parent.getString("username"),
                    parentId
                );

                List<FsDocument> invoices = client.listSubcollectionDocuments("parents", parentId, "invoices");
                if (invoices.isEmpty()) {
                    continue;
                }

                List<FsDocument> payments = client.listSubcollectionDocuments("parents", parentId, "payments");
                Map<String, List<FsDocument>> paymentsByInvoiceId = new HashMap<>();
                for (FsDocument payment : payments) {
                    String invoiceId = firstNonBlank(payment.getString("invoiceId"), parseDocId(payment.getString("invoiceRef")));
                    if (invoiceId == null || invoiceId.isBlank()) {
                        continue;
                    }
                    paymentsByInvoiceId.computeIfAbsent(invoiceId, ignored -> new ArrayList<>()).add(payment);
                }

                for (FsDocument invoice : invoices) {
                    FsDocument payment = latestPayment(paymentsByInvoiceId.get(invoice.getId()));
                    loaded.add(BillingLedgerView.LedgerRow.from(parentId, parentName, invoice, payment));
                }
            }

            loaded.sort(Comparator
                .comparing(BillingLedgerView.LedgerRow::getSortDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(BillingLedgerView.LedgerRow::getInvoiceId, Comparator.nullsLast(String::compareToIgnoreCase)));
            return loaded;
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static FsDocument latestPayment(List<FsDocument> payments) {
        if (payments == null || payments.isEmpty()) {
            return null;
        }

        return payments.stream()
            .filter(Objects::nonNull)
            .max(Comparator.comparing(BillingLedgerDataSupport::paymentSortDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .orElse(null);
    }

    private static Date paymentSortDate(FsDocument payment) {
        return firstDate(
            payment.getDate("paidAt"),
            payment.getDate("completedAt"),
            payment.getDate("updatedAt"),
            payment.getDate("createdAt")
        );
    }

    private static String parseDocId(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        int idx = reference.lastIndexOf('/');
        return idx >= 0 && idx + 1 < reference.length() ? reference.substring(idx + 1) : reference;
    }

    private static Date firstDate(Date... values) {
        for (Date value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}