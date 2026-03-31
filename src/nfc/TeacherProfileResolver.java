package nfc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TeacherProfileResolver {

    private TeacherProfileResolver() {
    }

    public static TeacherProfile resolve(String authEmail, String loginInput) {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            List<FsDocument> docs = client.listDocuments("teachers");
            Set<String> phoneKeys = new LinkedHashSet<>();

            String fromDerived = digitsFromDerivedTeacherEmail(authEmail);
            if (fromDerived != null && !fromDerived.isBlank()) {
                phoneKeys.add(normalizePhone(fromDerived));
            }

            if (loginInput != null && !loginInput.isBlank()) {
                String inputDigits = loginInput.replaceAll("[^0-9]", "");
                if (!inputDigits.isBlank()) {
                    phoneKeys.add(normalizePhone(inputDigits));
                }
            }

            // Match ONLY by phone (normalized). We intentionally ignore email/username so that
            // teacher profile resolution remains stable even after those fields are removed.
            if (phoneKeys.isEmpty()) return null;
            for (FsDocument d : docs) {
                String docPhone = normalizePhone(asString(d.get("phone")));
                if (docPhone.isBlank()) continue;
                for (String k : phoneKeys) {
                    if (!k.isBlank() && Objects.equals(k, docPhone)) {
                        return fromDoc(d);
                    }
                }
            }

            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static TeacherProfile fromDoc(FsDocument d) {
        TeacherProfile p = new TeacherProfile();
        p.id = d.getId();
        p.name = asString(d.get("name"));
        p.image = asString(d.get("image"));
        p.phone = asString(d.get("phone"));
        return p;
    }

    private static String asString(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String digitsFromDerivedTeacherEmail(String email) {
        if (email == null) return null;
        String e = email.trim().toLowerCase();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^t_([0-9]+)@taskazurah\\.local$")
                .matcher(e);
        return m.matches() ? m.group(1) : null;
    }

    /**
     * Normalizes Malaysian phone representations to a comparable key.
     * Examples:
     * - 60112345678 -> 112345678
     * - 0112345678 -> 112345678
     */
    private static String normalizePhone(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("[^0-9]", "").trim();
        if (digits.isEmpty()) return "";

        if (digits.startsWith("60") && digits.length() > 2) {
            digits = digits.substring(2);
        }
        if (digits.startsWith("0") && digits.length() > 1) {
            digits = digits.substring(1);
        }

        return digits;
    }

    public static final class TeacherProfile {
        public String id;
        public String name;
        public String image;
        public String phone;
    }
}
