package nfc;

import org.opencv.core.*;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class FaceDetector {
    private final CascadeClassifier detector;

    private final Mat gray = new Mat();
    private final MatOfRect mor = new MatOfRect();

    public FaceDetector() {
        try (InputStream in = getClass().getResourceAsStream("/nfc/haarcascade_frontalface_default.xml")) {
            if (in == null) throw new IllegalStateException("Cascade not found in resources");
            Path temp = Files.createTempFile("haarcascade_frontalface_default", ".xml");
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            detector = new CascadeClassifier(temp.toString());
            if (detector.empty()) throw new RuntimeException("Failed to load Haar cascade");
            temp.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException("Error loading cascade", e);
        }
    }

    public List<Rect> detectFaces(Mat bgrFrame) {
        if (bgrFrame == null || bgrFrame.empty()) return List.of();

        Imgproc.cvtColor(bgrFrame, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(gray, gray);

        Size minSize = new Size(
            Math.max(40, bgrFrame.cols() / 12.0),
            Math.max(40, bgrFrame.rows() / 12.0)
        );

        detector.detectMultiScale(gray, mor, 1.1, 4, 0, minSize, new Size());
        return Arrays.asList(mor.toArray());
    }

    public void release() {
        gray.release();
        mor.release();
    }
}