package nfc;

import java.util.function.Consumer;

import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

@SuppressWarnings("unused")
final class BillingPolicyTableSupport {
    private BillingPolicyTableSupport() {
    }

    static void setupTable(
        TableView<BillingPolicyView.Row> table,
        ObservableList<BillingPolicyView.Row> rows,
        Consumer<BillingPolicyView.Row> onSelected
    ) {
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<BillingPolicyView.Row, String> codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(new PropertyValueFactory<>("code"));
        codeCol.setMinWidth(320);

        TableColumn<BillingPolicyView.Row, Long> staffCol = new TableColumn<>("Staff (sen)");
        staffCol.setCellValueFactory(new PropertyValueFactory<>("staff"));
        staffCol.setMinWidth(140);

        TableColumn<BillingPolicyView.Row, Long> nonStaffCol = new TableColumn<>("Non-staff (sen)");
        nonStaffCol.setCellValueFactory(new PropertyValueFactory<>("nonStaff"));
        nonStaffCol.setMinWidth(160);

        table.getColumns().clear();
        table.getColumns().add(codeCol);
        table.getColumns().add(staffCol);
        table.getColumns().add(nonStaffCol);
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> onSelected.accept(newValue));
    }
}