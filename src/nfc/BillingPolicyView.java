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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

public class BillingPolicyView extends javafx.scene.layout.VBox {
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
    private final javafx.scene.control.Button liveHealthRefreshBtn = new javafx.scene.control.Button("Refresh");
    private final javafx.scene.control.Button liveHealthDetailsBtn = new javafx.scene.control.Button("Show Details");
    private final javafx.scene.control.Button backfillBtn = new javafx.scene.control.Button("Backfill Child Metadata");
    private final javafx.scene.layout.VBox liveHealthDetailsBox = new javafx.scene.layout.VBox(4);
    private final Label liveHealthVersionLabel = new Label("Version: -");
    private final Label liveHealthRowCountLabel = new Label("Rows: -");
    private final Label liveHealthTransitLabel = new Label("Registered Billing Model: -");
    private final Label liveHealthMissingLabel = new Label("Missing Required Codes: none");
    private final Label liveHealthGatewayLabel = new Label("Payment Mode: -");
    private final Timeline liveHealthTimeline;
    private final BillingPolicyStatusSupport.BillingPolicyStatusUi statusUi;

    private Map<String, Map<String, Long>> workingTable = new LinkedHashMap<>();

    public BillingPolicyView() {
        super(0);
        setFillWidth(true);

        liveHealthBadge.getStyleClass().add("app-status-badge");
        AppThemeSupport.applyStatusTone(liveHealthBadge, "app-status-badge", AppThemeSupport.Tone.WARNING);
        liveHealthMetaLabel.getStyleClass().add("app-muted-text");
        healthLabel.getStyleClass().add("app-helper-text");
        liveHealthRefreshBtn.setOnAction(e -> refreshLiveHealthStatus());
        liveHealthDetailsBtn.setOnAction(e -> toggleLiveHealthDetails());
        javafx.scene.layout.HBox titleRow = BillingPolicyLayoutSupport.createTitleRow(liveHealthBadge, liveHealthRefreshBtn, liveHealthDetailsBtn);

        versionField.setPromptText("Version (e.g. taska_zurah_2026)");
        versionField.setText("taska-zurah-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")));

        defaultTransitCodeField.setPromptText("Default transit code (optional)");
        defaultTransitCodeField.setText("");
        defaultTransitCodeField.setPrefColumnCount(16);

        catalogSelect.setItems(catalogs);
        catalogSelect.setPrefWidth(340);
        catalogSelect.setOnAction(e -> onCatalogSelected());
        AppThemeSupport.styleControls(catalogSelect, versionField, defaultTransitCodeField, selectedCode, selectedStaff, selectedNonStaff);

        javafx.scene.control.Button refreshBtn = new javafx.scene.control.Button("Refresh");
        refreshBtn.setOnAction(e -> {
            reloadCatalogs();
            refreshLiveHealthStatus();
        });

        javafx.scene.control.Button seedDefaultBtn = new javafx.scene.control.Button("Load Default Template");
        seedDefaultBtn.setOnAction(e -> loadDefaultTemplate());

        javafx.scene.control.Button saveNewBtn = new javafx.scene.control.Button("Save As New Version");
        saveNewBtn.setOnAction(e -> saveAsNewCatalog());

        javafx.scene.control.Button activateBtn = new javafx.scene.control.Button("Set Selected Active");
        activateBtn.setOnAction(e -> activateSelectedCatalog());

        javafx.scene.control.Button healthBtn = new javafx.scene.control.Button("Run Health Check");
        healthBtn.setOnAction(e -> runHealthCheckDialog());

        backfillBtn.setOnAction(e -> backfillChildMetadata());

        javafx.scene.control.Button auditBtn = new javafx.scene.control.Button("View Audit Log");
        auditBtn.setOnAction(e -> showAuditLogDialog());

        javafx.scene.control.Button exportTxtBtn = new javafx.scene.control.Button("Export Health TXT");
        exportTxtBtn.setOnAction(e -> exportHealthReportTxt());

        javafx.scene.control.Button exportJsonBtn = new javafx.scene.control.Button("Export Health JSON");
        exportJsonBtn.setOnAction(e -> exportHealthReportJson());

        BillingPolicyUiSupport.configureActionButtons(
            liveHealthRefreshBtn,
            liveHealthDetailsBtn,
            refreshBtn,
            seedDefaultBtn,
            saveNewBtn,
            activateBtn,
            healthBtn,
            backfillBtn,
            auditBtn,
            exportTxtBtn,
            exportJsonBtn
        );

        javafx.scene.layout.VBox topActions = BillingPolicyLayoutSupport.createTopActions(
            catalogSelect,
            refreshBtn,
            seedDefaultBtn,
            versionField,
            defaultTransitCodeField,
            saveNewBtn,
            activateBtn,
            healthBtn,
            backfillBtn,
            auditBtn,
            exportTxtBtn,
            exportJsonBtn
        );

        javafx.scene.layout.VBox detailsBox = BillingPolicyLayoutSupport.createLiveHealthDetailsBox(
            liveHealthVersionLabel,
            liveHealthRowCountLabel,
            liveHealthTransitLabel,
            liveHealthMissingLabel,
            liveHealthGatewayLabel
        );
        liveHealthDetailsBox.getChildren().setAll(detailsBox.getChildren());
        liveHealthDetailsBox.getStyleClass().setAll(detailsBox.getStyleClass());
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

        javafx.scene.control.Button updateRowBtn = new javafx.scene.control.Button("Update Row");
        updateRowBtn.setOnAction(e -> updateSelectedRow());
        BillingPolicyUiSupport.configureActionButtons(updateRowBtn);

        javafx.scene.layout.VBox rowEditor = BillingPolicyLayoutSupport.createRowEditor(selectedCode, selectedStaff, selectedNonStaff, updateRowBtn);
        VBox tableCard = BillingPolicyLayoutSupport.createTableCard(table);
        table.setMinHeight(420);

        VBox liveStatusCard = AppThemeSupport.createSectionCard(
            "Catalog Health",
            "Local validation and live backend health are shown together so you can spot issues before publishing changes.",
            healthLabel,
            liveHealthMetaLabel,
            liveHealthDetailsBox
        );
        liveStatusCard.getStyleClass().add("app-toolbar-card");

        VBox body = new VBox(12, liveStatusCard, topActions, tableCard, rowEditor);
        body.setPadding(new Insets(16, 18, 18, 18));
        body.setFillWidth(true);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        ScrollPane bodyScroll = AppThemeSupport.createPageBodyScrollWrapper(body);
        BorderPane shell = AppThemeSupport.createStandardPageShell(titleRow, bodyScroll);

        getChildren().setAll(shell);
        javafx.scene.layout.VBox.setVgrow(shell, Priority.ALWAYS);

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

    private void backfillChildMetadata() {
        nfc.BillingPolicyBackfillDialogSupport.showDialog(getWindow()).ifPresent(request -> {
            BillingPolicyBackfillProgressDialogSupport.ProgressHandle progressHandle = BillingPolicyBackfillProgressDialogSupport.showDialog(getWindow(), request);
            backfillBtn.setDisable(true);
            BillingPolicyAsyncSupport.backfillChildMetadataAsync(
                request,
                progressHandle::isStopRequested,
                progressHandle::update,
                result -> {
                    progressHandle.close();
                    backfillBtn.setDisable(false);
                    showSimple("Child Billing Metadata Backfill", buildBackfillSummary(result));
                },
                (header, ex) -> {
                    progressHandle.close();
                    backfillBtn.setDisable(false);
                    showError(header, ex);
                }
            );
        });
    }

    private String buildBackfillSummary(BillingPolicyAsyncSupport.ChildMetadataBackfillRunResult result) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String batchLabel = result.batchCount == 1 ? "batch" : "batches";
        String period = result.period.isBlank() ? "-" : result.period;

        if (result.canceledByUser) {
            lines.add("Stopped after " + result.batchCount + " " + batchLabel + " at operator request for period " + period + ".");
        } else if (result.requestedChildCount > 0) {
            String childLabel = result.requestedChildCount == 1 ? "child ID" : "child IDs";
            lines.add("Processed " + result.requestedChildCount + " requested " + childLabel + " for period " + period + " in " + result.batchCount + " " + batchLabel + ".");
        } else if (result.completedAllPages) {
            lines.add("Processed all available child records for period " + period + " in " + result.batchCount + " " + batchLabel + " of up to " + result.limit + " records each.");
        } else {
            lines.add("Processed " + result.batchCount + " " + batchLabel + " of up to " + result.limit + " child records for period " + period + ".");
        }

        lines.add("Scanned: " + result.scannedCount);
        lines.add("Patched: " + result.patchedCount);
        lines.add("Unchanged: " + result.unchangedCount);
        lines.add("Skipped migrated: " + result.skippedMigratedCount);
        lines.add("Failed: " + result.failedCount);
        lines.add("Force rerun: " + (result.force ? "Yes" : "No"));
        lines.add("Active Catalog Version: " + (result.activeCatalogVersion.isBlank() ? "-" : result.activeCatalogVersion));
        if (result.canceledByUser) {
            lines.add("Run status: paused at operator request.");
        }
        if (result.hasMore) {
            lines.add("More records remain. Resume cursor: " + (result.nextStartAfterId.isBlank() ? "-" : result.nextStartAfterId));
        }
        return String.join("\n", lines);
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
        if (result.updated()) {
            defaultTransitCodeField.setText(result.nextDefaultTransit());
            updateRowsFromWorkingTable();
            return;
        }
        if (!result.errorMessage().isEmpty()) {
            showSimple("Invalid number", result.errorMessage());
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
