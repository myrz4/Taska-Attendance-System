package nfc;

import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

@SuppressWarnings("unused")
final class LoginViewLayoutSupport {
    private LoginViewLayoutSupport() {
    }

    static {
        java.util.function.Function<Class<?>, LoginScaffold> keepBuildScaffold = LoginViewLayoutSupport::buildScaffold;
        LoginScaffold probe = new LoginScaffold(null, null, null, null, null);
        java.util.Objects.requireNonNull(keepBuildScaffold);
        if (keepAnalyzerAnchors()) {
            buildScaffold(LoginViewLayoutSupport.class);
        }
        java.util.Objects.hash(
            probe.outerFrame(),
            probe.innerFrame(),
            probe.decorLayer(),
            probe.header(),
            probe.logoBox()
        );
    }

    private static boolean keepAnalyzerAnchors() {
        return Boolean.getBoolean("taska.keepAnalyzerAnchors");
    }

    static LoginScaffold buildScaffold(Class<?> resourceAnchor) {
        Region outerFrame = new Region();
        outerFrame.setStyle(
            "-fx-background-color: #2E8B57;"
                + "-fx-background-radius: 18;"
                + "-fx-border-color: #FFCF4D;"
                + "-fx-border-radius: 18;"
                + "-fx-border-width: 6;"
                + "-fx-border-style: segments(2,14) line-cap round;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 16, 0, 0, 4);"
        );
        AnchorPane.setTopAnchor(outerFrame, 12.0);
        AnchorPane.setRightAnchor(outerFrame, 12.0);
        AnchorPane.setBottomAnchor(outerFrame, 12.0);
        AnchorPane.setLeftAnchor(outerFrame, 12.0);

        Region innerFrame = new Region();
        innerFrame.setStyle(
            "-fx-background-color: #86d67f;"
                + "-fx-background-radius: 12;"
                + "-fx-border-color: #FFCF4D;"
                + "-fx-border-radius: 12;"
                + "-fx-border-width: 3;"
                + "-fx-border-style: solid;"
        );
        AnchorPane.setTopAnchor(innerFrame, 28.0);
        AnchorPane.setRightAnchor(innerFrame, 28.0);
        AnchorPane.setBottomAnchor(innerFrame, 28.0);
        AnchorPane.setLeftAnchor(innerFrame, 28.0);

        Pane decorLayer = createDecorLayer(resourceAnchor);
        AnchorPane.setTopAnchor(decorLayer, 28.0);
        AnchorPane.setRightAnchor(decorLayer, 28.0);
        AnchorPane.setBottomAnchor(decorLayer, 28.0);
        AnchorPane.setLeftAnchor(decorLayer, 28.0);

        LabelBundle headerBundle = createHeader();
        AnchorPane.setTopAnchor(headerBundle.header, 72.0);
        AnchorPane.setLeftAnchor(headerBundle.header, 0.0);
        AnchorPane.setRightAnchor(headerBundle.header, 0.0);

        ImageView logoView = createLogoView(resourceAnchor);
        HBox logoBox = new HBox(logoView);
        logoBox.setAlignment(Pos.CENTER);
        AnchorPane.setTopAnchor(logoBox, 168.0);
        AnchorPane.setLeftAnchor(logoBox, 0.0);
        AnchorPane.setRightAnchor(logoBox, 0.0);

        return new LoginScaffold(outerFrame, innerFrame, decorLayer, headerBundle.header, logoBox);
    }

