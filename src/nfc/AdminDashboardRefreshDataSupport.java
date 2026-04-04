package nfc;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.ZoneId;

@SuppressWarnings({"java:S1144", "java:S1068"})
final class AdminDashboardRefreshDataSupport {
    private AdminDashboardRefreshDataSupport() {
    }

    static {
        java.util.function.Consumer<FirestoreRestClient> keepCollect = client -> {
            try {
                collectSnapshot(client);
            } catch (IOException | InterruptedException error) {
                throw new IllegalStateException(error);
            }
        };
        DashboardRefreshSnapshot probe = new DashboardRefreshSnapshot(0, 0, 0, 0, 0, 0, null, null);
        java.util.Objects.requireNonNull(keepCollect);
        java.util.Objects.hash(
            probe.presentCount
                + probe.absentCount
                + probe.ageReviewInvoices
                + probe.ageReviewFamilies
                + probe.overtimeReviewInvoices
                + probe.overtimeReviewFamilies
        );
        java.util.Objects.requireNonNull(probe.inLines);
        java.util.Objects.requireNonNull(probe.outLines);
    }

    @SuppressWarnings("java:S1144")
    static DashboardRefreshSnapshot collectSnapshot(FirestoreRestClient client) throws IOException, InterruptedException {
        List<FsDocument> childDocs = client.listDocuments("children");
        List<FsDocument> parentDocs = client.listDocuments("parents");
        int totalChildren = 0;
        Map<Long, String> childIdToNfcUid = new HashMap<>();
        Map<Long, String> childIdToChildDocId = new HashMap<>();
        Map<String, String> childDocIdToName = new HashMap<>();
        Map<String, String> nfcUidToName = new HashMap<>();
        Map<String, String> nfcUidToChildDocId = new HashMap<>();
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
            String childDocId = child.getId();
            String uid = child.getString("nfc_uid");
            String name = firstNonBlank(child.getString("name"), child.getString("childName"));
            if (childId != null && childDocId != null && !childDocId.isBlank()) {
                childIdToChildDocId.put(childId, childDocId);
            }
            if (childDocId != null && !childDocId.isBlank() && name != null && !name.isBlank()) {
                childDocIdToName.put(childDocId, name);
            }
            if (childId != null && uid != null && !uid.isBlank()) {
                childIdToNfcUid.put(childId, uid);
            }
            if (uid != null && !uid.isBlank() && childDocId != null && !childDocId.isBlank()) {
                putUidMapping(nfcUidToChildDocId, uid, childDocId);
            }
            if (uid != null && !uid.isBlank() && name != null && !name.isBlank()) {
                putUidMapping(nfcUidToName, uid, name);
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

        LocalDate today = LocalDate.now();
        List<FsDocument> attendanceDocs = loadTodayAttendanceDocs(client, today);

        Map<String, FsDocument> effectiveDocsByChild = new LinkedHashMap<>();
        List<Map.Entry<String, Date>> checkIns = new ArrayList<>();
        List<Map.Entry<String, Date>> checkOuts = new ArrayList<>();

        for (FsDocument attendance : attendanceDocs) {
            String childKey = resolveAttendanceChildKey(attendance, childIdToChildDocId, childIdToNfcUid, nfcUidToChildDocId);
            if (childKey == null || childKey.isBlank()) {
                continue;
            }

            FsDocument current = effectiveDocsByChild.get(childKey);
            if (shouldPreferAttendanceDocument(current, attendance)) {
                effectiveDocsByChild.put(childKey, attendance);
            }
        }

        Set<String> scannedChildren = new HashSet<>();
        for (Map.Entry<String, FsDocument> entry : effectiveDocsByChild.entrySet()) {
            FsDocument attendance = entry.getValue();
            Date checkInTime = firstDate(attendance.getDate("checkInAt"), attendance.getDate("check_in_time"), attendance.getDate("checkInTime"));
            Date checkOutTime = firstDate(
                attendance.getDate("checkOutAt"),
                attendance.getDate("check_out_time"),
                attendance.getDate("checkOutTime"),
                attendance.getDate("checkoutTime")
            );

            Boolean presentFlag = attendance.getBoolean("isPresent");
            if (presentFlag == null) {
                presentFlag = attendance.getBoolean("is_present");
            }
            if (presentFlag == null) {
                String status = attendance.getString("status");
                if (status != null && ("CHECKED_IN".equalsIgnoreCase(status) || "CHECKED_OUT".equalsIgnoreCase(status))) {
                    presentFlag = true;
                }
            }

            String childKey = entry.getKey();
            String name = resolveAttendanceChildName(attendance, childKey, childDocIdToName, nfcUidToName, nfcUidToChildDocId);
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

    private static List<FsDocument> loadTodayAttendanceDocs(FirestoreRestClient client, LocalDate today)
        throws IOException, InterruptedException {

        FirestoreRestClient resolvedClient = java.util.Objects.requireNonNull(client, "client");
        LocalDate resolvedToday = java.util.Objects.requireNonNull(today, "today");

        LinkedHashMap<String, FsDocument> documentsById = new LinkedHashMap<>();
        String dateKey = resolvedToday.toString();
        Date startOfDay = Date.from(resolvedToday.atStartOfDay(ZoneId.systemDefault()).toInstant());

        mergeAttendanceDocs(documentsById, resolvedClient.queryWhereEqual("attendance", "dateKey", dateKey));
        mergeAttendanceDocs(documentsById, resolvedClient.queryWhereEqual("attendance", "date", startOfDay));
        mergeAttendanceDocs(documentsById, resolvedClient.queryWhereEqual("attendance", "date", dateKey));

        return new ArrayList<>(documentsById.values());
    }

    private static void mergeAttendanceDocs(Map<String, FsDocument> target, List<FsDocument> documents) {
        if (target == null || documents == null) {
            return;
        }
        for (FsDocument document : documents) {
            if (document == null) {
                continue;
            }
            String id = document.getId();
            if (id == null || id.isBlank()) {
                id = "attendance-" + target.size();
            }
            target.putIfAbsent(id, document);
        }
    }

    private static boolean shouldPreferAttendanceDocument(FsDocument current, FsDocument candidate) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }

        long currentEpoch = attendanceSortEpoch(current);
        long candidateEpoch = attendanceSortEpoch(candidate);
        if (candidateEpoch != currentEpoch) {
            return candidateEpoch > currentEpoch;
        }

        boolean currentCorrected = isAdminCorrected(current);
        boolean candidateCorrected = isAdminCorrected(candidate);
        if (candidateCorrected != currentCorrected) {
            return candidateCorrected;
        }

        boolean currentHasCheckOut = firstDate(current.getDate("checkOutAt"), current.getDate("check_out_time"), current.getDate("checkOutTime"), current.getDate("checkoutTime")) != null;
        boolean candidateHasCheckOut = firstDate(candidate.getDate("checkOutAt"), candidate.getDate("check_out_time"), candidate.getDate("checkOutTime"), candidate.getDate("checkoutTime")) != null;
        if (candidateHasCheckOut != currentHasCheckOut) {
            return candidateHasCheckOut;
        }

        boolean currentHasCheckIn = firstDate(current.getDate("checkInAt"), current.getDate("check_in_time"), current.getDate("checkInTime")) != null;
        boolean candidateHasCheckIn = firstDate(candidate.getDate("checkInAt"), candidate.getDate("check_in_time"), candidate.getDate("checkInTime")) != null;
        if (candidateHasCheckIn != currentHasCheckIn) {
            return candidateHasCheckIn;
        }

        String currentId = current.getId() == null ? "" : current.getId();
        String candidateId = candidate.getId() == null ? "" : candidate.getId();
        return candidateId.compareTo(currentId) > 0;
    }

    private static long attendanceSortEpoch(FsDocument doc) {
        if (doc == null) {
            return 0L;
        }
        Date best = firstDate(
            doc.getDate("updatedAt"),
            doc.getDate("checkOutAt"),
            doc.getDate("check_out_time"),
            doc.getDate("checkOutTime"),
            doc.getDate("checkoutTime"),
            doc.getDate("checkInAt"),
            doc.getDate("check_in_time"),
            doc.getDate("checkInTime"),
            doc.getDate("createdAt"),
            doc.getDate("date")
        );
        if (best != null) {
            return best.getTime();
        }

        String id = doc.getId();
        if (id != null && id.length() >= 10) {
            try {
                return java.sql.Date.valueOf(id.substring(0, 10)).getTime();
            } catch (IllegalArgumentException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static boolean isAdminCorrected(FsDocument doc) {
        return !firstNonBlank(doc.getString("manualEditReason"), doc.getString("manual_edit_reason")).isBlank();
    }

    private static String resolveAttendanceChildKey(
        FsDocument attendance,
        Map<Long, String> childIdToChildDocId,
        Map<Long, String> childIdToNfcUid,
        Map<String, String> nfcUidToChildDocId
    ) {
        if (attendance == null) {
            return "";
        }

        String childKey = firstNonBlank(
            extractChildIdFromRef(attendance.get("childRef")),
            extractChildIdFromRef(attendance.get("child_ref")),
            attendance.getString("childId"),
            attendance.getString("nfc_uid")
        );
        if (childKey == null || childKey.isBlank()) {
            Long numericChildId = attendance.getLong("child_id");
            if (numericChildId != null) {
                childKey = firstNonBlank(
                    childIdToChildDocId.get(numericChildId),
                    childIdToNfcUid.get(numericChildId),
                    String.valueOf(numericChildId)
                );
            }
        }

        if (childKey == null || childKey.isBlank()) {
            String id = attendance.getId();
            if (id != null && id.contains("_")) {
                childKey = id.substring(id.indexOf('_') + 1);
            } else {
                childKey = id;
            }
        }

        String resolvedChildDocId = lookupUidMapping(nfcUidToChildDocId, childKey);
        if (resolvedChildDocId != null && !resolvedChildDocId.isBlank()) {
            return resolvedChildDocId;
        }
        return childKey == null ? "" : childKey;
    }

    private static String resolveAttendanceChildName(
        FsDocument attendance,
        String childKey,
        Map<String, String> childDocIdToName,
        Map<String, String> nfcUidToName,
        Map<String, String> nfcUidToChildDocId
    ) {
        String name = attendance == null ? "" : firstNonBlank(
            attendance.getString("name"),
            attendance.getString("childName"),
            attendance.getString("child_name")
        );
        if (name != null && !name.isBlank()) {
            return name;
        }

        String resolvedChildDocId = firstNonBlank(
            attendance == null ? "" : extractChildIdFromRef(attendance.get("childRef")),
            attendance == null ? "" : extractChildIdFromRef(attendance.get("child_ref")),
            lookupUidMapping(nfcUidToChildDocId, childKey),
            childKey
        );
        return firstNonBlank(
            childDocIdToName.get(childKey),
            childDocIdToName.get(resolvedChildDocId),
            lookupUidMapping(nfcUidToName, childKey),
            childDocIdToName.get(resolvedChildDocId),
            childKey,
            attendance == null ? null : attendance.getId(),
            "Unknown"
        );
    }

    private static String extractChildIdFromRef(Object raw) {
        if (!(raw instanceof String)) {
            return "";
        }
        String value = ((String) raw).trim();
        if (value.isEmpty()) {
            return "";
        }

        String path = value.startsWith("/") ? value.substring(1) : value;
        int index = path.indexOf("children/");
        if (index < 0) {
            return "";
        }

        String tail = path.substring(index + "children/".length());
        int slash = tail.indexOf('/');
        return slash >= 0 ? tail.substring(0, slash).trim() : tail.trim();
    }

    private static void putUidMapping(Map<String, String> target, String uid, String value) {
        if (target == null) {
            return;
        }
        String rawKey = uid == null ? "" : uid.trim();
        String mappedValue = value == null ? "" : value.trim();
        if (rawKey.isBlank() || mappedValue.isBlank()) {
            return;
        }
        target.put(rawKey, mappedValue);

        String normalizedKey = normalizeLookupKey(rawKey);
        if (!normalizedKey.isBlank()) {
            target.put(normalizedKey, mappedValue);
        }
    }

    private static String lookupUidMapping(Map<String, String> target, String key) {
        if (target == null) {
            return "";
        }
        String rawKey = key == null ? "" : key.trim();
        if (rawKey.isBlank()) {
            return "";
        }

        String mapped = target.get(rawKey);
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
        }

        String normalizedKey = normalizeLookupKey(rawKey);
        mapped = target.get(normalizedKey);
        return mapped == null ? "" : mapped;
    }

    private static String normalizeLookupKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static Date firstDate(Date... candidates) {
        if (candidates == null) {
            return null;
        }
        for (Date candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
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