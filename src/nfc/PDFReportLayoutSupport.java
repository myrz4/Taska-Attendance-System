package nfc;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.draw.LineSeparator;

import java.awt.Color;
import java.net.URL;

@SuppressWarnings("unused")
final class PDFReportLayoutSupport {
    private PDFReportLayoutSupport() {}

    static ReportPalette defaultPalette() {
        return new ReportPalette(
            new Color(255, 203, 60),
            new Color(255, 245, 217),
            new Color(29, 42, 58),
            new Color(120, 132, 150),
            new Color(234, 248, 239),
            new Color(13, 122, 56),
            new Color(255, 240, 238),
            new Color(197, 59, 42),
            new Color(228, 234, 242),
            new Color(248, 250, 252)
        );
    }

    static void addHeader(
        Document document,
        String logoPath,
        StudentInfo info,
        String month,
        String year,
        ReportPalette palette
    ) throws Exception {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1.2f, 6.2f});
        header.setSpacingAfter(14f);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setBackgroundColor(palette.brandGoldSoft);
        logoCell.setPadding(16f);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Image logo = loadLogo(logoPath);
        if (logo != null) {
            logo.scaleToFit(54, 54);
            logoCell.addElement(logo);
        }

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setBackgroundColor(palette.brandGoldSoft);
        titleCell.setPadding(16f);

        Paragraph eyebrow = new Paragraph("MONTHLY STUDENT REPORT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, palette.muted));
        eyebrow.setSpacingAfter(6f);
        titleCell.addElement(eyebrow);

        Paragraph title = new Paragraph("Taska Zurah Attendance Summary", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, palette.ink));
        title.setSpacingAfter(4f);
        titleCell.addElement(title);

        Paragraph subtitle = new Paragraph(safe(info.childName) + "  |  " + month.toUpperCase() + " " + year,
            FontFactory.getFont(FontFactory.HELVETICA, 11, palette.muted));
        titleCell.addElement(subtitle);

        header.addCell(logoCell);
        header.addCell(titleCell);
        document.add(header);

        PdfPTable badgeTable = new PdfPTable(1);
        badgeTable.setWidthPercentage(100);
        badgeTable.setSpacingAfter(8f);

        PdfPCell badgeCell = new PdfPCell(new Phrase("Attendance Report", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(154, 103, 0))));
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeCell.setBackgroundColor(palette.brandGold);
        badgeCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        badgeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        badgeCell.setPadding(10f);

        badgeTable.addCell(badgeCell);
        document.add(badgeTable);
        document.add(Chunk.NEWLINE);
    }

    static void addInfoCard(Document document, StudentInfo info, String month, String year, ReportPalette palette) throws Exception {
        PdfPTable infoCard = new PdfPTable(4);
        infoCard.setWidthPercentage(100);
        infoCard.setWidths(new float[]{1.5f, 2.5f, 1.5f, 2.5f});
        infoCard.setSpacingAfter(14f);

        addInfoRow(infoCard, "Child ID", safe(info.childId), "Month", month.toUpperCase(), palette);
        addInfoRow(infoCard, "Name", safe(info.childName), "Year", safe(year), palette);
        addInfoRow(infoCard, "Parent Name", safe(info.parentName), "Contact Number", safe(info.parentContact), palette);

        document.add(infoCard);
    }

    static void addMetricsRow(
        Document document,
        String attendancePercent,
        String performance,
        PDFReportAttendanceSectionSupport.SummaryCounts counts,
        ReportPalette palette
    ) throws Exception {
        PdfPTable metrics = new PdfPTable(4);
        metrics.setWidthPercentage(100);
        metrics.setWidths(new float[]{1.35f, 1.1f, 1.1f, 1.45f});
        metrics.setSpacingAfter(16f);

        metrics.addCell(metricCell("Attendance", safe(attendancePercent), palette.brandGoldSoft, new Color(154, 103, 0), palette.muted));
        metrics.addCell(metricCell("Present Days", String.valueOf(counts.presentCount), palette.successBg, palette.successText, palette.muted));
        metrics.addCell(metricCell("Absent Days", String.valueOf(counts.absentCount), palette.dangerBg, palette.dangerText, palette.muted));

        Color performanceBg = "Good".equalsIgnoreCase(performance) ? palette.successBg : palette.dangerBg;
        Color performanceText = "Good".equalsIgnoreCase(performance) ? palette.successText : palette.dangerText;
        metrics.addCell(metricCell("Performance", safe(performance).toUpperCase(), performanceBg, performanceText, palette.muted));

        document.add(metrics);
    }

    static void addFooter(Document document, ReportPalette palette) throws Exception {
        Paragraph spacer = new Paragraph();
        spacer.setSpacingBefore(26f);
        document.add(spacer);

        LineSeparator line = new LineSeparator();
        line.setLineWidth(0.8f);
        line.setPercentage(100f);
        line.setLineColor(palette.border);
        document.add(line);

        Paragraph footer = new Paragraph("Generated by Taska Zurah Attendance System", FontFactory.getFont(FontFactory.HELVETICA, 10, palette.muted));
        footer.setSpacingBefore(8f);
        footer.setAlignment(Element.ALIGN_LEFT);
        document.add(footer);

        Paragraph sign = new Paragraph("Assigned Teacher", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, palette.ink));
        sign.setAlignment(Element.ALIGN_RIGHT);
        sign.setSpacingBefore(18f);
        document.add(sign);
    }

    private static void addInfoRow(PdfPTable table, String leftLabel, String leftValue, String rightLabel, String rightValue, ReportPalette palette) {
        table.addCell(infoLabelCell(leftLabel, palette));
        table.addCell(infoValueCell(leftValue, palette));
        table.addCell(infoLabelCell(rightLabel, palette));
        table.addCell(infoValueCell(rightValue, palette));
    }

    private static PdfPCell infoLabelCell(String value, ReportPalette palette) {
        PdfPCell cell = new PdfPCell(new Phrase(value.toUpperCase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, palette.muted)));
        cell.setPadding(10f);
        cell.setBorderColor(palette.border);
        cell.setBackgroundColor(Color.WHITE);
        return cell;
    }

    private static PdfPCell infoValueCell(String value, ReportPalette palette) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(value), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, palette.ink)));
        cell.setPadding(10f);
        cell.setBorderColor(palette.border);
        cell.setBackgroundColor(Color.WHITE);
        return cell;
    }

    private static PdfPCell metricCell(String label, String value, Color bgColor, Color valueColor, Color muted) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bgColor);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(12f);

        Paragraph labelParagraph = new Paragraph(label.toUpperCase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, muted));
        labelParagraph.setSpacingAfter(6f);
        cell.addElement(labelParagraph);

        Paragraph valueParagraph = new Paragraph(safe(value), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, valueColor));
        cell.addElement(valueParagraph);
        return cell;
    }

    private static Image loadLogo(String logoPath) {
        try {
            URL resource = PDFReport.class.getResource("/nfc/logo.png");
            if (resource != null) {
                return Image.getInstance(resource);
            }
            return Image.getInstance(logoPath);
        } catch (java.io.IOException | com.lowagie.text.BadElementException ignored) {
            return null;
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    static final class ReportPalette {
        final Color brandGold;
        final Color brandGoldSoft;
        final Color ink;
        final Color muted;
        final Color successBg;
        final Color successText;
        final Color dangerBg;
        final Color dangerText;
        final Color border;
        final Color tableAlt;

        ReportPalette(
            Color brandGold,
            Color brandGoldSoft,
            Color ink,
            Color muted,
            Color successBg,
            Color successText,
            Color dangerBg,
            Color dangerText,
            Color border,
            Color tableAlt
        ) {
            this.brandGold = brandGold;
            this.brandGoldSoft = brandGoldSoft;
            this.ink = ink;
            this.muted = muted;
            this.successBg = successBg;
            this.successText = successText;
            this.dangerBg = dangerBg;
            this.dangerText = dangerText;
            this.border = border;
            this.tableAlt = tableAlt;
        }
    }
}