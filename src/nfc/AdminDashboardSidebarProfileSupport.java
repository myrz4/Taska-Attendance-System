package nfc;

import java.io.File;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

@SuppressWarnings("unused")
final class AdminDashboardSidebarProfileSupport {
    private AdminDashboardSidebarProfileSupport() {
    }

    @SuppressWarnings("unused")
    static VBox createProfileBox() {
        ImageView profileView = createProfileView();
        Label nameLabel = new Label(resolveDisplayName());
        nameLabel.getStyleClass().add("sidebar-profile-name");

        ImageView beeIcon = new ImageView(ImageLoader.loadSafe("bee-icon.png"));
        beeIcon.setFitWidth(24);
        beeIcon.setFitHeight(24);
        beeIcon.getStyleClass().add("sidebar-profile-accent");

        VBox profileBox = new VBox(10, profileView, createWelcomeBox(nameLabel, beeIcon));
        profileBox.setAlignment(Pos.CENTER);
        profileBox.getStyleClass().add("sidebar-profile-box");
        return profileBox;
    }

    static ImageView createProfileView() {
        ImageView profileView = new ImageView();
        profileView.setFitWidth(130);
        profileView.setFitHeight(130);
        profileView.setPreserveRatio(true);

        Circle clip = new Circle();
        clip.centerXProperty().bind(profileView.fitWidthProperty().divide(2));
        clip.centerYProperty().bind(profileView.fitHeightProperty().divide(2));
        clip.radiusProperty().bind(profileView.fitWidthProperty().divide(2));
        profileView.setClip(clip);
        profileView.getStyleClass().add("sidebar-profile-image");
        profileView.setStyle(
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0.2, 0, 3); "
                + "-fx-border-color: #FFD700; -fx-border-width: 3; -fx-border-radius: 65px;"
        );

        profileView.setImage(resolveProfileImage());
        return profileView;
    }

    private static HBox createWelcomeBox(Label nameLabel, ImageView beeIcon) {
        HBox welcomeBox = new HBox(6, nameLabel, beeIcon);
        welcomeBox.setAlignment(Pos.CENTER);
        welcomeBox.getStyleClass().add("sidebar-welcome-box");
        return welcomeBox;
    }

    private static Image resolveProfileImage() {
        String pic = UserSession.getProfilePicture();
        if (pic != null && !pic.isBlank()) {
            String value = pic.trim();
            if (ImageCache.isRemoteUrl(value)) {
                Image image = ImageCache.loadCachedOrRemote(value, 130, 130);
                ImageCache.prefetch(value);
                return image;
            }

            File imgFile = new File("profile_pics", value);
            if (imgFile.exists()) {
                return new Image(imgFile.toURI().toString());
            }
        }
        return ImageLoader.loadSafe("default_user.png");
    }

    private static String resolveDisplayName() {
        String displayName = UserSession.getName();
        if (displayName == null || displayName.isBlank()) {
            displayName = UserSession.getUsername();
        }
        return displayName;
    }
}