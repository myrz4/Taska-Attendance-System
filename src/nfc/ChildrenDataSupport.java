package nfc;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

final class ChildrenDataSupport {
    private static final ZoneId MALAYSIA_ZONE = ZoneId.of("Asia/Kuala_Lumpur");

    private ChildrenDataSupport() {}

    static ObservableList<ChildrenView.Child> loadChildren(FirestoreRestClient client) throws IOException, InterruptedException {
        List<FsDocument> childrenDocs = client.listDocuments("children");

        Map<String, String> nfcUidToChildId = new HashMap<>();
        Set<String> allChildIds = new HashSet<>();
        for (FsDocument childDoc : childrenDocs) {
            String childId = childDoc.getId();
            allChildIds.add(childId);
            String nfcUid = safeStr(childDoc.get("nfc_uid")).trim();
            if (!nfcUid.isEmpty()) {
                nfcUidToChildId.put(nfcUid, childId);
            }
        }

        Map<String, List<ChildrenParentLinkSupport.ParentLink>> childToParents = ChildrenParentLinkSupport.buildParentLinks(client, nfcUidToChildId, allChildIds);
        return buildRows(childrenDocs, childToParents);
    }

    private static ObservableList<ChildrenView.Child> buildRows(List<FsDocument> childrenDocs, Map<String, List<ChildrenParentLinkSupport.ParentLink>> childToParents) {
        ObservableList<ChildrenView.Child> rows = FXCollections.observableArrayList();
        Map<String, String> childFamilyKey = new HashMap<>();

        for (FsDocument childDoc : childrenDocs) {
            String migratedTo = safeStr(childDoc.get("migratedToChildId")).trim();
            if (!migratedTo.isEmpty()) {
                continue;
            }

            String childId = childDoc.getId();
            ChildrenParentLinkSupport.ParentDetails parentDetails = ChildrenParentLinkSupport.resolveParentDetails(childId, childDoc, childToParents);
            childFamilyKey.put(childId, parentDetails.familyKey);

            String nfcUid = safeStr(childDoc.get("nfc_uid")).trim();
            if (nfcUid.isEmpty()) {
                nfcUid = childId;
            }

            rows.add(new ChildrenView.Child(
                childId,
                childDoc.getString("name"),
                parseBirthDate(childDoc.get("birthDate")),
                parentDetails.parentName,
                parentDetails.parentRelationship,
                parentDetails.parentContact,
                nfcUid
            ));
        }

        FXCollections.sort(rows, (left, right) -> {
            String leftKey = childFamilyKey.get(left.getChildId());
            String rightKey = childFamilyKey.get(right.getChildId());
            leftKey = leftKey == null ? "" : leftKey.toLowerCase(Locale.ROOT);
            rightKey = rightKey == null ? "" : rightKey.toLowerCase(Locale.ROOT);
            int compare = leftKey.compareTo(rightKey);
            if (compare != 0) {
                return compare;
            }

            String leftName = left.getName() == null ? "" : left.getName().trim().toLowerCase(Locale.ROOT);
            String rightName = right.getName() == null ? "" : right.getName().trim().toLowerCase(Locale.ROOT);
            compare = leftName.compareTo(rightName);
            if (compare != 0) {
                return compare;
            }

            String leftId = left.getChildId() == null ? "" : left.getChildId();
            String rightId = right.getChildId() == null ? "" : right.getChildId();
            return leftId.compareTo(rightId);
        });
        return rows;
    }

    private static LocalDate parseBirthDate(Object birthDateValue) {
        if (birthDateValue == null) {
            return null;
        }
        try {
            if (birthDateValue instanceof java.util.Date) {
                return ((java.util.Date) birthDateValue).toInstant().atZone(MALAYSIA_ZONE).toLocalDate();
            }

            String raw = String.valueOf(birthDateValue).trim();
            if (raw.isEmpty()) {
                return null;
            }
            if (raw.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(raw);
            }

            SimpleDateFormat format = new SimpleDateFormat(
                "EEE MMM dd yyyy HH:mm:ss 'GMT'Z '(Malaysia Time)'",
                Locale.ENGLISH
            );
            return format.parse(raw).toInstant().atZone(MALAYSIA_ZONE).toLocalDate();
        } catch (ParseException ex) {
            System.out.println("⚠️ Could not parse birthDate: " + birthDateValue);
            return null;
        }
    }

    private static String safeStr(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

}