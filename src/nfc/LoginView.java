package nfc;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import javafx.application.Platform;

public class LoginView extends Application {

    // ---- Theme ----
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

        LoginViewLayoutSupport.LoginScaffold scaffold = LoginViewLayoutSupport.buildScaffold(getClass());
        LoginViewFormSupport.LoginFormBundle form = LoginViewFormSupport.buildLoginCard(CARD_BG, CARD_STROKE, BTN_GREEN);

        form.loginBtn.setOnAction(e -> {
            String u = form.username.getText().trim();
            String p = form.password.getText().trim();

            if (u.isEmpty() || p.isEmpty()) {
                form.msg.setText("Please enter both fields.");
                return;
            }

            new Thread(() -> {
                try {
                    LoginAuthSupport.authenticateAndBootstrapSession(u, p);

                    Platform.runLater(() -> {
                        try {
                            new AdminDashboard().start(new Stage());
                            primaryStage.close();
                        } catch (Exception ex) {
                            System.err.println("LoginView: failed to launch AdminDashboard - " + ex.getMessage());
                            form.msg.setText("Error: " + ex.getMessage());
                        }
                    });

                } catch (FirebaseAuthClient.FirebaseAuthException fae) {
                    System.err.println("LoginView: Firebase auth failed - " + fae.code + " - " + fae.getMessage());
                    Platform.runLater(() -> form.msg.setText(LoginAuthSupport.prettyAuthError(fae.code)));
                } catch (java.io.IOException | RuntimeException ex) {
                    System.err.println("LoginView: login failed - " + ex.getMessage());
                    Platform.runLater(() -> form.msg.setText("Login failed: " + ex.getMessage()));
                }
            }).start();
        });

        form.forgot.setOnMouseClicked(e -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                    "Please contact your system administrator.");
            a.setHeaderText(null);
            a.setTitle("Forgot Password");
            a.showAndWait();
        });

        // Layering: frame at back, then header/logo, then card
        root.getChildren().addAll(
            scaffold.outerFrame,
            scaffold.innerFrame,
            scaffold.decorLayer,
            scaffold.header,
            scaffold.logoBox,
            form.cardHolder
        );

        // Show
        Scene scene = new Scene(root, 1000, 800);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}