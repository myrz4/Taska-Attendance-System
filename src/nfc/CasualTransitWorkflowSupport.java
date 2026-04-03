package nfc;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import javafx.application.Platform;

public final class CasualTransitWorkflowSupport {
    private CasualTransitWorkflowSupport() {
    }

    public static void reloadVisitsAsync(
        Consumer<List<CasualTransitView.VisitRow>> onLoaded,
        Consumer<String> onError
    ) {
        java.util.concurrent.CompletableFuture
            .supplyAsync(CasualTransitDataSupport::loadVisits)
            .whenComplete((loadedRows, error) -> Platform.runLater(() -> {
                if (error != null) {
                    onError.accept(rootMessage(error));
                    return;
                }
                onLoaded.accept(loadedRows);
            }));
    }

    public static void loadAuditHistoryAsync(
        String visitId,
        Consumer<List<CasualTransitView.AuditEntry>> onLoaded,
        Consumer<String> onError
    ) {
        java.util.concurrent.CompletableFuture
            .supplyAsync(() -> CasualTransitDataSupport.loadAuditEntries(visitId))
            .whenComplete((entries, error) -> Platform.runLater(() -> {
                if (error != null) {
                    onError.accept(rootMessage(error));
                    return;
                }
                onLoaded.accept(entries);
            }));
    }

    public static void createVisitAsync(
        Map<String, String> values,
        Runnable onSuccess,
        Consumer<String> onError
    ) {
        java.util.concurrent.CompletableFuture
            .runAsync(() -> CasualTransitMutationSupport.createVisit(values))
            .whenComplete((unused, error) -> Platform.runLater(() -> {
                if (error != null) {
                    onError.accept(rootMessage(error));
                    return;
                }
                onSuccess.run();
            }));
    }

    public static void checkoutVisitAsync(
        CasualTransitView.VisitRow row,
        Map<String, String> values,
        Runnable onSuccess,
        Consumer<String> onError
    ) {
        java.util.concurrent.CompletableFuture
            .runAsync(() -> CasualTransitMutationSupport.checkoutVisit(row, values))
            .whenComplete((unused, error) -> Platform.runLater(() -> {
                if (error != null) {
                    onError.accept(rootMessage(error));
                    return;
                }
                onSuccess.run();
            }));
    }

    public static void overrideVisitAsync(
        String action,
        CasualTransitView.VisitRow row,
        Map<String, String> values,
        Runnable onSuccess,
        Consumer<String> onError
    ) {
        java.util.concurrent.CompletableFuture
            .runAsync(() -> CasualTransitMutationSupport.overrideVisit(action, row, values))
            .whenComplete((unused, error) -> Platform.runLater(() -> {
                if (error != null) {
                    onError.accept(rootMessage(error));
                    return;
                }
                onSuccess.run();
            }));
    }

    public static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = String.valueOf(current.getMessage());
        String normalized = message.toUpperCase(Locale.ROOT);
        if (normalized.contains("PERMISSION_DENIED") || (normalized.contains("403") && normalized.contains("INSUFFICIENT PERMISSIONS"))) {
            return "Access denied by Firestore rules for the current account. Re-login if the role claim changed, or verify this account has admin access.";
        }
        return message;
    }
}