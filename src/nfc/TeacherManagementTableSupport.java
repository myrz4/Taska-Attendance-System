package nfc;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

@SuppressWarnings("unused")
final class TeacherManagementTableSupport {
    private TeacherManagementTableSupport() {}

    static TableBundle setupTable(
        TableView<Map<String, Object>> table,
        Consumer<Map<String, Object>> onEdit,
        Consumer<Map<String, Object>> onDelete,
        Runnable onAdd
    ) {
        TableColumn<Map<String, Object>, Integer> noCol = new TableColumn<>("No");
        TableColumn<Map<String, Object>, String> nameCol = textCol("Name", "name");
        TableColumn<Map<String, Object>, String> usernameCol = textCol("Username", "username");
        TableColumn<Map<String, Object>, String> emailCol = textCol("Email", "email");
        TableColumn<Map<String, Object>, String> phoneCol = textCol("Phone", "phone");
        TableColumn<Map<String, Object>, String> salaryCol = moneyCol("Base Salary (RM)", "salaryBaseSen");
        TableColumn<Map<String, Object>, String> imageCol = imageCol("Image", "image");
        TableColumn<Map<String, Object>, Void> actionCol = actionCol(onEdit, onDelete);

        noCol.setCellFactory(col -> new TableCell<Map<String, Object>, Integer>() {
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

        noCol.setPrefWidth(60);
        nameCol.setPrefWidth(220);
        usernameCol.setPrefWidth(160);
        emailCol.setPrefWidth(220);
        phoneCol.setPrefWidth(150);
        salaryCol.setPrefWidth(140);
        imageCol.setPrefWidth(90);
        actionCol.setPrefWidth(180);

        table.getColumns().clear();
        table.getColumns().add(noCol);
        table.getColumns().add(nameCol);
        table.getColumns().add(usernameCol);
        table.getColumns().add(emailCol);
        table.getColumns().add(phoneCol);
        table.getColumns().add(salaryCol);
        table.getColumns().add(imageCol);
        table.getColumns().add(actionCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button addTeacherButton = new Button("Add Teacher");
        addTeacherButton.setStyle("-fx-background-color: #FFCB3C; -fx-font-size: 16px; -fx-font-weight: bold; -fx-background-radius: 28;");
        addTeacherButton.setOnAction(e -> onAdd.run());

        return new TableBundle(nameCol, addTeacherButton);
    }

    private static TableColumn<Map<String, Object>, Void> actionCol(
        Consumer<Map<String, Object>> onEdit,
        Consumer<Map<String, Object>> onDelete
    ) {
        TableColumn<Map<String, Object>, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setCellFactory(col -> new TableCell<Map<String, Object>, Void>() {
            private final Button edit = new Button("Edit");
            private final Button del = new Button("Delete");

            {
                edit.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
                del.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");

                edit.setOnAction(e -> {
                    Map<String, Object> data = getTableView().getItems().get(getIndex());
                    onEdit.accept(data);
                });

                del.setOnAction(e -> {
                    Map<String, Object> data = getTableView().getItems().get(getIndex());
                    onDelete.accept(data);
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
        return actionCol;
    }

    private static TableColumn<Map<String, Object>, String> moneyCol(String title, String key) {
        TableColumn<Map<String, Object>, String> column = new TableColumn<>(title);
        column.setCellValueFactory(d -> {
            Object raw = d.getValue().get(key);
            String text = "-";
            if (raw instanceof Number) {
                double rm = ((Number) raw).doubleValue() / 100.0;
                text = String.format(java.util.Locale.US, "RM %.2f", rm);
            }
            return new SimpleStringProperty(text);
        });
        column.setCellFactory(tc -> centeredTextCell());
        column.setStyle("-fx-alignment: CENTER;");
        return column;
    }

    private static TableColumn<Map<String, Object>, String> imageCol(String title, String key) {
        TableColumn<Map<String, Object>, String> column = new TableColumn<>(title);
        column.setCellValueFactory(d -> new SimpleStringProperty(Objects.toString(d.getValue().get(key), "")));
        column.setCellFactory(tc -> new TableCell<Map<String, Object>, String>() {
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
        column.setStyle("-fx-alignment: CENTER;");
        return column;
    }

    private static TableColumn<Map<String, Object>, String> textCol(String title, String key) {
        TableColumn<Map<String, Object>, String> column = new TableColumn<>(title);
        column.setCellValueFactory(d -> new SimpleStringProperty(Objects.toString(d.getValue().get(key), "")));
        column.setCellFactory(tc -> centeredTextCell());
        column.setStyle("-fx-alignment: CENTER;");
        return column;
    }

    private static TableCell<Map<String, Object>, String> centeredTextCell() {
        return new TableCell<Map<String, Object>, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #181818; -fx-alignment: CENTER;");
                    setAlignment(Pos.CENTER);
                }
            }
        };
    }

    static final class TableBundle {
        final TableColumn<Map<String, Object>, String> nameCol;
        final Button addTeacherButton;

        TableBundle(TableColumn<Map<String, Object>, String> nameCol, Button addTeacherButton) {
            this.nameCol = nameCol;
            this.addTeacherButton = addTeacherButton;
        }
    }
}