package nfc;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

@SuppressWarnings("unused")
final class BillingLedgerInvoiceDocumentSupport {
    private BillingLedgerInvoiceDocumentSupport() {}

    static void addIdentity(
        Document document,
        BillingLedgerView.LedgerRow row,
        java.awt.Color ink,
        java.awt.Color muted,
        java.awt.Color border,
        Function<String, String> nullSafe,
        Function<Date, String> formatDate,
        Function<Date, String> formatDateTime,
        Function<BillingLedgerView.LedgerRow, String> paymentProvider,
        Predicate<BillingLedgerView.LedgerRow> isDummyPayment,
        Function<String, String> formatProviderLabel,
        Function<String, String> formatPaymentMethod,
        Function<String[], String> firstNonBlank
    ) throws Exception {
        PdfPTable cards = new PdfPTable(2);
        cards.setWidthPercentage(100);
        cards.setWidths(new float[]{1f, 1f});
        cards.setSpacingAfter(14f);

        cards.addCell(BillingLedgerExportSupport.buildInfoCard("Parent", new String[][] {
            {"Name", nullSafe.apply(row.getParentName())},
            {"Parent ID", nullSafe.apply(row.getParentId())},
            {"Student(s)", nullSafe.apply(row.getChildDisplayName())}
        }, ink, muted, border));

        FsDocument invoice = row.getInvoiceDocument();
        FsDocument payment = row.getPaymentDocument();

        String method = firstNonBlank.apply(new String[] {
            invoice.getString("paidMethod"),
            payment == null ? null : payment.getString("method")
        });
        String bank = firstNonBlank.apply(new String[] {
            invoice.getString("paidBank"),
            payment == null ? null : payment.getString("bank")
        });
        String provider = paymentProvider.apply(row);
        String paymentIntentId = firstNonBlank.apply(new String[] {
            invoice.getString("stripePaymentIntentId"),
            payment == null ? null : payment.getString("stripePaymentIntentId")
        });
        String cardDetails = firstNonBlank.apply(new String[] {
            paymentCardLabel(invoice.getString("paidCardBrand"), invoice.getString("paidCardLast4")),
            payment == null ? null : paymentCardLabel(payment.getString("cardBrand"), payment.getString("cardLast4"))
        });

        cards.addCell(BillingLedgerExportSupport.buildInfoCard(row.isPaid() ? "Payment" : "Billing", new String[][] {
            {"Period", nullSafe.apply(row.getPeriod())},
            {"Due Date", formatDate.apply(row.getDueDate())},
            {"Paid At", formatDateTime.apply(row.getPaidAt())},
            {"Receipt No", nullSafe.apply(row.getReceiptNo())},
            {"Method", method == null ? "-" : formatPaymentMethod.apply(method)},
            {"Provider", provider == null ? "-" : formatProviderLabel.apply(provider)},
            {cardDetails == null ? "Bank" : "Card", cardDetails == null ? (bank == null ? "-" : bank) : cardDetails},
            {"PaymentIntent ID", paymentIntentId == null ? "-" : paymentIntentId}
        }, ink, muted, border));

        document.add(cards);
    }

    static void addItems(
        Document document,
        BillingLedgerView.LedgerRow row,
        java.awt.Color brandGold,
        java.awt.Color ink,
        java.awt.Color muted,
        java.awt.Color border,
        java.awt.Color successBg,
        java.awt.Color successText,
        java.awt.Color dangerBg,
        java.awt.Color dangerText,
        java.awt.Color tableAlt,
        Function<Long, String> formatMoney,
        Function<BillingLedgerView.LedgerRow, String> paymentProvider,
        Function<String, String> formatProviderLabel
    ) throws Exception {
        Paragraph sectionTitle = new Paragraph("Invoice Items", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, ink));
        sectionTitle.setSpacingAfter(8f);
        document.add(sectionTitle);

        PdfPTable itemsTable = new PdfPTable(3);
        itemsTable.setWidthPercentage(100);
        itemsTable.setWidths(new float[]{4.2f, 1f, 1.5f});
        itemsTable.setSpacingAfter(14f);

        BillingLedgerExportSupport.addTableHeaderCell(itemsTable, "Description", brandGold, ink, border, com.lowagie.text.Element.ALIGN_LEFT);
        BillingLedgerExportSupport.addTableHeaderCell(itemsTable, "Qty", brandGold, ink, border, com.lowagie.text.Element.ALIGN_CENTER);
        BillingLedgerExportSupport.addTableHeaderCell(itemsTable, "Amount", brandGold, ink, border, com.lowagie.text.Element.ALIGN_RIGHT);

