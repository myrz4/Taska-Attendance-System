package nfc;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;


public class RegisterChildView {

    private String tagId;

    public RegisterChildView(String tagId) {
        this.tagId = tagId;
        showForm();
    }

    private void showForm() {
        Stage stage = new Stage();
        stage.setTitle("Register New Child");

        Label label = new Label("Register New Child for Card: " + tagId);
        label.setStyle("-fx-font-weight: bold;");

        TextField nameField = new TextField();
        nameField.setPromptText("Enter Child's Name");

        Button saveButton = new Button("Save");
        Label messageLabel = new Label();

        saveButton.setOnAction(e -> {
            String childName = nameField.getText().trim();

            if (childName.isEmpty()) {
                messageLabel.setText("❌ Please enter child's name.");
                return;
            }
            System.out.println("⚠️ Skipped MySQL child insert — Firestore integration coming soon.");
        });


        VBox layout = new VBox(10, label, nameField, saveButton, messageLabel);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);

        Scene scene = new Scene(layout, 350, 250);
        stage.setScene(scene);
        stage.show();
    }
}