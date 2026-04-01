package nfc;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;

import java.util.concurrent.CompletableFuture;

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
            "-fx-background-color: #2e8b57, #FECF4D;" +
            "-fx-background-insets: 0, 0 0 3 0;" +
            "-fx-background-radius: 0, 0;"
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

    public final void reload() {
        data.clear();

        CompletableFuture
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
                    new Alert(Alert.AlertType.ERROR, "Failed to load admins").showAndWait();
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