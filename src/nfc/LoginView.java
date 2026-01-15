package nfc;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

// Firebase Firestore imports
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;

// Java utility import
import java.util.List;

public class LoginView extends Application {

    private ImageView logoView;

    // ---- Theme ----
    private static final String BG_GRADIENT =
            "-fx-background-color: linear-gradient(to bottom right, #8eea9a 0%, #12b913 100%);";
    private static final String CARD_BG = "#FBF7EA";   // warm cream
    private static final String CARD_STROKE = "#2C6B39";
    private static final String BTN_GREEN = "#19A52E";

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Taska Zurah Student Management System");

        // Root
        AnchorPane root = new AnchorPane();
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #30b04a;"); // dark green outside

     // =========================
     // Double frame: outer dotted + inner solid
     // =========================

     // Outer: dark-green panel with dotted yellow border
     Region outerFrame = new Region();
     outerFrame.setStyle(
         "-fx-background-color: #2E8B57;" +                 // dark green outside
         "-fx-background-radius: 18;" +
         "-fx-border-color: #FFCF4D;" +                     // yellow dots
         "-fx-border-radius: 18;" +
         "-fx-border-width: 6;" +
         "-fx-border-style: segments(2,14) line-cap round;" + // dotted look
         "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 16, 0, 0, 4);"
     );
     AnchorPane.setTopAnchor(outerFrame, 12.0);
     AnchorPane.setRightAnchor(outerFrame, 12.0);
     AnchorPane.setBottomAnchor(outerFrame, 12.0);
     AnchorPane.setLeftAnchor(outerFrame, 12.0);

     // Inner: light-green panel with straight yellow border
     Region innerFrame = new Region();
     innerFrame.setStyle(
         "-fx-background-color: #86d67f;" +                 // light green inside
         "-fx-background-radius: 12;" +
         "-fx-border-color: #FFCF4D;" +                     // solid yellow line
         "-fx-border-radius: 12;" +
         "-fx-border-width: 3;" +
         "-fx-border-style: solid;"
     );
     AnchorPane.setTopAnchor(innerFrame, 28.0);
     AnchorPane.setRightAnchor(innerFrame, 28.0);
     AnchorPane.setBottomAnchor(innerFrame, 28.0);
     AnchorPane.setLeftAnchor(innerFrame, 28.0);
     
  // === Decorations layer (sits inside the light‑green frame) ===
     Pane decorLayer = new Pane();
     AnchorPane.setTopAnchor(decorLayer, 28.0);
     AnchorPane.setRightAnchor(decorLayer, 28.0);
     AnchorPane.setBottomAnchor(decorLayer, 28.0);
     AnchorPane.setLeftAnchor(decorLayer, 28.0);

// --- Load all decoration images safely ---
ImageView ivWhiteFlower = loadImage("white flower.png");
ImageView ivGrass       = loadImage("grass.png");
ImageView ivCloud       = loadImage("cloud.png");
ImageView ivBee         = loadImage("bee.png");
ImageView ivTeddy       = loadImage("teddy bear.png");
ImageView ivGirl        = loadImage("girl.png");
ImageView ivBoy         = loadImage("boy.png");
ImageView ivPinkFlower  = loadImage("pink flower.png");
ImageView ivYellowFlower= loadImage("yellow flower.png");
ImageView ivSun         = loadImage("sun.png");

     // nice default behavior
     for (ImageView iv : new ImageView[]{ivGrass, ivCloud, ivSun}) {
         iv.setPreserveRatio(true);
         iv.setOpacity(0.96);
     }

     // Add to layer first (so we can size/position them after layout)
     decorLayer.getChildren().addAll(ivGrass, ivCloud, ivSun);

     // When the layer resizes, (re)randomize positions
     Runnable layoutDecor = () -> {
         double W = decorLayer.getWidth();
         double H = decorLayer.getHeight();
         if (W <= 0 || H <= 0) return;

         // Sizes
         ivGrass.setFitWidth(W * 0.85);                       // wide grass at the bottom
         ivCloud.setFitWidth(W * 0.22);
         ivSun.setFitWidth(W * 0.20);

         // Place grass fixed to bottom center
         ivGrass.setLayoutX((W - ivGrass.getFitWidth()) / 2.0);
         ivGrass.setLayoutY(H - ivGrass.getFitWidth() * 0.15 - 10); // approximate height fraction

         // Sun near the top-right; cloud near the top-left
         ivSun.setLayoutX(W - ivSun.getFitWidth() -40);
         ivSun.setLayoutY(20);
         ivCloud.setLayoutX(20);
         ivCloud.setLayoutY(50);

     };

     // Re-layout on size changes
     decorLayer.widthProperty().addListener((o, a, b) -> layoutDecor.run());
     decorLayer.heightProperty().addListener((o, a, b) -> layoutDecor.run());



