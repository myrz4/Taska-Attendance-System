package nfc;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.lowagie.text.BadElementException;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;

import javafx.collections.ObservableList;

@SuppressWarnings("unused")
final class DailyReportPdfSupport {
    private DailyReportPdfSupport() {
    }

    static void generateDailyPdf(Class<?> resourceAnchor, File file, LocalDate date, ObservableList<AttendanceRow> rows)
        throws DocumentException, IOException {
        Document doc = new Document(PageSize.A4, 34, 34, 38, 34);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Color brandGold = new Color(255, 203, 60);
            Color brandGoldSoft = new Color(255, 245, 217);
            Color ink = new Color(29, 42, 58);
            Color muted = new Color(120, 132, 150);
            Color border = new Color(228, 234, 242);
            Color successBg = new Color(234, 248, 239);
            Color successText = new Color(13, 122, 56);
            Color dangerBg = new Color(255, 240, 238);
            Color dangerText = new Color(197, 59, 42);
            Color tableAlt = new Color(248, 250, 252);

            int attendCount = 0;
            int absentCount = 0;
            for (AttendanceRow row : rows) {
                if ("attend".equalsIgnoreCase(row.getStatus())) {
                    attendCount++;
                } else {
                    absentCount++;
                }
            }

            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[]{1.15f, 5.85f});
            header.setSpacingAfter(10f);

            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setBackgroundColor(brandGoldSoft);
            logoCell.setPadding(16f);
            try {
                java.net.URL logoResource = resourceAnchor.getResource("/nfc/logo.png");
                if (logoResource != null) {
                    com.lowagie.text.Image logo = com.lowagie.text.Image.getInstance(logoResource);
                    logo.scaleToFit(84, 84);
                    logoCell.addElement(logo);
                }
            } catch (BadElementException | IOException ignored) {
            }

