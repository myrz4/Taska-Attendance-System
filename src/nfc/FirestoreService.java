package nfc;

import java.io.FileInputStream;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import javafx.application.Platform;

/**
 * ✅ Firestore connector for Firebase Admin SDK
 * Make sure your serviceAccountKey.json file is located in jar_files/
 */
public class FirestoreService {

    private static Firestore db;
    private static long lastRefreshTime = 0;

    public static synchronized Firestore db() {
        if (db == null) {
            try {
                String keyPath = "jar_files/serviceAccountKey.json";
                System.out.println("DEBUG >> Using credentials from: " + keyPath);

                FileInputStream serviceAccount = new FileInputStream(keyPath);

                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                            .setProjectId("taskazurah")
                            .build();
                    FirebaseApp.initializeApp(options);
                    System.out.println("✅ Firebase initialized successfully!");
                }

                // ✅ Use Firebase’s built-in Firestore connector
                db = FirestoreClient.getFirestore();

                System.out.println("────────────────────────────────────────");
                System.out.println("🔗 Connected to Firestore via Firebase Admin SDK");
                System.out.println("   → Project ID : taskazurah");
                System.out.println("────────────────────────────────────────");

            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("❌ Failed to initialize Firestore: " + e.getMessage());
            }
        }
        return db;
    }

    public static void safeRefresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime < 1500) return;
        lastRefreshTime = now;

        Platform.runLater(() -> {
            System.out.println("🔁 Safe UI refresh triggered");
            AttendanceView.refreshUI();
            AdminDashboard.updateDashboardData();
        });
    }

    public static void forceFullRefresh() {
        Platform.runLater(() -> {
            System.out.println("🔄 Force refreshing Dashboard + AttendanceView...");
            AdminDashboard.updateDashboardData();
            AttendanceView.refreshUI();
            AttendanceView.updateChartFromStatic();
        });
    }

    public static void enableRealtimeListener() {
        Firestore firestore = db();

        firestore.collection("attendance").addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                System.err.println("❌ Firestore listener error: " + e.getMessage());
                return;
            }

            System.out.println("📡 Firestore snapshot updated → refreshing Attendance & Dashboard");

            Platform.runLater(() -> {
                try {
                    AttendanceView.refreshUI();
                    AttendanceView.updateChartFromStatic();
                    AdminDashboard.updateDashboardData();
                    System.out.println("✅ Realtime UI refresh triggered!");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        });

        System.out.println("✅ Firestore realtime listener enabled.");
    }
}