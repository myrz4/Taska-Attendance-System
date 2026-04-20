package nfc;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

@SuppressWarnings("unused")
final class StaffManagementTableSupport {
    private static final TableColumn<StaffManagementView.Admin, String> EMAIL_COL = new TableColumn<>("Email");
    private static final TableColumn<StaffManagementView.Admin, String> PHONE_COL = new TableColumn<>("Phone");
    private static final TableColumn<StaffManagementView.Admin, String> LAST_LOGIN_COL = new TableColumn<>("Last Login");
    private static final TableColumn<StaffManagementView.Admin, String> RECORD_ID_COL = new TableColumn<>("Record ID");

    private StaffManagementTableSupport() {
    }

    interface StaffActions {
        void edit(StaffManagementView.Admin admin);

        void delete(StaffManagementView.Admin admin);
    }

    static TableBundle setupTable(
        TableView<StaffManagementView.Admin> table,
        StaffActions actions
    ) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<StaffManagementView.Admin, Integer> noCol = new TableColumn<>("No");
        TableColumn<StaffManagementView.Admin, String> profilePictureCol = new TableColumn<>("Avatar");
        TableColumn<StaffManagementView.Admin, String> nameCol = new TableColumn<>("Full Name");
        TableColumn<StaffManagementView.Admin, String> usernameCol = new TableColumn<>("Username");
        TableColumn<StaffManagementView.Admin, String> roleCol = new TableColumn<>("Role");
        TableColumn<StaffManagementView.Admin, String> statusCol = new TableColumn<>("Status");
        TableColumn<StaffManagementView.Admin, Void> actCol = new TableColumn<>("Actions");

