package nfc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

@SuppressWarnings("unused")
final class BillingLedgerDetailSupport {
    private BillingLedgerDetailSupport() {}

    static String buildDetailsText(
        BillingLedgerView.LedgerRow row,
        List<BillingLedgerView.LedgerRow> allRows,
        Function<String, String> nullSafe,
        Function<Date, String> formatDate,
        Function<Date, String> formatDateTime,
        Function<Long, String> formatMoney,
        Function<BillingLedgerView.LedgerRow, String> paymentProvider,
        Predicate<BillingLedgerView.LedgerRow> isDummyPayment,
        Function<String, String> formatProviderLabel,
        Function<String, String> formatPaymentMethod,
        Function<String[], String> firstNonBlank
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Taska Zurah Billing Record\n");
        sb.append("Generated At: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        sb.append("Invoice ID: ").append(nullSafe.apply(row.getInvoiceId())).append('\n');
        sb.append("Parent: ").append(nullSafe.apply(row.getParentName())).append(" (ID: ").append(nullSafe.apply(row.getParentId())).append(")\n");
        sb.append("Student(s): ").append(nullSafe.apply(row.getChildDisplayName())).append('\n');
        sb.append("Period: ").append(nullSafe.apply(row.getPeriod())).append('\n');
        sb.append("Status: ").append(nullSafe.apply(row.getStatus())).append('\n');
        sb.append("Due Date: ").append(formatDate.apply(row.getDueDate())).append('\n');
        if (row.isOverdue()) {
            sb.append("Overdue By: ").append(row.getOverdueDays()).append(" day(s)\n");
        }
        sb.append("Created At: ").append(formatDateTime.apply(row.getCreatedAt())).append('\n');
        sb.append("Total: ").append(row.getTotalText()).append('\n');

        String receiptNo = row.getReceiptNo();
        if (!"-".equals(receiptNo)) {
            sb.append("Receipt No: ").append(receiptNo).append('\n');
        }

        String paidAt = formatDateTime.apply(row.getPaidAt());
        if (!"-".equals(paidAt)) {
            sb.append("Paid At: ").append(paidAt).append('\n');
        }

        FsDocument invoice = row.getInvoiceDocument();
        FsDocument payment = row.getPaymentDocument();
        String method = firstNonBlank.apply(new String[] {
            invoice.getString("paidMethod"),
            payment == null ? null : payment.getString("method")
        });
        String provider = paymentProvider.apply(row);
        if (method != null) {
            sb.append("Payment Method: ")
                .append(formatPaymentMethod.apply(method)).append('\n');
        }

        String bank = firstNonBlank.apply(new String[] {
            invoice.getString("paidBank"),
            payment == null ? null : payment.getString("bank")
        });
        if (bank != null) {
            sb.append("Bank: ").append(bank).append('\n');
        }

        String paymentId = firstNonBlank.apply(new String[] {
            invoice.getString("paidPaymentId"),
            payment == null ? null : payment.getId()
        });
        if (paymentId != null) {
            sb.append("Payment Record ID: ").append(paymentId).append('\n');
        }

        if (provider != null) {
            sb.append("Provider: ").append(formatProviderLabel.apply(provider)).append('\n');
        }

        sb.append('\n').append("Invoice Items").append('\n');
        appendInvoiceItems(sb, invoice.get("items"), formatMoney);

        List<String> policyNotes = invoicePolicyNotes(invoice);
        if (!policyNotes.isEmpty()) {
            sb.append('\n').append("Policy Notes").append('\n');
            for (String note : policyNotes) {
                sb.append("- ").append(note).append('\n');
            }
        }

        if (invoiceManagementReviewRecommended(invoice)) {
            sb.append('\n').append("Management Review").append('\n');
            sb.append("- ").append(nullSafe.apply(invoiceReviewReason(invoice))).append('\n');
        }

        if (payment != null) {
            sb.append('\n').append("Payment Record").append('\n');
            sb.append("Status: ").append(nullSafe.apply(firstNonBlank.apply(new String[] {payment.getString("status"), row.getStatus()}))).append('\n');
            Long amountSen = payment.getLong("amountSen");
            if (amountSen != null) {
                sb.append("Amount: ").append(formatMoney.apply(amountSen)).append('\n');
            }
            String gatewaySummary = payment.getString("gatewaySummary");
            if (gatewaySummary != null && !gatewaySummary.isBlank()) {
                sb.append("Gateway Summary: ").append(gatewaySummary).append('\n');
            }
        }

        sb.append('\n').append(buildParentSnapshot(row, allRows, formatMoney, formatDateTime));
        return sb.toString();
    }

    static String buildParentSnapshot(
        BillingLedgerView.LedgerRow selectedRow,
        List<BillingLedgerView.LedgerRow> allRows,
        Function<Long, String> formatMoney,
        Function<Date, String> formatDateTime
    ) {
        List<BillingLedgerView.LedgerRow> parentRows = rowsForParent(selectedRow.getParentId(), allRows);

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

        StringBuilder sb = new StringBuilder();
        sb.append("Parent Billing Snapshot\n");
        sb.append("Invoices on record: ").append(parentRows.size()).append('\n');
        sb.append("Unpaid invoices: ").append(unpaid).append('\n');
        sb.append("Overdue invoices: ").append(overdue).append('\n');
        sb.append("Total paid: ").append(formatMoney.apply(paidTotal)).append('\n');
        sb.append("Total outstanding: ").append(formatMoney.apply(outstandingTotal)).append('\n');
        sb.append("Last payment date: ").append(formatDateTime.apply(lastPaidAt)).append('\n');
        return sb.toString();
    }

    static String buildChildNamesText(List<BillingLedgerView.LedgerRow> rows) {
        List<String> childNames = new ArrayList<>();
        for (BillingLedgerView.LedgerRow row : rows) {
            for (String childName : row.getChildNames()) {
                String normalized = firstNonBlank(childName, "-");
                if (!childNames.contains(normalized)) {
                    childNames.add(normalized);
                }
            }
        }
        return childNames.isEmpty() ? "-" : String.join(", ", childNames);
    }

    static List<BillingLedgerView.LedgerRow> rowsForParent(String parentId, List<BillingLedgerView.LedgerRow> rows) {
        List<BillingLedgerView.LedgerRow> parentRows = new ArrayList<>();
        for (BillingLedgerView.LedgerRow row : rows) {
            if (Objects.equals(parentId, row.getParentId())) {
                parentRows.add(row);
            }
        }
        return parentRows;
    }

    static List<String> invoicePolicyNotes(FsDocument invoice) {
        Object billingMeta = invoice == null ? null : invoice.get("billingMeta");
        if (!(billingMeta instanceof Map<?, ?>)) {
            return List.of();
        }

        Object raw = ((Map<?, ?>) billingMeta).get("policyNotes");
        if (!(raw instanceof List<?>)) {
            return List.of();
        }

        List<String> notes = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            String text = stringValue(item);
            if (text != null) {
                notes.add(text);
            }
        }
        return notes;
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

    static String invoiceReviewReason(FsDocument invoice) {
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
            return "Child age is outside the PDF fee range and this invoice should be reviewed manually.";
        }
        return "Late-night overtime exceeded the policy threshold and should be reviewed by management.";
    }

    private static void appendInvoiceItems(StringBuilder sb, Object rawItems, Function<Long, String> formatMoney) {
        if (!(rawItems instanceof List<?>)) {
            sb.append("- No line items stored for this invoice.\n");
            return;
        }

        List<?> items = (List<?>) rawItems;
        if (items.isEmpty()) {
            sb.append("- No line items stored for this invoice.\n");
            return;
        }

        int index = 1;
        for (Object item : items) {
            if (item instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) item;
                String title = firstNonBlank(
                    stringValue(map.get("title")),
                    stringValue(map.get("description")),
                    stringValue(map.get("label")),
                    stringValue(map.get("code")),
                    "Item " + index
                );
                Long amountSen = longValue(map.get("amountSen"));
                Long quantity = longValue(map.get("qty"));
                if (quantity == null) {
                    quantity = longValue(map.get("quantity"));
                }

                sb.append(index).append(". ").append(title);
                if (quantity != null && quantity > 1) {
                    sb.append(" x").append(quantity);
                }
                if (amountSen != null) {
                    sb.append(" - ").append(formatMoney.apply(amountSen));
                }
                sb.append('\n');
            } else if (item != null) {
                sb.append(index).append(". ").append(item).append('\n');
            }
            index++;
        }
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}