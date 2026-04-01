package nfc;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.stage.FileChooser;
import javafx.stage.Window;

final class CasualTransitExportSupport {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private CasualTransitExportSupport() {}

    static void writeSummaryCsv(File file, List<CasualTransitView.VisitRow> exportRows, String filterSummary) throws IOException {
        List<String> lines = new ArrayList<>();
        long openCount = exportRows.stream().filter(CasualTransitView.VisitRow::isOpen).count();
        long closedCount = exportRows.stream().filter(CasualTransitView.VisitRow::isClosed).count();
        long canceledCount = exportRows.stream().filter(CasualTransitView.VisitRow::isCanceled).count();
        long totalPaidSen = exportRows.stream().mapToLong(CasualTransitView.VisitRow::amountSen).sum();
        lines.add(String.join(",", csvValue("report_generated_at"), csvValue(LocalDateTime.now().format(DATE_TIME_FORMAT))));
        lines.add(String.join(",", csvValue("report_filters"), csvValue(filterSummary)));
        lines.add(String.join(",", csvValue("visible_visits"), csvValue(String.valueOf(exportRows.size()))));
        lines.add("");
        lines.add(String.join(",",
            csvValue("visit_id"), csvValue("status"), csvValue("payment_status"), csvValue("visit_date"), csvValue("child_name"),
            csvValue("guardian_name"), csvValue("guardian_phone"), csvValue("guardian_relationship"), csvValue("check_in"),
            csvValue("check_out"), csvValue("amount_paid"), csvValue("amount_paid_sen"), csvValue("payment_method"),
            csvValue("receipt_no"), csvValue("notes")
        ));
        for (CasualTransitView.VisitRow row : exportRows) {
            lines.add(String.join(",",
                csvValue(row.visitId()),
                csvValue(row.statusLabel()),
                csvValue(row.paymentStatusLabel()),
                csvValue(row.visitDateLabel()),
                csvValue(row.childName()),
                csvValue(row.guardianName()),
                csvValue(row.guardianPhone()),
                csvValue(row.guardianRelationship()),
                csvValue(row.checkInInputValue()),
                csvValue(row.checkOutInputValue()),
                csvValue(row.amountLabel()),
                csvValue(String.valueOf(row.amountSen())),
                csvValue(row.paymentMethod()),
                csvValue(row.receiptNo()),
                csvValue(row.notes())
            ));
        }
        lines.add("");
        lines.add(String.join(",", csvValue("summary_metric"), csvValue("value")));
        lines.add(String.join(",", csvValue("open_visits"), csvValue(String.valueOf(openCount))));
        lines.add(String.join(",", csvValue("closed_visits"), csvValue(String.valueOf(closedCount))));
        lines.add(String.join(",", csvValue("canceled_visits"), csvValue(String.valueOf(canceledCount))));
        lines.add(String.join(",", csvValue("total_paid_sen"), csvValue(String.valueOf(totalPaidSen))));
        lines.add(String.join(",", csvValue("total_paid_label"), csvValue(formatMoney(totalPaidSen))));
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }

    static String exportReceiptPdfWithDialog(Window owner, CasualTransitView.VisitRow row, String baseName) throws IOException {
        return exportWithDialog(
            owner,
            "Save Casual Transit Receipt (PDF)",
            "PDF Files",
            "*.pdf",
            baseName + ".pdf",
            file -> CasualTransitPdfExportSupport.writeReceiptPdf(file, row),
            "Casual transit receipt"
        );
    }

    static String exportSummaryPdfWithDialog(Window owner, List<CasualTransitView.VisitRow> exportRows, String filterSummary, String baseName) throws IOException {
        return exportWithDialog(
            owner,
            "Save Casual Transit Summary (PDF)",
            "PDF Files",
            "*.pdf",
            baseName + ".pdf",
            file -> CasualTransitPdfExportSupport.writeSummaryPdf(file, exportRows, filterSummary),
            "Casual transit summary"
        );
    }

    static String exportSummaryCsvWithDialog(Window owner, List<CasualTransitView.VisitRow> exportRows, String filterSummary, String baseName) throws IOException {
        return exportWithDialog(
            owner,
            "Save Casual Transit Summary (CSV)",
            "CSV Files",
            "*.csv",
            baseName + ".csv",
            file -> writeSummaryCsv(file, exportRows, filterSummary),
            "Casual transit summary CSV"
        );
    }

    static String exportAuditPdfWithDialog(
        Window owner,
        CasualTransitView.VisitRow row,
        List<CasualTransitView.AuditEntry> exportEntries,
        String filterSummary,
        String baseName
    ) throws IOException {
        return exportWithDialog(
            owner,
            "Save Casual Transit Audit History (PDF)",
            "PDF Files",
            "*.pdf",
            baseName + ".pdf",
            file -> CasualTransitPdfExportSupport.writeAuditPdf(file, row, exportEntries, filterSummary),
            "Casual transit audit history"
        );
    }

    static boolean openExportedFile(File file) {
        try {
            if (!Desktop.isDesktopSupported()) {
                return false;
            }
            Desktop.getDesktop().open(file);
            return true;
        } catch (IOException | SecurityException | UnsupportedOperationException ignored) {
            return false;
        }
    }

    static String buildExportMessage(String exportLabel, File file, boolean opened) {
        StringBuilder sb = new StringBuilder();
        sb.append(exportLabel).append(" saved to:\n").append(file.getAbsolutePath());
        if (opened) {
            sb.append("\n\nOpened in the default OS viewer.");
        }
        return sb.toString();
    }

    private static String exportWithDialog(
        Window owner,
        String dialogTitle,
        String extensionLabel,
        String extensionPattern,
        String initialFileName,
        ExportWriter writer,
        String exportLabel
    ) throws IOException {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(dialogTitle);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(extensionLabel, extensionPattern));
        fileChooser.setInitialFileName(initialFileName);

        File file = fileChooser.showSaveDialog(owner);
        if (file == null) {
            return null;
        }

        writer.write(file);
        boolean opened = openExportedFile(file);
        return buildExportMessage(exportLabel, file, opened);
    }

    @FunctionalInterface
    private interface ExportWriter {
        void write(File file) throws IOException;
    }

    private static String csvValue(String value) {
        String normalized = value == null ? "" : value;
        String escaped = normalized.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static String formatMoney(long sen) {
        return java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ms-MY")).format(sen / 100.0d);
    }
}