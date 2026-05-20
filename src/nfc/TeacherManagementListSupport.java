package nfc;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

@SuppressWarnings("unused")
final class TeacherManagementListSupport {
    private TeacherManagementListSupport() {
    }

    @SuppressWarnings("unused")
    static void configureSearch(TextField searchTf, FilteredList<Map<String, Object>> filtered) {
        searchTf.textProperty().addListener((obs, oldValue, newValue) -> {
            final String query = (newValue == null ? "" : newValue.trim().toLowerCase());
            if (query.isEmpty()) {
                filtered.setPredicate(row -> true);
                return;
            }

            filtered.setPredicate(row -> {
                if (row == null) {
                    return false;
                }
                String name = Objects.toString(row.get("name"), "").toLowerCase();
                String email = Objects.toString(row.get("email"), "").toLowerCase();
                String phone = Objects.toString(row.get("phone"), "").toLowerCase();
                String personalIdentification = Objects.toString(row.get("personalIdentification"), "").toLowerCase();
                String gender = Objects.toString(row.get("gender"), "").toLowerCase();
                String nationality = Objects.toString(row.get("nationality"), "").toLowerCase();
                String homeAddress = addressSearchText(row);
                return name.contains(query)
                    || email.contains(query)
                    || phone.contains(query)
                    || personalIdentification.contains(query)
                    || gender.contains(query)
                    || nationality.contains(query)
                    || homeAddress.contains(query);
            });
        });
    }

    private static String addressSearchText(Map<String, Object> row) {
        String homeAddress = Objects.toString(row.get("homeAddress"), "").trim();
        if (!homeAddress.isEmpty()) {
            return homeAddress.toLowerCase();
        }
        String streetAddress = Objects.toString(row.get("streetAddress"), "").trim();
        String city = Objects.toString(row.get("city"), "").trim();
        String state = Objects.toString(row.get("state"), "").trim();
        String postcode = Objects.toString(row.get("postcode"), "").trim();
        return String.join(" ", streetAddress, city, state, postcode).trim().toLowerCase();
    }

    static void applyDefaultSort(
        TableView<Map<String, Object>> table,
        TableColumn<Map<String, Object>, String> nameCol
    ) {
        if (nameCol == null) {
            return;
        }
        nameCol.setSortType(TableColumn.SortType.ASCENDING);
        table.getSortOrder().setAll(Collections.<TableColumn<Map<String, Object>, ?>>singletonList(nameCol));
        table.sort();
    }

    @SuppressWarnings("unused")
    static void loadTeachersAsync(
        ObservableList<Map<String, Object>> master,
        TableView<Map<String, Object>> table,
        TableColumn<Map<String, Object>, String> nameCol,
        Consumer<String> logError
    ) {
        java.util.concurrent.CompletableFuture
            .supplyAsync(() -> {
                try {
                    return TeacherDataSupport.loadTeachers();
                } catch (java.io.IOException | InterruptedException | IllegalStateException e) {
                    throw new RuntimeException(e);
                }
            })
            .whenComplete((rows, err) -> Platform.runLater(() -> {
                if (err != null) {
                    logError.accept("TeacherManagementView: failed to load teachers - " + err.getMessage());
                    return;
                }

                master.setAll(rows);
                applyDefaultSort(table, nameCol);
            }));
    }
}