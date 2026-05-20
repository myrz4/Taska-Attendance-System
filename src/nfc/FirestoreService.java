package nfc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
    private static final DateTimeFormatter PAYROLL_PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static long lastRefreshTime = 0;
    private static boolean refreshScheduled = false;
    private static final Set<String> pendingPayrollPeriods = new LinkedHashSet<>();
    private static boolean payrollRefreshScheduled = false;
    private static boolean payrollRefreshInFlight = false;

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

    public static void refreshAfterRosterMutation() {
        safeRefresh();
        Platform.runLater(() -> {
            PauseTransition delay = new PauseTransition(Duration.millis(250));
            delay.setOnFinished(event -> {
                AdminDashboard.updateDashboardData();
                AttendanceView.refreshRoster();
            });
            delay.play();
        });
    }

    public static void refreshAfterAttendanceMutation() {
        refreshAfterAttendanceMutation(LocalDate.now());
    }

    public static void refreshAfterAttendanceMutation(LocalDate attendanceDate) {
        safeRefresh();
        schedulePayrollRefresh(attendanceDate);
        Platform.runLater(() -> {
            PauseTransition delay = new PauseTransition(Duration.millis(900));
            delay.setOnFinished(event -> forceFullRefresh());
            delay.play();
        });
    }

    private static void schedulePayrollRefresh(LocalDate attendanceDate) {
        if (attendanceDate == null) {
            return;
        }

        synchronized (FirestoreService.class) {
            pendingPayrollPeriods.add(attendanceDate.withDayOfMonth(1).format(PAYROLL_PERIOD_FORMAT));
            if (payrollRefreshScheduled || payrollRefreshInFlight) {
                return;
            }
            payrollRefreshScheduled = true;
        }

        Platform.runLater(() -> {
            PauseTransition delay = new PauseTransition(Duration.millis(650));
            delay.setOnFinished(event -> triggerPayrollRefresh());
            delay.play();
        });
    }

    private static void triggerPayrollRefresh() {
        final List<String> periods;
        synchronized (FirestoreService.class) {
            payrollRefreshScheduled = false;
            if (payrollRefreshInFlight || pendingPayrollPeriods.isEmpty()) {
                return;
            }
            payrollRefreshInFlight = true;
            periods = new ArrayList<>(pendingPayrollPeriods);
            pendingPayrollPeriods.clear();
        }

        CompletableFuture
            .runAsync(() -> {
                for (String period : periods) {
                    TeacherPayrollRemoteSupport.generateMonthlyPayroll(period);
                }
            })
            .whenComplete((ignored, error) -> Platform.runLater(() -> {
                synchronized (FirestoreService.class) {
                    payrollRefreshInFlight = false;
                }

                if (error != null) {
                    Throwable failure = error;
                    if (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) {
                        failure = failure.getCause();
                    }
                    System.err.println("FirestoreService: teacher payroll refresh failed - " + failure.getMessage());
                } else {
                    for (String period : periods) {
                        AdminDashboard.refreshTeacherPayrollPageIfVisible(period);
                    }
                }

                synchronized (FirestoreService.class) {
                    if (pendingPayrollPeriods.isEmpty() || payrollRefreshScheduled) {
                        return;
                    }
                    payrollRefreshScheduled = true;
                }

                PauseTransition delay = new PauseTransition(Duration.millis(250));
                delay.setOnFinished(event -> triggerPayrollRefresh());
                delay.play();
            }));
    }
}