package nfc;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class FamilyRecordViewDialogSupport {
    private FamilyRecordViewDialogSupport() {
    }

    static void showChildDetails(ChildrenView.Child child) {
        if (child == null) {
            return;
        }
        try {
            FsDocument document = loadDocument("children", child.getChildId(), "Child");
            if (document == null) {
                return;
            }

            Map<String, Object> fields = document.fields();
            LocalDate birthDate = parseLocalDate(fields.get("birthDate"));
            LocalDate registrationDate = parseLocalDate(fields.get("registrationDate"));
            LocalDate previewDate = registrationDate == null ? LocalDate.now() : registrationDate;
            CRUDChildValidationSupport.RegistrationPreview preview =
                CRUDChildValidationSupport.deriveRegistrationPreview(previewDate, birthDate, registrationDate);

            List<Node> sections = new ArrayList<>();

            LinkedHashMap<String, String> profileFields = new LinkedHashMap<>();
            profileFields.put("Record ID", child.getRecordId());
            profileFields.put("Child Name", child.getName());
            profileFields.put("Child ID / MyKid", child.getChildIdentifier());
            profileFields.put("Birth Certificate No", text(fields.get("birthCertNo")));
            profileFields.put("Gender", text(fields.get("gender")));
            profileFields.put("Birth Date", child.getBirthDateText());
            profileFields.put("Age", child.getAgeSummary());
            profileFields.put("Place of Birth", text(fields.get("placeOfBirth")));
            profileFields.put("No. of Siblings", text(fields.get("siblingsCount")));
            profileFields.put("Language Speaking", text(fields.get("languageSpeaking")));
            profileFields.put("Status", child.getStatus());
            sections.add(createDetailSection(
                "Child Profile",
                "Review the full child registration identity captured in Taska Zurah.",
                profileFields
            ));

            LinkedHashMap<String, String> familyFields = new LinkedHashMap<>();
            familyFields.put("Parent Names", child.getFullParentNames());
            familyFields.put("Parent Phones", child.getFullParentPhones());
            familyFields.put("Relationship", child.getParentRelationship());
            familyFields.put("Father's Phone No", text(fields.get("fatherPhone")));
            familyFields.put("Mother's Phone No", text(fields.get("motherPhone")));
            familyFields.put("Home Address", firstNonBlank(fields.get("homeAddress"), fields.get("address")));
            sections.add(createDetailSection(
                "Family & Contact",
                "Keep the family contact record complete before making edits.",
                familyFields
            ));

            LinkedHashMap<String, String> registrationFields = new LinkedHashMap<>();
            registrationFields.put("NFC UID", child.getNfcUid());
            registrationFields.put("Registration Date", text(fields.get("registrationDate")));
            registrationFields.put("Registration Fee Received By", text(fields.get("registrationReceivedBy")));
            registrationFields.put("Date Received", text(fields.get("registrationReceivedDate")));
            registrationFields.put("Receipt No", text(fields.get("registrationReceiptNo")));
            registrationFields.put("Cheque No", text(fields.get("registrationChequeNo")));
            sections.add(createDetailSection(
                "Registration & NFC",
                "These values are view-only here so the edit form stays focused on mutable fields.",
                registrationFields
            ));

            LinkedHashMap<String, String> billingFields = new LinkedHashMap<>();
            billingFields.put("Billing Model", firstNonBlank(fields.get("activeBillingModel"), child.getBillingPlan()));
            billingFields.put("Fee Policy Version", text(fields.get("feePolicyVersion")));
            billingFields.put("Age Band", describeAgeBand(firstNonBlank(fields.get("ageBand"), preview.billingProfile().ageBand())));
            billingFields.put("Monthly Fee Preview", moneyText(firstNonBlankNumber(fields.get("monthlyFeeSen"), preview.billingProfile().monthlyFeeSen())));
            billingFields.put("Registration Total Preview", moneyText(preview.registrationTotalSen()));
            billingFields.put("Yearly Fee Covers", firstNonBlank(fields.get("yearlyFeeCoveredYear"), preview.billingProfile().yearlyFeeCoveredYear()));
            billingFields.put(
                "Invoice Schedule",
                String.format(
                    Locale.US,
                    "Generated on day %d and due on day %d of each month.",
                    preview.invoiceGenerationDay(),
                    resolveDueDay(fields, preview.billingProfile().invoiceDueDay())
                )
            );
            billingFields.put("Invoice Due Day", text(resolveDueDay(fields, preview.billingProfile().invoiceDueDay())));
            billingFields.put("Age Out Of Policy", yesNo(fields.get("ageOutOfPolicy")));
            billingFields.put("Age Policy Reason", text(fields.get("agePolicyReason")));
            billingFields.put("Approved Absence Letter", yesNo(fields.get("absenceLetterApproved")));
            billingFields.put("Absence Letter Period", text(fields.get("absenceLetterPeriod")));
            billingFields.put("Absence Days With Letter", text(fields.get("absenceLetterDays")));
            billingFields.put("Staff Child", yesNo(fields.get("staffChild")));
            billingFields.put("Casual Transit Billing", child.getTransportEnabledText());
            sections.add(createDetailSection(
                "Billing & Flags",
                "The billing preview moves here so Edit only exposes fields that can be changed.",
                billingFields
            ));
            sections.add(createSnapshotSection(fields));

            showDialog(
                "Child Details",
                "Review the full child profile, registration capture, and billing snapshot.",
                sections
            );
        } catch (IOException | InterruptedException | IllegalStateException ex) {
            showLoadError("child", ex);
        }
    }

    static void showParentDetails(ParentsPane.ParentRecord parent) {
        if (parent == null) {
            return;
        }
        try {
            FsDocument document = loadDocument("parents", parent.getParentId(), "Parent");
            if (document == null) {
                return;
            }

            Map<String, Object> fields = document.fields();
            List<Node> sections = new ArrayList<>();

            LinkedHashMap<String, String> profileFields = new LinkedHashMap<>();
            profileFields.put("Record ID", parent.getRecordId());
            profileFields.put("Parent Name", parent.getParentName());
            profileFields.put("Relationship", parent.getRelationship());
            profileFields.put("Custom Relationship", parent.getCustomRelationship());
            profileFields.put("Phone", parent.getPhone());
            profileFields.put("Parent IC", parent.getParentIc());
            profileFields.put("IC Verified", parent.getIcVerifiedStatus());
            profileFields.put("Status", parent.getStatus());
            sections.add(createDetailSection(
                "Parent Profile",
                "Core parent identity and verification details.",
                profileFields
            ));

            LinkedHashMap<String, String> workFields = new LinkedHashMap<>();
            workFields.put("Occupation", text(fields.get("occupation")));
            workFields.put("Department", text(fields.get("department")));
            workFields.put("Nationality", text(fields.get("nationality")));
            workFields.put("Family Key", text(parent.getFamilyKey()));
            workFields.put("Passcode Expiry", text(parent.getPasscodeExpiry()));
            sections.add(createDetailSection(
                "Work & Access",
                "Non-table metadata that still matters during support and registration follow-up.",
                workFields
            ));

            LinkedHashMap<String, String> childrenFields = new LinkedHashMap<>();
            childrenFields.put("Linked Children Count", parent.getLinkedChildrenCountText());
            childrenFields.put("Linked Child IDs", parent.getChildId());
            childrenFields.put("Linked Child Names", parent.getChildName());
            sections.add(createDetailSection(
                "Children Linking",
                "Linked children stay in View so the main table remains operationally focused.",
                childrenFields
            ));

            LinkedHashMap<String, String> notificationFields = new LinkedHashMap<>();
            notificationFields.put("Notifications", firstNonBlank(notificationSummary(fields), parent.getNotificationsSummary()));
            sections.add(createDetailSection(
                "Notifications",
                "Current parent notification preferences.",
                notificationFields
            ));

            sections.addAll(createEmergencyContactSections(fields.get("emergencyContacts")));
            sections.add(createSnapshotSection(fields));

            showDialog(
                "Parent Details",
                "Review linked children, verification, contacts, and notifications before editing.",
                sections
            );
        } catch (IOException | InterruptedException | IllegalStateException ex) {
            showLoadError("parent", ex);
        }
    }

    static void showTeacherDetails(Map<String, Object> teacher) {
        if (teacher == null) {
            return;
        }
        try {
            String teacherId = text(teacher.get("id"));
            FsDocument document = loadDocument("teachers", teacherId, "Teacher");
            if (document == null) {
                return;
            }

            Map<String, Object> fields = document.fields();
            List<Node> sections = new ArrayList<>();

            LinkedHashMap<String, String> profileFields = new LinkedHashMap<>();
            profileFields.put("Record ID", teacherId);
            profileFields.put("Full Name", firstNonBlank(fields.get("name"), teacher.get("name")));
            profileFields.put("IC No.", firstNonBlank(fields.get("personalIdentification"), teacher.get("personalIdentification")));
            profileFields.put("Gender", firstNonBlank(fields.get("gender"), teacher.get("gender")));
            profileFields.put("Date of Birth", firstNonBlank(fields.get("dateOfBirth"), teacher.get("dateOfBirth")));
            profileFields.put("Nationality", firstNonBlank(fields.get("nationality"), teacher.get("nationality")));
            profileFields.put("Status", teacherStatusText(teacher));
            sections.add(createDetailSection(
                "Teacher Profile",
                "Core identity values for the teacher record.",
                profileFields
            ));

            LinkedHashMap<String, String> contactFields = new LinkedHashMap<>();
            contactFields.put("Phone", firstNonBlank(fields.get("phone"), teacher.get("phone")));
            contactFields.put("Email", firstNonBlank(fields.get("email"), teacher.get("email")));
            contactFields.put("Home Address", firstNonBlank(fields.get("homeAddress"), teacher.get("homeAddress")));
            contactFields.put("Image URL", firstNonBlank(fields.get("image"), teacher.get("image")));
            sections.add(createDetailSection(
                "Contact & Address",
                "Full contact information stays here so the teacher table can stay lean.",
                contactFields
            ));

            LinkedHashMap<String, String> employmentFields = new LinkedHashMap<>();
            employmentFields.put("Joined Date", text(fields.get("joinedDate")));
            employmentFields.put("Base Salary", moneyText(firstNonBlankNumber(fields.get("salaryBaseSen"), teacher.get("salaryBaseSen"))));
            employmentFields.put("Salary Active", yesNo(firstNonBlankBoolean(fields.get("salaryActive"), teacher.get("salaryActive"))));
            employmentFields.put("Overtime Policy", "Shared Taska policy");
            employmentFields.put("Created At", text(fields.get("createdAt")));
            employmentFields.put("Updated At", text(fields.get("updatedAt")));
            sections.add(createDetailSection(
                "Employment & Payroll",
                "Administrative employment details and salary settings.",
                employmentFields
            ));
            sections.add(createSnapshotSection(fields));

            showDialog(
                "Teacher Details",
                "Review the full teacher profile and payroll setup before editing.",
                sections
            );
        } catch (IOException | InterruptedException | IllegalStateException ex) {
            showLoadError("teacher", ex);
        }
    }

    private static FsDocument loadDocument(String collectionId, String documentId, String label)
        throws IOException, InterruptedException {

        if (documentId == null || documentId.isBlank()) {
            throw new IllegalStateException("Missing " + label.toLowerCase(Locale.ROOT) + " ID.");
        }

        FirestoreRestClient client = FirestoreRest.forCurrentUser();
        FsDocument document = client.getDocument(collectionId, documentId);
        if (document == null) {
            new Alert(Alert.AlertType.ERROR, label + " record was not found.").showAndWait();
        }
        return document;
    }

    private static void showDialog(String title, String subtitle, List<Node> sections) {
        Dialog<ButtonType> dialog = new Dialog<>();
        AppThemeSupport.prepareDialog(dialog, title, subtitle, AppThemeSupport.Tone.INFO);

        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(closeType);
        AppThemeSupport.styleDialogButtons(dialog, closeType);

        VBox content = AppThemeSupport.createDialogContent(sections.toArray(new Node[0]));
        ScrollPane scrollPane = AppThemeSupport.wrapDialogContent(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportWidth(720);
        scrollPane.setPrefViewportHeight(760);

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().setPrefSize(780, 840);
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        dialog.showAndWait();
    }

    private static Node createDetailSection(String title, String subtitle, LinkedHashMap<String, String> fields) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(160);
        labelCol.setPrefWidth(180);

        ColumnConstraints valueCol = new ColumnConstraints();
        valueCol.setHgrow(Priority.ALWAYS);
        valueCol.setFillWidth(true);

        grid.getColumnConstraints().setAll(labelCol, valueCol);

        int rowIndex = 0;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            Label label = new Label(entry.getKey());
            label.getStyleClass().add("app-helper-text");
            label.setWrapText(true);

            Label value = new Label(text(entry.getValue()));
            value.setWrapText(true);
            value.setMaxWidth(Double.MAX_VALUE);
            value.setStyle("-fx-text-fill: #163426; -fx-font-size: 13px; -fx-font-weight: 600;");

            grid.add(label, 0, rowIndex);
            grid.add(value, 1, rowIndex);
            GridPane.setHgrow(value, Priority.ALWAYS);
            rowIndex++;
        }

        return AppThemeSupport.createFormSection(title, subtitle, grid);
    }

    private static Node createSnapshotSection(Map<String, Object> fields) {
        TextArea snapshotArea = new TextArea(SummaryTableSupport.toPrettyJson(fields));
        snapshotArea.setEditable(false);
        snapshotArea.setWrapText(false);
        snapshotArea.setPrefRowCount(12);
        snapshotArea.setFocusTraversable(false);
        snapshotArea.setStyle(
            "-fx-font-family: 'Consolas';" +
            "-fx-font-size: 12px;" +
            "-fx-highlight-fill: #c8f0c1;" +
            "-fx-highlight-text-fill: #163426;"
        );
        return AppThemeSupport.createFormSection(
            "Full Record Snapshot",
            "Raw Firestore fields for this record.",
            snapshotArea
        );
    }

    private static List<Node> createEmergencyContactSections(Object rawEmergencyContacts) {
        List<Map<String, Object>> contacts = mapList(rawEmergencyContacts);
        if (contacts.isEmpty()) {
            LinkedHashMap<String, String> emptyFields = new LinkedHashMap<>();
            emptyFields.put("Configured Contacts", "-");
            return List.of(createDetailSection(
                "Emergency Contacts",
                "No emergency contacts have been configured.",
                emptyFields
            ));
        }

        List<Node> sections = new ArrayList<>();
        for (int index = 0; index < contacts.size(); index++) {
            Map<String, Object> contact = contacts.get(index);
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("Name", text(contact.get("name")));
            fields.put("Address", text(contact.get("address")));
            fields.put("Contact No", firstNonBlank(contact.get("phone"), contact.get("contactNo")));
            fields.put("Office Contact No", firstNonBlank(contact.get("officePhone"), contact.get("officeContactNo")));
            sections.add(createDetailSection(
                "Emergency Contact " + (index + 1),
                index == 0 ? "Secondary contacts used when the primary parent cannot be reached." : null,
                fields
            ));
        }
        return sections;
    }

    private static List<Map<String, Object>> mapList(Object raw) {
        if (!(raw instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object entry : rawList) {
            if (entry instanceof Map<?, ?> mapEntry) {
                LinkedHashMap<String, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<?, ?> item : mapEntry.entrySet()) {
                    converted.put(String.valueOf(item.getKey()), item.getValue());
                }
                items.add(converted);
            }
        }
        return items;
    }

    private static String notificationSummary(Map<String, Object> fields) {
        Object settingsObj = fields.get("settings");
        if (!(settingsObj instanceof Map<?, ?> settings)) {
            return "";
        }
        Object notificationsObj = settings.get("notifications");
        if (!(notificationsObj instanceof Map<?, ?> notifications)) {
            return "";
        }

        List<String> enabled = new ArrayList<>();
        for (Map.Entry<?, ?> entry : notifications.entrySet()) {
            if (truthy(entry.getValue())) {
                enabled.add(titleCase(String.valueOf(entry.getKey())));
            }
        }
        return enabled.isEmpty() ? "None" : String.join(", ", enabled);
    }

    private static String teacherStatusText(Map<String, Object> teacher) {
        String fallback = Boolean.TRUE.equals(teacher.get("salaryActive")) ? "Active" : "Inactive";
        return SummaryTableSupport.resolveStatus(teacher, fallback);
    }

    private static String titleCase(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().replace('_', ' ').replace('-', ' ');
        if (normalized.isEmpty()) {
            return "";
        }
        String[] parts = normalized.split("\\s+");
        List<String> titled = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            titled.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1).toLowerCase(Locale.ROOT));
        }
        return String.join(" ", titled);
    }

    private static int resolveDueDay(Map<String, Object> fields, int fallback) {
        Object raw = firstNonBlankNumber(fields.get("invoiceDueDay"), fields.get("billingDueDay"));
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private static LocalDate parseLocalDate(Object raw) {
        String text = text(raw);
        if ("-".equals(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String describeAgeBand(Object rawAgeBand) {
        String ageBand = text(rawAgeBand);
        if ("-".equals(ageBand)) {
            return "-";
        }
        return CRUDChildValidationSupport.describeAgeBand(ageBand);
    }

    private static String moneyText(Object rawSen) {
        if (rawSen instanceof Number number) {
            double rm = number.doubleValue() / 100.0;
            return String.format(Locale.US, "RM %.2f", rm);
        }
        return "-";
    }

    private static Object firstNonBlankNumber(Object primary, Object fallback) {
        if (primary instanceof Number) {
            return primary;
        }
        if (fallback instanceof Number) {
            return fallback;
        }
        return null;
    }

    private static Object firstNonBlankBoolean(Object primary, Object fallback) {
        if (primary instanceof Boolean) {
            return primary;
        }
        if (fallback instanceof Boolean) {
            return fallback;
        }
        return null;
    }

    private static String firstNonBlank(Object primary, Object fallback) {
        String primaryText = rawText(primary);
        if (!primaryText.isBlank()) {
            return primaryText;
        }
        String fallbackText = rawText(fallback);
        return fallbackText.isBlank() ? "-" : fallbackText;
    }

    private static String yesNo(Object raw) {
        if (raw == null) {
            return "-";
        }
        return truthy(raw) ? "Yes" : "No";
    }

    private static boolean truthy(Object raw) {
        if (raw instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String value = rawText(raw).toLowerCase(Locale.ROOT);
        return "true".equals(value) || "1".equals(value) || "yes".equals(value);
    }

    private static String text(Object raw) {
        String value = rawText(raw);
        return value.isBlank() ? "-" : value;
    }

    private static String rawText(Object raw) {
        String value = SummaryTableSupport.displayText(Objects.toString(raw, "")).trim();
        return "-".equals(value) ? "" : value;
    }

    private static void showLoadError(String label, Exception ex) {
        new Alert(Alert.AlertType.ERROR, "Failed to load " + label + " details: " + ex.getMessage()).showAndWait();
    }
}