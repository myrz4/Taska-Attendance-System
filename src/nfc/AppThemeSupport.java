package nfc;

import java.net.URL;
import java.util.EnumSet;

import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

@SuppressWarnings({"unused", "java:S1118", "java:S1144"})
final class AppThemeSupport {
    enum Tone {
        NEUTRAL,
        INFO,
        SUCCESS,
        WARNING,
        DANGER
    }

    private static final EnumSet<ButtonBar.ButtonData> PRIMARY_BUTTON_DATA = EnumSet.of(
        ButtonBar.ButtonData.OK_DONE,
        ButtonBar.ButtonData.FINISH,
        ButtonBar.ButtonData.YES,
        ButtonBar.ButtonData.APPLY
    );

    static {
        java.util.function.Consumer<Scene> keepApplyScene = AppThemeSupport::applyScene;
        java.util.function.Consumer<Dialog<?>> keepAttachDialog = dialog -> attachDialogStyles(dialog.getDialogPane());
        java.util.function.BiFunction<String, String, VBox> keepEmptyState = AppThemeSupport::createEmptyState;
        java.util.function.Function<String, Label> keepChip = AppThemeSupport::createChip;
        java.util.Objects.requireNonNull(keepApplyScene);
        java.util.Objects.requireNonNull(keepAttachDialog);
        java.util.Objects.requireNonNull(keepEmptyState);
        java.util.Objects.requireNonNull(keepChip);
    }

    private AppThemeSupport() {
    }

