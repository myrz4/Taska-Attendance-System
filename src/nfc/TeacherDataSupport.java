package nfc;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;

final class TeacherDataSupport {
    private TeacherDataSupport() {}

    static ObservableList<Map<String, Object>> loadTeachers() throws IOException, InterruptedException {
        FirestoreRestClient client = FirestoreRest.forCurrentUser();
        List<FsDocument> docs = client.listDocuments("teachers");
        ObservableList<Map<String, Object>> rows = FXCollections.observableArrayList();

        for (FsDocument doc : docs) {
            if (doc == null) {
                continue;
            }
            Map<String, Object> data = doc.fields();
            if (data == null) {
                continue;
            }

            String teacherId = doc.getId();
            if (isEmptyLegacyTeacher(teacherId, data)) {
                continue;
            }

            Map<String, Object> row = new HashMap<>(data);
            row.put("id", teacherId);
            rows.add(row);
        }

        FXCollections.sort(rows, (left, right) -> {
            String leftName = Objects.toString(left.get("name"), "").trim().toLowerCase(Locale.ROOT);
            String rightName = Objects.toString(right.get("name"), "").trim().toLowerCase(Locale.ROOT);
            int compare = leftName.compareTo(rightName);
            if (compare != 0) {
                return compare;
            }
            String leftUsername = Objects.toString(left.get("username"), "").trim().toLowerCase(Locale.ROOT);
            String rightUsername = Objects.toString(right.get("username"), "").trim().toLowerCase(Locale.ROOT);
            return leftUsername.compareTo(rightUsername);
        });

        return rows;
    }

    static boolean deleteTeacher(Map<String, Object> teacherData) {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            client.deleteDocument("teachers", Objects.toString(teacherData.get("id"), "").trim());
            return true;
        } catch (IOException | InterruptedException | IllegalStateException ex) {
            System.err.println("TeacherManagementView: failed to delete teacher - " + ex.getMessage());
            new Alert(Alert.AlertType.ERROR, "Failed to delete teacher").showAndWait();
            return false;
        }
    }

    private static boolean isEmptyLegacyTeacher(String teacherId, Map<String, Object> data) {
        if (teacherId == null || !teacherId.matches("^t\\d+$")) {
            return false;
        }
        String name = Objects.toString(data.get("name"), "").trim();
        String phone = Objects.toString(data.get("phone"), "").trim();
        String image = Objects.toString(data.get("image"), "").trim();
        return name.isEmpty() && phone.isEmpty() && image.isEmpty();
    }
}