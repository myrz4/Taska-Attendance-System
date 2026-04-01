package nfc;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class BillingPolicyActionSupport {
    private BillingPolicyActionSupport() {
    }

    interface AlertSink {
        void showInfo(String header, String message);

        void showError(String header, Exception ex);
    }

    interface RefreshAction {
        void run();
    }

    static void saveAsNewCatalog(
        String version,
        Map<String, Map<String, Long>> workingTable,
        String defaultTransitInput,
        Consumer<String> setDefaultTransit,
        RefreshAction refreshAction,
        AlertSink sink
    ) {
        BillingPolicyWorkflowSupport.PreparedCatalogSave request = BillingPolicyWorkflowSupport.prepareSaveRequest(
            version,
            workingTable,
            defaultTransitInput
        );
        if (!request.ok) {
            sink.showInfo(request.header, request.message);
            return;
        }
        setDefaultTransit.accept(request.defaultTransitCode);

        BillingPolicyAsyncSupport.saveCatalogAsync(
            request,
            workingTable,
            createdId -> {
                sink.showInfo("Saved", "Created catalog: " + createdId);
                refreshAction.run();
            },
            sink::showError
        );
    }

    static void activateSelectedCatalog(
        BillingPolicyWorkflowSupport.CatalogItemOption selectedCatalog,
        Map<String, Map<String, Long>> workingTable,
        String defaultTransitInput,
        Consumer<String> setDefaultTransit,
        RefreshAction refreshAction,
        AlertSink sink
    ) {
        BillingPolicyWorkflowSupport.PreparedCatalogActivation request = BillingPolicyWorkflowSupport.prepareActivationRequest(
            selectedCatalog,
            workingTable,
            defaultTransitInput
        );
        if (!request.ok) {
            sink.showInfo(request.header, request.message);
            return;
        }
        setDefaultTransit.accept(request.defaultTransitCode);

        BillingPolicyAsyncSupport.activateCatalogAsync(
            request,
            catalogId -> {
                sink.showInfo("Activated", "Active catalog set to " + catalogId);
                refreshAction.run();
            },
            sink::showError
        );
    }
}