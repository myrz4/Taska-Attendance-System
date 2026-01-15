package nfc;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.util.Callback;
import javafx.stage.FileChooser;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;

/**
 * CRUDDialogs (Firestore Version)
 * Replaces MySQL logic with Firebase Firestore operations.
 */
public class CRUDDialogs {

    // ------------------ Child Dialog ------------------
    public static void showChildDialog(ChildrenView.Child existing,
                                       boolean isNew,
                                       Runnable onSave) {
        Dialog<ChildrenView.Child> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(isNew ? "Add New Child" : "Edit Child");

        TextField nameTf = new TextField();
        DatePicker dobPicker = new DatePicker();
        TextField parentNameTf = new TextField();
        TextField parentContactTf = new TextField();
        TextField uidTf = new TextField();

        if (!isNew && existing != null) {
            nameTf.setText(existing.getName());
            if (existing.getBirthDate() != null) {
                dobPicker.setValue(existing.getBirthDate());
            }
            parentNameTf.setText(existing.getParentName());
            parentContactTf.setText(existing.getParentContact());
            uidTf.setText(existing.getNfcUid());
            uidTf.setDisable(true);
        }

        VBox content = new VBox(10,
            new Label("Child Name:"), nameTf,
            new Label("Birth Date:"), dobPicker,
            new Label("Parent Name:"), parentNameTf,
            new Label("Parent Contact:"), parentContactTf,
            new Label("NFC UID:"), uidTf
        );
        content.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(content);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == saveType) {
                return new ChildrenView.Child(
                    nameTf.getText().trim(),
                    dobPicker.getValue(),
                    parentNameTf.getText().trim(),
                    parentContactTf.getText().trim(),
                    uidTf.getText().trim()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(child -> {
            try {
                Firestore db = FirestoreService.db();
                CollectionReference childrenRef = db.collection("children");

                if (isNew) {
                    // Add new child document
                    childrenRef.document(child.getNfcUid()).set(new java.util.HashMap<>() {{
                        put("name", child.getName());
                
                    String formattedBirthDate = "";
                    if (child.getBirthDate() != null) {
                        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
                            "EEE MMM dd yyyy HH:mm:ss 'GMT'+0800 '(Malaysia Time)'", java.util.Locale.ENGLISH);
                        java.util.Date parsedDate = java.util.Date.from(
                            child.getBirthDate().atStartOfDay(java.time.ZoneId.of("Asia/Kuala_Lumpur")).toInstant());
                        formattedBirthDate = fmt.format(parsedDate);
                    }

                    put("birthDate", formattedBirthDate);
            
                        put("parentName", child.getParentName());
                        put("parentContact", child.getParentContact());
                        put("nfc_uid", child.getNfcUid());
                    }}).get();
                    System.out.println("✅ Added new child: " + child.getName());
                }

                else {
                    // ✅ Update existing child directly using NFC UID as the document ID
                    childrenRef.document(child.getNfcUid()).set(new java.util.HashMap<>() {{
                        put("name", child.getName());

                        String formattedBirthDate = "";
                        if (child.getBirthDate() != null) {
                            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
                                "EEE MMM dd yyyy HH:mm:ss 'GMT'+0800 '(Malaysia Time)'", java.util.Locale.ENGLISH);
                            java.util.Date parsedDate = java.util.Date.from(
                                child.getBirthDate().atStartOfDay(java.time.ZoneId.of("Asia/Kuala_Lumpur")).toInstant());
                            formattedBirthDate = fmt.format(parsedDate);
                        }

                        put("birthDate", formattedBirthDate);
                        put("parentName", child.getParentName());
                        put("parentContact", child.getParentContact());
                    }}, SetOptions.merge()).get();

                    System.out.println("✏️ Updated child (UID = " + child.getNfcUid() + ")");
                }

                onSave.run();
            } catch (Exception ex) {
                ex.printStackTrace();
                new Alert(Alert.AlertType.ERROR, "Firestore Error: " + ex.getMessage()).showAndWait();
            }
        });
    }

    // ------------------ Generic Action Buttons ------------------
    public static <T> Callback<TableColumn<T, Void>, TableCell<T, Void>> createActionCell(
            Consumer<T> onEdit,
            Consumer<T> onDelete
    ) {
        return col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button delBtn  = new Button("Delete");

            {
                editBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
                delBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
                editBtn.setOnAction(e -> onEdit.accept(getCurrentItem()));
                delBtn.setOnAction(e -> onDelete.accept(getCurrentItem()));
                setGraphic(new HBox(5, editBtn, delBtn));
            }

            private T getCurrentItem() {
                return getTableView().getItems().get(getIndex());
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : getGraphic());
            }
        };
    }

    // ------------------ Staff Dialog (Firestore) ------------------
    public static void showStaffDialog(StaffManagementView.Admin existing,
                                       boolean isNew,
                                       Runnable onSave) {
        // 🔐 SECURITY CHECK: only allow editing own admin account
        if (!isNew && existing != null) {
            String loggedIn = UserSession.getUsername();
            if (!existing.getUsername().equals(loggedIn)) {
                new Alert(
                    Alert.AlertType.ERROR,
                    "You are not allowed to edit other admin accounts."
                ).showAndWait();
                return;
            }
        }
        Dialog<StaffManagementView.Admin> dlg = new Dialog<>();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle(isNew ? "Add New Staff" : "Edit Staff");

        TextField usernameTf = new TextField();

        PasswordField currentPasswordTf = new PasswordField();
        currentPasswordTf.setPromptText("Enter current password");

        PasswordField newPasswordTf = new PasswordField();
        newPasswordTf.setPromptText("Enter new password");

        TextField profilePictureTf = new TextField();
        profilePictureTf.setEditable(false);
        TextField nameTf = new TextField();

        // Profile picture preview
        ImageView imagePreview = new ImageView();
        imagePreview.setFitWidth(100);
        imagePreview.setFitHeight(120);

        Button uploadBtn = new Button("Upload Profile Picture");
        uploadBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Profile Picture");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
            );
            File file = fileChooser.showOpenDialog(dlg.getOwner());
            if (file != null) {
                try {
                    String destDir = "profile_pics";
                    Files.createDirectories(new File(destDir).toPath());
                    File destFile = new File(destDir, file.getName());
                    Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    profilePictureTf.setText(destFile.getName());
                    imagePreview.setImage(new Image(destFile.toURI().toString()));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        if (!isNew && existing != null) {
            usernameTf.setText(existing.getUsername());
            usernameTf.setDisable(true);
            profilePictureTf.setText(existing.getProfilePicture());
            nameTf.setText(existing.getName());
            if (existing.getProfilePicture() != null && !existing.getProfilePicture().isEmpty()) {
                File imgFile = new File("profile_pics", existing.getProfilePicture());
                if (imgFile.exists())
                    imagePreview.setImage(new Image(imgFile.toURI().toString()));
            }
        }

        VBox vb = new VBox(10,
            new Label("Username:"), usernameTf,
            new Label("Current Password:"), currentPasswordTf,
            new Label("New Password:"), newPasswordTf,
            new Label("Profile Picture:"), new HBox(10, uploadBtn, profilePictureTf),
            imagePreview,
            new Label("Name:"), nameTf
        );
        vb.setPadding(new Insets(20));
        dlg.getDialogPane().setContent(vb);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt == saveBtn) {

                String username = usernameTf.getText().trim();
                String name = nameTf.getText().trim();

                String currentPwInput = currentPasswordTf.getText().trim();
                String newPwInput = newPasswordTf.getText().trim();

                String finalPassword;

                // 🟢 ADD NEW STAFF → password MUST be provided
                if (isNew) {
                    if (newPwInput.isEmpty()) {
                        new Alert(
                            Alert.AlertType.ERROR,
                            "Password is required for new staff."
                        ).showAndWait();
                        return null;
                    }
                    finalPassword = newPwInput;
                }
                // 🟡 EDIT STAFF
                else {
                    // Case 1: both blank → keep old password
                    if (currentPwInput.isEmpty() && newPwInput.isEmpty()) {
                        finalPassword = existing.getPassword();
                    }
                    // Case 2: one blank → error
                    else if (currentPwInput.isEmpty() || newPwInput.isEmpty()) {
                        new Alert(
                            Alert.AlertType.ERROR,
                            "To change password, please enter BOTH current and new password."
                        ).showAndWait();
                        return null;
                    }
                    // Case 3: wrong current password
                    else if (!currentPwInput.equals(existing.getPassword())) {
                        new Alert(
                            Alert.AlertType.ERROR,
                            "Current password is incorrect."
                        ).showAndWait();
                        return null;
                    }
                    // Case 4: valid change
                    else {
                        finalPassword = newPwInput;
                    }
                }

                String profilePic = profilePictureTf.getText();

                return new StaffManagementView.Admin(
                    username,
                    finalPassword,
                    profilePic,
                    name
                );
            }
            return null;
        });

        dlg.showAndWait().ifPresent(admin -> {
            if (admin.getUsername() == null || admin.getUsername().isBlank()) {
                new Alert(Alert.AlertType.ERROR, "Username cannot be empty.").showAndWait();
                return;
            }
            try {
                Firestore db = FirestoreService.db();
                CollectionReference adminRef = db.collection("admins");

                if (isNew) {

                    // 🔍 CHECK DUPLICATE USERNAME
                    QuerySnapshot qs = adminRef
                            .whereEqualTo("username", admin.getUsername())
                            .get()
                            .get();

                    if (!qs.isEmpty()) {
                        // ❌ DUPLICATE FOUND
                        new Alert(
                            Alert.AlertType.ERROR,
                            "Admin account with username \"" + admin.getUsername() + "\" already exists."
                        ).showAndWait();
                        return;
                    }

                    adminRef.document(admin.getUsername())
                        .set(new HashMap<>() {{
                            put("username", admin.getUsername());
                            put("password", admin.getPassword());
                            put("profilePicture", admin.getProfilePicture());
                            put("name", admin.getName());
                        }})
                        .get();

                    System.out.println("✅ Added new staff: " + admin.getUsername());
                }
                else {
                    adminRef.document(admin.getUsername())
                        .set(new HashMap<>() {{
                            put("password", admin.getPassword());
                            put("profilePicture", admin.getProfilePicture());
                            put("name", admin.getName());
                        }}, SetOptions.merge())
                        .get();

                    System.out.println("✏️ Updated staff: " + admin.getUsername());
                }
                onSave.run();
            } catch (Exception ex) {
                ex.printStackTrace();
                new Alert(Alert.AlertType.ERROR, "Firestore Error: " + ex.getMessage()).showAndWait();
            }
        });
    }

    public static void showDeleteAdminDialog(
            StaffManagementView.Admin admin,
            Runnable onDeleteSuccess
    ) {
        Dialog<String> dlg = new Dialog<>();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Delete Admin Account");

        PasswordField passwordTf = new PasswordField();
        passwordTf.setPromptText("Enter your password to confirm");

        VBox content = new VBox(10,
            new Label("Enter your password to delete your account:"),
            passwordTf
        );
        content.setPadding(new Insets(20));

        dlg.getDialogPane().setContent(content);

        ButtonType deleteBtn = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(deleteBtn, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt == deleteBtn) {
                return passwordTf.getText().trim();
            }
            return null;
        });

        dlg.showAndWait().ifPresent(enteredPw -> {
            // ❌ Empty password
            if (enteredPw.isEmpty()) {
                new Alert(
                    Alert.AlertType.ERROR,
                    "Password is required to delete your account."
                ).showAndWait();
                return;
            }

            // ❌ Wrong password
            if (!enteredPw.equals(admin.getPassword())) {
                new Alert(
                    Alert.AlertType.ERROR,
                    "Incorrect password. Account not deleted."
                ).showAndWait();
                return;
            }

            try {
                Firestore db = FirestoreService.db();

                db.collection("admins")
                .document(admin.getUsername())
                .delete()
                .get();

                new Alert(
                    Alert.AlertType.INFORMATION,
                    "Your admin account has been deleted."
                ).showAndWait();

                System.out.println("🗑 Deleted admin: " + admin.getUsername());

                // 🔥 Optional: force logout / close app
                System.exit(0);

            } catch (Exception ex) {
                ex.printStackTrace();
                new Alert(
                    Alert.AlertType.ERROR,
                    "Failed to delete account: " + ex.getMessage()
                ).showAndWait();
            }
        });
    }
}