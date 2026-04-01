package nfc;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.lowagie.text.Element;
import com.lowagie.text.pdf.PdfPTable;

@SuppressWarnings("unused")
final class BillingLedgerInvoicePdfSupport {
    private BillingLedgerInvoicePdfSupport() {}

    static void appendInvoiceItemsPdf(
        PdfPTable table,
        Object rawItems,
        java.awt.Color border,
        java.awt.Color ink,
        java.awt.Color muted,
        java.awt.Color tableAlt,
        Function<Long, String> formatMoney
    ) {
        if (!(rawItems instanceof List<?>)) {
            BillingLedgerExportSupport.addEmptyItemsRow(table, "No line items stored for this invoice.", 3, border, muted);
            return;
        }

        List<?> items = (List<?>) rawItems;
        if (items.isEmpty()) {
            BillingLedgerExportSupport.addEmptyItemsRow(table, "No line items stored for this invoice.", 3, border, muted);
            return;
        }

        int index = 0;
        for (Object item : items) {
            java.awt.Color rowBg = index % 2 == 0 ? java.awt.Color.WHITE : tableAlt;
            if (item instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) item;
                String title = firstNonBlank(
                    stringValue(map.get("title")),
                    stringValue(map.get("description")),
                    stringValue(map.get("label")),
                    stringValue(map.get("code")),
                    "Item " + (index + 1)
                );
                Long amountSen = longValue(map.get("amountSen"));
                Long quantity = longValue(map.get("qty"));
                if (quantity == null) {
                    quantity = longValue(map.get("quantity"));
                }

                BillingLedgerExportSupport.addBodyCell(table, title, rowBg, border, Element.ALIGN_LEFT, ink, false);
                BillingLedgerExportSupport.addBodyCell(table, quantity == null ? "1" : String.valueOf(quantity), rowBg, border, Element.ALIGN_CENTER, ink, false);
                BillingLedgerExportSupport.addBodyCell(table, amountSen == null ? "-" : formatMoney.apply(amountSen), rowBg, border, Element.ALIGN_RIGHT, ink, true);
            } else if (item != null) {
                BillingLedgerExportSupport.addBodyCell(table, String.valueOf(item), rowBg, border, Element.ALIGN_LEFT, ink, false);
                BillingLedgerExportSupport.addBodyCell(table, "1", rowBg, border, Element.ALIGN_CENTER, ink, false);
                BillingLedgerExportSupport.addBodyCell(table, "-", rowBg, border, Element.ALIGN_RIGHT, ink, false);
            }
            index++;
        }
    }

    static String buildSnapshotValue(
        List<BillingLedgerView.LedgerRow> parentRows,
        String key,
        Function<Long, String> formatMoney,
        Function<Date, String> formatDateTime
    ) {
        long paidTotal = 0L;
        long outstandingTotal = 0L;
        int overdue = 0;
        int unpaid = 0;
        Date lastPaidAt = null;

        for (BillingLedgerView.LedgerRow row : parentRows) {
            if (row.isPaid()) {
                paidTotal += row.getTotalSen();
                if (row.getPaidAt() != null && (lastPaidAt == null || row.getPaidAt().after(lastPaidAt))) {
                    lastPaidAt = row.getPaidAt();
                }
            } else {
                outstandingTotal += row.getTotalSen();
                unpaid++;
            }
            if (row.isOverdue()) {
                overdue++;
            }
        }

        switch (key) {
            case "unpaid":
                return String.valueOf(unpaid);
            case "overdue":
                return String.valueOf(overdue);
            case "paidTotal":
                return formatMoney.apply(paidTotal);
            case "outstandingTotal":
                return formatMoney.apply(outstandingTotal);
            case "lastPaidDate":
                return formatDateTime.apply(lastPaidAt);
            default:
                return "-";
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
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

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}