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
    private final javafx.collections.ObservableList<Admin> master = javafx.collections.FXCollections.observableArrayList();
    private final javafx.collections.transformation.FilteredList<Admin> filtered =
        new javafx.collections.transformation.FilteredList<>(master, row -> true);
    private final javafx.collections.transformation.SortedList<Admin> sorted =
        new javafx.collections.transformation.SortedList<>(filtered);
    private final javafx.scene.control.TableColumn<Admin, String> nameCol;

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

        javafx.scene.control.TextField searchField = SummaryTableSupport.createSearchField(
            "Search admin / username / email / phone..."
        );
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase(java.util.Locale.ROOT);
            filtered.setPredicate(admin -> admin == null || query.isEmpty() || admin.matchesSearch(query));
        });

        StaffManagementTableSupport.TableBundle tableBundle = StaffManagementTableSupport.setupTable(table, new StaffManagementTableSupport.StaffActions() {
            @Override
            public void edit(Admin admin) {
                showEdit(admin);
            }

            @Override
            public void delete(Admin admin) {
                CRUDDialogs.showDeleteAdminDialog(admin, StaffManagementView.this::reload);
            }
        });
        this.nameCol = tableBundle.nameCol;

        javafx.scene.control.MenuButton columnChooser = SummaryTableSupport.createColumnChooser(
            "Columns",
            tableBundle.optionalColumns
        );

        Button addBtn = SummaryTableSupport.createPrimaryButton("Add New Staff");
        addBtn.setOnAction(e -> CRUDDialogs.showStaffDialog(null, true, this::reload));

        javafx.scene.layout.HBox toolbar = new javafx.scene.layout.HBox(10, searchField, columnChooser, addBtn);
        toolbar.getStyleClass().add("summary-toolbar");
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.layout.HBox.setHgrow(searchField, javafx.scene.layout.Priority.ALWAYS);

        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        mainBody.getChildren().addAll(toolbar, table);

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

        nameCol.setSortType(javafx.scene.control.TableColumn.SortType.ASCENDING);
        table.getSortOrder().setAll(java.util.List.of(nameCol));
        table.sort();

        reload();
    }

    public final void reload() {
        master.clear();

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

                master.setAll(admins);
                table.getSortOrder().setAll(java.util.List.of(nameCol));
                table.sort();
                System.out.println("✅ Loaded " + admins.size() + " admin records.");
            }));
    }

    private void showEdit(Admin admin) {
        CRUDDialogs.showStaffDialog(admin, false, this::reload);
    }

    public static class Admin {
        private final String recordId;
        private final String username;
        private final String password;
        private final String profilePicture;
        private final String name;
        private final String role;
        private final String email;
        private final String phone;
        private final String status;
        private final String lastLogin;

        public Admin(String username, String password, String profilePicture, String name) {
            this(username, username, password, profilePicture, name, "Admin", "", "", "Active", "-");
        }

        public Admin(
            String recordId,
            String username,
            String password,
            String profilePicture,
            String name,
            String role,
            String email,
            String phone,
            String status,
            String lastLogin
        ) {
            this.recordId = recordId;
            this.username = username;
            this.password = password;
            this.profilePicture = profilePicture;
            this.name = name;
            this.role = role;
            this.email = email;
            this.phone = phone;
            this.status = status;
            this.lastLogin = lastLogin;
        }

        public String getRecordId() { return recordId; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getProfilePicture() { return profilePicture; }
        public String getName() { return name; }
        public String getRole() { return role; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public String getStatus() { return status; }
        public String getLastLogin() { return lastLogin; }

        public boolean matchesSearch(String query) {
            return contains(name, query)
                || contains(username, query)
                || contains(email, query)
                || contains(phone, query)
                || contains(role, query)
                || contains(status, query)
                || contains(recordId, query);
        }

        private static boolean contains(String value, String query) {
            return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(query);
        }
    }
}