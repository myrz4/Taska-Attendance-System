package nfc;

import java.io.IOException;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

final class StaffDataSupport {
    private StaffDataSupport() {}

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