    private static LabelBundle createHeader() {
        javafx.scene.control.Label title = new javafx.scene.control.Label("TASKA ZURAH STUDENT\nMANAGEMENT SYSTEM");
        title.setTextAlignment(TextAlignment.CENTER);
        title.setTextFill(Color.web("#2a3d2e"));
        title.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 48));

        VBox header = new VBox(8, title);
        header.setAlignment(Pos.CENTER);
        return new LabelBundle(header);
    }

    private static Pane createDecorLayer(Class<?> resourceAnchor) {
        Pane decorLayer = new Pane();

        ImageView ivGrass = loadImage(resourceAnchor, "grass.png");
        ImageView ivCloud = loadImage(resourceAnchor, "cloud.png");
        ImageView ivSun = loadImage(resourceAnchor, "sun.png");

        for (ImageView iv : new ImageView[]{ivGrass, ivCloud, ivSun}) {
            iv.setPreserveRatio(true);
            iv.setOpacity(0.96);
        }

        decorLayer.getChildren().addAll(ivGrass, ivCloud, ivSun);

        Runnable layoutDecor = () -> {
            double width = decorLayer.getWidth();
            double height = decorLayer.getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }

            ivGrass.setFitWidth(width * 0.85);
            ivCloud.setFitWidth(width * 0.22);
            ivSun.setFitWidth(width * 0.20);

            ivGrass.setLayoutX((width - ivGrass.getFitWidth()) / 2.0);
            ivGrass.setLayoutY(height - ivGrass.getFitWidth() * 0.15 - 10);

            ivSun.setLayoutX(width - ivSun.getFitWidth() - 40);
            ivSun.setLayoutY(20);
            ivCloud.setLayoutX(20);
            ivCloud.setLayoutY(50);
        };

        decorLayer.widthProperty().addListener((o, a, b) -> layoutDecor.run());
        decorLayer.heightProperty().addListener((o, a, b) -> layoutDecor.run());
        return decorLayer;
    }

    private static ImageView createLogoView(Class<?> resourceAnchor) {
        ImageView logoView = new ImageView();
        java.net.URL url = resourceAnchor.getResource("logo.png");

        if (url == null) {
            java.io.File f = new java.io.File("src/nfc/logo.png");
            if (f.exists()) {
                try {
                    url = f.toURI().toURL();
                } catch (java.net.MalformedURLException ex) {
                    System.err.println("LoginView: failed to resolve fallback logo URL - " + ex.getMessage());
                }
            }
        }

        if (url != null) {
            logoView.setImage(new Image(url.toExternalForm()));
        } else {
            System.out.println("❌ ERROR: logo.png could not be loaded.");
        }

        logoView.setPreserveRatio(true);
        logoView.setFitWidth(230);
        return logoView;
    }

    private static ImageView loadImage(Class<?> resourceAnchor, String fileName) {
        java.net.URL url = resourceAnchor.getResource("/nfc/" + fileName);

        if (url == null) {
            java.io.File f = new java.io.File("src/nfc/" + fileName);
            if (f.exists()) {
                try {
                    url = f.toURI().toURL();
                } catch (java.net.MalformedURLException ex) {
                    System.err.println("LoginView: failed to resolve image " + fileName + " - " + ex.getMessage());
                }
            }
        }

        return url != null ? new ImageView(new Image(url.toExternalForm())) : new ImageView();
    }

    @SuppressWarnings("unused")
    static final class LoginScaffold {
        private final Region outerFrame;
        private final Region innerFrame;
        private final Pane decorLayer;
        private final VBox header;
        private final HBox logoBox;

        LoginScaffold(Region outerFrame, Region innerFrame, Pane decorLayer, VBox header, HBox logoBox) {
            this.outerFrame = outerFrame;
            this.innerFrame = innerFrame;
            this.decorLayer = decorLayer;
            this.header = header;
            this.logoBox = logoBox;
        }

        Region outerFrame() {
            return outerFrame;
        }

        Region innerFrame() {
            return innerFrame;
        }

        Pane decorLayer() {
            return decorLayer;
        }

        VBox header() {
            return header;
        }

        HBox logoBox() {
            return logoBox;
        }
    }

    private static final class LabelBundle {
        final VBox header;

        LabelBundle(VBox header) {
            this.header = header;
        }
    }
}