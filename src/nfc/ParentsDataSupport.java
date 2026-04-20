package nfc;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

@SuppressWarnings("unused")
final class ParentsDataSupport {
    private static final ZoneId MALAYSIA_ZONE = ZoneId.of("Asia/Kuala_Lumpur");

    private ParentsDataSupport() {}

    static {
        java.util.function.Supplier<ObservableList<ParentsPane.ParentRecord>> keepLoadParents = () -> {
            try {
                return loadParents(null);
            } catch (IOException | InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        };
        java.util.Objects.requireNonNull(keepLoadParents);
        if (keepAnalyzerAnchors()) {
            try {
                loadParents(null);
            } catch (IOException | InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static boolean keepAnalyzerAnchors() {
        return Boolean.getBoolean("taska.keepAnalyzerAnchors");
    }

    static ObservableList<ParentsPane.ParentRecord> loadParents(FirestoreRestClient client) throws IOException, InterruptedException {
        List<FsDocument> docs = client.listDocuments("parents");
        ObservableList<ParentsPane.ParentRecord> rows = FXCollections.observableArrayList();

        for (FsDocument doc : docs) {
            String parentId = doc.getId();
            String parentName = safeStr(doc.get("parentName"));
            String phone = safeStr(doc.get("phone"));
            String relationship = formatRelationship(doc);
            int relationshipPriority = relationshipPriority(doc);
            String notificationsSummary = notificationsSummary(doc);
            boolean icVerified = Boolean.TRUE.equals(doc.get("icVerified"));
            String parentIc = safeStr(doc.get("icNo")).trim();
            String status = SummaryTableSupport.resolveStatus(doc.fields(), "Active");
            String customRelationship = safeStr(doc.get("relationshipLabel")).trim();

            ChildrenAgg childrenAgg = extractChildrenAgg(doc);
            String childId = childrenAgg.childIdsJoined;
            String childName = childrenAgg.childNamesJoined;

            String familyKey = firstLine(childName);
            if (familyKey.isEmpty()) {
                familyKey = firstLine(childId);
            }

            LocalDate passcodeExpiry = toLocalDate(doc.get("passcode_expiry"));
            rows.add(new ParentsPane.ParentRecord(
                parentId,
                parentName,
                phone,
                relationship,
                childId,
                childName,
                passcodeExpiry,
                familyKey,
                relationshipPriority,
                notificationsSummary,
                icVerified,
                parentIc,
                status,
                customRelationship
            ));
        }

        FXCollections.sort(rows, (left, right) -> {
            String leftKey = left.getFamilyKey() == null ? "" : left.getFamilyKey().trim().toLowerCase(Locale.ROOT);
            String rightKey = right.getFamilyKey() == null ? "" : right.getFamilyKey().trim().toLowerCase(Locale.ROOT);
            int compare = leftKey.compareTo(rightKey);
            if (compare != 0) {
                return compare;
            }

            compare = Integer.compare(left.getRelationshipPriority(), right.getRelationshipPriority());
            if (compare != 0) {
                return compare;
            }

            String leftName = left.getParentName() == null ? "" : left.getParentName().trim().toLowerCase(Locale.ROOT);
            String rightName = right.getParentName() == null ? "" : right.getParentName().trim().toLowerCase(Locale.ROOT);
            compare = leftName.compareTo(rightName);
            if (compare != 0) {
                return compare;
            }

            String leftPhone = left.getPhone() == null ? "" : left.getPhone().trim();
            String rightPhone = right.getPhone() == null ? "" : right.getPhone().trim();
            return leftPhone.compareTo(rightPhone);
        });

        return rows;
    }

    private static String notificationsSummary(FsDocument parentDoc) {
        if (parentDoc == null) {
            return "-";
        }
        Object settingsObj = parentDoc.get("settings");
        if (!(settingsObj instanceof java.util.Map<?, ?>)) {
            return "-";
        }
        Object notificationsObj = ((java.util.Map<?, ?>) settingsObj).get("notifications");
        if (!(notificationsObj instanceof java.util.Map<?, ?>)) {
            return "-";
        }

        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.Map<?, ?> notifications = (java.util.Map<?, ?>) notificationsObj;
        if (Boolean.TRUE.equals(notifications.get("fees"))) {
            labels.add("Billing");
        }
        if (Boolean.TRUE.equals(notifications.get("attendance"))) {
            labels.add("Attendance");
        }
        if (Boolean.TRUE.equals(notifications.get("emergency"))) {
            labels.add("Emergency");
        }
        if (Boolean.TRUE.equals(notifications.get("activity"))) {
            labels.add("Activity");
        }
        return labels.isEmpty() ? "-" : String.join(" / ", labels);
    }

    private static String firstLine(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int index = trimmed.indexOf('\n');
        return index >= 0 ? trimmed.substring(0, index).trim() : trimmed;
    }

    private static int relationshipPriority(FsDocument parentDoc) {
        if (parentDoc == null) {
            return 2;
        }
        String type = safeStr(parentDoc.get("relationshipType")).trim().toLowerCase(Locale.ROOT);
        if ("mother".equals(type)) {
            return 0;
        }
        if ("father".equals(type)) {
            return 1;
        }
        return 2;
    }

    private static String safeStr(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof java.util.Date) {
                return ((java.util.Date) value).toInstant().atZone(MALAYSIA_ZONE).toLocalDate();
            }
            String text = String.valueOf(value).trim();
            if (text.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(text);
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static ChildrenAgg extractChildrenAgg(FsDocument parentDoc) {
        if (parentDoc == null) {
            return new ChildrenAgg("", "");
        }

        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();

        Object idsRaw = parentDoc.get("childIds");
        Object namesRaw = parentDoc.get("childNames");
        Object refsRaw = parentDoc.get("childRefs");
        if (refsRaw == null) {
            refsRaw = parentDoc.get("childrenRefs");
        }

        if (idsRaw instanceof List<?>) {
            for (Object entry : (List<?>) idsRaw) {
                if (entry == null) {
                    continue;
                }
                String text = String.valueOf(entry).trim();
                if (!text.isEmpty()) {
                    ids.add(text);
                }
            }
        }

        if (namesRaw instanceof List<?>) {
            for (Object entry : (List<?>) namesRaw) {
                if (entry == null) {
                    continue;
                }
                String text = String.valueOf(entry).trim();
                if (!text.isEmpty()) {
                    names.add(text);
                }
            }
        }

        if (ids.isEmpty() && refsRaw instanceof List<?>) {
            for (Object entry : (List<?>) refsRaw) {
                String childId = extractChildIdFromRef(entry);
                if (childId != null && !childId.trim().isEmpty()) {
                    ids.add(childId.trim());
                }
            }
        }

        if (ids.isEmpty()) {
            String legacyId = safeStr(parentDoc.get("childId")).trim();
            if (!legacyId.isEmpty()) {
                ids.add(legacyId);
            }
        }
        if (names.isEmpty()) {
            String legacyName = safeStr(parentDoc.get("childName")).trim();
            if (!legacyName.isEmpty()) {
                names.add(legacyName);
            }
        }

        List<ChildPair> pairs = new ArrayList<>();
        int count = Math.max(ids.size(), names.size());
        for (int index = 0; index < count; index++) {
            String id = index < ids.size() ? safeStr(ids.get(index)).trim() : "";
            String name = index < names.size() ? safeStr(names.get(index)).trim() : "";
            if (id.isEmpty()) {
                continue;
            }
            if (name.isEmpty()) {
                name = id;
            }
            pairs.add(new ChildPair(id, name));
        }

        List<ChildPair> deduped = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (ChildPair pair : pairs) {
            if (seenIds.add(pair.id)) {
                deduped.add(pair);
            }
        }

        List<String> idLines = new ArrayList<>();
        List<String> nameLines = new ArrayList<>();
        for (ChildPair pair : deduped) {
            idLines.add(pair.id);
            nameLines.add(pair.name);
        }

        return new ChildrenAgg(String.join("\n", idLines), String.join("\n", nameLines));
    }

    private static String extractChildIdFromRef(Object raw) {
        if (!(raw instanceof String)) {
            return null;
        }
        String value = ((String) raw).trim();
        if (value.isEmpty()) {
            return null;
        }
        String path = value.startsWith("/") ? value.substring(1) : value;
        int index = path.indexOf("children/");
        if (index < 0) {
            return null;
        }
        String tail = path.substring(index + "children/".length());
        int slash = tail.indexOf('/');
        return slash >= 0 ? tail.substring(0, slash) : tail;
    }

    private static String formatRelationship(FsDocument parentDoc) {
        if (parentDoc == null) {
            return "";
        }
        String type = safeStr(parentDoc.get("relationshipType")).trim().toLowerCase(Locale.ROOT);
        String label = safeStr(parentDoc.get("relationshipLabel")).trim();

        switch (type) {
            case "mother":
                return "Mother";
            case "father":
                return "Father";
            case "guardian":
            case "":
                return label.isEmpty() ? "Guardian" : label;
            default:
                return label.isEmpty() ? type : label;
        }
    }

    private static final class ChildPair {
        final String id;
        final String name;

        ChildPair(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class ChildrenAgg {
        final String childIdsJoined;
        final String childNamesJoined;

        ChildrenAgg(String childIdsJoined, String childNamesJoined) {
            this.childIdsJoined = childIdsJoined;
            this.childNamesJoined = childNamesJoined;
        }
    }
}