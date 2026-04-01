package nfc;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

final class AdminDashboardContentSupport {
    private static volatile long lastDashboardFirestoreRefreshMs = 0;

    private AdminDashboardContentSupport() {
    }

    interface DashboardActions {
        Image loadImage(String fileName);
        void openAgeReviewLedger();
        void openOvertimeReviewLedger();
        void logError(String context, Exception error);
    }

    static final class DashboardWidgets {
        private final BorderPane root;
        private final Label scannedToday;
        private final Label notScanned;
        private final Label billingAgeReview;
        private final Label billingOvertimeReview;
        private final VBox checkInList;
        private final VBox checkOutList;

        DashboardWidgets(
            BorderPane root,
            Label scannedToday,
            Label notScanned,
            Label billingAgeReview,
            Label billingOvertimeReview,
            VBox checkInList,
            VBox checkOutList
        ) {
            this.root = root;
            this.scannedToday = scannedToday;
            this.notScanned = notScanned;
            this.billingAgeReview = billingAgeReview;
            this.billingOvertimeReview = billingOvertimeReview;
            this.checkInList = checkInList;
            this.checkOutList = checkOutList;
        }

        BorderPane root() {
            return root;
        }
    }

    static DashboardWidgets buildDashboard(DashboardActions actions) {
        return AdminDashboardLayoutSupport.buildDashboard(actions);
    }

    static void refreshDashboardFromFirestoreAsync(DashboardWidgets widgets, DashboardActions actions) {
        if (widgets == null) {
            return;
        }
        if (!UserSession.isLoggedIn()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastDashboardFirestoreRefreshMs < 1200) {
            return;
        }
        lastDashboardFirestoreRefreshMs = now;

        CompletableFuture.runAsync(() -> {
            try {
                FirestoreRestClient client = FirestoreRest.forCurrentUser();
                AdminDashboardRefreshDataSupport.DashboardRefreshSnapshot snapshot =
                    AdminDashboardRefreshDataSupport.collectSnapshot(client);

                Platform.runLater(() -> {
                    widgets.scannedToday.setText("Scanned Today: " + snapshot.presentCount);
                    widgets.notScanned.setText("Not Yet Scanned: " + snapshot.absentCount);
                    widgets.billingAgeReview.setText(
                        "Age Review: " + snapshot.ageReviewFamilies + " fam / " + snapshot.ageReviewInvoices + " inv"
                    );
                    widgets.billingOvertimeReview.setText(
                        "OT Review: " + snapshot.overtimeReviewFamilies + " fam / " + snapshot.overtimeReviewInvoices + " inv"
                    );
                    updateLiveListBox(widgets.checkInList, snapshot.inLines, "No check-ins yet today.");
                    updateLiveListBox(widgets.checkOutList, snapshot.outLines, "No check-outs yet today.");
                });

                System.out.println("✅ Dashboard refreshed (Firestore) → In=" + snapshot.inLines.size() + " | Out=" + snapshot.outLines.size());
            } catch (IOException | InterruptedException error) {
                actions.logError("dashboard refresh failed", error);
            }
        });
    }

    private static void updateLiveListBox(VBox container, List<String> values, String emptyText) {
        container.getChildren().clear();
        if (values.isEmpty()) {
            Label empty = new Label(emptyText);
            empty.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");
            container.getChildren().add(empty);
            return;
        }

        for (String text : values) {
            Label label = new Label(text);
            label.setStyle("-fx-font-size: 15px; -fx-text-fill: #2b3b2b;");
            container.getChildren().add(label);
        }
    }
}