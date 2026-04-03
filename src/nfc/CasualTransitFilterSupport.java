package nfc;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("unused")
final class CasualTransitFilterSupport {
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ms-MY"));

    private CasualTransitFilterSupport() {}

    @SuppressWarnings("unused")
    static FilterResult applyFilters(
        List<CasualTransitView.VisitRow> allRows,
        String searchText,
        String statusValue,
        String paymentStatusValue,
        String dateScope,
        LocalDate rangeFrom,
        LocalDate rangeTo
    ) {
        String search = safe(searchText).toLowerCase(Locale.ROOT);
        String status = safe(statusValue).toUpperCase(Locale.ROOT);
        String paymentStatus = normalizePaymentFilter(paymentStatusValue);

        List<CasualTransitView.VisitRow> filtered = allRows.stream()
            .filter(row -> "ALL".equals(status) || row.status().equals(status))
            .filter(row -> paymentStatus.isBlank() || row.paymentStatus().equals(paymentStatus))
            .filter(row -> row.matchesDateScope(dateScope, rangeFrom, rangeTo))
            .filter(row -> search.isBlank() || row.searchText().contains(search))
            .toList();

        long openCount = filtered.stream().filter(CasualTransitView.VisitRow::isOpen).count();
        long closedCount = filtered.stream().filter(CasualTransitView.VisitRow::isClosed).count();
        long canceledCount = filtered.stream().filter(CasualTransitView.VisitRow::isCanceled).count();
        long pendingPaymentCount = filtered.stream().filter(CasualTransitView.VisitRow::isPaymentPending).count();
        long paidPaymentCount = filtered.stream().filter(CasualTransitView.VisitRow::isPaymentPaid).count();
        long voidPaymentCount = filtered.stream().filter(CasualTransitView.VisitRow::isPaymentVoid).count();
        long totalPaidSen = filtered.stream().mapToLong(CasualTransitView.VisitRow::amountSen).sum();

        return new FilterResult(
            filtered,
            "Visits: " + filtered.size()
                + " | Open: " + openCount
                + " | Closed: " + closedCount
                + " | Canceled: " + canceledCount
                + " | Pending Payment: " + pendingPaymentCount
                + " | Paid: " + paidPaymentCount
                + " | Void: " + voidPaymentCount
                + " | Paid: " + formatMoney(totalPaidSen)
        );
    }

    @SuppressWarnings("unused")
    static String currentViewFilterSummary(
        String searchText,
        String statusValue,
        String paymentStatusValue,
        String dateScope,
        LocalDate rangeFrom,
        LocalDate rangeTo
    ) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        String search = safe(searchText);
        String status = safe(statusValue);
        String paymentStatus = safe(paymentStatusValue);
        if (!search.isBlank()) {
            parts.add("Search: " + search);
        }
        if (!status.isBlank() && !"All".equalsIgnoreCase(status)) {
            parts.add("Status: " + status);
        }
        if (!paymentStatus.isBlank() && !"All Payment States".equalsIgnoreCase(paymentStatus)) {
            parts.add("Payment: " + paymentStatus);
        }
        String describedDateScope = describeDateScope(dateScope, rangeFrom, rangeTo);
        if (!describedDateScope.isBlank()) {
            parts.add(describedDateScope);
        }
        return parts.isEmpty() ? "All visible visits" : String.join(" | ", parts);
    }

    @SuppressWarnings("unused")
    static String currentAuditFilterSummary(String actionValue, String dateScopeValue) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        String action = safe(actionValue);
        String dateScope = safe(dateScopeValue);
        if (!action.isBlank() && !"All Actions".equalsIgnoreCase(action)) {
            parts.add("Action: " + action);
        }
        if (!dateScope.isBlank() && !"All Dates".equalsIgnoreCase(dateScope)) {
            parts.add("Date: " + dateScope);
        }
        return parts.isEmpty() ? "All audit entries" : String.join(" | ", parts);
    }

    private static String normalizePaymentFilter(String value) {
        String normalized = safe(value).trim();
        if (normalized.isBlank() || "All Payment States".equalsIgnoreCase(normalized)) {
            return "";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static String describeDateScope(String dateScope, LocalDate rangeFrom, LocalDate rangeTo) {
        if (safe(dateScope).isBlank() || "All Dates".equalsIgnoreCase(dateScope)) {
            return "";
        }
        if ("Custom Range".equalsIgnoreCase(dateScope)) {
            String fromLabel = rangeFrom == null ? "Start" : rangeFrom.toString();
            String toLabel = rangeTo == null ? "End" : rangeTo.toString();
            return "Date: " + fromLabel + " to " + toLabel;
        }
        return "Date: " + dateScope;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String formatMoney(long sen) {
        return MONEY_FORMAT.format(sen / 100.0d);
    }

    @SuppressWarnings("unused")
    static final class FilterResult {
        final List<CasualTransitView.VisitRow> filteredRows;
        final String totalsText;

        FilterResult(List<CasualTransitView.VisitRow> filteredRows, String totalsText) {
            this.filteredRows = filteredRows;
            this.totalsText = totalsText;
        }
    }
}