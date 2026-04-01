package nfc;

import java.io.IOException;
import java.time.LocalDate;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Firestore-based ChildrenView
 * Fixed to properly handle Firestore field names and date parsing.
 */
public class ChildrenView extends VBox {

    private final TableView<Child> table = new TableView<>();
    private final ObservableList<Child> data = FXCollections.observableArrayList();

    private static void logError(String context, Exception error) {
        System.err.println("ChildrenView: " + context + " - " + error.getMessage());
        error.printStackTrace(System.err);
    }

    public ChildrenView() {
        buildTable();
        Button addChildBtn = ChildrenLayoutSupport.createAddChildButton(() -> CRUDDialogs.showChildDialog(null, true, this::reload));
        VBox childrenTabContent = new VBox(10, table, addChildBtn);
        VBox.setVgrow(table, Priority.ALWAYS);

        BorderPane layout = ChildrenLayoutSupport.buildLayout(this, childrenTabContent);
        this.getChildren().add(layout);

        reload();
    }

    private void buildTable() {
        table.setItems(data);
        ChildrenTableSupport.setupTable(
            table,
            this::showEdit,
            child -> {
                deleteChild(child);
                data.remove(child);
            }
        );
    }

    // ✅ FIRESTORE LOAD FUNCTION
    // ✅ ASYNC FIRESTORE LOAD FUNCTION
    public final void reload() {
        FamilyManagementLoadSupport.reloadChildren(data, ChildrenView::logError);
    }

    private void showEdit(Child c) {
        CRUDDialogs.showChildDialog(c, false, this::reload);
    }

    private void deleteChild(Child c) {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            client.deleteDocument("children", c.getChildId());

            System.out.println("🗑 Deleted child " + c.getChildId());
        } catch (IOException | InterruptedException | IllegalStateException ex) {
            logError("failed to delete child", ex);
            new Alert(Alert.AlertType.ERROR,
                    "Failed to delete child: " + ex.getMessage())
                    .showAndWait();
        }
    }

    public static class Child {
        private final String childId;
        private final String name;
        private final LocalDate birthDate;
        private final String parentName;
        private final String parentRelationship;
        private final String parentContact;
        private final String nfcUid;

        public Child(String childId, String name, LocalDate birthDate,
                String parentName, String parentRelationship, String parentContact,
                String nfcUid) {
            this.childId = childId;
            this.name = name;
            this.birthDate = birthDate;
            this.parentName = parentName;
            this.parentRelationship = parentRelationship;
            this.parentContact = parentContact;
            this.nfcUid = nfcUid;
        }

        public String getChildId() { return childId; }
        public String getName() { return name; }
        public LocalDate getBirthDate() { return birthDate; }
        public String getParentName() { return parentName; }
        public String getParentRelationship() { return parentRelationship; }
        public String getParentContact() { return parentContact; }
        public String getNfcUid() { return nfcUid; }
    }
}