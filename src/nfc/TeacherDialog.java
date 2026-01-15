package nfc;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import com.google.firebase.cloud.StorageClient;
import com.google.cloud.firestore.SetOptions;

public class TeacherDialog {

    private File imageFile;

    public TeacherDialog(Map<String, Object> data, Runnable refresh) {
        Stage stage = new Stage();

        TextField name = new TextField();
        name.setPromptText("Full Name");

        TextField username = new TextField();
        username.setPromptText("Username");

        TextField phone = new TextField();
        phone.setPromptText("Phone Number");

        TextField email = new TextField();
        email.setPromptText("Email Address");

        TextField clazz = new TextField();
        clazz.setPromptText("Class");

        Button imgBtn = new Button("Choose Image");
        imgBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            imageFile = fc.showOpenDialog(stage);
        });

        if (data != null) {
            name.setText((String) data.get("name"));
            username.setText((String) data.get("username"));
            phone.setText((String) data.get("phone"));
            email.setText((String) data.get("email"));
            clazz.setText((String) data.get("class"));
        }

        Button save = new Button("Save");
        save.setOnAction(e -> {
            try {
                Map<String, Object> m = new HashMap<>();
                m.put("name", name.getText());
                m.put("username", username.getText());
                m.put("phone", phone.getText());
                m.put("email", email.getText());
                m.put("class", clazz.getText());
                m.put("status", "active");

                var db = FirestoreService.db();

                if (data == null) {
                    db.collection("teachers")
                        .document(UUID.randomUUID().toString())
                        .set(m)
                        .get();   // ✅ chained correctly
                } else {
                    db.collection("teachers")
                        .document((String) data.get("id"))
                        .set(m, SetOptions.merge())
                        .get();   // ✅ chained correctly
                }

                refresh.run();
                stage.close();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        VBox root = new VBox(10, name, username, phone, email, clazz, imgBtn, save);
        root.setPadding(new Insets(20));
        stage.setScene(new Scene(root));
        stage.show();
    }
}