            PdfPCell titleCell = new PdfPCell();
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleCell.setBackgroundColor(brandGoldSoft);
            titleCell.setPadding(16f);
            Paragraph eyebrow = new Paragraph("DAILY ATTENDANCE EXPORT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, muted));
            eyebrow.setSpacingAfter(6f);
            titleCell.addElement(eyebrow);
            Paragraph title = new Paragraph("Taska Zurah Daily Attendance Report", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, ink));
            title.setSpacingAfter(4f);
            titleCell.addElement(title);
            String dateStr = date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ROOT)
                + "  |  " + date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
            titleCell.addElement(new Paragraph(dateStr, FontFactory.getFont(FontFactory.HELVETICA, 11, muted)));
            header.addCell(logoCell);
            header.addCell(titleCell);
            doc.add(header);

            PdfPTable metrics = new PdfPTable(4);
            metrics.setWidthPercentage(100);
            metrics.setWidths(new float[]{1.2f, 1f, 1f, 1.2f});
            metrics.setSpacingAfter(14f);
            metrics.addCell(buildDailyMetricCell("Date", date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), brandGoldSoft, ink, muted));
            metrics.addCell(buildDailyMetricCell("Students", String.valueOf(rows.size()), brandGoldSoft, ink, muted));
            metrics.addCell(buildDailyMetricCell("Attend", String.valueOf(attendCount), successBg, successText, muted));
            metrics.addCell(buildDailyMetricCell("Absence", String.valueOf(absentCount), dangerBg, dangerText, muted));
            doc.add(metrics);

            Paragraph sectionTitle = new Paragraph("Attendance Timeline", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, ink));
            sectionTitle.setSpacingAfter(8f);
            doc.add(sectionTitle);

            PdfPTable attendanceTable = new PdfPTable(7);
            attendanceTable.setWidthPercentage(100);
            attendanceTable.setWidths(new float[]{1.35f, 2.2f, 1.2f, 2f, 1.4f, 1.4f, 1.8f});
            attendanceTable.setSpacingAfter(14f);

            String[] columns = {"Child ID", "Name", "Status", "Reason", "Check-In", "Check-Out", "Remark"};
            for (String col : columns) {
                PdfPCell cell = new PdfPCell(new Phrase(col, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink)));
                cell.setBackgroundColor(brandGold);
                cell.setBorderColor(border);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(8f);
                attendanceTable.addCell(cell);
            }

            if (rows.isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No attendance records were found for the selected date.", FontFactory.getFont(FontFactory.HELVETICA, 10, muted)));
                emptyCell.setColspan(7);
                emptyCell.setPadding(12f);
                emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                emptyCell.setBorderColor(border);
                attendanceTable.addCell(emptyCell);
            } else {
                for (int index = 0; index < rows.size(); index++) {
                    AttendanceRow row = rows.get(index);
                    Color rowBg = index % 2 == 0 ? Color.WHITE : tableAlt;
                    boolean attend = "attend".equalsIgnoreCase(row.getStatus());
                    Color statusBg = attend ? successBg : dangerBg;
                    Color statusColor = attend ? successText : dangerText;

                    addDailyPdfBodyCell(attendanceTable, row.childIdProperty().get(), rowBg, border, Element.ALIGN_CENTER, ink, false);
                    addDailyPdfBodyCell(attendanceTable, row.nameProperty().get(), rowBg, border, Element.ALIGN_LEFT, ink, false);
                    addDailyPdfBodyCell(attendanceTable, row.getStatus(), statusBg, border, Element.ALIGN_CENTER, statusColor, true);
                    addDailyPdfBodyCell(attendanceTable, row.getReason() == null ? "-" : row.getReason(), rowBg, border, Element.ALIGN_LEFT, ink, false);
                    addDailyPdfBodyCell(attendanceTable, formatTime(row.getCheckInTime()), rowBg, border, Element.ALIGN_CENTER, ink, false);
                    addDailyPdfBodyCell(attendanceTable, formatTime(row.getCheckOutTime()), rowBg, border, Element.ALIGN_CENTER, ink, false);
                    addDailyPdfBodyCell(attendanceTable, row.getRemark() == null || row.getRemark().isBlank() ? "-" : row.getRemark(), rowBg, border, Element.ALIGN_LEFT, ink, false);
                }
            }
            doc.add(attendanceTable);

            LineSeparator line = new LineSeparator();
            line.setPercentage(100f);
            line.setLineWidth(0.8f);
            line.setLineColor(border);
            doc.add(Chunk.NEWLINE);
            doc.add(line);

            Paragraph footer = new Paragraph("Generated from the Taska Zurah attendance report module for daily attendance review and record keeping.", FontFactory.getFont(FontFactory.HELVETICA, 10, muted));
            footer.setSpacingBefore(8f);
            doc.add(footer);

            Paragraph sign = new Paragraph("Assigned Teacher", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink));
            sign.setAlignment(Element.ALIGN_RIGHT);
            sign.setSpacingBefore(16f);
            doc.add(sign);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    private static PdfPCell buildDailyMetricCell(String label, String value, Color bgColor, Color valueColor, Color muted) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(10f);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(bgColor);

        Paragraph labelParagraph = new Paragraph(label.toUpperCase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, muted));
        labelParagraph.setSpacingAfter(6f);
        cell.addElement(labelParagraph);

        Paragraph valueParagraph = new Paragraph(value == null ? "-" : value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, valueColor));
        cell.addElement(valueParagraph);
        return cell;
    }

    private static void addDailyPdfBodyCell(PdfPTable table, String text, Color bgColor, Color borderColor, int alignment, Color textColor, boolean bold) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 10, bold ? Font.BOLD : Font.NORMAL, textColor);
        PdfPCell cell = new PdfPCell(new Phrase(text == null || text.isBlank() ? "-" : text, font));
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(borderColor);
        cell.setPadding(8f);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private static String formatTime(String time) {
        try {
            if (time == null || time.trim().isEmpty()) {
                return "";
            }
            LocalTime t = LocalTime.parse(time.substring(11));
            return t.format(DateTimeFormatter.ofPattern("h:mm a"));
        } catch (Exception e) {
            return time != null ? time : "";
        }
    }
}