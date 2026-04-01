package nfc;

import java.util.Optional;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class TeacherManagementView extends javafx.scene.layout.VBox {

    private final javafx.scene.control.TableView<java.util.Map<String, Object>> table = new javafx.scene.control.TableView<>();
    private final ObservableList<java.util.Map<String, Object>> master = FXCollections.observableArrayList();
    private final javafx.collections.transformation.FilteredList<java.util.Map<String, Object>> filtered;
    private final javafx.collections.transformation.SortedList<java.util.Map<String, Object>> sorted;
    private final javafx.scene.control.TableColumn<java.util.Map<String, Object>, String> nameCol;
    private final javafx.scene.control.Button addTeacherButton;

    public TeacherManagementView() {

        // ===== HEADER BAR (COPIED FROM STAFF MANAGEMENT) =====
        javafx.scene.layout.HBox headerBar = new javafx.scene.layout.HBox(18);
        headerBar.setAlignment(Pos.CENTER_LEFT);
        headerBar.setPrefHeight(70);
        headerBar.setMaxWidth(Double.MAX_VALUE);
        headerBar.setStyle(
            "-fx-background-color: #2e8b57, #FECF4D;" +
            "-fx-background-insets: 0, 0 0 3 0;" +
            "-fx-background-radius: 0, 0;"
        );

        Label title = new Label("Teachers");
        title.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 44));
        title.setStyle("-fx-text-fill: #181818;");

        ImageView honeyPot = new ImageView(ImageLoader.loadSafe("hive2.png"));
        honeyPot.setFitWidth(54);
        honeyPot.setFitHeight(54);

        headerBar.getChildren().addAll(honeyPot, title);

        // ===== MAIN BODY =====
        javafx.scene.layout.VBox mainBody = new javafx.scene.layout.VBox(10);
        mainBody.setPadding(new Insets(20));
        mainBody.setAlignment(Pos.TOP_LEFT);

        TextField searchTf = new TextField();
        searchTf.setPromptText("Search name / username / email / phone...");
        searchTf.setMaxWidth(Double.MAX_VALUE);

        TeacherManagementTableSupport.TableBundle tableBundle = TeacherManagementTableSupport.setupTable(
            table,
            this::showTeacherDialog,
            this::confirmDeleteTeacher,
            () -> showTeacherDialog(null)
        );
        this.nameCol = tableBundle.nameCol;
        this.addTeacherButton = tableBundle.addTeacherButton;
        mainBody.getChildren().addAll(searchTf, table, addTeacherButton);

        // ===== WRAPPER LAYOUT (SAME AS ADMINS) =====
        javafx.scene.layout.BorderPane layout = new javafx.scene.layout.BorderPane();
        layout.setTop(headerBar);
        layout.setCenter(mainBody);
        layout.setStyle("-fx-background-color: #86d67f;");

        javafx.scene.layout.VBox.setVgrow(layout, javafx.scene.layout.Priority.ALWAYS);
        this.setFillWidth(true);

        getChildren().clear();
        getChildren().add(layout);

        // Filter + sort wiring (search box + default sort).
        this.filtered = new javafx.collections.transformation.FilteredList<>(master, r -> true);
        this.sorted = new javafx.collections.transformation.SortedList<>(filtered);
        this.sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(this.sorted);

        TeacherManagementListSupport.configureSearch(searchTf, filtered);

        TeacherManagementListSupport.applyDefaultSort(table, nameCol);
        loadTeachers(); // ✅ EXACTLY HERE
    }

    private void loadTeachers() {
        TeacherManagementListSupport.loadTeachersAsync(master, table, nameCol, System.err::println);
    }

    private void showTeacherDialog(java.util.Map<String, Object> data) {
        TeacherDialog.open(data, this::loadTeachers);
    }

    private void confirmDeleteTeacher(java.util.Map<String, Object> data) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete Teacher");
        alert.setContentText(
            "Are you sure you want to delete:\n\n" +
            data.get("name")
        );

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (TeacherDataSupport.deleteTeacher(data)) {
                loadTeachers();
            }
        }
    }

}