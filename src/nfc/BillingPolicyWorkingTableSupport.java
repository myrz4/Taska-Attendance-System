package nfc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.collections.ObservableList;
import javafx.scene.control.Label;

final class BillingPolicyWorkingTableSupport {
    private BillingPolicyWorkingTableSupport() {
    }

    static List<BillingPolicyView.Row> buildRows(Map<String, Map<String, Long>> workingTable) {
        List<BillingPolicyView.Row> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> entry : workingTable.entrySet()) {
            Map<String, Long> values = entry.getValue();
            result.add(new BillingPolicyView.Row(
                entry.getKey(),
                BillingPolicyCatalogSupport.toLong(values.get("staff")),
                BillingPolicyCatalogSupport.toLong(values.get("nonstaff"))
            ));
        }
        return result;
    }

    static void refreshRows(ObservableList<BillingPolicyView.Row> rows, Map<String, Map<String, Long>> workingTable) {
        rows.setAll(buildRows(workingTable));
    }

    static List<String> validateWorkingTable(Map<String, Map<String, Long>> workingTable) {
        return BillingPolicyCatalogSupport.validateWorkingTable(workingTable);
    }

    static void updateHealthLabel(Label healthLabel, List<String> errors) {
        if (errors.isEmpty()) {
            healthLabel.setText("Catalog health: OK (all required billing codes present)");
            healthLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1b5e20;");
            return;
        }
        healthLabel.setText("Catalog health: INVALID (" + errors.size() + " issue(s))");
        healthLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #b71c1c;");
    }
}