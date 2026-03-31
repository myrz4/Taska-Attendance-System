package nfc;

import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import java.util.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

public class TeacherManagementView extends VBox {

    private static final ZoneId KL_ZONE = ZoneId.of("Asia/Kuala_Lumpur");

    private static boolean isLegacyId(String id) {
        if (id == null) return false;
        if (id.matches("^t\\d+$")) return true;
        return id.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }

    private final TableView<Map<String, Object>> table = new TableView<>();
    private final ObservableList<Map<String, Object>> master = FXCollections.observableArrayList();
    private FilteredList<Map<String, Object>> filtered;
    private SortedList<Map<String, Object>> sorted;
    private TableColumn<Map<String, Object>, String> nameCol;
    private Button addTeacherButton;

    public TeacherManagementView() {

        // ===== HEADER BAR (COPIED FROM STAFF MANAGEMENT) =====
        HBox headerBar = new HBox(18);
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
        VBox mainBody = new VBox(10);
        mainBody.setPadding(new Insets(20));
        mainBody.setAlignment(Pos.TOP_LEFT);

        TextField searchTf = new TextField();
        searchTf.setPromptText("Search name / username / email / phone...");
        searchTf.setMaxWidth(Double.MAX_VALUE);

        createTable(); // builds table + add button
        mainBody.getChildren().addAll(searchTf, table, addTeacherButton);

        // ===== WRAPPER LAYOUT (SAME AS ADMINS) =====
        BorderPane layout = new BorderPane();
        layout.setTop(headerBar);
        layout.setCenter(mainBody);
        layout.setStyle("-fx-background-color: #86d67f;");

        VBox.setVgrow(layout, Priority.ALWAYS);
        this.setFillWidth(true);

        getChildren().clear();
        getChildren().add(layout);

        // Filter + sort wiring (search box + default sort).
        filtered = new FilteredList<>(master, r -> true);
        sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        searchTf.textProperty().addListener((obs, oldV, newV) -> {
            final String q = (newV == null ? "" : newV.trim().toLowerCase());
            if (q.isEmpty()) {
                filtered.setPredicate(r -> true);
                return;
            }
            filtered.setPredicate(r -> {
                if (r == null) return false;
                String n = Objects.toString(r.get("name"), "").toLowerCase();
                String u = Objects.toString(r.get("username"), "").toLowerCase();
                String e = Objects.toString(r.get("email"), "").toLowerCase();
                String p = Objects.toString(r.get("phone"), "").toLowerCase();
                return n.contains(q) || u.contains(q) || e.contains(q) || p.contains(q);
            });
        });

        if (nameCol != null) {
            nameCol.setSortType(TableColumn.SortType.ASCENDING);
            table.getSortOrder().setAll(nameCol);
        }
        loadTeachers(); // ✅ EXACTLY HERE
    }

    private void loadTeachers() {
        CompletableFuture
            .supplyAsync(() -> {
                try {
                    FirestoreRestClient client = FirestoreRest.forCurrentUser();
                    return client.listDocuments("teachers");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .whenComplete((docs, err) -> javafx.application.Platform.runLater(() -> {
                if (err != null) {
                    err.printStackTrace();
                    return;
                }

                ObservableList<Map<String, Object>> list = FXCollections.observableArrayList();
                for (FsDocument doc : docs) {
                    if (doc == null) continue;
                    Map<String, Object> data = doc.fields();
                    if (data == null) continue;

                    String teacherId = doc.getId();

                    // Skip obvious legacy placeholder docs (t01/t02) if they don't carry real data.
                    if (teacherId != null && teacherId.matches("^t\\d+$")) {
                        String nm = Objects.toString(data.get("name"), "").trim();
                        String ph = Objects.toString(data.get("phone"), "").trim();
                        String img = Objects.toString(data.get("image"), "").trim();
                        if (nm.isEmpty() && ph.isEmpty() && img.isEmpty()) {
                            continue;
                        }
                    }

                    Map<String, Object> m = new HashMap<>(data);
                    m.put("id", teacherId);
                    list.add(m);
                }

                // Default sort by name (A→Z) for easier scanning.
                FXCollections.sort(list, (a, b) -> {
                    String an = Objects.toString(a.get("name"), "").trim().toLowerCase();
                    String bn = Objects.toString(b.get("name"), "").trim().toLowerCase();
                    int c = an.compareTo(bn);
                    if (c != 0) return c;
                    String au = Objects.toString(a.get("username"), "").trim().toLowerCase();
                    String bu = Objects.toString(b.get("username"), "").trim().toLowerCase();
                    return au.compareTo(bu);
                });

                master.setAll(list);

                if (nameCol != null) {
                    nameCol.setSortType(TableColumn.SortType.ASCENDING);
                    table.getSortOrder().setAll(nameCol);
                    table.sort();
                }
            }));
    }

    private void createTable() {
        TableColumn<Map<String, Object>, Integer> noCol = new TableColumn<>("No");
        nameCol = col("Name", "name");
        TableColumn<Map<String, Object>, String> usernameCol = col("Username", "username");
        TableColumn<Map<String, Object>, String> emailCol = col("Email", "email");
        TableColumn<Map<String, Object>, String> phoneCol = col("Phone", "phone");
        TableColumn<Map<String, Object>, String> salaryCol = moneyCol("Base Salary (RM)", "salaryBaseSen");
        TableColumn<Map<String, Object>, String> imageCol = imageCol("Image", "image");

        TableColumn<Map<String, Object>, Void> actionCol = new TableColumn<>("Actions");

        noCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else {
                    setText(String.valueOf(getIndex() + 1));
                }
                setAlignment(Pos.CENTER);
                setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #181818; -fx-alignment: CENTER;");
            }
        });

        actionCol.setCellFactory(col -> new TableCell<>() {

            private final Button edit = new Button("Edit");
            private final Button del = new Button("Delete");

            {
                edit.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
                del.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");

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
                            FirestoreRestClient client = FirestoreRest.forCurrentUser();
                            client.deleteDocument("teachers", Objects.toString(data.get("id"), "").trim());

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

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox actions = new HBox(8, edit, del);
                    actions.setAlignment(Pos.CENTER);
                    setAlignment(Pos.CENTER);
                    setGraphic(actions);
                }
            }
        });

