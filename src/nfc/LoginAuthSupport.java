package nfc;

import java.io.IOException;
import java.util.LinkedHashSet;

final class LoginAuthSupport {
    private LoginAuthSupport() {
    }

    static void authenticateAndBootstrapSession(String input, String password)
        throws FirebaseAuthClient.FirebaseAuthException, IOException {
        String normalizedInput = input == null ? "" : input.trim();
        boolean inputIsEmail = normalizedInput.contains("@");
        String[] candidates = inputIsEmail
            ? new String[] { normalizedInput.toLowerCase() }
            : buildTeacherEmailCandidates(normalizedInput);

        FirebaseAuthClient.FirebaseUser user = null;
        FirebaseAuthClient.FirebaseAuthException last = null;

        for (String email : candidates) {
            try {
                user = FirebaseAuthClient.signInWithEmailPassword(email, password);
                user.email = email;
                break;
            } catch (FirebaseAuthClient.FirebaseAuthException fae) {
                last = fae;
                if (!inputIsEmail) {
                    String code = fae.code == null ? "" : fae.code.toUpperCase();
                    if ("EMAIL_NOT_FOUND".equals(code)
                        || "INVALID_PASSWORD".equals(code)
                        || "INVALID_LOGIN_CREDENTIALS".equals(code)) {
                        continue;
                    }
                }
                throw fae;
            }
        }

        if (user == null) {
            if (!inputIsEmail && last != null && "EMAIL_NOT_FOUND".equalsIgnoreCase(last.code)) {
                String canonical = candidates.length > 0 ? candidates[0] : null;
                if (canonical == null || canonical.isBlank()) {
                    throw new IllegalArgumentException("Enter a valid phone number.");
                }
                FirebaseAuthClient.signUpWithEmailPassword(canonical, password);
                user = FirebaseAuthClient.signInWithEmailPassword(canonical, password);
                user.email = canonical;
            } else if (last != null) {
                throw last;
            } else {
                throw new IllegalStateException("Login failed.");
            }
        }

        String projectId = FirebaseConfig.readPropertyFromJarFiles("firebase.properties", "projectId");
        if (projectId != null && !projectId.isBlank()) {
            try {
                FirebaseFunctionsClient.callClaimTeacherRole(projectId.trim(), user.idToken);
                FirebaseAuthClient.FirebaseUser refreshed = FirebaseAuthClient.refreshIdToken(user.refreshToken);
                if (refreshed != null && refreshed.idToken != null && !refreshed.idToken.isBlank()) {
                    user.idToken = refreshed.idToken;
                    if (refreshed.refreshToken != null && !refreshed.refreshToken.isBlank()) {
                        user.refreshToken = refreshed.refreshToken;
                    }
                }
            } catch (IllegalStateException ignored) {
                // Best-effort only; role check below still protects access.
            }
        }

        UserSession.set(user.idToken, user.localId, user.email);
        if (!(UserSession.isAdmin() || UserSession.isTeacher())) {
            UserSession.clear();
            throw new IllegalStateException(
                "Not authorized (missing role claim). Please login in Teacher App once using OTP, then try again."
            );
        }

        if (UserSession.isTeacher() && !UserSession.isAdmin()) {
            TeacherProfileResolver.TeacherProfile prof = TeacherProfileResolver.resolve(user.email, normalizedInput);

            String usernameVal = (prof != null && prof.phone != null && !prof.phone.isBlank())
                ? prof.phone
                : (!normalizedInput.isBlank()
                    ? normalizedInput
                    : (user.email != null && !user.email.isBlank() ? user.email : normalizedInput));

            String nameVal = (prof != null && prof.name != null && !prof.name.isBlank())
                ? prof.name
                : usernameVal;

            String profilePicVal = (prof != null && prof.image != null && !prof.image.isBlank())
                ? prof.image
                : "default_user.png";

            UserSession.setAdmin(usernameVal, nameVal, profilePicVal);
        } else {
            String display = (user.email != null && !user.email.isBlank()) ? user.email : normalizedInput;
            UserSession.setAdmin(display, display, "logo.png");
        }
    }

    static String prettyAuthError(String code) {
        if (code == null || code.isBlank()) {
            return "Login failed.";
        }
        switch (code) {
            case "INVALID_PASSWORD":
                return "Wrong password.";
            case "EMAIL_NOT_FOUND":
                return "Account not found.";
            case "USER_DISABLED":
                return "This account is disabled.";
            case "TOO_MANY_ATTEMPTS_TRY_LATER":
                return "Too many attempts. Try again later.";
            case "INVALID_EMAIL":
                return "Invalid email.";
            case "WEAK_PASSWORD":
                return "Password is too weak.";
            default:
                return "Login failed: " + code;
        }
    }

    private static String digitsOnly(String input) {
        return input == null ? "" : input.replaceAll("[^0-9]", "");
    }

    private static String phoneLocalDigitsFromAny(String phoneAny) {
        String d = digitsOnly(phoneAny);
        if (d.isEmpty()) {
            return "";
        }
        if (d.startsWith("60") && d.length() > 2) {
            return "0" + d.substring(2);
        }
        if (d.startsWith("0")) {
            return d;
        }
        if (d.startsWith("1")) {
            return "0" + d;
        }
        return d;
    }

    private static String[] buildTeacherEmailCandidates(String phoneInput) {
        String rawDigits = digitsOnly(phoneInput);
        if (rawDigits.isEmpty()) {
            return new String[0];
        }

        String local = phoneLocalDigitsFromAny(phoneInput);
        String e164Digits = (local.startsWith("0") && local.length() > 1)
            ? "60" + local.substring(1)
            : rawDigits;

        LinkedHashSet<String> emails = new LinkedHashSet<>();
        if (!local.isBlank()) {
            emails.add("t_" + local + "@taskazurah.local");
        }
        if (!e164Digits.isBlank()) {
            emails.add("t_" + e164Digits + "@taskazurah.local");
        }
        if (!rawDigits.isBlank()) {
            emails.add("t_" + rawDigits + "@taskazurah.local");
        }
        return emails.toArray(new String[0]);
    }
}