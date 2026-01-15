package nfc;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

public final class FaceCameraService {

    // ---------- FIELDS ----------
    private final Object lock = new Object();
    private volatile boolean running = false;
    private VideoCapture cap;
    private Thread worker;
    private ImageView preview;           // set via setView(...)
    private int lastWorkingIndex = -1;
    private volatile Mat lastFrame = null; // <-- keep a copy of most recent frame

    // ---------- PUBLIC API ----------
    /** Hook an ImageView for preview (can be null). */
    public void setView(ImageView v) { this.preview = v; }

    /** Start the camera on a preferred index. Safe to call multiple times. */
    public synchronized void start(int preferredIndex) {
        if (running) return;
        OpenCVLoader.load();
        running = true;
        worker = new Thread(() -> runLoop(preferredIndex), "FaceCameraService-Thread");
        worker.setDaemon(true);
        worker.start();
    }

    /** Convenience: start using last known or 0. */
    public void start() { start(lastWorkingIndex >= 0 ? lastWorkingIndex : 0); }

    /** Stop and release resources. Safe to call multiple times. */
    public synchronized void stop() {
        running = false;
        try {
            if (worker != null) worker.join(800);
        } catch (InterruptedException ignore) {}
        synchronized (lock) {
            if (cap != null) {
                if (cap.isOpened()) cap.release();
                cap = null;
            }
            if (lastFrame != null) { lastFrame.release(); lastFrame = null; }
        }
        if (preview != null) {
            Platform.runLater(() -> preview.setImage(null));
        }
    }

    /** Get a clone of the most recent frame (caller must release). May be null. */
    public Mat getLastFrame() {
        synchronized (lock) {
            return (lastFrame == null) ? null : lastFrame.clone();
        }
    }

    // ---------- INTERNAL ----------
    private void runLoop(int preferredIndex) {
        try {
            if (!openAnyCamera(preferredIndex)) {
                System.err.println("[Camera] No camera could be opened (indices tried: preferred, 0..3).");
                running = false;
                return;
            }

            // Sane default resolution
            cap.set(Videoio.CAP_PROP_FRAME_WIDTH,  640);
            cap.set(Videoio.CAP_PROP_FRAME_HEIGHT, 480);

            Mat frame = new Mat();
            while (running) {
                boolean ok;
                synchronized (lock) {
                    ok = (cap != null && cap.isOpened()) && cap.read(frame);
                }
                if (!ok || frame.empty()) {
                    System.err.println("[Camera] Frame read failed; attempting reopen…");
                    reopen();
                    try { Thread.sleep(60); } catch (InterruptedException ignore) {}
                    continue;
                }

                // cache copy for getters
                synchronized (lock) {
                    if (lastFrame != null) lastFrame.release();
                    lastFrame = frame.clone();
                }

                // show preview
                if (preview != null) {
                    Image fx = matToFxImage(frame);
                    Platform.runLater(() -> preview.setImage(fx));
                }

                try { Thread.sleep(15); } catch (InterruptedException ignore) {}
            }

            frame.release();
        } catch (Throwable t) {
            System.err.println("[Camera] Fatal error in runLoop: " + t);
        } finally {
            synchronized (lock) {
                if (cap != null && cap.isOpened()) cap.release();
                cap = null;
            }
        }
    }

    /** Try preferred index, then 0..3. */
    private boolean openAnyCamera(int preferredIndex) {
        int[] candidates = new int[] { preferredIndex, 0, 1, 2, 3 };
        boolean seenPreferred = false;

        for (int idx : candidates) {
            if (idx < 0 || idx > 9) continue;
            if (seenPreferred && idx == preferredIndex) continue;
            if (idx == preferredIndex) seenPreferred = true;
            if (tryOpen(idx)) {
                lastWorkingIndex = idx;
                System.out.println("[Camera] Opened camera index " + idx);
                return true;
            } else {
                System.err.println("[Camera] Failed to open camera index " + idx);
            }
        }
        return false;
    }

    /** On Windows, prefer DirectShow (CAP_DSHOW). Else fallback. */
    private boolean tryOpen(int index) {
        synchronized (lock) {
            if (cap != null && cap.isOpened()) cap.release();
            cap = new VideoCapture();

            String os = System.getProperty("os.name", "").toLowerCase();
            boolean opened;
            try {
                if (os.contains("win")) {
                    opened = cap.open(index, Videoio.CAP_DSHOW);
                    if (!opened) opened = cap.open(index);
                } else {
                    opened = cap.open(index);
                }
            } catch (Throwable t) {
                System.err.println("[Camera] Exception opening index " + index + " -> " + t);
                opened = false;
            }

            if (!opened) {
                cap.release();
                cap = null;
            }
            return opened;
        }
    }

    private void reopen() {
        int idx = lastWorkingIndex >= 0 ? lastWorkingIndex : 0;
        tryOpen(idx);
    }

    // Convert BGR Mat -> JavaFX Image (RGB)
    private static Image matToFxImage(Mat bgr) {
        Mat rgb = new Mat();
        Imgproc.cvtColor(bgr, rgb, Imgproc.COLOR_BGR2RGB);
        int w = rgb.cols(), h = rgb.rows(), stride = w * 3;
        byte[] buf = new byte[h * stride];
        rgb.get(0, 0, buf);
        WritableImage img = new WritableImage(w, h);
        img.getPixelWriter().setPixels(0, 0, w, h,
                PixelFormat.getByteRgbInstance(), buf, 0, stride);
        rgb.release();
        return img;
    }
}