        noCol.setCellFactory(col -> new TableCell<StaffManagementView.Admin, Integer>() {
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

        profilePictureCol.setCellValueFactory(new PropertyValueFactory<>("profilePicture"));
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        roleCol.setCellValueFactory(new PropertyValueFactory<>("role"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        EMAIL_COL.setCellValueFactory(new PropertyValueFactory<>("email"));
        PHONE_COL.setCellValueFactory(new PropertyValueFactory<>("phone"));
        LAST_LOGIN_COL.setCellValueFactory(new PropertyValueFactory<>("lastLogin"));
        RECORD_ID_COL.setCellValueFactory(new PropertyValueFactory<>("recordId"));

        EMAIL_COL.setVisible(false);
        PHONE_COL.setVisible(false);
        LAST_LOGIN_COL.setVisible(false);
        RECORD_ID_COL.setVisible(false);

        profilePictureCol.setCellFactory(tc -> imageCell());
        nameCol.setCellFactory(tc -> copyCell(StaffManagementView.Admin::getName, Pos.CENTER_LEFT));
        usernameCol.setCellFactory(tc -> copyCell(StaffManagementView.Admin::getUsername, Pos.CENTER_LEFT));
        roleCol.setCellFactory(tc -> copyCell(StaffManagementView.Admin::getRole, Pos.CENTER));
        statusCol.setCellFactory(tc -> copyCell(StaffManagementView.Admin::getStatus, Pos.CENTER));
        EMAIL_COL.setCellFactory(tc -> copyCell(StaffManagementView.Admin::getEmail, Pos.CENTER_LEFT));
        PHONE_COL.setCellFactory(tc -> copyCell(StaffManagementView.Admin::getPhone, Pos.CENTER_LEFT));
        LAST_LOGIN_COL.setCellFactory(tc -> copyCell(StaffManagementView.Admin::getLastLogin, Pos.CENTER_LEFT));
        RECORD_ID_COL.setCellFactory(tc -> copyCell(StaffManagementView.Admin::getRecordId, Pos.CENTER_LEFT));
        actCol.setCellFactory(tc -> new ActionButtonsTableCell<>(
            ActionButtonsTableCell.ActionSpec.normal("Edit", StaffManagementTableSupport::canManage, actions::edit),
            ActionButtonsTableCell.ActionSpec.destructive("Delete", StaffManagementTableSupport::canManage, actions::delete)
        ));

        setPrefWidth(noCol, 56);
        setPrefWidth(profilePictureCol, 86);
        setPrefWidth(nameCol, 200);
        setPrefWidth(usernameCol, 160);
        setPrefWidth(roleCol, 110);
        setPrefWidth(statusCol, 96);
        setPrefWidth(EMAIL_COL, 200);
        setPrefWidth(PHONE_COL, 140);
        setPrefWidth(LAST_LOGIN_COL, 150);
        setPrefWidth(RECORD_ID_COL, 180);
        setPrefWidth(actCol, 148);

        table.getColumns().clear();
        table.getColumns().setAll(Arrays.<TableColumn<StaffManagementView.Admin, ?>>asList(
            noCol,
            profilePictureCol,
            nameCol,
            usernameCol,
            roleCol,
            statusCol,
            EMAIL_COL,
            PHONE_COL,
            LAST_LOGIN_COL,
            RECORD_ID_COL,
            actCol
        ));

        SummaryTableSupport.configureSummaryTable(
            table,
            "No admins found.",
            StaffManagementTableSupport::rowSummary,
            StaffManagementView.Admin::getRecordId,
            StaffManagementTableSupport::asJson,
            admin -> {
                if (canManage(admin)) {
                    actions.edit(admin);
                }
            }
        );

        return new TableBundle(nameCol, List.of(EMAIL_COL, PHONE_COL, LAST_LOGIN_COL, RECORD_ID_COL));
    }

    private static CopyableTableCell<StaffManagementView.Admin> copyCell(
        Function<StaffManagementView.Admin, String> valueProvider,
        Pos alignment
    ) {
        return new CopyableTableCell<>(
            valueProvider,
            SummaryTableSupport::displayText,
            StaffManagementTableSupport::rowSummary,
            StaffManagementView.Admin::getRecordId,
            admin -> SummaryTableSupport.toPrettyJson(asJson(admin)),
            alignment,
            false
        );
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

    private static boolean canManage(StaffManagementView.Admin admin) {
        return admin != null && admin.getUsername() != null && admin.getUsername().equals(UserSession.getUsername());
    }

    private static LinkedHashMap<String, Object> asJson(StaffManagementView.Admin admin) {
        LinkedHashMap<String, Object> json = new LinkedHashMap<>();
        json.put("recordId", admin.getRecordId());
        json.put("name", admin.getName());
        json.put("username", admin.getUsername());
        json.put("role", admin.getRole());
        json.put("status", admin.getStatus());
        json.put("email", admin.getEmail());
        json.put("phone", admin.getPhone());
        json.put("lastLogin", admin.getLastLogin());
        json.put("profilePicture", admin.getProfilePicture());
        return json;
    }

    private static String rowSummary(StaffManagementView.Admin admin) {
        if (admin == null) {
            return "";
        }
        return String.join("\n",
            "Admin: " + SummaryTableSupport.displayText(admin.getName()),
            "Record ID: " + SummaryTableSupport.displayText(admin.getRecordId()),
            "Username: " + SummaryTableSupport.displayText(admin.getUsername()),
            "Role: " + SummaryTableSupport.displayText(admin.getRole()),
            "Status: " + SummaryTableSupport.displayText(admin.getStatus()),
            "Email: " + SummaryTableSupport.displayText(admin.getEmail()),
            "Phone: " + SummaryTableSupport.displayText(admin.getPhone()),
            "Last Login: " + SummaryTableSupport.displayText(admin.getLastLogin())
        );
    }

    private static void setPrefWidth(TableColumn<StaffManagementView.Admin, ?> column, double width) {
        column.setPrefWidth(width);
    }

    static final class TableBundle {
        final TableColumn<StaffManagementView.Admin, String> nameCol;
        final List<TableColumn<StaffManagementView.Admin, ?>> optionalColumns;

        TableBundle(
            TableColumn<StaffManagementView.Admin, String> nameCol,
            List<TableColumn<StaffManagementView.Admin, ?>> optionalColumns
        ) {
            this.nameCol = nameCol;
            this.optionalColumns = optionalColumns;
        }
    }
}
