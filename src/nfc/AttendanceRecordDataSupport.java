package nfc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AttendanceRecordDataSupport {
    private static final DateTimeFormatter DB_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AttendanceRecordDataSupport() {
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
                nfcUidToChildDocId.put(uid, child.getId());
            }
        }

        Map<String, FsDocument> attendanceMap = new HashMap<>();
        for (FsDocument doc : attendanceDocs) {
            String childDocId = resolveAttendanceChildDocId(doc, childIdToNfcUid, childIdToChildDocId, nfcUidToChildDocId, allChildDocIds);
            if (childDocId != null && !childDocId.isBlank()) {
                attendanceMap.put(childDocId, doc);
            }
        }

        List<AttendanceRecord> records = new ArrayList<>();
        for (FsDocument child : children) {
            AttendanceRecord record = new AttendanceRecord(child.getId(), child.getString("name"), child.getString("nfc_uid"));
            applyAttendance(record, child.getString("name"), attendanceMap.get(child.getId()));
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
        String childDocId = attendanceDoc.getString("childId");

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

        if (childDocId != null && !childDocId.isBlank() && !allChildDocIds.contains(childDocId)) {
            String mapped = nfcUidToChildDocId.get(childDocId);
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

        record.nameProperty().set(childName);

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
}