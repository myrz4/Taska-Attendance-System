package nfc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BillingLedgerDataSupport {
    private static final String TASKA_ZURAH_POLICY_VERSION = "TASKA_ZURAH_2026";
    private static final String TASKA_ZURAH_BILLING_MODEL = "TASKA_ZURAH_AGE_BASED";

    private BillingLedgerDataSupport() {}

    public static List<BillingLedgerView.LedgerRow> loadRows() {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            List<FsDocument> parents = client.listDocuments("parents");
            List<ParentInvoiceSnapshot> parentSnapshots = new ArrayList<>();
            Map<String, List<String>> refreshParentIdsByPeriod = new LinkedHashMap<>();
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

                parentSnapshots.add(new ParentInvoiceSnapshot(parentId, parentName, invoices));
                collectLegacyRefreshRequests(parentId, invoices, refreshParentIdsByPeriod);
            }

            if (!refreshParentIdsByPeriod.isEmpty()) {
                refreshLegacyInvoices(refreshParentIdsByPeriod);
            }

            LinkedHashSet<String> refreshedParentIds = collectRefreshedParentIds(refreshParentIdsByPeriod);
            for (ParentInvoiceSnapshot snapshot : parentSnapshots) {
                List<FsDocument> invoices = refreshedParentIds.contains(snapshot.parentId)
                    ? client.listSubcollectionDocuments("parents", snapshot.parentId, "invoices")
                    : snapshot.invoices;
                if (invoices.isEmpty()) {
                    continue;
                }

                List<FsDocument> payments = client.listSubcollectionDocuments("parents", snapshot.parentId, "payments");
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
                    loaded.add(BillingLedgerView.LedgerRow.from(snapshot.parentId, snapshot.parentName, invoice, payment));
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

    private static void collectLegacyRefreshRequests(String parentId, List<FsDocument> invoices, Map<String, List<String>> refreshParentIdsByPeriod) {
        for (FsDocument invoice : invoices) {
            if (!invoiceNeedsTaskaZurahRefresh(invoice)) {
                continue;
            }
            String period = firstNonBlank(invoice.getString("period"));
            if (period == null || period.isBlank()) {
                continue;
            }
            addUniqueParentId(refreshParentIdsByPeriod, period, parentId);
        }
    }

    private static void refreshLegacyInvoices(Map<String, List<String>> refreshParentIdsByPeriod) {
        for (Map.Entry<String, List<String>> entry : refreshParentIdsByPeriod.entrySet()) {
            String period = entry.getKey();
            List<String> parentIds = entry.getValue();
            if (period == null || period.isBlank() || parentIds == null || parentIds.isEmpty()) {
                continue;
            }

            try {
                BillingLedgerRemoteSupport.generateInvoicesForPeriod(period, parentIds);
            } catch (RuntimeException ex) {
                System.err.println(
                    "BillingLedgerDataSupport: failed to refresh legacy invoice details for period "
                        + period
                        + " - "
                        + BillingLedgerMessageSupport.rootMessage(ex)
                );
            }
        }
    }

    private static LinkedHashSet<String> collectRefreshedParentIds(Map<String, List<String>> refreshParentIdsByPeriod) {
        LinkedHashSet<String> parentIds = new LinkedHashSet<>();
        for (List<String> ids : refreshParentIdsByPeriod.values()) {
            if (ids == null) {
                continue;
            }
            for (String parentId : ids) {
                if (parentId != null && !parentId.isBlank()) {
                    parentIds.add(parentId);
                }
            }
        }
        return parentIds;
    }

    private static void addUniqueParentId(Map<String, List<String>> refreshParentIdsByPeriod, String period, String parentId) {
        List<String> parentIds = refreshParentIdsByPeriod.computeIfAbsent(period, ignored -> new ArrayList<>());
        if (!parentIds.contains(parentId)) {
            parentIds.add(parentId);
        }
    }

    private static boolean invoiceNeedsTaskaZurahRefresh(FsDocument invoice) {
        if (invoice == null) {
            return false;
        }

        String period = firstNonBlank(invoice.getString("period"));
        if (period == null || period.isBlank()) {
            return false;
        }

        String status = firstNonBlank(invoice.getString("status"), "").toLowerCase();
        if ("paid".equals(status) || "void".equals(status)) {
            return false;
        }

        return !invoiceUsesTaskaZurahBilling(invoice);
    }

    private static boolean invoiceUsesTaskaZurahBilling(FsDocument invoice) {
        String pricingVersion = firstNonBlank(invoice.getString("pricingVersion"), "").toLowerCase();
        if (pricingVersion.startsWith("taska_zurah")) {
            return true;
        }

        Object billingMeta = invoice.get("billingMeta");
        if (billingMeta instanceof Map<?, ?>) {
            Map<?, ?> billingMetaMap = (Map<?, ?>) billingMeta;
            if (TASKA_ZURAH_POLICY_VERSION.equals(stringValue(billingMetaMap.get("feePolicyVersion")))
                || TASKA_ZURAH_BILLING_MODEL.equals(stringValue(billingMetaMap.get("activeBillingModel")))) {
                return true;
            }

            Object rawChildren = billingMetaMap.get("children");
            if (rawChildren instanceof List<?>) {
                for (Object rawChildSummary : (List<?>) rawChildren) {
                    if (!(rawChildSummary instanceof Map<?, ?>)) {
                        continue;
                    }
                    Object rawChildBillingMeta = ((Map<?, ?>) rawChildSummary).get("billingMeta");
                    if (!(rawChildBillingMeta instanceof Map<?, ?>)) {
                        continue;
                    }
                    Map<?, ?> childBillingMeta = (Map<?, ?>) rawChildBillingMeta;
                    if (TASKA_ZURAH_POLICY_VERSION.equals(stringValue(childBillingMeta.get("feePolicyVersion")))
                        && TASKA_ZURAH_BILLING_MODEL.equals(stringValue(childBillingMeta.get("activeBillingModel")))) {
                        return true;
                    }
                }
            }
        }

        Object rawItems = invoice.get("items");
        if (!(rawItems instanceof List<?>)) {
            return false;
        }

        for (Object rawItem : (List<?>) rawItems) {
            if (!(rawItem instanceof Map<?, ?>)) {
                continue;
            }
            String code = stringValue(((Map<?, ?>) rawItem).get("code"));
            if ("monthly_fee".equals(code)
                || "registration_fee".equals(code)
                || "insurance_takaful".equals(code)
                || "yearly_maintenance_fee".equals(code)
                || "overtime_charge".equals(code)) {
                return true;
            }
        }
        return false;
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

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static final class ParentInvoiceSnapshot {
        private final String parentId;
        private final String parentName;
        private final List<FsDocument> invoices;

        private ParentInvoiceSnapshot(String parentId, String parentName, List<FsDocument> invoices) {
            this.parentId = parentId;
            this.parentName = parentName;
            this.invoices = invoices;
        }
    }
}