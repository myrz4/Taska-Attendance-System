package nfc;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

@SuppressWarnings({"unused", "java:S1301"})
final class AdminDashboardUtilitySupport {
    private static volatile boolean missingAssetsWarningShown = false;

    interface ErrorLogger {
        void log(String context, Exception error);
    }

    private AdminDashboardUtilitySupport() {
    }

    @SuppressWarnings("unused")
    static void showRegisterForm(String tagId, ErrorLogger logger) {
        Stage stage = new Stage();
        stage.setTitle("Register New Child");

        Label nameLabel = new Label("Child's Name:");
        TextField nameField = new TextField();

        Label parentContactLabel = new Label("Parent Contact:");
        TextField parentContactField = new TextField();

        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> {
            String name = nameField.getText().trim();
            String parentContact = parentContactField.getText().trim();

            if (name.isEmpty() || parentContact.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Please fill all fields.");
                alert.showAndWait();
                return;
            }

            try {
                FirestoreRestClient client = FirestoreRest.forCurrentUser();

                int nextId = 1;
                for (FsDocument kid : client.listDocuments("children")) {
                    Long value = kid.getLong("child_id");
                    if (value != null) {
                        nextId = Math.max(nextId, Math.toIntExact(value + 1L));
                    }
                }

                Map<String, Object> data = new HashMap<>();
                data.put("child_id", nextId);
                data.put("name", name);
                data.put("parent_contact", parentContact);
                data.put("nfc_uid", tagId == null ? "" : tagId.trim().toUpperCase());

                client.addDocumentAutoId("children", data);

                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Child registered successfully!");
                alert.showAndWait();
                stage.close();

                Platform.runLater(FirestoreService::safeRefresh);
            } catch (IOException | InterruptedException ex) {
                logger.log("child registration failed", ex);
            }
        });

        VBox form = new VBox(10, nameLabel, nameField, parentContactLabel, parentContactField, saveButton);
        form.setPadding(new Insets(20));
        form.setAlignment(Pos.CENTER);

        BorderPane root = new BorderPane();
        root.setCenter(form);

        Scene scene = new Scene(root, 800, 400);
        stage.setScene(scene);
        stage.show();
    }

    @SuppressWarnings({"unused", "java:S1301"})
    static void handleNfcAttendance(String nfcUid, ErrorLogger logger) {
        String uid = NFCAttendanceSupport.normalizeUid(nfcUid);
        if (uid.isEmpty()) {
            Platform.runLater(() -> showAlert("⚠ Invalid NFC UID", Alert.AlertType.WARNING));
            return;
        }

        try {
            NFCAttendanceSupport.AttendanceUpdateResult result = NFCAttendanceSupport.submitCheckIn(uid, UserSession.getName());
            switch (result.status()) {
                case UNKNOWN_CARD:
                    Platform.runLater(() -> showAlert("⚠ This card is not registered!", Alert.AlertType.WARNING));
                    return;
                case CHECKED_IN:
                    AttendanceView.markPresentFromNfcScan(result.childId(), result.childName(), result.normalizedUid());
                    Platform.runLater(() -> showAlert("✅ Check-in successful for " + result.childName(), Alert.AlertType.INFORMATION));
                    break;
                case ALREADY_OPEN:
                    Platform.runLater(() -> showAlert(
                        result.childName() + " is already checked in. Use parent QR pickup in Teacher App. If QR is unavailable, use the existing manual checkout override.",
                        Alert.AlertType.INFORMATION
                    ));
                    break;
                case ALREADY_CLOSED:
                    Platform.runLater(() -> showAlert("⚠ Already checked out today for " + result.childName(), Alert.AlertType.WARNING));
                    break;
                default:
                    Platform.runLater(() -> showAlert("❌ Attendance update failed: " + result.reason(), Alert.AlertType.ERROR));
                    break;
            }
            Platform.runLater(FirestoreService::refreshAfterAttendanceMutation);
        } catch (RuntimeException | IOException | InterruptedException ex) {
            logger.log("handle NFC attendance", ex);
            String errorMessage = "❌ Firestore error: " + ex.getMessage();
            Platform.runLater(() -> showAlert(errorMessage, Alert.AlertType.ERROR));
        }
    }

    @SuppressWarnings("unused")
    static void showToast(Stage owner, String message) {
        Label toastLabel = new Label(message);
        toastLabel.setStyle("-fx-background-color: #323232; -fx-text-fill: white; -fx-padding: 16px 32px; -fx-background-radius: 32px; -fx-font-size: 20px; -fx-font-weight: bold;");
        toastLabel.setOpacity(0);

        StackPane root = (StackPane) owner.getScene().getRoot();
        root.getChildren().add(toastLabel);
        StackPane.setAlignment(toastLabel, Pos.CENTER);

        Timeline fadeIn = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(toastLabel.opacityProperty(), 0)),
            new KeyFrame(Duration.seconds(0.2), new KeyValue(toastLabel.opacityProperty(), 1))
        );
        Timeline stay = new Timeline(new KeyFrame(Duration.seconds(2)));
        Timeline fadeOut = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(toastLabel.opacityProperty(), 1)),
            new KeyFrame(Duration.seconds(0.5), new KeyValue(toastLabel.opacityProperty(), 0))
        );

        fadeIn.setOnFinished(e -> stay.play());
        stay.setOnFinished(e -> fadeOut.play());
        fadeOut.setOnFinished(e -> root.getChildren().remove(toastLabel));
        fadeIn.play();
    }

    @SuppressWarnings("unused")
    static Image loadSafe(Class<?> resourceOwner, String fileName, ErrorLogger logger) {
        try {
            java.net.URL url = resourceOwner.getResource("/nfc/" + fileName);
            if (url != null) {
                return new Image(url.toExternalForm());
            }

            File localFile = new File("src/nfc/" + fileName);
            if (localFile.exists()) {
                return new Image(localFile.toURI().toString());
            }

            System.err.println("⚠️ Missing image: " + fileName);
            if (!missingAssetsWarningShown) {
                missingAssetsWarningShown = true;
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Missing Assets");
                    alert.setHeaderText("Some images could not be loaded");
                    alert.setContentText(
                        "Some UI images are missing from the classpath. "
                            + "If you are running from source, rebuild to copy assets into bin/nfc."
                    );
                    alert.show();

                    PauseTransition delay = new PauseTransition(Duration.seconds(4));
                    delay.setOnFinished(e -> alert.close());
                    delay.play();
                });
            }

            return new Image("https://via.placeholder.com/60x60.png?text=Missing");
        } catch (Exception ex) {
            logger.log("loadSafe image fallback", ex);
            return new Image("https://via.placeholder.com/60x60.png?text=Error");
        }
    }

    @SuppressWarnings("unused")
    static int[] getTodayStats() throws Exception {
        FirestoreRestClient client = FirestoreRest.forCurrentUser();
        AdminDashboardRefreshDataSupport.DashboardRefreshSnapshot snapshot = AdminDashboardRefreshDataSupport.collectSnapshot(client);
        return new int[]{snapshot.presentCount, snapshot.absentCount};
    }

    private static void showAlert(String msg, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(type == Alert.AlertType.ERROR ? "Error" : "Scan Successful");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.show();

        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(e -> alert.close());
        delay.play();
    }
}