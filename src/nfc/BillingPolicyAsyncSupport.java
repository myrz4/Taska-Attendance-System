package nfc;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.application.Platform;

final class BillingPolicyAsyncSupport {
    private BillingPolicyAsyncSupport() {}

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

    interface ErrorCallback {
        void handle(String header, Exception ex);
    }
}