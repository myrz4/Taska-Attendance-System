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
final class ParentsTableSupport {
    private ParentsTableSupport() {}

    private static final TableColumn<ParentsPane.ParentRecord, String> RECORD_ID_COL = new TableColumn<>("Record ID");
    private static final TableColumn<ParentsPane.ParentRecord, String> MASKED_IC_COL = new TableColumn<>("Parent IC");
    private static final TableColumn<ParentsPane.ParentRecord, String> CUSTOM_REL_COL = new TableColumn<>("Custom Relationship");

    static void setupTable(
        TableView<ParentsPane.ParentRecord> table,
        Consumer<ParentsPane.ParentRecord> onView,
        Consumer<ParentsPane.ParentRecord> onEdit,
        Consumer<ParentsPane.ParentRecord> onDeleteConfirmed
    ) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ParentsPane.ParentRecord, Integer> noCol = new TableColumn<>("No");
        TableColumn<ParentsPane.ParentRecord, String> nameCol = new TableColumn<>("Parent Name");
        TableColumn<ParentsPane.ParentRecord, String> relationshipCol = new TableColumn<>("Relationship");
        TableColumn<ParentsPane.ParentRecord, String> phoneCol = new TableColumn<>("Phone");
        TableColumn<ParentsPane.ParentRecord, String> childCountCol = new TableColumn<>("Linked Children");
        TableColumn<ParentsPane.ParentRecord, String> childNameCol = new TableColumn<>("Children Summary");
        TableColumn<ParentsPane.ParentRecord, String> notificationsCol = new TableColumn<>("Notifications");
        TableColumn<ParentsPane.ParentRecord, String> icVerifiedCol = new TableColumn<>("IC Verified");
        TableColumn<ParentsPane.ParentRecord, String> statusCol = new TableColumn<>("Status");
        TableColumn<ParentsPane.ParentRecord, Void> actionsCol = new TableColumn<>("Actions");

