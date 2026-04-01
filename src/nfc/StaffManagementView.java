package nfc;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;

/**
 * Firestore-based Admins View
 * Replaces MySQL queries with Firestore reads/writes.
 */
public class StaffManagementView extends javafx.scene.layout.VBox {

    private final javafx.scene.control.TableView<Admin> table = new javafx.scene.control.TableView<>();
    private final javafx.collections.ObservableList<Admin> data = javafx.collections.FXCollections.observableArrayList();

    public StaffManagementView() {
        // Header bar
        javafx.scene.layout.HBox headerBar = new javafx.scene.layout.HBox(18);
        headerBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        headerBar.setPrefHeight(70);
        headerBar.setMaxWidth(Double.MAX_VALUE);
        headerBar.setStyle(
            "-fx-background-color: #2e8b57, #FECF4D;" +
            "-fx-background-insets: 0, 0 0 3 0;" +
            "-fx-background-radius: 0, 0;"
        );
        javafx.scene.image.ImageView honeyPot = new javafx.scene.image.ImageView(ImageLoader.loadSafe("hive2.png"));
        honeyPot.setFitWidth(54);
        honeyPot.setFitHeight(54);
        Label dashboardTitle = new Label("Admins");
        dashboardTitle.setFont(javafx.scene.text.Font.font("Impact", javafx.scene.text.FontWeight.EXTRA_BOLD, 44));
        dashboardTitle.setStyle("-fx-text-fill: #181818;");
        headerBar.getChildren().addAll(honeyPot, dashboardTitle);

        // Main body
        javafx.scene.layout.VBox mainBody = new javafx.scene.layout.VBox(10);
        mainBody.setPadding(new javafx.geometry.Insets(20));
        mainBody.setAlignment(javafx.geometry.Pos.TOP_LEFT);

        StaffManagementTableSupport.setupTable(table, data, new StaffManagementTableSupport.StaffActions() {
            @Override
            public void edit(Admin admin) {
                showEdit(admin);
            }

            @Override
            public void delete(Admin admin) {
                CRUDDialogs.showDeleteAdminDialog(admin, StaffManagementView.this::reload);
            }
        });

        Button addBtn = new Button("Add New Staff");
        addBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        addBtn.setOnAction(e -> CRUDDialogs.showStaffDialog(null, true, this::reload));

        mainBody.getChildren().addAll(table, addBtn);

        // Layout
        javafx.scene.layout.BorderPane layout = new javafx.scene.layout.BorderPane();
        layout.setTop(headerBar);
        layout.setCenter(mainBody);
        layout.setStyle("-fx-background-color: #86d67f;");
        javafx.scene.layout.VBox.setVgrow(layout, javafx.scene.layout.Priority.ALWAYS);
        this.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(layout);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        getChildren().clear();
        getChildren().add(scroll);
        javafx.scene.layout.VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);

        reload();
    }

    public final void reload() {
        data.clear();

        java.util.concurrent.CompletableFuture
            .supplyAsync(() -> {
                try {
                    return StaffDataSupport.loadAdmins();
                } catch (java.io.IOException | InterruptedException | IllegalStateException e) {
                    throw new RuntimeException(e);
                }
            })
            .whenComplete((admins, err) -> javafx.application.Platform.runLater(() -> {
                if (err != null) {
                    System.err.println("StaffManagementView: failed to load admins - " + err.getMessage());
                    new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR, "Failed to load admins").showAndWait();
                    return;
                }

                data.setAll(admins);
                System.out.println("✅ Loaded " + admins.size() + " admin records.");
            }));
    }

    private void showEdit(Admin admin) {
        CRUDDialogs.showStaffDialog(admin, false, this::reload);
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