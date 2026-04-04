package nfc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AttendanceRecordDataSupport {
    private static final DateTimeFormatter DB_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AttendanceRecordDataSupport() {
    }

    static {
        java.util.function.BiFunction<List<FsDocument>, List<FsDocument>, List<AttendanceRecord>> keepBuild =
            AttendanceRecordDataSupport::buildRecords;
        java.util.Objects.requireNonNull(keepBuild);
    }

    static List<AttendanceRecord> buildRecords(List<FsDocument> children, List<FsDocument> attendanceDocs) {
        Map<Long, String> childIdToNfcUid = new HashMap<>();
        Map<Long, String> childIdToChildDocId = new HashMap<>();
        Map<String, String> nfcUidToChildDocId = new HashMap<>();
        Set<String> allChildDocIds = new HashSet<>();

        for (FsDocument child : children) {
            allChildDocIds.add(child.getId());
            Long numericId = child.getLong("child_id");
            String uid = child.getString("nfc_uid");
            if (numericId != null && uid != null && !uid.isBlank()) {
                childIdToNfcUid.put(numericId, uid);
            }
            if (numericId != null) {
                childIdToChildDocId.put(numericId, child.getId());
            }
            if (uid != null && !uid.isBlank()) {
                putUidMapping(nfcUidToChildDocId, uid, child.getId());
            }
        }

        Map<String, FsDocument> attendanceMap = new HashMap<>();
        for (FsDocument doc : attendanceDocs) {
            String childDocId = resolveAttendanceChildDocId(doc, childIdToNfcUid, childIdToChildDocId, nfcUidToChildDocId, allChildDocIds);
            if (childDocId != null && !childDocId.isBlank()) {
                FsDocument existing = attendanceMap.get(childDocId);
                if (shouldPreferAttendanceDocument(existing, doc)) {
                    attendanceMap.put(childDocId, doc);
                }
            }
        }

        List<AttendanceRecord> records = new ArrayList<>();
        for (FsDocument child : children) {
            String childName = firstNonBlank(child.getString("name"), child.getString("childName"));
            AttendanceRecord record = new AttendanceRecord(child.getId(), childName, child.getString("nfc_uid"));
            applyAttendance(record, childName, attendanceMap.get(child.getId()));
            records.add(record);
        }
        return records;
    }

    private static String resolveAttendanceChildDocId(
        FsDocument attendanceDoc,
        Map<Long, String> childIdToNfcUid,
        Map<Long, String> childIdToChildDocId,
        Map<String, String> nfcUidToChildDocId,
        Set<String> allChildDocIds
    ) {
        String childDocId = firstNonBlank(
            attendanceDoc.getString("childId"),
            extractChildIdFromRef(attendanceDoc.get("childRef")),
            extractChildIdFromRef(attendanceDoc.get("child_ref")),
            attendanceDoc.getString("nfc_uid")
        );

        if (childDocId == null || childDocId.isBlank()) {
            Long numericChildId = attendanceDoc.getLong("child_id");
            if (numericChildId != null) {
                childDocId = childIdToChildDocId.get(numericChildId);
                if (childDocId == null || childDocId.isBlank()) {
                    String legacyNfc = childIdToNfcUid.get(numericChildId);
                    if (legacyNfc != null && !legacyNfc.isBlank()) {
                        childDocId = nfcUidToChildDocId.get(legacyNfc);
                    }
                }
            }
        }

        if (childDocId == null || childDocId.isBlank()) {
            String attendanceId = attendanceDoc.getId();
            if (attendanceId != null && attendanceId.contains("_")) {
                childDocId = attendanceId.substring(attendanceId.indexOf('_') + 1).trim();
            }
        }

        if (childDocId != null && !childDocId.isBlank() && !allChildDocIds.contains(childDocId)) {
            String mapped = lookupUidMapping(nfcUidToChildDocId, childDocId);
            if (mapped != null && !mapped.isBlank()) {
                childDocId = mapped;
            }
        }
        return childDocId;
    }

    private static void applyAttendance(AttendanceRecord record, String childName, FsDocument attendanceDoc) {
        if (attendanceDoc == null) {
            return;
        }

        record.nameProperty().set(firstNonBlank(
            childName,
            attendanceDoc.getString("name"),
            attendanceDoc.getString("childName"),
            attendanceDoc.getString("child_name")
        ));

        Boolean presentFlag = attendanceDoc.getBoolean("isPresent");
        if (presentFlag == null) {
            presentFlag = attendanceDoc.getBoolean("is_present");
        }
        if (presentFlag == null) {
            presentFlag = attendanceDoc.getBoolean("isPresent");
        }
        record.setPresent(Boolean.TRUE.equals(presentFlag));

        Date checkIn = firstDate(attendanceDoc.getDate("checkInAt"), attendanceDoc.getDate("check_in_time"));
        Date checkOut = firstDate(attendanceDoc.getDate("checkOutAt"), attendanceDoc.getDate("check_out_time"));

        if (checkIn != null) {
            record.setCheckInFullTimestamp(LocalDateTime.ofInstant(checkIn.toInstant(), ZoneId.systemDefault()).format(DB_TIMESTAMP_FORMAT));
            record.setPresent(true);
        }

        if (checkOut != null) {
            record.setCheckOutFullTimestamp(LocalDateTime.ofInstant(checkOut.toInstant(), ZoneId.systemDefault()).format(DB_TIMESTAMP_FORMAT));
            record.setManualCheckOut(true);
        }

        String reason = attendanceDoc.getString("reason");
        record.setReason(reason != null ? reason : "Default");
        record.setCheckInMethod(firstNonBlank(attendanceDoc.getString("checkInMethod"), attendanceDoc.getString("checkin_method")));
        record.setCheckOutMethod(firstNonBlank(attendanceDoc.getString("checkOutMethod"), attendanceDoc.getString("checkout_method")));
        record.setManualEditReason(firstNonBlank(attendanceDoc.getString("manualEditReason"), attendanceDoc.getString("manual_edit_reason")));
        record.setUpdatedBy(firstNonBlank(
            extractNestedString(attendanceDoc.fields(), "auditMetadata", "lastActorName"),
            attendanceDoc.getString("checkedOutByName"),
            attendanceDoc.getString("checkedInByName")
        ));
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
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String extractNestedString(Map<String, Object> source, String parentKey, String childKey) {
        if (source == null || parentKey == null || childKey == null) {
            return "";
        }
        Object parent = source.get(parentKey);
        if (!(parent instanceof Map<?, ?>)) {
            return "";
        }
        Map<?, ?> nested = (Map<?, ?>) parent;
        Object value = nested.get(childKey);
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
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
}