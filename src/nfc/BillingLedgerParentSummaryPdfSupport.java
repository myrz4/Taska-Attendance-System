package nfc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

@SuppressWarnings("unused")
final class BillingLedgerParentSummaryPdfSupport {
    private BillingLedgerParentSummaryPdfSupport() {}

    static void addMetrics(
        Document document,
        BillingLedgerView.ParentSummaryRow summary,
        List<BillingLedgerView.LedgerRow> sortedRows,
        String childNamesText,
        java.awt.Color ink,
        java.awt.Color muted,
        java.awt.Color border,
        java.awt.Color brandGoldSoft,
        java.awt.Color successBg,
        java.awt.Color successText,
        java.awt.Color dangerBg,
        java.awt.Color dangerText,
        Function<Long, String> formatMoney
    ) throws Exception {
        long paidTotal = 0L;
        long outstandingTotal = 0L;
        int paidCount = 0;
        int unpaidCount = 0;
        int overdueCount = 0;

        for (BillingLedgerView.LedgerRow row : sortedRows) {
            if (row.isPaid()) {
                paidTotal += row.getTotalSen();
                paidCount++;
            } else {
                outstandingTotal += row.getTotalSen();
                unpaidCount++;
            }
            if (row.isOverdue()) {
                overdueCount++;
            }
        }

        PdfPTable cards = new PdfPTable(2);
        cards.setWidthPercentage(100);
        cards.setWidths(new float[]{1f, 1f});
        cards.setSpacingAfter(14f);

        cards.addCell(BillingLedgerExportSupport.buildInfoCard("Parent Overview", new String[][] {
            {"Parent", nullSafe(summary.getParentName())},
            {"Children in View", childNamesText},
            {"Last Payment", nullSafe(summary.getLastPaidText())},
            {"Review Status", firstNonBlank(summary.getReviewStatus(), "-")}
        }, ink, muted, border));

        PdfPCell metricsCard = new PdfPCell();
        metricsCard.setPadding(14f);
        metricsCard.setBorderColor(border);
        metricsCard.setBackgroundColor(java.awt.Color.WHITE);
        Paragraph title = new Paragraph("SUMMARY METRICS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, muted));
        title.setSpacingAfter(10f);
        metricsCard.addElement(title);

        PdfPTable metrics = new PdfPTable(2);
        metrics.setWidthPercentage(100);
        metrics.setWidths(new float[]{1f, 1f});
        metrics.addCell(BillingLedgerExportSupport.buildMetricCell("Paid", String.valueOf(paidCount), successBg, successText, muted));
        metrics.addCell(BillingLedgerExportSupport.buildMetricCell("Unpaid", String.valueOf(unpaidCount), dangerBg, dangerText, muted));
        metrics.addCell(BillingLedgerExportSupport.buildMetricCell("Outstanding", formatMoney.apply(outstandingTotal), brandGoldSoft, ink, muted));
        metrics.addCell(BillingLedgerExportSupport.buildMetricCell("Overdue", String.valueOf(overdueCount), overdueCount > 0 ? dangerBg : successBg, overdueCount > 0 ? dangerText : successText, muted));
        metrics.addCell(BillingLedgerExportSupport.buildMetricCell("Paid Total", formatMoney.apply(paidTotal), successBg, successText, muted));
        metrics.addCell(BillingLedgerExportSupport.buildMetricCell("Review Alerts", String.valueOf(summary.getReviewCount()), brandGoldSoft, ink, muted));
        metricsCard.addElement(metrics);
        cards.addCell(metricsCard);

        document.add(cards);
    }

    static void addInvoices(
        Document document,
        List<BillingLedgerView.LedgerRow> sortedRows,
        java.awt.Color brandGold,
        java.awt.Color ink,
        java.awt.Color muted,
        java.awt.Color border,
        java.awt.Color successBg,
        java.awt.Color successText,
        java.awt.Color dangerBg,
        java.awt.Color dangerText,
        java.awt.Color tableAlt,
        Function<String, String> nullSafe,
        Function<java.util.Date, String> formatDate
    ) throws Exception {
        Paragraph sectionTitle = new Paragraph("Visible Invoices", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, ink));
        sectionTitle.setSpacingAfter(8f);
        document.add(sectionTitle);

        PdfPTable invoicesTable = new PdfPTable(6);
        invoicesTable.setWidthPercentage(100);
        invoicesTable.setWidths(new float[]{2.3f, 1.7f, 1.2f, 1.4f, 1.5f, 1.3f});
        invoicesTable.setSpacingAfter(14f);

        BillingLedgerExportSupport.addTableHeaderCell(invoicesTable, "Invoice ID", brandGold, ink, border, Element.ALIGN_LEFT);
        BillingLedgerExportSupport.addTableHeaderCell(invoicesTable, "Student(s)", brandGold, ink, border, Element.ALIGN_LEFT);
        BillingLedgerExportSupport.addTableHeaderCell(invoicesTable, "Period", brandGold, ink, border, Element.ALIGN_CENTER);
        BillingLedgerExportSupport.addTableHeaderCell(invoicesTable, "Status", brandGold, ink, border, Element.ALIGN_CENTER);
        BillingLedgerExportSupport.addTableHeaderCell(invoicesTable, "Due Date", brandGold, ink, border, Element.ALIGN_CENTER);
        BillingLedgerExportSupport.addTableHeaderCell(invoicesTable, "Amount", brandGold, ink, border, Element.ALIGN_RIGHT);

        if (sortedRows.isEmpty()) {
            BillingLedgerExportSupport.addEmptyItemsRow(invoicesTable, "No visible invoices for this parent summary.", 6, border, muted);
        } else {
            for (int index = 0; index < sortedRows.size(); index++) {
                BillingLedgerView.LedgerRow row = sortedRows.get(index);
                java.awt.Color rowBg = index % 2 == 0 ? java.awt.Color.WHITE : tableAlt;
                java.awt.Color statusBg = row.isPaid() ? successBg : (row.isOverdue() ? dangerBg : brandGold);
                java.awt.Color statusColor = row.isPaid() ? successText : (row.isOverdue() ? dangerText : ink);

                BillingLedgerExportSupport.addBodyCell(invoicesTable, nullSafe.apply(row.getInvoiceId()), rowBg, border, Element.ALIGN_LEFT, ink, false);
                BillingLedgerExportSupport.addBodyCell(invoicesTable, nullSafe.apply(row.getChildDisplayName()), rowBg, border, Element.ALIGN_LEFT, ink, false);
                BillingLedgerExportSupport.addBodyCell(invoicesTable, nullSafe.apply(row.getPeriod()), rowBg, border, Element.ALIGN_CENTER, ink, false);
                BillingLedgerExportSupport.addBodyCell(invoicesTable, nullSafe.apply(row.getStatus()).toUpperCase(Locale.ROOT), statusBg, border, Element.ALIGN_CENTER, statusColor, true);
                BillingLedgerExportSupport.addBodyCell(invoicesTable, formatDate.apply(row.getDueDate()), rowBg, border, Element.ALIGN_CENTER, ink, false);
                BillingLedgerExportSupport.addBodyCell(invoicesTable, row.getTotalText(), rowBg, border, Element.ALIGN_RIGHT, ink, true);
            }
        }

        document.add(invoicesTable);
    }

