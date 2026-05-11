package nfc;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

/**
 * UI refresh helper.
 *
 * IMPORTANT: This project runs in REST mode (Firestore REST + Firebase Auth ID tokens),
 * so we must not initialize Firebase Admin SDK or read service-account keys.
 */
public class FirestoreService {
    private static long lastRefreshTime = 0;
    private static boolean refreshScheduled = false;

    private static void triggerRefreshNow() {
        lastRefreshTime = System.currentTimeMillis();
        System.out.println("🔁 Safe UI refresh triggered");
        AttendanceView.refreshUI();
        AdminDashboard.updateDashboardData();
    }

    public static void safeRefresh() {
        long now = System.currentTimeMillis();
        long remainingDelay = 1500 - (now - lastRefreshTime);
        if (remainingDelay <= 0) {
            Platform.runLater(FirestoreService::triggerRefreshNow);
            return;
        }

        if (refreshScheduled) {
            return;
        }
        refreshScheduled = true;

        Platform.runLater(() -> {
            PauseTransition delay = new PauseTransition(Duration.millis(remainingDelay));
            delay.setOnFinished(event -> {
                refreshScheduled = false;
                triggerRefreshNow();
            });
            delay.play();
        });
    }

    public static void forceFullRefresh() {
        Platform.runLater(() -> {
            System.out.println("🔄 Force refreshing Dashboard + AttendanceView...");
            AdminDashboard.updateDashboardData();
            AdminDashboard.refreshAttendancePageIfVisible();
            AttendanceView.refreshUI();
            AttendanceView.updateChartFromStatic();
        });
    }

    public static void refreshAfterAttendanceMutation() {
        safeRefresh();
        Platform.runLater(() -> {
            PauseTransition delay = new PauseTransition(Duration.millis(1800));
            delay.setOnFinished(event -> forceFullRefresh());
            delay.play();
        });
    }
}