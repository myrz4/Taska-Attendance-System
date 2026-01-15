package nfc;

public class HashGenerator {
    public static void main(String[] args) {
        String raw = "12345";  // change to whatever password you want
        String hash = PasswordUtil.hashPassword(raw);
        System.out.println("BCrypt hash for " + raw + " = " + hash);
    }
}