    static void applyScene(Scene scene) {
        if (scene == null) {
            return;
        }
        URL stylesheet = AppThemeSupport.class.getResource("style.css");
        if (stylesheet == null) {
            return;
        }
        String css = stylesheet.toExternalForm();
        if (!scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
    }

    static void attachDialogStyles(DialogPane pane) {
        if (pane == null) {
            return;
        }
        URL stylesheet = AppThemeSupport.class.getResource("style.css");
        if (stylesheet != null) {
            String css = stylesheet.toExternalForm();
            if (!pane.getStylesheets().contains(css)) {
                pane.getStylesheets().add(css);
            }
        }
        addStyleClasses(pane, "app-dialog-pane");
    }

    static void prepareDialog(Dialog<?> dialog, String title, String subtitle, Tone tone) {
        if (dialog == null) {
            return;
        }
        attachDialogStyles(dialog.getDialogPane());
        dialog.setTitle(title == null ? "Taska Zurah" : title);
        dialog.setHeaderText(null);
        dialog.getDialogPane().setGraphic(null);
        dialog.getDialogPane().setHeader(createDialogHeader(title, subtitle, tone));
        dialog.setResizable(true);
    }

    static void styleDialogButtons(Dialog<?> dialog, ButtonType primaryType, ButtonType... dangerTypes) {
        if (dialog == null) {
            return;
        }
        DialogPane pane = dialog.getDialogPane();
        for (ButtonType buttonType : pane.getButtonTypes()) {
            Node node = pane.lookupButton(buttonType);
            if (!(node instanceof Button)) {
                continue;
            }
            Button button = (Button) node;
            button.getStyleClass().removeAll(
                "app-primary-button",
                "app-secondary-button",
                "app-danger-button",
                "app-ghost-button",
                "app-toolbar-button"
            );
            styleButton(button, resolveButtonStyle(buttonType, primaryType, dangerTypes));
            button.setDefaultButton(buttonType == primaryType);
            button.setCancelButton(buttonType.getButtonData().isCancelButton());
        }
    }

    static void styleToolbarButtons(Button... buttons) {
        for (Button button : buttons) {
            styleButton(button, "app-toolbar-button");
        }
    }

    static void stylePrimaryButtons(Button... buttons) {
        for (Button button : buttons) {
            styleButton(button, "app-primary-button");
        }
    }

    static void styleSecondaryButtons(Button... buttons) {
        for (Button button : buttons) {
            styleButton(button, "app-secondary-button");
        }
    }

    static void styleDangerButtons(Button... buttons) {
        for (Button button : buttons) {
            styleButton(button, "app-danger-button");
        }
    }

    static void styleGhostButtons(Button... buttons) {
        for (Button button : buttons) {
            styleButton(button, "app-ghost-button");
        }
    }

    static void styleButton(Button button, String variantClass) {
        if (button == null) {
            return;
        }
        addStyleClasses(button, "app-button", variantClass);
        button.setWrapText(true);
        button.setMinHeight(34);
    }

    static void styleControls(Node... nodes) {
        for (Node node : nodes) {
            if (node == null) {
                continue;
            }
            if (node instanceof TextInputControl || node instanceof ComboBoxBase<?> || node instanceof Spinner<?> || node instanceof ListView<?>) {
                addStyleClasses(node, "app-form-field");
            }
            if (node instanceof TextArea) {
                addStyleClasses(node, "app-form-field", "app-text-area");
            }
            if (node instanceof TextField || node instanceof PasswordField) {
                addStyleClasses(node, "app-form-field", "app-text-field");
            }
            if (node instanceof TableView<?>) {
                addStyleClasses(node, "app-data-table");
            }
            if (node instanceof Labeled) {
                ((Labeled) node).setWrapText(true);
            }
        }
    }

    static HBox createPageHeader(String title, String subtitle, Node... actions) {
        Label titleLabel = new Label(title == null ? "" : title);
        titleLabel.getStyleClass().add("app-page-title");

        Label subtitleLabel = new Label(subtitle == null ? "" : subtitle);
        subtitleLabel.getStyleClass().add("app-page-subtitle");
        subtitleLabel.setWrapText(true);

        VBox textBox = new VBox(4, titleLabel, subtitleLabel);
        textBox.getStyleClass().add("app-page-header-copy");

        HBox actionBox = new HBox(8);
        actionBox.getStyleClass().add("app-page-header-actions");
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        if (actions != null) {
            actionBox.getChildren().addAll(actions);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(18, textBox, spacer, actionBox);
        header.getStyleClass().add("app-page-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    static HBox createStandardPageBanner(String title, String subtitle, Node... actions) {
        ImageView honeyPot = new ImageView(ImageLoader.loadSafe("hive2.png"));
        honeyPot.setFitWidth(54);
        honeyPot.setFitHeight(54);

        Label titleLabel = new Label(title == null ? "" : title);
        titleLabel.getStyleClass().add("app-standard-page-title");

        VBox textBox = new VBox(0, titleLabel);
        textBox.getStyleClass().add("app-standard-page-copy");
        if (subtitle != null && !subtitle.isBlank()) {
            Label subtitleLabel = new Label(subtitle);
            subtitleLabel.getStyleClass().add("app-standard-page-subtitle");
            subtitleLabel.setWrapText(true);
            textBox.getChildren().add(subtitleLabel);
        }

        HBox actionBox = new HBox(8);
        actionBox.getStyleClass().add("app-standard-page-actions");
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        if (actions != null) {
            actionBox.getChildren().addAll(actions);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox banner = new HBox(18, honeyPot, textBox, spacer, actionBox);
        banner.getStyleClass().add("app-standard-page-banner");
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setMaxWidth(Double.MAX_VALUE);
        return banner;
    }

    static ScrollPane createPageBodyScrollWrapper(VBox body) {
        ScrollPane scrollPane = new ScrollPane(body);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("app-page-scroll");
        body.minHeightProperty().bind(Bindings.createDoubleBinding(
            () -> Math.max(0, scrollPane.getViewportBounds().getHeight()),
            scrollPane.viewportBoundsProperty()
        ));
        return scrollPane;
    }

    static BorderPane createStandardPageShell(Node header, Node body) {
        BorderPane shell = new BorderPane();
        shell.setTop(header);
        shell.setCenter(body);
        shell.getStyleClass().add("app-standard-page-shell");
        return shell;
    }

    static VBox createSectionCard(String title, String subtitle, Node... bodyNodes) {
        VBox card = new VBox(14);
        card.getStyleClass().addAll("app-card", "app-section-card");
        if (title != null || subtitle != null) {
            card.getChildren().add(createSectionHeader(title, subtitle));
        }
        if (bodyNodes != null) {
            card.getChildren().addAll(bodyNodes);
        }
        return card;
    }

    static HBox createSectionHeader(String title, String subtitle, Node... actions) {
        Label titleLabel = new Label(title == null ? "" : title);
        titleLabel.getStyleClass().add("app-section-title");
        Label subtitleLabel = new Label(subtitle == null ? "" : subtitle);
        subtitleLabel.getStyleClass().add("app-section-subtitle");
        subtitleLabel.setWrapText(true);

        VBox textBox = new VBox(3, titleLabel, subtitleLabel);
        HBox actionBox = new HBox(8);
        actionBox.getStyleClass().add("app-section-actions");
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        if (actions != null) {
            actionBox.getChildren().addAll(actions);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, textBox, spacer, actionBox);
        header.getStyleClass().add("app-section-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    static VBox createFormSection(String title, String helperText, Node... nodes) {
        VBox section = new VBox(12);
        section.getStyleClass().add("app-form-section");
        if (title != null && !title.isBlank()) {
            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("app-form-section-title");
            section.getChildren().add(titleLabel);
        }
        if (helperText != null && !helperText.isBlank()) {
            Label helperLabel = new Label(helperText);
            helperLabel.getStyleClass().add("app-form-section-helper");
            helperLabel.setWrapText(true);
            section.getChildren().add(helperLabel);
        }
        if (nodes != null) {
            section.getChildren().addAll(nodes);
        }
        return section;
    }

    static ScrollPane wrapDialogContent(Node content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("app-dialog-scroll");
        return scrollPane;
    }

    static VBox createDialogContent(Node... sections) {
        VBox content = new VBox(16);
        content.setPadding(new Insets(18, 18, 18, 18));
        content.getStyleClass().add("app-dialog-content");
        if (sections != null) {
            content.getChildren().addAll(sections);
        }
        return content;
    }

    static FlowPane createChipStrip(Node... chips) {
        FlowPane strip = new FlowPane();
        strip.setHgap(10);
        strip.setVgap(10);
        strip.getStyleClass().add("app-chip-strip");
        if (chips != null) {
            strip.getChildren().addAll(chips);
        }
        return strip;
    }

    static VBox createKpiCard(String title, Labeled valueLabel, String... styleClasses) {
        Label titleLabel = new Label(title == null ? "" : title);
        titleLabel.getStyleClass().add("app-kpi-title");
        valueLabel.getStyleClass().add("app-kpi-value");
        VBox card = new VBox(6, titleLabel, valueLabel);
        card.getStyleClass().addAll("app-card", "app-kpi-card");
        if (styleClasses != null) {
            card.getStyleClass().addAll(styleClasses);
        }
        return card;
    }

    static Label createChip(String text, String... styleClasses) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add("app-chip");
        if (styleClasses != null) {
            label.getStyleClass().addAll(styleClasses);
        }
        label.setWrapText(true);
        return label;
    }

    static VBox createEmptyState(String title, String message) {
        Label titleLabel = new Label(title == null ? "Nothing here yet" : title);
        titleLabel.getStyleClass().add("app-empty-title");
        Label messageLabel = new Label(message == null ? "" : message);
        messageLabel.getStyleClass().add("app-empty-message");
        messageLabel.setWrapText(true);
        VBox box = new VBox(6, titleLabel, messageLabel);
        box.getStyleClass().add("app-empty-state");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    static void applyStatusTone(Node node, String baseClass, Tone tone) {
        if (node == null) {
            return;
        }
        node.getStyleClass().removeAll(
            "app-tone-neutral",
            "app-tone-info",
            "app-tone-success",
            "app-tone-warning",
            "app-tone-danger"
        );
        addStyleClasses(node, baseClass, toneClass(tone));
    }

    static void showInfo(Window owner, String title, String message) {
        showMessageDialog(owner, title, null, message, Tone.INFO, "OK");
    }

    static void showWarning(Window owner, String title, String message) {
        showMessageDialog(owner, title, null, message, Tone.WARNING, "OK");
    }

    static void showError(Window owner, String title, String message) {
        showMessageDialog(owner, title, null, message, Tone.DANGER, "OK");
    }

    static void showException(Window owner, String title, Exception error) {
        showMessageDialog(owner, title, null, error == null ? "Unexpected error." : String.valueOf(error.getMessage()), Tone.DANGER, "OK");
    }

    static boolean showConfirm(Window owner, String title, String subtitle, String message, Tone tone, String confirmLabel, String cancelLabel) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        prepareDialog(dialog, title, subtitle, tone);

        ButtonType confirmType = new ButtonType(confirmLabel == null || confirmLabel.isBlank() ? "Confirm" : confirmLabel, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(cancelLabel == null || cancelLabel.isBlank() ? "Cancel" : cancelLabel, ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmType, cancelType);

        Label messageLabel = new Label(message == null ? "" : message);
        messageLabel.setWrapText(true);
        messageLabel.getStyleClass().add("app-dialog-message");
        VBox content = createDialogContent(createFormSection("Review this action", null, messageLabel));
        dialog.getDialogPane().setContent(content);
        styleDialogButtons(dialog, confirmType, tone == Tone.DANGER ? new ButtonType[] {confirmType} : new ButtonType[0]);
        return dialog.showAndWait().orElse(cancelType) == confirmType;
    }

    static void showToast(Window owner, String title, String message, Tone tone) {
        if (owner == null) {
            return;
        }
        Popup popup = new Popup();
        popup.setAutoHide(true);

        Label titleLabel = new Label(title == null ? "Taska Zurah" : title);
        titleLabel.getStyleClass().add("app-toast-title");
        Label messageLabel = new Label(message == null ? "" : message);
        messageLabel.getStyleClass().add("app-toast-message");
        messageLabel.setWrapText(true);

        VBox card = new VBox(4, titleLabel, messageLabel);
        card.getStyleClass().addAll("app-toast-card", toneClass(tone));
        card.setMaxWidth(320);
        popup.getContent().add(card);

        double anchorX = owner.getX() + Math.max(20, owner.getWidth() - 360);
        double anchorY = owner.getY() + Math.max(20, owner.getHeight() - 120);
        popup.show(owner, anchorX, anchorY);

        PauseTransition delay = new PauseTransition(Duration.seconds(2.6));
        delay.setOnFinished(event -> popup.hide());
        delay.play();
    }

    private static void showMessageDialog(Window owner, String title, String subtitle, String message, Tone tone, String buttonLabel) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        prepareDialog(dialog, title, subtitle, tone);
        ButtonType okType = new ButtonType(buttonLabel == null || buttonLabel.isBlank() ? "OK" : buttonLabel, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(okType);

        Label messageLabel = new Label(message == null ? "" : message);
        messageLabel.setWrapText(true);
        messageLabel.getStyleClass().add("app-dialog-message");
        dialog.getDialogPane().setContent(createDialogContent(createFormSection("Details", null, messageLabel)));
        styleDialogButtons(dialog, okType, tone == Tone.DANGER ? new ButtonType[] {okType} : new ButtonType[0]);
        dialog.showAndWait();
    }

    private static Node createDialogHeader(String title, String subtitle, Tone tone) {
        Label titleLabel = new Label(title == null ? "Taska Zurah" : title);
        titleLabel.getStyleClass().add("app-dialog-title");

        Label subtitleLabel = new Label(subtitle == null ? "" : subtitle);
        subtitleLabel.getStyleClass().add("app-dialog-subtitle");
        subtitleLabel.setWrapText(true);

        VBox header = new VBox(4, titleLabel, subtitleLabel);
        header.getStyleClass().addAll("app-dialog-header", dialogHeaderClass(tone));
        return header;
    }

    private static String resolveButtonStyle(ButtonType buttonType, ButtonType primaryType, ButtonType... dangerTypes) {
        if (buttonType == primaryType) {
            return isDangerButton(buttonType, dangerTypes) ? "app-danger-button" : "app-primary-button";
        }
        if (isDangerButton(buttonType, dangerTypes)) {
            return "app-danger-button";
        }
        if (PRIMARY_BUTTON_DATA.contains(buttonType.getButtonData())) {
            return "app-primary-button";
        }
        return "app-secondary-button";
    }

    private static boolean isDangerButton(ButtonType buttonType, ButtonType... dangerTypes) {
        if (dangerTypes == null) {
            return false;
        }
        for (ButtonType dangerType : dangerTypes) {
            if (buttonType == dangerType) {
                return true;
            }
        }
        return false;
    }

    private static String dialogHeaderClass(Tone tone) {
        switch (tone == null ? Tone.NEUTRAL : tone) {
            case DANGER:
                return "app-dialog-header-danger";
            case WARNING:
                return "app-dialog-header-warning";
            case SUCCESS:
                return "app-dialog-header-success";
            case INFO:
                return "app-dialog-header-info";
            default:
                return "app-dialog-header-neutral";
        }
    }

    private static String toneClass(Tone tone) {
        switch (tone == null ? Tone.NEUTRAL : tone) {
            case DANGER:
                return "app-tone-danger";
            case WARNING:
                return "app-tone-warning";
            case SUCCESS:
                return "app-tone-success";
            case INFO:
                return "app-tone-info";
            default:
                return "app-tone-neutral";
        }
    }

    private static void addStyleClasses(Node node, String... styleClasses) {
        if (node == null || styleClasses == null) {
            return;
        }
        for (String styleClass : styleClasses) {
            if (styleClass == null || styleClass.isBlank()) {
                continue;
            }
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        }
    }
}