        BillingLedgerInvoicePdfSupport.appendInvoiceItemsPdf(itemsTable, row.getInvoiceDocument().get("items"), border, ink, muted, tableAlt, formatMoney);
        document.add(itemsTable);

        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(42);
        totals.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_RIGHT);
        totals.setWidths(new float[]{1.3f, 1f});
        totals.setSpacingAfter(14f);

        BillingLedgerExportSupport.addTotalRow(totals, "Total Amount", row.getTotalText(), new java.awt.Color(255, 245, 217), ink, border);

        String provider = paymentProvider.apply(row);
        if (provider != null) {
            BillingLedgerExportSupport.addTotalRow(totals, "Provider", formatProviderLabel.apply(provider), row.isPaid() ? successBg : tableAlt, row.isPaid() ? successText : ink, border);
        }
        if (row.isOverdue()) {
            BillingLedgerExportSupport.addTotalRow(totals, "Overdue By", row.getOverdueDays() + " day(s)", dangerBg, dangerText, border);
        }

        document.add(totals);
    }

    static void addNotes(
        Document document,
        BillingLedgerView.LedgerRow row,
        List<BillingLedgerView.LedgerRow> parentRows,
        List<String> policyNotes,
        boolean hasReview,
        java.awt.Color ink,
        java.awt.Color muted,
        java.awt.Color border,
        java.awt.Color brandGoldSoft,
        java.awt.Color successBg,
        Function<String, String> nullSafe,
        Function<Long, String> formatMoney,
        Function<Date, String> formatDateTime,
        Function<String, String> formatProviderLabel,
        Function<String[], String> firstNonBlank
    ) throws Exception {
        PdfPTable section = new PdfPTable(2);
        section.setWidthPercentage(100);
        section.setWidths(new float[]{1f, 1f});
        section.setSpacingAfter(12f);

        section.addCell(BillingLedgerExportSupport.buildInfoCard("Parent Snapshot", new String[][] {
            {"Invoices on record", String.valueOf(parentRows.size())},
            {"Unpaid invoices", BillingLedgerInvoicePdfSupport.buildSnapshotValue(parentRows, "unpaid", formatMoney, formatDateTime)},
            {"Overdue invoices", BillingLedgerInvoicePdfSupport.buildSnapshotValue(parentRows, "overdue", formatMoney, formatDateTime)},
            {"Total paid", BillingLedgerInvoicePdfSupport.buildSnapshotValue(parentRows, "paidTotal", formatMoney, formatDateTime)},
            {"Total outstanding", BillingLedgerInvoicePdfSupport.buildSnapshotValue(parentRows, "outstandingTotal", formatMoney, formatDateTime)},
            {"Last payment date", BillingLedgerInvoicePdfSupport.buildSnapshotValue(parentRows, "lastPaidDate", formatMoney, formatDateTime)}
        }, ink, muted, border));

        if (!policyNotes.isEmpty() || hasReview) {
            List<String[]> noteRows = new ArrayList<>();
            for (String note : policyNotes) {
                noteRows.add(new String[] {"Policy", note});
            }
            if (hasReview) {
                noteRows.add(new String[] {firstNonBlank.apply(new String[] {row.getReviewFlag(), "Review"}), nullSafe.apply(row.getReviewReason())});
            }
            section.addCell(BillingLedgerExportSupport.buildInfoCard("Policy and Review", noteRows.toArray(new String[0][]), ink, muted, border));
        } else {
            PdfPCell noteCard = new PdfPCell();
            noteCard.setPadding(14f);
            noteCard.setBorderColor(border);
            noteCard.setBackgroundColor(brandGoldSoft);
            Paragraph title = new Paragraph("POLICY AND REVIEW", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, muted));
            title.setSpacingAfter(10f);
            noteCard.addElement(title);
            noteCard.addElement(new Paragraph("No manual review flags were recorded for this invoice.", FontFactory.getFont(FontFactory.HELVETICA, 10, ink)));
            section.addCell(noteCard);
        }

        FsDocument invoice = row.getInvoiceDocument();
        FsDocument payment = row.getPaymentDocument();
        if (payment != null) {
            PdfPCell paymentCard = BillingLedgerExportSupport.buildInfoCard("Payment Record", new String[][] {
                {"Status", nullSafe.apply(firstNonBlank.apply(new String[] {payment.getString("status"), row.getStatus()}))},
                {"Amount", payment.getLong("amountSen") == null ? "-" : formatMoney.apply(payment.getLong("amountSen"))},
                {"Provider", nullSafe.apply(formatProviderLabel.apply(payment.getString("provider")))},
                {"PaymentIntent ID", nullSafe.apply(firstNonBlank.apply(new String[] {payment.getString("stripePaymentIntentId"), invoice.getString("stripePaymentIntentId"), "-"}))},
                {"Card", nullSafe.apply(firstNonBlank.apply(new String[] {paymentCardLabel(payment.getString("cardBrand"), payment.getString("cardLast4")), paymentCardLabel(invoice.getString("paidCardBrand"), invoice.getString("paidCardLast4")), "-"}))},
                {"Gateway Summary", nullSafe.apply(firstNonBlank.apply(new String[] {payment.getString("gatewaySummary"), "-"}))}
            }, ink, muted, border);
            paymentCard.setColspan(2);
            paymentCard.setBackgroundColor(row.isPaid() ? successBg : brandGoldSoft);
            section.addCell(paymentCard);
        }

        document.add(section);
    }

    private static String paymentCardLabel(String rawBrand, String rawLast4) {
        String brand = BillingLedgerValueSupport.firstNonBlank(rawBrand);
        String last4 = BillingLedgerValueSupport.firstNonBlank(rawLast4);
        if (brand == null && last4 == null) {
            return null;
        }
        if (brand == null) {
            return "Card ending in " + last4;
        }
        if (last4 == null) {
            return brand.toUpperCase();
        }
        return brand.toUpperCase() + " ending in " + last4;
    }
}