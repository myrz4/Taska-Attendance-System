package nfc;

import java.util.Map;

@SuppressWarnings("unused")
final class BillingLedgerMessageSupport {
    private BillingLedgerMessageSupport() {
    }

    static {
        java.util.function.Function<Map<?, ?>, String> keepBuildInvoiceGenerationSummary =
            BillingLedgerMessageSupport::buildInvoiceGenerationSummary;
        java.util.function.Function<Throwable, String> keepRootMessage = BillingLedgerMessageSupport::rootMessage;
        java.util.Objects.requireNonNull(keepBuildInvoiceGenerationSummary);
        java.util.Objects.requireNonNull(keepRootMessage);
    }

    static String buildInvoiceGenerationSummary(Map<?, ?> result) {
        long created = longValueOrZero(result == null ? null : result.get("createdCount"));
        long existing = longValueOrZero(result == null ? null : result.get("existingCount"));
        long noChildren = longValueOrZero(result == null ? null : result.get("skippedNoChildrenCount"));
        long noItems = longValueOrZero(result == null ? null : result.get("skippedNoItemsCount"));
        long errors = longValueOrZero(result == null ? null : result.get("errorCount"));

        StringBuilder sb = new StringBuilder();
        sb.append("Period: ").append(nullSafe(stringValue(result == null ? null : result.get("period")))).append("\n");
        sb.append("Created invoices: ").append(created).append("\n");
        sb.append("Already existed: ").append(existing).append("\n");
        sb.append("Skipped (no linked children): ").append(noChildren).append("\n");
        sb.append("Skipped (no billable items): ").append(noItems).append("\n");
        sb.append("Errors: ").append(errors);
        return sb.toString();
    }

    static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        if (current == null) {
            return "Unknown error";
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            if (value instanceof String) {
                return Long.valueOf(((String) value).trim());
            }
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long longValueOrZero(Object value) {
        Long parsed = longValue(value);
        return parsed == null ? 0L : parsed;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}