package nfc;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;

import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

final class BillingLedgerPdfSupport {
    private BillingLedgerPdfSupport() {}

    static String exportLedgerRowPdfWithDialog(
        Window owner,
        BillingLedgerView.LedgerRow row,
        List<BillingLedgerView.LedgerRow> allRows,
        Class<?> ownerClass,
        String baseName,
        boolean openAfterExport,
        boolean printAfterExport,
        Function<String, String> nullSafe,
        Function<java.util.Date, String> formatDate,
        Function<java.util.Date, String> formatDateTime,
        Function<Long, String> formatMoney,
        Function<BillingLedgerView.LedgerRow, String> paymentProvider,
        Function<String, String> formatProviderLabel,
        BiFunction<String, String, String> formatPaymentMethod,
        Predicate<BillingLedgerView.LedgerRow> isDummyPayment
    ) throws Exception {
        File file = BillingPolicyUiSupport.chooseSaveFile(
            owner,
            "Save Invoice or Receipt (PDF)",
            "PDF Files",
            "*.pdf",
            baseName + ".pdf"
        );
        if (file == null) {
            return null;
        }

        writeLedgerRowPdf(
            file,
            row,
            allRows,
            ownerClass,
            nullSafe,
            formatDate,
            formatDateTime,
            formatMoney,
            paymentProvider,
            formatProviderLabel,
            formatPaymentMethod,
            isDummyPayment
        );

        boolean opened = openAfterExport && BillingLedgerExportSupport.openExportedFile(file);
        boolean printed = printAfterExport && BillingLedgerExportSupport.printExportedFile(file);
        return BillingLedgerExportSupport.buildExportMessage("Billing PDF", file, openAfterExport, opened, printAfterExport, printed);
    }

    static String exportParentSummaryPdfWithDialog(
        Window owner,
        BillingLedgerView.ParentSummaryRow summary,
        List<BillingLedgerView.LedgerRow> parentRows,
        Class<?> ownerClass,
        String currentFilterDescription,
        String baseName,
        boolean openAfterExport,
        boolean printAfterExport,
        Function<Long, String> formatMoney,
        Function<String, String> nullSafe,
        Function<java.util.Date, String> formatDate
    ) throws Exception {
        File file = BillingPolicyUiSupport.chooseSaveFile(
            owner,
            "Save Parent Billing Summary (PDF)",
            "PDF Files",
            "*.pdf",
            baseName + ".pdf"
        );
        if (file == null) {
            return null;
        }

        writeParentSummaryPdf(
            file,
            summary,
            parentRows,
            ownerClass,
            BillingLedgerDetailSupport.buildChildNamesText(parentRows),
            currentFilterDescription,
            formatMoney,
            nullSafe,
            formatDate
        );

        boolean opened = openAfterExport && BillingLedgerExportSupport.openExportedFile(file);
        boolean printed = printAfterExport && BillingLedgerExportSupport.printExportedFile(file);
        return BillingLedgerExportSupport.buildExportMessage("Parent billing PDF", file, openAfterExport, opened, printAfterExport, printed);
    }

    static BatchParentExportResult exportVisibleParentSummariesPdfWithDialog(
        Window owner,
        List<BillingLedgerView.ParentSummaryRow> summaries,
        List<BillingLedgerView.LedgerRow> visibleRows,
        Class<?> ownerClass,
        String currentFilterDescription,
        Function<Long, String> formatMoney,
        Function<String, String> nullSafe,
        Function<java.util.Date, String> formatDate,
        Function<BillingLedgerView.ParentSummaryRow, String> baseNameProvider
    ) throws Exception {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Choose Folder for Visible Parent PDF Export");
        File targetDirectory = directoryChooser.showDialog(owner);
        if (targetDirectory == null) {
            return null;
        }

        String exportStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File exportDirectory = new File(targetDirectory, "billing-parent-pdfs-" + exportStamp);
        if (!exportDirectory.exists() && !exportDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create the export folder:\n" + exportDirectory.getAbsolutePath());
        }

        int exportedCount = 0;
        for (BillingLedgerView.ParentSummaryRow summary : summaries) {
            List<BillingLedgerView.LedgerRow> parentRows = BillingLedgerDetailSupport.rowsForParent(summary.getParentId(), visibleRows);
            if (parentRows.isEmpty()) {
                continue;
            }

            File outputFile = new File(exportDirectory, baseNameProvider.apply(summary) + ".pdf");
            writeParentSummaryPdf(
                outputFile,
                summary,
                parentRows,
                ownerClass,
                BillingLedgerDetailSupport.buildChildNamesText(parentRows),
                currentFilterDescription,
                formatMoney,
                nullSafe,
                formatDate
            );
            exportedCount++;
        }

        return new BatchParentExportResult(exportedCount, exportDirectory);
    }

