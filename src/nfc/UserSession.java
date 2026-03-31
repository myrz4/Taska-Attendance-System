package nfc;

/**
 * UserSession
 * Handles Firebase-authenticated session info (no UI changes required).
 */
public class UserSession {
    private static String idToken;
    private static String uid;
    private static String email;
    private static String role;
    
    // --- UI session fields (Admin Dashboard) ---
    private static String username;
    private static String name;
    private static String profilePicture;

    public static void setAdmin(String u, String n, String pic) {
        username = u;
        name = n;
        profilePicture = pic;
    }

    public static String getName() {
        return name;
    }

    /**
     * Set session manually (used after successful login).
     */
    public static void set(String token, String uidVal, String emailVal) {
        idToken = token;
        uid = uidVal;
        email = emailVal;
        role = JwtUtils.extractStringClaim(token, "role");
    }

    // --- Getters (UI uses these exactly the same way) ---
    // --- Admin UI helpers ---
    public static void setUsername(String u) {
        username = u;
    }

    public static String getUsername() {
        return username;
    }

    public static void setProfilePicture(String pic) {
        profilePicture = pic;
    }

    public static String getProfilePicture() {
        return profilePicture;
    }

    public static String getIdToken() { return idToken; }
    public static String getUid() { return uid; }
    public static String getEmail() { return email; }
    public static String getRole() { return role; }
    public static String getToken() { return idToken; }
    public static boolean isLoggedIn() { return idToken != null; }

    public static boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }

    public static boolean isTeacher() {
        return "teacher".equalsIgnoreCase(role);
    }

    public static void clear() {
        idToken = null;
        uid = null;
        email = null;
        role = null;
        username = null;
        name = null;
        profilePicture = null;
    }
}