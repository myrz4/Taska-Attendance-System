package nfc;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

final class BillingPolicyAuditDialogSupport {
    private static final List<String> AUDIT_ACTION_OPTIONS = java.util.Arrays.asList(
        "all",
        "catalog_saved",
        "catalog_activated"
    );

    private BillingPolicyAuditDialogSupport() {
    }

    static void showAuditLogDialog(Window owner, BillingPolicyStatusSupport.AlertSink alerts) {
        Alert auditDialog = new Alert(Alert.AlertType.INFORMATION);
        auditDialog.setHeaderText("Recent Billing Audit Log");
        auditDialog.setTitle("Billing Audit");

        ComboBox<String> actionFilter = new ComboBox<>(FXCollections.observableArrayList(AUDIT_ACTION_OPTIONS));
        actionFilter.setValue("all");
        actionFilter.setPrefWidth(180);

        Button refreshBtn = new Button("Refresh");
        Button exportTxtBtn = new Button("Export Audit TXT");
        Button exportJsonBtn = new Button("Export Audit JSON");

        TextArea body = new TextArea("Loading recent audit entries...");
        body.setEditable(false);
        body.setWrapText(false);
        body.setPrefColumnCount(100);
        body.setPrefRowCount(24);
        body.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

        HBox controls = new HBox(8,
            new Label("Action:"),
            actionFilter,
            refreshBtn,
            exportTxtBtn,
            exportJsonBtn
        );
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, controls, body);
        AtomicReference<BillingPolicyRemoteSupport.AuditLogSnapshot> currentSnapshot = new AtomicReference<>(new BillingPolicyRemoteSupport.AuditLogSnapshot(
            "all",
            Collections.emptyList(),
            "Loading recent audit entries..."
        ));

        Runnable refreshAction = () -> refreshAuditLog(body, currentSnapshot, BillingPolicyRemoteSupport.normalizeAuditActionFilter(actionFilter.getValue()));
        refreshBtn.setOnAction(event -> refreshAction.run());
        actionFilter.setOnAction(event -> refreshAction.run());
        exportTxtBtn.setOnAction(event -> exportAuditLogTxt(owner, currentSnapshot.get(), alerts));
        exportJsonBtn.setOnAction(event -> exportAuditLogJson(owner, currentSnapshot.get(), alerts));

        auditDialog.getDialogPane().setContent(content);
        auditDialog.getDialogPane().setMinWidth(860);
        auditDialog.show();

        refreshAuditLog(body, currentSnapshot, "all");
    }

    private static void refreshAuditLog(TextArea body, AtomicReference<BillingPolicyRemoteSupport.AuditLogSnapshot> currentSnapshot, String actionFilter) {
        body.setText("Loading recent audit entries...");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            BillingPolicyRemoteSupport.AuditLogSnapshot snapshot = BillingPolicyRemoteSupport.fetchAuditLogSnapshot(actionFilter);
            Platform.runLater(() -> {
                currentSnapshot.set(snapshot);
                body.setText(snapshot.formattedText);
            });
        });
    }

    private static void exportAuditLogTxt(Window owner, BillingPolicyRemoteSupport.AuditLogSnapshot snapshot, BillingPolicyStatusSupport.AlertSink alerts) {
        try {
            java.io.File file = BillingPolicyUiSupport.chooseSaveFile(
                owner,
                "Save Billing Audit Log (TXT)",
                "Text Files",
                "*.txt",
                BillingPolicyRemoteSupport.auditExportFileName(snapshot.actionFilter, ".txt")
            );
            if (file == null) {
                return;
            }

            BillingPolicyUiSupport.writeTextFile(file, snapshot.formattedText);
            alerts.showInfo("Exported", "Audit log saved to:\n" + file.getAbsolutePath());
        } catch (IOException | SecurityException ex) {
            alerts.showError("Failed to export audit TXT", ex);
        }
    }

    private static void exportAuditLogJson(Window owner, BillingPolicyRemoteSupport.AuditLogSnapshot snapshot, BillingPolicyStatusSupport.AlertSink alerts) {
        try {
            java.io.File file = BillingPolicyUiSupport.chooseSaveFile(
                owner,
                "Save Billing Audit Log (JSON)",
                "JSON Files",
                "*.json",
                BillingPolicyRemoteSupport.auditExportFileName(snapshot.actionFilter, ".json")
            );
            if (file == null) {
                return;
            }

            BillingPolicyUiSupport.writeTextFile(
                file,
                BillingPolicyCallableSupport.toPrettyJson(BillingPolicyRemoteSupport.auditJsonPayload(
                    new BillingPolicyRemoteSupport.AuditLogSnapshot(snapshot.actionFilter, snapshot.entries, snapshot.formattedText)
                ))
            );
            alerts.showInfo("Exported", "Audit log saved to:\n" + file.getAbsolutePath());
        } catch (IOException | SecurityException ex) {
            alerts.showError("Failed to export audit JSON", ex);
        }
    }
}