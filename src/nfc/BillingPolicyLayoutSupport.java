package nfc;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

@SuppressWarnings("unused")
final class BillingPolicyLayoutSupport {
    private BillingPolicyLayoutSupport() {
    }

    @SuppressWarnings("unused")
    static javafx.scene.layout.HBox createTitleRow(Label liveHealthBadge, Button liveHealthRefreshBtn, Button liveHealthDetailsBtn) {
        return AppThemeSupport.createStandardPageBanner(
            "Billing Policy Catalog",
            "Manage active billing catalogs, monitor live backend health, and keep policy rows clean and auditable.",
            liveHealthRefreshBtn,
            liveHealthDetailsBtn,
            liveHealthBadge
        );
    }

    @SuppressWarnings("unused")
    static VBox createLiveHealthDetailsBox(
        Label liveHealthVersionLabel,
        Label liveHealthRowCountLabel,
        Label liveHealthTransitLabel,
        Label liveHealthMissingLabel,
        Label liveHealthGatewayLabel
    ) {
        VBox liveHealthDetailsBox = new VBox(4);
        liveHealthDetailsBox.getStyleClass().addAll("app-card", "app-status-panel");
        liveHealthDetailsBox.getChildren().setAll(
            liveHealthVersionLabel,
            liveHealthRowCountLabel,
            liveHealthTransitLabel,
            liveHealthMissingLabel,
            liveHealthGatewayLabel
        );
        liveHealthDetailsBox.setPadding(new Insets(12));
        liveHealthDetailsBox.setVisible(false);
        liveHealthDetailsBox.setManaged(false);
        return liveHealthDetailsBox;
    }

    @SuppressWarnings("unused")
    static VBox createTopActions(
        javafx.scene.control.ComboBox<BillingPolicyWorkflowSupport.CatalogItemOption> catalogSelect,
        Button refreshBtn,
        Button seedDefaultBtn,
        TextField versionField,
        TextField defaultTransitCodeField,
        Button saveNewBtn,
        Button activateBtn,
        Button healthBtn,
        Button auditBtn,
        Button exportTxtBtn,
        Button exportJsonBtn
    ) {
        FlowPane catalogRow = BillingPolicyUiSupport.createWrapRow(
            new Label("Catalog:"), catalogSelect,
            refreshBtn,
            seedDefaultBtn
        );
        FlowPane versionRow = BillingPolicyUiSupport.createWrapRow(
            new Label("Version:"), versionField,
            new Label("Default Transit:"), defaultTransitCodeField,
            saveNewBtn,
            activateBtn
        );
        FlowPane healthRow = BillingPolicyUiSupport.createWrapRow(
            healthBtn,
            auditBtn,
            exportTxtBtn,
            exportJsonBtn
        );

        VBox controlsCard = AppThemeSupport.createSectionCard(
            "Catalog Controls",
            "Keep versioning and export actions compact so the policy table stays dominant.",
            catalogRow,
            versionRow,
            healthRow
        );
        controlsCard.getStyleClass().add("app-toolbar-card");
        return controlsCard;
    }

    @SuppressWarnings("unused")
    static VBox createRowEditor(
        TextField selectedCode,
        TextField selectedStaff,
        TextField selectedNonStaff,
        Button updateRowBtn
    ) {
        VBox editorCard = AppThemeSupport.createSectionCard(
            "Selected Policy Row",
            "Keep the selected row editor visible without taking height away from the table.",
            BillingPolicyUiSupport.createWrapRow(
                new Label("Selected:"),
                selectedCode,
                selectedStaff,
                selectedNonStaff,
                updateRowBtn
            )
        );
        editorCard.getStyleClass().add("app-editor-card");
        return editorCard;
    }

    @SuppressWarnings("unused")
    static VBox createTableCard(TableView<BillingPolicyView.Row> table) {
        VBox tableCard = new VBox(
            10,
            AppThemeSupport.createSectionHeader(
                "Policy Table",
                "The working catalog rows below should stay visible and resizable while you edit policy values."
            ),
            table
        );
        tableCard.getStyleClass().addAll("app-card", "app-detail-card");
        tableCard.setMinHeight(0);
        VBox.setVgrow(table, Priority.ALWAYS);
        return tableCard;
    }
}