package nfc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

@SuppressWarnings("unused")
final class CRUDParentDialogSupport {
    private CRUDParentDialogSupport() {
    }

    @SuppressWarnings("unused")
    static void showParentDialog(ParentsPane.ParentRecord existing, boolean isNew, Runnable onSave) {
        Dialog<ParentsPane.ParentRecord> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        AppThemeSupport.prepareDialog(
            dialog,
            isNew ? "Add New Parent" : "Edit Parent",
            "Link children, relationship details, and family notification settings.",
            AppThemeSupport.Tone.INFO
        );
        dialog.setResizable(true);

        FirestoreRestClient client;
        Map<String, Object> existingData = null;
        try {
            client = FirestoreRest.forCurrentUser();
            if (!isNew && existing != null) {
                FsDocument snap = client.getDocument("parents", existing.getParentId());
                existingData = snap == null ? null : snap.fields();
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppThemeSupport.showException(null, "Firestore REST Error", e);
            return;
        }

        String parentId = isNew ? newDocId() : (existing == null ? "" : existing.getParentId());
        if (parentId == null || parentId.isBlank()) {
            AppThemeSupport.showError(null, "Missing Parent ID", "Missing parent ID.");
            return;
        }

        TextField parentIdTf = new TextField(parentId);
        parentIdTf.setEditable(false);
        parentIdTf.setDisable(true);
        parentIdTf.setPromptText("(auto-generated)");

        TextField parentNameTf = new TextField();
        TextField phoneTf = new TextField();
        TextField parentIcTf = new TextField();
        parentIcTf.setPromptText("No. IC Parent / Penjaga");
        CheckBox parentIcVerifiedCb = new CheckBox("IC parent disahkan");

        ComboBox<RelationshipType> relationshipCb = new ComboBox<>();
        relationshipCb.getItems().setAll(RelationshipType.MOTHER, RelationshipType.FATHER, RelationshipType.GUARDIAN);
        relationshipCb.setMaxWidth(Double.MAX_VALUE);
        relationshipCb.setValue(RelationshipType.GUARDIAN);

        TextField relationshipLabelTf = new TextField();
        relationshipLabelTf.setPromptText("Custom (e.g., grandmother / uncle / brother / sister)");

        TextField childrenSummaryTf = new TextField();
        childrenSummaryTf.setEditable(false);
        childrenSummaryTf.setPromptText("Select one or more children");
        Button selectChildrenBtn = new Button("Select");
        AppThemeSupport.styleToolbarButtons(selectChildrenBtn);
        HBox childrenPickerBox = new HBox(10, childrenSummaryTf, selectChildrenBtn);
        HBox.setHgrow(childrenSummaryTf, Priority.ALWAYS);

        CheckBox notifActivityCb = new CheckBox("Activity");
        CheckBox notifAttendanceCb = new CheckBox("Attendance");
        CheckBox notifEmergencyCb = new CheckBox("Emergency");
        CheckBox notifFeesCb = new CheckBox("Billing");
        notifActivityCb.setSelected(false);
        notifAttendanceCb.setSelected(false);
        notifEmergencyCb.setSelected(false);
        notifFeesCb.setSelected(true);

        if (existingData != null) {
            relationshipCb.setValue(RelationshipType.fromFirestore(existingData.get("relationshipType")));
            relationshipLabelTf.setText(safeStr(existingData.get("relationshipLabel")).trim());
            parentIcTf.setText(safeStr(existingData.get("icNo")).trim());
            parentIcVerifiedCb.setSelected(Boolean.TRUE.equals(existingData.get("icVerified")));
        }

        relationshipLabelTf.disableProperty().bind(
            Bindings.createBooleanBinding(
                () -> relationshipCb.getValue() != RelationshipType.GUARDIAN,
                relationshipCb.valueProperty()
            )
        );

        if (existingData != null) {
            try {
                Object settingsObj = existingData.get("settings");
                if (settingsObj instanceof Map) {
                    Map<?, ?> settingsMap = (Map<?, ?>) settingsObj;
                    Object notifObj = settingsMap.get("notifications");
                    if (notifObj instanceof Map) {
                        Map<?, ?> notifMap = (Map<?, ?>) notifObj;
                        notifActivityCb.setSelected(Boolean.TRUE.equals(notifMap.get("activity")));
                        notifAttendanceCb.setSelected(Boolean.TRUE.equals(notifMap.get("attendance")));
                        notifEmergencyCb.setSelected(Boolean.TRUE.equals(notifMap.get("emergency")));
                        notifFeesCb.setSelected(Boolean.TRUE.equals(notifMap.get("fees")));
                    }
                }
            } catch (Exception ignore) {
            }
        }

        List<ChildOption> allChildren = new ArrayList<>();
        try {
            for (FsDocument doc : client.listDocuments("children")) {
                if (doc == null) continue;
                String id = doc.getId();
                String name = doc.getString("name");
                name = name == null ? "" : name.trim();
                if (name.isEmpty()) name = id;
                allChildren.add(new ChildOption(id, name));
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppThemeSupport.showException(null, "Firestore REST Error", e);
            return;
        }
        allChildren.sort(Comparator.comparing(ChildOption::getName, String.CASE_INSENSITIVE_ORDER));

        Set<String> initialChildIds = Collections.emptySet();
        if (existingData != null) {
            Object idsObj = existingData.get("childIds");
            if (idsObj instanceof List) {
                List<?> idList = (List<?>) idsObj;
                initialChildIds = idList.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toSet());
            } else {
                Object refsObj = existingData.get("childRefs");
                if (refsObj instanceof List) {
                    List<?> refList = (List<?>) refsObj;
                    initialChildIds = refList.stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .map(CRUDParentDialogSupport::extractChildIdFromRef)
                        .filter(s -> s != null && !s.trim().isEmpty())
                        .collect(Collectors.toSet());
                }
            }
        }
        if ((initialChildIds == null || initialChildIds.isEmpty()) && !isNew && existing != null) {
            String legacy = existing.getChildId();
            if (legacy != null && !legacy.trim().isEmpty()) {
                initialChildIds = Collections.singleton(legacy.trim());
            }
        }

        if (initialChildIds != null && !initialChildIds.isEmpty()) {
            Set<String> existingIds = allChildren.stream().map(ChildOption::getId).collect(Collectors.toSet());
            for (String id : initialChildIds) {
                if (id != null && !id.isBlank() && !existingIds.contains(id)) {
                    allChildren.add(new ChildOption(id.trim(), id.trim()));
                }
            }
            allChildren.sort(Comparator.comparing(ChildOption::getName, String.CASE_INSENSITIVE_ORDER));
        }

        CRUDParentChildPickerSupport.ChildPickerState childPickerState = CRUDParentChildPickerSupport.attachChildPicker(
            allChildren,
            initialChildIds,
            childrenSummaryTf,
            selectChildrenBtn
        );

        if (!isNew && existing != null) {
            parentNameTf.setText(existing.getParentName());
            phoneTf.setText(existing.getPhone());
        }

        AppThemeSupport.styleControls(
            parentNameTf,
            phoneTf,
            parentIcTf,
            relationshipCb,
            relationshipLabelTf,
            childrenSummaryTf
        );

        GridPane parentInfoGrid = createFormGrid();
        parentInfoGrid.addRow(0, new Label("Parent Name"), parentNameTf);
        parentInfoGrid.addRow(1, new Label("Phone"), phoneTf);
        parentInfoGrid.addRow(2, new Label("Parent IC"), parentIcTf);
        parentInfoGrid.add(parentIcVerifiedCb, 1, 3);

        GridPane relationshipGrid = createFormGrid();
        relationshipGrid.addRow(0, new Label("Relationship"), relationshipCb);
        relationshipGrid.addRow(1, new Label("Custom Relationship"), relationshipLabelTf);

        GridPane childrenGrid = createFormGrid();
        childrenGrid.addRow(0, new Label("Children"), childrenPickerBox);
        Label childHelper = new Label("Link one or more children to keep the family record and notification targets in sync.");
        childHelper.getStyleClass().add("app-helper-text");
        childHelper.setWrapText(true);
        childrenGrid.add(childHelper, 1, 1);

        HBox notificationsBox = new HBox(12, notifActivityCb, notifAttendanceCb, notifEmergencyCb, notifFeesCb);
        notificationsBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox content = AppThemeSupport.createDialogContent(
            AppThemeSupport.createFormSection(
                "Parent Info",
                "Keep the core parent record accurate for contact and verification workflows.",
                parentInfoGrid
            ),
            AppThemeSupport.createFormSection(
                "Relationship",
                "Use a guardian custom label only when the standard family roles are not enough.",
                relationshipGrid
            ),
            AppThemeSupport.createFormSection(
                "Children Linking",
                "Attach the children that belong to this parent record.",
                childrenGrid
            ),
            AppThemeSupport.createFormSection(
                "Notifications",
                "Choose which update categories this parent should receive.",
                notificationsBox
            )
        );

        ScrollPane scroll = AppThemeSupport.wrapDialogContent(content);
        scroll.setPannable(true);
        scroll.setPrefViewportWidth(520);
        scroll.setPrefViewportHeight(650);

        dialog.getDialogPane().setPrefSize(560, 720);
        dialog.getDialogPane().setContent(scroll);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        AppThemeSupport.styleDialogButtons(dialog, saveType);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.disableProperty().bind(
            Bindings.createBooleanBinding(
                () -> parentNameTf.getText().trim().isEmpty()
                    || phoneTf.getText().trim().isEmpty()
                    || !childPickerState.hasSelectedChildProperty().get(),
                parentNameTf.textProperty(),
                phoneTf.textProperty(),
                childPickerState.hasSelectedChildProperty()
            )
        );

        dialog.setResultConverter(btn -> {
            if (btn == saveType) {
                if (!childPickerState.hasSelectedChildProperty().get()) {
                    AppThemeSupport.showError(null, "Child Selection Required", "Please select at least one child.");
                    return null;
                }
                return new ParentsPane.ParentRecord(
                    parentId,
                    parentNameTf.getText().trim(),
                    phoneTf.getText().trim(),
                    "",
                    "",
                    "",
                    null,
                    "",
                    2
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(parent -> {
            try {
                Map<String, Object> document = new HashMap<>();
                document.put("parentName", parentNameTf.getText().trim());
                String phoneLocal = PhoneUtil.toLocalMy(phoneTf.getText());
                if (phoneLocal == null || phoneLocal.isBlank()) {
                    AppThemeSupport.showError(null, "Phone Required", "Phone cannot be empty.");
                    return;
                }
                document.put("phone", phoneLocal);
                document.put("phoneTail", PhoneUtil.myTail(phoneLocal));
                document.put("phoneE164", PhoneUtil.toE164My(phoneLocal));
                document.put("icNo", parentIcTf.getText() == null ? "" : parentIcTf.getText().trim());
                document.put("icVerified", parentIcVerifiedCb.isSelected());
                if (parentIcVerifiedCb.isSelected()) {
                    document.put("icVerifiedAt", new Date());
                }

                List<ChildOption> selectedChildren = childPickerState.selectedChildren();

                List<String> childIds = selectedChildren.stream().map(ChildOption::getId).collect(Collectors.toList());
                List<String> childNames = selectedChildren.stream().map(ChildOption::getName).collect(Collectors.toList());
                List<String> childRefs = selectedChildren.stream().map(ChildOption::getRef).collect(Collectors.toList());

                RelationshipType relationshipType = relationshipCb.getValue() == null ? RelationshipType.GUARDIAN : relationshipCb.getValue();
                String relationshipLabel = relationshipLabelTf.getText() == null ? "" : relationshipLabelTf.getText().trim();

                if (relationshipType == RelationshipType.MOTHER || relationshipType == RelationshipType.FATHER) {
                    List<FsDocument> allParents = client.listDocuments("parents");
                    String desiredType = relationshipType.firestoreValue;

                    Map<String, String> childIdToName = new HashMap<>();
                    for (int i = 0; i < Math.min(childIds.size(), childNames.size()); i++) {
                        childIdToName.put(childIds.get(i), childNames.get(i));
                    }

                    for (String childId : childIds) {
                        if (childId == null || childId.isBlank()) continue;

                        for (FsDocument other : allParents) {
                            if (other == null || other.getId() == null) continue;
                            if (other.getId().equals(parentId)) continue;

                            RelationshipType otherType = RelationshipType.fromFirestore(other.get("relationshipType"));
                            if (!desiredType.equalsIgnoreCase(otherType.firestoreValue)) continue;
                            if (!isParentLinkedToChild(other, childId)) continue;

                            String otherName = safeStr(other.get("parentName")).trim();
                            String otherPhone = safeStr(other.get("phone")).trim();
                            String childName = childIdToName.getOrDefault(childId, childId);

                            AppThemeSupport.showError(
                                null,
                                "Duplicate Relationship",
                                "Cannot save: \"" + childName + "\" already has a " + relationshipType.display.toLowerCase()
                                    + ": " + (otherName.isEmpty() ? other.getId() : otherName)
                                    + (otherPhone.isEmpty() ? "" : (" (" + otherPhone + ")"))
                            );
                            return;
                        }
                    }
                }

                document.put("childIds", childIds);
                document.put("childNames", childNames);
                document.put("childRefs", childRefs);
                document.put("relationshipType", relationshipType.firestoreValue);
                document.put("relationshipLabel", relationshipType == RelationshipType.GUARDIAN && !relationshipLabel.isEmpty() ? relationshipLabel : null);

                if (!selectedChildren.isEmpty()) {
                    ChildOption first = selectedChildren.get(0);
                    document.put("childId", first.getId());
                    document.put("childName", first.getName());
                    document.put("childRef", first.getRef());
                }

                Map<String, Object> notif = new HashMap<>();
                notif.put("activity", notifActivityCb.isSelected());
                notif.put("attendance", notifAttendanceCb.isSelected());
                notif.put("emergency", notifEmergencyCb.isSelected());
                notif.put("fees", notifFeesCb.isSelected());
                Map<String, Object> settings = new HashMap<>();
                settings.put("notifications", notif);
                document.put("settings", settings);

                if (isNew) {
                    document.put("timestamp", new Date());
                    client.createDocumentWithId("parents", parentId, document);
                } else {
                    client.patchDocumentMerge("parents", parentId, document);
                }

                refreshChildParentCacheAsync(client, childIds);
                onSave.run();
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                AppThemeSupport.showException(null, "Firestore REST Error", ex);
            }
        });
    }

    private static GridPane createFormGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.getStyleClass().add("app-form-grid");
        return grid;
    }

    private static void refreshChildParentCacheAsync(FirestoreRestClient client, List<String> childIds) {
        if (client == null || childIds == null || childIds.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            try {
                List<FsDocument> parents = client.listDocuments("parents");

                for (String childId : childIds) {
                    try {
                        String cid = childId == null ? "" : childId.trim();
                        if (cid.isEmpty()) continue;

                        Set<String> parentIds = new HashSet<>();
                        List<String> parentNames = new ArrayList<>();
                        List<String> parentPhones = new ArrayList<>();
                        List<String> parentPhoneTails = new ArrayList<>();
                        List<String> parentPhonesE164 = new ArrayList<>();

                        for (FsDocument parent : parents) {
                            if (parent == null || parent.getId() == null) continue;
                            boolean linked = false;
                            Object idsObj = parent.get("childIds");
                            if (idsObj instanceof List<?>) {
                                for (Object o : (List<?>) idsObj) {
                                    if (o != null && cid.equals(String.valueOf(o).trim())) {
                                        linked = true;
                                        break;
                                    }
                                }
                            }
                            if (!linked) {
                                String legacy = parent.getString("childId");
                                linked = legacy != null && cid.equals(legacy.trim());
                            }
                            if (!linked || !parentIds.add(parent.getId())) continue;

                            String parentName = safeStr(parent.get("parentName")).trim();
                            String parentPhone = safeStr(parent.get("phone")).trim();
                            if (!parentName.isEmpty()) parentNames.add(parentName);
                            if (!parentPhone.isEmpty()) {
                                parentPhones.add(parentPhone);
                                String tail = PhoneUtil.myTail(parentPhone);
                                if (tail != null && !tail.isBlank()) parentPhoneTails.add(tail);
                                String e164 = PhoneUtil.toE164My(parentPhone);
                                if (e164 != null && !e164.isBlank()) parentPhonesE164.add(e164);
                            }
                        }

                        Map<String, Object> patch = new HashMap<>();
                        patch.put("parentIds", new ArrayList<>(parentIds));
                        patch.put("parentNames", parentNames);
                        patch.put("parentPhones", parentPhones);
                        patch.put("parentPhoneTails", parentPhoneTails);
                        patch.put("parentPhonesE164", parentPhonesE164);
                        patch.put("parentName", String.join("\n", parentNames));
                        patch.put("parentContact", String.join("\n", parentPhones));
                        patch.put("parentCacheUpdatedAt", new Date());
                        patch.put("parentCacheSource", "parents.childIds");

                        client.patchDocumentMerge("children", cid, patch);
                    } catch (IOException | InterruptedException ex) {
                        if (ex instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        System.err.println("⚠ Failed to refresh child parent cache: " + ex.getMessage());
                    }
                }
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                System.err.println("⚠ Failed to refresh child parent cache batch: " + ex.getMessage());
            }
        });
    }

    private static boolean isParentLinkedToChild(FsDocument parentDoc, String childId) {
        if (parentDoc == null || childId == null || childId.trim().isEmpty()) return false;
        String cid = childId.trim();
        Object idsObj = parentDoc.get("childIds");
        if (idsObj instanceof List<?>) {
            for (Object o : (List<?>) idsObj) {
                if (o != null && cid.equals(String.valueOf(o).trim())) return true;
            }
        }
        String legacy = parentDoc.getString("childId");
        return legacy != null && cid.equals(legacy.trim());
    }

    private static String safeStr(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String newDocId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private static String extractChildIdFromRef(String ref) {
        if (ref == null) return null;
        String s = ref.trim();
        if (s.isEmpty()) return null;
        int idx = s.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < s.length()) {
            return s.substring(idx + 1).trim();
        }
        return s;
    }

    static enum RelationshipType {
        MOTHER("mother", "Mother"),
        FATHER("father", "Father"),
        GUARDIAN("guardian", "Guardian");

        final String firestoreValue;
        final String display;

        RelationshipType(String firestoreValue, String display) {
            this.firestoreValue = firestoreValue;
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }

        static RelationshipType fromFirestore(Object raw) {
            String s = raw == null ? "" : String.valueOf(raw).trim().toLowerCase();
            if ("mother".equals(s)) return MOTHER;
            if ("father".equals(s)) return FATHER;
            if ("guardian".equals(s)) return GUARDIAN;
            return GUARDIAN;
        }
    }

    static final class ChildOption {
        private final String id;
        private final String name;

        private ChildOption(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getRef() {
            return "/children/" + id;
        }

        @Override
        public String toString() {
            return name + " (" + id + ")";
        }
    }
}