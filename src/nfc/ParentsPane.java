package nfc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

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
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ParentRecord, Integer> noCol = new TableColumn<>("No");
        TableColumn<ParentRecord, String> nameCol = new TableColumn<>("Parent Name");
        TableColumn<ParentRecord, String> relationshipCol = new TableColumn<>("Relationship");
        TableColumn<ParentRecord, String> phoneCol = new TableColumn<>("Phone");
        TableColumn<ParentRecord, String> childNameCol = new TableColumn<>("Child Name");
        TableColumn<ParentRecord, Void> actionsCol = new TableColumn<>("Actions");

        noCol.setCellFactory(col -> new TableCell<>() {
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
        childNameCol.setCellValueFactory(new PropertyValueFactory<>("childName"));

        String fontStyle = "-fx-font-family: 'Poppins', 'Arial', sans-serif; -fx-font-size: 13px;-fx-font-weight: bold; -fx-text-fill: #181818;";

        // Center alignment for all columns
        String centerCol = "-fx-alignment: CENTER;";
        nameCol.setStyle(centerCol);
        relationshipCol.setStyle(centerCol);
        phoneCol.setStyle(centerCol);
        childNameCol.setStyle(centerCol);
        actionsCol.setStyle(centerCol);
        noCol.setStyle(centerCol);

        nameCol.setCellFactory(tc -> makeCell(fontStyle));
        relationshipCol.setCellFactory(tc -> makeCell(fontStyle));
        phoneCol.setCellFactory(tc -> makeCell(fontStyle));
        childNameCol.setCellFactory(tc -> makeMultilineCell(fontStyle));

        actionsCol.setCellFactory(tc -> new TableCell<>() {
            private final Button edit = new Button("Edit");
            private final Button del = new Button("Delete");

            {
                edit.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
                del.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");

                edit.setOnAction(e -> {
                    ParentRecord current = getTableView().getItems().get(getIndex());
                    CRUDDialogs.showParentDialog(current, false, ParentsPane.this::reload);
                });

                del.setOnAction(e -> {
                    ParentRecord current = getTableView().getItems().get(getIndex());
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                            "Delete parent record? This will NOT delete child records.",
                            ButtonType.YES, ButtonType.NO);
                    alert.setHeaderText("Confirm Delete");
                    alert.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            deleteParent(current);
                        }
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox actions = new HBox(5, edit, del);
                    setGraphic(actions);
                }
            }
        });

        table.getColumns().setAll(noCol, nameCol, relationshipCol, phoneCol, childNameCol, actionsCol);
    }

    private <T> TableCell<ParentRecord, T> makeCell(String style) {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setAlignment(Pos.CENTER);
                setTextAlignment(TextAlignment.CENTER);
                setStyle(style + " -fx-alignment: CENTER;");
            }
        };
    }

    private TableCell<ParentRecord, String> makeMultilineCell(String style) {
        return new TableCell<ParentRecord, String>() {
            private final Text text = new Text();

            {
                text.setStyle(style);
                text.setTextAlignment(TextAlignment.CENTER);
                text.wrappingWidthProperty().bind(widthProperty().subtract(12));
                setPrefHeight(Control.USE_COMPUTED_SIZE);
                setAlignment(Pos.CENTER);
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
                    setAlignment(Pos.CENTER);
                    setGraphic(text);
                }
            }
        };
    }

    public void reload() {
        data.clear();
        if (!UserSession.isLoggedIn()) return;

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                FirestoreRestClient client = FirestoreRest.forCurrentUser();
                List<FsDocument> docs = client.listDocuments("parents");
                ObservableList<ParentRecord> temp = FXCollections.observableArrayList();

                for (FsDocument doc : docs) {
                    String parentId = doc.getId();
                    String parentName = safeStr(doc.get("parentName"));
                    String phone = safeStr(doc.get("phone"));

                    String relationship = formatRelationship(doc);
                    int relationshipPriority = relationshipPriority(doc);

                    ChildrenAgg childrenAgg = extractChildrenAgg(doc);
                    String childId = childrenAgg.childIdsJoined;
                    String childName = childrenAgg.childNamesJoined;

                    String familyKey = firstLine(childName);
                    if (familyKey.isEmpty()) familyKey = firstLine(childId);

                    LocalDate passcodeExpiry = toLocalDate(doc.get("passcode_expiry"));

                    temp.add(new ParentRecord(parentId, parentName, phone, relationship, childId, childName, passcodeExpiry, familyKey, relationshipPriority));
                }

                // Group by family (first linked child), then relationship.
                FXCollections.sort(temp, (a, b) -> {
                    String ak = a.getFamilyKey() == null ? "" : a.getFamilyKey().trim().toLowerCase();
                    String bk = b.getFamilyKey() == null ? "" : b.getFamilyKey().trim().toLowerCase();
                    int c = ak.compareTo(bk);
                    if (c != 0) return c;

                    c = Integer.compare(a.getRelationshipPriority(), b.getRelationshipPriority());
                    if (c != 0) return c;

                    String an = a.getParentName() == null ? "" : a.getParentName().trim().toLowerCase();
                    String bn = b.getParentName() == null ? "" : b.getParentName().trim().toLowerCase();
                    c = an.compareTo(bn);
                    if (c != 0) return c;

                    String ap = a.getPhone() == null ? "" : a.getPhone().trim();
                    String bp = b.getPhone() == null ? "" : b.getPhone().trim();
                    return ap.compareTo(bp);
                });

                Platform.runLater(() -> data.setAll(temp));
            } catch (Exception e) {
                logError("failed to load parents", e);
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Failed to load parents: " + e.getMessage()).showAndWait());
            }
        });
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isEmpty()) return "";
        int idx = t.indexOf('\n');
        return idx >= 0 ? t.substring(0, idx).trim() : t;
    }

    private static int relationshipPriority(FsDocument parentDoc) {
        if (parentDoc == null) return 2;
        String type = safeStr(parentDoc.get("relationshipType")).trim().toLowerCase();
        if ("mother".equals(type)) return 0;
        if ("father".equals(type)) return 1;
        return 2;
    }

    private void deleteParent(ParentRecord parent) {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            client.deleteDocument("parents", parent.getParentId());
            reload();
        } catch (Exception ex) {
            logError("failed to delete parent", ex);
            new Alert(Alert.AlertType.ERROR, "Failed to delete parent: " + ex.getMessage()).showAndWait();
        }
    }

    private static String safeStr(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static LocalDate toLocalDate(Object o) {
        if (o == null) return null;
        try {
            if (o instanceof java.util.Date) {
                java.util.Date d = (java.util.Date) o;
                return d.toInstant().atZone(ZoneId.of("Asia/Kuala_Lumpur")).toLocalDate();
            }
            // If someone stored yyyy-MM-dd as string
            String s = String.valueOf(o).trim();
            if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(s);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static ChildrenAgg extractChildrenAgg(FsDocument parentDoc) {
        if (parentDoc == null) return new ChildrenAgg("", "");

        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();

        Object idsRaw = parentDoc.get("childIds");
        Object namesRaw = parentDoc.get("childNames");
        Object refsRaw = parentDoc.get("childRefs");
        if (refsRaw == null) refsRaw = parentDoc.get("childrenRefs");

        if (idsRaw instanceof List) {
            List list = (List) idsRaw;
            for (Object o : list) {
                if (o == null) continue;
                String s = String.valueOf(o).trim();
                if (!s.isEmpty()) ids.add(s);
            }
        }

        if (namesRaw instanceof List) {
            List list = (List) namesRaw;
            for (Object o : list) {
                if (o == null) continue;
                String s = String.valueOf(o).trim();
                if (!s.isEmpty()) names.add(s);
            }
        }

        // If no ids, derive from refs
        if (ids.isEmpty() && refsRaw instanceof List) {
            List list = (List) refsRaw;
            for (Object o : list) {
                String id = extractChildIdFromRef(o);
                if (id != null && !id.trim().isEmpty()) ids.add(id.trim());
            }
        }

        // Legacy fallback
        if (ids.isEmpty()) {
            String legacyId = safeStr(parentDoc.get("childId")).trim();
            if (!legacyId.isEmpty()) ids.add(legacyId);
        }
        if (names.isEmpty()) {
            String legacyName = safeStr(parentDoc.get("childName")).trim();
            if (!legacyName.isEmpty()) names.add(legacyName);
        }

        // Pair by index so IDs and Names stay aligned.
        List<ChildPair> pairs = new ArrayList<>();
        int n = Math.max(ids.size(), names.size());
        for (int i = 0; i < n; i++) {
            String id = (i < ids.size()) ? safeStr(ids.get(i)).trim() : "";
            String name = (i < names.size()) ? safeStr(names.get(i)).trim() : "";
            if (id.isEmpty()) continue;
            if (name.isEmpty()) name = id;
            pairs.add(new ChildPair(id, name));
        }

        // Dedupe by childId while preserving first occurrence.
        List<ChildPair> deduped = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (ChildPair p : pairs) {
            if (seenIds.add(p.id)) deduped.add(p);
        }

        List<String> idLines = new ArrayList<>();
        List<String> nameLines = new ArrayList<>();
        for (ChildPair p : deduped) {
            idLines.add(p.id);
            nameLines.add(p.name);
        }

        return new ChildrenAgg(String.join("\n", idLines), String.join("\n", nameLines));
    }

    private static class ChildPair {
        final String id;
        final String name;

        ChildPair(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    // (kept for backward compatibility; no longer used)
    private static List<String> dedupe(List<String> in) {
        if (in == null || in.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String s : in) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isEmpty()) continue;
            if (seen.add(t)) out.add(t);
        }
        return out;
    }

    private static String extractChildIdFromRef(Object raw) {
        if (raw == null) return null;
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

    private static String formatRelationship(FsDocument parentDoc) {
        if (parentDoc == null) return "";
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

    private static class ChildrenAgg {
        final String childIdsJoined;
        final String childNamesJoined;

        ChildrenAgg(String childIdsJoined, String childNamesJoined) {
            this.childIdsJoined = childIdsJoined;
            this.childNamesJoined = childNamesJoined;
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
