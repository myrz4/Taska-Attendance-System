package nfc;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

final class CRUDStaffDialogSupport {
    private CRUDStaffDialogSupport() {
    }

    static void showStaffDialog(StaffManagementView.Admin existing, boolean isNew, Runnable onSave) {
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

        ImageView imagePreview = new ImageView();
        imagePreview.setFitWidth(100);
        imagePreview.setFitHeight(120);

        Button uploadBtn = new Button("Upload Profile Picture");
        uploadBtn.setOnAction(e -> CRUDStaffImageSupport.handleUpload(dlg, profilePictureTf, imagePreview));

        if (!isNew && existing != null) {
            usernameTf.setText(existing.getUsername());
            usernameTf.setDisable(true);
            profilePictureTf.setText(existing.getProfilePicture());
            nameTf.setText(existing.getName());
            CRUDStaffImageSupport.loadExistingPreview(existing.getProfilePicture(), imagePreview);
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
                if (isNew) {
                    if (newPwInput.isEmpty()) {
                        new Alert(Alert.AlertType.ERROR, "Password is required for new staff.").showAndWait();
                        return null;
                    }
                    finalPassword = newPwInput;
                } else {
                    String existingPassword = existing == null ? "" : safeStr(existing.getPassword());
                    if (currentPwInput.isEmpty() && newPwInput.isEmpty()) {
                        finalPassword = existingPassword;
                    } else if (currentPwInput.isEmpty() || newPwInput.isEmpty()) {
                        new Alert(
                            Alert.AlertType.ERROR,
                            "To change password, please enter BOTH current and new password."
                        ).showAndWait();
                        return null;
                    } else if (!currentPwInput.equals(existingPassword)) {
                        new Alert(Alert.AlertType.ERROR, "Current password is incorrect.").showAndWait();
                        return null;
                    } else {
                        finalPassword = newPwInput;
                    }
                }

                return new StaffManagementView.Admin(
                    username,
                    finalPassword,
                    profilePictureTf.getText(),
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
                FirestoreRestClient client = FirestoreRest.forCurrentUser();

                if (isNew) {
                    FsDocument byId = client.getDocument("admins", admin.getUsername());
                    if (byId != null) {
                        new Alert(Alert.AlertType.ERROR,
                            "Admin account with username \"" + admin.getUsername() + "\" already exists.")
                            .showAndWait();
                        return;
                    }
                    for (FsDocument document : client.listDocuments("admins")) {
                        String username = document == null ? null : document.getString("username");
                        if (username != null && username.trim().equalsIgnoreCase(admin.getUsername())) {
                            new Alert(Alert.AlertType.ERROR,
                                "Admin account with username \"" + admin.getUsername() + "\" already exists.")
                                .showAndWait();
                            return;
                        }
                    }

                    Map<String, Object> doc = new HashMap<>();
                    doc.put("username", admin.getUsername());
                    doc.put("password", admin.getPassword());
                    doc.put("profilePicture", admin.getProfilePicture());
                    doc.put("name", admin.getName());
                    client.createDocumentWithId("admins", admin.getUsername(), doc);
                    System.out.println("✅ Added new staff: " + admin.getUsername());
                } else {
                    Map<String, Object> patch = new HashMap<>();
                    patch.put("password", admin.getPassword());
                    patch.put("profilePicture", admin.getProfilePicture());
                    patch.put("name", admin.getName());
                    client.patchDocumentMerge("admins", admin.getUsername(), patch);
                    System.out.println("✏️ Updated staff: " + admin.getUsername());
                }
                onSave.run();
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                new Alert(Alert.AlertType.ERROR, "Firestore REST Error: " + ex.getMessage()).showAndWait();
            }
        });
    }

    static void showDeleteAdminDialog(StaffManagementView.Admin admin, Runnable onDeleteSuccess) {
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

        dlg.setResultConverter(bt -> bt == deleteBtn ? passwordTf.getText().trim() : null);

        dlg.showAndWait().ifPresent(enteredPw -> {
            if (enteredPw.isEmpty()) {
                new Alert(Alert.AlertType.ERROR, "Password is required to delete your account.").showAndWait();
                return;
            }

            if (!enteredPw.equals(admin.getPassword())) {
                new Alert(Alert.AlertType.ERROR, "Incorrect password. Account not deleted.").showAndWait();
                return;
            }

            try {
                FirestoreRestClient client = FirestoreRest.forCurrentUser();
                client.deleteDocument("admins", admin.getUsername());

                new Alert(Alert.AlertType.INFORMATION, "Your admin account has been deleted.").showAndWait();
                System.out.println("🗑 Deleted admin: " + admin.getUsername());
                if (onDeleteSuccess != null) {
                    onDeleteSuccess.run();
                }
                System.exit(0);
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                new Alert(Alert.AlertType.ERROR, "Failed to delete account: " + ex.getMessage()).showAndWait();
            }
        });
    }

    private static String safeStr(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}