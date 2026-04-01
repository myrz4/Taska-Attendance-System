package nfc;

import java.util.Map;
import java.util.function.Supplier;

import javafx.scene.control.TextField;

final class BillingPolicyEditorSupport {
    private BillingPolicyEditorSupport() {
    }

    static void populateEditorFields(
        BillingPolicyView.Row row,
        TextField selectedCode,
        TextField selectedStaff,
        TextField selectedNonStaff
    ) {
        if (row == null) {
            return;
        }
        selectedCode.setText(row.getCode());
        selectedStaff.setText(String.valueOf(row.getStaff()));
        selectedNonStaff.setText(String.valueOf(row.getNonStaff()));
    }

    static ApplyEditedRowResult applyEditedRow(
        Map<String, Map<String, Long>> workingTable,
        String codeText,
        String staffText,
        String nonStaffText,
        String defaultTransitText,
        Supplier<String> fallbackDefaultTransit
    ) {
        try {
            BillingPolicyCommandSupport.EditedRow editedRow = BillingPolicyCommandSupport.parseEditedRow(
                codeText,
                staffText,
                nonStaffText
            );
            workingTable.put(editedRow.code, editedRow.toTableRow());

            String nextDefaultTransit = defaultTransitText == null ? "" : defaultTransitText.trim();
            if (nextDefaultTransit.isEmpty()) {
                nextDefaultTransit = fallbackDefaultTransit.get();
            }
            return ApplyEditedRowResult.updated(nextDefaultTransit);
        } catch (IllegalArgumentException ex) {
            if ("missing-code".equals(ex.getMessage())) {
                return ApplyEditedRowResult.ignored();
            }
            return ApplyEditedRowResult.invalid(ex.getMessage());
        }
    }

    static final class ApplyEditedRowResult {
        final boolean updated;
        final String nextDefaultTransit;
        final String errorMessage;

        private ApplyEditedRowResult(boolean updated, String nextDefaultTransit, String errorMessage) {
            this.updated = updated;
            this.nextDefaultTransit = nextDefaultTransit == null ? "" : nextDefaultTransit;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }

        static ApplyEditedRowResult updated(String nextDefaultTransit) {
            return new ApplyEditedRowResult(true, nextDefaultTransit, "");
        }

        static ApplyEditedRowResult ignored() {
            return new ApplyEditedRowResult(false, "", "");
        }

        static ApplyEditedRowResult invalid(String errorMessage) {
            return new ApplyEditedRowResult(false, "", errorMessage);
        }
    }
}