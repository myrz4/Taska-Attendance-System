package nfc;

import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.util.Duration;

final class UiClipboardSupport {
    private static final Duration FEEDBACK_DURATION = Duration.millis(900);

    private UiClipboardSupport() {
    }

    static boolean copyText(String value) {
        return copyText(value, null, "Copied");
    }

    static boolean copyText(String value, Node owner, String feedbackMessage) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return false;
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(normalized);
        Clipboard.getSystemClipboard().setContent(content);
        showCopiedFeedback(owner, feedbackMessage == null || feedbackMessage.isBlank() ? "Copied" : feedbackMessage);
        return true;
    }

    private static void showCopiedFeedback(Node owner, String message) {
        if (owner == null || owner.getScene() == null || owner.getScene().getWindow() == null) {
            return;
        }

        Bounds bounds = owner.localToScreen(owner.getBoundsInLocal());
        if (bounds == null) {
            return;
        }

        Tooltip tooltip = new Tooltip(message);
        tooltip.getStyleClass().add("copy-feedback-tooltip");
        tooltip.show(owner, bounds.getMinX() + 8, bounds.getMinY() - 6);

        PauseTransition hideDelay = new PauseTransition(FEEDBACK_DURATION);
        hideDelay.setOnFinished(event -> tooltip.hide());
        hideDelay.play();
    }
}