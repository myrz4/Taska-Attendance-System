package nfc;

/**
 * Phone normalization helpers.
 *
 * Goal: Keep Firestore documents consistent with Firebase Phone Auth, which uses E.164 (e.g. +6011...).
 * We store phones in local MY display format (e.g. 011...) plus a tail (e.g. 111...) for rules matching.
 */
public final class PhoneUtil {
    private PhoneUtil() {
    }

    /** Returns digits-only string (removes +, spaces, dashes, etc.). */
    public static String digitsOnly(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Returns Malaysia phone in local format starting with 0 when possible (e.g. 01112345678).
     * If it cannot confidently normalize, it returns digits-only as-is.
     */
    public static String toLocalMy(String phoneAny) {
        String d = digitsOnly(phoneAny);
        if (d.isBlank()) return "";

        // E.164 without plus: 60XXXXXXXXXX
        if (d.startsWith("60") && d.length() >= 10) {
            String rest = d.substring(2);
            if (!rest.startsWith("0")) rest = "0" + rest;
            return rest;
        }

        // Already local
        if (d.startsWith("0") && d.length() >= 9) {
            return d;
        }

        // Likely missing leading 0 (e.g. auth tail 1112577356)
        if (!d.startsWith("0") && d.length() >= 8) {
            return "0" + d;
        }

        return d;
    }

    /**
     * Returns a comparable tail used for auth/doc matching.
     * Examples:
     * - +601112345678 -> 1112345678
     * - 01112345678 -> 1112345678
     */
    public static String myTail(String phoneAny) {
        String d = digitsOnly(phoneAny);
        if (d.isBlank()) return "";
        if (d.startsWith("60") && d.length() > 2) d = d.substring(2);
        if (d.startsWith("0") && d.length() > 1) d = d.substring(1);
        return d;
    }

    /**
     * Returns Malaysia phone in E.164 format, e.g. +601112345678.
     * Best-effort; returns empty string if it cannot normalize.
     */
    public static String toE164My(String phoneAny) {
        String raw = phoneAny == null ? "" : phoneAny.trim();
        if (raw.isEmpty()) return "";

        if (raw.startsWith("+")) {
            String d = digitsOnly(raw);
            return d.length() >= 10 ? ("+" + d) : "";
        }

        String d = digitsOnly(raw);
        if (d.isEmpty()) return "";
        if (d.startsWith("60")) return d.length() >= 11 ? ("+" + d) : "";
        if (d.startsWith("0")) {
            String rest = d.substring(1);
            return rest.length() >= 9 ? ("+60" + rest) : "";
        }
        if (d.startsWith("1")) return d.length() >= 9 ? ("+60" + d) : "";
        return "";
    }
}
