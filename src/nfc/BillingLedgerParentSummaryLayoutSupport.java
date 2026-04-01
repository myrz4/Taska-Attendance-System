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
final class BillingLedgerParentSummaryLayoutSupport {
    private BillingLedgerParentSummaryLayoutSupport() {}

    static void addParentSummaryPdfHeader(
        Document document,
        BillingLedgerView.ParentSummaryRow summary,
        Class<?> resourceAnchor,
        String childSummary,
        String filterDescription,
        java.awt.Color brandGoldSoft,
        java.awt.Color ink,
        java.awt.Color muted,
        java.awt.Color successBg,
        java.awt.Color dangerBg,
        java.awt.Color successText,
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
            // Optional logo.
        }

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setBackgroundColor(brandGoldSoft);
        titleCell.setPadding(16f);

        Paragraph eyebrow = new Paragraph("BILLING LEDGER EXPORT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, muted));
        eyebrow.setSpacingAfter(6f);
        titleCell.addElement(eyebrow);

        Paragraph heading = new Paragraph("Taska Zurah Parent Billing Summary", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, ink));
        heading.setSpacingAfter(4f);
        titleCell.addElement(heading);

        Paragraph subtitle = new Paragraph(
            nullSafe(summary.getParentName()) + "  |  " + nullSafe(childSummary),
            FontFactory.getFont(FontFactory.HELVETICA, 11, muted)
        );
        titleCell.addElement(subtitle);

        header.addCell(logoCell);
        header.addCell(titleCell);
        document.add(header);

        PdfPTable statusTable = new PdfPTable(2);
        statusTable.setWidthPercentage(100);
        statusTable.setWidths(new float[]{3.7f, 1.5f});
        statusTable.setSpacingAfter(12f);

        PdfPCell metaCell = new PdfPCell();
        metaCell.setBorder(Rectangle.NO_BORDER);
        metaCell.setPadding(10f);
        metaCell.setBackgroundColor(java.awt.Color.WHITE);
        metaCell.addElement(new Paragraph(
            "Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))
                + "\nParent ID: " + nullSafe(summary.getParentId())
                + "\nFilter Scope: " + nullSafe(filterDescription),
            FontFactory.getFont(FontFactory.HELVETICA, 10, muted)
        ));

        String risk = firstNonBlank(summary.getRiskLevel(), "Clear");
        boolean critical = "Critical".equalsIgnoreCase(risk);
        PdfPCell badgeCell = new PdfPCell(new Phrase(
            "Risk: " + risk.toUpperCase(Locale.ROOT),
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, critical ? dangerText : successText)
        ));
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeCell.setPadding(10f);
        badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        badgeCell.setBackgroundColor(critical ? dangerBg : successBg);

        statusTable.addCell(metaCell);
        statusTable.addCell(badgeCell);
        document.add(statusTable);
    }

    static void addParentSummaryPdfFooter(Document document, BillingLedgerView.ParentSummaryRow summary, java.awt.Color muted, java.awt.Color ink) throws Exception {
        LineSeparator line = new LineSeparator();
        line.setPercentage(100f);
        line.setLineWidth(0.8f);
        line.setLineColor(new java.awt.Color(228, 234, 242));
        document.add(Chunk.NEWLINE);
        document.add(line);

        Paragraph footer = new Paragraph(
            "Generated from the Taska Zurah billing ledger as a parent-level billing summary for follow-up and review.",
            FontFactory.getFont(FontFactory.HELVETICA, 10, muted)
        );
        footer.setSpacingBefore(8f);
        document.add(footer);

        Paragraph sign = new Paragraph(firstNonBlank(summary.getParentName(), "Taska Zurah Parent Summary"), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink));
        sign.setAlignment(Element.ALIGN_RIGHT);
        sign.setSpacingBefore(16f);
        document.add(sign);
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