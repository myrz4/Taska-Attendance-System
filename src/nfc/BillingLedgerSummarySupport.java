package nfc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@SuppressWarnings("unused")
final class BillingLedgerSummarySupport {
    private BillingLedgerSummarySupport() {}

    static List<BillingLedgerView.ParentSummaryRow> buildParentSummaries(
        List<BillingLedgerView.LedgerRow> rows,
        String sortMode,
        long watchOutstandingSen,
        long criticalOutstandingSen,
        Function<Long, String> formatMoney,
        Function<Date, String> formatDateTime
    ) {
        Map<String, ParentSummaryAccumulator> byParent = new LinkedHashMap<>();
        for (BillingLedgerView.LedgerRow row : rows) {
            String parentId = firstNonBlank(row.getParentId(), "-");
            ParentSummaryAccumulator accumulator = byParent.get(parentId);
            if (accumulator == null) {
                accumulator = new ParentSummaryAccumulator(parentId, firstNonBlank(row.getParentName(), "-"));
                byParent.put(parentId, accumulator);
            }
            accumulator.add(row);
        }

        List<BillingLedgerView.ParentSummaryRow> summaries = new ArrayList<>();
        for (ParentSummaryAccumulator accumulator : byParent.values()) {
            summaries.add(accumulator.toRow(watchOutstandingSen, criticalOutstandingSen, formatMoney, formatDateTime));
        }
        summaries.sort(buildParentSummaryComparator(firstNonBlank(sortMode, "Highest Outstanding")));
        return summaries;
    }

    static List<BillingLedgerView.ParentSummaryRow> filterParentSummariesByRisk(List<BillingLedgerView.ParentSummaryRow> summaries, String riskMode) {
        String normalizedRisk = firstNonBlank(riskMode, "All Families");
        if ("All Families".equalsIgnoreCase(normalizedRisk)) {
            return summaries;
        }

        List<BillingLedgerView.ParentSummaryRow> filtered = new ArrayList<>();
        for (BillingLedgerView.ParentSummaryRow summary : summaries) {
            if ("Needs Review".equalsIgnoreCase(normalizedRisk)) {
                if (summary.getReviewCount() > 0) {
                    filtered.add(summary);
                }
                continue;
            }
            if (normalizedRisk.equalsIgnoreCase(summary.getRiskLevel())) {
                filtered.add(summary);
            }
        }
        return filtered;
    }

    static SummarySnapshot summarizeVisibleRows(List<BillingLedgerView.LedgerRow> rows, Function<Long, String> formatMoney) {
        long paid = 0L;
        long outstanding = 0L;
        int overdueCount = 0;
        int ageReviewInvoiceCount = 0;
        int overtimeReviewInvoiceCount = 0;
        Map<String, Boolean> ageReviewParents = new LinkedHashMap<>();
        Map<String, Boolean> overtimeReviewParents = new LinkedHashMap<>();
        Map<String, Long> outstandingByPeriod = new LinkedHashMap<>();

        for (BillingLedgerView.LedgerRow row : rows) {
            if (row.isPaid()) {
                paid += row.getTotalSen();
            } else {
                outstanding += row.getTotalSen();
                String periodKey = firstNonBlank(row.getPeriod(), "Unknown");
                Long current = outstandingByPeriod.get(periodKey);
                outstandingByPeriod.put(periodKey, (current == null ? 0L : current) + row.getTotalSen());
            }
            if (row.isOverdue()) {
                overdueCount++;
            }
            if (row.isAgeOutOfPolicy()) {
                ageReviewInvoiceCount++;
                ageReviewParents.put(row.getParentId(), Boolean.TRUE);
            } else if (row.isManagementReviewRecommended()) {
                overtimeReviewInvoiceCount++;
                overtimeReviewParents.put(row.getParentId(), Boolean.TRUE);
            }
        }

        return new SummarySnapshot(
            "Invoices: " + rows.size(),
            "Paid: " + formatMoney.apply(paid),
            "Outstanding: " + formatMoney.apply(outstanding),
            "Overdue: " + overdueCount,
            "Age Review: " + ageReviewParents.size() + " fam / " + ageReviewInvoiceCount + " inv",
            "OT Review: " + overtimeReviewParents.size() + " fam / " + overtimeReviewInvoiceCount + " inv",
            buildPeriodTotalsText(outstandingByPeriod, formatMoney)
        );
    }