    static void addReview(
        Document document,
        BillingLedgerView.ParentSummaryRow summary,
        List<BillingLedgerView.LedgerRow> sortedRows,
        java.awt.Color ink,
        java.awt.Color muted,
        java.awt.Color border,
        java.awt.Color brandGoldSoft
    ) throws Exception {
        List<String> reviewReasons = collectReviewReasons(sortedRows);
        PdfPTable cards = new PdfPTable(2);
        cards.setWidthPercentage(100);
        cards.setWidths(new float[]{1f, 1f});
        cards.setSpacingAfter(12f);

        cards.addCell(BillingLedgerExportSupport.buildInfoCard("Review Summary", new String[][] {
            {"Risk Level", firstNonBlank(summary.getRiskLevel(), "-")},
            {"Management Review", String.valueOf(summary.getReviewCount())},
            {"Age Review", String.valueOf(summary.getAgeReviewCount())},
            {"OT Review", String.valueOf(summary.getOvertimeReviewCount())}
        }, ink, muted, border));

        if (!reviewReasons.isEmpty()) {
            List<String[]> rows = new ArrayList<>();
            for (String reason : reviewReasons) {
                rows.add(new String[] {"Review", reason});
            }
            cards.addCell(BillingLedgerExportSupport.buildInfoCard("Review Reasons", rows.toArray(new String[0][]), ink, muted, border));
        } else {
            PdfPCell noteCard = new PdfPCell();
            noteCard.setPadding(14f);
            noteCard.setBorderColor(border);
            noteCard.setBackgroundColor(brandGoldSoft);
            Paragraph title = new Paragraph("REVIEW REASONS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, muted));
            title.setSpacingAfter(10f);
            noteCard.addElement(title);
            noteCard.addElement(new Paragraph("No manual review reasons were recorded for the visible invoices.", FontFactory.getFont(FontFactory.HELVETICA, 10, ink)));
            cards.addCell(noteCard);
        }

        document.add(cards);
    }

    private static List<String> collectReviewReasons(List<BillingLedgerView.LedgerRow> rows) {
        List<String> reasons = new ArrayList<>();
        for (BillingLedgerView.LedgerRow row : rows) {
            if (!row.isManagementReviewRecommended()) {
                continue;
            }
            String reviewReason = firstNonBlank(row.getReviewReason());
            if (reviewReason != null && !reasons.contains(reviewReason)) {
                reasons.add(reviewReason);
            }
        }
        return reasons;
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
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