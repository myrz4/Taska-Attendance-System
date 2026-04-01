package nfc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class BillingPolicyView extends VBox {
    private final ComboBox<BillingPolicyWorkflowSupport.CatalogItemOption> catalogSelect = new ComboBox<>();
    private final ObservableList<BillingPolicyWorkflowSupport.CatalogItemOption> catalogs = FXCollections.observableArrayList();
    private final TableView<Row> table = new TableView<>();
    private final ObservableList<Row> rows = FXCollections.observableArrayList();

    private final TextField versionField = new TextField();
    private final TextField defaultTransitCodeField = new TextField();
    private final TextField selectedCode = new TextField();
    private final TextField selectedStaff = new TextField();
    private final TextField selectedNonStaff = new TextField();
    private final Label healthLabel = new Label();
    private final Label liveHealthBadge = new Label("Live Backend: checking...");
    private final Label liveHealthMetaLabel = new Label("Last checked: pending");
    private final Button liveHealthRefreshBtn = new Button("Refresh");
    private final Button liveHealthDetailsBtn = new Button("Show Details");
    private final VBox liveHealthDetailsBox = new VBox(4);
    private final Label liveHealthVersionLabel = new Label("Version: -");
    private final Label liveHealthRowCountLabel = new Label("Rows: -");
    private final Label liveHealthTransitLabel = new Label("Resolved Default Transit: -");
    private final Label liveHealthMissingLabel = new Label("Missing Required Codes: none");
    private final Label liveHealthGatewayLabel = new Label("Payment Mode: -");
    private final Timeline liveHealthTimeline;
    private final BillingPolicyStatusSupport.BillingPolicyStatusUi statusUi;

    private Map<String, Map<String, Long>> workingTable = new LinkedHashMap<>();

    public BillingPolicyView() {
        setSpacing(10);
        setPadding(new Insets(12));

        liveHealthBadge.setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #7a5200; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 999;");
        liveHealthRefreshBtn.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 8 4 8;");
        liveHealthRefreshBtn.setOnAction(e -> refreshLiveHealthStatus());
        liveHealthDetailsBtn.setOnAction(e -> toggleLiveHealthDetails());
        HBox titleRow = BillingPolicyLayoutSupport.createTitleRow(liveHealthBadge, liveHealthRefreshBtn, liveHealthDetailsBtn);

        versionField.setPromptText("Version (e.g. pdf-2026-03-19)");
        versionField.setText("catalog-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")));

        defaultTransitCodeField.setPromptText("Default transit monthly code");
        defaultTransitCodeField.setText("transit_2h_month");
        defaultTransitCodeField.setPrefColumnCount(16);

        catalogSelect.setItems(catalogs);
        catalogSelect.setPrefWidth(340);
        catalogSelect.setOnAction(e -> onCatalogSelected());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> {
            reloadCatalogs();
            refreshLiveHealthStatus();
        });

        Button seedDefaultBtn = new Button("Load Default Template");
        seedDefaultBtn.setOnAction(e -> loadDefaultTemplate());

        Button saveNewBtn = new Button("Save As New Version");
        saveNewBtn.setOnAction(e -> saveAsNewCatalog());

        Button activateBtn = new Button("Set Selected Active");
        activateBtn.setOnAction(e -> activateSelectedCatalog());

        Button healthBtn = new Button("Run Health Check");
        healthBtn.setOnAction(e -> runHealthCheckDialog());

        Button auditBtn = new Button("View Audit Log");
        auditBtn.setOnAction(e -> showAuditLogDialog());

        Button exportTxtBtn = new Button("Export Health TXT");
        exportTxtBtn.setOnAction(e -> exportHealthReportTxt());

        Button exportJsonBtn = new Button("Export Health JSON");
        exportJsonBtn.setOnAction(e -> exportHealthReportJson());

        BillingPolicyUiSupport.configureActionButtons(
            liveHealthRefreshBtn,
            liveHealthDetailsBtn,
            refreshBtn,
            seedDefaultBtn,
            saveNewBtn,
            activateBtn,
            healthBtn,
            auditBtn,
            exportTxtBtn,
            exportJsonBtn
        );

        FlowPane topActions = BillingPolicyLayoutSupport.createTopActions(
            catalogSelect,
            refreshBtn,
            seedDefaultBtn,
            versionField,
            defaultTransitCodeField,
            saveNewBtn,
            activateBtn,
            healthBtn,
            auditBtn,
            exportTxtBtn,
            exportJsonBtn
        );

        healthLabel.setStyle("-fx-font-weight: bold;");
        liveHealthMetaLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #476150;");
        VBox detailsBox = BillingPolicyLayoutSupport.createLiveHealthDetailsBox(
            liveHealthVersionLabel,
            liveHealthRowCountLabel,
            liveHealthTransitLabel,
            liveHealthMissingLabel,
            liveHealthGatewayLabel
        );
        liveHealthDetailsBox.getChildren().setAll(detailsBox.getChildren());
        liveHealthDetailsBox.setPadding(detailsBox.getPadding());
        liveHealthDetailsBox.setStyle(detailsBox.getStyle());
        liveHealthDetailsBox.setVisible(detailsBox.isVisible());
        liveHealthDetailsBox.setManaged(detailsBox.isManaged());
        statusUi = new BillingPolicyStatusSupport.BillingPolicyStatusUi(
            liveHealthBadge,
            liveHealthMetaLabel,
            liveHealthDetailsBtn,
            liveHealthDetailsBox,
            liveHealthVersionLabel,
            liveHealthRowCountLabel,
            liveHealthTransitLabel,
            liveHealthMissingLabel,
            liveHealthGatewayLabel
        );

        BillingPolicyTableSupport.setupTable(
            table,
            rows,
            row -> BillingPolicyEditorSupport.populateEditorFields(row, selectedCode, selectedStaff, selectedNonStaff)
        );

        selectedCode.setEditable(false);
        selectedCode.setPromptText("Code");
        selectedCode.setPrefWidth(220);
        selectedStaff.setPromptText("Staff (sen)");
        selectedStaff.setPrefWidth(140);
        selectedNonStaff.setPromptText("Non-staff (sen)");
        selectedNonStaff.setPrefWidth(140);

        Button updateRowBtn = new Button("Update Row");
        updateRowBtn.setOnAction(e -> updateSelectedRow());
        BillingPolicyUiSupport.configureActionButtons(updateRowBtn);

        FlowPane rowEditor = BillingPolicyLayoutSupport.createRowEditor(selectedCode, selectedStaff, selectedNonStaff, updateRowBtn);

        BorderPane wrapper = BillingPolicyLayoutSupport.createWrapper(topActions, table, rowEditor);

        getChildren().addAll(titleRow, healthLabel, liveHealthMetaLabel, liveHealthDetailsBox, wrapper);
        VBox.setVgrow(wrapper, Priority.ALWAYS);

        liveHealthTimeline = new Timeline(new KeyFrame(Duration.seconds(45), e -> refreshLiveHealthStatus()));
        liveHealthTimeline.setCycleCount(Timeline.INDEFINITE);
        liveHealthTimeline.play();

        loadDefaultTemplate();
        reloadCatalogs();
        refreshLiveHealthStatus();
    }

    private void updateRowsFromWorkingTable() {
        BillingPolicyWorkingTableSupport.refreshRows(rows, workingTable);
        updateHealthLabel();
    }

    private List<String> validateWorkingTable() {
        return BillingPolicyWorkingTableSupport.validateWorkingTable(workingTable);
    }

    private void updateHealthLabel() {
        BillingPolicyWorkingTableSupport.updateHealthLabel(healthLabel, validateWorkingTable());
    }

    private void runHealthCheckDialog() {
        String localSummary = BillingPolicyStatusSupport.localHealthSummary(validateWorkingTable());
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            String remoteSummary = BillingPolicyRemoteSupport.fetchRemoteHealthSummary().summary;
            Platform.runLater(() -> showSimple("Catalog Health", localSummary + "\n\n" + remoteSummary));
        });
    }

    private void refreshLiveHealthStatus() {
        BillingPolicyStatusSupport.refreshLiveHealthStatus(statusUi);
    }

    private void toggleLiveHealthDetails() {
        BillingPolicyStatusSupport.toggleLiveHealthDetails(statusUi);
    }

    private void showAuditLogDialog() {
        BillingPolicyStatusSupport.showAuditLogDialog(getScene() == null ? null : getScene().getWindow(), new BillingPolicyStatusSupport.AlertSink() {
            @Override
            public void showInfo(String header, String message) {
                showSimple(header, message);
            }

            @Override
            public void showError(String header, Exception ex) {
                BillingPolicyView.this.showError(header, ex);
            }
        });
    }

    private Window getWindow() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private Map<String, Object> healthReportPayload() {
        BillingPolicyWorkflowSupport.CatalogItemOption sel = catalogSelect.getValue();
        List<String> errors = validateWorkingTable();
        return BillingPolicyCommandSupport.healthReportPayload(
            String.valueOf(UserSession.getUsername()),
            sel == null ? "" : sel.id,
            sel == null ? "" : sel.version,
            versionField.getText() == null ? "" : versionField.getText().trim(),
            resolveDefaultTransitCodeForWorkingTable(),
            workingTable,
            errors
        );
    }

    private String buildHealthTxt() {
        return BillingPolicyCommandSupport.buildHealthTxt(healthReportPayload(), workingTable);
    }

    private void exportHealthReportTxt() {
        BillingPolicyExportSupport.exportHealthReportTxt(getWindow(), this::buildHealthTxt, new BillingPolicyExportSupport.AlertSink() {
            @Override
            public void showInfo(String header, String message) {
                showSimple(header, message);
            }

            @Override
            public void showError(String header, Exception ex) {
                BillingPolicyView.this.showError(header, ex);
            }
        });
    }

    private void exportHealthReportJson() {
        BillingPolicyExportSupport.exportHealthReportJson(getWindow(), this::healthReportPayload, new BillingPolicyExportSupport.AlertSink() {
            @Override
            public void showInfo(String header, String message) {
                showSimple(header, message);
            }

            @Override
            public void showError(String header, Exception ex) {
                BillingPolicyView.this.showError(header, ex);
            }
        });
    }

    private void loadDefaultTemplate() {
        workingTable = BillingPolicyCatalogSupport.defaultTemplate();
        defaultTransitCodeField.setText(resolveDefaultTransitCodeForWorkingTable());
        updateRowsFromWorkingTable();
    }

    private String resolveDefaultTransitCodeForWorkingTable() {
        return BillingPolicyCatalogSupport.resolveDefaultTransitCodeForWorkingTable(workingTable, defaultTransitCodeField.getText());
    }

    private void reloadCatalogs() {
        catalogs.clear();
        BillingPolicyAsyncSupport.reloadCatalogsAsync(
            items -> {
                catalogs.setAll(items);
                if (!catalogs.isEmpty() && catalogSelect.getValue() == null) {
                    catalogSelect.getSelectionModel().select(0);
                }
            },
            (header, ex) -> showError(header, ex)
        );
    }

    private void onCatalogSelected() {
        BillingPolicyWorkflowSupport.CatalogItemOption sel = catalogSelect.getValue();
        if (sel == null || sel.doc == null) return;
        BillingPolicyCommandSupport.CatalogSelectionState state = BillingPolicyCommandSupport.toCatalogSelectionState(sel.doc, sel.version);
        if (state.table.isEmpty()) return;

        workingTable = state.table;
        defaultTransitCodeField.setText(state.selectedDefaultTransit.isEmpty() ? resolveDefaultTransitCodeForWorkingTable() : state.selectedDefaultTransit);
        versionField.setText(state.suggestedVersion);
        updateRowsFromWorkingTable();
    }

    private void updateSelectedRow() {
        BillingPolicyEditorSupport.ApplyEditedRowResult result = BillingPolicyEditorSupport.applyEditedRow(
            workingTable,
            selectedCode.getText(),
            selectedStaff.getText(),
            selectedNonStaff.getText(),
            defaultTransitCodeField.getText(),
            this::resolveDefaultTransitCodeForWorkingTable
        );
        if (result.updated) {
            defaultTransitCodeField.setText(result.nextDefaultTransit);
            updateRowsFromWorkingTable();
            return;
        }
        if (!result.errorMessage.isEmpty()) {
            showSimple("Invalid number", result.errorMessage);
        }
    }

    private void saveAsNewCatalog() {
        BillingPolicyActionSupport.saveAsNewCatalog(
            versionField.getText(),
            workingTable,
            defaultTransitCodeField.getText(),
            defaultTransitCodeField::setText,
            this::refreshCatalogState,
            new BillingPolicyActionSupport.AlertSink() {
                @Override
                public void showInfo(String header, String message) {
                    showSimple(header, message);
                }

                @Override
                public void showError(String header, Exception ex) {
                    BillingPolicyView.this.showError(header, ex);
                }
            }
        );
    }

    private void activateSelectedCatalog() {
        BillingPolicyActionSupport.activateSelectedCatalog(
            catalogSelect.getValue(),
            workingTable,
            defaultTransitCodeField.getText(),
            defaultTransitCodeField::setText,
            this::refreshCatalogState,
            new BillingPolicyActionSupport.AlertSink() {
                @Override
                public void showInfo(String header, String message) {
                    showSimple(header, message);
                }

                @Override
                public void showError(String header, Exception ex) {
                    BillingPolicyView.this.showError(header, ex);
                }
            }
        );
    }

    private void refreshCatalogState() {
        reloadCatalogs();
        refreshLiveHealthStatus();
    }

    private void showSimple(String header, String message) {
        BillingPolicyUiSupport.showInfo(getWindow(), header, message);
    }

    private void showError(String header, Exception ex) {
        BillingPolicyUiSupport.showError(getWindow(), header, ex);
    }

    public static final class Row {
        private final String code;
        private final long staff;
        private final long nonStaff;

        public Row(String code, long staff, long nonStaff) {
            this.code = code;
            this.staff = staff;
            this.nonStaff = nonStaff;
        }

        public String getCode() { return code; }
        public long getStaff() { return staff; }
        public long getNonStaff() { return nonStaff; }
    }

}
