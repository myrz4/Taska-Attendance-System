package nfc;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javafx.stage.Window;

@SuppressWarnings("unused")
final class BillingPolicyExportSupport {
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private BillingPolicyExportSupport() {
    }

    interface AlertSink {
        void showInfo(String header, String message);

        void showError(String header, Exception ex);
    }

    static void exportHealthReportTxt(Window owner, Supplier<String> textSupplier, AlertSink sink) {
        exportReport(
            owner,
            "Save Billing Health Report (TXT)",
            "Text Files",
            "*.txt",
            "billing-health-report-" + timestamp() + ".txt",
            textSupplier.get(),
            "Failed to export TXT report",
            sink
        );
    }

    static void exportHealthReportJson(Window owner, Supplier<Map<String, Object>> payloadSupplier, AlertSink sink) {
        exportReport(
            owner,
            "Save Billing Health Report (JSON)",
            "JSON Files",
            "*.json",
            "billing-health-report-" + timestamp() + ".json",
            GSON_PRETTY.toJson(payloadSupplier.get()),
            "Failed to export JSON report",
            sink
        );
    }

    private static void exportReport(
        Window owner,
        String dialogTitle,
        String extensionDescription,
        String extensionPattern,
        String defaultFileName,
        String contents,
        String failureHeader,
        AlertSink sink
    ) {
        try {
            java.io.File target = BillingPolicyUiSupport.chooseSaveFile(
                owner,
                dialogTitle,
                extensionDescription,
                extensionPattern,
                defaultFileName
            );
            if (target == null) {
                return;
            }

            BillingPolicyUiSupport.writeTextFile(target, contents);
            sink.showInfo("Exported", "Health report saved to:\n" + target.getAbsolutePath());
        } catch (IOException | SecurityException ex) {
            sink.showError(failureHeader, ex);
        }
    }

    private static String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
    }
}