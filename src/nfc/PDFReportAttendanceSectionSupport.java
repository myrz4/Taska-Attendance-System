package nfc;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@SuppressWarnings("all")
final class PDFReportAttendanceSectionSupport {
    private PDFReportAttendanceSectionSupport() {}

    @SuppressWarnings("unused")
    static SummaryCounts countAttendance(List<AttendanceRow> days) {
        int presentCount = 0;
        int absentCount = 0;
        for (AttendanceRow row : days) {
            if ("attend".equalsIgnoreCase(row.getStatus())) {
                presentCount++;
            } else {
                absentCount++;
            }
        }
        return new SummaryCounts(presentCount, absentCount);
    }

    @SuppressWarnings("unused")
    static void addAttendanceTable(Document document, List<AttendanceRow> days, PDFReportLayoutSupport.ReportPalette palette) throws Exception {
        Paragraph sectionTitle = new Paragraph("Attendance Timeline", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, palette.ink));
        sectionTitle.setSpacingAfter(8f);
        document.add(sectionTitle);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.8f, 1.2f, 1.35f, 1.35f, 2.3f});

        String[] headers = {"Date", "Status", "Check-In", "Check-Out", "Reason"};
        for (String headerLabel : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(headerLabel, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, palette.ink)));
            cell.setBackgroundColor(palette.brandGold);
            cell.setBorderColor(palette.border);
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
            Color rowBg = index % 2 == 0 ? Color.WHITE : palette.tableAlt;
            boolean absent = "absence".equalsIgnoreCase(row.getStatus());

            table.addCell(bodyCell(row.getDate() != null ? row.getDate().format(dateFormat) : "-", rowBg, palette.border, Element.ALIGN_LEFT, palette.ink, false));
            table.addCell(bodyCell(row.getStatus() == null ? "-" : row.getStatus().toUpperCase(), absent ? palette.dangerBg : palette.successBg, palette.border, Element.ALIGN_CENTER, absent ? palette.dangerText : palette.successText, true));
            table.addCell(bodyCell(formatReportTime(row.getCheckInTime(), inputFormat, displayFormat), rowBg, palette.border, Element.ALIGN_CENTER, palette.ink, false));
            table.addCell(bodyCell(formatReportTime(row.getCheckOutTime(), inputFormat, displayFormat), rowBg, palette.border, Element.ALIGN_CENTER, palette.ink, false));
            table.addCell(bodyCell(safe(row.getReason()), rowBg, palette.border, Element.ALIGN_LEFT, absent ? palette.dangerText : palette.ink, false));
        }

        if (days.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No attendance records were found for this month.", FontFactory.getFont(FontFactory.HELVETICA, 11, palette.ink)));
            empty.setColspan(5);
            empty.setPadding(14f);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setBorderColor(palette.border);
            table.addCell(empty);
        }

        document.add(table);
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
        } catch (java.time.format.DateTimeParseException ignored) {
            return raw;
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    @SuppressWarnings("unused")
    static final class SummaryCounts {
        @SuppressWarnings("unused")
        final int presentCount;
        @SuppressWarnings("unused")
        final int absentCount;

        SummaryCounts(int presentCount, int absentCount) {
            this.presentCount = presentCount;
            this.absentCount = absentCount;
        }
    }
}