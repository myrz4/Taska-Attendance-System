package nfc;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;

import nfc.ChildrenView.Child;
import nfc.FirestoreService;

/**
 * Firestore-based ChildrenView
 * Fixed to properly handle Firestore field names and date parsing.
 */
public class ChildrenView extends VBox {

    private final TableView<Child> table = new TableView<>();
    private final ObservableList<Child> data = FXCollections.observableArrayList();

    public ChildrenView() {
        // --- HEADER ---
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

        Label headerTitle = new Label("Children & Parents");
        headerTitle.setStyle("-fx-font-family: Impact; -fx-font-size: 44px; -fx-font-weight: bold; -fx-text-fill: #181818;");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        headerBar.getChildren().addAll(honeyPot, headerTitle, headerSpacer);

        // --- MAIN CONTENT ---
        VBox mainContent = new VBox(10);
        mainContent.setPadding(new Insets(20));
        mainContent.setAlignment(Pos.TOP_LEFT);

        buildTable();

        Button addBtn = new Button("Add New Child");
        addBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        addBtn.setOnAction(e -> CRUDDialogs.showChildDialog(null, true, this::reload));

        VBox.setVgrow(table, Priority.ALWAYS);
        mainContent.getChildren().addAll(table, addBtn);

        BorderPane layout = new BorderPane();
        layout.setTop(headerBar);
        layout.setCenter(mainContent);
        layout.setStyle("-fx-background-color: linear-gradient(to bottom right, #86d67f 0%, #76cc6e 100%);");

        VBox.setVgrow(layout, Priority.ALWAYS);
        this.setFillWidth(true);
        this.getChildren().add(layout);

        reload();
    }

