package nfc;

import javafx.application.Platform;

/**
 * UI refresh helper.
 *
 * IMPORTANT: This project runs in REST mode (Firestore REST + Firebase Auth ID tokens),
 * so we must not initialize Firebase Admin SDK or read service-account keys.
 */
public class FirestoreService {
    private static long lastRefreshTime = 0;

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
}