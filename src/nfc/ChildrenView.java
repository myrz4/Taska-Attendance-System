package nfc;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;

import nfc.ChildrenView.Child;

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
        // --- HEADER ---
        HBox headerBar = new HBox(18);
        headerBar.setAlignment(Pos.CENTER_LEFT);
        headerBar.setPrefHeight(70);
        headerBar.setMaxWidth(Double.MAX_VALUE);
        headerBar.setStyle(
            "-fx-background-color: #2e8b57, #FECF4D;" +
            "-fx-background-insets: 0, 0 0 3 0;" +
            "-fx-background-radius: 0, 0;"
        );
        ImageView honeyPot = new ImageView(ImageLoader.loadSafe("hive2.png"));
        honeyPot.setFitWidth(54);
        honeyPot.setFitHeight(54);

        Label headerTitle = new Label("Children & Parents");
        headerTitle.setStyle("-fx-font-family: Impact; -fx-font-size: 44px; -fx-font-weight: bold; -fx-text-fill: #181818;");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        headerBar.getChildren().addAll(honeyPot, headerTitle, headerSpacer);

        // --- MAIN CONTENT ---
        VBox mainContent = new VBox(10);
        mainContent.setPadding(new Insets(20));
        mainContent.setAlignment(Pos.TOP_LEFT);

        // Children tab
        buildTable();
        Button addChildBtn = new Button("Add New Child");
        addChildBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        addChildBtn.setOnAction(e -> CRUDDialogs.showChildDialog(null, true, this::reload));
        VBox childrenTabContent = new VBox(10, table, addChildBtn);
        VBox.setVgrow(table, Priority.ALWAYS);

        // Parents tab
        ParentsPane parentsPane = new ParentsPane();

        TabPane tabs = new TabPane();
        Tab childrenTab = new Tab("Children", childrenTabContent);
        childrenTab.setClosable(false);
        Tab parentsTab = new Tab("Parents", parentsPane);
        parentsTab.setClosable(false);
        tabs.getTabs().addAll(childrenTab, parentsTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        mainContent.getChildren().add(tabs);

        BorderPane layout = new BorderPane();
        layout.setTop(headerBar);
        layout.setCenter(mainContent);
        layout.setStyle("-fx-background-color: linear-gradient(to bottom right, #86d67f 0%, #76cc6e 100%);");

        VBox.setVgrow(layout, Priority.ALWAYS);
        this.setFillWidth(true);
        this.getChildren().add(layout);

        reload();
    }

    private void buildTable() {
        table.setItems(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Child, Integer> idCol = new TableColumn<>("No");
        TableColumn<Child, String> nameCol = new TableColumn<>("Name");
        TableColumn<Child, LocalDate> dobCol = new TableColumn<>("Birth Date");
        TableColumn<Child, String> parentNameCol = new TableColumn<>("Parent Name");
        TableColumn<Child, String> relationshipCol = new TableColumn<>("Relationship");
        TableColumn<Child, String> parentContactCol = new TableColumn<>("Parent Contact");
        TableColumn<Child, String> uidCol = new TableColumn<>("NFC UID");
        TableColumn<Child, Void> actionsCol = new TableColumn<>("Actions");

        idCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else {
                    setText(String.valueOf(getIndex() + 1));
                }
                setStyle("-fx-font-weight: bold; -fx-text-fill: #181818;");
            }
        });

        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        dobCol.setCellValueFactory(new PropertyValueFactory<>("birthDate"));
        parentNameCol.setCellValueFactory(new PropertyValueFactory<>("parentName"));
        relationshipCol.setCellValueFactory(new PropertyValueFactory<>("parentRelationship"));
        parentContactCol.setCellValueFactory(new PropertyValueFactory<>("parentContact"));
        uidCol.setCellValueFactory(new PropertyValueFactory<>("nfcUid"));

        // Center alignment for all columns
        idCol.setStyle("-fx-alignment: CENTER;");
        nameCol.setStyle("-fx-alignment: CENTER;");
        dobCol.setStyle("-fx-alignment: CENTER;");
        parentNameCol.setStyle("-fx-alignment: CENTER;");
        relationshipCol.setStyle("-fx-alignment: CENTER;");
        parentContactCol.setStyle("-fx-alignment: CENTER;");
        uidCol.setStyle("-fx-alignment: CENTER;");
        actionsCol.setStyle("-fx-alignment: CENTER;");

        String fontStyle = "-fx-font-family: 'Poppins', 'Arial', sans-serif; -fx-font-size: 13px;-fx-font-weight: bold; -fx-text-fill: #181818;";

        nameCol.setCellFactory(tc -> makeCell(fontStyle));
        dobCol.setCellFactory(tc -> makeCell(fontStyle));
        parentNameCol.setCellFactory(tc -> makeMultilineCell(fontStyle));
        relationshipCol.setCellFactory(tc -> makeMultilineCell(fontStyle));
        parentContactCol.setCellFactory(tc -> makeMultilineCell(fontStyle));
        uidCol.setCellFactory(tc -> makeCell(fontStyle));

        actionsCol.setCellFactory(tc -> new TableCell<>() {
            private final Button edit = new Button("Edit");
            private final Button del = new Button("Delete");
            {
                edit.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
                del.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");

                edit.setOnAction(e -> showEdit(getCurrent()));
                del.setOnAction(e -> {
                    Child current = getCurrent();
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete child and all their attendance records?", ButtonType.YES, ButtonType.NO);
                    alert.setHeaderText("Confirm Delete");
                    alert.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            deleteChild(current);
                            data.remove(current);
                        }
                    });
                });
            }

            private Child getCurrent() { return getTableView().getItems().get(getIndex()); }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else setGraphic(new HBox(5, edit, del));
            }
        });

        table.getColumns().setAll(idCol, nameCol, dobCol, parentNameCol, relationshipCol, parentContactCol, uidCol, actionsCol);
    }

    private <T> TableCell<Child, T> makeCell(String style) {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setStyle(style);
                setAlignment(Pos.CENTER);
            }
        };
    }

    private TableCell<Child, String> makeMultilineCell(String style) {
        return new TableCell<Child, String>() {
            private final Text text = new Text();

            {
                text.setStyle(style);
                text.setTextAlignment(TextAlignment.CENTER);
                text.wrappingWidthProperty().bind(widthProperty().subtract(12));
                setPrefHeight(Control.USE_COMPUTED_SIZE);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(null);
                    setGraphic(null);
                } else {
                    text.setText(item);
                    setText(null);
                    setGraphic(text);
                }
                setAlignment(Pos.CENTER);
            }
        };
    }

    // ✅ FIRESTORE LOAD FUNCTION
    // ✅ ASYNC FIRESTORE LOAD FUNCTION
    public void reload() {
        data.clear();
        if (!UserSession.isLoggedIn()) return;

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                FirestoreRestClient client = FirestoreRest.forCurrentUser();

                List<FsDocument> childrenDocs = client.listDocuments("children");

                Map<String, String> nfcUidToChildId = new HashMap<>();
                Set<String> allChildIds = new HashSet<>();
                for (FsDocument d : childrenDocs) {
                    String cid = d.getId();
                    allChildIds.add(cid);
                    String nfc = safeStr(d.get("nfc_uid")).trim();
                    if (!nfc.isEmpty()) {
                        nfcUidToChildId.put(nfc, cid);
                    }
                }

                Map<String, List<ParentLink>> childToParents = new HashMap<>();
                try {
                    List<FsDocument> parentDocs = client.listDocuments("parents");
                    for (FsDocument p : parentDocs) {
                        String parentId = p == null ? "" : safeStr(p.getId()).trim();
                        String pName = safeStr(p.get("parentName"));
                        String pPhone = safeStr(p.get("phone"));
                        String relationship = formatRelationship(p);
                        int relationshipPriority = relationshipPriority(p);

                        List<String> rawChildIds = extractChildIdsFromParent(p);
                        for (String raw : rawChildIds) {
                            if (raw == null || raw.trim().isEmpty()) continue;
                            String resolved = raw.trim();

                            if (!allChildIds.contains(resolved)) {
                                String mapped = nfcUidToChildId.get(resolved);
                                if (mapped != null && !mapped.trim().isEmpty()) {
                                    resolved = mapped.trim();
                                }
                            }

                            List<ParentLink> list = childToParents.get(resolved);
                            if (list == null) {
                                list = new ArrayList<>();
                                childToParents.put(resolved, list);
                            }
                            list.add(new ParentLink(parentId, relationship, relationshipPriority, pName, pPhone));
                        }
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ Could not build parent->child map: " + e.getMessage());
                }

                ObservableList<Child> tempList = FXCollections.observableArrayList();
                Map<String, String> childFamilyKey = new HashMap<>();

                for (FsDocument doc : childrenDocs) {
                    String migratedTo = safeStr(doc.get("migratedToChildId")).trim();
                    if (!migratedTo.isEmpty()) {
                        continue;
                    }

                    String name = doc.getString("name");
                    String childId = doc.getId();

                    String parentRelationship = "";
                    String parentName = "";
                    String parentContact = "";
                    String familyKey = "";
                    List<ParentLink> parents = childToParents.get(childId);
                    if (parents != null && !parents.isEmpty()) {
                        List<ParentLink> unique = new ArrayList<>();
                        Set<String> seen = new HashSet<>();
                        for (ParentLink pl : parents) {
                            String idKey = pl.parentId == null ? "" : pl.parentId.trim();
                            String key = !idKey.isEmpty()
                                ? ("id:" + idKey)
                                : (safeStr(pl.relationship).trim().toLowerCase() + "|" + (pl.name == null ? "" : pl.name.trim()) + "|" + (pl.phone == null ? "" : pl.phone.trim()));
                            if (seen.add(key)) unique.add(pl);
                        }

                        unique.sort((a, b) -> {
                            int pr = Integer.compare(a.relationshipPriority, b.relationshipPriority);
                            if (pr != 0) return pr;

                            String ar = a.relationship == null ? "" : a.relationship.trim().toLowerCase();
                            String br = b.relationship == null ? "" : b.relationship.trim().toLowerCase();
                            int rc = ar.compareTo(br);
                            if (rc != 0) return rc;

                            String an = a.name == null ? "" : a.name.trim().toLowerCase();
                            String bn = b.name == null ? "" : b.name.trim().toLowerCase();
                            int c = an.compareTo(bn);
                            if (c != 0) return c;
                            String ap = a.phone == null ? "" : a.phone.trim();
                            String bp = b.phone == null ? "" : b.phone.trim();
                            return ap.compareTo(bp);
                        });

                        List<String> relationshipLines = new ArrayList<>();
                        List<String> nameLines = new ArrayList<>();
                        List<String> phoneLines = new ArrayList<>();
                        List<String> familyParts = new ArrayList<>();
                        for (int i = 0; i < unique.size(); i++) {
                            ParentLink pl = unique.get(i);
                            String pn = pl.name == null ? "" : pl.name.trim();
                            String ph = pl.phone == null ? "" : pl.phone.trim();
                            String rel = pl.relationship == null ? "" : pl.relationship.trim();
                            if (rel.isEmpty()) rel = "Guardian";
                            relationshipLines.add(rel);
                            nameLines.add(pn.isEmpty() ? "-" : pn);
                            phoneLines.add(ph.isEmpty() ? "-" : ph);

                            String pid = pl.parentId == null ? "" : pl.parentId.trim();
                            if (!pid.isEmpty()) {
                                familyParts.add("id:" + pid);
                            } else {
                                familyParts.add(rel.toLowerCase() + "|" + pn.toLowerCase() + "|" + ph);
                            }
                        }

                        parentRelationship = String.join("\n", relationshipLines);
                        parentName = String.join("\n", nameLines);
                        parentContact = String.join("\n", phoneLines);
                        familyKey = String.join(";", familyParts);
                    } else {
                        parentRelationship = "";
                        parentName = safeStr(doc.get("parentName"));
                        parentContact = safeStr(doc.get("parentContact"));
                        familyKey = childId;
                    }

                    childFamilyKey.put(childId, familyKey == null ? "" : familyKey);

                    String nfcUid = safeStr(doc.get("nfc_uid")).trim();
                    if (nfcUid.isEmpty()) nfcUid = childId;

                    LocalDate birthDate = null;
                    Object birthObj = doc.get("birthDate");
                    if (birthObj != null) {
                        try {
                            if (birthObj instanceof java.util.Date d) {
                                birthDate = d.toInstant().atZone(java.time.ZoneId.of("Asia/Kuala_Lumpur")).toLocalDate();
                            } else {
                                String raw = birthObj.toString().trim();
                                if (!raw.isEmpty()) {
                                    java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
                                            "EEE MMM dd yyyy HH:mm:ss 'GMT'Z '(Malaysia Time)'", java.util.Locale.ENGLISH);

                                    if (raw.matches("\\d{4}-\\d{2}-\\d{2}")) {
                                        birthDate = LocalDate.parse(raw);
                                    } else {
                                        java.util.Date parsedDate = fmt.parse(raw);
                                        birthDate = parsedDate.toInstant()
                                                .atZone(java.time.ZoneId.of("Asia/Kuala_Lumpur"))
                                                .toLocalDate();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("⚠️ Could not parse birthDate: " + birthObj);
                        }
                    }

                    tempList.add(new Child(childId, name, birthDate, parentName, parentRelationship, parentContact, nfcUid));
                }

                // Group by family (same set of linked parents) for easier display.
                FXCollections.sort(tempList, (a, b) -> {
                    String ak = childFamilyKey.get(a.getChildId());
                    String bk = childFamilyKey.get(b.getChildId());
                    ak = ak == null ? "" : ak.toLowerCase();
                    bk = bk == null ? "" : bk.toLowerCase();
                    int c = ak.compareTo(bk);
                    if (c != 0) return c;
                    String an = a.getName() == null ? "" : a.getName().trim().toLowerCase();
                    String bn = b.getName() == null ? "" : b.getName().trim().toLowerCase();
                    c = an.compareTo(bn);
                    if (c != 0) return c;
                    String aid = a.getChildId() == null ? "" : a.getChildId();
                    String bid = b.getChildId() == null ? "" : b.getChildId();
                    return aid.compareTo(bid);
                });

                javafx.application.Platform.runLater(() -> data.setAll(tempList));
            } catch (Exception e) {
                logError("failed to load children", e);
                javafx.application.Platform.runLater(() ->
                    new Alert(Alert.AlertType.ERROR, "Failed to load children: " + e.getMessage()).showAndWait()
                );
            }
        });
    }

    private static String safeStr(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static List<String> extractChildIdsFromParent(FsDocument parentDoc) {
        if (parentDoc == null) return Collections.emptyList();

        List<String> out = new ArrayList<>();
        Object childIdsRaw = parentDoc.get("childIds");
        Object childRefsRaw = parentDoc.get("childRefs");
        if (childRefsRaw == null) childRefsRaw = parentDoc.get("childrenRefs");

        // Prefer childIds array
        if (childIdsRaw instanceof List) {
            List list = (List) childIdsRaw;
            for (Object o : list) {
                if (o == null) continue;
                String s = String.valueOf(o).trim();
                if (!s.isEmpty()) out.add(s);
            }
        }

        // Or derive from childRefs array
        if (out.isEmpty() && childRefsRaw instanceof List) {
            List list = (List) childRefsRaw;
            for (Object o : list) {
                String id = extractChildIdFromRef(o);
                if (id != null && !id.trim().isEmpty()) out.add(id.trim());
            }
        }

        // Legacy fallback
        if (out.isEmpty()) {
            String legacy = safeStr(parentDoc.get("childId")).trim();
            if (!legacy.isEmpty()) out.add(legacy);
        }

        // Dedupe, keep order
        List<String> deduped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String s : out) {
            if (seen.add(s)) deduped.add(s);
        }
        return deduped;
    }

    private static String extractChildIdFromRef(Object raw) {
        if (raw == null) return null;

        // Or a string like "/children/<id>" or "children/<id>"
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (s.isEmpty()) return null;
            String path = s.startsWith("/") ? s.substring(1) : s;
            int idx = path.indexOf("children/");
            if (idx >= 0) {
                String tail = path.substring(idx + "children/".length());
                int slash = tail.indexOf('/');
                return slash >= 0 ? tail.substring(0, slash) : tail;
            }
        }

        return null;
    }

    private static class ParentLink {
        final String parentId;
        final String relationship;
        final int relationshipPriority;
        final String name;
        final String phone;

        ParentLink(String parentId, String relationship, int relationshipPriority, String name, String phone) {
            this.parentId = parentId;
            this.relationship = relationship;
            this.relationshipPriority = relationshipPriority;
            this.name = name;
            this.phone = phone;
        }
    }

    private static int relationshipPriority(FsDocument parentDoc) {
        if (parentDoc == null) return 2;
        String type = safeStr(parentDoc.get("relationshipType")).trim().toLowerCase();
        return switch (type) {
            case "mother" -> 0;
            case "father" -> 1;
            default -> 2;
        };
    }

    private static String formatRelationship(FsDocument parentDoc) {
        if (parentDoc == null) return "Guardian";
        String type = safeStr(parentDoc.get("relationshipType")).trim().toLowerCase();
        String label = safeStr(parentDoc.get("relationshipLabel")).trim();

        return switch (type) {
            case "mother" -> "Mother";
            case "father" -> "Father";
            case "guardian" -> label.isEmpty() ? "Guardian" : label;
            case "" -> label.isEmpty() ? "Guardian" : label;
            default -> label.isEmpty() ? type : label;
        };
    }

    private void showEdit(Child c) {
        CRUDDialogs.showChildDialog(c, false, this::reload);
    }

    private void deleteChild(Child c) {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            client.deleteDocument("children", c.getChildId());

            System.out.println("🗑 Deleted child " + c.getChildId());
        } catch (Exception ex) {
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