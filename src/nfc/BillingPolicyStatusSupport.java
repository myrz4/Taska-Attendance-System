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
        ui.badge.setStyle(warningBadgeStyle());
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
            ui.badge.setStyle(errorBadgeStyle());
            ui.detailsBox.setStyle(errorDetailsStyle());
            setLiveHealthDetails(ui, "-", "-", "-", "unknown", "unknown");
            return;
        }
        if (!snapshot.ok) {
            ui.badge.setText("Live Backend: failed");
            ui.badge.setStyle(errorBadgeStyle());
            ui.detailsBox.setStyle(errorDetailsStyle());
            setLiveHealthDetails(ui, snapshot.version, snapshot.rowCount, snapshot.resolvedTransit, snapshot.missingSummary, snapshot.gatewaySummary);
            return;
        }
        if (snapshot.valid) {
            ui.badge.setText("Live Backend: healthy");
            ui.badge.setStyle(successBadgeStyle());
            ui.detailsBox.setStyle(successDetailsStyle());
            setLiveHealthDetails(ui, snapshot.version, snapshot.rowCount, snapshot.resolvedTransit, snapshot.missingSummary, snapshot.gatewaySummary);
            return;
        }
        ui.badge.setText("Live Backend: invalid");
        ui.badge.setStyle(invalidBadgeStyle());
        ui.detailsBox.setStyle(invalidDetailsStyle());
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
        ui.transitLabel.setText("Resolved Default Transit: " + String.valueOf(resolvedTransit == null || resolvedTransit.isBlank() ? "-" : resolvedTransit));
        ui.missingLabel.setText("Missing Required Codes: " + String.valueOf(missingSummary == null || missingSummary.isBlank() ? "none" : missingSummary));
        ui.gatewayLabel.setText("Payment Mode: " + String.valueOf(gatewaySummary == null || gatewaySummary.isBlank() ? "-" : gatewaySummary));
    }

    private static String warningBadgeStyle() {
        return "-fx-background-color: #fff3cd; -fx-text-fill: #7a5200; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 999;";
    }

    private static String errorBadgeStyle() {
        return "-fx-background-color: #fde2e1; -fx-text-fill: #9f1d1d; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 999;";
    }

    private static String successBadgeStyle() {
        return "-fx-background-color: #dff6e4; -fx-text-fill: #17643a; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 999;";
    }

    private static String invalidBadgeStyle() {
        return "-fx-background-color: #fff1d6; -fx-text-fill: #8a5800; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 999;";
    }

    private static String errorDetailsStyle() {
        return "-fx-background-color: rgba(253,226,225,0.78); -fx-border-color: #d36b6b; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 8 12 8 12;";
    }

    private static String successDetailsStyle() {
        return "-fx-background-color: rgba(223,246,228,0.78); -fx-border-color: #52a071; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 8 12 8 12;";
    }

    private static String invalidDetailsStyle() {
        return "-fx-background-color: rgba(255,241,214,0.82); -fx-border-color: #d39a32; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 8 12 8 12;";
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