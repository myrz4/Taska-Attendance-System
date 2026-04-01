package nfc;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class BillingPolicyLayoutSupport {
    private BillingPolicyLayoutSupport() {
    }

    static HBox createTitleRow(Label liveHealthBadge, Button liveHealthRefreshBtn, Button liveHealthDetailsBtn) {
        Label title = new Label("Billing Policy Catalog");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #1d2f24;");

        HBox titleRow = new HBox(8, title, liveHealthRefreshBtn, liveHealthDetailsBtn, liveHealthBadge);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        return titleRow;
    }

    static VBox createLiveHealthDetailsBox(
        Label liveHealthVersionLabel,
        Label liveHealthRowCountLabel,
        Label liveHealthTransitLabel,
        Label liveHealthMissingLabel,
        Label liveHealthGatewayLabel
    ) {
        VBox liveHealthDetailsBox = new VBox(4);
        liveHealthDetailsBox.getChildren().setAll(
            liveHealthVersionLabel,
            liveHealthRowCountLabel,
            liveHealthTransitLabel,
            liveHealthMissingLabel,
            liveHealthGatewayLabel
        );
        liveHealthDetailsBox.setPadding(new Insets(8, 12, 8, 12));
        liveHealthDetailsBox.setStyle("-fx-background-color: rgba(255,243,205,0.70); -fx-border-color: #d6b656; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 8 12 8 12;");
        liveHealthDetailsBox.setVisible(false);
        liveHealthDetailsBox.setManaged(false);
        return liveHealthDetailsBox;
    }

    static FlowPane createTopActions(
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
        return BillingPolicyUiSupport.createWrapRow(
            new Label("Catalog:"), catalogSelect,
            refreshBtn,
            seedDefaultBtn,
            versionField,
            new Label("Default Transit:"), defaultTransitCodeField,
            saveNewBtn,
            activateBtn,
            healthBtn,
            auditBtn,
            exportTxtBtn,
            exportJsonBtn
        );
    }

    static FlowPane createRowEditor(
        TextField selectedCode,
        TextField selectedStaff,
        TextField selectedNonStaff,
        Button updateRowBtn
    ) {
        return BillingPolicyUiSupport.createWrapRow(
            new Label("Selected:"),
            selectedCode,
            selectedStaff,
            selectedNonStaff,
            updateRowBtn
        );
    }

    static BorderPane createWrapper(FlowPane topActions, javafx.scene.control.TableView<BillingPolicyView.Row> table, FlowPane rowEditor) {
        BorderPane wrapper = new BorderPane();
        wrapper.setTop(topActions);
        wrapper.setCenter(table);
        wrapper.setBottom(rowEditor);
        BorderPane.setMargin(topActions, new Insets(0, 0, 8, 0));
        BorderPane.setMargin(rowEditor, new Insets(8, 0, 0, 0));
        VBox.setVgrow(wrapper, Priority.ALWAYS);
        return wrapper;
    }
}