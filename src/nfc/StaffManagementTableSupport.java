package nfc;

import java.io.File;
import java.util.Arrays;
import java.util.function.Consumer;

import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

final class StaffManagementTableSupport {
    private StaffManagementTableSupport() {
    }

    interface StaffActions {
        void edit(StaffManagementView.Admin admin);

        void delete(StaffManagementView.Admin admin);
    }

    static void setupTable(
        TableView<StaffManagementView.Admin> table,
        ObservableList<StaffManagementView.Admin> data,
        StaffActions actions
    ) {
        table.setItems(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<StaffManagementView.Admin, String> usernameCol = new TableColumn<>("Username");
        TableColumn<StaffManagementView.Admin, String> passwordCol = new TableColumn<>("Password");
        TableColumn<StaffManagementView.Admin, String> profilePictureCol = new TableColumn<>("Profile Picture");
        TableColumn<StaffManagementView.Admin, String> nameCol = new TableColumn<>("Name");
        TableColumn<StaffManagementView.Admin, Void> actCol = new TableColumn<>("Actions");

        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        passwordCol.setCellValueFactory(new PropertyValueFactory<>("password"));
        profilePictureCol.setCellValueFactory(new PropertyValueFactory<>("profilePicture"));
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        String fontStyle = "-fx-font-family: 'Poppins', 'Arial', sans-serif; -fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #181818;";

        usernameCol.setCellFactory(tc -> textCell(fontStyle, false));
        passwordCol.setCellFactory(tc -> textCell(fontStyle, true));
        profilePictureCol.setCellFactory(tc -> imageCell());
        nameCol.setCellFactory(tc -> textCell(fontStyle, false));
        actCol.setCellFactory(tc -> actionCell(actions));

        table.getColumns().clear();
        table.getColumns().addAll(Arrays.asList(
            usernameCol,
            passwordCol,
            profilePictureCol,
            nameCol,
            actCol
        ));
    }

    private static TableCell<StaffManagementView.Admin, String> textCell(String fontStyle, boolean maskPassword) {
        return new TableCell<StaffManagementView.Admin, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else if (maskPassword) {
                    setText("*".repeat(8));
                } else {
                    setText(item);
                }
                setStyle(fontStyle);
            }
        };
    }

    private static TableCell<StaffManagementView.Admin, String> imageCell() {
        return new TableCell<StaffManagementView.Admin, String>() {
            private final int imageSize = 44;

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                ImageView imageView = new ImageView();
                imageView.setFitHeight(imageSize);
                imageView.setFitWidth(imageSize);
                imageView.setPreserveRatio(true);

                String value = item.trim();
                if (ImageCache.isRemoteUrl(value)) {
                    imageView.setImage(ImageCache.loadCachedOrRemote(value, imageSize, imageSize));
                    setGraphic(imageView);
                    setText(null);
                    return;
                }

                File imageFile = new File("profile_pics/" + value);
                if (imageFile.exists()) {
                    imageView.setImage(new Image(imageFile.toURI().toString()));
                    setGraphic(imageView);
                    setText(null);
                } else {
                    setGraphic(null);
                    setText("No image");
                }
            }
        };
    }

    private static TableCell<StaffManagementView.Admin, Void> actionCell(StaffActions actions) {
        return new TableCell<StaffManagementView.Admin, Void>() {
            private final Button edit = new Button("Edit");
            private final Button del = new Button("Delete");
            private final HBox actionsBox = new HBox(5, edit, del);

            {
                String btnStyle =
                    "-fx-background-color: #FFCB3C;" +
                    "-fx-font-size: 16px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-text-fill: #222;" +
                    "-fx-background-radius: 28px;";

                edit.setStyle(btnStyle);
                del.setStyle(btnStyle);

                edit.setOnAction(e -> actions.edit(getCurrent()));
                del.setOnAction(e -> actions.delete(getCurrent()));
            }

            private StaffManagementView.Admin getCurrent() {
                return getTableView().getItems().get(getIndex());
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }

                StaffManagementView.Admin admin = getCurrent();
                String loggedIn = UserSession.getUsername();
                boolean canManage = admin.getUsername() != null && admin.getUsername().equals(loggedIn);
                edit.setVisible(canManage);
                edit.setManaged(canManage);
                del.setVisible(canManage);
                del.setManaged(canManage);
                setGraphic(actionsBox);
            }
        };
    }
}