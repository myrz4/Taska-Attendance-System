package nfc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import com.google.gson.Gson;

/**
 * Minimal JWT helper for extracting non-sensitive claims from a Firebase ID token.
 *
 * NOTE: This does NOT verify the token signature. Firestore Security Rules are the real enforcement.
 */
public final class JwtUtils {
    private static final Gson gson = new Gson();

    private JwtUtils() {
    }

    public static String extractStringClaim(String jwt, String claimName) {
        Object val = extractClaim(jwt, claimName);
        return val == null ? null : String.valueOf(val);
    }

    @SuppressWarnings("unchecked")
    public static Object extractClaim(String jwt, String claimName) {
        if (jwt == null || jwt.isBlank()) return null;
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) return null;

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            String json = new String(decoded, StandardCharsets.UTF_8);
            Map<String, Object> payload = gson.fromJson(json, Map.class);
            if (payload == null) return null;
            return payload.get(claimName);
        } catch (IllegalArgumentException | com.google.gson.JsonSyntaxException ignored) {
            return null;
        }
    }

    private static String padBase64(String s) {
        int mod = s.length() % 4;
        if (mod == 2) return s + "==";
        if (mod == 3) return s + "=";
        if (mod == 1) return s + "===";
        return s;
    }
}
