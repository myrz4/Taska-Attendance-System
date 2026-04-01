package nfc;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.Platform;

@SuppressWarnings("unused")
final class BillingLedgerAsyncSupport {
    private BillingLedgerAsyncSupport() {}

    static void reloadRowsAsync(
        Supplier<List<BillingLedgerView.LedgerRow>> loader,
        Consumer<List<BillingLedgerView.LedgerRow>> onLoaded,
        ErrorCallback onError
    ) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                List<BillingLedgerView.LedgerRow> rows = loader.get();
                Platform.runLater(() -> onLoaded.accept(rows));
            } catch (RuntimeException ex) {
                Platform.runLater(() -> onError.handle(ex));
            }
        });
    }

    static void issueInvoicesAsync(
        String period,
        List<String> parentIds,
        Consumer<Map<?, ?>> onIssued,
        ErrorCallback onError
    ) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Map<?, ?> result = BillingLedgerRemoteSupport.generateInvoicesForPeriod(period, parentIds);
                Platform.runLater(() -> onIssued.accept(result));
            } catch (RuntimeException ex) {
                Platform.runLater(() -> onError.handle(ex));
            }
        });
    }

    static void recordCashPaymentAsync(
        BillingLedgerView.LedgerRow row,
        Runnable onRecorded,
        ErrorCallback onError
    ) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                BillingLedgerPaymentSupport.recordCashPayment(row);
                Platform.runLater(onRecorded);
            } catch (RuntimeException ex) {
                Platform.runLater(() -> onError.handle(ex));
            }
        });
    }

    interface ErrorCallback {
        void handle(Exception ex);
    }
}