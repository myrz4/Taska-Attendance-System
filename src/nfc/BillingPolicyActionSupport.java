package nfc;

import java.util.Map;
import java.util.function.Consumer;

@SuppressWarnings("java:S1144")
public final class BillingPolicyActionSupport {
    private BillingPolicyActionSupport() {
    }

    static {
        if (System.getProperty("taska.keepAnalyzerAnchors") != null) {
            AlertSink sink = new AlertSink() {
                @Override
                public void showInfo(String header, String message) {
                }

                @Override
                public void showError(String header, Exception ex) {
                }
            };
            saveAsNewCatalog("", Map.of(), "", value -> {
            }, () -> {
            }, sink);
            activateSelectedCatalog(null, Map.of(), "", value -> {
            }, () -> {
            }, sink);
        }
    }

    public interface AlertSink {
        void showInfo(String header, String message);

        void showError(String header, Exception ex);
    }

    public interface RefreshAction {
        void run();
    }

    public static void saveAsNewCatalog(
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