package nfc;

/**
 * Firestore-based ChildrenView
 * Fixed to properly handle Firestore field names and date parsing.
 */
@SuppressWarnings("this-escape")
public class ChildrenView extends javafx.scene.layout.VBox {

    private final javafx.scene.control.TableView<Child> table = new javafx.scene.control.TableView<>();
    private final javafx.collections.ObservableList<Child> master = javafx.collections.FXCollections.observableArrayList();
    private final javafx.collections.transformation.FilteredList<Child> filtered =
        new javafx.collections.transformation.FilteredList<>(master, row -> true);
    private final javafx.collections.transformation.SortedList<Child> sorted =
        new javafx.collections.transformation.SortedList<>(filtered);

    private static void logError(String context, Exception error) {
        System.err.println("ChildrenView: " + context + " - " + error.getMessage());
        error.printStackTrace(System.err);
    }

    public ChildrenView() {
        buildTable();
        javafx.scene.control.TextField searchField = SummaryTableSupport.createSearchField(
            "Search child / MyKid / parent / phone / NFC..."
        );
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase(java.util.Locale.ROOT);
            filtered.setPredicate(child -> child == null || query.isEmpty() || child.matchesSearch(query));
        });

        javafx.scene.control.MenuButton columnChooser = ChildrenTableSupport.createColumnChooser(table);
        javafx.scene.control.Button addChildBtn = ChildrenLayoutSupport.createAddChildButton(
            () -> CRUDDialogs.showRegistrationWizard(() -> {
                reload();
                ParentsPane.refreshOpenPane();
                FirestoreService.refreshAfterRosterMutation();
            })
        );

        javafx.scene.layout.HBox toolbar = new javafx.scene.layout.HBox(10, searchField, columnChooser, addChildBtn);
        toolbar.getStyleClass().add("summary-toolbar");
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.layout.HBox.setHgrow(searchField, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.layout.VBox childrenTabContent = new javafx.scene.layout.VBox(10, toolbar, table);
        javafx.scene.layout.VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.layout.BorderPane layout = ChildrenLayoutSupport.buildLayout(childrenTabContent);
        this.setFillWidth(true);
        this.getChildren().add(layout);

        reload();
    }

    private void buildTable() {
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        ChildrenTableSupport.setupTable(
            table,
            this::showDetails,
            child -> showEdit(child),
            child -> {
                deleteChild(child);
                master.remove(child);
            }
        );
    }

    // ✅ FIRESTORE LOAD FUNCTION
    // ✅ ASYNC FIRESTORE LOAD FUNCTION
    public final void reload() {
        FamilyManagementLoadSupport.reloadChildren(master, ChildrenView::logError);
    }

    private void showEdit(Child c) {
        CRUDDialogs.showChildDialog(c, false, () -> {
            reload();
            FirestoreService.refreshAfterRosterMutation();
        });
    }

    private void showDetails(Child child) {
        FamilyRecordViewDialogSupport.showChildDetails(child);
    }

    private void deleteChild(Child c) {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            client.deleteDocument("children", c.getChildId());

            System.out.println("🗑 Deleted child " + c.getChildId());
            FirestoreService.refreshAfterRosterMutation();
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
        private final String childIdentifier;
        private final java.time.LocalDate birthDate;
        private final String ageSummary;
        private final String primaryParentName;
        private final String parentPhone;
        private final String billingPlan;
        private final int paymentDueDay;
        private final boolean transportEnabled;
        private final String nfcUid;
        private final String status;
        private final String parentRelationship;
        private final String fullParentNames;
        private final String fullParentPhones;

        public Child(
            String childId,
            String name,
            java.time.LocalDate birthDate,
            String parentName,
            String relationship,
            String parentContact,
            String nfcUid
        ) {
            this(
                childId,
                name,
                "",
                birthDate,
                "",
                parentName,
                parentContact,
                "",
                0,
                false,
                nfcUid,
                "Active",
                relationship,
                parentName,
                parentContact
            );
        }

        public Child(
            String childId,
            String name,
            String childIdentifier,
            java.time.LocalDate birthDate,
            String ageSummary,
            String primaryParentName,
            String parentPhone,
            String billingPlan,
            int paymentDueDay,
            boolean transportEnabled,
            String nfcUid,
            String status,
            String parentRelationship,
            String fullParentNames,
            String fullParentPhones
        ) {
            this.childId = childId;
            this.name = name;
            this.childIdentifier = childIdentifier;
            this.birthDate = birthDate;
            this.ageSummary = ageSummary;
            this.primaryParentName = primaryParentName;
            this.parentPhone = parentPhone;
            this.billingPlan = billingPlan;
            this.paymentDueDay = paymentDueDay;
            this.transportEnabled = transportEnabled;
            this.parentRelationship = parentRelationship;
            this.nfcUid = nfcUid;
            this.status = status;
            this.fullParentNames = fullParentNames;
            this.fullParentPhones = fullParentPhones;
        }

        public String getChildId() { return childId; }
        public String getRecordId() { return childId; }
        public String getName() { return name; }
        public String getChildIdentifier() { return childIdentifier; }
        public java.time.LocalDate getBirthDate() { return birthDate; }
        public String getBirthDateText() { return birthDate == null ? "-" : birthDate.toString(); }
        public String getAgeSummary() { return ageSummary; }
        public String getPrimaryParentName() { return primaryParentName; }
        public String getParentPhone() { return parentPhone; }
        public String getBillingPlan() { return billingPlan; }
        public String getPaymentDueDayText() { return paymentDueDay <= 0 ? "-" : String.valueOf(paymentDueDay); }
        public String getTransportEnabledText() { return transportEnabled ? "Yes" : "No"; }
        public int getPaymentDueDay() { return paymentDueDay; }
        public boolean isTransportEnabled() { return transportEnabled; }
        public String getParentRelationship() { return parentRelationship; }
        public String getParentName() { return fullParentNames; }
        public String getParentContact() { return fullParentPhones; }
        public String getNfcUid() { return nfcUid; }
        public String getStatus() { return status; }
        public String getFullParentNames() { return fullParentNames; }
        public String getFullParentPhones() { return fullParentPhones; }

        public boolean matchesSearch(String query) {
            return contains(name, query)
                || contains(childIdentifier, query)
                || contains(childId, query)
                || contains(primaryParentName, query)
                || contains(parentPhone, query)
                || contains(billingPlan, query)
                || contains(status, query)
                || contains(nfcUid, query);
        }

        private static boolean contains(String value, String query) {
            return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(query);
        }
    }
}