    private static String buildPeriodTotalsText(Map<String, Long> outstandingByPeriod, Function<Long, String> formatMoney) {
        if (outstandingByPeriod.isEmpty()) {
            return "Periods: no unpaid invoices in the current filter.";
        }

        StringBuilder sb = new StringBuilder("Outstanding by period: ");
        boolean first = true;
        for (Map.Entry<String, Long> entry : outstandingByPeriod.entrySet()) {
            if (!first) {
                sb.append(" | ");
            }
            first = false;
            sb.append(entry.getKey()).append(" ").append(formatMoney.apply(entry.getValue()));
        }
        return sb.toString();
    }

    private static Comparator<BillingLedgerView.ParentSummaryRow> buildParentSummaryComparator(String sortMode) {
        if ("Most Overdue Parents".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparingInt(BillingLedgerView.ParentSummaryRow::getOverdueCount)
                .reversed()
                .thenComparing(Comparator.comparingLong(BillingLedgerView.ParentSummaryRow::getOutstandingSen).reversed())
                .thenComparing(BillingLedgerView.ParentSummaryRow::getParentName, String::compareToIgnoreCase);
        }
        if ("Most Unpaid Invoices".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparingInt(BillingLedgerView.ParentSummaryRow::getUnpaidCount)
                .reversed()
                .thenComparing(Comparator.comparingLong(BillingLedgerView.ParentSummaryRow::getOutstandingSen).reversed())
                .thenComparing(BillingLedgerView.ParentSummaryRow::getParentName, String::compareToIgnoreCase);
        }
        if ("Most Age Reviews".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparingInt(BillingLedgerView.ParentSummaryRow::getAgeReviewCount)
                .reversed()
                .thenComparing(Comparator.comparingInt(BillingLedgerView.ParentSummaryRow::getReviewCount).reversed())
                .thenComparing(Comparator.comparingLong(BillingLedgerView.ParentSummaryRow::getOutstandingSen).reversed())
                .thenComparing(BillingLedgerView.ParentSummaryRow::getParentName, String::compareToIgnoreCase);
        }
        if ("Most Overtime Reviews".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparingInt(BillingLedgerView.ParentSummaryRow::getOvertimeReviewCount)
                .reversed()
                .thenComparing(Comparator.comparingInt(BillingLedgerView.ParentSummaryRow::getReviewCount).reversed())
                .thenComparing(Comparator.comparingLong(BillingLedgerView.ParentSummaryRow::getOutstandingSen).reversed())
                .thenComparing(BillingLedgerView.ParentSummaryRow::getParentName, String::compareToIgnoreCase);
        }
        if ("Latest Parent Payment".equalsIgnoreCase(sortMode)) {
            return Comparator
                .comparing(BillingLedgerView.ParentSummaryRow::getLastPaidAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Comparator.comparingLong(BillingLedgerView.ParentSummaryRow::getOutstandingSen).reversed())
                .thenComparing(BillingLedgerView.ParentSummaryRow::getParentName, String::compareToIgnoreCase);
        }
        if ("Parent Name".equalsIgnoreCase(sortMode)) {
            return Comparator.comparing(BillingLedgerView.ParentSummaryRow::getParentName, String::compareToIgnoreCase);
        }
        return Comparator
            .comparingLong(BillingLedgerView.ParentSummaryRow::getOutstandingSen)
            .reversed()
            .thenComparing(Comparator.comparingInt(BillingLedgerView.ParentSummaryRow::getOverdueCount).reversed())
            .thenComparing(BillingLedgerView.ParentSummaryRow::getParentName, String::compareToIgnoreCase);
    }

