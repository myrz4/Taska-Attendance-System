package nfc;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordUtil {

    // use this when creating/updating a password
    public static String hashPassword(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(12));
    }

    // use this when verifying a login attempt
    public static boolean verifyPassword(String rawPassword, String storedHash) {
        return BCrypt.checkpw(rawPassword, storedHash);
    }
}