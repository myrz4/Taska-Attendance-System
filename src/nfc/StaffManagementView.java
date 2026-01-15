package nfc;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import nfc.StaffManagementView.Admin;
import javafx.scene.layout.Priority;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;

import java.io.File;
import java.util.List;

/**
 * Firestore-based Admins View
 * Replaces MySQL queries with Firestore reads/writes.
 */
public class StaffManagementView extends VBox {

    private final TableView<Admin> table = new TableView<>();
    private final ObservableList<Admin> data = FXCollections.observableArrayList();

    public StaffManagementView() {
        // Header bar
        HBox headerBar = new HBox(18);
        headerBar.setAlignment(Pos.CENTER_LEFT);
        headerBar.setPrefHeight(70);
        headerBar.setMaxWidth(Double.MAX_VALUE);
        headerBar.setStyle(
            "-fx-background-color: #2e8b57;" +
            "-fx-border-color: #f4b400; -fx-border-width: 0 0 3 0;" +
            "-fx-background-image: repeating-linear-gradient(to bottom, transparent, transparent 12px, #FECF4D 12px, #FECF4D 15px);"
        );
        ImageView honeyPot = new ImageView(ImageLoader.loadSafe("hive2.png"));
        honeyPot.setFitWidth(54);
        honeyPot.setFitHeight(54);
        Label dashboardTitle = new Label("Admins");
        dashboardTitle.setFont(javafx.scene.text.Font.font("Impact", javafx.scene.text.FontWeight.EXTRA_BOLD, 44));
        dashboardTitle.setStyle("-fx-text-fill: #181818;");
        headerBar.getChildren().addAll(honeyPot, dashboardTitle);

        // Main body
        VBox mainBody = new VBox(10);
        mainBody.setPadding(new Insets(20));
        mainBody.setAlignment(Pos.TOP_LEFT);

        buildTable();

        Button addBtn = new Button("Add New Staff");
        addBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        addBtn.setOnAction(e -> CRUDDialogs.showStaffDialog(null, true, this::reload));

        mainBody.getChildren().addAll(table, addBtn);

        // Layout
        BorderPane layout = new BorderPane();
        layout.setTop(headerBar);
        layout.setCenter(mainBody);
        layout.setStyle("-fx-background-color: #86d67f;");
        VBox.setVgrow(layout, Priority.ALWAYS);
        this.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(layout);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        getChildren().clear();
        getChildren().add(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        reload();
    }

    private void buildTable() {
        table.setItems(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Admin, String> usernameCol = new TableColumn<>("Username");
        TableColumn<Admin, String> passwordCol = new TableColumn<>("Password");
        TableColumn<Admin, String> profilePictureCol = new TableColumn<>("Profile Picture");
        TableColumn<Admin, String> nameCol = new TableColumn<>("Name");
        TableColumn<Admin, Void> actCol = new TableColumn<>("Actions");

        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        passwordCol.setCellValueFactory(new PropertyValueFactory<>("password"));
        profilePictureCol.setCellValueFactory(new PropertyValueFactory<>("profilePicture"));
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        String fontStyle = "-fx-font-family: 'Poppins', 'Arial', sans-serif; -fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #181818;";

        usernameCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle(fontStyle);
            }
        });
        passwordCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : "*".repeat(8));
                setStyle(fontStyle);
            }
        });
        profilePictureCol.setCellFactory(tc -> new TableCell<>() {
            private final int imageSize = 44;
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setGraphic(null);
                    setText(null);
                } else {
                    File imgFile = new File("profile_pics/" + item);
                    if (imgFile.exists()) {
                        ImageView iv = new ImageView(new Image(imgFile.toURI().toString()));
                        iv.setFitHeight(imageSize);
                        iv.setFitWidth(imageSize);
                        iv.setPreserveRatio(true);
                        setGraphic(iv);
                        setText(null);
                    } else {
                        setGraphic(null);
                        setText("No image");
                    }
                }
            }
        });
        nameCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle(fontStyle);
            }
        });

        actCol.setCellFactory(tc -> new TableCell<>() {
            private final Button edit = new Button("Edit");
            private final Button del = new Button("Delete");

            {
                String btnStyle =
                    "-fx-background-color: #FFCB3C;" +
                    "-fx-font-size: 16px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-text-fill: #222;" +
                    "-fx-background-radius: 28px;";

                edit.setStyle(btnStyle);
                del.setStyle(btnStyle);

                edit.setOnAction(e -> showEdit(getCurrent()));

                del.setOnAction(e ->
                    CRUDDialogs.showDeleteAdminDialog(getCurrent(), StaffManagementView.this::reload)
                );
            }

            private Admin getCurrent() {
                return getTableView().getItems().get(getIndex());
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                    return;
                }

                Admin admin = getCurrent();
                String loggedIn = UserSession.getUsername();

                if (admin.getUsername() == null || !admin.getUsername().equals(loggedIn)) {
                    edit.setVisible(false);
                    edit.setManaged(false);
                    del.setVisible(false);
                    del.setManaged(false);
                } else {
                    edit.setVisible(true);
                    edit.setManaged(true);
                    del.setVisible(true);
                    del.setManaged(true);
                }

                setGraphic(new HBox(5, edit, del));
            }
        });
        table.getColumns().setAll(
            usernameCol,
            passwordCol,
            profilePictureCol,
            nameCol,
            actCol
        );
    }

    public void reload() {
        data.clear();

        try {
            Firestore db = FirestoreService.db();
            ApiFuture<QuerySnapshot> future = db.collection("admins").get();

            future.addListener(() -> {
                try {
                    List<QueryDocumentSnapshot> docs = future.get().getDocuments();
                    ObservableList<Admin> temp = FXCollections.observableArrayList();

                    for (DocumentSnapshot d : docs) {
                        temp.add(new Admin(
                            d.getString("username") != null ? d.getString("username") : d.getId(),
                            d.getString("password"),
                            d.getString("profilePicture"),
                            d.getString("name")
                        ));
                    }

                    javafx.application.Platform.runLater(() -> {
                        data.setAll(temp);
                        System.out.println("✅ Loaded " + temp.size() + " admin records.");
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }, java.util.concurrent.Executors.newSingleThreadExecutor());

        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to load admins").showAndWait();
        }
    }

    private void showEdit(Admin admin) {
        CRUDDialogs.showStaffDialog(admin, false, this::reload);
    }

    private void deleteAdmin(Admin admin) {
        if (admin.getUsername().equals(UserSession.getUsername())) {
            new Alert(
                Alert.AlertType.ERROR,
                "You cannot delete your own admin account."
            ).showAndWait();
            return;
        }

        // ✅ CONFIRMATION POPUP
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete Admin Account");
        confirm.setContentText(
            "Are you sure you want to delete admin \"" + admin.getUsername() + "\"?\n\n" +
            "This action cannot be undone."
        );

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return; // ❌ User cancelled
        }

        try {
            Firestore db = FirestoreService.db();
            Query q = db.collection("admins")
                        .whereEqualTo("username", admin.getUsername());

            List<QueryDocumentSnapshot> docs = q.get().get().getDocuments();

            for (DocumentSnapshot doc : docs) {
                db.collection("admins").document(doc.getId()).delete().get();
            }

            new Alert(Alert.AlertType.INFORMATION,
                    "Admin \"" + admin.getUsername() + "\" deleted successfully."
            ).showAndWait();

            System.out.println("🗑 Deleted staff: " + admin.getUsername());

        } catch (Exception e) {
            e.printStackTrace();
            new Alert(
                Alert.AlertType.ERROR,
                "Failed to delete admin: " + e.getMessage()
            ).showAndWait();
        }
    }

    public static class Admin {
        private final String username;
        private final String password;
        private final String profilePicture;
        private final String name;

        public Admin(String username, String password, String profilePicture, String name) {
            this.username = username;
            this.password = password;
            this.profilePicture = profilePicture;
            this.name = name;
        }

        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getProfilePicture() { return profilePicture; }
        public String getName() { return name; }
    }
}