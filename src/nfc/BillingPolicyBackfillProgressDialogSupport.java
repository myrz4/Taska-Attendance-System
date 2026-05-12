package nfc;

import java.util.concurrent.atomic.AtomicBoolean;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

@SuppressWarnings("unused")
final class BillingPolicyBackfillProgressDialogSupport {
    static {
        java.util.function.BiFunction<Window, BillingPolicyCatalogRemoteSupport.ChildMetadataBackfillRequest, ProgressHandle> keepShowDialog
            = BillingPolicyBackfillProgressDialogSupport::showDialog;
        java.util.function.BiConsumer<ProgressHandle, BillingPolicyAsyncSupport.ChildMetadataBackfillProgress> keepUpdate
            = ProgressHandle::update;
        java.util.function.Predicate<ProgressHandle> keepIsStopRequested = ProgressHandle::isStopRequested;
        java.util.function.Consumer<ProgressHandle> keepClose = ProgressHandle::close;
        java.util.Objects.requireNonNull(keepShowDialog);
        java.util.Objects.requireNonNull(keepUpdate);
        java.util.Objects.requireNonNull(keepIsStopRequested);
        java.util.Objects.requireNonNull(keepClose);
    }

    private BillingPolicyBackfillProgressDialogSupport() {
    }

    static ProgressHandle showDialog(
        Window owner,
        BillingPolicyCatalogRemoteSupport.ChildMetadataBackfillRequest request
    ) {
        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        AppThemeSupport.prepareDialog(
            dialog,
            "Running Child Metadata Backfill",
            "The backfill runs batch by batch. Keep this window open to watch live counts update.",
            AppThemeSupport.Tone.INFO
        );
        dialog.getDialogPane().getButtonTypes().clear();

        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        indicator.setPrefSize(64, 64);

        Label headlineLabel = new Label(buildInitialHeadline(request));
        headlineLabel.getStyleClass().add("app-form-section-title");

        Label progressLabel = new Label("Starting first batch...");
        progressLabel.getStyleClass().add("app-helper-text");

        Label totalsLabel = new Label("Scanned: 0\nPatched: 0\nUnchanged: 0\nSkipped migrated: 0\nFailed: 0");
        totalsLabel.getStyleClass().add("app-helper-text");

        Label cursorLabel = new Label("Resume cursor: pending");
        cursorLabel.getStyleClass().add("app-helper-text");

        Label stopStateLabel = new Label("Use Stop After Current Batch to pause and keep the resume cursor for the next run.");
        stopStateLabel.getStyleClass().add("app-helper-text");

        Button stopButton = new Button("Stop After Current Batch");
        AppThemeSupport.styleSecondaryButtons(stopButton);
        stopButton.setMaxWidth(Double.MAX_VALUE);

        AppThemeSupport.styleControls(headlineLabel, progressLabel, totalsLabel, cursorLabel, stopStateLabel);

        VBox actionsBox = new VBox(10, stopButton, stopStateLabel);
        actionsBox.setAlignment(Pos.CENTER_LEFT);

        VBox body = AppThemeSupport.createDialogContent(
            AppThemeSupport.createFormSection(
                "Live progress",
                "Counts refresh after each backend batch completes.",
                indicator,
                headlineLabel,
                progressLabel,
                totalsLabel,
                cursorLabel,
                actionsBox
            )
        );
        dialog.getDialogPane().setPrefWidth(520);
        dialog.getDialogPane().setContent(body);
        ProgressHandle handle = new ProgressHandle(dialog, progressLabel, totalsLabel, cursorLabel, stopStateLabel, stopButton);
        stopButton.setOnAction(event -> handle.requestStop());
        dialog.setOnCloseRequest(event -> {
            handle.requestStop();
            event.consume();
        });
        dialog.show();

        return handle;
    }

    private static String buildInitialHeadline(BillingPolicyCatalogRemoteSupport.ChildMetadataBackfillRequest request) {
        BillingPolicyCatalogRemoteSupport.ChildMetadataBackfillRequest normalizedRequest = request == null
            ? new BillingPolicyCatalogRemoteSupport.ChildMetadataBackfillRequest("", 200, false, true, "", java.util.Collections.emptyList())
            : request;
        String period = normalizedRequest.period.isBlank() ? "current period" : normalizedRequest.period;
        if (normalizedRequest.hasChildIds()) {
            String childLabel = normalizedRequest.childIds.size() == 1 ? "child ID" : "child IDs";
            return "Targeting " + normalizedRequest.childIds.size() + " requested " + childLabel + " for " + period + ".";
        }
        String pagingLabel = normalizedRequest.runAllPages ? "all pages" : "one page";
        return "Scanning " + pagingLabel + " for " + period + " in batches of up to " + normalizedRequest.limit + " records.";
    }

    @SuppressWarnings("unused")
    static final class ProgressHandle {
        private final Dialog<Void> dialog;
        private final Label progressLabel;
        private final Label totalsLabel;
        private final Label cursorLabel;
        private final Label stopStateLabel;
        private final Button stopButton;
        private final AtomicBoolean stopRequested = new AtomicBoolean(false);

        ProgressHandle(
            Dialog<Void> dialog,
            Label progressLabel,
            Label totalsLabel,
            Label cursorLabel,
            Label stopStateLabel,
            Button stopButton
        ) {
            this.dialog = dialog;
            this.progressLabel = progressLabel;
            this.totalsLabel = totalsLabel;
            this.cursorLabel = cursorLabel;
            this.stopStateLabel = stopStateLabel;
            this.stopButton = stopButton;
        }

        void update(BillingPolicyAsyncSupport.ChildMetadataBackfillProgress progress) {
            if (progress == null) {
                return;
            }
            String batchLabel = progress.batchCount == 1 ? "batch" : "batches";
            String period = progress.period.isBlank() ? "-" : progress.period;
            progressLabel.setText(
                "Completed " + progress.batchCount + " " + batchLabel
                    + " for period " + period
                    + " using batches of up to " + progress.limit + " records."
            );
            totalsLabel.setText(String.join(
                "\n",
                "Scanned: " + progress.scannedCount,
                "Patched: " + progress.patchedCount,
                "Unchanged: " + progress.unchangedCount,
                "Skipped migrated: " + progress.skippedMigratedCount,
                "Failed: " + progress.failedCount,
                "Force rerun: " + (progress.force ? "Yes" : "No")
            ));
            if (progress.hasMore) {
                cursorLabel.setText("Resume cursor: " + (progress.nextStartAfterId.isBlank() ? "-" : progress.nextStartAfterId));
            } else {
                cursorLabel.setText("Resume cursor: none, current run reached its final batch.");
            }
            if (stopRequested.get()) {
                stopStateLabel.setText("Stop requested. The backfill will pause before the next batch and keep the current resume cursor.");
            }
        }

        boolean isStopRequested() {
            return stopRequested.get();
        }

        void requestStop() {
            if (!stopRequested.compareAndSet(false, true)) {
                return;
            }
            stopButton.setDisable(true);
            stopButton.setText("Stop Requested");
            stopStateLabel.setText("Stop requested. Finishing the current batch before pausing the run.");
        }

        void close() {
            if (dialog != null && dialog.isShowing()) {
                dialog.close();
            }
        }
    }
}