        noCol.setCellFactory(col -> new TableCell<ParentsPane.ParentRecord, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
                setAlignment(Pos.CENTER);
                setStyle("-fx-font-weight: bold; -fx-text-fill: #181818; -fx-alignment: CENTER;");
            }
        });

        nameCol.setCellValueFactory(new PropertyValueFactory<>("parentName"));
        phoneCol.setCellValueFactory(new PropertyValueFactory<>("phone"));
        relationshipCol.setCellValueFactory(new PropertyValueFactory<>("relationship"));
        childCountCol.setCellValueFactory(new PropertyValueFactory<>("linkedChildrenCountText"));
        childNameCol.setCellValueFactory(new PropertyValueFactory<>("linkedChildrenSummary"));
        notificationsCol.setCellValueFactory(new PropertyValueFactory<>("notificationsSummary"));
        icVerifiedCol.setCellValueFactory(new PropertyValueFactory<>("icVerifiedStatus"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));

        RECORD_ID_COL.setCellValueFactory(new PropertyValueFactory<>("recordId"));
        MASKED_IC_COL.setCellValueFactory(new PropertyValueFactory<>("maskedParentIc"));
        CUSTOM_REL_COL.setCellValueFactory(new PropertyValueFactory<>("customRelationship"));

        RECORD_ID_COL.setVisible(false);
        MASKED_IC_COL.setVisible(false);
        CUSTOM_REL_COL.setVisible(false);

        nameCol.setCellFactory(tc -> copyCell(ParentsPane.ParentRecord::getParentName, Pos.CENTER_LEFT));
        relationshipCol.setCellFactory(tc -> copyCell(ParentsPane.ParentRecord::getRelationship, Pos.CENTER_LEFT));
        phoneCol.setCellFactory(tc -> copyCell(ParentsPane.ParentRecord::getPhone, Pos.CENTER_LEFT));
        childCountCol.setCellFactory(tc -> copyCell(ParentsPane.ParentRecord::getLinkedChildrenCountText, Pos.CENTER));
        childNameCol.setCellFactory(tc -> copyCell(ParentsPane.ParentRecord::getLinkedChildrenSummary, Pos.CENTER_LEFT));
        notificationsCol.setCellFactory(tc -> copyCell(ParentsPane.ParentRecord::getNotificationsSummary, Pos.CENTER_LEFT));
        icVerifiedCol.setCellFactory(tc -> copyCell(ParentsPane.ParentRecord::getIcVerifiedStatus, Pos.CENTER));
        statusCol.setCellFactory(tc -> copyCell(ParentsPane.ParentRecord::getStatus, Pos.CENTER));
        RECORD_ID_COL.setCellFactory(tc -> copyCell(ParentsPane.ParentRecord::getRecordId, Pos.CENTER_LEFT));
        MASKED_IC_COL.setCellFactory(tc -> new MaskedValueTableCell<>(
            ParentsPane.ParentRecord::getParentIc,
            value -> SummaryTableSupport.maskMiddle(value, 3, 2),
            ParentsTableSupport::rowSummary,
            ParentsPane.ParentRecord::getRecordId,
            row -> SummaryTableSupport.toPrettyJson(asJson(row)),
            Pos.CENTER_LEFT
        ));
        CUSTOM_REL_COL.setCellFactory(tc -> copyCell(ParentsPane.ParentRecord::getCustomRelationship, Pos.CENTER_LEFT));

        actionsCol.setCellFactory(tc -> new ActionButtonsTableCell<>(
            ActionButtonsTableCell.ActionSpec.normal("View", onView),
            ActionButtonsTableCell.ActionSpec.normal("Edit", onEdit),
            ActionButtonsTableCell.ActionSpec.destructive("Delete", current -> {
                Alert alert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Delete parent record? This will NOT delete child records.",
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

        setPrefWidth(noCol, 56);
        setPrefWidth(nameCol, 190);
        setPrefWidth(relationshipCol, 130);
        setPrefWidth(phoneCol, 138);
        setPrefWidth(childCountCol, 110);
        setPrefWidth(childNameCol, 220);
        setPrefWidth(notificationsCol, 180);
        setPrefWidth(icVerifiedCol, 104);
        setPrefWidth(statusCol, 96);
        setPrefWidth(actionsCol, 212);
        setPrefWidth(RECORD_ID_COL, 180);
        setPrefWidth(MASKED_IC_COL, 140);
        setPrefWidth(CUSTOM_REL_COL, 180);

        table.getColumns().clear();
        table.getColumns().setAll(Arrays.<TableColumn<ParentsPane.ParentRecord, ?>>asList(
            noCol,
            nameCol,
            relationshipCol,
            phoneCol,
            childCountCol,
            icVerifiedCol,
            statusCol,
            RECORD_ID_COL,
            MASKED_IC_COL,
            CUSTOM_REL_COL,
            actionsCol
        ));

        SummaryTableSupport.configureSummaryTable(
            table,
            "No parents found.",
            ParentsTableSupport::rowSummary,
            ParentsPane.ParentRecord::getRecordId,
            row -> asJson(row),
            onView
        );
    }

    static javafx.scene.control.MenuButton createColumnChooser(TableView<ParentsPane.ParentRecord> table) {
        return SummaryTableSupport.createColumnChooser("Columns", List.of(RECORD_ID_COL, MASKED_IC_COL, CUSTOM_REL_COL));
    }

    private static CopyableTableCell<ParentsPane.ParentRecord> copyCell(
        java.util.function.Function<ParentsPane.ParentRecord, String> valueProvider,
        Pos alignment
    ) {
        return new CopyableTableCell<>(
            valueProvider,
            SummaryTableSupport::displayText,
            ParentsTableSupport::rowSummary,
            ParentsPane.ParentRecord::getRecordId,
            row -> SummaryTableSupport.toPrettyJson(asJson(row)),
            alignment,
            false
        );
    }

    private static LinkedHashMap<String, Object> asJson(ParentsPane.ParentRecord row) {
        LinkedHashMap<String, Object> json = new LinkedHashMap<>();
        json.put("recordId", row.getRecordId());
        json.put("parentName", row.getParentName());
        json.put("relationship", row.getRelationship());
        json.put("customRelationship", row.getCustomRelationship());
        json.put("phone", row.getPhone());
        json.put("parentIc", row.getParentIc());
        json.put("icVerified", row.isIcVerified());
        json.put("linkedChildIds", row.getChildId());
        json.put("linkedChildNames", row.getChildName());
        json.put("linkedChildrenCount", row.getLinkedChildrenCount());
        json.put("notifications", row.getNotificationsSummary());
        json.put("status", row.getStatus());
        return json;
    }

    private static String rowSummary(ParentsPane.ParentRecord row) {
        if (row == null) {
            return "";
        }
        return String.join("\n",
            "Parent: " + SummaryTableSupport.displayText(row.getParentName()),
            "Record ID: " + SummaryTableSupport.displayText(row.getRecordId()),
            "Relationship: " + SummaryTableSupport.displayText(row.getRelationship()),
            "Phone: " + SummaryTableSupport.displayText(row.getPhone()),
            "Linked Children: " + SummaryTableSupport.displayText(row.getLinkedChildrenSummary()),
            "Notifications: " + SummaryTableSupport.displayText(row.getNotificationsSummary()),
            "IC Verified: " + SummaryTableSupport.displayText(row.getIcVerifiedStatus()),
            "Status: " + SummaryTableSupport.displayText(row.getStatus())
        );
    }

    private static void setPrefWidth(TableColumn<ParentsPane.ParentRecord, ?> column, double width) {
        column.setPrefWidth(width);
    }
}