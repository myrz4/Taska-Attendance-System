package nfc;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class ChildrenLayoutSupport {
    private ChildrenLayoutSupport() {
    }

    static {
        java.util.function.Function<VBox, BorderPane> keepBuildLayout = ChildrenLayoutSupport::buildLayout;
        java.util.function.Function<Runnable, Button> keepAddChildButton = ChildrenLayoutSupport::createAddChildButton;
        java.util.Objects.requireNonNull(keepBuildLayout);
        java.util.Objects.requireNonNull(keepAddChildButton);
    }

    static BorderPane buildLayout(VBox childrenContent) {
        HBox headerBar = new HBox(18);
        headerBar.setAlignment(Pos.CENTER_LEFT);
        headerBar.setPrefHeight(70);
        headerBar.setMaxWidth(Double.MAX_VALUE);
        headerBar.setStyle(
            "-fx-background-color: #2e8b57, #FECF4D;"
                + "-fx-background-insets: 0, 0 0 3 0;"
                + "-fx-background-radius: 0, 0;"
        );
        ImageView honeyPot = new ImageView(ImageLoader.loadSafe("hive2.png"));
        honeyPot.setFitWidth(54);
        honeyPot.setFitHeight(54);

        Label headerTitle = new Label("Children & Parents");
        headerTitle.setStyle("-fx-font-family: Impact; -fx-font-size: 44px; -fx-font-weight: bold; -fx-text-fill: #181818;");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        headerBar.getChildren().addAll(honeyPot, headerTitle, headerSpacer);

        VBox mainContent = new VBox(10);
        mainContent.setPadding(new Insets(20));
        mainContent.setAlignment(Pos.TOP_LEFT);

        ParentsPane parentsPane = new ParentsPane();
        TabPane tabs = new TabPane();
        Tab childrenTab = new Tab("Children", childrenContent);
        childrenTab.setClosable(false);
        Tab parentsTab = new Tab("Parents", parentsPane);
        parentsTab.setClosable(false);
        tabs.getTabs().addAll(childrenTab, parentsTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        mainContent.getChildren().add(tabs);

        BorderPane layout = new BorderPane();
        layout.setTop(headerBar);
        layout.setCenter(mainContent);
        layout.setStyle("-fx-background-color: linear-gradient(to bottom right, #86d67f 0%, #76cc6e 100%);");

        VBox.setVgrow(layout, Priority.ALWAYS);
        return layout;
    }

    static Button createAddChildButton(Runnable onAdd) {
        Button addChildBtn = new Button("Add New Child");
        addChildBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        addChildBtn.setOnAction(event -> onAdd.run());
        return addChildBtn;
    }
}