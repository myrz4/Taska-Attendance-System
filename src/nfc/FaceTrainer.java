package nfc;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
// Requires opencv_contrib 'face' module:
import org.opencv.face.LBPHFaceRecognizer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class FaceTrainer {

    private static final String FACES_DIR = "faces";
    private static final String MODEL_DIR = "model";
    private static final String MODEL_FILE = MODEL_DIR + "/lbph.yml";
    private static final String LABELS_FILE = MODEL_DIR + "/labels.csv"; // label,int;userId,string

    public static void trainAll() throws IOException {
        OpenCVLoader.load();

        List<Mat> images = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        Map<Integer, String> idToUser = new LinkedHashMap<>();

        // Assign stable numeric label per user folder
        int nextLabel = 0;

        if (!Files.exists(Paths.get(FACES_DIR))) {
            throw new FileNotFoundException("faces/ folder not found");
        }

        // faces/<userId>/*.png
        try (var userDirs = Files.list(Paths.get(FACES_DIR))) {
            for (Path userDir : userDirs.collect(Collectors.toList())) {
                if (!Files.isDirectory(userDir)) continue;
                String userId = userDir.getFileName().toString();
                int label = nextLabel++;
                idToUser.put(label, userId);

                try (var imgPaths = Files.list(userDir)) {
                    for (Path p : imgPaths.collect(Collectors.toList())) {
                        if (!Files.isRegularFile(p)) continue;
                        String fname = p.getFileName().toString().toLowerCase();
                        if (!(fname.endsWith(".png") || fname.endsWith(".jpg") || fname.endsWith(".jpeg"))) continue;

                        Mat imgGray = Imgcodecs.imread(p.toString(), Imgcodecs.IMREAD_GRAYSCALE);
                        if (imgGray.empty()) continue;

                        // Normalize size to 200x200
                        Mat resized = new Mat();
                        Imgproc.resize(imgGray, resized, new Size(200, 200));
                        images.add(resized);
                        labels.add(label);
                    }
                }
            }
        }

        if (images.isEmpty()) throw new IllegalStateException("No face images to train.");

        // Convert labels to Mat
        Mat labelsMat = new Mat(labels.size(), 1, CvType.CV_32SC1);
        for (int i = 0; i < labels.size(); i++) labelsMat.put(i, 0, labels.get(i));

        // Train LBPH
        LBPHFaceRecognizer rec = LBPHFaceRecognizer.create(1, 8, 8, 8, 100.0);
        rec.train(images, labelsMat);

        Files.createDirectories(Paths.get(MODEL_DIR));
        rec.write(MODEL_FILE);

        // Save labels map
        try (PrintWriter pw = new PrintWriter(new FileWriter(LABELS_FILE, false))) {
            for (Map.Entry<Integer, String> e : idToUser.entrySet()) {
                pw.println(e.getKey() + "," + e.getValue());
            }
        }

        System.out.println("[FaceTrainer] Trained model saved: " + MODEL_FILE);
    }

    public static Map<Integer, String> loadLabels() throws IOException {
        Map<Integer, String> map = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(LABELS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] t = line.split(",", 2);
                map.put(Integer.parseInt(t[0].trim()), t[1].trim());
            }
        }
        return map;
    }
}