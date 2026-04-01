package nfc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

@SuppressWarnings("unused")
final class BillingLedgerCommandSupport {
    private BillingLedgerCommandSupport() {}

    static void applyQuickFilter(
        ComboBox<String> statusFilter,
        ComboBox<String> dateScopeFilter,
        ComboBox<String> riskFilter,
        ComboBox<String> reviewFilter,
        TextField searchField,
        Runnable applyFilters,
        String status,
        String dateScope,
        String searchText,
        String familyRisk,
        String reviewType
    ) {
        statusFilter.setValue(status);
        dateScopeFilter.setValue(dateScope);
        riskFilter.setValue(familyRisk);
        reviewFilter.setValue(reviewType);
        if (searchText != null) {
            searchField.setText(searchText);
        }
        applyFilters.run();
    }

    static FocusResult focusSelectedParent(
        BillingLedgerView.LedgerRow row,
        boolean unpaidOnly,
        Function<BillingLedgerView.LedgerRow, String> focusLabelBuilder
    ) {
        if (row == null) {
            return null;
        }
        return new FocusResult(row.getParentId(), focusLabelBuilder.apply(row), unpaidOnly ? "Unpaid" : null, false);
    }

    static FocusResult clearParentFocus(String emptyFocusLabel) {
        return new FocusResult(null, emptyFocusLabel, null, true);
    }

    static LatestUnpaidResult latestUnpaidSelection(
        BillingLedgerView.LedgerRow row,
        List<BillingLedgerView.LedgerRow> allRows,
        Function<BillingLedgerView.LedgerRow, String> focusLabelBuilder
    ) {
        if (row == null) {
            return null;
        }

        BillingLedgerView.LedgerRow target = BillingLedgerInteractionSupport.latestUnpaidInvoiceForParent(row, allRows);
        if (target == null) {
            return new LatestUnpaidResult(null, null, null, false);
        }
        return new LatestUnpaidResult(target, target.getParentId(), focusLabelBuilder.apply(target), true);
    }

    static PreparedInvoiceIssue prepareInvoiceIssue(
        String periodText,
        List<BillingLedgerView.ParentSummaryRow> parentSummaryRows,
        boolean visibleOnly
    ) {
        String period = periodText == null ? "" : periodText.trim();
        if (!period.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("invalid-period");
        }

        List<String> parentIds = new ArrayList<>();
        if (visibleOnly) {
            for (BillingLedgerView.ParentSummaryRow row : parentSummaryRows) {
                String parentId = row == null ? null : row.getParentId();
                if (parentId != null && !parentId.isBlank() && !parentIds.contains(parentId)) {
                    parentIds.add(parentId);
                }
            }
            if (parentIds.isEmpty()) {
                throw new IllegalStateException("no-visible-parents");
            }
        }

        return new PreparedInvoiceIssue(period, parentIds, visibleOnly);
    }

    static String buildInvoiceIssueHeader(PreparedInvoiceIssue prepared) {
        return prepared.visibleOnly ? "Issue invoices for visible parents?" : "Issue invoices for all parents?";
    }

    static String buildInvoiceIssueMessage(PreparedInvoiceIssue prepared) {
        return (prepared.visibleOnly
            ? ("Period " + prepared.period + " for " + prepared.parentIds.size() + " visible parent(s).")
            : ("Period " + prepared.period + " for every parent record."))
            + "\n\nExisting invoices for the period will be skipped.";
    }

    static String buildCashPaymentConfirmation(BillingLedgerView.LedgerRow row, Function<String, String> nullSafe) {
        return "Invoice: " + nullSafe.apply(row.getInvoiceId())
            + "\nParent: " + nullSafe.apply(row.getParentName())
            + "\nAmount: " + row.getTotalText()
            + "\n\nThis will create a payment record and set the invoice status to Paid.";
    }

    static final class FocusResult {
        final String parentId;
        final String focusLabel;
        final String statusOverride;
        final boolean clearFocus;

        FocusResult(String parentId, String focusLabel, String statusOverride, boolean clearFocus) {
            this.parentId = parentId;
            this.focusLabel = focusLabel;
            this.statusOverride = statusOverride;
            this.clearFocus = clearFocus;
        }
    }

    static final class LatestUnpaidResult {
        final BillingLedgerView.LedgerRow target;
        final String parentId;
        final String focusLabel;
        final boolean found;

        LatestUnpaidResult(BillingLedgerView.LedgerRow target, String parentId, String focusLabel, boolean found) {
            this.target = target;
            this.parentId = parentId;
            this.focusLabel = focusLabel;
            this.found = found;
        }
    }

    static final class PreparedInvoiceIssue {
        final String period;
        final List<String> parentIds;
        final boolean visibleOnly;

        PreparedInvoiceIssue(String period, List<String> parentIds, boolean visibleOnly) {
            this.period = period;
            this.parentIds = parentIds;
            this.visibleOnly = visibleOnly;
        }
    }
}