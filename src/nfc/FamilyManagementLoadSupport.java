package nfc;

import java.io.IOException;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;

final class FamilyManagementLoadSupport {
    private FamilyManagementLoadSupport() {
    }

    interface ErrorLogger {
        void log(String context, Exception error);
    }

    static void reloadChildren(
        ObservableList<ChildrenView.Child> data,
        ErrorLogger logger
    ) {
        data.clear();
        if (!UserSession.isLoggedIn()) {
            return;
        }

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                FirestoreRestClient client = FirestoreRest.forCurrentUser();
                ObservableList<ChildrenView.Child> tempList = ChildrenDataSupport.loadChildren(client);
                Platform.runLater(() -> data.setAll(tempList));
            } catch (IOException | InterruptedException | IllegalStateException e) {
                logger.log("failed to load children", e);
                Platform.runLater(() ->
                    new Alert(Alert.AlertType.ERROR, "Failed to load children: " + e.getMessage()).showAndWait()
                );
            }
        });
    }

    static void reloadParents(
        ObservableList<ParentsPane.ParentRecord> data,
        ErrorLogger logger
    ) {
        data.clear();
        if (!UserSession.isLoggedIn()) {
            return;
        }

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                FirestoreRestClient client = FirestoreRest.forCurrentUser();
                ObservableList<ParentsPane.ParentRecord> temp = ParentsDataSupport.loadParents(client);
                Platform.runLater(() -> data.setAll(temp));
            } catch (IOException | InterruptedException | IllegalStateException e) {
                logger.log("failed to load parents", e);
                Platform.runLater(() ->
                    new Alert(Alert.AlertType.ERROR, "Failed to load parents: " + e.getMessage()).showAndWait()
                );
            }
        });
    }
}