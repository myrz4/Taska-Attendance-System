package nfc;

import java.util.Locale;

final class BillingLedgerValueSupport {
    private BillingLedgerValueSupport() {}

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    static long longValueOrZero(Object value) {
        Long parsed = longValue(value);
        return parsed == null ? 0L : parsed;
    }

    static String slug(String value) {
        if (value == null || value.isBlank()) {
            return "record";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
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
}