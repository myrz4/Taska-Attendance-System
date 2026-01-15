package nfc;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public final class FastMatFx {
    private FastMatFx() {}

    /** Convert BGR Mat (from VideoCapture) to JavaFX WritableImage without PNG encode. */
    public static WritableImage toImage(Mat bgr) {
        if (bgr == null || bgr.empty()) return null;

        Mat src = bgr;
        Mat converted = null;
        Mat bgra = new Mat();
        try {
            if (bgr.type() != CvType.CV_8UC3) {
                converted = new Mat();
                bgr.convertTo(converted, CvType.CV_8UC3);
                src = converted;
            }
            Imgproc.cvtColor(src, bgra, Imgproc.COLOR_BGR2BGRA);

            int w = bgra.cols(), h = bgra.rows();
            byte[] buffer = new byte[w * h * 4];
            bgra.get(0, 0, buffer);

            WritableImage img = new WritableImage(w, h);
            PixelWriter pw = img.getPixelWriter();
            pw.setPixels(0, 0, w, h, PixelFormat.getByteBgraInstance(), buffer, 0, w * 4);
            return img;
        } finally {
            if (converted != null) converted.release();
            bgra.release();
        }
    }
}