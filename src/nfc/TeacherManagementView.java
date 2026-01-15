package nfc;

import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;

import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.DocumentSnapshot;
import java.util.*;

public class TeacherManagementView extends VBox {

    private TableView<Map<String, Object>> table = new TableView<>();
    private Button addTeacherButton;

    public TeacherManagementView() {

        // ===== HEADER BAR (COPIED FROM STAFF MANAGEMENT) =====
        HBox headerBar = new HBox(18);
        headerBar.setAlignment(Pos.CENTER_LEFT);
        headerBar.setPrefHeight(70);
        headerBar.setMaxWidth(Double.MAX_VALUE);
        headerBar.setStyle(
            "-fx-background-color: #2e8b57;" +
            "-fx-border-color: #f4b400; -fx-border-width: 0 0 3 0;" +
            "-fx-background-image: repeating-linear-gradient(to bottom, transparent, transparent 12px, #FECF4D 12px, #FECF4D 15px);"
        );

        Label title = new Label("Teachers");
        title.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 44));
        title.setStyle("-fx-text-fill: #181818;");

        ImageView honeyPot = new ImageView(ImageLoader.loadSafe("hive2.png"));
        honeyPot.setFitWidth(54);
        honeyPot.setFitHeight(54);

        headerBar.getChildren().addAll(honeyPot, title);

        // ===== MAIN BODY =====
        VBox mainBody = new VBox(10);
        mainBody.setPadding(new Insets(20));
        mainBody.setAlignment(Pos.TOP_LEFT);

        createTable(); // builds table + add button
        mainBody.getChildren().addAll(table, addTeacherButton);

        // ===== WRAPPER LAYOUT (SAME AS ADMINS) =====
        BorderPane layout = new BorderPane();
        layout.setTop(headerBar);
        layout.setCenter(mainBody);
        layout.setStyle("-fx-background-color: #86d67f;");

        VBox.setVgrow(layout, Priority.ALWAYS);
        this.setFillWidth(true);

        getChildren().clear();
        getChildren().add(layout);
        loadTeachers(); // ✅ EXACTLY HERE
    }

    private void loadTeachers() {
        try {
            Firestore db = FirestoreService.db();
            var future = db.collection("teachers").get();

            future.addListener(() -> {
                try {
                    QuerySnapshot snapshot = future.get();
                    ObservableList<Map<String, Object>> list =
                            FXCollections.observableArrayList();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Map<String, Object> m = new HashMap<>(doc.getData());
                        m.put("id", doc.getId());
                        list.add(m);
                    }

                    javafx.application.Platform.runLater(() ->
                            table.setItems(list)
                    );

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, java.util.concurrent.Executors.newSingleThreadExecutor());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createTable() {

        TableColumn<Map<String, Object>, String> idCol = col("ID", "id");
        TableColumn<Map<String, Object>, String> nameCol = col("Name", "name");
        TableColumn<Map<String, Object>, String> userCol = col("Username", "username");
        TableColumn<Map<String, Object>, String> phoneCol = col("Phone", "phone");

        TableColumn<Map<String, Object>, Void> actionCol = new TableColumn<>("Actions");

        actionCol.setCellFactory(col -> new TableCell<>() {

            private final Button edit = new Button("Edit");
            private final Button del = new Button("Delete");

            {
                edit.setStyle("-fx-background-color: #FFCB3C; -fx-background-radius: 20;");
                del.setStyle("-fx-background-color: #FFCB3C; -fx-background-radius: 20;");

                {
                    edit.setStyle("-fx-background-color: #FFCB3C; -fx-background-radius: 20;");
                    del.setStyle("-fx-background-color: #FFCB3C; -fx-background-radius: 20;");

                    edit.setOnAction(e -> {
                        Map<String, Object> data =
                            getTableView().getItems().get(getIndex());
                        new TeacherDialog(data, () -> loadTeachers());
                    });

                    del.setOnAction(e -> {
                        Map<String, Object> data =
                            getTableView().getItems().get(getIndex());

                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Confirm Delete");
                        alert.setHeaderText("Delete Teacher");
                        alert.setContentText(
                            "Are you sure you want to delete:\n\n" +
                            data.get("name")
                        );

                        Optional<ButtonType> result = alert.showAndWait();
                        if (result.isPresent() && result.get() == ButtonType.OK) {
                            try {
                                FirestoreService.db()
                                    .collection("teachers")
                                    .document((String) data.get("id"))
                                    .delete()
                                    .get(); // wait for Firestore

                                loadTeachers();
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                new Alert(
                                    Alert.AlertType.ERROR,
                                    "Failed to delete teacher"
                                ).showAndWait();
                            }
                        }
                    });
                }
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(new HBox(8, edit, del));
                }
            }
        });

        table.getColumns().setAll(idCol, nameCol, userCol, phoneCol, actionCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        addTeacherButton = new Button("Add Teacher");
        addTeacherButton.setStyle("-fx-background-color: #FFCB3C; -fx-font-size: 16px; -fx-font-weight: bold; -fx-background-radius: 28;");
        addTeacherButton.setOnAction(e -> new TeacherDialog(null, () -> loadTeachers()));
    }

    private TableColumn<Map<String, Object>, String> col(String title, String key) {
        TableColumn<Map<String, Object>, String> c = new TableColumn<>(title);

        c.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(
                String.valueOf(d.getValue().get(key))
            )
        );

        // ✅ MAKE TEXT BOLD (same style as Children & Parents)
        c.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle(
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 13px;" +
                        "-fx-text-fill: #181818;"
                    );
                }
            }
        });

        return c;
    }
}