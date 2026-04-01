package nfc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.draw.LineSeparator;

@SuppressWarnings("unused")
final class BillingLedgerInvoiceLayoutSupport {
    private BillingLedgerInvoiceLayoutSupport() {}

    static void addInvoicePdfHeader(
        Document document,
        BillingLedgerView.LedgerRow row,
        Class<?> resourceAnchor,
        java.awt.Color brandGold,
        java.awt.Color brandGoldSoft,
        java.awt.Color ink,
        java.awt.Color muted,
        java.awt.Color successBg,
        java.awt.Color successText,
        java.awt.Color dangerBg,
        java.awt.Color dangerText
    ) throws Exception {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1.15f, 5.85f});
        header.setSpacingAfter(10f);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setBackgroundColor(brandGoldSoft);
        logoCell.setPadding(16f);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        try {
            java.net.URL resource = resourceAnchor.getResource("/nfc/logo.png");
            if (resource != null) {
                Image logo = Image.getInstance(resource);
                logo.scaleToFit(52, 52);
                logoCell.addElement(logo);
            }
        } catch (DocumentException | java.io.IOException ignored) {
            // Logo is optional in the PDF export.
        }

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setBackgroundColor(brandGoldSoft);
        titleCell.setPadding(16f);

        Paragraph eyebrow = new Paragraph("BILLING LEDGER EXPORT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, muted));
        eyebrow.setSpacingAfter(6f);
        titleCell.addElement(eyebrow);

        String title = row.isPaid() ? "Taska Zurah Payment Receipt" : "Taska Zurah Billing Invoice";
        Paragraph heading = new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, ink));
        heading.setSpacingAfter(4f);
        titleCell.addElement(heading);

        Paragraph subtitle = new Paragraph(
            nullSafe(row.getParentName()) + "  |  " + nullSafe(row.getChildDisplayName()) + "  |  " + nullSafe(row.getPeriod()),
            FontFactory.getFont(FontFactory.HELVETICA, 11, muted)
        );
        titleCell.addElement(subtitle);

        header.addCell(logoCell);
        header.addCell(titleCell);
        document.add(header);

        PdfPTable statusTable = new PdfPTable(2);
        statusTable.setWidthPercentage(100);
        statusTable.setWidths(new float[]{3.8f, 1.4f});
        statusTable.setSpacingAfter(12f);

        PdfPCell metaCell = new PdfPCell();
        metaCell.setBorder(Rectangle.NO_BORDER);
        metaCell.setPadding(10f);
        metaCell.setBackgroundColor(java.awt.Color.WHITE);
        metaCell.addElement(new Paragraph(
            "Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))
                + "\nInvoice ID: " + nullSafe(row.getInvoiceId()),
            FontFactory.getFont(FontFactory.HELVETICA, 10, muted)
        ));

        PdfPCell badgeCell = new PdfPCell(new Phrase("Status: " + nullSafe(row.getStatus()).toUpperCase(Locale.ROOT), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10,
            row.isPaid() ? successText : (row.isOverdue() ? dangerText : new java.awt.Color(154, 103, 0)))));
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeCell.setPadding(10f);
        badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        badgeCell.setBackgroundColor(row.isPaid() ? successBg : (row.isOverdue() ? dangerBg : brandGold));

        statusTable.addCell(metaCell);
        statusTable.addCell(badgeCell);
        document.add(statusTable);
    }

    static void addInvoicePdfFooter(Document document, BillingLedgerView.LedgerRow row, java.awt.Color muted, java.awt.Color ink) throws Exception {
        LineSeparator line = new LineSeparator();
        line.setPercentage(100f);
        line.setLineWidth(0.8f);
        line.setLineColor(new java.awt.Color(228, 234, 242));
        document.add(Chunk.NEWLINE);
        document.add(line);

        Paragraph footer = new Paragraph(
            row.isPaid()
                ? "Generated from the Taska Zurah billing ledger as a payment receipt record."
                : "Generated from the Taska Zurah billing ledger as an invoice snapshot for collection and follow-up.",
            FontFactory.getFont(FontFactory.HELVETICA, 10, muted)
        );
        footer.setSpacingBefore(8f);
        document.add(footer);

        Paragraph sign = new Paragraph("Taska Zurah Admin Billing Ledger", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink));
        sign.setAlignment(Element.ALIGN_RIGHT);
        sign.setSpacingBefore(16f);
        document.add(sign);
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}