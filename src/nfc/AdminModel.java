package nfc;

public class AdminModel {
    private static String username;
    private static String name;
    private static String profilePicture;

    // --- Username ---
    public static String getUsername() {
        return username;
    }

    public static void setUsername(String username) {
        AdminModel.username = username;
    }

    // --- Name ---
    public static String getName() {
        return name;
    }

    public static void setName(String name) {
        AdminModel.name = name;
    }

    // --- Profile Picture ---
    public static String getProfilePicture() {
        return profilePicture;
    }

    public static void setProfilePicture(String profilePicture) {
        AdminModel.profilePicture = profilePicture;
    }
}