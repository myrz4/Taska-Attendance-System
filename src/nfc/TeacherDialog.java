package nfc;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
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

    private static LocalDate safeLocalDate(Object value) {
        String text = safeString(value).trim();
        if (text.isEmpty()) return null;
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String teacherAddressText(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }
        String homeAddress = safeString(data.get("homeAddress")).trim();
        if (!homeAddress.isEmpty()) {
            return homeAddress;
        }
        String streetAddress = safeString(data.get("streetAddress")).trim();
        String city = safeString(data.get("city")).trim();
        String state = safeString(data.get("state")).trim();
        String postcode = safeString(data.get("postcode")).trim();
        StringBuilder sb = new StringBuilder();
        if (!streetAddress.isEmpty()) {
            sb.append(streetAddress);
        }
        if (!city.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        if (!state.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(state);
        }
        if (!postcode.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(postcode);
        }
        return sb.toString();
    }

    private static String teacherGenderChoice(Object value) {
        String text = safeString(value).trim();
        if (text.equalsIgnoreCase("female")) {
            return "Female";
        }
        if (text.equalsIgnoreCase("male")) {
            return "Male";
        }
        return "";
    }

    private static String comboValue(ComboBox<String> comboBox) {
        if (comboBox == null) return "";
        if (comboBox.isEditable() && comboBox.getEditor() != null) {
            String editorText = comboBox.getEditor().getText();
            if (editorText != null && !editorText.trim().isEmpty()) {
                return editorText.trim();
            }
        }
        Object value = comboBox.getValue();
        return value == null ? "" : String.valueOf(value).trim();
    }

    public TeacherDialog(Map<String, Object> data, Runnable refresh) {
        Map<String, Object> editData = data == null ? java.util.Collections.<String, Object>emptyMap() : data;
        Stage stage = new Stage();
        boolean isEdit = (data != null);

        TextField name = new TextField();
        name.setPromptText("Full Name");

        TextField phone = new TextField();
        phone.setPromptText("Phone Number");

        TextField email = new TextField();
        email.setPromptText("Email");

        TextField personalIdentification = new TextField();
        personalIdentification.setPromptText("IC No.");
        personalIdentification.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> gender = new ComboBox<>();
        gender.getItems().setAll("Female", "Male");
        gender.setPromptText("Select Gender");
        gender.setMaxWidth(Double.MAX_VALUE);

        DatePicker dateOfBirth = new DatePicker();
        dateOfBirth.setPromptText("YYYY-MM-DD");
        dateOfBirth.setEditable(false);
        dateOfBirth.setMaxWidth(Double.MAX_VALUE);

        TextField homeAddress = new TextField();
        homeAddress.setPromptText("Alamat Rumah / Home Address");

        TextField nationality = new TextField();
        nationality.setPromptText("Nationality");
        nationality.setMaxWidth(Double.MAX_VALUE);

        TextField imageUrl = new TextField();
        imageUrl.setPromptText("Image URL (Firebase Storage download URL)");
        ImageView imagePreview = new ImageView();
        imagePreview.setFitWidth(112);
        imagePreview.setFitHeight(136);
        imagePreview.setPreserveRatio(true);

        TextField baseSalaryTf = new TextField();
        baseSalaryTf.setPromptText("Contoh: 1800.00");
        CheckBox salaryActiveCb = new CheckBox("Aktif untuk kiraan gaji bulanan");
        salaryActiveCb.setSelected(true);

        Button uploadImageBtn = new Button("Upload...");
        AppThemeSupport.styleToolbarButtons(uploadImageBtn);

        if (data != null) {
            name.setText(safeString(data.get("name")));
            phone.setText(safeString(data.get("phone")));
            email.setText(safeString(data.get("email")));
            personalIdentification.setText(safeString(data.get("personalIdentification")));
            gender.setValue(teacherGenderChoice(data.get("gender")));
            dateOfBirth.setValue(safeLocalDate(data.get("dateOfBirth")));
            homeAddress.setText(teacherAddressText(data));
            nationality.setText(safeString(data.get("nationality")).trim());
            imageUrl.setText(safeString(data.get("image")));
            CRUDStaffImageSupport.loadExistingPreview(safeString(data.get("image")), imagePreview);
            baseSalaryTf.setText(senToMoneyText(data.get("salaryBaseSen"), ""));
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
            phone,
            email,
            personalIdentification,
            gender,
            dateOfBirth,
            homeAddress,
            nationality,
            imageUrl,
            baseSalaryTf
        );
        cancel.setOnAction(e -> stage.close());
        save.setOnAction(e -> {
            try {
                String fullName = name.getText() == null ? "" : name.getText().trim();
                if (fullName.isBlank()) {
                    AppThemeSupport.showError(stage, "Full Name Required", "Full Name cannot be empty.");
                    return;
                }

                String emailVal = email.getText() == null ? "" : email.getText().trim().toLowerCase();
                String personalIdentificationVal = personalIdentification.getText() == null
                    ? ""
                    : personalIdentification.getText().trim();
                String genderVal = comboValue(gender);
                String dateOfBirthVal = dateOfBirth.getValue() == null ? "" : dateOfBirth.getValue().toString();
                String homeAddressVal = homeAddress.getText() == null ? "" : homeAddress.getText().trim();
                String nationalityVal = nationality.getText() == null ? "" : nationality.getText().trim();

                String phoneLocal = PhoneUtil.toLocalMy(phone.getText());
                if (phoneLocal == null || phoneLocal.isBlank()) {
                    AppThemeSupport.showError(stage, "Phone Required", "Phone cannot be empty.");
                    return;
                }

                Map<String, Object> m = new HashMap<>();
                m.put("name", fullName);
                m.put("email", emailVal);
                m.put("phone", phoneLocal);
                m.put("phoneTail", PhoneUtil.myTail(phoneLocal));
                m.put("phoneE164", PhoneUtil.toE164My(phoneLocal));
                m.put("personalIdentification", personalIdentificationVal);
                m.put("gender", genderVal);
                m.put("dateOfBirth", dateOfBirthVal);
                m.put("homeAddress", homeAddressVal);
                m.put("nationality", nationalityVal);

                String baseSalaryText = baseSalaryTf.getText() == null ? "" : baseSalaryTf.getText().trim();
                int salaryBaseSen = parseMoneyToSen(baseSalaryText, -1);
                if (salaryBaseSen <= 0) {
                    AppThemeSupport.showError(stage, "Base Salary Required", "Base Salary (RM / month) must be a number greater than 0.");
                    return;
                }
                m.put("salaryBaseSen", salaryBaseSen);
                m.put("salaryCurrency", "MYR");
                m.put("salaryActive", salaryActiveCb.isSelected());
                m.put("status", salaryActiveCb.isSelected() ? "Active" : "Inactive");
                m.put("joinedDate", safeString(editData.get("joinedDate")).trim().isEmpty()
                    ? LocalDate.now().toString()
                    : safeString(editData.get("joinedDate")).trim());
                m.put("updatedAt", Instant.now().toString());
                if (safeString(editData.get("createdAt")).trim().isEmpty()) {
                    m.put("createdAt", Instant.now().toString());
                }

                String imageUrlText = imageUrl.getText() == null ? "" : imageUrl.getText().trim();
                if (!imageUrlText.isEmpty()) {
                    m.put("image", imageUrlText);
                    ImageCache.prefetch(imageUrlText);
                }

                FirestoreRestClient client = FirestoreRest.forCurrentUser();

                if (!isEdit) {
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
                    client.patchDocumentMergeDeletingFields(
                        "teachers",
                        teacherId,
                        m,
                        java.util.List.of(
                            "username",
                            "personalIdentificationType",
                            "streetAddress",
                            "city",
                            "state",
                            "postcode",
                            "salaryOvertimeAfter530Sen",
                            "salaryOvertime8to12Sen",
                            "salaryOvertime12to7Sen"
                        )
                    );
                }

                refresh.run();
                AppThemeSupport.showToast(stage, "Teacher Saved", fullName + " was saved successfully.", AppThemeSupport.Tone.SUCCESS);
                stage.close();

            } catch (java.io.IOException | InterruptedException | RuntimeException ex) {
                System.err.println("TeacherDialog: failed to save teacher - " + ex.getMessage());
                AppThemeSupport.showError(stage, "Failed to save teacher", String.valueOf(ex.getMessage()));
            }
        });

        GridPane registrationGrid = new GridPane();
        registrationGrid.setHgap(12);
        registrationGrid.setVgap(10);
        registrationGrid.getStyleClass().add("app-form-grid");
        registrationGrid.addRow(0, new Label("Full Name"), name);
        registrationGrid.addRow(1, new Label("Phone"), phone);
        registrationGrid.addRow(2, new Label("Email"), email);
        registrationGrid.addRow(3, new Label("IC No."), personalIdentification);
        registrationGrid.addRow(4, new Label("Gender"), gender);
        registrationGrid.addRow(5, new Label("Date of Birth"), dateOfBirth);
        registrationGrid.addRow(6, new Label("Alamat Rumah / Home Address"), homeAddress);
        registrationGrid.addRow(7, new Label("Nationality"), nationality);

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

        VBox formContent = new VBox(16,
            AppThemeSupport.createSectionCard(
                "Teacher Registration",
                "Capture the stakeholder-required teacher identity, home address, and contact details used during registration.",
                registrationGrid
            ),
            AppThemeSupport.createSectionCard(
                "Image & Profile",
                "Use the upload action to push a profile photo into Firebase Storage and keep the URL synced.",
                imageGrid
            ),
            AppThemeSupport.createSectionCard(
                "Salary",
                "Monthly salary stays here. Teacher overtime pay now follows the shared Taska operating-hours policy instead of per-teacher rate overrides.",
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
                "Manage teacher registration, profile image, and payroll settings in one place."
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
        stage.setMinHeight(800);
        stage.setScene(scene);
        stage.show();
    }
}