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
    private static ParentsPane currentInstance;

    private final TableView<ParentRecord> table = new TableView<>();
    private final ObservableList<ParentRecord> master = FXCollections.observableArrayList();
    private final javafx.collections.transformation.FilteredList<ParentRecord> filtered =
        new javafx.collections.transformation.FilteredList<>(master, row -> true);
    private final javafx.collections.transformation.SortedList<ParentRecord> sorted =
        new javafx.collections.transformation.SortedList<>(filtered);

    private static void logError(String context, Exception error) {
        System.err.println("ParentsPane: " + context + " - " + error.getMessage());
        error.printStackTrace(System.err);
    }

    public ParentsPane() {
        currentInstance = this;
        setSpacing(10);
        setPadding(new Insets(10, 0, 0, 0));

        buildTable();

        javafx.scene.control.TextField searchField = SummaryTableSupport.createSearchField(
            "Search parent / phone / child / notifications..."
        );
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase(java.util.Locale.ROOT);
            filtered.setPredicate(parent -> parent == null || query.isEmpty() || parent.matchesSearch(query));
        });

        javafx.scene.control.MenuButton columnChooser = createColumnChooser();
        Button addBtn = SummaryTableSupport.createPrimaryButton("Add New Parent");
        addBtn.setOnAction(e -> CRUDDialogs.showParentDialog(null, true, this::reload));

        javafx.scene.layout.HBox toolbar = new javafx.scene.layout.HBox(10, searchField, columnChooser, addBtn);
        toolbar.getStyleClass().add("summary-toolbar");
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.layout.HBox.setHgrow(searchField, Priority.ALWAYS);

        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(toolbar, table);

        reload();
    }

    public static void refreshOpenPane() {
        if (currentInstance != null) {
            currentInstance.reload();
        }
    }

    private void buildTable() {
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        ParentsTableSupport.setupTable(
            table,
            current -> CRUDDialogs.showParentDialog(current, false, ParentsPane.this::reload),
            this::deleteParent
        );
    }

    public final void reload() {
        FamilyManagementLoadSupport.reloadParents(master, ParentsPane::logError);
    }

    private javafx.scene.control.MenuButton createColumnChooser() {
        java.util.List<javafx.scene.control.TableColumn<ParentRecord, ?>> optionalColumns = new java.util.ArrayList<>();
        for (javafx.scene.control.TableColumn<ParentRecord, ?> column : table.getColumns()) {
            String title = column.getText();
            if ("Record ID".equals(title) || "Parent IC".equals(title) || "Custom Relationship".equals(title)) {
                optionalColumns.add(column);
            }
        }
        return SummaryTableSupport.createColumnChooser("Columns", optionalColumns);
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
        private final String notificationsSummary;
        private final boolean icVerified;
        private final String parentIc;
        private final String status;
        private final String customRelationship;

        // Backward-compatible constructor for older call sites.
        public ParentRecord(String parentId, String parentName, String phone,
                            String relationship,
                            String childId, String childName,
                            LocalDate passcodeExpiry) {
            this(parentId, parentName, phone, relationship, childId, childName, passcodeExpiry, "", 2, "-", false, "", "Active", "");
        }

        public ParentRecord(String parentId, String parentName, String phone,
                            String relationship,
                            String childId, String childName,
                            LocalDate passcodeExpiry,
                            String familyKey,
                            int relationshipPriority) {
            this(parentId, parentName, phone, relationship, childId, childName, passcodeExpiry, familyKey, relationshipPriority, "-", false, "", "Active", "");
        }

        public ParentRecord(String parentId, String parentName, String phone,
                            String relationship,
                            String childId, String childName,
                            LocalDate passcodeExpiry,
                            String familyKey,
                            int relationshipPriority,
                            String notificationsSummary,
                            boolean icVerified,
                            String parentIc,
                            String status,
                            String customRelationship) {
            this.parentId = parentId;
            this.parentName = parentName;
            this.phone = phone;
            this.relationship = relationship;
            this.childId = childId;
            this.childName = childName;
            this.passcodeExpiry = passcodeExpiry;
            this.familyKey = familyKey;
            this.relationshipPriority = relationshipPriority;
            this.notificationsSummary = notificationsSummary;
            this.icVerified = icVerified;
            this.parentIc = parentIc;
            this.status = status;
            this.customRelationship = customRelationship;
        }

        public String getParentId() { return parentId; }
        public String getRecordId() { return parentId; }
        public String getParentName() { return parentName; }
        public String getPhone() { return phone; }
        public String getRelationship() { return relationship; }
        public String getChildId() { return childId; }
        public String getChildName() { return childName; }
        public int getLinkedChildrenCount() {
            if (childName == null || childName.isBlank()) {
                return 0;
            }
            return (int) java.util.Arrays.stream(childName.split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .count();
        }
        public String getLinkedChildrenCountText() { return String.valueOf(getLinkedChildrenCount()); }
        public String getLinkedChildrenSummary() {
            java.util.List<String> names = java.util.Arrays.stream((childName == null ? "" : childName).split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
            return SummaryTableSupport.compactListSummary(names, 2);
        }
        public String getNotificationsSummary() { return notificationsSummary; }
        public String getIcVerifiedStatus() { return icVerified ? "Verified" : "Unverified"; }
        public boolean isIcVerified() { return icVerified; }
        public String getMaskedParentIc() { return SummaryTableSupport.maskMiddle(parentIc, 3, 2); }
        public String getParentIc() { return parentIc; }
        public String getStatus() { return status; }
        public String getCustomRelationship() { return customRelationship; }
        public LocalDate getPasscodeExpiry() { return passcodeExpiry; }
        public String getFamilyKey() { return familyKey; }
        public int getRelationshipPriority() { return relationshipPriority; }

        public boolean matchesSearch(String query) {
            return contains(parentName, query)
                || contains(phone, query)
                || contains(relationship, query)
                || contains(childName, query)
                || contains(notificationsSummary, query)
                || contains(status, query)
                || contains(parentIc, query)
                || contains(parentId, query);
        }

        private static boolean contains(String value, String query) {
            return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(query);
        }
    }
}
