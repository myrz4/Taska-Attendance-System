package nfc;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

final class NFCAttendanceSupport {
    private static final Gson GSON = new Gson();

    private NFCAttendanceSupport() {
    }

    static AttendanceUpdateResult submitCheckIn(String rawUid, String actorName) throws IOException, InterruptedException {
        String normalizedUid = normalizeUid(rawUid);
        if (normalizedUid.isEmpty()) {
            return AttendanceUpdateResult.invalidUid();
        }

        FirestoreRestClient client = FirestoreRest.forCurrentUser();
        FsDocument childDoc = resolveActiveChildByNfcUid(client, normalizedUid);
        if (childDoc == null) {
            return AttendanceUpdateResult.unknownCard(normalizedUid);
        }

        String childId = childDoc.getId();
        String childName = childDoc.getString("name");

        Map<String, Object> payload = new HashMap<>();
        payload.put("childId", childId);
        payload.put("nfcUid", normalizedUid);
        payload.put("actorName", actorName);

        FirebaseFunctionsClient.CallResult result = FirebaseFunctionsClient.callAttendanceNfcCheckIn(
            FirestoreRest.projectId(),
            UserSession.getIdToken(),
            GSON.toJson(payload)
        );

        if (result.ok) {
            return AttendanceUpdateResult.checkedIn(normalizedUid, childId, childName);
        }

        String reason = result.reason == null ? "unknown-error" : result.reason;
        if ("attendance-already-open".equalsIgnoreCase(reason)) {
            return AttendanceUpdateResult.alreadyOpen(normalizedUid, childId, childName);
        }
        if ("attendance-already-closed".equalsIgnoreCase(reason)) {
            return AttendanceUpdateResult.alreadyClosed(normalizedUid, childId, childName);
        }
        return AttendanceUpdateResult.failed(normalizedUid, childId, childName, reason);
    }

    static String normalizeUid(String rawUid) {
        return rawUid == null ? "" : rawUid.trim().toUpperCase();
    }

    private static FsDocument resolveActiveChildByNfcUid(FirestoreRestClient client, String normalizedUid) throws IOException, InterruptedException {
        List<FsDocument> matches = client.queryWhereEqual("children", "nfc_uid", normalizedUid);
        if (matches == null || matches.isEmpty()) {
            return null;
        }

        for (FsDocument document : matches) {
            String migratedTo = document.getString("migratedToChildId");
            if (migratedTo == null || migratedTo.trim().isEmpty()) {
                return document;
            }
        }

        FsDocument legacy = matches.get(0);
        String migratedTo = legacy.getString("migratedToChildId");
        if (migratedTo != null && !migratedTo.trim().isEmpty()) {
            return client.getDocument("children", migratedTo.trim());
        }
        return null;
    }

    static final class AttendanceUpdateResult {
        enum Status {
            INVALID_UID,
            UNKNOWN_CARD,
            CHECKED_IN,
            ALREADY_OPEN,
            ALREADY_CLOSED,
            FAILED
        }

        final Status status;
        final String normalizedUid;
        final String childId;
        final String childName;
        final String reason;

        private AttendanceUpdateResult(Status status, String normalizedUid, String childId, String childName, String reason) {
            this.status = status;
            this.normalizedUid = normalizedUid == null ? "" : normalizedUid;
            this.childId = childId == null ? "" : childId;
            this.childName = childName == null ? "" : childName;
            this.reason = reason == null ? "" : reason;
        }

        static AttendanceUpdateResult invalidUid() {
            return new AttendanceUpdateResult(Status.INVALID_UID, "", "", "", "invalid-uid");
        }

        static AttendanceUpdateResult unknownCard(String normalizedUid) {
            return new AttendanceUpdateResult(Status.UNKNOWN_CARD, normalizedUid, "", "", "unknown-card");
        }

        static AttendanceUpdateResult checkedIn(String normalizedUid, String childId, String childName) {
            return new AttendanceUpdateResult(Status.CHECKED_IN, normalizedUid, childId, childName, "");
        }

        static AttendanceUpdateResult alreadyOpen(String normalizedUid, String childId, String childName) {
            return new AttendanceUpdateResult(Status.ALREADY_OPEN, normalizedUid, childId, childName, "attendance-already-open");
        }

        static AttendanceUpdateResult alreadyClosed(String normalizedUid, String childId, String childName) {
            return new AttendanceUpdateResult(Status.ALREADY_CLOSED, normalizedUid, childId, childName, "attendance-already-closed");
        }

        static AttendanceUpdateResult failed(String normalizedUid, String childId, String childName, String reason) {
            return new AttendanceUpdateResult(Status.FAILED, normalizedUid, childId, childName, reason);
        }
    }
}