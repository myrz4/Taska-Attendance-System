package nfc;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;

/**
 * UserSession
 * Handles Firebase-authenticated session info (no UI changes required).
 */
public class UserSession {
    private static String idToken;
    private static String uid;
    private static String email;
    
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
    }

    /**
     * Verify a Firebase ID token and populate session fields automatically.
     * Call this right after FirebaseAuth sign-in if you receive an ID token.
     */
    public static boolean verifyAndSet(String token) {
        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);
            idToken = token;
            uid = decoded.getUid();
            email = decoded.getEmail();
            return true;
        } catch (FirebaseAuthException e) {
            e.printStackTrace();
            clear();
            return false;
        }
    }

    /**
     * Refresh current user data from Firebase if UID is known.
     */
    public static void refreshFromFirebase() {
        if (uid == null) return;
        try {
            UserRecord user = FirebaseAuth.getInstance().getUser(uid);
            email = user.getEmail();
        } catch (FirebaseAuthException e) {
            e.printStackTrace();
        }
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
    public static String getToken() { return idToken; }
    public static boolean isLoggedIn() { return idToken != null; }

    public static void clear() {
        idToken = null;
        uid = null;
        email = null;
        username = null;
        name = null;
        profilePicture = null;
    }
}