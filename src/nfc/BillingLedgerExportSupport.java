package nfc;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

@SuppressWarnings("unused")
final class BillingLedgerExportSupport {
    private BillingLedgerExportSupport() {}

    static PdfPCell buildInfoCard(String title, String[][] rows, java.awt.Color ink, java.awt.Color muted, java.awt.Color border) {
        PdfPCell card = new PdfPCell();
        card.setPadding(14f);
        card.setBorderColor(border);
        card.setBackgroundColor(java.awt.Color.WHITE);

        Paragraph cardTitle = new Paragraph(title.toUpperCase(Locale.ROOT), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, muted));
        cardTitle.setSpacingAfter(10f);
        card.addElement(cardTitle);

        PdfPTable inner = new PdfPTable(2);
        inner.setWidthPercentage(100);
        inner.setWidths(new float[]{1.2f, 1.8f});
        for (String[] row : rows) {
            PdfPCell labelCell = new PdfPCell(new Phrase(row[0], FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, muted)));
            labelCell.setBorder(Rectangle.NO_BORDER);
            labelCell.setPadding(6f);
            inner.addCell(labelCell);

            PdfPCell valueCell = new PdfPCell(new Phrase(row[1] == null ? "-" : row[1], FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink)));
            valueCell.setBorder(Rectangle.NO_BORDER);
            valueCell.setPadding(6f);
            inner.addCell(valueCell);
        }
        card.addElement(inner);
        return card;
    }

    static void addTableHeaderCell(PdfPTable table, String text, java.awt.Color bg, java.awt.Color ink, java.awt.Color border, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ink)));
        cell.setBackgroundColor(bg);
        cell.setBorderColor(border);
        cell.setPadding(8f);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    static void addEmptyItemsRow(PdfPTable table, String message, int colspan, java.awt.Color border, java.awt.Color muted) {
        PdfPCell cell = new PdfPCell(new Phrase(message, FontFactory.getFont(FontFactory.HELVETICA, 10, muted)));
        cell.setColspan(colspan);
        cell.setPadding(12f);
        cell.setBorderColor(border);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    static void addBodyCell(PdfPTable table, String text, java.awt.Color bg, java.awt.Color border, int alignment, java.awt.Color color, boolean bold) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 10, bold ? Font.BOLD : Font.NORMAL, color);
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "-" : text, font));
        cell.setBackgroundColor(bg);
        cell.setBorderColor(border);
        cell.setPadding(8f);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    static void addTotalRow(PdfPTable table, String label, String value, java.awt.Color bg, java.awt.Color valueColor, java.awt.Color border) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new java.awt.Color(120, 132, 150))));
        labelCell.setBackgroundColor(bg);
        labelCell.setBorderColor(border);
        labelCell.setPadding(8f);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);

        PdfPCell valueCell = new PdfPCell(new Phrase(value == null ? "-" : value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, valueColor)));
        valueCell.setBackgroundColor(bg);
        valueCell.setBorderColor(border);
        valueCell.setPadding(8f);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    static PdfPCell buildMetricCell(String label, String value, java.awt.Color bgColor, java.awt.Color valueColor, java.awt.Color muted) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(10f);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(bgColor);

        Paragraph labelParagraph = new Paragraph(label.toUpperCase(Locale.ROOT), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, muted));
        labelParagraph.setSpacingAfter(6f);
        cell.addElement(labelParagraph);

        Paragraph valueParagraph = new Paragraph(value == null ? "-" : value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, valueColor));
        cell.addElement(valueParagraph);
        return cell;
    }

    static boolean openExportedFile(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        try {
            Desktop.getDesktop().open(file);
            return true;
        } catch (IOException | IllegalArgumentException | SecurityException ignored) {
            return false;
        }
    }

    static boolean printExportedFile(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.PRINT)) {
            return false;
        }
        try {
            desktop.print(file);
            return true;
        } catch (IOException | IllegalArgumentException | SecurityException ignored) {
            return false;
        }
    }

    static String buildExportMessage(String exportLabel, File file, boolean openRequested, boolean opened, boolean printRequested, boolean printed) {
        StringBuilder sb = new StringBuilder();
        sb.append(exportLabel).append(" saved to:\n").append(file.getAbsolutePath());
        if (openRequested) {
            sb.append(opened ? "\n\nOpened with the default application." : "\n\nOpen was requested, but the OS did not launch a viewer.");
        }
        if (printRequested) {
            sb.append(printed ? "\n\nSent to the OS print handler." : "\n\nPrint was requested, but this machine did not expose a printable handler for the file.");
        }
        return sb.toString();
    }
}