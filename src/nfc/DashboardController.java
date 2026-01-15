package nfc;

import javafx.fxml.FXML;
import javafx.scene.image.ImageView;

public class DashboardController {
    @FXML private ImageView cameraView;
    private FaceCameraService cam;

    public void initialize() {
        // DO NOT auto-start here
    }

    public void startCamera() {
        if (cam == null) {
            cam = new FaceCameraService();   // <-- no-args
            cam.setView(cameraView);         // <-- attach FXML ImageView
            cam.start(0);                    // <-- start device 0 (change if needed)
        }
    }

    public void stopCamera() {
        if (cam != null) {
            cam.stop();
            cam = null;
        }
    }
}