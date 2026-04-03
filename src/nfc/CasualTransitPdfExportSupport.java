package nfc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

public final class CasualTransitPdfExportSupport {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private CasualTransitPdfExportSupport() {
    }

    static {
        if (keepAnalyzerAnchors()) {
            try {
                writeAuditPdf(null, null, List.<CasualTransitView.AuditEntry>of(), "");
                writeReceiptPdf(null, null);
                writeSummaryPdf(null, List.<CasualTransitView.VisitRow>of(), "");
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static boolean keepAnalyzerAnchors() {
        return Boolean.getBoolean("taska.keepAnalyzerAnchors");
    }

    public static void writeAuditPdf(File file, CasualTransitView.VisitRow row, List<CasualTransitView.AuditEntry> exportEntries, String filterSummary) throws IOException {
        Document document = new Document(PageSize.A4, 34, 34, 38, 34);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            PdfWriter.getInstance(document, out);
            document.open();

            java.awt.Color brandGoldSoft = new java.awt.Color(255, 245, 217);
            java.awt.Color ink = new java.awt.Color(29, 42, 58);
            java.awt.Color muted = new java.awt.Color(120, 132, 150);
            java.awt.Color border = new java.awt.Color(228, 234, 242);

            PdfPTable header = new PdfPTable(1);
            header.setWidthPercentage(100);
            PdfPCell titleCell = new PdfPCell();
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleCell.setBackgroundColor(brandGoldSoft);
            titleCell.setPadding(14f);
            titleCell.addElement(new Paragraph("CASUAL TRANSIT AUDIT HISTORY", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, muted)));
            titleCell.addElement(new Paragraph("Taska Zurah Visit Audit Report", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, ink)));
            titleCell.addElement(new Paragraph(
                "Generated: " + LocalDateTime.now().format(DATE_TIME_FORMAT)
                    + " | Child: " + row.childName()
                    + " | Visit ID: " + row.visitId(),
                FontFactory.getFont(FontFactory.HELVETICA, 10, muted)
            ));
            titleCell.addElement(new Paragraph(
                "Filters: " + filterSummary + " | Visible entries: " + exportEntries.size(),
                FontFactory.getFont(FontFactory.HELVETICA, 10, muted)
            ));
            header.addCell(titleCell);
            document.add(header);

            PdfPTable visitTable = new PdfPTable(2);
            visitTable.setSpacingBefore(12f);
            visitTable.setSpacingAfter(12f);
            visitTable.setWidthPercentage(100);
            visitTable.setWidths(new float[]{1.2f, 2.4f});
            addReceiptRow(visitTable, "Status", row.statusLabel(), ink, muted, border);
            addReceiptRow(visitTable, "Payment", row.paymentStatusLabel(), ink, muted, border);
            addReceiptRow(visitTable, "Guardian", row.guardianName(), ink, muted, border);
            addReceiptRow(visitTable, "Phone", row.guardianPhone().isBlank() ? "-" : row.guardianPhone(), ink, muted, border);
            addReceiptRow(visitTable, "Check In", row.checkInLabel(), ink, muted, border);
            addReceiptRow(visitTable, "Check Out", row.checkOutLabel(), ink, muted, border);
            addReceiptRow(visitTable, "Receipt", row.receiptNo(), ink, muted, border);
            document.add(visitTable);

            for (CasualTransitView.AuditEntry entry : exportEntries) {
                Paragraph title = new Paragraph(entry.exportTitle(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, ink));
                title.setSpacingBefore(8f);
                title.setSpacingAfter(4f);
                document.add(title);
                document.add(new Paragraph(entry.render(), FontFactory.getFont(FontFactory.HELVETICA, 10, ink)));
            }
        } catch (DocumentException error) {
            throw new IOException(error);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    public static void writeReceiptPdf(File file, CasualTransitView.VisitRow row) throws IOException {
        Document document = new Document(PageSize.A4, 34, 34, 38, 34);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            PdfWriter.getInstance(document, out);
            document.open();

            java.awt.Color brandGoldSoft = new java.awt.Color(255, 245, 217);
            java.awt.Color ink = new java.awt.Color(29, 42, 58);
            java.awt.Color muted = new java.awt.Color(120, 132, 150);
            java.awt.Color border = new java.awt.Color(228, 234, 242);
            java.awt.Color successBg = new java.awt.Color(234, 248, 239);
            java.awt.Color successText = new java.awt.Color(13, 122, 56);

            PdfPTable header = new PdfPTable(1);
            header.setWidthPercentage(100);
            PdfPCell titleCell = new PdfPCell();
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleCell.setBackgroundColor(brandGoldSoft);
            titleCell.setPadding(16f);
            titleCell.addElement(new Paragraph("CASUAL TRANSIT RECEIPT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, muted)));
            titleCell.addElement(new Paragraph("Taska Zurah Walk-In Receipt", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, ink)));
            titleCell.addElement(new Paragraph("Generated: " + LocalDateTime.now().format(DATE_TIME_FORMAT), FontFactory.getFont(FontFactory.HELVETICA, 10, muted)));
            header.addCell(titleCell);
            document.add(header);

            Paragraph paidBadge = new Paragraph("PAID", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, successText));
            PdfPCell badgeCell = new PdfPCell(paidBadge);
            badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            badgeCell.setBackgroundColor(successBg);
            badgeCell.setBorder(Rectangle.NO_BORDER);
            badgeCell.setPadding(8f);
            PdfPTable badgeTable = new PdfPTable(1);
            badgeTable.setSpacingBefore(10f);
            badgeTable.setSpacingAfter(10f);
            badgeTable.setWidthPercentage(28f);
            badgeTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            badgeTable.addCell(badgeCell);
            document.add(badgeTable);

            PdfPTable details = new PdfPTable(2);
            details.setWidthPercentage(100);
            details.setWidths(new float[]{1.2f, 2.4f});
            details.setSpacingBefore(8f);
            details.setSpacingAfter(12f);
            addReceiptRow(details, "Receipt No", row.receiptNo(), ink, muted, border);
            addReceiptRow(details, "Visit ID", row.visitId(), ink, muted, border);
            addReceiptRow(details, "Child", row.childName(), ink, muted, border);
            addReceiptRow(details, "Guardian", row.guardianName(), ink, muted, border);
            addReceiptRow(details, "Phone", row.guardianPhone().isBlank() ? "-" : row.guardianPhone(), ink, muted, border);
            addReceiptRow(details, "Relationship", row.guardianRelationship().isBlank() ? "-" : row.guardianRelationship(), ink, muted, border);
            addReceiptRow(details, "Check In", row.checkInLabel(), ink, muted, border);
            addReceiptRow(details, "Check Out", row.checkOutLabel(), ink, muted, border);
            addReceiptRow(details, "Payment Status", row.paymentStatusLabel(), ink, muted, border);
            addReceiptRow(details, "Payment Method", row.paymentMethod().isBlank() ? "-" : row.paymentMethod(), ink, muted, border);
            addReceiptRow(details, "Amount Paid", row.amountLabel(), ink, muted, border);
            document.add(details);

            Paragraph notesTitle = new Paragraph("Notes", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, ink));
            notesTitle.setSpacingAfter(6f);
            document.add(notesTitle);
            document.add(new Paragraph(row.notes().isBlank() ? "-" : row.notes(), FontFactory.getFont(FontFactory.HELVETICA, 10, ink)));
        } catch (DocumentException error) {
            throw new IOException(error);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    public static void writeSummaryPdf(File file, List<CasualTransitView.VisitRow> exportRows, String filterSummary) throws IOException {
        Document document = new Document(PageSize.A4.rotate(), 28, 28, 34, 28);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            PdfWriter.getInstance(document, out);
            document.open();

            java.awt.Color brandGoldSoft = new java.awt.Color(255, 245, 217);
            java.awt.Color ink = new java.awt.Color(29, 42, 58);
            java.awt.Color muted = new java.awt.Color(120, 132, 150);
            java.awt.Color border = new java.awt.Color(228, 234, 242);
            java.awt.Color tableAlt = new java.awt.Color(248, 250, 252);

            long openCount = exportRows.stream().filter(CasualTransitView.VisitRow::isOpen).count();
            long closedCount = exportRows.stream().filter(CasualTransitView.VisitRow::isClosed).count();
            long canceledCount = exportRows.stream().filter(CasualTransitView.VisitRow::isCanceled).count();
            long totalPaidSen = exportRows.stream().mapToLong(CasualTransitView.VisitRow::amountSen).sum();

            PdfPTable header = new PdfPTable(1);
            header.setWidthPercentage(100);
            PdfPCell titleCell = new PdfPCell();
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleCell.setBackgroundColor(brandGoldSoft);
            titleCell.setPadding(14f);
            titleCell.addElement(new Paragraph("CASUAL TRANSIT SUMMARY", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, muted)));
            titleCell.addElement(new Paragraph("Taska Zurah Daily Walk-In Summary", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, ink)));
            titleCell.addElement(new Paragraph(
                "Generated: " + LocalDateTime.now().format(DATE_TIME_FORMAT)
                    + " | Visible visits: " + exportRows.size()
                    + " | Open: " + openCount
                    + " | Closed: " + closedCount
                    + " | Canceled: " + canceledCount
                    + " | Paid: " + formatMoney(totalPaidSen),
                FontFactory.getFont(FontFactory.HELVETICA, 10, muted)
            ));
            titleCell.addElement(new Paragraph(
                "Filters: " + filterSummary,
                FontFactory.getFont(FontFactory.HELVETICA, 10, muted)
            ));
            header.addCell(titleCell);
            document.add(header);

            PdfPTable summaryTable = new PdfPTable(8);
            summaryTable.setSpacingBefore(12f);
            summaryTable.setWidthPercentage(100);
            summaryTable.setWidths(new float[]{1.0f, 1.1f, 1.6f, 1.9f, 1.6f, 1.6f, 1.1f, 1.1f});
            addSummaryHeaderCell(summaryTable, "Status", ink, border, brandGoldSoft);
            addSummaryHeaderCell(summaryTable, "Payment", ink, border, brandGoldSoft);
            addSummaryHeaderCell(summaryTable, "Child", ink, border, brandGoldSoft);
            addSummaryHeaderCell(summaryTable, "Guardian", ink, border, brandGoldSoft);
            addSummaryHeaderCell(summaryTable, "Check In", ink, border, brandGoldSoft);
            addSummaryHeaderCell(summaryTable, "Check Out", ink, border, brandGoldSoft);
            addSummaryHeaderCell(summaryTable, "Amount", ink, border, brandGoldSoft);
            addSummaryHeaderCell(summaryTable, "Receipt", ink, border, brandGoldSoft);

            int index = 0;
            for (CasualTransitView.VisitRow row : exportRows) {
                java.awt.Color background = index % 2 == 0 ? java.awt.Color.WHITE : tableAlt;
                addSummaryBodyCell(summaryTable, row.statusLabel(), ink, border, background, Element.ALIGN_CENTER);
                addSummaryBodyCell(summaryTable, row.paymentStatusLabel(), ink, border, background, Element.ALIGN_CENTER);
                addSummaryBodyCell(summaryTable, row.childName(), ink, border, background, Element.ALIGN_LEFT);
                addSummaryBodyCell(summaryTable, row.guardianSummary().replace('\n', ' '), ink, border, background, Element.ALIGN_LEFT);
                addSummaryBodyCell(summaryTable, row.checkInLabel(), ink, border, background, Element.ALIGN_CENTER);
                addSummaryBodyCell(summaryTable, row.checkOutLabel(), ink, border, background, Element.ALIGN_CENTER);
                addSummaryBodyCell(summaryTable, row.amountLabel(), ink, border, background, Element.ALIGN_RIGHT);
                addSummaryBodyCell(summaryTable, row.receiptNo(), ink, border, background, Element.ALIGN_CENTER);
                index++;
            }
            document.add(summaryTable);
        } catch (DocumentException error) {
            throw new IOException(error);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    private static void addSummaryHeaderCell(PdfPTable table, String text, java.awt.Color ink, java.awt.Color border, java.awt.Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink)));
        cell.setBackgroundColor(background);
        cell.setBorderColor(border);
        cell.setPadding(8f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private static void addSummaryBodyCell(PdfPTable table, String text, java.awt.Color ink, java.awt.Color border, java.awt.Color background, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "-" : text, FontFactory.getFont(FontFactory.HELVETICA, 9, ink)));
        cell.setBackgroundColor(background);
        cell.setBorderColor(border);
        cell.setPadding(7f);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private static void addReceiptRow(PdfPTable table, String label, String value, java.awt.Color ink, java.awt.Color muted, java.awt.Color border) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, muted)));
        labelCell.setBorderColor(border);
        labelCell.setPadding(8f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value == null ? "-" : value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink)));
        valueCell.setBorderColor(border);
        valueCell.setPadding(8f);
        table.addCell(valueCell);
    }

    private static String formatMoney(long sen) {
        return java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ms-MY")).format(sen / 100.0d);
    }
}