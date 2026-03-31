package nfc;

/**
 * Provides a Firestore REST client bound to the current authenticated user.
 */
public final class FirestoreRest {
    private FirestoreRest() {
    }

    public static FirestoreRestClient forCurrentUser() {
        String token = UserSession.getIdToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Missing idToken. Please log in again.");
        }

        String projectId = projectId();
        return new FirestoreRestClient(projectId, token);
    }

    public static String projectId() {
        // 0) If we have an authenticated user, prefer the token's project id.
        // This prevents config mismatches where the Web API key points to project A
        // but firebase.properties projectId points to project B.
        try {
            String token = UserSession.getIdToken();
            if (token != null && !token.isBlank()) {
                String aud = JwtUtils.extractStringClaim(token, "aud");
                if (aud != null && !aud.isBlank()) return aud.trim();

                // Fallback: iss = https://securetoken.google.com/<projectId>
                String iss = JwtUtils.extractStringClaim(token, "iss");
                if (iss != null) {
                    int idx = iss.lastIndexOf('/');
                    if (idx >= 0 && idx + 1 < iss.length()) {
                        String fromIss = iss.substring(idx + 1).trim();
                        if (!fromIss.isBlank()) return fromIss;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 1) JVM property
        String p = System.getProperty("FIREBASE_PROJECT_ID");
        if (p != null && !p.isBlank()) return p.trim();

        // 2) Environment variable
        p = System.getenv("FIREBASE_PROJECT_ID");
        if (p != null && !p.isBlank()) return p.trim();

        // 3) jar_files/firebase.properties
        p = FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "projectId");
        if (p != null && !p.isBlank()) return p.trim();

        throw new IllegalStateException(
            "Missing Firebase projectId. Set FIREBASE_PROJECT_ID env/Java property, or add jar_files/firebase.properties with projectId=<id>."
        );
    }
}
