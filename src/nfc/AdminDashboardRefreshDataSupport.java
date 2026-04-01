package nfc;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AdminDashboardRefreshDataSupport {
    private AdminDashboardRefreshDataSupport() {
    }

    static DashboardRefreshSnapshot collectSnapshot(FirestoreRestClient client) throws IOException, InterruptedException {
        List<FsDocument> childDocs = client.listDocuments("children");
        List<FsDocument> parentDocs = client.listDocuments("parents");
        int totalChildren = 0;
        Map<Long, String> childIdToNfcUid = new HashMap<>();
        Map<String, String> nfcUidToName = new HashMap<>();
        Set<String> ageReviewParents = new HashSet<>();
        Set<String> overtimeReviewParents = new HashSet<>();
        int ageReviewInvoices = 0;
        int overtimeReviewInvoices = 0;
        for (FsDocument child : childDocs) {
            String migratedTo = child.getString("migratedToChildId");
            if (migratedTo != null && !migratedTo.isBlank()) {
                continue;
            }

            totalChildren++;

            Long childId = child.getLong("child_id");
            String uid = child.getString("nfc_uid");
            String name = child.getString("name");
            if (childId != null && uid != null && !uid.isBlank()) {
                childIdToNfcUid.put(childId, uid);
            }
            if (uid != null && name != null) {
                nfcUidToName.put(uid, name);
            }
        }

        for (FsDocument parent : parentDocs) {
            String parentId = parent.getId();
            if (parentId == null || parentId.isBlank()) {
                continue;
            }
            List<FsDocument> invoices = client.listSubcollectionDocuments("parents", parentId, "invoices");
            for (FsDocument invoice : invoices) {
                Object billingMeta = invoice.get("billingMeta");
                if (!(billingMeta instanceof Map<?, ?>)) {
                    continue;
                }
                Map<?, ?> billingMetaMap = (Map<?, ?>) billingMeta;

                Object reviewRaw = billingMetaMap.get("managementReviewRecommended");
                boolean managementReview = reviewRaw instanceof Boolean
                    ? (Boolean) reviewRaw
                    : "true".equalsIgnoreCase(String.valueOf(reviewRaw));
                if (!managementReview) {
                    continue;
                }

                Object ageRaw = billingMetaMap.get("ageOutOfPolicy");
                boolean ageReview = ageRaw instanceof Boolean
                    ? (Boolean) ageRaw
                    : "true".equalsIgnoreCase(String.valueOf(ageRaw));
                if (ageReview) {
                    ageReviewInvoices++;
                    ageReviewParents.add(parentId);
                } else {
                    overtimeReviewInvoices++;
                    overtimeReviewParents.add(parentId);
                }
            }
        }

        Date startOfDay = Date.from(java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
        List<FsDocument> attendanceDocs = client.queryWhereEqual("attendance", "date", startOfDay);

        Set<String> scannedChildren = new HashSet<>();
        List<Map.Entry<String, Date>> checkIns = new ArrayList<>();
        List<Map.Entry<String, Date>> checkOuts = new ArrayList<>();

        for (FsDocument attendance : attendanceDocs) {
            Date checkInTime = attendance.getDate("check_in_time");
            Date checkOutTime = attendance.getDate("check_out_time");

            Boolean presentFlag = attendance.getBoolean("isPresent");
            if (presentFlag == null) {
                presentFlag = attendance.getBoolean("is_present");
            }

            String childKey = attendance.getString("childId");
            if (childKey == null || childKey.isBlank()) {
                Long numericChildId = attendance.getLong("child_id");
                if (numericChildId != null) {
                    childKey = childIdToNfcUid.getOrDefault(numericChildId, String.valueOf(numericChildId));
                } else {
                    String id = attendance.getId();
                    if (id != null && id.contains("_")) {
                        childKey = id.substring(id.indexOf('_') + 1);
                    } else {
                        childKey = id;
                    }
                }
            }

            String name = attendance.getString("name");
            if ((name == null || name.isBlank()) && childKey != null) {
                name = nfcUidToName.getOrDefault(childKey, childKey);
            }

            boolean scanned = checkInTime != null || Boolean.TRUE.equals(presentFlag);
            if (scanned) {
                scannedChildren.add(childKey);
            }

            if (checkInTime != null) {
                checkIns.add(Map.entry(name, checkInTime));
            }
            if (checkOutTime != null) {
                checkOuts.add(Map.entry(name, checkOutTime));
            }
        }

        checkIns.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        checkOuts.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int presentCount = scannedChildren.size();
        int absentCount = Math.max(0, totalChildren - presentCount);
        int ageReviewFamilies = ageReviewParents.size();
        int overtimeReviewFamilies = overtimeReviewParents.size();

        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a");
        List<String> inLines = checkIns.stream()
            .map(entry -> "✅ " + entry.getKey() + " – " + timeFormat.format(entry.getValue()))
            .toList();
        List<String> outLines = checkOuts.stream()
            .map(entry -> "🏁 " + entry.getKey() + " – " + timeFormat.format(entry.getValue()))
            .toList();

        return new DashboardRefreshSnapshot(
            presentCount,
            absentCount,
            ageReviewInvoices,
            ageReviewFamilies,
            overtimeReviewInvoices,
            overtimeReviewFamilies,
            inLines,
            outLines
        );
    }

    static final class DashboardRefreshSnapshot {
        final int presentCount;
        final int absentCount;
        final int ageReviewInvoices;
        final int ageReviewFamilies;
        final int overtimeReviewInvoices;
        final int overtimeReviewFamilies;
        final List<String> inLines;
        final List<String> outLines;

        DashboardRefreshSnapshot(
            int presentCount,
            int absentCount,
            int ageReviewInvoices,
            int ageReviewFamilies,
            int overtimeReviewInvoices,
            int overtimeReviewFamilies,
            List<String> inLines,
            List<String> outLines
        ) {
            this.presentCount = presentCount;
            this.absentCount = absentCount;
            this.ageReviewInvoices = ageReviewInvoices;
            this.ageReviewFamilies = ageReviewFamilies;
            this.overtimeReviewInvoices = overtimeReviewInvoices;
            this.overtimeReviewFamilies = overtimeReviewFamilies;
            this.inLines = inLines == null ? List.of() : inLines;
            this.outLines = outLines == null ? List.of() : outLines;
        }
    }
}