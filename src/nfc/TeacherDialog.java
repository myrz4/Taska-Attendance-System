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
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
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
        ImageView imagePreview = new ImageView();
        imagePreview.setFitWidth(112);
        imagePreview.setFitHeight(136);
        imagePreview.setPreserveRatio(true);

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
        AppThemeSupport.styleToolbarButtons(uploadImageBtn);

        if (data != null) {
            name.setText(safeString(data.get("name")));
            username.setText(safeString(data.get("username")));
            email.setText(safeString(data.get("email")));
            phone.setText(safeString(data.get("phone")));
            imageUrl.setText(safeString(data.get("image")));
            CRUDStaffImageSupport.loadExistingPreview(safeString(data.get("image")), imagePreview);
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
                    AppThemeSupport.showError(stage, "Upload failed", String.valueOf(err.getMessage()));
                    return;
                }

                if (url != null && !url.isBlank()) {
                    imageUrl.setText(url);
                    ImageCache.prefetch(url);
                    CRUDStaffImageSupport.loadExistingPreview(url, imagePreview);
                }
            }));
        });

        Button save = new Button("Save");
        Button cancel = new Button("Cancel");
        AppThemeSupport.stylePrimaryButtons(save);
        AppThemeSupport.styleSecondaryButtons(cancel);
        AppThemeSupport.styleControls(
            name,
            username,
            email,
            phone,
            imageUrl,
            baseSalaryTf,
            otAfter530Tf,
            ot8to12Tf,
            ot12to7Tf
        );
        cancel.setOnAction(e -> stage.close());
        save.setOnAction(e -> {
            try {
                String fullName = name.getText() == null ? "" : name.getText().trim();
                if (fullName.isBlank()) {
                    AppThemeSupport.showError(stage, "Full Name Required", "Full Name cannot be empty.");
                    return;
                }

                String usernameVal = username.getText() == null ? "" : username.getText().trim().toLowerCase();
                String emailVal = email.getText() == null ? "" : email.getText().trim().toLowerCase();

                String phoneLocal = PhoneUtil.toLocalMy(phone.getText());
                if (phoneLocal == null || phoneLocal.isBlank()) {
                    AppThemeSupport.showError(stage, "Phone Required", "Phone cannot be empty.");
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
                            AppThemeSupport.showError(stage, "Duplicate Phone", "A teacher with this phone number already exists.");
                            return;
                        }
                    }
                    client.addDocumentAutoId("teachers", m);
                } else {
                    String teacherId = safeString(editData.get("id")).trim();
                    if (teacherId.isEmpty()) {
                        AppThemeSupport.showError(stage, "Missing Teacher ID", "Missing teacher ID.");
                        return;
                    }
                    client.patchDocumentMerge("teachers", teacherId, m);
                }

                refresh.run();
                AppThemeSupport.showToast(stage, "Teacher Saved", fullName + " was saved successfully.", AppThemeSupport.Tone.SUCCESS);
                stage.close();

            } catch (java.io.IOException | InterruptedException | RuntimeException ex) {
                System.err.println("TeacherDialog: failed to save teacher - " + ex.getMessage());
                AppThemeSupport.showError(stage, "Failed to save teacher", String.valueOf(ex.getMessage()));
            }
        });

        GridPane identityGrid = new GridPane();
        identityGrid.setHgap(12);
        identityGrid.setVgap(10);
        identityGrid.getStyleClass().add("app-form-grid");
        identityGrid.addRow(0, new Label("Full Name"), name);
        identityGrid.addRow(1, new Label("Username"), username);

        GridPane contactGrid = new GridPane();
        contactGrid.setHgap(12);
        contactGrid.setVgap(10);
        contactGrid.getStyleClass().add("app-form-grid");
        contactGrid.addRow(0, new Label("Email"), email);
        contactGrid.addRow(1, new Label("Phone"), phone);

        GridPane imageGrid = new GridPane();
        imageGrid.setHgap(12);
        imageGrid.setVgap(10);
        imageGrid.getStyleClass().add("app-form-grid");
        imageGrid.addRow(0, new Label("Image URL"), imageRow);
        imageGrid.add(imagePreview, 1, 1);

        GridPane salaryGrid = new GridPane();
        salaryGrid.setHgap(12);
        salaryGrid.setVgap(10);
        salaryGrid.getStyleClass().add("app-form-grid");
        salaryGrid.addRow(0, new Label("Base Salary (RM / month)"), baseSalaryTf);
        salaryGrid.addRow(1, new Label("Overtime Rate 5:30pm+ (RM / hour)"), otAfter530Tf);
        salaryGrid.addRow(2, new Label("Overtime Rate 8pm-12am (RM / hour)"), ot8to12Tf);
        salaryGrid.addRow(3, new Label("Overtime Rate 12am-7am (RM / hour)"), ot12to7Tf);

        VBox formContent = new VBox(16,
            AppThemeSupport.createSectionCard(
                "Identity",
                "Store the teacher's main identity used throughout the admin system.",
                identityGrid
            ),
            AppThemeSupport.createSectionCard(
                "Contact",
                "Keep email and local Malaysia phone details consistent for admin messaging and payroll follow-up.",
                contactGrid
            ),
            AppThemeSupport.createSectionCard(
                "Image & Profile",
                "Use the upload action to push a profile photo into Firebase Storage and keep the URL synced.",
                imageGrid
            ),
            AppThemeSupport.createSectionCard(
                "Salary & Overtime",
                "Monthly salary and overtime rates stay in RM and keep the existing payroll math untouched.",
                salaryGrid
            ),
            AppThemeSupport.createSectionCard(
                "Payroll Flags",
                "Disable this only when the teacher should be excluded from monthly payroll runs.",
                salaryActiveCb
            )
        );

        ScrollPane scrollPane = new ScrollPane(formContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("app-dialog-scroll");

        HBox footer = new HBox(10, cancel, save);
        footer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        VBox root = new VBox(
            16,
            AppThemeSupport.createPageHeader(
                isEdit ? "Edit Teacher" : "Add Teacher",
                "Manage teacher identity, contact, profile image, and payroll settings in one place."
            ),
            scrollPane,
            footer
        );
        root.getStyleClass().add("app-page-root");
        root.setPadding(new Insets(16));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Scene scene = new Scene(root, 680, 760);
        AppThemeSupport.applyScene(scene);
        stage.setTitle(isEdit ? "Edit Teacher" : "Add Teacher");
        stage.setMinWidth(620);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.show();
    }
}