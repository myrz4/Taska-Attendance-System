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

@SuppressWarnings("unused")
final class ChildrenDataSupport {
    private static final ZoneId MALAYSIA_ZONE = ZoneId.of("Asia/Kuala_Lumpur");

    private ChildrenDataSupport() {}

    @SuppressWarnings("unused")
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
            childFamilyKey.put(childId, parentDetails.familyKey());

            String nfcUid = safeStr(childDoc.get("nfc_uid")).trim();
            if (nfcUid.isEmpty()) {
                nfcUid = childId;
            }

            Map<String, Object> fields = childDoc.fields();
            LocalDate birthDate = parseBirthDate(childDoc.get("birthDate"));
            String childIdentifier = safeStr(childDoc.get("childIcNo")).trim();
            if (childIdentifier.isEmpty()) {
                childIdentifier = safeStr(childDoc.get("icNo")).trim();
            }
            if (childIdentifier.isEmpty()) {
                childIdentifier = childId;
            }

            rows.add(new ChildrenView.Child(
                childId,
                childDoc.getString("name"),
                childIdentifier,
                birthDate,
                ageSummary(birthDate),
                firstLine(parentDetails.parentName()),
                firstLine(parentDetails.parentContact()),
                CRUDChildDialogSupport.FeePlanType.fromChildData(fields).toString(),
                parseDueDay(childDoc.get("billingDueDay")),
                Boolean.TRUE.equals(childDoc.get("transportFromTadika")),
                nfcUid,
                SummaryTableSupport.resolveStatus(fields, "Active"),
                parentDetails.parentRelationship(),
                parentDetails.parentName(),
                parentDetails.parentContact()
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

    private static int parseDueDay(Object value) {
        if (value instanceof Number) {
            int day = ((Number) value).intValue();
            return day == 5 ? 5 : 7;
        }
        return 7;
    }

    private static String ageSummary(LocalDate birthDate) {
        if (birthDate == null) {
            return "-";
        }
        LocalDate today = LocalDate.now(MALAYSIA_ZONE);
        if (birthDate.isAfter(today)) {
            return "0m";
        }
        java.time.Period age = java.time.Period.between(birthDate, today);
        if (age.getYears() > 0) {
            return age.getMonths() > 0 ? age.getYears() + "y " + age.getMonths() + "m" : age.getYears() + "y";
        }
        return Math.max(0, age.getMonths()) + "m";
    }

    private static String firstLine(String value) {
        if (value == null) {
            return "-";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "-";
        }
        int index = trimmed.indexOf('\n');
        return index >= 0 ? trimmed.substring(0, index).trim() : trimmed;
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