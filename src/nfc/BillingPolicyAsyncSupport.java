package nfc;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javafx.application.Platform;

@SuppressWarnings("all")
final class BillingPolicyAsyncSupport {
    private BillingPolicyAsyncSupport() {}

    @SuppressWarnings("unused")
    static void reloadCatalogsAsync(
        Consumer<List<BillingPolicyWorkflowSupport.CatalogItemOption>> onLoaded,
        ErrorCallback onError
    ) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                List<BillingPolicyWorkflowSupport.CatalogItemOption> items = BillingPolicyWorkflowSupport.loadCatalogItems();
                Platform.runLater(() -> onLoaded.accept(items));
            } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
                Platform.runLater(() -> onError.handle("Failed to load billing catalogs", ex));
            }
        });
    }

    @SuppressWarnings("unused")
    static void saveCatalogAsync(
        BillingPolicyWorkflowSupport.PreparedCatalogSave request,
        Map<String, Map<String, Long>> workingTable,
        Consumer<String> onSaved,
        ErrorCallback onError
    ) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String createdId = BillingPolicyCatalogRemoteSupport.saveCatalog(request.version, workingTable, request.defaultTransitCode);
                Platform.runLater(() -> onSaved.accept(createdId));
            } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
                Platform.runLater(() -> onError.handle("Failed to save catalog", ex));
            }
        });
    }

    @SuppressWarnings("unused")
    static void activateCatalogAsync(
        BillingPolicyWorkflowSupport.PreparedCatalogActivation request,
        Consumer<String> onActivated,
        ErrorCallback onError
    ) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                BillingPolicyCatalogRemoteSupport.activateCatalog(request.catalogId, request.defaultTransitCode);
                Platform.runLater(() -> onActivated.accept(request.catalogId));
            } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
                Platform.runLater(() -> onError.handle("Failed to activate catalog", ex));
            }
        });
    }

    @SuppressWarnings("unused")
    static void backfillChildMetadataAsync(
        BillingPolicyCatalogRemoteSupport.ChildMetadataBackfillRequest request,
        BooleanSupplier stopRequested,
        Consumer<ChildMetadataBackfillProgress> onProgress,
        Consumer<ChildMetadataBackfillRunResult> onCompleted,
        ErrorCallback onError
    ) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                ChildMetadataBackfillRunResult result = runChildMetadataBackfill(request, stopRequested, onProgress);
                Platform.runLater(() -> onCompleted.accept(result));
            } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
                Platform.runLater(() -> onError.handle("Failed to backfill child billing metadata", ex));
            }
        });
    }

    private static ChildMetadataBackfillRunResult runChildMetadataBackfill(
        BillingPolicyCatalogRemoteSupport.ChildMetadataBackfillRequest request,
        BooleanSupplier stopRequested,
        Consumer<ChildMetadataBackfillProgress> onProgress
    ) throws IOException {
        BillingPolicyCatalogRemoteSupport.ChildMetadataBackfillRequest normalizedRequest = request == null
            ? new BillingPolicyCatalogRemoteSupport.ChildMetadataBackfillRequest("", 200, false, true, "", java.util.Collections.emptyList())
            : request;

        int batchCount = 0;
        int scannedCount = 0;
        int patchedCount = 0;
        int unchangedCount = 0;
        int skippedMigratedCount = 0;
        int failedCount = 0;
        boolean hasMore;
        String nextStartAfterId = normalizedRequest.startAfterId;
        String resultPeriod = normalizedRequest.period;
        String activeCatalogVersion = "";

        while (true) {
            BillingPolicyCatalogRemoteSupport.ChildMetadataBackfillResult batchResult = BillingPolicyCatalogRemoteSupport.backfillChildMetadata(
                normalizedRequest.withStartAfterId(nextStartAfterId)
            );
            batchCount += 1;
            if (!batchResult.period.isBlank()) {
                resultPeriod = batchResult.period;
            }
            if (!batchResult.activeCatalogVersion.isBlank()) {
                activeCatalogVersion = batchResult.activeCatalogVersion;
            }
            scannedCount += batchResult.scannedCount;
            patchedCount += batchResult.patchedCount;
            unchangedCount += batchResult.unchangedCount;
            skippedMigratedCount += batchResult.skippedMigratedCount;
            failedCount += batchResult.failedCount;
            hasMore = batchResult.hasMore;
            nextStartAfterId = batchResult.nextStartAfterId;

            emitBackfillProgress(
                onProgress,
                new ChildMetadataBackfillProgress(
                    resultPeriod,
                    normalizedRequest.limit,
                    normalizedRequest.force,
                    normalizedRequest.runAllPages,
                    normalizedRequest.childIds.size(),
                    batchCount,
                    scannedCount,
                    patchedCount,
                    unchangedCount,
                    skippedMigratedCount,
                    failedCount,
                    hasMore,
                    nextStartAfterId
                )
            );

            boolean stopAfterCurrentBatch = stopRequested != null
                && stopRequested.getAsBoolean()
                && normalizedRequest.runAllPages
                && !normalizedRequest.hasChildIds()
                && hasMore;
            if (stopAfterCurrentBatch) {
                return new ChildMetadataBackfillRunResult(
                    resultPeriod,
                    activeCatalogVersion,
                    normalizedRequest.limit,
                    normalizedRequest.force,
                    normalizedRequest.runAllPages,
                    normalizedRequest.childIds.size(),
                    batchCount,
                    scannedCount,
                    patchedCount,
                    unchangedCount,
                    skippedMigratedCount,
                    failedCount,
                    true,
                    nextStartAfterId,
                    false,
                    true
                );
            }

            if (!normalizedRequest.runAllPages || normalizedRequest.hasChildIds() || !hasMore) {
                return new ChildMetadataBackfillRunResult(
                    resultPeriod,
                    activeCatalogVersion,
                    normalizedRequest.limit,
                    normalizedRequest.force,
                    normalizedRequest.runAllPages,
                    normalizedRequest.childIds.size(),
                    batchCount,
                    scannedCount,
                    patchedCount,
                    unchangedCount,
                    skippedMigratedCount,
                    failedCount,
                    hasMore,
                    nextStartAfterId,
                    !hasMore,
                    false
                );
            }

            if (nextStartAfterId.isBlank()) {
                return new ChildMetadataBackfillRunResult(
                    resultPeriod,
                    activeCatalogVersion,
                    normalizedRequest.limit,
                    normalizedRequest.force,
                    normalizedRequest.runAllPages,
                    normalizedRequest.childIds.size(),
                    batchCount,
                    scannedCount,
                    patchedCount,
                    unchangedCount,
                    skippedMigratedCount,
                    failedCount,
                    true,
                    nextStartAfterId,
                    false,
                    false
                );
            }
        }
    }

    private static void emitBackfillProgress(
        Consumer<ChildMetadataBackfillProgress> onProgress,
        ChildMetadataBackfillProgress progress
    ) {
        if (onProgress == null || progress == null) {
            return;
        }
        Platform.runLater(() -> onProgress.accept(progress));
    }

    public static final class ChildMetadataBackfillProgress {
        public final String period;
        public final int limit;
        public final boolean force;
        public final boolean runAllPages;
        public final int requestedChildCount;
        public final int batchCount;
        public final int scannedCount;
        public final int patchedCount;
        public final int unchangedCount;
        public final int skippedMigratedCount;
        public final int failedCount;
        public final boolean hasMore;
        public final String nextStartAfterId;

        ChildMetadataBackfillProgress(
            String period,
            int limit,
            boolean force,
            boolean runAllPages,
            int requestedChildCount,
            int batchCount,
            int scannedCount,
            int patchedCount,
            int unchangedCount,
            int skippedMigratedCount,
            int failedCount,
            boolean hasMore,
            String nextStartAfterId
        ) {
            this.period = period == null ? "" : period;
            this.limit = limit;
            this.force = force;
            this.runAllPages = runAllPages;
            this.requestedChildCount = requestedChildCount;
            this.batchCount = batchCount;
            this.scannedCount = scannedCount;
            this.patchedCount = patchedCount;
            this.unchangedCount = unchangedCount;
            this.skippedMigratedCount = skippedMigratedCount;
            this.failedCount = failedCount;
            this.hasMore = hasMore;
            this.nextStartAfterId = nextStartAfterId == null ? "" : nextStartAfterId;
        }
    }

    @SuppressWarnings("unused")
    static final class ChildMetadataBackfillRunResult {
        final String period;
        final String activeCatalogVersion;
        final int limit;
        final boolean force;
        final boolean runAllPages;
        final int requestedChildCount;
        final int batchCount;
        final int scannedCount;
        final int patchedCount;
        final int unchangedCount;
        final int skippedMigratedCount;
        final int failedCount;
        final boolean hasMore;
        final String nextStartAfterId;
        final boolean completedAllPages;
        final boolean canceledByUser;

        ChildMetadataBackfillRunResult(
            String period,
            String activeCatalogVersion,
            int limit,
            boolean force,
            boolean runAllPages,
            int requestedChildCount,
            int batchCount,
            int scannedCount,
            int patchedCount,
            int unchangedCount,
            int skippedMigratedCount,
            int failedCount,
            boolean hasMore,
            String nextStartAfterId,
            boolean completedAllPages,
            boolean canceledByUser
        ) {
            this.period = period == null ? "" : period;
            this.activeCatalogVersion = activeCatalogVersion == null ? "" : activeCatalogVersion;
            this.limit = limit;
            this.force = force;
            this.runAllPages = runAllPages;
            this.requestedChildCount = requestedChildCount;
            this.batchCount = batchCount;
            this.scannedCount = scannedCount;
            this.patchedCount = patchedCount;
            this.unchangedCount = unchangedCount;
            this.skippedMigratedCount = skippedMigratedCount;
            this.failedCount = failedCount;
            this.hasMore = hasMore;
            this.nextStartAfterId = nextStartAfterId == null ? "" : nextStartAfterId;
            this.completedAllPages = completedAllPages;
            this.canceledByUser = canceledByUser;
        }
    }

    interface ErrorCallback {
        void handle(String header, Exception ex);
    }
}