package nfc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class BillingPolicyView extends VBox {
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private static final List<String> REQUIRED_CODES = Arrays.asList(
        "monthly_fulltime_3m_2y",
        "monthly_fulltime_2y_4y",
        "transit_halfday_month",
        "transit_2h_month",
        "transit_schoolholiday_month",
        "transit_1day",
        "transit_1week",
        "transit_1hour",
        "overtime_after_530",
        "overtime_8pm_12am",
        "overtime_12am_7am",
        "transport_tadika_month",
        "registration_fulltime_oneoff",
        "registration_transit_oneoff",
        "annual_fee_yearly",
        "comms_book_4months",
        "insurance_yearly_age2plus"
    );

    private static final List<String> AUDIT_ACTION_OPTIONS = Arrays.asList(
        "all",
        "catalog_saved",
        "catalog_activated"
    );

    private final ComboBox<CatalogItem> catalogSelect = new ComboBox<>();
    private final ObservableList<CatalogItem> catalogs = FXCollections.observableArrayList();
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
    private final Timeline liveHealthTimeline;

    private Map<String, Map<String, Long>> workingTable = new LinkedHashMap<>();

    public BillingPolicyView() {
        setSpacing(10);
        setPadding(new Insets(12));

        Label title = new Label("Billing Policy Catalog");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #1d2f24;");

        liveHealthBadge.setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #7a5200; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 999;");
        liveHealthRefreshBtn.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 8 4 8;");
        liveHealthRefreshBtn.setOnAction(e -> refreshLiveHealthStatus());
        liveHealthDetailsBtn.setOnAction(e -> toggleLiveHealthDetails());
        HBox titleRow = new HBox(8, title, liveHealthRefreshBtn, liveHealthDetailsBtn, liveHealthBadge);
        titleRow.setAlignment(Pos.CENTER_LEFT);

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

        configureActionButtons(
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

        FlowPane topActions = createWrapRow(
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

        healthLabel.setStyle("-fx-font-weight: bold;");
        liveHealthMetaLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #476150;");
        liveHealthDetailsBox.getChildren().setAll(
            liveHealthVersionLabel,
            liveHealthRowCountLabel,
            liveHealthTransitLabel,
            liveHealthMissingLabel
        );
        liveHealthDetailsBox.setPadding(new Insets(8, 12, 8, 12));
        liveHealthDetailsBox.setStyle("-fx-background-color: rgba(255,243,205,0.70); -fx-border-color: #d6b656; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 8 12 8 12;");
        liveHealthDetailsBox.setVisible(false);
        liveHealthDetailsBox.setManaged(false);

        buildTable();

        selectedCode.setEditable(false);
        selectedCode.setPromptText("Code");
        selectedCode.setPrefWidth(220);
        selectedStaff.setPromptText("Staff (sen)");
        selectedStaff.setPrefWidth(140);
        selectedNonStaff.setPromptText("Non-staff (sen)");
        selectedNonStaff.setPrefWidth(140);

        Button updateRowBtn = new Button("Update Row");
        updateRowBtn.setOnAction(e -> updateSelectedRow());
        configureActionButtons(updateRowBtn);

        FlowPane rowEditor = createWrapRow(
            new Label("Selected:"),
            selectedCode,
            selectedStaff,
            selectedNonStaff,
            updateRowBtn
        );

        BorderPane wrapper = new BorderPane();
        wrapper.setTop(topActions);
        wrapper.setCenter(table);
        wrapper.setBottom(rowEditor);
        BorderPane.setMargin(topActions, new Insets(0, 0, 8, 0));
        BorderPane.setMargin(rowEditor, new Insets(8, 0, 0, 0));

        getChildren().addAll(titleRow, healthLabel, liveHealthMetaLabel, liveHealthDetailsBox, wrapper);
        VBox.setVgrow(wrapper, Priority.ALWAYS);

        liveHealthTimeline = new Timeline(new KeyFrame(Duration.seconds(45), e -> refreshLiveHealthStatus()));
        liveHealthTimeline.setCycleCount(Timeline.INDEFINITE);
        liveHealthTimeline.play();

        loadDefaultTemplate();
        reloadCatalogs();
        refreshLiveHealthStatus();
    }

    private void buildTable() {
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Row, String> codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(new PropertyValueFactory<>("code"));
        codeCol.setMinWidth(320);

        TableColumn<Row, Long> staffCol = new TableColumn<>("Staff (sen)");
        staffCol.setCellValueFactory(new PropertyValueFactory<>("staff"));
        staffCol.setMinWidth(140);

        TableColumn<Row, Long> nonStaffCol = new TableColumn<>("Non-staff (sen)");
        nonStaffCol.setCellValueFactory(new PropertyValueFactory<>("nonStaff"));
        nonStaffCol.setMinWidth(160);

        table.getColumns().clear();
        table.getColumns().add(codeCol);
        table.getColumns().add(staffCol);
        table.getColumns().add(nonStaffCol);
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n == null) return;
            selectedCode.setText(n.getCode());
            selectedStaff.setText(String.valueOf(n.getStaff()));
            selectedNonStaff.setText(String.valueOf(n.getNonStaff()));
        });
    }

    private void updateRowsFromWorkingTable() {
        rows.clear();
        for (Map.Entry<String, Map<String, Long>> e : workingTable.entrySet()) {
            Map<String, Long> v = e.getValue();
            rows.add(new Row(e.getKey(), toLong(v.get("staff")), toLong(v.get("nonstaff"))));
        }
        updateHealthLabel();
    }

    private List<String> validateWorkingTable() {
        List<String> errors = new ArrayList<>();

        for (String code : REQUIRED_CODES) {
            Map<String, Long> row = workingTable.get(code);
            if (row == null) {
                errors.add("Missing required code: " + code);
                continue;
            }
            long staff = toLong(row.get("staff"));
            long nonStaff = toLong(row.get("nonstaff"));
            if (staff < 0) errors.add("Negative staff value for: " + code);
            if (nonStaff < 0) errors.add("Negative non-staff value for: " + code);
        }

        for (Map.Entry<String, Map<String, Long>> e : workingTable.entrySet()) {
            String code = e.getKey();
            Map<String, Long> row = e.getValue();
            if (row == null) {
                errors.add("Invalid row for: " + code);
                continue;
            }
            if (!row.containsKey("staff") || !row.containsKey("nonstaff")) {
                errors.add("Row missing staff/nonstaff keys: " + code);
                continue;
            }
            long staff = toLong(row.get("staff"));
            long nonStaff = toLong(row.get("nonstaff"));
            if (staff < 0 || nonStaff < 0) {
                errors.add("Negative values are not allowed: " + code);
            }
        }

        return errors;
    }

    private void updateHealthLabel() {
        List<String> errors = validateWorkingTable();
        if (errors.isEmpty()) {
            healthLabel.setText("Catalog health: OK (all required billing codes present)");
            healthLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1b5e20;");
        } else {
            healthLabel.setText("Catalog health: INVALID (" + errors.size() + " issue(s))");
            healthLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #b71c1c;");
        }
    }

    private void runHealthCheckDialog() {
        List<String> errors = validateWorkingTable();
        String localSummary = errors.isEmpty()
            ? "Local: OK (all required billing codes are present and valid)."
            : "Local: INVALID\n" + String.join("\n", errors);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            String remoteSummary = fetchRemoteHealthSummary().summary;
            Platform.runLater(() -> showSimple("Catalog Health", localSummary + "\n\n" + remoteSummary));
        });
    }

    private void refreshLiveHealthStatus() {
        liveHealthBadge.setText("Live Backend: checking...");
        liveHealthBadge.setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #7a5200; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 999;");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            RemoteHealthSnapshot snapshot = fetchRemoteHealthSummary();
            Platform.runLater(() -> applyRemoteHealthSnapshot(snapshot));
        });
    }

    private void applyRemoteHealthSnapshot(RemoteHealthSnapshot snapshot) {
        String checkedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        liveHealthMetaLabel.setText("Last checked: " + checkedAt);
        if (snapshot == null) {
            liveHealthBadge.setText("Live Backend: error");
            liveHealthBadge.setStyle("-fx-background-color: #fde2e1; -fx-text-fill: #9f1d1d; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 999;");
            liveHealthDetailsBox.setStyle("-fx-background-color: rgba(253,226,225,0.78); -fx-border-color: #d36b6b; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 8 12 8 12;");
            setLiveHealthDetails("-", "-", "-", "unknown");
            return;
        }
        if (!snapshot.ok) {
            liveHealthBadge.setText("Live Backend: failed");
            liveHealthBadge.setStyle("-fx-background-color: #fde2e1; -fx-text-fill: #9f1d1d; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 999;");
            liveHealthDetailsBox.setStyle("-fx-background-color: rgba(253,226,225,0.78); -fx-border-color: #d36b6b; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 8 12 8 12;");
            setLiveHealthDetails(snapshot.version, snapshot.rowCount, snapshot.resolvedTransit, snapshot.missingSummary);
            return;
        }
        if (snapshot.valid) {
            liveHealthBadge.setText("Live Backend: healthy");
            liveHealthBadge.setStyle("-fx-background-color: #dff6e4; -fx-text-fill: #17643a; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 999;");
            liveHealthDetailsBox.setStyle("-fx-background-color: rgba(223,246,228,0.78); -fx-border-color: #52a071; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 8 12 8 12;");
            setLiveHealthDetails(snapshot.version, snapshot.rowCount, snapshot.resolvedTransit, snapshot.missingSummary);
            return;
        }
        liveHealthBadge.setText("Live Backend: invalid");
        liveHealthBadge.setStyle("-fx-background-color: #fff1d6; -fx-text-fill: #8a5800; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 999;");
        liveHealthDetailsBox.setStyle("-fx-background-color: rgba(255,241,214,0.82); -fx-border-color: #d39a32; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 8 12 8 12;");
        setLiveHealthDetails(snapshot.version, snapshot.rowCount, snapshot.resolvedTransit, snapshot.missingSummary);
    }

    private void toggleLiveHealthDetails() {
        boolean nextVisible = !liveHealthDetailsBox.isVisible();
        liveHealthDetailsBox.setVisible(nextVisible);
        liveHealthDetailsBox.setManaged(nextVisible);
        liveHealthDetailsBtn.setText(nextVisible ? "Hide Details" : "Show Details");
    }

    private void showAuditLogDialog() {
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
        AtomicReference<AuditLogSnapshot> currentSnapshot = new AtomicReference<>(new AuditLogSnapshot(
            "all",
            Collections.emptyList(),
            "Loading recent audit entries..."
        ));

        refreshBtn.setOnAction(e -> refreshAuditLog(body, currentSnapshot, normalizeAuditActionFilter(actionFilter.getValue())));
        actionFilter.setOnAction(e -> refreshAuditLog(body, currentSnapshot, normalizeAuditActionFilter(actionFilter.getValue())));
        exportTxtBtn.setOnAction(e -> exportAuditLogTxt(currentSnapshot.get()));
        exportJsonBtn.setOnAction(e -> exportAuditLogJson(currentSnapshot.get()));

        auditDialog.getDialogPane().setContent(content);
        auditDialog.getDialogPane().setMinWidth(860);
        auditDialog.show();

        refreshAuditLog(body, currentSnapshot, "all");
    }

    private FlowPane createWrapRow(Node... nodes) {
        FlowPane row = new FlowPane();
        row.setHgap(8);
        row.setVgap(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(nodes);
        return row;
    }

    private void configureActionButtons(Button... buttons) {
        for (Button button : buttons) {
            button.setWrapText(true);
            button.setMinHeight(32);
            button.setMaxWidth(150);
        }
    }

    private void setLiveHealthDetails(String version, String rowCount, String resolvedTransit, String missingSummary) {
        liveHealthVersionLabel.setText("Version: " + String.valueOf(version == null || version.isBlank() ? "-" : version));
        liveHealthRowCountLabel.setText("Rows: " + String.valueOf(rowCount == null || rowCount.isBlank() ? "-" : rowCount));
        liveHealthTransitLabel.setText("Resolved Default Transit: " + String.valueOf(resolvedTransit == null || resolvedTransit.isBlank() ? "-" : resolvedTransit));
        liveHealthMissingLabel.setText("Missing Required Codes: " + String.valueOf(missingSummary == null || missingSummary.isBlank() ? "none" : missingSummary));
    }

    private RemoteHealthSnapshot fetchRemoteHealthSummary() {
        String projectId = FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "projectId");
        String idToken = UserSession.getIdToken();
        if (projectId == null || projectId.isBlank()) {
            return new RemoteHealthSnapshot(false, false,
                "Live Backend: unavailable (missing projectId in firebase.properties).", "-", "-", "-", "unknown");
        }
        if (idToken == null || idToken.isBlank()) {
            return new RemoteHealthSnapshot(false, false,
                "Live Backend: unavailable (missing login token).", "-", "-", "-", "unknown");
        }

        try {
            FirebaseFunctionsClient.CallResult res = FirebaseFunctionsClient.callBillingGetHealth(projectId.trim(), idToken);
            Map<?, ?> result = parseCallableResultMap(res.rawBody);
            Map<?, ?> health = (result.get("health") instanceof Map) ? (Map<?, ?>) result.get("health") : Collections.emptyMap();

            boolean ok = res.ok;
            String reason = humanizeCallableReason(res.reason, result);
            Object validObj = health.get("isValid");
            boolean valid = validObj instanceof Boolean && (Boolean) validObj;

            String version = String.valueOf(health.get("version") == null ? "" : health.get("version"));
            String resolvedTransit = String.valueOf(health.get("resolvedDefaultTransitCode") == null ? "" : health.get("resolvedDefaultTransitCode"));
            String rowCount = String.valueOf(health.get("rowCount") == null ? "0" : health.get("rowCount"));

            List<?> missing = (health.get("missingRequiredCodes") instanceof List)
                ? (List<?>) health.get("missingRequiredCodes")
                : Collections.emptyList();
            String missingSummary = missing.isEmpty() ? "none" : joinList(missing);

            StringBuilder sb = new StringBuilder();
            sb.append("Live Backend: ").append(ok ? "OK" : "FAILED");
            if (!reason.isBlank()) sb.append(" (").append(reason).append(")");
            sb.append("\n");
            sb.append("Version: ").append(version).append("\n");
            sb.append("Rows: ").append(rowCount).append("\n");
            sb.append("Resolved Default Transit: ").append(resolvedTransit).append("\n");
            sb.append("Catalog Valid: ").append(valid ? "YES" : "NO");

            if (!missing.isEmpty()) {
                sb.append("\nMissing Required Codes: ");
                sb.append(missingSummary);
            }
            return new RemoteHealthSnapshot(ok, valid, sb.toString(), version, rowCount, resolvedTransit, missingSummary);
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            return new RemoteHealthSnapshot(false, false, "Live Backend: ERROR (" + ex.getMessage() + ")", "-", "-", "-", "unknown");
        }
    }

    private String joinList(List<?> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.valueOf(items.get(i)));
        }
        return sb.toString();
    }

    private void refreshAuditLog(TextArea body, AtomicReference<AuditLogSnapshot> currentSnapshot, String actionFilter) {
        body.setText("Loading recent audit entries...");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            AuditLogSnapshot snapshot = fetchAuditLogSnapshot(actionFilter);
            Platform.runLater(() -> {
                currentSnapshot.set(snapshot);
                body.setText(snapshot.formattedText);
            });
        });
    }

    private AuditLogSnapshot fetchAuditLogSnapshot(String actionFilter) {
        String projectId = FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "projectId");
        String idToken = UserSession.getIdToken();
        if (projectId == null || projectId.isBlank()) {
            return new AuditLogSnapshot(actionFilter, Collections.emptyList(),
                "Audit log unavailable: missing projectId in firebase.properties.");
        }
        if (idToken == null || idToken.isBlank()) {
            return new AuditLogSnapshot(actionFilter, Collections.emptyList(),
                "Audit log unavailable: missing login token.");
        }

        try {
            FirebaseFunctionsClient.CallResult res = FirebaseFunctionsClient.callBillingAdminListAudit(
                projectId.trim(),
                idToken,
                GSON_PRETTY.toJson(Collections.singletonMap("limit", 100))
            );
            Map<?, ?> result = parseCallableResultMap(res.rawBody);
            if (!(result.get("ok") instanceof Boolean) || !((Boolean) result.get("ok"))) {
                throw callableFailure(res.reason, result);
            }
            List<?> rawEntries = (result.get("entries") instanceof List)
                ? (List<?>) result.get("entries")
                : Collections.emptyList();
            List<Map<String, Object>> filteredEntries = filterAuditEntries(rawEntries, actionFilter);
            return new AuditLogSnapshot(actionFilter, filteredEntries, formatAuditEntries(filteredEntries, actionFilter));
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            return new AuditLogSnapshot(actionFilter, Collections.emptyList(),
                "Audit log load failed: " + ex.getMessage());
        }
    }

    private List<Map<String, Object>> filterAuditEntries(List<?> entries, String actionFilter) {
        List<Map<String, Object>> filtered = new ArrayList<>();
        String normalizedFilter = normalizeAuditActionFilter(actionFilter);
        for (Object raw : entries) {
            if (!(raw instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) raw;
            String action = String.valueOf(entry.get("action") == null ? "" : entry.get("action")).trim();
            if (!"all".equals(normalizedFilter) && !normalizedFilter.equalsIgnoreCase(action)) {
                continue;
            }
            filtered.add(entry);
        }
        return filtered;
    }

    private String normalizeAuditActionFilter(String actionFilter) {
        String normalized = String.valueOf(actionFilter == null ? "all" : actionFilter).trim().toLowerCase();
        return normalized.isEmpty() ? "all" : normalized;
    }

    private String formatAuditEntries(List<?> entries, String actionFilter) {
        if (entries == null || entries.isEmpty()) {
            if ("all".equals(normalizeAuditActionFilter(actionFilter))) {
                return "No billing audit entries found.";
            }
            return "No billing audit entries found for action filter: " + normalizeAuditActionFilter(actionFilter);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Recent Billing Audit Entries\n");
        sb.append("===========================\n\n");
        sb.append("Filter: ").append(normalizeAuditActionFilter(actionFilter)).append("\n");
        sb.append("Entries: ").append(entries.size()).append("\n\n");
        for (Object raw : entries) {
            if (!(raw instanceof Map)) continue;
            Map<?, ?> entry = (Map<?, ?>) raw;
            String createdAt = String.valueOf(entry.get("createdAt") == null ? "" : entry.get("createdAt"));
            String action = String.valueOf(entry.get("action") == null ? "" : entry.get("action"));
            String version = String.valueOf(entry.get("version") == null ? "" : entry.get("version"));
            String catalogId = String.valueOf(entry.get("catalogId") == null ? "" : entry.get("catalogId"));
            String actorUid = String.valueOf(entry.get("actorUid") == null ? "" : entry.get("actorUid"));
            String actorRole = String.valueOf(entry.get("actorRole") == null ? "" : entry.get("actorRole"));
            String actorEmail = String.valueOf(entry.get("actorEmail") == null ? "" : entry.get("actorEmail"));
            String actorPhone = String.valueOf(entry.get("actorPhoneE164") == null ? "" : entry.get("actorPhoneE164"));
            String detailsSummary = formatAuditDetails(entry.get("details"));

            sb.append("Time: ").append(createdAt.isBlank() ? "-" : createdAt).append("\n");
            sb.append("Action: ").append(action.isBlank() ? "-" : action).append("\n");
            sb.append("Version: ").append(version.isBlank() ? "-" : version).append("\n");
            sb.append("Catalog ID: ").append(catalogId.isBlank() ? "-" : catalogId).append("\n");
            sb.append("Actor UID: ").append(actorUid.isBlank() ? "-" : actorUid).append("\n");
            sb.append("Actor Role: ").append(actorRole.isBlank() ? "-" : actorRole).append("\n");
            sb.append("Actor Email: ").append(actorEmail.isBlank() ? "-" : actorEmail).append("\n");
            sb.append("Actor Phone: ").append(actorPhone.isBlank() ? "-" : actorPhone).append("\n");
            sb.append("Details: ").append(detailsSummary).append("\n");
            sb.append("----------------------------------------\n");
        }
        return sb.toString();
    }

    private void exportAuditLogTxt(AuditLogSnapshot snapshot) {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Billing Audit Log (TXT)");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
            fc.setInitialFileName("billing-audit-" + normalizeAuditActionFilter(snapshot.actionFilter)
                + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".txt");

            File f = fc.showSaveDialog(getWindow());
            if (f == null) return;

            try (PrintWriter out = new PrintWriter(new FileWriter(f))) {
                out.print(snapshot.formattedText);
            }
            showSimple("Exported", "Audit log saved to:\n" + f.getAbsolutePath());
        } catch (IOException | SecurityException ex) {
            showError("Failed to export audit TXT", ex);
        }
    }

    private void exportAuditLogJson(AuditLogSnapshot snapshot) {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Billing Audit Log (JSON)");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
            fc.setInitialFileName("billing-audit-" + normalizeAuditActionFilter(snapshot.actionFilter)
                + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".json");

            File f = fc.showSaveDialog(getWindow());
            if (f == null) return;

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            payload.put("actionFilter", normalizeAuditActionFilter(snapshot.actionFilter));
            payload.put("entryCount", snapshot.entries.size());
            payload.put("entries", snapshot.entries);

            try (PrintWriter out = new PrintWriter(new FileWriter(f))) {
                out.print(GSON_PRETTY.toJson(payload));
            }
            showSimple("Exported", "Audit log saved to:\n" + f.getAbsolutePath());
        } catch (IOException | SecurityException ex) {
            showError("Failed to export audit JSON", ex);
        }
    }

    private String formatAuditDetails(Object detailsObj) {
        if (!(detailsObj instanceof Map)) {
            return "-";
        }
        Map<?, ?> details = (Map<?, ?>) detailsObj;
        List<String> parts = new ArrayList<>();
        Object rowCount = details.get("rowCount");
        if (rowCount != null) {
            parts.add("rows=" + rowCount);
        }
        Object defaultTransit = details.get("defaultTransitMonthlyCode");
        if (defaultTransit != null && !String.valueOf(defaultTransit).isBlank()) {
            parts.add("defaultTransit=" + String.valueOf(defaultTransit));
        }
        Object missing = details.get("missingRequiredCodes");
        if (missing instanceof List && !((List<?>) missing).isEmpty()) {
            parts.add("missing=" + joinList((List<?>) missing));
        }
        return parts.isEmpty() ? GSON_PRETTY.toJson(details) : String.join(", ", parts);
    }

    private Map<?, ?> parseCallableResultMap(String rawBody) {
        Map<?, ?> top = GSON_PRETTY.fromJson(rawBody, Map.class);
        Object resultObj = (top instanceof Map) ? top.get("result") : null;
        if (resultObj instanceof Map) return (Map<?, ?>) resultObj;
        return Collections.emptyMap();
    }

    private String humanizeCallableReason(Object reasonObj, Map<?, ?> result) {
        String reason = String.valueOf(reasonObj == null ? "" : reasonObj).trim();
        if (reason.isEmpty() && result != null && result.get("reason") != null) {
            reason = String.valueOf(result.get("reason")).trim();
        }

        if ("admin-only".equalsIgnoreCase(reason)) {
            return "admin access required";
        }
        if ("invalid-catalog".equalsIgnoreCase(reason)) {
            Object healthObj = result == null ? null : result.get("health");
            if (healthObj instanceof Map) {
                Object missing = ((Map<?, ?>) healthObj).get("missingRequiredCodes");
                if (missing instanceof List && !((List<?>) missing).isEmpty()) {
                    return "invalid catalog: missing required codes";
                }
            }
            return "invalid catalog";
        }
        if ("missing-version".equalsIgnoreCase(reason)) {
            return "version is required";
        }
        if ("missing-catalogId".equalsIgnoreCase(reason)) {
            return "catalog selection is required";
        }
        if ("catalog-not-found".equalsIgnoreCase(reason)) {
            return "selected catalog no longer exists";
        }
        if ("http-401".equalsIgnoreCase(reason) || "unauthenticated".equalsIgnoreCase(reason)) {
            return "login expired, please sign in again";
        }
        if ("http-403".equalsIgnoreCase(reason) || "permission-denied".equalsIgnoreCase(reason)) {
            return "permission denied for this account";
        }
        return reason.isBlank() ? "unknown backend error" : reason;
    }

    private IllegalStateException callableFailure(String fallbackReason, Map<?, ?> result) {
        return new IllegalStateException(humanizeCallableReason(fallbackReason, result));
    }

    private Window getWindow() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private Map<String, Object> healthReportPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        CatalogItem sel = catalogSelect.getValue();
        List<String> errors = validateWorkingTable();

        payload.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        payload.put("generatedBy", String.valueOf(UserSession.getUsername()));
        payload.put("selectedCatalogId", sel == null ? "" : sel.id);
        payload.put("selectedCatalogVersion", sel == null ? "" : sel.version);
        payload.put("workingVersionLabel", versionField.getText() == null ? "" : versionField.getText().trim());
        payload.put("defaultTransitMonthlyCode", resolveDefaultTransitCodeForWorkingTable());
        payload.put("rowCount", workingTable.size());
        payload.put("requiredCodeCount", REQUIRED_CODES.size());
        payload.put("missingRequiredCodes", missingRequiredCodes());
        payload.put("isValid", errors.isEmpty());
        payload.put("issues", errors);
        payload.put("table", workingTable);
        return payload;
    }

    private List<String> missingRequiredCodes() {
        List<String> missing = new ArrayList<>();
        for (String code : REQUIRED_CODES) {
            if (!workingTable.containsKey(code)) {
                missing.add(code);
            }
        }
        return missing;
    }

    private String buildHealthTxt() {
        Map<String, Object> p = healthReportPayload();
        @SuppressWarnings("unchecked")
        List<String> issues = (List<String>) p.get("issues");

        StringBuilder sb = new StringBuilder();
        sb.append("Billing Catalog Health Report\n");
        sb.append("============================\n");
        sb.append("Generated At: ").append(String.valueOf(p.get("generatedAt"))).append("\n");
        sb.append("Generated By: ").append(String.valueOf(p.get("generatedBy"))).append("\n");
        sb.append("Selected Catalog ID: ").append(String.valueOf(p.get("selectedCatalogId"))).append("\n");
        sb.append("Selected Catalog Version: ").append(String.valueOf(p.get("selectedCatalogVersion"))).append("\n");
        sb.append("Working Version Label: ").append(String.valueOf(p.get("workingVersionLabel"))).append("\n");
        sb.append("Default Transit Monthly Code: ").append(String.valueOf(p.get("defaultTransitMonthlyCode"))).append("\n");
        sb.append("Rows: ").append(String.valueOf(p.get("rowCount"))).append("\n");
        sb.append("Required Codes: ").append(String.valueOf(p.get("requiredCodeCount"))).append("\n");
        sb.append("Valid: ").append(String.valueOf(p.get("isValid"))).append("\n\n");

        sb.append("Issues\n");
        sb.append("------\n");
        if (issues == null || issues.isEmpty()) {
            sb.append("None\n");
        } else {
            for (String i : issues) {
                sb.append("- ").append(i).append("\n");
            }
        }

        sb.append("\nRequired Code Coverage\n");
        sb.append("----------------------\n");
        for (String code : REQUIRED_CODES) {
            sb.append(workingTable.containsKey(code) ? "[OK] " : "[MISSING] ").append(code).append("\n");
        }

        return sb.toString();
    }

    private void exportHealthReportTxt() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Billing Health Report (TXT)");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
            fc.setInitialFileName("billing-health-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".txt");

            File f = fc.showSaveDialog(getWindow());
            if (f == null) return;

            try (PrintWriter out = new PrintWriter(new FileWriter(f))) {
                out.print(buildHealthTxt());
            }
            showSimple("Exported", "Health report saved to:\n" + f.getAbsolutePath());
        } catch (IOException | SecurityException ex) {
            showError("Failed to export TXT report", ex);
        }
    }

    private void exportHealthReportJson() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Billing Health Report (JSON)");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
            fc.setInitialFileName("billing-health-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".json");

            File f = fc.showSaveDialog(getWindow());
            if (f == null) return;

            try (PrintWriter out = new PrintWriter(new FileWriter(f))) {
                out.print(GSON_PRETTY.toJson(healthReportPayload()));
            }
            showSimple("Exported", "Health report saved to:\n" + f.getAbsolutePath());
        } catch (IOException | SecurityException ex) {
            showError("Failed to export JSON report", ex);
        }
    }

    private void loadDefaultTemplate() {
        Map<String, Map<String, Long>> t = new LinkedHashMap<>();
        putRow(t, "monthly_fulltime_3m_2y", 35000, 40000);
        putRow(t, "monthly_fulltime_2y_4y", 30000, 35000);
        putRow(t, "transit_halfday_month", 15000, 25000);
        putRow(t, "transit_2h_month", 10000, 18000);
        putRow(t, "transit_schoolholiday_month", 25000, 30000);
        putRow(t, "transit_1day", 1500, 2000);
        putRow(t, "transit_1week", 7000, 10000);
        putRow(t, "transit_1hour", 350, 400);
        putRow(t, "overtime_after_530", 500, 600);
        putRow(t, "overtime_8pm_12am", 1000, 1300);
        putRow(t, "overtime_12am_7am", 700, 1000);
        putRow(t, "transport_tadika_month", 15000, 15000);
        putRow(t, "registration_fulltime_oneoff", 10000, 10000);
        putRow(t, "registration_transit_oneoff", 5000, 5000);
        putRow(t, "annual_fee_yearly", 10000, 10000);
        putRow(t, "comms_book_4months", 1500, 1500);
        putRow(t, "insurance_yearly_age2plus", 2000, 2000);
        workingTable = t;
        defaultTransitCodeField.setText(resolveDefaultTransitCodeForWorkingTable());
        updateRowsFromWorkingTable();
    }

    private String normalizeCode(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    private boolean isTransitCodeValidForWorkingTable(String code) {
        String c = normalizeCode(code);
        if (c.isEmpty()) return false;
        if (!c.startsWith("transit_")) return false;
        return workingTable.containsKey(c);
    }

    private String resolveDefaultTransitCodeForWorkingTable() {
        String fromField = normalizeCode(defaultTransitCodeField.getText());
        if (isTransitCodeValidForWorkingTable(fromField)) return fromField;
        if (workingTable.containsKey("transit_2h_month")) return "transit_2h_month";
        if (workingTable.containsKey("transit_halfday_month")) return "transit_halfday_month";
        if (workingTable.containsKey("transit_schoolholiday_month")) return "transit_schoolholiday_month";
        for (String code : workingTable.keySet()) {
            if (code != null && code.startsWith("transit_")) return code;
        }
        return "";
    }

    private void putRow(Map<String, Map<String, Long>> tableMap, String code, long staff, long nonStaff) {
        Map<String, Long> row = new LinkedHashMap<>();
        row.put("staff", staff);
        row.put("nonstaff", nonStaff);
        tableMap.put(code, row);
    }

    private long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void reloadCatalogs() {
        catalogs.clear();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String projectId = FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "projectId");
                String idToken = UserSession.getIdToken();
                FirebaseFunctionsClient.CallResult res = FirebaseFunctionsClient.callBillingAdminListCatalogs(projectId, idToken);
                Map<?, ?> result = parseCallableResultMap(res.rawBody);
                if (!(result.get("ok") instanceof Boolean) || !((Boolean) result.get("ok"))) {
                    throw callableFailure(res.reason, result);
                }

                List<?> docs = (result.get("catalogs") instanceof List)
                    ? (List<?>) result.get("catalogs")
                    : Collections.emptyList();
                List<CatalogItem> items = new ArrayList<>();
                for (Object raw : docs) {
                    if (!(raw instanceof Map)) continue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> d = (Map<String, Object>) raw;
                    String id = String.valueOf(d.get("id") == null ? "" : d.get("id"));
                    String version = String.valueOf(d.get("version") == null ? "" : d.get("version"));
                    boolean active = Boolean.TRUE.equals(d.get("active"));
                    items.add(new CatalogItem(id, version, active, d));
                }
                Platform.runLater(() -> {
                    catalogs.setAll(items);
                    if (!catalogs.isEmpty() && catalogSelect.getValue() == null) {
                        catalogSelect.getSelectionModel().select(0);
                    }
                });
            } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
                Platform.runLater(() -> showError("Failed to load billing catalogs", ex));
            }
        });
    }

    private void onCatalogSelected() {
        CatalogItem sel = catalogSelect.getValue();
        if (sel == null || sel.doc == null) return;
        Object tableObj = sel.doc.get("table");
        if (!(tableObj instanceof Map)) return;

        Map<String, Map<String, Long>> next = new LinkedHashMap<>();
        Map<?, ?> raw = (Map<?, ?>) tableObj;
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            String code = String.valueOf(e.getKey());
            Object rv = e.getValue();
            if (!(rv instanceof Map)) continue;
            Map<?, ?> rowRaw = (Map<?, ?>) rv;
            Map<String, Long> row = new LinkedHashMap<>();
            row.put("staff", toLong(rowRaw.get("staff")));
            row.put("nonstaff", toLong(rowRaw.get("nonstaff")));
            next.put(code, row);
        }
        workingTable = next;
        String selectedDefaultTransit = normalizeCode(String.valueOf(sel.doc.get("defaultTransitMonthlyCode") == null ? "" : sel.doc.get("defaultTransitMonthlyCode")));
        defaultTransitCodeField.setText(selectedDefaultTransit.isEmpty() ? resolveDefaultTransitCodeForWorkingTable() : selectedDefaultTransit);
        versionField.setText(sel.version == null || sel.version.isBlank() ?
            ("catalog-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))) :
            sel.version + "-copy");
        updateRowsFromWorkingTable();
    }

    private void updateSelectedRow() {
        String code = selectedCode.getText() == null ? "" : selectedCode.getText().trim();
        if (code.isEmpty()) return;
        long staff;
        long nonStaff;
        try {
            staff = Long.parseLong(selectedStaff.getText().trim());
            nonStaff = Long.parseLong(selectedNonStaff.getText().trim());
        } catch (NumberFormatException ex) {
            showSimple("Invalid number", "Please enter numeric values in sen.");
            return;
        }

        Map<String, Long> row = new LinkedHashMap<>();
        row.put("staff", staff);
        row.put("nonstaff", nonStaff);
        workingTable.put(code, row);
        if (defaultTransitCodeField.getText() == null || defaultTransitCodeField.getText().trim().isEmpty()) {
            defaultTransitCodeField.setText(resolveDefaultTransitCodeForWorkingTable());
        }
        updateRowsFromWorkingTable();
    }

    private void saveAsNewCatalog() {
        String version = versionField.getText() == null ? "" : versionField.getText().trim();
        if (version.isEmpty()) {
            showSimple("Version required", "Please enter a version label.");
            return;
        }

        List<String> errors = validateWorkingTable();
        if (!errors.isEmpty()) {
            showSimple("Catalog invalid", "Fix issues before saving:\n" + String.join("\n", errors));
            return;
        }

        String defaultTransitCode = resolveDefaultTransitCodeForWorkingTable();
        if (!isTransitCodeValidForWorkingTable(defaultTransitCode)) {
            showSimple("Default transit code required",
                "Please set a valid transit code (e.g. transit_2h_month, transit_halfday_month, transit_schoolholiday_month).");
            return;
        }
        defaultTransitCodeField.setText(defaultTransitCode);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("version", version);
                payload.put("table", workingTable);
                payload.put("defaultTransitMonthlyCode", defaultTransitCode);

                String projectId = FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "projectId");
                String idToken = UserSession.getIdToken();
                FirebaseFunctionsClient.CallResult res = FirebaseFunctionsClient.callBillingAdminSaveCatalog(
                    projectId,
                    idToken,
                    GSON_PRETTY.toJson(payload)
                );
                Map<?, ?> result = parseCallableResultMap(res.rawBody);
                if (!(result.get("ok") instanceof Boolean) || !((Boolean) result.get("ok"))) {
                    throw callableFailure(res.reason, result);
                }
                String createdId = String.valueOf(result.get("catalogId") == null ? "" : result.get("catalogId"));
                Platform.runLater(() -> {
                    showSimple("Saved", "Created catalog: " + createdId);
                    reloadCatalogs();
                    refreshLiveHealthStatus();
                });
            } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
                Platform.runLater(() -> showError("Failed to save catalog", ex));
            }
        });
    }

    private void activateSelectedCatalog() {
        CatalogItem sel = catalogSelect.getValue();
        if (sel == null) {
            showSimple("No selection", "Please select a catalog to activate.");
            return;
        }

        List<String> errors = validateWorkingTable();
        if (!errors.isEmpty()) {
            showSimple("Catalog invalid", "Cannot activate incomplete catalog:\n" + String.join("\n", errors));
            return;
        }

        String defaultTransitCode = resolveDefaultTransitCodeForWorkingTable();
        if (!isTransitCodeValidForWorkingTable(defaultTransitCode)) {
            showSimple("Default transit code required",
                "Please set a valid transit code before activation.");
            return;
        }
        defaultTransitCodeField.setText(defaultTransitCode);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("catalogId", sel.id);
                payload.put("defaultTransitMonthlyCode", defaultTransitCode);

                String projectId = FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "projectId");
                String idToken = UserSession.getIdToken();
                FirebaseFunctionsClient.CallResult res = FirebaseFunctionsClient.callBillingAdminActivateCatalog(
                    projectId,
                    idToken,
                    GSON_PRETTY.toJson(payload)
                );
                Map<?, ?> result = parseCallableResultMap(res.rawBody);
                if (!(result.get("ok") instanceof Boolean) || !((Boolean) result.get("ok"))) {
                    throw callableFailure(res.reason, result);
                }

                Platform.runLater(() -> {
                    showSimple("Activated", "Active catalog set to " + sel.id);
                    reloadCatalogs();
                    refreshLiveHealthStatus();
                });
            } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
                Platform.runLater(() -> showError("Failed to activate catalog", ex));
            }
        });
    }

    private void showSimple(String header, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message);
        a.setHeaderText(header);
        a.showAndWait();
    }

    private void showError(String header, Exception ex) {
        Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage());
        a.setHeaderText(header);
        a.showAndWait();
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

    private static final class CatalogItem {
        final String id;
        final String version;
        final boolean active;
        final Map<String, Object> doc;

        CatalogItem(String id, String version, boolean active, Map<String, Object> doc) {
            this.id = id;
            this.version = version;
            this.active = active;
            this.doc = doc;
        }

        @Override
        public String toString() {
            String v = (version == null || version.isBlank()) ? id : version;
            return active ? (v + " [ACTIVE]") : v;
        }
    }

    private static final class RemoteHealthSnapshot {
        final boolean ok;
        final boolean valid;
        final String summary;
        final String version;
        final String rowCount;
        final String resolvedTransit;
        final String missingSummary;

        RemoteHealthSnapshot(boolean ok, boolean valid, String summary, String version, String rowCount, String resolvedTransit, String missingSummary) {
            this.ok = ok;
            this.valid = valid;
            this.summary = summary;
            this.version = version;
            this.rowCount = rowCount;
            this.resolvedTransit = resolvedTransit;
            this.missingSummary = missingSummary;
        }
    }

    private static final class AuditLogSnapshot {
        final String actionFilter;
        final List<Map<String, Object>> entries;
        final String formattedText;

        AuditLogSnapshot(String actionFilter, List<Map<String, Object>> entries, String formattedText) {
            this.actionFilter = actionFilter;
            this.entries = entries == null ? Collections.emptyList() : entries;
            this.formattedText = formattedText == null ? "" : formattedText;
        }
    }
}
