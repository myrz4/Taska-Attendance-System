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
                String username = Objects.toString(row.get("username"), "").toLowerCase();
                String email = Objects.toString(row.get("email"), "").toLowerCase();
                String phone = Objects.toString(row.get("phone"), "").toLowerCase();
                return name.contains(query) || username.contains(query) || email.contains(query) || phone.contains(query);
            });
        });
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