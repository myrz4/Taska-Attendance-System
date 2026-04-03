package nfc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

@SuppressWarnings("all")
final class CRUDStaffImageSupport {
    private CRUDStaffImageSupport() {
    }

    @SuppressWarnings("unused")
    static void handleUpload(Dialog<?> dialog, TextField profilePictureTf, ImageView imagePreview) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Picture");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        File file = fileChooser.showOpenDialog(dialog.getOwner());
        if (file == null) {
            return;
        }

        try {
            String destDir = "profile_pics";
            Files.createDirectories(new File(destDir).toPath());
            File destFile = new File(destDir, file.getName());
            Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            profilePictureTf.setText(destFile.getName());
            imagePreview.setImage(new Image(destFile.toURI().toString()));
        } catch (IOException | SecurityException ex) {
            new Alert(Alert.AlertType.ERROR, "Failed to copy profile picture: " + ex.getMessage()).showAndWait();
        }
    }

    @SuppressWarnings("unused")
    static void loadExistingPreview(String profilePicture, ImageView imagePreview) {
        if (profilePicture == null || profilePicture.isEmpty()) {
            return;
        }
        File imageFile = new File("profile_pics", profilePicture);
        if (imageFile.exists()) {
            imagePreview.setImage(new Image(imageFile.toURI().toString()));
        }
    }
}