        noCol.setPrefWidth(60);
        nameCol.setPrefWidth(220);
        usernameCol.setPrefWidth(160);
        emailCol.setPrefWidth(220);
        phoneCol.setPrefWidth(150);
        salaryCol.setPrefWidth(140);
        imageCol.setPrefWidth(90);
        actionCol.setPrefWidth(180);

        table.getColumns().setAll(
            noCol,
            nameCol,
            usernameCol,
            emailCol,
            phoneCol,
            salaryCol,
            imageCol,
            actionCol
        );

        // Match Children & Parents table behavior.
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        addTeacherButton = new Button("Add Teacher");
        addTeacherButton.setStyle("-fx-background-color: #FFCB3C; -fx-font-size: 16px; -fx-font-weight: bold; -fx-background-radius: 28;");
        addTeacherButton.setOnAction(e -> new TeacherDialog(null, () -> loadTeachers()));
    }

    private TableColumn<Map<String, Object>, String> moneyCol(String title, String key) {
        TableColumn<Map<String, Object>, String> c = new TableColumn<>(title);

        c.setCellValueFactory(d -> {
            Object raw = d.getValue().get(key);
            String text = "-";
            if (raw instanceof Number) {
                double rm = ((Number) raw).doubleValue() / 100.0;
                text = String.format(java.util.Locale.US, "RM %.2f", rm);
            }
            return new javafx.beans.property.SimpleStringProperty(text);
        });

        c.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                }
                setAlignment(Pos.CENTER);
                setStyle("-fx-font-family: 'Poppins', 'Arial', sans-serif; -fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #181818; -fx-alignment: CENTER;");
            }
        });

        c.setStyle("-fx-alignment: CENTER;");
        return c;
    }

    private TableColumn<Map<String, Object>, String> imageCol(String title, String key) {
        TableColumn<Map<String, Object>, String> c = new TableColumn<>(title);

        c.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(
                Objects.toString(d.getValue().get(key), "")
            )
        );

        c.setCellFactory(tc -> new TableCell<>() {
            private final ImageView iv = new ImageView();

            {
                iv.setFitWidth(42);
                iv.setFitHeight(42);
                iv.setPreserveRatio(true);
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(String url, boolean empty) {
                super.updateItem(url, empty);
                if (empty || url == null || url.trim().isEmpty()) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                try {
                    Image img = ImageCache.loadCachedOrRemote(url.trim(), 42, 42);
                    iv.setImage(img);
                    setGraphic(iv);
                    setText(null);
                } catch (Exception ex) {
                    setGraphic(null);
                    setText("");
                }
            }
        });

        c.setStyle("-fx-alignment: CENTER;");

        return c;
    }

    private TableColumn<Map<String, Object>, String> col(String title, String key) {
        TableColumn<Map<String, Object>, String> c = new TableColumn<>(title);

        c.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(
                Objects.toString(d.getValue().get(key), "")
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
                        "-fx-text-fill: #181818;" +
                        "-fx-alignment: CENTER;"
                    );
                    setAlignment(Pos.CENTER);
                }
            }
        });

        c.setStyle("-fx-alignment: CENTER;");

        return c;
    }

    private interface Formatter {
        String format(Object value);
    }

    private TableColumn<Map<String, Object>, String> colFormatted(String title, String key, Formatter formatter) {
        TableColumn<Map<String, Object>, String> c = new TableColumn<>(title);

        c.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(
                formatter.format(d.getValue().get(key))
            )
        );

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

    private static String formatJoinDate(Object value) {
        if (value == null) return "";
        try {
            if (value instanceof Date d) {
                LocalDate ld = d.toInstant().atZone(KL_ZONE).toLocalDate();
                return ld.toString();
            }
            String s = String.valueOf(value).trim();
            if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return s;
            }
        } catch (Exception ignored) {
        }
        return String.valueOf(value);
    }

    private static String formatMoneyLike(Object value) {
        if (value == null) return "";
        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (Math.floor(d) == d) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        return String.valueOf(value);
    }
}