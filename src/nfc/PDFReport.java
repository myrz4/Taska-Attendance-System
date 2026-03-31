package nfc;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;

import java.awt.Color;
import java.io.FileOutputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PDFReport {
    public static void generateStudentMonthlyReport(
            String filePath,
            String logoPath,
            StudentInfo info,
            List<AttendanceRow> days,
            String month,
            String year,
            String attendancePercent,
            String performance
    ) throws Exception {
        Document document = new Document(PageSize.A4, 36, 36, 42, 40);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(filePath);
            PdfWriter.getInstance(document, out);
            document.open();

            Color brandGold = new Color(255, 203, 60);
            Color brandGoldSoft = new Color(255, 245, 217);
            Color ink = new Color(29, 42, 58);
            Color muted = new Color(120, 132, 150);
            Color successBg = new Color(234, 248, 239);
            Color successText = new Color(13, 122, 56);
            Color dangerBg = new Color(255, 240, 238);
            Color dangerText = new Color(197, 59, 42);
            Color border = new Color(228, 234, 242);
            Color tableAlt = new Color(248, 250, 252);

            int presentCount = 0;
            int absentCount = 0;
            for (AttendanceRow row : days) {
                if ("attend".equalsIgnoreCase(row.getStatus())) {
                    presentCount++;
                } else {
                    absentCount++;
                }
            }

            addHeader(document, logoPath, info, month, year, brandGold, brandGoldSoft, ink, muted);
            addInfoCard(document, info, month, year, ink, muted, border);
            addMetricsRow(document, attendancePercent, performance, presentCount, absentCount, brandGoldSoft, successBg, successText, dangerBg, dangerText, muted);
            addAttendanceTable(document, days, brandGold, ink, border, tableAlt, successBg, successText, dangerBg, dangerText);
            addFooter(document, muted, ink);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    private static void addHeader(
            Document document,
            String logoPath,
            StudentInfo info,
            String month,
            String year,
            Color brandGold,
            Color brandGoldSoft,
            Color ink,
            Color muted
    ) throws Exception {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1.2f, 6.2f});
        header.setSpacingAfter(14f);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setBackgroundColor(brandGoldSoft);
        logoCell.setPadding(16f);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Image logo = loadLogo(logoPath);
        if (logo != null) {
            logo.scaleToFit(54, 54);
            logoCell.addElement(logo);
        }

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setBackgroundColor(brandGoldSoft);
        titleCell.setPadding(16f);

        Paragraph eyebrow = new Paragraph("MONTHLY STUDENT REPORT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, muted));
        eyebrow.setSpacingAfter(6f);
        titleCell.addElement(eyebrow);

        Paragraph title = new Paragraph("Taska Zurah Attendance Summary", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, ink));
        title.setSpacingAfter(4f);
        titleCell.addElement(title);

        Paragraph subtitle = new Paragraph(safe(info.childName) + "  |  " + month.toUpperCase() + " " + year,
                FontFactory.getFont(FontFactory.HELVETICA, 11, muted));
        titleCell.addElement(subtitle);

        header.addCell(logoCell);
        header.addCell(titleCell);
        document.add(header);

        PdfPTable badgeTable = new PdfPTable(1);
        badgeTable.setWidthPercentage(100);
        badgeTable.setSpacingAfter(8f);

        PdfPCell badgeCell = new PdfPCell(new Phrase("Attendance Report", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(154, 103, 0))));
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeCell.setBackgroundColor(brandGold);
        badgeCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        badgeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        badgeCell.setPadding(10f);

        badgeTable.addCell(badgeCell);
        document.add(badgeTable);
        document.add(Chunk.NEWLINE);
    }

    private static void addInfoCard(Document document, StudentInfo info, String month, String year, Color ink, Color muted, Color border) throws Exception {
        PdfPTable infoCard = new PdfPTable(4);
        infoCard.setWidthPercentage(100);
        infoCard.setWidths(new float[]{1.5f, 2.5f, 1.5f, 2.5f});
        infoCard.setSpacingAfter(14f);

        addInfoRow(infoCard, "Child ID", safe(info.childId), "Month", month.toUpperCase(), ink, muted, border);
        addInfoRow(infoCard, "Name", safe(info.childName), "Year", safe(year), ink, muted, border);
        addInfoRow(infoCard, "Parent Name", safe(info.parentName), "Contact Number", safe(info.parentContact), ink, muted, border);

        document.add(infoCard);
    }

    private static void addMetricsRow(
            Document document,
            String attendancePercent,
            String performance,
            int presentCount,
            int absentCount,
            Color brandGoldSoft,
            Color successBg,
            Color successText,
            Color dangerBg,
            Color dangerText,
            Color muted
    ) throws Exception {
        PdfPTable metrics = new PdfPTable(4);
        metrics.setWidthPercentage(100);
        metrics.setWidths(new float[]{1.35f, 1.1f, 1.1f, 1.45f});
        metrics.setSpacingAfter(16f);

        metrics.addCell(metricCell("Attendance", safe(attendancePercent), brandGoldSoft, new Color(154, 103, 0), muted));
        metrics.addCell(metricCell("Present Days", String.valueOf(presentCount), successBg, successText, muted));
        metrics.addCell(metricCell("Absent Days", String.valueOf(absentCount), dangerBg, dangerText, muted));

        Color performanceBg = "Good".equalsIgnoreCase(performance) ? successBg : dangerBg;
        Color performanceText = "Good".equalsIgnoreCase(performance) ? successText : dangerText;
        metrics.addCell(metricCell("Performance", safe(performance).toUpperCase(), performanceBg, performanceText, muted));

        document.add(metrics);
    }

    private static void addAttendanceTable(
            Document document,
            List<AttendanceRow> days,
            Color brandGold,
            Color ink,
            Color border,
            Color tableAlt,
            Color successBg,
            Color successText,
            Color dangerBg,
            Color dangerText
    ) throws Exception {
        Paragraph sectionTitle = new Paragraph("Attendance Timeline", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, ink));
        sectionTitle.setSpacingAfter(8f);
        document.add(sectionTitle);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.8f, 1.2f, 1.35f, 1.35f, 2.3f});

        String[] headers = {"Date", "Status", "Check-In", "Check-Out", "Reason"};
        for (String headerLabel : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(headerLabel, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink)));
            cell.setBackgroundColor(brandGold);
            cell.setBorderColor(border);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(8f);
            table.addCell(cell);
        }

        DateTimeFormatter inputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter displayFormat = DateTimeFormatter.ofPattern("h:mm a");
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy");

        for (int index = 0; index < days.size(); index++) {
            AttendanceRow row = days.get(index);
            Color rowBg = index % 2 == 0 ? Color.WHITE : tableAlt;
            boolean absent = "absence".equalsIgnoreCase(row.getStatus());

            table.addCell(bodyCell(row.getDate() != null ? row.getDate().format(dateFormat) : "-", rowBg, border, Element.ALIGN_LEFT, ink, false));
            table.addCell(bodyCell(row.getStatus() == null ? "-" : row.getStatus().toUpperCase(), absent ? dangerBg : successBg, border, Element.ALIGN_CENTER, absent ? dangerText : successText, true));
            table.addCell(bodyCell(formatReportTime(row.getCheckInTime(), inputFormat, displayFormat), rowBg, border, Element.ALIGN_CENTER, ink, false));
            table.addCell(bodyCell(formatReportTime(row.getCheckOutTime(), inputFormat, displayFormat), rowBg, border, Element.ALIGN_CENTER, ink, false));
            table.addCell(bodyCell(safe(row.getReason()), rowBg, border, Element.ALIGN_LEFT, absent ? dangerText : ink, false));
        }

        if (days.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No attendance records were found for this month.", FontFactory.getFont(FontFactory.HELVETICA, 11, ink)));
            empty.setColspan(5);
            empty.setPadding(14f);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setBorderColor(border);
            table.addCell(empty);
        }

        document.add(table);
    }

    private static void addFooter(Document document, Color muted, Color ink) throws Exception {
        Paragraph spacer = new Paragraph();
        spacer.setSpacingBefore(26f);
        document.add(spacer);

        LineSeparator line = new LineSeparator();
        line.setLineWidth(0.8f);
        line.setPercentage(100f);
        line.setLineColor(new Color(228, 234, 242));
        document.add(line);

        Paragraph footer = new Paragraph("Generated by Taska Zurah Attendance System", FontFactory.getFont(FontFactory.HELVETICA, 10, muted));
        footer.setSpacingBefore(8f);
        footer.setAlignment(Element.ALIGN_LEFT);
        document.add(footer);

        Paragraph sign = new Paragraph("Assigned Teacher", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, ink));
        sign.setAlignment(Element.ALIGN_RIGHT);
        sign.setSpacingBefore(18f);
        document.add(sign);
    }

    private static void addInfoRow(PdfPTable table, String leftLabel, String leftValue, String rightLabel, String rightValue, Color ink, Color muted, Color border) {
        table.addCell(infoLabelCell(leftLabel, muted, border));
        table.addCell(infoValueCell(leftValue, ink, border));
        table.addCell(infoLabelCell(rightLabel, muted, border));
        table.addCell(infoValueCell(rightValue, ink, border));
    }

    private static PdfPCell infoLabelCell(String value, Color muted, Color border) {
        PdfPCell cell = new PdfPCell(new Phrase(value.toUpperCase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, muted)));
        cell.setPadding(10f);
        cell.setBorderColor(border);
        cell.setBackgroundColor(Color.WHITE);
        return cell;
    }

    private static PdfPCell infoValueCell(String value, Color ink, Color border) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(value), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, ink)));
        cell.setPadding(10f);
        cell.setBorderColor(border);
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

    private static PdfPCell bodyCell(String text, Color bgColor, Color border, int alignment, Color textColor, boolean bold) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 10, bold ? Font.BOLD : Font.NORMAL, textColor);
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), font));
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(border);
        cell.setPadding(8f);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private static String formatReportTime(String raw, DateTimeFormatter inputFormat, DateTimeFormatter outputFormat) {
        if (raw == null || raw.isBlank() || "-".equals(raw)) {
            return "-";
        }
        try {
            return LocalDateTime.parse(raw, inputFormat).format(outputFormat);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private static Image loadLogo(String logoPath) {
        try {
            URL resource = PDFReport.class.getResource("/nfc/logo.png");
            if (resource != null) {
                return Image.getInstance(resource);
            }
            return Image.getInstance(logoPath);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
