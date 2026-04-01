package nfc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class BillingPolicyWorkflowSupport {
    private BillingPolicyWorkflowSupport() {}

    static List<CatalogItemOption> loadCatalogItems() throws IOException {
        List<CatalogItemOption> items = new ArrayList<>();
        for (BillingPolicyCatalogRemoteSupport.CatalogDescriptor descriptor : BillingPolicyCatalogRemoteSupport.loadCatalogs()) {
            items.add(new CatalogItemOption(descriptor.id, descriptor.version, descriptor.active, descriptor.doc));
        }
        return items;
    }

    static PreparedCatalogSave prepareSaveRequest(
        String version,
        Map<String, Map<String, Long>> workingTable,
        String defaultTransitInput
    ) {
        String trimmedVersion = version == null ? "" : version.trim();
        if (trimmedVersion.isEmpty()) {
            return PreparedCatalogSave.error("Version required", "Please enter a version label.");
        }

        List<String> errors = BillingPolicyCatalogSupport.validateWorkingTable(workingTable);
        if (!errors.isEmpty()) {
            return PreparedCatalogSave.error("Catalog invalid", "Fix issues before saving:\n" + String.join("\n", errors));
        }

        String defaultTransitCode = BillingPolicyCatalogSupport.resolveDefaultTransitCodeForWorkingTable(workingTable, defaultTransitInput);
        if (!BillingPolicyCatalogSupport.isTransitCodeValidForWorkingTable(workingTable, defaultTransitCode)) {
            return PreparedCatalogSave.error(
                "Default transit code required",
                "Please set a valid transit code (e.g. transit_2h_month, transit_halfday_month, transit_schoolholiday_month)."
            );
        }

        return PreparedCatalogSave.success(trimmedVersion, defaultTransitCode);
    }

    static PreparedCatalogActivation prepareActivationRequest(
        CatalogItemOption selectedCatalog,
        Map<String, Map<String, Long>> workingTable,
        String defaultTransitInput
    ) {
        if (selectedCatalog == null) {
            return PreparedCatalogActivation.error("No selection", "Please select a catalog to activate.");
        }

        List<String> errors = BillingPolicyCatalogSupport.validateWorkingTable(workingTable);
        if (!errors.isEmpty()) {
            return PreparedCatalogActivation.error(
                "Catalog invalid",
                "Cannot activate incomplete catalog:\n" + String.join("\n", errors)
            );
        }

        String defaultTransitCode = BillingPolicyCatalogSupport.resolveDefaultTransitCodeForWorkingTable(workingTable, defaultTransitInput);
        if (!BillingPolicyCatalogSupport.isTransitCodeValidForWorkingTable(workingTable, defaultTransitCode)) {
            return PreparedCatalogActivation.error(
                "Default transit code required",
                "Please set a valid transit code before activation."
            );
        }

        return PreparedCatalogActivation.success(selectedCatalog.id, defaultTransitCode);
    }

    static final class CatalogItemOption {
        final String id;
        final String version;
        final boolean active;
        final Map<String, Object> doc;

        CatalogItemOption(String id, String version, boolean active, Map<String, Object> doc) {
            this.id = id;
            this.version = version;
            this.active = active;
            this.doc = doc;
        }

        @Override
        public String toString() {
            String visibleVersion = (version == null || version.isBlank()) ? id : version;
            return active ? (visibleVersion + " [ACTIVE]") : visibleVersion;
        }
    }

    static final class PreparedCatalogSave {
        final boolean ok;
        final String header;
        final String message;
        final String version;
        final String defaultTransitCode;

        private PreparedCatalogSave(boolean ok, String header, String message, String version, String defaultTransitCode) {
            this.ok = ok;
            this.header = header;
            this.message = message;
            this.version = version == null ? "" : version;
            this.defaultTransitCode = defaultTransitCode == null ? "" : defaultTransitCode;
        }

        static PreparedCatalogSave success(String version, String defaultTransitCode) {
            return new PreparedCatalogSave(true, "", "", version, defaultTransitCode);
        }

        static PreparedCatalogSave error(String header, String message) {
            return new PreparedCatalogSave(false, header, message, "", "");
        }
    }

    static final class PreparedCatalogActivation {
        final boolean ok;
        final String header;
        final String message;
        final String catalogId;
        final String defaultTransitCode;

        private PreparedCatalogActivation(boolean ok, String header, String message, String catalogId, String defaultTransitCode) {
            this.ok = ok;
            this.header = header;
            this.message = message;
            this.catalogId = catalogId == null ? "" : catalogId;
            this.defaultTransitCode = defaultTransitCode == null ? "" : defaultTransitCode;
        }

        static PreparedCatalogActivation success(String catalogId, String defaultTransitCode) {
            return new PreparedCatalogActivation(true, "", "", catalogId, defaultTransitCode);
        }

        static PreparedCatalogActivation error(String header, String message) {
            return new PreparedCatalogActivation(false, header, message, "", "");
        }
    }
}