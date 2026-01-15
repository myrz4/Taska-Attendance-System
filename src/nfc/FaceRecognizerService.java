package nfc;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.face.LBPHFaceRecognizer;

import java.io.IOException;
import java.util.Map;

public class FaceRecognizerService {

    private static final String MODEL_FILE = "model/lbph.yml";
    private static final double THRESHOLD = 55.0; // tune later

    private final LBPHFaceRecognizer recognizer;
    private final Map<Integer, String> labels;

    public FaceRecognizerService() {
        OpenCVLoader.load();
        recognizer = LBPHFaceRecognizer.create();
        recognizer.read(MODEL_FILE);
        try {
            labels = FaceTrainer.loadLabels();
        } catch (IOException e) {
            throw new RuntimeException("Labels not found. Train first.", e);
        }
    }

    /** Returns predicted userId and confidence (lower = better). */
    public Prediction predict(Mat bgrFrame, Rect faceRect) {
        Mat gray = new Mat();
        Imgproc.cvtColor(bgrFrame, gray, Imgproc.COLOR_BGR2GRAY);
        Mat face = new Mat(gray, faceRect);
        Imgproc.resize(face, face, new Size(200, 200));

        int[] label = new int[1];
        double[] conf = new double[1];
        recognizer.predict(face, label, conf);

        String userId = labels.getOrDefault(label[0], "UNKNOWN");
        return new Prediction(userId, conf[0], conf[0] <= THRESHOLD);
    }

    public record Prediction(String userId, double confidence, boolean accepted) {}
}