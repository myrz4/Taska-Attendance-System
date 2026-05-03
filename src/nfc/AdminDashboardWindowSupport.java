package nfc;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

public final class AdminDashboardWindowSupport {
    private AdminDashboardWindowSupport() {
    }

    static {
        java.util.function.Function<Stage, HBox> keepTopBar = stage -> createTopBar(stage, () -> {});
        java.util.function.Function<Runnable, Timeline> keepAutoRefresh = AdminDashboardWindowSupport::startAutoRefresh;
        java.util.function.Consumer<Stage> keepScreenSizedLaunch = AdminDashboardWindowSupport::applyScreenSizedLaunch;
        java.util.Objects.requireNonNull(keepTopBar);
        java.util.Objects.requireNonNull(keepAutoRefresh);
        java.util.Objects.requireNonNull(keepScreenSizedLaunch);
    }

    public static HBox createTopBar(Stage primaryStage, Runnable onClose) {
        Label title = new Label("Taska Zurah Student Management System");
        title.setStyle("-fx-text-fill: black; -fx-font-size: 16px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimizeButton = new Button("-");
        Button maximizeButton = new Button("⬜");
        Button closeButton = new Button("X");

        minimizeButton.getStyleClass().add("window-button");
        maximizeButton.getStyleClass().add("window-button");
        closeButton.getStyleClass().addAll("window-button", "close-button");

        minimizeButton.setOnAction(event -> primaryStage.setIconified(true));
        maximizeButton.setOnAction(event -> primaryStage.setMaximized(!primaryStage.isMaximized()));
        closeButton.setOnAction(event -> onClose.run());

        HBox topBar = new HBox(10, title, spacer, minimizeButton, maximizeButton, closeButton);
        topBar.setPadding(new Insets(5, 10, 5, 10));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #30cd30;");
        bindWindowDragging(topBar, primaryStage);
        return topBar;
    }

    public static Timeline startAutoRefresh(Runnable refreshAction) {
        Timeline autoRefreshTimeline = new Timeline(
            new KeyFrame(Duration.seconds(2), event -> refreshAction.run())
        );
        autoRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        autoRefreshTimeline.play();
        return autoRefreshTimeline;
    }

    public static void applyScreenSizedLaunch(Stage primaryStage) {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        primaryStage.setX(bounds.getMinX());
        primaryStage.setY(bounds.getMinY());
        primaryStage.setWidth(bounds.getWidth());
        primaryStage.setHeight(bounds.getHeight());
        primaryStage.setMaximized(true);
    }

    private static void bindWindowDragging(HBox topBar, Stage stage) {
        final double[] offsets = new double[2];
        topBar.setOnMousePressed(event -> {
            offsets[0] = event.getSceneX();
            offsets[1] = event.getSceneY();
        });
        topBar.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - offsets[0]);
            stage.setY(event.getScreenY() - offsets[1]);
        });
    }
}