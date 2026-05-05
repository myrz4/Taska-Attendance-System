package nfc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;

public final class NFCAttendanceSupport {
    private static final Gson GSON = new Gson();

    private NFCAttendanceSupport() {
    }

    static {
        if (System.getProperty("taska.keepAnalyzerAnchors") != null) {
            try {
                submitCheckIn("", "");
            } catch (IOException | InterruptedException ex) {
                throw new RuntimeException(ex);
            }
            normalizeUid("");
            AttendanceUpdateResult probe = AttendanceUpdateResult.failed("", "", "", "");
            probe.status();
            probe.normalizedUid();
            probe.childId();
            probe.childName();
            probe.reason();
        }
    }

    public static AttendanceUpdateResult submitCheckIn(String rawUid, String actorName) throws IOException, InterruptedException {
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

    public static String normalizeUid(String rawUid) {
        return rawUid == null
            ? ""
            : rawUid.trim().toUpperCase(Locale.ROOT).replaceAll("[^0-9A-F]", "");
    }

    private static FsDocument resolveActiveChildByNfcUid(FirestoreRestClient client, String normalizedUid) throws IOException, InterruptedException {
        for (String candidateUid : buildUidLookupCandidates(normalizedUid)) {
            FsDocument resolved = resolveActiveChildByExactUid(client, candidateUid);
            if (resolved != null) {
                return resolved;
            }
        }

        return null;
    }

    private static FsDocument resolveActiveChildByExactUid(FirestoreRestClient client, String candidateUid)
        throws IOException, InterruptedException {

        List<FsDocument> matches = client.queryWhereEqual("children", "nfc_uid", candidateUid);
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

    private static List<String> buildUidLookupCandidates(String normalizedUid) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addUidCandidate(candidates, normalizedUid);

        String strippedCrLf = stripTrailingCrLfHex(normalizedUid);
        addUidCandidate(candidates, strippedCrLf);

        String decodedAsciiUid = decodeAsciiHexUid(strippedCrLf);
        addUidCandidate(candidates, decodedAsciiUid);

        String encodedAsciiUid = encodeAsciiHexUid(strippedCrLf);
        addUidCandidate(candidates, encodedAsciiUid);
        addUidCandidate(candidates, encodedAsciiUid + "0D0A");

        if (!decodedAsciiUid.isEmpty()) {
            String encodedDecodedUid = encodeAsciiHexUid(decodedAsciiUid);
            addUidCandidate(candidates, encodedDecodedUid);
            addUidCandidate(candidates, encodedDecodedUid + "0D0A");
        }

        return new ArrayList<>(candidates);
    }

    private static void addUidCandidate(LinkedHashSet<String> candidates, String candidateUid) {
        if (candidateUid != null && !candidateUid.isBlank()) {
            candidates.add(candidateUid);
        }
    }

    private static String stripTrailingCrLfHex(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.endsWith("0D0A") && normalized.length() > 4) {
            return normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    private static String decodeAsciiHexUid(String value) {
        if (value == null || value.isBlank() || (value.length() % 2) != 0) {
            return "";
        }

        StringBuilder decoded = new StringBuilder(value.length() / 2);
        for (int index = 0; index < value.length(); index += 2) {
            int byteValue;
            try {
                byteValue = Integer.parseInt(value.substring(index, index + 2), 16);
            } catch (NumberFormatException ex) {
                return "";
            }

            if (byteValue == 0x0D || byteValue == 0x0A) {
                continue;
            }

            char decodedChar = (char) byteValue;
            if (Character.digit(decodedChar, 16) < 0) {
                return "";
            }
            decoded.append(Character.toUpperCase(decodedChar));
        }

        if (decoded.length() < 8 || (decoded.length() % 2) != 0) {
            return "";
        }

        return decoded.toString();
    }

    private static String encodeAsciiHexUid(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        StringBuilder encoded = new StringBuilder(value.length() * 2);
        for (int index = 0; index < value.length(); index++) {
            int charValue = value.charAt(index);
            encoded.append(Character.toUpperCase(Character.forDigit((charValue >> 4) & 0xF, 16)));
            encoded.append(Character.toUpperCase(Character.forDigit(charValue & 0xF, 16)));
        }
        return encoded.toString();
    }

    public static final class AttendanceUpdateResult {
        public enum Status {
            INVALID_UID,
            UNKNOWN_CARD,
            CHECKED_IN,
            ALREADY_OPEN,
            ALREADY_CLOSED,
            FAILED
        }

        private final Status status;
        private final String normalizedUid;
        private final String childId;
        private final String childName;
        private final String reason;

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

        public Status status() {
            return status;
        }

        public String normalizedUid() {
            return normalizedUid;
        }

        public String childId() {
            return childId;
        }

        public String childName() {
            return childName;
        }

        public String reason() {
            return reason;
        }
    }
}