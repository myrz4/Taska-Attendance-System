package nfc;

import java.io.IOException;
import java.time.LocalDate;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * ParentsPane
 * A simple Firestore-backed Parents table with Add/Edit/Delete.
 *
 * Intended to be embedded inside ChildrenView (Children & Parents screen).
 */
public class ParentsPane extends VBox {

    private final TableView<ParentRecord> table = new TableView<>();
    private final ObservableList<ParentRecord> data = FXCollections.observableArrayList();

    private static void logError(String context, Exception error) {
        System.err.println("ParentsPane: " + context + " - " + error.getMessage());
        error.printStackTrace(System.err);
    }

    public ParentsPane() {
        setSpacing(10);
        setPadding(new Insets(10, 0, 0, 0));

        buildTable();

        Button addBtn = new Button("Add New Parent");
        addBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        addBtn.setOnAction(e -> CRUDDialogs.showParentDialog(null, true, this::reload));

        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(table, addBtn);

        reload();
    }

    private void buildTable() {
        table.setItems(data);
        ParentsTableSupport.setupTable(
            table,
            current -> CRUDDialogs.showParentDialog(current, false, ParentsPane.this::reload),
            this::deleteParent
        );
    }

    public final void reload() {
        FamilyManagementLoadSupport.reloadParents(data, ParentsPane::logError);
    }

    private void deleteParent(ParentRecord parent) {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            client.deleteDocument("parents", parent.getParentId());
            reload();
        } catch (IOException | InterruptedException | IllegalStateException ex) {
            logError("failed to delete parent", ex);
            new Alert(Alert.AlertType.ERROR, "Failed to delete parent: " + ex.getMessage()).showAndWait();
        }
    }

    public static class ParentRecord {
        private final String parentId;
        private final String parentName;
        private final String phone;
        private final String relationship;
        private final String childId;
        private final String childName;
        private final LocalDate passcodeExpiry;
        private final String familyKey;
        private final int relationshipPriority;

        // Backward-compatible constructor for older call sites.
        public ParentRecord(String parentId, String parentName, String phone,
                            String relationship,
                            String childId, String childName,
                            LocalDate passcodeExpiry) {
            this(parentId, parentName, phone, relationship, childId, childName, passcodeExpiry, "", 2);
        }

        public ParentRecord(String parentId, String parentName, String phone,
                            String relationship,
                            String childId, String childName,
                            LocalDate passcodeExpiry,
                            String familyKey,
                            int relationshipPriority) {
            this.parentId = parentId;
            this.parentName = parentName;
            this.phone = phone;
            this.relationship = relationship;
            this.childId = childId;
            this.childName = childName;
            this.passcodeExpiry = passcodeExpiry;
            this.familyKey = familyKey;
            this.relationshipPriority = relationshipPriority;
        }

        public String getParentId() { return parentId; }
        public String getParentName() { return parentName; }
        public String getPhone() { return phone; }
        public String getRelationship() { return relationship; }
        public String getChildId() { return childId; }
        public String getChildName() { return childName; }
        public LocalDate getPasscodeExpiry() { return passcodeExpiry; }
        public String getFamilyKey() { return familyKey; }
        public int getRelationshipPriority() { return relationshipPriority; }
    }
}
