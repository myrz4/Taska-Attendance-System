package nfc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

@SuppressWarnings("unused")
final class BillingPolicyStatusSupport {
    private BillingPolicyStatusSupport() {}

    static String localHealthSummary(List<String> errors) {
        return errors == null || errors.isEmpty()
            ? "Local: OK (all required billing codes are present and valid)."
            : "Local: INVALID\n" + String.join("\n", errors);
    }

    static void refreshLiveHealthStatus(BillingPolicyStatusUi ui) {
        ui.badge.setText("Live Backend: checking...");
        AppThemeSupport.applyStatusTone(ui.badge, "app-status-badge", AppThemeSupport.Tone.WARNING);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            BillingPolicyRemoteSupport.RemoteHealthSnapshot snapshot = BillingPolicyRemoteSupport.fetchRemoteHealthSummary();
            Platform.runLater(() -> applyRemoteHealthSnapshot(ui, snapshot));
        });
    }

    static void applyRemoteHealthSnapshot(BillingPolicyStatusUi ui, BillingPolicyRemoteSupport.RemoteHealthSnapshot snapshot) {
        String checkedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        ui.metaLabel.setText("Last checked: " + checkedAt);
        if (snapshot == null) {
            ui.badge.setText("Live Backend: error");
            AppThemeSupport.applyStatusTone(ui.badge, "app-status-badge", AppThemeSupport.Tone.DANGER);
            AppThemeSupport.applyStatusTone(ui.detailsBox, "app-status-panel", AppThemeSupport.Tone.DANGER);
            setLiveHealthDetails(ui, "-", "-", "-", "unknown", "unknown");
            return;
        }
        if (!snapshot.ok) {
            ui.badge.setText("Live Backend: failed");
            AppThemeSupport.applyStatusTone(ui.badge, "app-status-badge", AppThemeSupport.Tone.DANGER);
            AppThemeSupport.applyStatusTone(ui.detailsBox, "app-status-panel", AppThemeSupport.Tone.DANGER);
            setLiveHealthDetails(ui, snapshot.version, snapshot.rowCount, snapshot.resolvedTransit, snapshot.missingSummary, snapshot.gatewaySummary);
            return;
        }
        if (snapshot.valid) {
            ui.badge.setText("Live Backend: healthy");
            AppThemeSupport.applyStatusTone(ui.badge, "app-status-badge", AppThemeSupport.Tone.SUCCESS);
            AppThemeSupport.applyStatusTone(ui.detailsBox, "app-status-panel", AppThemeSupport.Tone.SUCCESS);
            setLiveHealthDetails(ui, snapshot.version, snapshot.rowCount, snapshot.resolvedTransit, snapshot.missingSummary, snapshot.gatewaySummary);
            return;
        }
        ui.badge.setText("Live Backend: invalid");
        AppThemeSupport.applyStatusTone(ui.badge, "app-status-badge", AppThemeSupport.Tone.WARNING);
        AppThemeSupport.applyStatusTone(ui.detailsBox, "app-status-panel", AppThemeSupport.Tone.WARNING);
        setLiveHealthDetails(ui, snapshot.version, snapshot.rowCount, snapshot.resolvedTransit, snapshot.missingSummary, snapshot.gatewaySummary);
    }

    static void toggleLiveHealthDetails(BillingPolicyStatusUi ui) {
        boolean nextVisible = !ui.detailsBox.isVisible();
        ui.detailsBox.setVisible(nextVisible);
        ui.detailsBox.setManaged(nextVisible);
        ui.detailsButton.setText(nextVisible ? "Hide Details" : "Show Details");
    }

    static void showAuditLogDialog(Window owner, AlertSink alerts) {
        BillingPolicyAuditDialogSupport.showAuditLogDialog(owner, alerts);
    }

    private static void setLiveHealthDetails(BillingPolicyStatusUi ui, String version, String rowCount, String resolvedTransit, String missingSummary, String gatewaySummary) {
        ui.versionLabel.setText("Version: " + String.valueOf(version == null || version.isBlank() ? "-" : version));
        ui.rowCountLabel.setText("Rows: " + String.valueOf(rowCount == null || rowCount.isBlank() ? "-" : rowCount));
        ui.transitLabel.setText("Registered Billing Model: " + String.valueOf(resolvedTransit == null || resolvedTransit.isBlank() ? "-" : resolvedTransit));
        ui.missingLabel.setText("Missing Required Codes: " + String.valueOf(missingSummary == null || missingSummary.isBlank() ? "none" : missingSummary));
        ui.gatewayLabel.setText("Payment Mode: " + String.valueOf(gatewaySummary == null || gatewaySummary.isBlank() ? "-" : gatewaySummary));
    }

    static final class BillingPolicyStatusUi {
        final Label badge;
        final Label metaLabel;
        final Button detailsButton;
        final VBox detailsBox;
        final Label versionLabel;
        final Label rowCountLabel;
        final Label transitLabel;
        final Label missingLabel;
        final Label gatewayLabel;

        BillingPolicyStatusUi(Label badge, Label metaLabel, Button detailsButton, VBox detailsBox, Label versionLabel, Label rowCountLabel, Label transitLabel, Label missingLabel, Label gatewayLabel) {
            this.badge = badge;
            this.metaLabel = metaLabel;
            this.detailsButton = detailsButton;
            this.detailsBox = detailsBox;
            this.versionLabel = versionLabel;
            this.rowCountLabel = rowCountLabel;
            this.transitLabel = transitLabel;
            this.missingLabel = missingLabel;
            this.gatewayLabel = gatewayLabel;
        }
    }

    interface AlertSink {
        void showInfo(String header, String message);
        void showError(String header, Exception ex);
    }
}