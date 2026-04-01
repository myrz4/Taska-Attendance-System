package nfc;

import java.time.LocalDate;
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
final class ChildrenTableSupport {
    private ChildrenTableSupport() {}

    static void setupTable(
        TableView<ChildrenView.Child> table,
        Consumer<ChildrenView.Child> onEdit,
        Consumer<ChildrenView.Child> onDeleteConfirmed
    ) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ChildrenView.Child, Integer> idCol = new TableColumn<>("No");
        TableColumn<ChildrenView.Child, String> nameCol = new TableColumn<>("Name");
        TableColumn<ChildrenView.Child, LocalDate> dobCol = new TableColumn<>("Birth Date");
        TableColumn<ChildrenView.Child, String> parentNameCol = new TableColumn<>("Parent Name");
        TableColumn<ChildrenView.Child, String> relationshipCol = new TableColumn<>("Relationship");
        TableColumn<ChildrenView.Child, String> parentContactCol = new TableColumn<>("Parent Contact");
        TableColumn<ChildrenView.Child, String> uidCol = new TableColumn<>("NFC UID");
        TableColumn<ChildrenView.Child, Void> actionsCol = new TableColumn<>("Actions");

        idCol.setCellFactory(col -> new TableCell<ChildrenView.Child, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else {
                    setText(String.valueOf(getIndex() + 1));
                }
                setStyle("-fx-font-weight: bold; -fx-text-fill: #181818;");
                setAlignment(Pos.CENTER);
            }
        });

        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        dobCol.setCellValueFactory(new PropertyValueFactory<>("birthDate"));
        parentNameCol.setCellValueFactory(new PropertyValueFactory<>("parentName"));
        relationshipCol.setCellValueFactory(new PropertyValueFactory<>("parentRelationship"));
        parentContactCol.setCellValueFactory(new PropertyValueFactory<>("parentContact"));
        uidCol.setCellValueFactory(new PropertyValueFactory<>("nfcUid"));

        idCol.setStyle("-fx-alignment: CENTER;");
        nameCol.setStyle("-fx-alignment: CENTER;");
        dobCol.setStyle("-fx-alignment: CENTER;");
        parentNameCol.setStyle("-fx-alignment: CENTER;");
        relationshipCol.setStyle("-fx-alignment: CENTER;");
        parentContactCol.setStyle("-fx-alignment: CENTER;");
        uidCol.setStyle("-fx-alignment: CENTER;");
        actionsCol.setStyle("-fx-alignment: CENTER;");

        String fontStyle = "-fx-font-family: 'Poppins', 'Arial', sans-serif; -fx-font-size: 13px;-fx-font-weight: bold; -fx-text-fill: #181818;";

        nameCol.setCellFactory(tc -> makeCell(fontStyle));
        dobCol.setCellFactory(tc -> makeCell(fontStyle));
        parentNameCol.setCellFactory(tc -> makeMultilineCell(fontStyle));
        relationshipCol.setCellFactory(tc -> makeMultilineCell(fontStyle));
        parentContactCol.setCellFactory(tc -> makeMultilineCell(fontStyle));
        uidCol.setCellFactory(tc -> makeCell(fontStyle));

        actionsCol.setCellFactory(tc -> new TableCell<ChildrenView.Child, Void>() {
            private final Button edit = new Button("Edit");
            private final Button del = new Button("Delete");

            {
                edit.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
                del.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");

                edit.setOnAction(e -> onEdit.accept(getCurrent()));
                del.setOnAction(e -> {
                    ChildrenView.Child current = getCurrent();
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete child and all their attendance records?", ButtonType.YES, ButtonType.NO);
                    alert.setHeaderText("Confirm Delete");
                    alert.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            onDeleteConfirmed.accept(current);
                        }
                    });
                });
            }

            private ChildrenView.Child getCurrent() {
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
        table.getColumns().setAll(Arrays.<TableColumn<ChildrenView.Child, ?>>asList(
            idCol,
            nameCol,
            dobCol,
            parentNameCol,
            relationshipCol,
            parentContactCol,
            uidCol,
            actionsCol
        ));
    }

    private static <T> TableCell<ChildrenView.Child, T> makeCell(String style) {
        return new TableCell<ChildrenView.Child, T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setStyle(style);
                setAlignment(Pos.CENTER);
            }
        };
    }

    private static TableCell<ChildrenView.Child, String> makeMultilineCell(String style) {
        return new TableCell<ChildrenView.Child, String>() {
            private final Text text = new Text();

            {
                text.setStyle(style);
                text.setTextAlignment(TextAlignment.CENTER);
                text.wrappingWidthProperty().bind(widthProperty().subtract(12));
                setPrefHeight(Control.USE_COMPUTED_SIZE);
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
                    setGraphic(text);
                }
                setAlignment(Pos.CENTER);
            }
        };
    }
}