    private void buildTable() {
        table.setItems(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Child, Integer> idCol = new TableColumn<>("No");
        TableColumn<Child, String> nameCol = new TableColumn<>("Name");
        TableColumn<Child, LocalDate> dobCol = new TableColumn<>("Birth Date");
        TableColumn<Child, String> parentNameCol = new TableColumn<>("Parent Name");
        TableColumn<Child, String> parentContactCol = new TableColumn<>("Parent Contact");
        TableColumn<Child, String> uidCol = new TableColumn<>("NFC UID");
        TableColumn<Child, Void> actionsCol = new TableColumn<>("Actions");

        idCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else {
                    setText(String.valueOf(getIndex() + 1));
                }
                setStyle("-fx-font-weight: bold; -fx-text-fill: #181818;");
            }
        });

        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        dobCol.setCellValueFactory(new PropertyValueFactory<>("birthDate"));
        parentNameCol.setCellValueFactory(new PropertyValueFactory<>("parentName"));
        parentContactCol.setCellValueFactory(new PropertyValueFactory<>("parentContact"));
        uidCol.setCellValueFactory(new PropertyValueFactory<>("nfcUid"));

        String fontStyle = "-fx-font-family: 'Poppins', 'Arial', sans-serif; -fx-font-size: 13px;-fx-font-weight: bold; -fx-text-fill: #181818;";

        nameCol.setCellFactory(tc -> makeCell(fontStyle));
        dobCol.setCellFactory(tc -> makeCell(fontStyle));
        parentNameCol.setCellFactory(tc -> makeCell(fontStyle));
        parentContactCol.setCellFactory(tc -> makeCell(fontStyle));
        uidCol.setCellFactory(tc -> makeCell(fontStyle));

        actionsCol.setCellFactory(tc -> new TableCell<>() {
            private final Button edit = new Button("Edit");
            private final Button del = new Button("Delete");
            {
                edit.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
                del.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");

                edit.setOnAction(e -> showEdit(getCurrent()));
                del.setOnAction(e -> {
                    Child current = getCurrent();
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete child and all their attendance records?", ButtonType.YES, ButtonType.NO);
                    alert.setHeaderText("Confirm Delete");
                    alert.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            deleteChild(current);
                            data.remove(current);
                        }
                    });
                });
            }

            private Child getCurrent() { return getTableView().getItems().get(getIndex()); }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else setGraphic(new HBox(5, edit, del));
            }
        });

        table.getColumns().setAll(idCol, nameCol, dobCol, parentNameCol, parentContactCol, uidCol, actionsCol);
    }

    private <T> TableCell<Child, T> makeCell(String style) {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setStyle(style);
            }
        };
    }

    // ✅ FIRESTORE LOAD FUNCTION
    // ✅ ASYNC FIRESTORE LOAD FUNCTION
    public void reload() {
        data.clear();
        try {
            Firestore db = FirestoreService.db();
            CollectionReference childrenRef = db.collection("children");
            System.out.println("DEBUG >> Fetching from root path: " + childrenRef.getPath());

            ApiFuture<QuerySnapshot> future = childrenRef.get();
            future.addListener(() -> {
                try {
                    List<QueryDocumentSnapshot> docs = future.get().getDocuments();
                    System.out.println("DEBUG >> Fetched " + docs.size() + " children docs.");

                    int counter = 1;
                    ObservableList<Child> tempList = FXCollections.observableArrayList();

                    for (DocumentSnapshot doc : docs) {
                        String name = doc.getString("name");
                        String parentName = doc.getString("parentName");
                        String parentContact = doc.getString("parentContact");
                        String nfcUid = doc.getId();  // ✅ use the Firestore document ID instead

                        System.out.println("✅ Loaded child: " + name + " (" + nfcUid + ")");

                        LocalDate birthDate = null;
                        Object birthObj = doc.get("birthDate");
                        if (birthObj != null) {
                            try {
                                String raw = birthObj.toString().trim();

                                // ✅ Try format: Fri Feb 07 2003 00:00:00 GMT+0800 (Malaysia Time)
                                java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
                                        "EEE MMM dd yyyy HH:mm:ss 'GMT'Z '(Malaysia Time)'", java.util.Locale.ENGLISH);

                                // Try parsing — fallback if it’s already yyyy-MM-dd
                                java.util.Date parsedDate;
                                if (raw.matches("\\d{4}-\\d{2}-\\d{2}")) {
                                    birthDate = LocalDate.parse(raw);
                                } else {
                                    parsedDate = fmt.parse(raw);
                                    birthDate = parsedDate.toInstant()
                                            .atZone(java.time.ZoneId.of("Asia/Kuala_Lumpur"))
                                            .toLocalDate();
                                }

                                System.out.println("✅ Parsed birthDate: " + birthDate);
                            } catch (Exception e) {
                                System.out.println("⚠️ Could not parse birthDate: " + birthObj);
                            }
                        }
                        tempList.add(new Child(name, birthDate, parentName, parentContact, nfcUid));
                    }

                    // ✅ Safely update JavaFX UI
                    javafx.application.Platform.runLater(() -> data.setAll(tempList));

                } catch (Exception e) {
                    e.printStackTrace();
                    javafx.application.Platform.runLater(() ->
                        new Alert(Alert.AlertType.ERROR, "Failed to load children: " + e.getMessage()).showAndWait()
                    );
                }
            }, java.util.concurrent.Executors.newSingleThreadExecutor());

        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to start Firestore load: " + ex.getMessage()).showAndWait();
        }
    }

    private void showEdit(Child c) {
        CRUDDialogs.showChildDialog(c, false, this::reload);
    }

    private void deleteChild(Child c) {
        try {
            Firestore db = FirestoreService.db();
            db.collection("children")
            .document(c.getNfcUid())
            .delete();

            System.out.println("🗑 Deleted child " + c.getNfcUid());
        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR,
                    "Failed to delete child: " + ex.getMessage())
                    .showAndWait();
        }
    }

    public static class Child {
        private final String name;
        private final LocalDate birthDate;
        private final String parentName;
        private final String parentContact;
        private final String nfcUid;

        public Child(String name, LocalDate birthDate,
                    String parentName, String parentContact, String nfcUid) {
            this.name = name;
            this.birthDate = birthDate;
            this.parentName = parentName;
            this.parentContact = parentContact;
            this.nfcUid = nfcUid;
        }

        public String getName() { return name; }
        public LocalDate getBirthDate() { return birthDate; }
        public String getParentName() { return parentName; }
        public String getParentContact() { return parentContact; }
        public String getNfcUid() { return nfcUid; }
    }
}