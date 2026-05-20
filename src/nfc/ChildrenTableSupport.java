package nfc;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

@SuppressWarnings("unused")
final class ChildrenTableSupport {
    private ChildrenTableSupport() {}

    private static final TableColumn<ChildrenView.Child, String> RECORD_ID_COL = new TableColumn<>("Record ID");
    private static final TableColumn<ChildrenView.Child, String> DUE_DAY_COL = new TableColumn<>("Invoice Due");
    private static final TableColumn<ChildrenView.Child, String> TRANSPORT_COL = new TableColumn<>("Casual Transit");

    static void setupTable(
        TableView<ChildrenView.Child> table,
        Consumer<ChildrenView.Child> onView,
        Consumer<ChildrenView.Child> onEdit,
        Consumer<ChildrenView.Child> onDeleteConfirmed
    ) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ChildrenView.Child, Integer> idCol = new TableColumn<>("No");
        TableColumn<ChildrenView.Child, String> nameCol = new TableColumn<>("Name");
        TableColumn<ChildrenView.Child, String> childIdentifierCol = new TableColumn<>("Child ID / MyKid");
        TableColumn<ChildrenView.Child, String> dobCol = new TableColumn<>("Date of Birth");
        TableColumn<ChildrenView.Child, String> ageCol = new TableColumn<>("Age");
        TableColumn<ChildrenView.Child, String> parentNameCol = new TableColumn<>("Primary Parent");
        TableColumn<ChildrenView.Child, String> parentContactCol = new TableColumn<>("Parent Phone");
        TableColumn<ChildrenView.Child, String> billingPlanCol = new TableColumn<>("Billing Model");
        TableColumn<ChildrenView.Child, String> uidCol = new TableColumn<>("NFC UID");
        TableColumn<ChildrenView.Child, String> statusCol = new TableColumn<>("Status");
        TableColumn<ChildrenView.Child, Void> actionsCol = new TableColumn<>("Actions");

