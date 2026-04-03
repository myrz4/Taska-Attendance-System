package nfc;

import java.io.IOException;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

@SuppressWarnings("unused")
final class StaffDataSupport {
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
                doc.getString("username") != null ? doc.getString("username") : doc.getId(),
                doc.getString("password"),
                doc.getString("profilePicture"),
                doc.getString("name")
            ));
        }
        return admins;
    }
}