    private static String parentRiskLevel(int unpaidCount, int overdueCount, long outstandingSen, long watchOutstandingSen, long criticalOutstandingSen) {
        if (overdueCount >= 2 || outstandingSen >= criticalOutstandingSen || unpaidCount >= 4) {
            return "Critical";
        }
        if (overdueCount >= 1 || outstandingSen >= watchOutstandingSen || unpaidCount >= 2) {
            return "Watch";
        }
        return "Clear";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    static final class SummarySnapshot {
        final String invoiceCountText;
        final String paidTotalText;
        final String outstandingTotalText;
        final String overdueCountText;
        final String ageReviewSummaryText;
        final String overtimeReviewSummaryText;
        final String periodTotalsText;

        SummarySnapshot(
            String invoiceCountText,
            String paidTotalText,
            String outstandingTotalText,
            String overdueCountText,
            String ageReviewSummaryText,
            String overtimeReviewSummaryText,
            String periodTotalsText
        ) {
            this.invoiceCountText = invoiceCountText;
            this.paidTotalText = paidTotalText;
            this.outstandingTotalText = outstandingTotalText;
            this.overdueCountText = overdueCountText;
            this.ageReviewSummaryText = ageReviewSummaryText;
            this.overtimeReviewSummaryText = overtimeReviewSummaryText;
            this.periodTotalsText = periodTotalsText;
        }
    }

    private static final class ParentSummaryAccumulator {
        private final String parentId;
        private final String parentName;
        private int invoiceCount;
        private int unpaidCount;
        private int overdueCount;
        private long outstandingSen;
        private int reviewCount;
        private int ageReviewCount;
        private int overtimeReviewCount;
        private final List<String> reviewReasons = new ArrayList<>();
        private Date lastPaidAt;

        private ParentSummaryAccumulator(String parentId, String parentName) {
            this.parentId = parentId;
            this.parentName = parentName;
        }

        private void add(BillingLedgerView.LedgerRow row) {
            invoiceCount++;
            if (row.isPaid()) {
                if (row.getPaidAt() != null && (lastPaidAt == null || row.getPaidAt().after(lastPaidAt))) {
                    lastPaidAt = row.getPaidAt();
                }
            } else {
                unpaidCount++;
                outstandingSen += row.getTotalSen();
            }
            if (row.isOverdue()) {
                overdueCount++;
            }
            if (row.isManagementReviewRecommended()) {
                reviewCount++;
                if (row.isAgeOutOfPolicy()) {
                    ageReviewCount++;
                } else {
                    overtimeReviewCount++;
                }
                String reviewReason = firstNonBlank(row.getReviewReason());
                if (reviewReason != null && !reviewReasons.contains(reviewReason)) {
                    reviewReasons.add(reviewReason);
                }
            }
        }

        private BillingLedgerView.ParentSummaryRow toRow(
            long watchOutstandingSen,
            long criticalOutstandingSen,
            Function<Long, String> formatMoney,
            Function<Date, String> formatDateTime
        ) {
            List<String> reviewSummaryParts = new ArrayList<>();
            if (ageReviewCount > 0) {
                reviewSummaryParts.add(ageReviewCount + " age");
            }
            if (overtimeReviewCount > 0) {
                reviewSummaryParts.add(overtimeReviewCount + " overtime");
            }
            if (!reviewReasons.isEmpty()) {
                reviewSummaryParts.add(String.join("; ", reviewReasons));
            }
            return new BillingLedgerView.ParentSummaryRow(
                parentId,
                parentName,
                invoiceCount,
                unpaidCount,
                overdueCount,
                parentRiskLevel(unpaidCount, overdueCount, outstandingSen, watchOutstandingSen, criticalOutstandingSen),
                outstandingSen,
                formatMoney.apply(outstandingSen),
                reviewCount,
                ageReviewCount,
                overtimeReviewCount,
                reviewCount > 0 ? (reviewCount == 1 ? "Needs Review" : reviewCount + " reviews") : "",
                String.join(" | ", reviewSummaryParts),
                formatDateTime.apply(lastPaidAt),
                lastPaidAt
            );
        }
    }
}