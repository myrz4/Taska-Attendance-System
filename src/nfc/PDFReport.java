package nfc;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;

import java.io.FileOutputStream;
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

            PDFReportLayoutSupport.ReportPalette palette = PDFReportLayoutSupport.defaultPalette();
            PDFReportAttendanceSectionSupport.SummaryCounts counts = PDFReportAttendanceSectionSupport.countAttendance(days);

            PDFReportLayoutSupport.addHeader(document, logoPath, info, month, year, palette);
            PDFReportLayoutSupport.addInfoCard(document, info, month, year, palette);
            PDFReportLayoutSupport.addMetricsRow(document, attendancePercent, performance, counts, palette);
            PDFReportAttendanceSectionSupport.addAttendanceTable(document, days, palette);
            PDFReportLayoutSupport.addFooter(document, palette);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }
}
