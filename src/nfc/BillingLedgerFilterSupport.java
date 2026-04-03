package nfc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class BillingLedgerFilterSupport {
    private BillingLedgerFilterSupport() {}

    static {
        java.util.function.Consumer<List<BillingLedgerView.LedgerRow>> keepApply = rows -> applyFilters(
            rows,
            null,
            "All",
            "All Dates",
            "All Reviews",
            "All Families",
            "",
            "Latest First",
            "Highest Outstanding",
            0L,
            0L,
            amount -> "",
            date -> ""
        );
        java.util.function.Function<List<BillingLedgerView.LedgerRow>, List<BillingLedgerView.ParentSummaryRow>> keepSummary =
            rows -> updateParentSummary(rows, "Highest Outstanding", "All Families", 0L, 0L, amount -> "", date -> "");
        java.util.function.Function<String, String> keepDescription =
            status -> buildCurrentFilterDescription(status, "All Dates", "All Families", "All Reviews", "", "");
        FilterResult probe = new FilterResult(List.of(), List.of());
        java.util.Objects.requireNonNull(keepApply);
        java.util.Objects.requireNonNull(keepSummary);
        java.util.Objects.requireNonNull(keepDescription);
        java.util.Objects.hash(probe.filteredRows, probe.visibleSummaries);
    }

    static FilterResult applyFilters(
        List<BillingLedgerView.LedgerRow> allRows,
        String focusedParentId,
        String selectedStatus,
        String dateScope,
        String reviewType,
        String familyRisk,
        String query,
        String sortMode,
        String parentSortMode,
        long watchOutstandingSen,
        long criticalOutstandingSen,
        Function<Long, String> formatMoney,
        Function<java.util.Date, String> formatDateTime
    ) {
        List<BillingLedgerView.LedgerRow> baseFiltered = new ArrayList<>();
        for (BillingLedgerView.LedgerRow row : allRows) {
            if (focusedParentId != null && !focusedParentId.equals(row.getParentId())) {
                continue;
            }
            if (!"All".equalsIgnoreCase(selectedStatus) && !selectedStatus.equalsIgnoreCase(row.getStatus())) {
                continue;
            }
            if (!matchesDateScope(row, dateScope)) {
                continue;
            }
            if (!matchesReviewFilter(row, reviewType)) {
                continue;
            }
            if (!query.isBlank() && !row.matches(query)) {
                continue;
            }
            baseFiltered.add(row);
        }

        List<BillingLedgerView.ParentSummaryRow> summaries = BillingLedgerSummarySupport.buildParentSummaries(
            baseFiltered,
            BillingLedgerRowSupport.firstNonBlank(parentSortMode, "Highest Outstanding"),
            watchOutstandingSen,
            criticalOutstandingSen,
            formatMoney,
            formatDateTime
        );
        List<BillingLedgerView.ParentSummaryRow> visibleSummaries = BillingLedgerSummarySupport.filterParentSummariesByRisk(
            summaries,
            BillingLedgerRowSupport.firstNonBlank(familyRisk, "All Families")
        );

        Map<String, BillingLedgerView.ParentSummaryRow> visibleParentIds = new LinkedHashMap<>();
        for (BillingLedgerView.ParentSummaryRow summary : visibleSummaries) {
            visibleParentIds.put(summary.getParentId(), summary);
        }

        List<BillingLedgerView.LedgerRow> filtered = new ArrayList<>();
        for (BillingLedgerView.LedgerRow row : baseFiltered) {
            if (visibleParentIds.containsKey(row.getParentId())) {
                filtered.add(row);
            }
        }

        filtered.sort(buildSortComparator(sortMode));
        return new FilterResult(filtered, visibleSummaries);
    }

    static List<BillingLedgerView.ParentSummaryRow> updateParentSummary(
        List<BillingLedgerView.LedgerRow> rows,
        String parentSortMode,
        String familyRisk,
        long watchOutstandingSen,
        long criticalOutstandingSen,
        Function<Long, String> formatMoney,
        Function<java.util.Date, String> formatDateTime
    ) {
        List<BillingLedgerView.ParentSummaryRow> summaries = BillingLedgerSummarySupport.buildParentSummaries(
            rows,
            BillingLedgerRowSupport.firstNonBlank(parentSortMode, "Highest Outstanding"),
            watchOutstandingSen,
            criticalOutstandingSen,
            formatMoney,
            formatDateTime
        );
        return BillingLedgerSummarySupport.filterParentSummariesByRisk(
            summaries,
            BillingLedgerRowSupport.firstNonBlank(familyRisk, "All Families")
        );
    }

    static String buildCurrentFilterDescription(
        String status,
        String dateScope,
        String familyRisk,
        String reviewType,
        String searchText,
        String focusedParentId
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(BillingLedgerRowSupport.firstNonBlank(status, "All"));
        sb.append(" | Date Scope: ").append(BillingLedgerRowSupport.firstNonBlank(dateScope, "All Dates"));
        sb.append(" | Family Risk: ").append(BillingLedgerRowSupport.firstNonBlank(familyRisk, "All Families"));
        sb.append(" | Review Type: ").append(BillingLedgerRowSupport.firstNonBlank(reviewType, "All Reviews"));
        sb.append(" | Search: ").append(searchText == null || searchText.isBlank() ? "none" : searchText.trim());
        sb.append(" | Parent Focus: ").append(focusedParentId == null || focusedParentId.isBlank() ? "none" : focusedParentId);
        return sb.toString();
    }

    private static Comparator<BillingLedgerView.LedgerRow> buildSortComparator(String sortMode) {
        if ("Highest Outstanding".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparingLong((BillingLedgerView.LedgerRow row) -> row.isPaid() ? 0L : row.getTotalSen())
                .reversed()
                .thenComparing(BillingLedgerView.LedgerRow::getSortDate, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        if ("Most Overdue".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparingLong(BillingLedgerView.LedgerRow::getOverdueDays)
                .reversed()
                .thenComparing(Comparator.comparingLong((BillingLedgerView.LedgerRow row) -> row.isPaid() ? 0L : row.getTotalSen()).reversed())
                .thenComparing(BillingLedgerView.LedgerRow::getSortDate, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        if ("Latest Payment".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparing(BillingLedgerView.LedgerRow::getPaidAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(BillingLedgerView.LedgerRow::getSortDate, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return Comparator
            .comparing(BillingLedgerView.LedgerRow::getSortDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(BillingLedgerView.LedgerRow::getInvoiceId, Comparator.nullsLast(String::compareToIgnoreCase));
    }

    private static boolean matchesReviewFilter(BillingLedgerView.LedgerRow row, String reviewType) {
        String selectedReviewType = BillingLedgerRowSupport.firstNonBlank(reviewType, "All Reviews");
        if ("All Reviews".equalsIgnoreCase(selectedReviewType)) {
            return true;
        }
        if ("Review Only".equalsIgnoreCase(selectedReviewType)) {
            return row.isManagementReviewRecommended();
        }
        if ("Age Review".equalsIgnoreCase(selectedReviewType)) {
            return row.isAgeOutOfPolicy();
        }
        if ("Overtime Review".equalsIgnoreCase(selectedReviewType)) {
            return row.isManagementReviewRecommended() && !row.isAgeOutOfPolicy();
        }
        if ("No Review".equalsIgnoreCase(selectedReviewType)) {
            return !row.isManagementReviewRecommended();
        }
        return true;
    }

    private static boolean matchesDateScope(BillingLedgerView.LedgerRow row, String scope) {
        String normalized = BillingLedgerRowSupport.firstNonBlank(scope, "All Dates");
        if ("All Dates".equalsIgnoreCase(normalized)) {
            return true;
        }

        LocalDate invoiceDate = row.getRelevantDate();
        if (invoiceDate == null) {
            return false;
        }

        LocalDate today = LocalDate.now();
        if ("Current Month".equalsIgnoreCase(normalized)) {
            return invoiceDate.getYear() == today.getYear() && invoiceDate.getMonthValue() == today.getMonthValue();
        }
        if ("Current Year".equalsIgnoreCase(normalized)) {
            return invoiceDate.getYear() == today.getYear();
        }
        return true;
    }

    static final class FilterResult {
        final List<BillingLedgerView.LedgerRow> filteredRows;
        final List<BillingLedgerView.ParentSummaryRow> visibleSummaries;

        FilterResult(List<BillingLedgerView.LedgerRow> filteredRows, List<BillingLedgerView.ParentSummaryRow> visibleSummaries) {
            this.filteredRows = filteredRows;
            this.visibleSummaries = visibleSummaries;
        }
    }
}