        // =========================
        // Header (smaller title)
        // =========================
        Label title = new Label("TASKA ZURAH STUDENT\nMANAGEMENT SYSTEM");
        title.setTextAlignment(TextAlignment.CENTER);
        title.setTextFill(Color.web("#2a3d2e"));
        title.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 48)); // smaller than earlier



        VBox header = new VBox(8, title);
        header.setAlignment(Pos.CENTER);
        AnchorPane.setTopAnchor(header, 52.0);
        AnchorPane.setLeftAnchor(header, 0.0);
        AnchorPane.setRightAnchor(header, 0.0);

        // =========================
        // Logo (slightly bigger)
        // =========================
        logoView = new ImageView();
java.net.URL url = LoginView.class.getResource("logo.png");
System.out.println("DEBUG >> Classpath URL: " + url);

if (url == null) {
    java.io.File f = new java.io.File("src/nfc/logo.png");
    System.out.println("DEBUG >> Fallback file exists: " + f.exists() + " @ " + f.getAbsolutePath());
    if (f.exists()) {
        try {
            url = f.toURI().toURL();
        } catch (java.net.MalformedURLException ex) {
            ex.printStackTrace();
        }
    }
}

if (url != null) {
    if (url != null) logoView.setImage(new Image(url.toExternalForm()));
    else System.out.println("⚠️ logo.png not found.");

    System.out.println("✅ Loaded logo: " + url);
} else {
    System.out.println("❌ ERROR: logo.png could not be loaded.");
}


        logoView.setPreserveRatio(true);
        logoView.setFitWidth(230); // logo size
        HBox logoBox = new HBox(logoView);
        logoBox.setAlignment(Pos.CENTER);
        AnchorPane.setTopAnchor(logoBox, 170.0); // smaller go up bigger go down
        AnchorPane.setLeftAnchor(logoBox, 0.0);
        AnchorPane.setRightAnchor(logoBox, 0.0);


        // =========================
        // Login Card (cream + thick green border + soft shadow)
        // =========================
        VBox card = new VBox(18);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(28));
        card.setPrefSize(460, 360);
        card.setMaxSize(460, 360);
        card.setStyle(
            "-fx-background-color: " + CARD_BG + ";" +
            "-fx-background-radius: 20;" +
            "-fx-border-color: " + CARD_STROKE + ";" +
            "-fx-border-width: 3;" +
            "-fx-border-radius: 20;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.22), 20, 0, 0, 10);"
        );

        // Labels
        Label userLbl = new Label("Username");
        userLbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 16));
        userLbl.setTextFill(Color.web("#2a3d2e"));

        Label passLbl = new Label("Password");
        passLbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 16));
        passLbl.setTextFill(Color.web("#2a3d2e"));

        // Username field + lock
        TextField username = new TextField();
        username.setPromptText("Enter username");
        username.setPrefSize(360, 44);
        username.setMaxSize(360, 44);
        username.setFont(Font.font("System", 16));
        username.setFont(Font.font("System", FontWeight.BOLD, 18));

        ImageView lockUser = new ImageView(ImageLoader.loadSafe("mangga1.png"));
        lockUser.setFitWidth(24);
        lockUser.setPreserveRatio(true);

     // Username row: icon INSIDE the bar
        HBox userRow = new HBox(10);
        userRow.setAlignment(Pos.CENTER_LEFT);
        userRow.setPadding(new Insets(0, 12, 0, 12));
        userRow.setPrefWidth(360);
        userRow.setMaxWidth(360);
        userRow.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 8;" +
            "-fx-border-radius: 8;" +
            "-fx-border-color: #2C6B39;" +
            "-fx-border-width: 2;"
        );

        // make the inner TextField flat so we see only the wrapper border
        username.setBackground(Background.EMPTY);
        username.setBorder(Border.EMPTY);

        // icon sizing
        lockUser.setFitWidth(20);
        lockUser.setPreserveRatio(true);

        userRow.getChildren().addAll(lockUser, username);


        // Password field + lock
        PasswordField password = new PasswordField();
        password.setPromptText("Enter password");
        password.setPrefSize(360, 44);
        password.setMaxSize(360, 44);
        password.setFont(Font.font("System", FontWeight.BOLD, 18));

        ImageView lockPass = new ImageView(ImageLoader.loadSafe("mangga2.png"));
        lockPass.setFitWidth(24);
        lockPass.setPreserveRatio(true);

     // Password row: icon INSIDE the bar
        HBox passRow = new HBox(10);
        passRow.setAlignment(Pos.CENTER_LEFT);
        passRow.setPadding(new Insets(0, 12, 0, 12));
        passRow.setPrefWidth(360);
        passRow.setMaxWidth(360);
        passRow.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 8;" +
            "-fx-border-radius: 8;" +
            "-fx-border-color: #C9C9C9;" +
            "-fx-border-width: 2;"
        );

        password.setBackground(Background.EMPTY);
        password.setBorder(Border.EMPTY);

        lockPass.setFitWidth(20);
        lockPass.setPreserveRatio(true);

        passRow.getChildren().addAll(lockPass, password);


        // Message
        Label msg = new Label();
        msg.setTextFill(Color.web("#c62828"));
        msg.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));

        // Login button
        Button loginBtn = new Button("Login");
        loginBtn.setDefaultButton(true);
        loginBtn.setPrefSize(220, 46);
        loginBtn.setStyle(
            "-fx-background-color: " + BTN_GREEN + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 18px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 26;"
        );

        loginBtn.setOnAction(e -> {
            String u = username.getText().trim();
            String p = password.getText().trim();

            if (u.isEmpty() || p.isEmpty()) {
                msg.setText("Please enter both fields.");
                return;
            }

            new Thread(() -> {
                try {
                    Firestore db = FirestoreService.db();
                    System.out.println("[LoginView] Firestore connected to: " + db.getOptions().getProjectId());

                    // Query the admins collection
                    ApiFuture<QuerySnapshot> query = db.collection("admins")
                            .whereEqualTo("username", u)
                            .limit(1)
                            .get();

                    List<QueryDocumentSnapshot> docs = query.get().getDocuments();

                    if (!docs.isEmpty()) {
                        QueryDocumentSnapshot doc = docs.get(0);
                        String storedPassword = doc.getString("password");

                        if (storedPassword != null && storedPassword.equals(p)) {
                            System.out.println("✅ Login successful for " + u);

                            // 🔥 READ FROM FIRESTORE
                            String usernameVal   = doc.getString("username");
                            String nameVal       = doc.getString("name");
                            String profilePicVal = doc.getString("profilePicture");

                            // 🔥 SET SESSION (THIS FIXES null 🐝)
                            UserSession.setAdmin(usernameVal, nameVal, profilePicVal);

                            // 🔎 DEBUG — MUST PRINT REAL VALUES
                            System.out.println("LOGIN SESSION:");
                            System.out.println("username = " + usernameVal);
                            System.out.println("name = " + nameVal);
                            System.out.println("profilePic = " + profilePicVal);

                            javafx.application.Platform.runLater(() -> {
                                try {
                                    new AdminDashboard().start(new Stage());
                                    primaryStage.close();
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            });
                        }
                        else {
                            System.out.println("❌ Wrong password for " + u);
                            javafx.application.Platform.runLater(() -> msg.setText("Incorrect password."));
                        }
                    } else {
                        System.out.println("❌ No such admin: " + u);
                        javafx.application.Platform.runLater(() -> msg.setText("User not found."));
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                    javafx.application.Platform.runLater(() ->
                        msg.setText("Error: " + ex.getMessage()));
                }
            }).start();
        });

        // Forgot password link
        Label forgot = new Label("Forgot password?");
        forgot.setTextFill(Color.web("#3D3D3D"));
        forgot.setUnderline(true);
        forgot.setOnMouseClicked(e -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                    "Please contact your system administrator.");
            a.setHeaderText(null);
            a.setTitle("Forgot Password");
            a.showAndWait();
        });

        // Inputs into card
        VBox inputs = new VBox(8, userLbl, userRow, passLbl, passRow);
        inputs.setAlignment(Pos.CENTER);

        card.getChildren().addAll(inputs, loginBtn, msg, forgot);

        // Keep card same size, centered, pushed down a bit
        StackPane cardHolder = new StackPane(card);
        StackPane.setAlignment(card, Pos.TOP_CENTER);
        StackPane.setMargin(card, new Insets(330, 0, 0, 0)); // adjust if needed
        AnchorPane.setTopAnchor(cardHolder, 0.0);
        AnchorPane.setRightAnchor(cardHolder, 0.0);
        AnchorPane.setBottomAnchor(cardHolder, 0.0);
        AnchorPane.setLeftAnchor(cardHolder, 0.0);

        // Layering: frame at back, then header/logo, then card
        root.getChildren().addAll(outerFrame, innerFrame, decorLayer, header, logoBox, cardHolder);

        // Show
        Scene scene = new Scene(root, 1000, 800);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // =========================
// Helper method for safe image loading
// =========================
private ImageView loadImage(String fileName) {
    java.net.URL url = getClass().getResource("/nfc/" + fileName);
    System.out.println("DEBUG >> Loading " + fileName + " => " + url);

    if (url == null) {
        java.io.File f = new java.io.File("src/nfc/" + fileName);
        if (f.exists()) {
            try {
                url = f.toURI().toURL();
            } catch (java.net.MalformedURLException ex) {
                ex.printStackTrace();
            }
        }
    }

    return (url != null)
            ? new ImageView(new Image(url != null ? url.toExternalForm() : "https://via.placeholder.com/60x60.png?text=Missing"))
            : new ImageView();

}

    public static void main(String[] args) {
        launch(args);
    }
}