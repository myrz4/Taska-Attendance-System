package nfc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;

@SuppressWarnings({"unused", "java:S1144"})
final class LoginHistorySupport {
    private static final Preferences PREFS = Preferences.userNodeForPackage(LoginView.class);
    private static final String KEY_LAST_USERNAME = "login.lastUsername";
    private static final String KEY_RECENT_USERNAMES = "login.recentUsernames";
    private static final int MAX_RECENT_USERNAMES = 6;

    static {
        java.util.function.Consumer<ComboBox<String>> keepApplySavedUsernames = LoginHistorySupport::applySavedUsernames;
        java.util.function.Consumer<String> keepRememberUsername = LoginHistorySupport::rememberUsername;
        java.util.Objects.requireNonNull(keepApplySavedUsernames);
        java.util.Objects.requireNonNull(keepRememberUsername);
    }

    private LoginHistorySupport() {
    }

    static void applySavedUsernames(ComboBox<String> usernameBox) {
        List<String> recent = recentUsernames();
        usernameBox.setItems(FXCollections.observableArrayList(recent));

        String lastUsername = sanitize(PREFS.get(KEY_LAST_USERNAME, ""));
        if (!lastUsername.isEmpty()) {
            if (!containsIgnoreCase(recent, lastUsername)) {
                usernameBox.getItems().add(0, lastUsername);
            }
            usernameBox.setValue(lastUsername);
            usernameBox.getEditor().setText(lastUsername);
        }
    }

    static void rememberUsername(String username) {
        String normalized = sanitize(username);
        if (normalized.isEmpty()) {
            return;
        }

        List<String> ordered = new ArrayList<>();
        ordered.add(normalized);
        for (String saved : recentUsernames()) {
            if (!saved.equalsIgnoreCase(normalized)) {
                ordered.add(saved);
            }
            if (ordered.size() >= MAX_RECENT_USERNAMES) {
                break;
            }
        }

        PREFS.put(KEY_LAST_USERNAME, normalized);
        PREFS.put(KEY_RECENT_USERNAMES, String.join("\n", ordered));
    }

    private static List<String> recentUsernames() {
        String serialized = PREFS.get(KEY_RECENT_USERNAMES, "");
        List<String> usernames = new ArrayList<>();
        List<String> lowered = new ArrayList<>();
        for (String part : serialized.split("\\R")) {
            String normalized = sanitize(part);
            if (normalized.isEmpty()) {
                continue;
            }

            String lower = normalized.toLowerCase(Locale.ROOT);
            if (lowered.contains(lower)) {
                continue;
            }

            usernames.add(normalized);
            lowered.add(lower);
            if (usernames.size() >= MAX_RECENT_USERNAMES) {
                break;
            }
        }
        return usernames;
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        for (String value : values) {
            if (value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}