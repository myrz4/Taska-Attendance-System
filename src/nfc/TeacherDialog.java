package nfc;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class TeacherDialog {

    public static TeacherDialog open(Map<String, Object> data, Runnable refresh) {
        return new TeacherDialog(data, refresh);
    }

    private static int parseMoneyToSen(String input, int fallbackSen) {
        if (input == null) return fallbackSen;
        String s = input.trim().replace(",", "");
        if (s.isEmpty()) return fallbackSen;
        try {
            double v = Double.parseDouble(s);
            return Math.max(0, (int) Math.round(v * 100.0));
        } catch (NumberFormatException ignored) {
            return fallbackSen;
        }
    }

    private static String senToMoneyText(Object rawSen, String fallback) {
        if (!(rawSen instanceof Number)) return fallback;
        double v = ((Number) rawSen).doubleValue() / 100.0;
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private static String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String sanitizePathPart(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "_");
    }

    private static String guessContentType(File file) {
        try {
            String ct = Files.probeContentType(file.toPath());
            if (ct != null && !ct.isBlank()) return ct;
        } catch (java.io.IOException | SecurityException ignored) {
        }
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    private static String extLower(File f) {
        if (f == null) return "";
        String n = f.getName();
        int idx = n.lastIndexOf('.');
        if (idx < 0 || idx + 1 >= n.length()) return "";
        return n.substring(idx + 1).toLowerCase();
    }


    public TeacherDialog(Map<String, Object> data, Runnable refresh) {
        Map<String, Object> editData = data == null ? java.util.Collections.<String, Object>emptyMap() : data;
        Stage stage = new Stage();
        boolean isEdit = (data != null);

        TextField name = new TextField();
        name.setPromptText("Full Name");

        TextField username = new TextField();
        username.setPromptText("Username");

        TextField email = new TextField();
        email.setPromptText("Email");

        TextField phone = new TextField();
        phone.setPromptText("Phone Number");

        TextField imageUrl = new TextField();
        imageUrl.setPromptText("Image URL (Firebase Storage download URL)");

        TextField baseSalaryTf = new TextField();
        baseSalaryTf.setPromptText("Contoh: 1800.00");
        TextField otAfter530Tf = new TextField();
        otAfter530Tf.setPromptText("Contoh: 5.00 / jam");
        TextField ot8to12Tf = new TextField();
        ot8to12Tf.setPromptText("Contoh: 10.00 / jam");
        TextField ot12to7Tf = new TextField();
        ot12to7Tf.setPromptText("Contoh: 7.00 / jam");
        CheckBox salaryActiveCb = new CheckBox("Aktif untuk kiraan gaji bulanan");
        salaryActiveCb.setSelected(true);

        Button uploadImageBtn = new Button("Upload...");
        uploadImageBtn.setStyle("-fx-background-color: #FFCB3C; -fx-background-radius: 16;");

        if (data != null) {
            name.setText(safeString(data.get("name")));
            username.setText(safeString(data.get("username")));
            email.setText(safeString(data.get("email")));
            phone.setText(safeString(data.get("phone")));
            imageUrl.setText(safeString(data.get("image")));
            baseSalaryTf.setText(senToMoneyText(data.get("salaryBaseSen"), ""));
            otAfter530Tf.setText(senToMoneyText(data.get("salaryOvertimeAfter530Sen"), "5.00"));
            ot8to12Tf.setText(senToMoneyText(data.get("salaryOvertime8to12Sen"), "10.00"));
            ot12to7Tf.setText(senToMoneyText(data.get("salaryOvertime12to7Sen"), "7.00"));
            if (data.get("salaryActive") instanceof Boolean) {
                salaryActiveCb.setSelected(Boolean.TRUE.equals(data.get("salaryActive")));
            }
        }

        HBox imageRow = new HBox(10, imageUrl, uploadImageBtn);
        imageRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(imageUrl, Priority.ALWAYS);

        uploadImageBtn.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Teacher Profile Image");
            fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.webp", "*.gif")
            );
            File file = fc.showOpenDialog(stage);
            if (file == null) return;

            uploadImageBtn.setDisable(true);
            uploadImageBtn.setText("Uploading...");

            CompletableFuture.supplyAsync(() -> {
                try {
                    String ct = guessContentType(file);
                    byte[] bytes = Files.readAllBytes(file.toPath());

                    String ext = extLower(file);
                    String base = sanitizePathPart(name.getText());
                    if (base.isBlank()) base = "teacher";
                    String object = "teacher_image/teacher_" + base + "_" + Instant.now().toEpochMilli();
                    if (!ext.isBlank()) object += "." + ext;

                    return FirebaseStorageRest.uploadPublicDownloadUrl(object, bytes, ct);
                } catch (java.io.IOException | InterruptedException | RuntimeException ex) {
                    throw new RuntimeException(ex);
                }
            }).whenComplete((url, err) -> Platform.runLater(() -> {
                uploadImageBtn.setDisable(false);
                uploadImageBtn.setText("Upload...");

                if (err != null) {
                    System.err.println("TeacherDialog: upload failed - " + err.getMessage());
                    new Alert(Alert.AlertType.ERROR, "Upload failed: " + err.getMessage()).showAndWait();
                    return;
                }

                if (url != null && !url.isBlank()) {
                    imageUrl.setText(url);
                    ImageCache.prefetch(url);
                }
            }));
        });

        Button save = new Button("Save");
        save.setOnAction(e -> {
            try {
                String fullName = name.getText() == null ? "" : name.getText().trim();
                if (fullName.isBlank()) {
                    new Alert(Alert.AlertType.ERROR, "Full Name cannot be empty.").showAndWait();
                    return;
                }

                String usernameVal = username.getText() == null ? "" : username.getText().trim().toLowerCase();
                String emailVal = email.getText() == null ? "" : email.getText().trim().toLowerCase();

                String phoneLocal = PhoneUtil.toLocalMy(phone.getText());
                if (phoneLocal == null || phoneLocal.isBlank()) {
                    new Alert(Alert.AlertType.ERROR, "Phone cannot be empty.").showAndWait();
                    return;
                }

                Map<String, Object> m = new HashMap<>();
                m.put("name", fullName);
                // Always persist these so edits (including clearing) are reflected in Firestore.
                m.put("username", usernameVal);
                m.put("email", emailVal);
                m.put("phone", phoneLocal);
                m.put("phoneTail", PhoneUtil.myTail(phoneLocal));
                m.put("phoneE164", PhoneUtil.toE164My(phoneLocal));

                int salaryBaseSen = parseMoneyToSen(baseSalaryTf.getText(), 0);
                int salaryOt530Sen = parseMoneyToSen(otAfter530Tf.getText(), 500);
                int salaryOt8to12Sen = parseMoneyToSen(ot8to12Tf.getText(), 1000);
                int salaryOt12to7Sen = parseMoneyToSen(ot12to7Tf.getText(), 700);
                m.put("salaryBaseSen", salaryBaseSen);
                m.put("salaryOvertimeAfter530Sen", salaryOt530Sen);
                m.put("salaryOvertime8to12Sen", salaryOt8to12Sen);
                m.put("salaryOvertime12to7Sen", salaryOt12to7Sen);
                m.put("salaryCurrency", "MYR");
                m.put("salaryActive", salaryActiveCb.isSelected());

                String imageUrlText = imageUrl.getText() == null ? "" : imageUrl.getText().trim();
                if (!imageUrlText.isEmpty()) {
                    m.put("image", imageUrlText);
                    // Cache to disk to make subsequent loads faster.
                    ImageCache.prefetch(imageUrlText);
                }

                FirestoreRestClient client = FirestoreRest.forCurrentUser();

                if (!isEdit) {
                    // Best-effort duplicate check by phone.
                    for (FsDocument d : client.listDocuments("teachers")) {
                        if (d == null) continue;
                        String ph = d.getString("phone");
                        String phNorm = PhoneUtil.toLocalMy(ph);
                        if (phNorm != null && !phNorm.isBlank() && phNorm.equals(phoneLocal)) {
                            new Alert(Alert.AlertType.ERROR, "A teacher with this phone number already exists.").showAndWait();
                            return;
                        }
                    }
                    client.addDocumentAutoId("teachers", m);
                } else {
                    String teacherId = safeString(editData.get("id")).trim();
                    if (teacherId.isEmpty()) {
                        new Alert(Alert.AlertType.ERROR, "Missing teacher ID.").showAndWait();
                        return;
                    }
                    client.patchDocumentMerge("teachers", teacherId, m);
                }

                refresh.run();
                stage.close();

            } catch (java.io.IOException | InterruptedException | RuntimeException ex) {
                System.err.println("TeacherDialog: failed to save teacher - " + ex.getMessage());
                new Alert(Alert.AlertType.ERROR, "Failed to save teacher: " + ex.getMessage()).showAndWait();
            }
        });

        VBox root = new VBox(
            10,
            new Label("Full Name"), name,
            new Label("Username"), username,
            new Label("Email"), email,
            new Label("Phone"), phone,
            new Label("Image"), imageRow,
            new Label("Base Salary (RM / month)"), baseSalaryTf,
            new Label("Overtime Rate 5:30pm+ (RM / hour)"), otAfter530Tf,
            new Label("Overtime Rate 8pm-12am (RM / hour)"), ot8to12Tf,
            new Label("Overtime Rate 12am-7am (RM / hour)"), ot12to7Tf,
            salaryActiveCb,
            save
        );
        root.setPadding(new Insets(20));
        stage.setScene(new Scene(root));
        stage.show();
    }
}