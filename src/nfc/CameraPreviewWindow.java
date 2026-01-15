package nfc;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class CameraPreviewWindow {
    private final Stage stage = new Stage();
    private final ImageView view = new ImageView();
    private FaceCameraService service;

    public CameraPreviewWindow(Stage owner) {
        view.setFitWidth(640);
        view.setPreserveRatio(true);

        StackPane root = new StackPane(view);
        root.setPadding(new Insets(10));
        Scene scene = new Scene(root, 680, 520);

        stage.initOwner(owner);
        stage.initModality(Modality.NONE); // do not block dashboard
        stage.setTitle("Camera Preview");
        stage.setScene(scene);

        // always stop camera when user closes the window
        stage.setOnCloseRequest(e -> stop());
    }

    /** Show camera index (usually 0). */
    public void show(int camIndex) {
        System.out.println("[CameraPreviewWindow] show(" + camIndex + ")");
        // stop any previous service to avoid double-grab
        if (service != null) { service.stop(); service = null; }

        service = new FaceCameraService();   // <-- no-args
        service.setView(view);               // <-- attach the ImageView
        service.start(camIndex);             // <-- start with the index you want (e.g., 0)

        stage.show();
        stage.toFront();
    }

    /** Stop camera & hide window. */
    public void stop() {
        System.out.println("[CameraPreviewWindow] stop()");
        if (service != null) {
            try { service.stop(); } catch (Exception ignore) {}
            service = null;
        }
        try { stage.hide(); } catch (Exception ignore) {}
    }
    
    public ImageView getView() {
        return this.view;
    }
}