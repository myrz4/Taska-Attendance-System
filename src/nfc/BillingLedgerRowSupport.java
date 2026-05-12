package nfc;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("java:S1144")
final class BillingLedgerRowSupport {
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ms-MY"));
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private BillingLedgerRowSupport() {}

    @SuppressWarnings("unused")
    static List<String> extractChildNames(FsDocument invoice) {
        List<String> names = new ArrayList<>();
        Object rawChildNames = invoice.get("childNames");
        if (rawChildNames instanceof List<?>) {
            for (Object rawName : (List<?>) rawChildNames) {
                String name = firstNonBlank(stringValue(rawName));
                if (name != null && !names.contains(name)) {
                    names.add(name);
                }
            }
        }

        String childNameSummary = firstNonBlank(invoice.getString("childName"), invoice.getString("childId"));
        if (names.isEmpty() && childNameSummary != null) {
            names.add(childNameSummary);
        }
        return names;
    }

    @SuppressWarnings("unused")
    static String resolveStatus(String invoiceStatus, String paymentStatus, Date dueDate) {
        String raw = firstNonBlank(paymentStatus, invoiceStatus, "unknown");
        String normalized = raw.toLowerCase(Locale.ROOT);
        if ("paid".equals(normalized) || "succeeded".equals(normalized)) {
            return "Paid";
        }
        if ("failed".equals(normalized)) {
            return "Failed";
        }
        if ("pending".equals(normalized)) {
            return "Pending";
        }
        if ("unpaid".equals(normalized) || "open".equals(normalized) || "unknown".equals(normalized)) {
            if (dueDate != null && dueDate.before(new Date())) {
                return "Overdue";
            }
            return "Unpaid";
        }
        return normalized.isEmpty()
            ? "Unpaid"
            : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    static boolean invoiceManagementReviewRecommended(FsDocument invoice) {
        Object billingMeta = invoice == null ? null : invoice.get("billingMeta");
        if (!(billingMeta instanceof Map<?, ?>)) {
            return false;
        }
        Object raw = ((Map<?, ?>) billingMeta).get("managementReviewRecommended");
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        return "true".equalsIgnoreCase(String.valueOf(raw));
    }

    static boolean invoiceAgeOutOfPolicy(FsDocument invoice) {
        Object billingMeta = invoice == null ? null : invoice.get("billingMeta");
        if (!(billingMeta instanceof Map<?, ?>)) {
            return false;
        }
        Object raw = ((Map<?, ?>) billingMeta).get("ageOutOfPolicy");
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        return "true".equalsIgnoreCase(String.valueOf(raw));
    }

    @SuppressWarnings("all")
    public static String invoiceReviewReason(FsDocument invoice) {
        if (!invoiceManagementReviewRecommended(invoice)) {
            return "";
        }
        Object billingMeta = invoice == null ? null : invoice.get("billingMeta");
        if (billingMeta instanceof Map<?, ?>) {
            Object customReason = ((Map<?, ?>) billingMeta).get("reviewReason");
            if (customReason != null) {
                String text = String.valueOf(customReason).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        if (invoiceAgeOutOfPolicy(invoice)) {
            return "Child age is outside the supported Taska Zurah range and this invoice should be reviewed manually.";
        }
        return "Late-night overtime exceeded the policy threshold and should be reviewed by management.";
    }

    @SuppressWarnings("unused")
    static String formatMoney(long sen) {
        return MONEY_FORMAT.format(sen / 100.0);
    }

    @SuppressWarnings("unused")
    static String formatDate(Date date) {
        if (date == null) {
            return "-";
        }
        return DATE_FORMAT.format(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }

    @SuppressWarnings("unused")
    static Date firstDate(Date... values) {
        for (Date value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static String firstNonBlank(String... values) {
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
}