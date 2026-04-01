package nfc;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

final class LoginViewFormSupport {
    private LoginViewFormSupport() {
    }

    static LoginFormBundle buildLoginCard(String cardBg, String cardStroke, String buttonGreen) {
        VBox card = new VBox(18);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(28));
        card.setPrefSize(460, 360);
        card.setMaxSize(460, 360);
        card.setStyle(
            "-fx-background-color: " + cardBg + ";"
                + "-fx-background-radius: 20;"
                + "-fx-border-color: " + cardStroke + ";"
                + "-fx-border-width: 3;"
                + "-fx-border-radius: 20;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.22), 20, 0, 0, 10);"
        );

        Label userLbl = new Label("Username");
        userLbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 16));
        userLbl.setTextFill(Color.web("#2a3d2e"));

        Label passLbl = new Label("Password");
        passLbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 16));
        passLbl.setTextFill(Color.web("#2a3d2e"));

        TextField username = new TextField();
        username.setPromptText("Enter username");
        username.setPrefSize(360, 44);
        username.setMaxSize(360, 44);
        username.setFont(Font.font("System", FontWeight.BOLD, 18));

        ImageView lockUser = new ImageView(ImageLoader.loadSafe("mangga1.png"));
        lockUser.setFitWidth(20);
        lockUser.setPreserveRatio(true);

        HBox userRow = new HBox(10);
        userRow.setAlignment(Pos.CENTER_LEFT);
        userRow.setPadding(new Insets(0, 12, 0, 12));
        userRow.setPrefWidth(360);
        userRow.setMaxWidth(360);
        userRow.setStyle(
            "-fx-background-color: white;"
                + "-fx-background-radius: 8;"
                + "-fx-border-radius: 8;"
                + "-fx-border-color: #2C6B39;"
                + "-fx-border-width: 2;"
        );
        username.setBackground(Background.EMPTY);
        username.setBorder(Border.EMPTY);
        userRow.getChildren().addAll(lockUser, username);

        PasswordField password = new PasswordField();
        password.setPromptText("Enter password");
        password.setPrefSize(360, 44);
        password.setMaxSize(360, 44);
        password.setFont(Font.font("System", FontWeight.BOLD, 18));

        ImageView lockPass = new ImageView(ImageLoader.loadSafe("mangga2.png"));
        lockPass.setFitWidth(20);
        lockPass.setPreserveRatio(true);

        HBox passRow = new HBox(10);
        passRow.setAlignment(Pos.CENTER_LEFT);
        passRow.setPadding(new Insets(0, 12, 0, 12));
        passRow.setPrefWidth(360);
        passRow.setMaxWidth(360);
        passRow.setStyle(
            "-fx-background-color: white;"
                + "-fx-background-radius: 8;"
                + "-fx-border-radius: 8;"
                + "-fx-border-color: #C9C9C9;"
                + "-fx-border-width: 2;"
        );
        password.setBackground(Background.EMPTY);
        password.setBorder(Border.EMPTY);
        passRow.getChildren().addAll(lockPass, password);

        Label msg = new Label();
        msg.setTextFill(Color.web("#c62828"));
        msg.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));

        Button loginBtn = new Button("Login");
        loginBtn.setDefaultButton(true);
        loginBtn.setPrefSize(220, 46);
        loginBtn.setStyle(
            "-fx-background-color: " + buttonGreen + ";"
                + "-fx-text-fill: white;"
                + "-fx-font-size: 18px;"
                + "-fx-font-weight: bold;"
                + "-fx-background-radius: 26;"
        );

        Label forgot = new Label("Forgot password?");
        forgot.setTextFill(Color.web("#3D3D3D"));
        forgot.setUnderline(true);

        VBox inputs = new VBox(8, userLbl, userRow, passLbl, passRow);
        inputs.setAlignment(Pos.CENTER);

        card.getChildren().addAll(inputs, loginBtn, msg, forgot);

        StackPane cardHolder = new StackPane(card);
        StackPane.setAlignment(card, Pos.TOP_CENTER);
        StackPane.setMargin(card, new Insets(330, 0, 0, 0));
        AnchorPane.setTopAnchor(cardHolder, 0.0);
        AnchorPane.setRightAnchor(cardHolder, 0.0);
        AnchorPane.setBottomAnchor(cardHolder, 0.0);
        AnchorPane.setLeftAnchor(cardHolder, 0.0);

        return new LoginFormBundle(cardHolder, username, password, msg, loginBtn, forgot);
    }

    static final class LoginFormBundle {
        final StackPane cardHolder;
        final TextField username;
        final PasswordField password;
        final Label msg;
        final Button loginBtn;
        final Label forgot;

        LoginFormBundle(StackPane cardHolder, TextField username, PasswordField password, Label msg, Button loginBtn, Label forgot) {
            this.cardHolder = cardHolder;
            this.username = username;
            this.password = password;
            this.msg = msg;
            this.loginBtn = loginBtn;
            this.forgot = forgot;
        }
    }
}