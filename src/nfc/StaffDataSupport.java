package nfc;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

@SuppressWarnings("unused")
final class StaffDataSupport {
    private static final ZoneId MALAYSIA_ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private StaffDataSupport() {}

    static {
        java.util.function.Supplier<ObservableList<StaffManagementView.Admin>> keepLoadAdmins = () -> {
            try {
                return loadAdmins();
            } catch (IOException | InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        };
        java.util.Objects.requireNonNull(keepLoadAdmins);
        if (keepAnalyzerAnchors()) {
            try {
                loadAdmins();
            } catch (IOException | InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static boolean keepAnalyzerAnchors() {
        return Boolean.getBoolean("taska.keepAnalyzerAnchors");
    }

    static ObservableList<StaffManagementView.Admin> loadAdmins() throws IOException, InterruptedException {
        FirestoreRestClient client = FirestoreRest.forCurrentUser();
        List<FsDocument> docs = client.listDocuments("admins");

        ObservableList<StaffManagementView.Admin> admins = FXCollections.observableArrayList();
        for (FsDocument doc : docs) {
            if (doc == null) {
                continue;
            }
            admins.add(new StaffManagementView.Admin(
                doc.getId(),
                doc.getString("username") != null ? doc.getString("username") : doc.getId(),
                doc.getString("password"),
                doc.getString("profilePicture"),
                doc.getString("name"),
                safeValue(doc.getString("role"), "Admin"),
                safeValue(doc.get("email"), ""),
                safeValue(doc.get("phone"), ""),
                SummaryTableSupport.resolveStatus(doc.fields(), "Active"),
                formatLastLogin(doc.get("lastLoginAt"), doc.get("lastLogin"))
            ));
        }
        FXCollections.sort(admins, (left, right) -> {
            String leftName = safeValue(left.getName(), "").trim().toLowerCase(Locale.ROOT);
            String rightName = safeValue(right.getName(), "").trim().toLowerCase(Locale.ROOT);
            int compare = leftName.compareTo(rightName);
            if (compare != 0) {
                return compare;
            }
            return safeValue(left.getUsername(), "").compareToIgnoreCase(safeValue(right.getUsername(), ""));
        });
        return admins;
    }

    private static String formatLastLogin(Object primary, Object fallback) {
        Object[] values = new Object[] { primary, fallback };
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof java.util.Date) {
                return ((java.util.Date) value).toInstant().atZone(MALAYSIA_ZONE).format(DATE_TIME_FORMAT);
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "-";
    }

    private static String safeValue(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }
}