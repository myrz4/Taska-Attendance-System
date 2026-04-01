package nfc;

import java.util.Arrays;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

@SuppressWarnings("unused")
final class ParentsTableSupport {
    private ParentsTableSupport() {}

    static void setupTable(
        TableView<ParentsPane.ParentRecord> table,
        Consumer<ParentsPane.ParentRecord> onEdit,
        Consumer<ParentsPane.ParentRecord> onDeleteConfirmed
    ) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ParentsPane.ParentRecord, Integer> noCol = new TableColumn<>("No");
        TableColumn<ParentsPane.ParentRecord, String> nameCol = new TableColumn<>("Parent Name");
        TableColumn<ParentsPane.ParentRecord, String> relationshipCol = new TableColumn<>("Relationship");
        TableColumn<ParentsPane.ParentRecord, String> phoneCol = new TableColumn<>("Phone");
        TableColumn<ParentsPane.ParentRecord, String> childNameCol = new TableColumn<>("Child Name");
        TableColumn<ParentsPane.ParentRecord, Void> actionsCol = new TableColumn<>("Actions");

        noCol.setCellFactory(col -> new TableCell<ParentsPane.ParentRecord, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
                setAlignment(Pos.CENTER);
                setStyle("-fx-font-weight: bold; -fx-text-fill: #181818; -fx-alignment: CENTER;");
            }
        });

        nameCol.setCellValueFactory(new PropertyValueFactory<>("parentName"));
        phoneCol.setCellValueFactory(new PropertyValueFactory<>("phone"));
        relationshipCol.setCellValueFactory(new PropertyValueFactory<>("relationship"));
        childNameCol.setCellValueFactory(new PropertyValueFactory<>("childName"));

        String fontStyle = "-fx-font-family: 'Poppins', 'Arial', sans-serif; -fx-font-size: 13px;-fx-font-weight: bold; -fx-text-fill: #181818;";
        String centerCol = "-fx-alignment: CENTER;";
        nameCol.setStyle(centerCol);
        relationshipCol.setStyle(centerCol);
        phoneCol.setStyle(centerCol);
        childNameCol.setStyle(centerCol);
        actionsCol.setStyle(centerCol);
        noCol.setStyle(centerCol);

        nameCol.setCellFactory(tc -> makeCell(fontStyle));
        relationshipCol.setCellFactory(tc -> makeCell(fontStyle));
        phoneCol.setCellFactory(tc -> makeCell(fontStyle));
        childNameCol.setCellFactory(tc -> makeMultilineCell(fontStyle));

        actionsCol.setCellFactory(tc -> new TableCell<ParentsPane.ParentRecord, Void>() {
            private final Button edit = new Button("Edit");
            private final Button del = new Button("Delete");

            {
                edit.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
                del.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");

                edit.setOnAction(e -> onEdit.accept(getCurrent()));
                del.setOnAction(e -> {
                    ParentsPane.ParentRecord current = getCurrent();
                    Alert alert = new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Delete parent record? This will NOT delete child records.",
                        ButtonType.YES,
                        ButtonType.NO
                    );
                    alert.setHeaderText("Confirm Delete");
                    alert.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            onDeleteConfirmed.accept(current);
                        }
                    });
                });
            }

            private ParentsPane.ParentRecord getCurrent() {
                return getTableView().getItems().get(getIndex());
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(new HBox(5, edit, del));
                }
            }
        });

        table.getColumns().clear();
        table.getColumns().setAll(Arrays.<TableColumn<ParentsPane.ParentRecord, ?>>asList(
            noCol,
            nameCol,
            relationshipCol,
            phoneCol,
            childNameCol,
            actionsCol
        ));
    }

    private static <T> TableCell<ParentsPane.ParentRecord, T> makeCell(String style) {
        return new TableCell<ParentsPane.ParentRecord, T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setAlignment(Pos.CENTER);
                setTextAlignment(TextAlignment.CENTER);
                setStyle(style + " -fx-alignment: CENTER;");
            }
        };
    }

    private static TableCell<ParentsPane.ParentRecord, String> makeMultilineCell(String style) {
        return new TableCell<ParentsPane.ParentRecord, String>() {
            private final Text text = new Text();

            {
                text.setStyle(style);
                text.setTextAlignment(TextAlignment.CENTER);
                text.wrappingWidthProperty().bind(widthProperty().subtract(12));
                setPrefHeight(Control.USE_COMPUTED_SIZE);
                setAlignment(Pos.CENTER);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(null);
                    setGraphic(null);
                } else {
                    text.setText(item);
                    setText(null);
                    setAlignment(Pos.CENTER);
                    setGraphic(text);
                }
            }
        };
    }
}