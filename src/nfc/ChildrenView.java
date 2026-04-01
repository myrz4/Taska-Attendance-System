package nfc;

/**
 * Firestore-based ChildrenView
 * Fixed to properly handle Firestore field names and date parsing.
 */
@SuppressWarnings("this-escape")
public class ChildrenView extends javafx.scene.layout.VBox {

    private final javafx.scene.control.TableView<Child> table = new javafx.scene.control.TableView<>();
    private final javafx.collections.ObservableList<Child> data = javafx.collections.FXCollections.observableArrayList();

    private static void logError(String context, Exception error) {
        System.err.println("ChildrenView: " + context + " - " + error.getMessage());
        error.printStackTrace(System.err);
    }

    public ChildrenView() {
        buildTable();
        javafx.scene.control.Button addChildBtn = ChildrenLayoutSupport.createAddChildButton(() -> CRUDDialogs.showChildDialog(null, true, () -> reload()));
        javafx.scene.layout.VBox childrenTabContent = new javafx.scene.layout.VBox(10, table, addChildBtn);
        javafx.scene.layout.VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.layout.BorderPane layout = ChildrenLayoutSupport.buildLayout(childrenTabContent);
        this.setFillWidth(true);
        this.getChildren().add(layout);

        reload();
    }

    private void buildTable() {
        table.setItems(data);
        ChildrenTableSupport.setupTable(
            table,
            child -> showEdit(child),
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
        CRUDDialogs.showChildDialog(c, false, () -> reload());
    }

    private void deleteChild(Child c) {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            client.deleteDocument("children", c.getChildId());

            System.out.println("🗑 Deleted child " + c.getChildId());
        } catch (java.io.IOException | InterruptedException | IllegalStateException ex) {
            logError("failed to delete child", ex);
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                    "Failed to delete child: " + ex.getMessage())
                    .showAndWait();
        }
    }

    public static class Child {
        private final String childId;
        private final String name;
        private final java.time.LocalDate birthDate;
        private final String parentName;
        private final String parentRelationship;
        private final String parentContact;
        private final String nfcUid;

        public Child(String childId, String name, java.time.LocalDate birthDate,
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
        public java.time.LocalDate getBirthDate() { return birthDate; }
        public String getParentName() { return parentName; }
        public String getParentRelationship() { return parentRelationship; }
        public String getParentContact() { return parentContact; }
        public String getNfcUid() { return nfcUid; }
    }
}