        idCol.setCellFactory(col -> new TableCell<ChildrenView.Child, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else {
                    setText(String.valueOf(getIndex() + 1));
                }
                setStyle("-fx-font-weight: bold; -fx-text-fill: #181818;");
                setAlignment(Pos.CENTER);
            }
        });

        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        childIdentifierCol.setCellValueFactory(new PropertyValueFactory<>("childIdentifier"));
        dobCol.setCellValueFactory(new PropertyValueFactory<>("birthDateText"));
        ageCol.setCellValueFactory(new PropertyValueFactory<>("ageSummary"));
        parentNameCol.setCellValueFactory(new PropertyValueFactory<>("primaryParentName"));
        parentContactCol.setCellValueFactory(new PropertyValueFactory<>("parentPhone"));
        billingPlanCol.setCellValueFactory(new PropertyValueFactory<>("billingPlan"));
        uidCol.setCellValueFactory(new PropertyValueFactory<>("nfcUid"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));

        RECORD_ID_COL.setCellValueFactory(new PropertyValueFactory<>("recordId"));
        DUE_DAY_COL.setCellValueFactory(new PropertyValueFactory<>("paymentDueDayText"));
        TRANSPORT_COL.setCellValueFactory(new PropertyValueFactory<>("transportEnabledText"));

        RECORD_ID_COL.setVisible(false);
        DUE_DAY_COL.setVisible(false);
        TRANSPORT_COL.setVisible(false);

        nameCol.setCellFactory(col -> copyCell(ChildrenView.Child::getName, Pos.CENTER_LEFT));
        childIdentifierCol.setCellFactory(col -> copyCell(ChildrenView.Child::getChildIdentifier, Pos.CENTER_LEFT));
        dobCol.setCellFactory(col -> copyCell(ChildrenView.Child::getBirthDateText, Pos.CENTER));
        ageCol.setCellFactory(col -> copyCell(ChildrenView.Child::getAgeSummary, Pos.CENTER));
        parentNameCol.setCellFactory(col -> copyCell(ChildrenView.Child::getPrimaryParentName, Pos.CENTER_LEFT));
        parentContactCol.setCellFactory(col -> copyCell(ChildrenView.Child::getParentPhone, Pos.CENTER_LEFT));
        billingPlanCol.setCellFactory(col -> copyCell(ChildrenView.Child::getBillingPlan, Pos.CENTER_LEFT));
        uidCol.setCellFactory(col -> copyCell(ChildrenView.Child::getNfcUid, Pos.CENTER_LEFT));
        statusCol.setCellFactory(col -> copyCell(ChildrenView.Child::getStatus, Pos.CENTER));
        RECORD_ID_COL.setCellFactory(col -> copyCell(ChildrenView.Child::getRecordId, Pos.CENTER_LEFT));
        DUE_DAY_COL.setCellFactory(col -> copyCell(ChildrenView.Child::getPaymentDueDayText, Pos.CENTER));
        TRANSPORT_COL.setCellFactory(col -> copyCell(ChildrenView.Child::getTransportEnabledText, Pos.CENTER));

        actionsCol.setCellFactory(col -> new ActionButtonsTableCell<>(
            ActionButtonsTableCell.ActionSpec.normal("View", onView),
            ActionButtonsTableCell.ActionSpec.normal("Edit", onEdit),
            ActionButtonsTableCell.ActionSpec.destructive("Delete", current -> {
                Alert alert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Delete child and all their attendance records?",
                    ButtonType.YES,
                    ButtonType.NO
                );
                alert.setHeaderText("Confirm Delete");
                alert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.YES) {
                        onDeleteConfirmed.accept(current);
                    }
                });
            })
        ));

        setPrefWidth(idCol, 56);
        setPrefWidth(nameCol, 190);
        setPrefWidth(childIdentifierCol, 150);
        setPrefWidth(dobCol, 118);
        setPrefWidth(ageCol, 76);
        setPrefWidth(parentNameCol, 180);
        setPrefWidth(parentContactCol, 138);
        setPrefWidth(billingPlanCol, 186);
        setPrefWidth(uidCol, 140);
        setPrefWidth(statusCol, 96);
        setPrefWidth(actionsCol, 212);
        setPrefWidth(RECORD_ID_COL, 180);
        setPrefWidth(DUE_DAY_COL, 90);
        setPrefWidth(TRANSPORT_COL, 104);

        table.getColumns().clear();
        table.getColumns().setAll(Arrays.<TableColumn<ChildrenView.Child, ?>>asList(
            idCol,
            nameCol,
            childIdentifierCol,
            dobCol,
            ageCol,
            parentNameCol,
            parentContactCol,
            uidCol,
            statusCol,
            RECORD_ID_COL,
            DUE_DAY_COL,
            TRANSPORT_COL,
            actionsCol
        ));

        SummaryTableSupport.configureSummaryTable(
            table,
            "No children found.",
            ChildrenTableSupport::rowSummary,
            ChildrenView.Child::getRecordId,
            child -> asJson(child),
            onView
        );
    }

    static javafx.scene.control.MenuButton createColumnChooser(TableView<ChildrenView.Child> table) {
        return SummaryTableSupport.createColumnChooser("Columns", List.of(RECORD_ID_COL, DUE_DAY_COL, TRANSPORT_COL));
    }

    private static CopyableTableCell<ChildrenView.Child> copyCell(
        java.util.function.Function<ChildrenView.Child, String> valueProvider,
        Pos alignment
    ) {
        return new CopyableTableCell<>(
            valueProvider,
            value -> SummaryTableSupport.displayText(value),
            ChildrenTableSupport::rowSummary,
            ChildrenView.Child::getRecordId,
            child -> SummaryTableSupport.toPrettyJson(asJson(child)),
            alignment,
            false
        );
    }

    private static LinkedHashMap<String, Object> asJson(ChildrenView.Child child) {
        LinkedHashMap<String, Object> json = new LinkedHashMap<>();
        json.put("recordId", child.getRecordId());
        json.put("childName", child.getName());
        json.put("childIdentifier", child.getChildIdentifier());
        json.put("dateOfBirth", child.getBirthDateText());
        json.put("age", child.getAgeSummary());
        json.put("parentNames", child.getFullParentNames());
        json.put("parentPhones", child.getFullParentPhones());
        json.put("relationship", child.getParentRelationship());
        json.put("billingModel", child.getBillingPlan());
        json.put("invoiceDueDay", child.getPaymentDueDay());
        json.put("casualTransitBilling", casualTransitSummary(child));
        json.put("nfcUid", child.getNfcUid());
        json.put("status", child.getStatus());
        return json;
    }

    private static String rowSummary(ChildrenView.Child child) {
        if (child == null) {
            return "";
        }
        return String.join("\n",
            "Child: " + SummaryTableSupport.displayText(child.getName()),
            "Record ID: " + SummaryTableSupport.displayText(child.getRecordId()),
            "Child ID / MyKid: " + SummaryTableSupport.displayText(child.getChildIdentifier()),
            "DOB: " + SummaryTableSupport.displayText(child.getBirthDateText()),
            "Age: " + SummaryTableSupport.displayText(child.getAgeSummary()),
            "Primary Parent: " + SummaryTableSupport.displayText(child.getPrimaryParentName()),
            "Parent Phone: " + SummaryTableSupport.displayText(child.getParentPhone()),
            "Billing Model: " + SummaryTableSupport.displayText(child.getBillingPlan()),
            "Invoice Due: " + SummaryTableSupport.displayText(child.getPaymentDueDayText()),
            "Casual Transit: " + SummaryTableSupport.displayText(casualTransitSummary(child)),
            "NFC UID: " + SummaryTableSupport.displayText(child.getNfcUid()),
            "Status: " + SummaryTableSupport.displayText(child.getStatus())
        );
    }

    private static String casualTransitSummary(ChildrenView.Child child) {
        if (child != null && child.getBillingPlan() != null && child.getBillingPlan().startsWith("Taska Zurah Age-Based")) {
            return "Separate from monthly billing";
        }
        return child == null ? "-" : child.getTransportEnabledText();
    }

    private static void setPrefWidth(TableColumn<ChildrenView.Child, ?> column, double width) {
        column.setPrefWidth(width);
    }
}