package nfc;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

@SuppressWarnings("unused")
public final class BillingPolicyBackfillDialogSupport {
    private BillingPolicyBackfillDialogSupport() {
    }

    static Optional<BillingPolicyCatalogRemoteSupport.ChildMetadataBackfillRequest> showDialog(Window owner) {
        Dialog<BillingPolicyCatalogRemoteSupport.ChildMetadataBackfillRequest> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        AppThemeSupport.prepareDialog(
            dialog,
            "Backfill Child Metadata",
            "Run the Taska Zurah billing metadata migration in controlled batches. Leave child IDs blank to scan the whole collection in document-id order.",
            AppThemeSupport.Tone.WARNING
        );

        ButtonType runType = new ButtonType("Run Backfill", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(runType, ButtonType.CANCEL);
        AppThemeSupport.styleDialogButtons(dialog, runType);

        TextField periodField = new TextField(YearMonth.now().toString());
        periodField.setPromptText("YYYY-MM");

        TextField limitField = new TextField("200");
        limitField.setPromptText("1-200");

        CheckBox forceCheck = new CheckBox("Force rewrite even when a child already matches the Taska Zurah profile");
        CheckBox runAllPagesCheck = new CheckBox("Continue through every page until no more child records remain");
        runAllPagesCheck.setSelected(true);

        TextArea childIdsArea = new TextArea();
        childIdsArea.setPrefRowCount(5);
        childIdsArea.setPromptText("Optional child IDs, one per line or comma-separated");

        Label periodHint = new Label("Use the invoice period that should drive the metadata, usually the current month.");
        periodHint.getStyleClass().add("app-helper-text");
        Label batchHint = new Label("Batch size is capped at 200 records per callable request.");
        batchHint.getStyleClass().add("app-helper-text");
        Label pagingHint = new Label("If child IDs are provided, the backfill runs only for those records and paging is skipped.");
        pagingHint.getStyleClass().add("app-helper-text");

        AppThemeSupport.styleControls(periodField, limitField, forceCheck, runAllPagesCheck, childIdsArea, periodHint, batchHint, pagingHint);

        childIdsArea.textProperty().addListener((obs, oldText, newText) -> {
            boolean hasChildIds = !normalizeChildIds(newText).isEmpty();
            runAllPagesCheck.setDisable(hasChildIds);
            if (hasChildIds) {
                runAllPagesCheck.setSelected(false);
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getStyleClass().add("app-form-grid");
        grid.addRow(0, new Label("Period"), periodField);
        grid.addRow(1, new Label("Batch Size"), limitField);
        grid.addRow(2, new Label("Options"), forceCheck);
        grid.addRow(3, new Label("Paging"), runAllPagesCheck);
        grid.addRow(4, new Label("Child IDs"), childIdsArea);
        GridPane.setHgrow(periodField, Priority.ALWAYS);
        GridPane.setHgrow(limitField, Priority.ALWAYS);
        GridPane.setHgrow(childIdsArea, Priority.ALWAYS);
        GridPane.setVgrow(childIdsArea, Priority.ALWAYS);

        dialog.getDialogPane().setPrefWidth(680);
        dialog.getDialogPane().setContent(
            AppThemeSupport.createDialogContent(
                AppThemeSupport.createFormSection(
                    "Backfill options",
                    "Choose how broadly to run the metadata migration and whether the scan should keep paging until completion.",
                    grid,
                    periodHint,
                    batchHint,
                    pagingHint
                )
            )
        );

        Button runButton = (Button) dialog.getDialogPane().lookupButton(runType);
        runButton.addEventFilter(ActionEvent.ACTION, event -> {
            String validation = validate(periodField.getText(), limitField.getText());
            if (!validation.isBlank()) {
                event.consume();
                AppThemeSupport.showWarning(owner, "Invalid Backfill Options", validation);
            }
        });

        dialog.setResultConverter(button -> {
            if (button != runType) {
                return null;
            }
            return new BillingPolicyCatalogRemoteSupport.ChildMetadataBackfillRequest(
                periodField.getText(),
                parseLimit(limitField.getText()),
                forceCheck.isSelected(),
                runAllPagesCheck.isSelected(),
                "",
                normalizeChildIds(childIdsArea.getText())
            );
        });

        return dialog.showAndWait();
    }

    private static String validate(String rawPeriod, String rawLimit) {
        String period = rawPeriod == null ? "" : rawPeriod.trim();
        if (!period.isEmpty() && !period.matches("\\d{4}-\\d{2}")) {
            return "Period must be blank or use the YYYY-MM format.";
        }

        String limit = rawLimit == null ? "" : rawLimit.trim();
        if (limit.isEmpty()) {
            return "";
        }

        try {
            int parsed = Integer.parseInt(limit);
            if (parsed < 1 || parsed > 200) {
                return "Batch size must be a whole number between 1 and 200.";
            }
        } catch (NumberFormatException ex) {
            return "Batch size must be a whole number between 1 and 200.";
        }
        return "";
    }

    private static int parseLimit(String rawLimit) {
        String value = rawLimit == null ? "" : rawLimit.trim();
        if (value.isEmpty()) {
            return 200;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 200;
        }
    }

    private static List<String> normalizeChildIds(String rawIds) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        String normalized = rawIds == null ? "" : rawIds.replace(',', '\n');
        for (String token : normalized.split("\\s+")) {
            String value = token == null ? "" : token.trim();
            if (!value.isEmpty()) {
                ids.add(value);
            }
        }
        return new ArrayList<>(ids);
    }
}