    static void writeParentSummaryPdf(
        File file,
        BillingLedgerView.ParentSummaryRow summary,
        List<BillingLedgerView.LedgerRow> parentRows,
        Class<?> ownerClass,
        String childNamesText,
        String currentFilterDescription,
        Function<Long, String> formatMoney,
        Function<String, String> nullSafe,
        Function<java.util.Date, String> formatDate
    ) throws Exception {
        Document document = new Document(PageSize.A4, 34, 34, 38, 34);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            PdfWriter.getInstance(document, out);
            document.open();

            java.awt.Color brandGold = new java.awt.Color(255, 203, 60);
            java.awt.Color brandGoldSoft = new java.awt.Color(255, 245, 217);
            java.awt.Color ink = new java.awt.Color(29, 42, 58);
            java.awt.Color muted = new java.awt.Color(120, 132, 150);
            java.awt.Color border = new java.awt.Color(228, 234, 242);
            java.awt.Color successBg = new java.awt.Color(234, 248, 239);
            java.awt.Color successText = new java.awt.Color(13, 122, 56);
            java.awt.Color dangerBg = new java.awt.Color(255, 240, 238);
            java.awt.Color dangerText = new java.awt.Color(197, 59, 42);
            java.awt.Color tableAlt = new java.awt.Color(248, 250, 252);

            List<BillingLedgerView.LedgerRow> sortedRows = new ArrayList<>(parentRows);
            sortedRows.sort(Comparator
                .comparing(BillingLedgerView.LedgerRow::getSortDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(BillingLedgerView.LedgerRow::getInvoiceId, Comparator.nullsLast(String::compareToIgnoreCase)));

            BillingLedgerParentSummaryLayoutSupport.addParentSummaryPdfHeader(
                document,
                summary,
                ownerClass,
                childNamesText,
                currentFilterDescription,
                brandGoldSoft,
                ink,
                muted,
                successBg,
                dangerBg,
                successText,
                dangerText
            );
            BillingLedgerParentSummaryPdfSupport.addMetrics(document, summary, sortedRows, childNamesText, ink, muted, border, brandGoldSoft, successBg, successText, dangerBg, dangerText, formatMoney);
            BillingLedgerParentSummaryPdfSupport.addInvoices(document, sortedRows, brandGold, ink, muted, border, successBg, successText, dangerBg, dangerText, tableAlt, nullSafe, formatDate);
            BillingLedgerParentSummaryPdfSupport.addReview(document, summary, sortedRows, ink, muted, border, brandGoldSoft);
            BillingLedgerParentSummaryLayoutSupport.addParentSummaryPdfFooter(document, summary, muted, ink);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    static void writeLedgerRowPdf(
        File file,
        BillingLedgerView.LedgerRow row,
        List<BillingLedgerView.LedgerRow> allRows,
        Class<?> ownerClass,
        Function<String, String> nullSafe,
        Function<java.util.Date, String> formatDate,
        Function<java.util.Date, String> formatDateTime,
        Function<Long, String> formatMoney,
        Function<BillingLedgerView.LedgerRow, String> paymentProvider,
        Function<String, String> formatProviderLabel,
        BiFunction<String, String, String> formatPaymentMethod,
        java.util.function.Predicate<BillingLedgerView.LedgerRow> isDummyPayment
    ) throws Exception {
        Document document = new Document(PageSize.A4, 34, 34, 38, 34);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            PdfWriter.getInstance(document, out);
            document.open();

            java.awt.Color brandGold = new java.awt.Color(255, 203, 60);
            java.awt.Color brandGoldSoft = new java.awt.Color(255, 245, 217);
            java.awt.Color ink = new java.awt.Color(29, 42, 58);
            java.awt.Color muted = new java.awt.Color(120, 132, 150);
            java.awt.Color border = new java.awt.Color(228, 234, 242);
            java.awt.Color successBg = new java.awt.Color(234, 248, 239);
            java.awt.Color successText = new java.awt.Color(13, 122, 56);
            java.awt.Color dangerBg = new java.awt.Color(255, 240, 238);
            java.awt.Color dangerText = new java.awt.Color(197, 59, 42);
            java.awt.Color tableAlt = new java.awt.Color(248, 250, 252);

            BillingLedgerInvoiceLayoutSupport.addInvoicePdfHeader(document, row, ownerClass, brandGold, brandGoldSoft, ink, muted, successBg, successText, dangerBg, dangerText);
            BillingLedgerInvoiceDocumentSupport.addIdentity(
                document,
                row,
                ink,
                muted,
                border,
                nullSafe,
                formatDate,
                formatDateTime,
                paymentProvider,
                isDummyPayment,
                formatProviderLabel,
                formatPaymentMethod,
                BillingLedgerRowSupport::firstNonBlank
            );
            BillingLedgerInvoiceDocumentSupport.addItems(
                document,
                row,
                brandGold,
                ink,
                muted,
                border,
                successBg,
                successText,
                dangerBg,
                dangerText,
                tableAlt,
                formatMoney,
                paymentProvider,
                formatProviderLabel
            );
            BillingLedgerInvoiceDocumentSupport.addNotes(
                document,
                row,
                BillingLedgerDetailSupport.rowsForParent(row.getParentId(), allRows),
                BillingLedgerDetailSupport.invoicePolicyNotes(row.getInvoiceDocument()),
                BillingLedgerDetailSupport.invoiceManagementReviewRecommended(row.getInvoiceDocument()),
                ink,
                muted,
                border,
                brandGoldSoft,
                successBg,
                nullSafe,
                formatMoney,
                formatDateTime,
                formatProviderLabel,
                BillingLedgerRowSupport::firstNonBlank
            );
            BillingLedgerInvoiceLayoutSupport.addInvoicePdfFooter(document, row, muted, ink);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    static final class BatchParentExportResult {
        final int exportedCount;
        final File exportDirectory;

        BatchParentExportResult(int exportedCount, File exportDirectory) {
            this.exportedCount = exportedCount;
            this.exportDirectory = exportDirectory;
        }
    }
}