package nfc;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@SuppressWarnings("unused")
final class BillingLedgerInteractionSupport {
    private BillingLedgerInteractionSupport() {}

    @SuppressWarnings("unused")
    static String buildParentFocusLabel(BillingLedgerView.LedgerRow row, Function<String, String> nullSafe) {
        if (row == null) {
            return "Parent Focus: none";
        }
        return "Parent Focus: " + nullSafe.apply(row.getParentName()) + " (" + nullSafe.apply(row.getParentId()) + ")";
    }

    @SuppressWarnings("unused")
    static BillingLedgerView.LedgerRow latestUnpaidInvoiceForParent(
        BillingLedgerView.LedgerRow selectedRow,
        List<BillingLedgerView.LedgerRow> allRows
    ) {
        if (selectedRow == null) {
            return null;
        }

        BillingLedgerView.LedgerRow target = null;
        for (BillingLedgerView.LedgerRow candidate : allRows) {
            if (!Objects.equals(selectedRow.getParentId(), candidate.getParentId())) {
                continue;
            }
            if (candidate.isPaid()) {
                continue;
            }
            if (target == null || compareInvoicePriority(candidate, target) < 0) {
                target = candidate;
            }
        }
        return target;
    }

    private static int compareInvoicePriority(BillingLedgerView.LedgerRow left, BillingLedgerView.LedgerRow right) {
        Comparator<BillingLedgerView.LedgerRow> comparator = Comparator
            .comparing(BillingLedgerView.LedgerRow::getSortDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(BillingLedgerView.LedgerRow::getInvoiceId, Comparator.nullsLast(String::compareToIgnoreCase));
        return comparator.compare(left, right);
    }
}