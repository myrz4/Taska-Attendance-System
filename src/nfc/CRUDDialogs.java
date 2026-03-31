package nfc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.util.Callback;

/**
 * CRUDDialogs (Firestore Version)
 * Replaces MySQL logic with Firebase Firestore operations.
 */
public class CRUDDialogs {
    private static final DateTimeFormatter BILLING_PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static String safeStr(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static int parseMoneyToSen(String raw) {
        String normalized = safeStr(raw).trim().replace("RM", "").replace(",", "");
        if (normalized.isEmpty()) {
            return 0;
        }
        double value = Double.parseDouble(normalized);
        return (int) Math.round(Math.max(0d, value) * 100d);
    }

    private static Integer ageInMonths(LocalDate at, LocalDate birthDate) {
        if (at == null || birthDate == null) return null;
        return (at.getYear() - birthDate.getYear()) * 12 + (at.getMonthValue() - birthDate.getMonthValue());
    }

    private static FirestoreRestClient rest() {
        return FirestoreRest.forCurrentUser();
    }

    private static String newDocId() {
        // Best-effort: Firestore auto IDs are 20 chars base62; UUID-without-dashes is fine for our use.
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private static void refreshChildParentCacheAsync(FirestoreRestClient client, List<String> childIds) {
        if (client == null || childIds == null || childIds.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            try {
                List<FsDocument> parents = client.listDocuments("parents");

                for (String childId : childIds) {
                    try {
                        String cid = childId == null ? "" : childId.trim();
                        if (cid.isEmpty()) continue;

                        Set<String> parentIds = new HashSet<>();
                        List<String> parentNames = new ArrayList<>();
                        List<String> parentPhones = new ArrayList<>();
                        List<String> parentPhoneTails = new ArrayList<>();
                        List<String> parentPhonesE164 = new ArrayList<>();

                        for (FsDocument p : parents) {
                            if (p == null || p.getId() == null) continue;

                            boolean linked = false;
                            Object idsObj = p.get("childIds");
                            if (idsObj instanceof List<?>) {
                                List<?> list = (List<?>) idsObj;
                                for (Object o : list) {
                                    if (o == null) continue;
                                    if (cid.equals(String.valueOf(o).trim())) {
                                        linked = true;
                                        break;
                                    }
                                }
                            }
                            if (!linked) {
                                String legacy = p.getString("childId");
                                linked = legacy != null && cid.equals(legacy.trim());
                            }

                            if (!linked) continue;
                            if (!parentIds.add(p.getId())) continue;
                            String pn = safeStr(p.get("parentName")).trim();
                            String ph = safeStr(p.get("phone")).trim();
                            if (!pn.isEmpty()) parentNames.add(pn);
                            if (!ph.isEmpty()) {
                                parentPhones.add(ph);
                                String tail = PhoneUtil.myTail(ph);
                                if (tail != null && !tail.isBlank()) parentPhoneTails.add(tail);
                                String e164 = PhoneUtil.toE164My(ph);
                                if (e164 != null && !e164.isBlank()) parentPhonesE164.add(e164);
                            }
                        }

                        Map<String, Object> patch = new HashMap<>();
                        patch.put("parentIds", new ArrayList<>(parentIds));
                        patch.put("parentNames", parentNames);
                        patch.put("parentPhones", parentPhones);
                        patch.put("parentPhoneTails", parentPhoneTails);
                        patch.put("parentPhonesE164", parentPhonesE164);
                        patch.put("parentName", String.join("\n", parentNames));
                        patch.put("parentContact", String.join("\n", parentPhones));
                        patch.put("parentCacheUpdatedAt", new Date());
                        patch.put("parentCacheSource", "parents.childIds");

                        client.patchDocumentMerge("children", cid, patch);
                    } catch (IOException | InterruptedException ex) {
                        if (ex instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        System.err.println("⚠ Failed to refresh child parent cache: " + ex.getMessage());
                    }
                }
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                System.err.println("⚠ Failed to refresh child parent cache batch: " + ex.getMessage());
            }
        });
    }

    private enum RelationshipType {
        MOTHER("mother", "Mother"),
        FATHER("father", "Father"),
        GUARDIAN("guardian", "Guardian");

        final String firestoreValue;
        final String display;

        RelationshipType(String firestoreValue, String display) {
            this.firestoreValue = firestoreValue;
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }

        static RelationshipType fromFirestore(Object raw) {
            String s = raw == null ? "" : String.valueOf(raw).trim().toLowerCase();
            if ("mother".equals(s)) {
                return MOTHER;
            }
            if ("father".equals(s)) {
                return FATHER;
            }
            if ("guardian".equals(s)) {
                return GUARDIAN;
            }
            return GUARDIAN;
        }
    }

    private enum FeePlanType {
        MONTHLY_FULLTIME("monthly", "fulltime", "Monthly Full-Time"),
        TRANSIT_AUTO_MONTHLY("transit", "transit", "Transit Monthly (Auto by duration/attendance)"),
        TRANSIT_HALFDAY_MONTH("transit", "transit_halfday_month", "Transit 1/2 Day (Monthly)"),
        TRANSIT_2H_MONTH("transit", "transit_2h_month", "Transit 2 Hours (Monthly)"),
        TRANSIT_SCHOOLHOLIDAY_MONTH("transit", "transit_schoolholiday_month", "Transit School Holiday (Monthly)"),
        TRANSIT_1DAY("transit", "transit_1day", "Transit 1 Day (attendance-based)"),
        TRANSIT_1WEEK("transit", "transit_1week", "Transit 1 Week (attendance-based)"),
        TRANSIT_1HOUR("transit", "transit_1hour", "Transit 1 Hour (attendance-based)");

        final String code;
        final String careType;
        final String display;

        FeePlanType(String code, String careType, String display) {
            this.code = code;
            this.careType = careType;
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }

        static FeePlanType fromChildData(Map<String, Object> data) {
            if (data == null) return MONTHLY_FULLTIME;
            String careType = safeStr(data.get("careType")).trim().toLowerCase();
            for (FeePlanType option : values()) {
                if (option.careType.equals(careType)) {
                    return option;
                }
            }
            String feePlan = safeStr(data.get("feePlan")).trim().toLowerCase();
            if ("transit".equals(feePlan)) return TRANSIT_AUTO_MONTHLY;
            return MONTHLY_FULLTIME;
        }
    }

    private enum TransitDurationHint {
        AUTO(null, "Auto from attendance"),
        TWO_HOURS(2.0, "2 Hours / Short Transit"),
        HALF_DAY(4.0, "Half Day Transit");

        final Double durationHours;
        final String display;

        TransitDurationHint(Double durationHours, String display) {
            this.durationHours = durationHours;
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }

        static TransitDurationHint fromChildData(Map<String, Object> data) {
            if (data == null) return AUTO;
            Object raw = data.get("careDurationHours");
            if (!(raw instanceof Number)) raw = data.get("transitDurationHours");
            if (raw instanceof Number) {
                double hours = ((Number) raw).doubleValue();
                if (hours <= 2.25d) return TWO_HOURS;
                return HALF_DAY;
            }
            return AUTO;
        }
    }

    private static boolean isParentLinkedToChild(FsDocument parentDoc, String childId) {
        if (parentDoc == null || childId == null || childId.trim().isEmpty()) return false;
        String cid = childId.trim();

        Object idsObj = parentDoc.get("childIds");
        if (idsObj instanceof List<?>) {
            List<?> list = (List<?>) idsObj;
            for (Object o : list) {
                if (o == null) continue;
                if (cid.equals(String.valueOf(o).trim())) return true;
            }
        }

        String legacy = parentDoc.getString("childId");
        return legacy != null && cid.equals(legacy.trim());
    }

    // ------------------ Child Dialog ------------------
    public static void showChildDialog(ChildrenView.Child existing,
                                       boolean isNew,
                                       Runnable onSave) {
        Dialog<ChildrenView.Child> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(isNew ? "Add New Child" : "Edit Child");

        FirestoreRestClient client;
        String childId;
        Map<String, Object> existingData = null;
        try {
            client = rest();
            childId = isNew ? newDocId() : (existing == null ? "" : existing.getChildId());
            if (childId == null || childId.isBlank()) {
                throw new IllegalStateException("Missing child ID");
            }
            if (!isNew) {
                FsDocument snap = client.getDocument("children", childId);
                existingData = snap == null ? null : snap.fields();
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            new Alert(Alert.AlertType.ERROR, "Firestore REST Error: " + e.getMessage()).showAndWait();
            return;
        }

        TextField nameTf = new TextField();
        DatePicker dobPicker = new DatePicker();
        TextField uidTf = new TextField();
        uidTf.setPromptText("Scan/write NFC UID (replaceable)");
        TextField childIcTf = new TextField();
        childIcTf.setPromptText("No. IC / MyKid");
        TextField birthCertTf = new TextField();
        birthCertTf.setPromptText("No. Sijil Lahir");
        TextArea addressTa = new TextArea();
        addressTa.setPromptText("Alamat penuh kanak-kanak");
        addressTa.setPrefRowCount(2);

        CheckBox staffChildCb = new CheckBox("Anak staff");

        ComboBox<FeePlanType> feePlanCb = new ComboBox<>();
        feePlanCb.getItems().setAll(FeePlanType.values());
        feePlanCb.setMaxWidth(Double.MAX_VALUE);
        feePlanCb.setValue(FeePlanType.MONTHLY_FULLTIME);
        ComboBox<TransitDurationHint> transitDurationHintCb = new ComboBox<>();
        transitDurationHintCb.getItems().setAll(TransitDurationHint.values());
        transitDurationHintCb.setMaxWidth(Double.MAX_VALUE);
        transitDurationHintCb.setValue(TransitDurationHint.AUTO);
        CheckBox schoolHolidayTransitCb = new CheckBox("Use school-holiday monthly transit rate");
        Label transitHint = new Label("For generic monthly transit, backend will use school-holiday override first, then duration hint, then attendance time if no duration is saved.");
        transitHint.setWrapText(true);
        CheckBox transportFromTadikaCb = new CheckBox("Add transport from tadika (RM150/month)");
        ComboBox<Integer> billingDueDayCb = new ComboBox<>();
        billingDueDayCb.getItems().setAll(5, 7);
        billingDueDayCb.setMaxWidth(Double.MAX_VALUE);
        billingDueDayCb.setValue(7);
        CheckBox absenceLetterApprovedCb = new CheckBox("Approved absence letter for billing discount");
        TextField absenceLetterPeriodTf = new TextField();
        absenceLetterPeriodTf.setPromptText("yyyy-MM");
        absenceLetterPeriodTf.setText(LocalDate.now().format(BILLING_PERIOD_FORMAT));
        TextField absenceLetterDaysTf = new TextField();
        absenceLetterDaysTf.setPromptText("0");
        CheckBox uniformChargeCb = new CheckBox("Charge uniform (3 & 4 years, current price)");
        TextField uniformFeeTf = new TextField();
        uniformFeeTf.setPromptText("e.g. 45.00");
        TextField uniformChargePeriodTf = new TextField();
        uniformChargePeriodTf.setPromptText("yyyy-MM (blank = registration month)");
        uniformFeeTf.disableProperty().bind(uniformChargeCb.selectedProperty().not());
        uniformChargePeriodTf.disableProperty().bind(uniformChargeCb.selectedProperty().not());
        Label billingHint = new Label("Full-time monthly fees are age-based. Daily, weekly, and hourly transit plans are billed from actual attendance records.");
        billingHint.setWrapText(true);

        Runnable syncTransitControls = () -> {
            FeePlanType selected = feePlanCb.getValue() == null ? FeePlanType.MONTHLY_FULLTIME : feePlanCb.getValue();
            boolean autoMonthlyTransit = selected == FeePlanType.TRANSIT_AUTO_MONTHLY;
            transitDurationHintCb.setDisable(!autoMonthlyTransit);
            schoolHolidayTransitCb.setDisable(!autoMonthlyTransit);
            transitHint.setDisable(!autoMonthlyTransit);
            transitHint.setVisible(autoMonthlyTransit);
            transitHint.setManaged(autoMonthlyTransit);
            if (!autoMonthlyTransit) {
                transitDurationHintCb.setValue(TransitDurationHint.AUTO);
                schoolHolidayTransitCb.setSelected(false);
            }
        };
        feePlanCb.valueProperty().addListener((obs, oldValue, newValue) -> syncTransitControls.run());

        // Simplified billing input: admin selects fee type + staff/non-staff only.

        if (!isNew && existing != null) {
            nameTf.setText(existing.getName());
            if (existing.getBirthDate() != null) {
                dobPicker.setValue(existing.getBirthDate());
            }
            uidTf.setText(existing.getNfcUid());
        }

        if (existingData != null) {
            String childIc = safeStr(existingData.get("childIcNo")).trim();
            if (childIc.isEmpty()) childIc = safeStr(existingData.get("icNo")).trim();
            childIcTf.setText(childIc);
            birthCertTf.setText(safeStr(existingData.get("birthCertNo")).trim());
            addressTa.setText(safeStr(existingData.get("address")).trim());

            staffChildCb.setSelected(Boolean.TRUE.equals(existingData.get("staffChild")));
            feePlanCb.setValue(FeePlanType.fromChildData(existingData));
            transitDurationHintCb.setValue(TransitDurationHint.fromChildData(existingData));
            schoolHolidayTransitCb.setSelected(Boolean.TRUE.equals(existingData.get("schoolHolidayTransit"))
                || Boolean.TRUE.equals(existingData.get("isSchoolHolidayTransit"))
                || Boolean.TRUE.equals(existingData.get("transitSchoolHoliday")));
            transportFromTadikaCb.setSelected(Boolean.TRUE.equals(existingData.get("transportFromTadika")));
            absenceLetterApprovedCb.setSelected(Boolean.TRUE.equals(existingData.get("absenceLetterApproved")));
            String absenceLetterPeriod = safeStr(existingData.get("absenceLetterPeriod")).trim();
            if (!absenceLetterPeriod.isEmpty()) {
                absenceLetterPeriodTf.setText(absenceLetterPeriod);
            }
            Object absenceLetterDays = existingData.get("absenceLetterDays");
            if (absenceLetterDays instanceof Number) {
                absenceLetterDaysTf.setText(String.valueOf(((Number) absenceLetterDays).intValue()));
            }
            Object uniformFeeSen = existingData.get("uniformFeeSen");
            if (uniformFeeSen instanceof Number && ((Number) uniformFeeSen).intValue() > 0) {
                uniformChargeCb.setSelected(true);
                uniformFeeTf.setText(String.format(java.util.Locale.ROOT, "%.2f", ((Number) uniformFeeSen).doubleValue() / 100d));
            }
            String uniformChargePeriod = safeStr(existingData.get("uniformChargePeriod")).trim();
            if (!uniformChargePeriod.isEmpty()) {
                uniformChargePeriodTf.setText(uniformChargePeriod);
            }
        }

        final boolean existingTransportDefault = existingData != null && Boolean.TRUE.equals(existingData.get("transportFromTadika"));
        int dueDayDefault = 7;
        if (existingData != null) {
            Object dueObj = existingData.get("billingDueDay");
            if (dueObj instanceof Number) {
                int d = ((Number) dueObj).intValue();
                if (d == 5 || d == 7) dueDayDefault = d;
            }
        }
        final int existingDueDayDefault = dueDayDefault;
        transportFromTadikaCb.setSelected(existingTransportDefault);
        billingDueDayCb.setValue(existingDueDayDefault);
        syncTransitControls.run();

        VBox content = new VBox(10,
            new Label("Child Name:"), nameTf,
            new Label("Birth Date:"), dobPicker,
            new Label("NFC UID:"), uidTf,
            new Label("Child IC / MyKid:"), childIcTf,
            new Label("Birth Certificate No:"), birthCertTf,
            new Label("Address:"), addressTa,
            new Label("Billing Plan:"), feePlanCb,
            new Label("Auto Monthly Transit Duration Hint:"), transitDurationHintCb,
            schoolHolidayTransitCb,
            transitHint,
            billingHint,
            transportFromTadikaCb,
            new Label("Payment due day:"), billingDueDayCb,
            uniformChargeCb,
            new Label("Uniform fee amount (RM):"), uniformFeeTf,
            new Label("Uniform charge period (optional):"), uniformChargePeriodTf,
            new Label("Approved Absence Letter Period (optional):"), absenceLetterPeriodTf,
            new Label("Absence Days With Letter (optional):"), absenceLetterDaysTf,
            absenceLetterApprovedCb,
            new Label("Note: All monthly/yearly totals are auto-calculated by backend."),
            staffChildCb
        );
        content.setPadding(new Insets(20));
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double maxDialogWidth = Math.min(visualBounds.getWidth() * 0.78, 760);
        double maxDialogHeight = Math.min(visualBounds.getHeight() * 0.82, 820);

        content.setPrefWidth(maxDialogWidth - 60);
        dialog.getDialogPane().setPrefWidth(maxDialogWidth);
        dialog.getDialogPane().setPrefHeight(maxDialogHeight);
        dialog.getDialogPane().setContent(scrollPane);
        dialog.setResizable(true);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == saveType) {
                return new ChildrenView.Child(
                    childId,
                    nameTf.getText().trim(),
                    dobPicker.getValue(),
                    "", // computed from parents collection
                    "", // computed from parents collection
                    "", // computed from parents collection
                    uidTf.getText().trim()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(child -> {
            try {
                // Normalize NFC UID for consistency
                String nfcUid = child.getNfcUid() == null ? "" : child.getNfcUid().trim().toUpperCase();
                if (nfcUid.isEmpty()) {
                    new Alert(Alert.AlertType.ERROR, "NFC UID cannot be empty.").showAndWait();
                    return;
                }

                // Ensure NFC UID is unique across children (ignore redirect docs created by migration).
                for (FsDocument d : client.listDocuments("children")) {
                    if (d == null) continue;
                    String migratedTo = d.getString("migratedToChildId");
                    if (migratedTo != null && !migratedTo.trim().isEmpty()) continue;
                    String otherUid = d.getString("nfc_uid");
                    if (otherUid == null) continue;
                    if (nfcUid.equalsIgnoreCase(otherUid.trim()) && !d.getId().equals(childId)) {
                        new Alert(Alert.AlertType.ERROR, "This NFC UID is already assigned to another child.").showAndWait();
                        return;
                    }
                }

                Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("name", child.getName());
                // Store DOB in ISO format (yyyy-MM-dd) for consistent parsing across apps.
                payload.put("birthDate", child.getBirthDate() == null ? "" : child.getBirthDate().toString());
                Integer childAgeMonths = ageInMonths(LocalDate.now(), child.getBirthDate());
                FeePlanType selectedFeePlan = feePlanCb.getValue() == null ? FeePlanType.MONTHLY_FULLTIME : feePlanCb.getValue();
                boolean requestsSchoolHolidayTransit = selectedFeePlan == FeePlanType.TRANSIT_SCHOOLHOLIDAY_MONTH
                    || (selectedFeePlan == FeePlanType.TRANSIT_AUTO_MONTHLY && schoolHolidayTransitCb.isSelected());
                boolean schoolHolidayAgeBlocked = requestsSchoolHolidayTransit
                    && (childAgeMonths == null || childAgeMonths < 48);
                boolean billingReviewRequired = !selectedFeePlan.code.equals("transit")
                    && childAgeMonths != null
                    && (childAgeMonths < 3 || childAgeMonths >= 60);

                if (schoolHolidayAgeBlocked) {
                    new Alert(
                        Alert.AlertType.ERROR,
                        "Transit penuh cuti sekolah hanya boleh digunakan untuk kanak-kanak umur 4 tahun dan ke atas.")
                        .showAndWait();
                    return;
                }

                if (billingReviewRequired) {
                    Alert warning = new Alert(
                        Alert.AlertType.WARNING,
                        "This child is outside the PDF fee range of 3 months to below 4 years. The invoice will use the nearest standard band and be flagged for manual review. Continue saving?",
                        ButtonType.OK,
                        ButtonType.CANCEL
                    );
                    warning.setHeaderText("Billing Review Required");
                    if (warning.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                        return;
                    }
                }
                payload.put("billingReviewRequired", billingReviewRequired);
                payload.put("billingReviewReason", billingReviewRequired
                    ? (childAgeMonths != null && childAgeMonths < 3 ? "under_3_months" : "age_4y_or_above")
                    : "");
                payload.put("nfc_uid", nfcUid);
                payload.put("childIcNo", childIcTf.getText() == null ? "" : childIcTf.getText().trim());
                payload.put("birthCertNo", birthCertTf.getText() == null ? "" : birthCertTf.getText().trim());
                payload.put("address", addressTa.getText() == null ? "" : addressTa.getText().trim());
                payload.put("staffChild", staffChildCb.isSelected());

                String absenceLetterPeriod = absenceLetterPeriodTf.getText() == null ? "" : absenceLetterPeriodTf.getText().trim();
                String absenceLetterDaysRaw = absenceLetterDaysTf.getText() == null ? "" : absenceLetterDaysTf.getText().trim();
                int absenceLetterDays = 0;
                if (!absenceLetterDaysRaw.isEmpty()) {
                    try {
                        absenceLetterDays = Math.max(0, Integer.parseInt(absenceLetterDaysRaw));
                    } catch (NumberFormatException ex) {
                        new Alert(Alert.AlertType.ERROR, "Absence days must be a whole number.").showAndWait();
                        return;
                    }
                }
                if (!absenceLetterPeriod.isEmpty() && !absenceLetterPeriod.matches("\\d{4}-\\d{2}")) {
                    new Alert(Alert.AlertType.ERROR, "Approved absence letter period must be in yyyy-MM format.").showAndWait();
                    return;
                }
                String uniformChargePeriod = uniformChargePeriodTf.getText() == null ? "" : uniformChargePeriodTf.getText().trim();
                int uniformFeeSen = 0;
                if (uniformChargeCb.isSelected()) {
                    try {
                        uniformFeeSen = parseMoneyToSen(uniformFeeTf.getText());
                    } catch (NumberFormatException ex) {
                        new Alert(Alert.AlertType.ERROR, "Uniform fee must be a valid amount.").showAndWait();
                        return;
                    }
                    if (uniformFeeSen <= 0) {
                        new Alert(Alert.AlertType.ERROR, "Uniform fee must be greater than zero when uniform billing is enabled.").showAndWait();
                        return;
                    }
                    if (!uniformChargePeriod.isEmpty() && !uniformChargePeriod.matches("\\d{4}-\\d{2}")) {
                        new Alert(Alert.AlertType.ERROR, "Uniform charge period must be in yyyy-MM format.").showAndWait();
                        return;
                    }
                } else {
                    uniformChargePeriod = "";
                }
                payload.put("absenceLetterApproved", absenceLetterApprovedCb.isSelected());
                payload.put("absenceLetterPeriod", absenceLetterPeriod);
                payload.put("absenceLetterDays", absenceLetterDays);
                payload.put("uniformFeeSen", uniformFeeSen);
                payload.put("uniformChargePeriod", uniformChargePeriod);
                payload.put("uniformFeeDescription", "Uniform Taska (3 & 4 tahun)");

                FeePlanType feePlan = feePlanCb.getValue() == null ? FeePlanType.MONTHLY_FULLTIME : feePlanCb.getValue();
                // Keep careType for compatibility while saving simple intent for backend derivation.
                payload.put("careType", feePlan.careType);

                // Backend determines full monthly/yearly totals; UI only provides fee type + staff flag.
                String registrationType = "monthly".equals(feePlan.code) ? "fulltime" : "transit";
                payload.put("registrationType", registrationType);
                payload.put("feePlan", feePlan.code);

                TransitDurationHint transitDurationHint = transitDurationHintCb.getValue() == null
                    ? TransitDurationHint.AUTO
                    : transitDurationHintCb.getValue();
                boolean schoolHolidayTransit = false;
                Object careDurationHours = null;
                if (feePlan == FeePlanType.TRANSIT_AUTO_MONTHLY) {
                    schoolHolidayTransit = schoolHolidayTransitCb.isSelected();
                    if (transitDurationHint.durationHours != null) {
                        careDurationHours = transitDurationHint.durationHours;
                    }
                } else if (feePlan == FeePlanType.TRANSIT_2H_MONTH) {
                    careDurationHours = 2.0d;
                } else if (feePlan == FeePlanType.TRANSIT_HALFDAY_MONTH) {
                    careDurationHours = 4.0d;
                } else if (feePlan == FeePlanType.TRANSIT_SCHOOLHOLIDAY_MONTH) {
                    schoolHolidayTransit = true;
                }
                payload.put("schoolHolidayTransit", schoolHolidayTransit);
                payload.put("transitDurationHours", careDurationHours);
                payload.put("careDurationHours", careDurationHours);

                Integer dueDay = billingDueDayCb.getValue();
                payload.put("transportFromTadika", transportFromTadikaCb.isSelected());
                payload.put("billingDueDay", (dueDay != null && dueDay == 5) ? 5 : 7);

                if (isNew) {
                    payload.put("registeredAt", new Date());
                    client.createDocumentWithId("children", childId, payload);
                } else {
                    client.patchDocumentMerge("children", childId, payload);
                }
                if (isNew) {
                    System.out.println("✅ Added new child: " + child.getName() + " (childId=" + childId + ")");
                } else {
                    System.out.println("✏️ Updated child: " + child.getName() + " (childId=" + childId + ")");
                }

                onSave.run();
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                new Alert(Alert.AlertType.ERROR, "Firestore REST Error: " + ex.getMessage()).showAndWait();
            }
        });
    }

    // ------------------ Parent Dialog ------------------
    public static void showParentDialog(ParentsPane.ParentRecord existing,
                                        boolean isNew,
                                        Runnable onSave) {
        Dialog<ParentsPane.ParentRecord> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(isNew ? "Add New Parent" : "Edit Parent");
        dialog.setResizable(true);

        FirestoreRestClient client;
        Map<String, Object> existingData = null;
        try {
            client = rest();
            if (!isNew && existing != null) {
                FsDocument snap = client.getDocument("parents", existing.getParentId());
                existingData = snap == null ? null : snap.fields();
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            new Alert(Alert.AlertType.ERROR, "Firestore REST Error: " + e.getMessage()).showAndWait();
            return;
        }

        // Parent ID is the Firestore document ID and should not be manually edited.
        // For new parents, we generate a new doc ID up-front.
        String parentId = isNew ? newDocId() : (existing == null ? "" : existing.getParentId());
        if (parentId == null || parentId.isBlank()) {
            new Alert(Alert.AlertType.ERROR, "Missing parent ID.").showAndWait();
            return;
        }

        TextField parentIdTf = new TextField(parentId);
        parentIdTf.setEditable(false);
        parentIdTf.setDisable(true);
        parentIdTf.setPromptText("(auto-generated)");

        TextField parentNameTf = new TextField();
        TextField phoneTf = new TextField();
        TextField parentIcTf = new TextField();
        parentIcTf.setPromptText("No. IC Parent / Penjaga");
        CheckBox parentIcVerifiedCb = new CheckBox("IC parent disahkan");

        ComboBox<RelationshipType> relationshipCb = new ComboBox<>();
        relationshipCb.getItems().setAll(RelationshipType.MOTHER, RelationshipType.FATHER, RelationshipType.GUARDIAN);
        relationshipCb.setMaxWidth(Double.MAX_VALUE);
        relationshipCb.setValue(RelationshipType.GUARDIAN);

        TextField relationshipLabelTf = new TextField();
        relationshipLabelTf.setPromptText("Custom (e.g., grandmother / uncle / brother / sister)");

        // Multi-child picker UI (Dropbox-like multi-select)
        TextField childrenSummaryTf = new TextField();
        childrenSummaryTf.setEditable(false);
        childrenSummaryTf.setPromptText("Select one or more children");
        Button selectChildrenBtn = new Button("Select");
        HBox childrenPickerBox = new HBox(10, childrenSummaryTf, selectChildrenBtn);
        HBox.setHgrow(childrenSummaryTf, Priority.ALWAYS);

        // NOTE: Representative/status/passcode/QR/FCM/timestamp fields are system-managed or deprecated.
        // They are intentionally not shown/editable in this admin dialog.

        CheckBox notifActivityCb = new CheckBox("Activity");
        CheckBox notifAttendanceCb = new CheckBox("Attendance");
        CheckBox notifEmergencyCb = new CheckBox("Emergency");
        CheckBox notifFeesCb = new CheckBox("Billing");

        // Defaults
        notifActivityCb.setSelected(false);
        notifAttendanceCb.setSelected(false);
        notifEmergencyCb.setSelected(false);
        notifFeesCb.setSelected(true);

        if (existingData != null) {
            relationshipCb.setValue(RelationshipType.fromFirestore(existingData.get("relationshipType")));
            relationshipLabelTf.setText(safeStr(existingData.get("relationshipLabel")).trim());
            parentIcTf.setText(safeStr(existingData.get("icNo")).trim());
            parentIcVerifiedCb.setSelected(Boolean.TRUE.equals(existingData.get("icVerified")));
        }

        // Only show/use custom label for Guardian
        relationshipLabelTf.disableProperty().bind(
            Bindings.createBooleanBinding(
                () -> relationshipCb.getValue() != RelationshipType.GUARDIAN,
                relationshipCb.valueProperty()
            )
        );

        if (existingData != null) {
            // notifications settings
            try {
                Object settingsObj = existingData.get("settings");
                if (settingsObj instanceof Map) {
                    Map<?, ?> settingsMap = (Map<?, ?>) settingsObj;
                    Object notifObj = settingsMap.get("notifications");
                    if (notifObj instanceof Map) {
                        Map<?, ?> notifMap = (Map<?, ?>) notifObj;
                        notifActivityCb.setSelected(Boolean.TRUE.equals(notifMap.get("activity")));
                        notifAttendanceCb.setSelected(Boolean.TRUE.equals(notifMap.get("attendance")));
                        notifEmergencyCb.setSelected(Boolean.TRUE.equals(notifMap.get("emergency")));
                        notifFeesCb.setSelected(Boolean.TRUE.equals(notifMap.get("fees")));
                    }
                }
            } catch (Exception ignore) {
                // keep defaults
            }
        }

        // Load all children options once
        List<ChildOption> allChildren = new ArrayList<>();
        try {
            for (FsDocument doc : client.listDocuments("children")) {
                if (doc == null) continue;
                String id = doc.getId();
                String nm = doc.getString("name");
                nm = nm == null ? "" : nm.trim();
                if (nm.isEmpty()) nm = id;
                allChildren.add(new ChildOption(id, nm));
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            new Alert(Alert.AlertType.ERROR, "Firestore REST Error loading children: " + e.getMessage()).showAndWait();
            return;
        }
        allChildren.sort(Comparator.comparing(ChildOption::getName, String.CASE_INSENSITIVE_ORDER));

        // Determine initial selection from existing parent doc
        Set<String> initialChildIds = java.util.Collections.emptySet();
        if (existingData != null) {
            Object idsObj = existingData.get("childIds");
            if (idsObj instanceof List) {
                List<?> idList = (List<?>) idsObj;
                initialChildIds = idList.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toSet());
            } else {
                Object refsObj = existingData.get("childRefs");
                if (refsObj instanceof List) {
                    List<?> refList = (List<?>) refsObj;
                    initialChildIds = refList.stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .map(CRUDDialogs::extractChildIdFromRef)
                        .filter(s -> s != null && !s.trim().isEmpty())
                        .collect(Collectors.toSet());
                }
            }
        }
        if ((initialChildIds == null || initialChildIds.isEmpty()) && !isNew && existing != null) {
            String legacy = existing.getChildId();
            if (legacy != null && !legacy.trim().isEmpty()) {
                initialChildIds = java.util.Collections.singleton(legacy.trim());
            }
        }

        // Ensure legacy-selected IDs exist in the options list
        if (initialChildIds != null && !initialChildIds.isEmpty()) {
            Set<String> existingIds = allChildren.stream().map(ChildOption::getId).collect(Collectors.toSet());
            for (String id : initialChildIds) {
                if (id != null && !id.isBlank() && !existingIds.contains(id)) {
                    allChildren.add(new ChildOption(id.trim(), id.trim()));
                }
            }
            allChildren.sort(Comparator.comparing(ChildOption::getName, String.CASE_INSENSITIVE_ORDER));
        }

        ObservableList<ChildOption> allChildrenObs = FXCollections.observableArrayList(allChildren);
        FilteredList<ChildOption> filteredChildren = new FilteredList<>(allChildrenObs, c -> true);
        Map<ChildOption, BooleanProperty> selectedProps = new LinkedHashMap<>();
        for (ChildOption c : allChildrenObs) {
            boolean selected = initialChildIds != null && initialChildIds.contains(c.getId());
            selectedProps.put(c, new SimpleBooleanProperty(selected));
        }

        BooleanProperty hasSelectedChild = new SimpleBooleanProperty(
            selectedProps.values().stream().anyMatch(BooleanProperty::get)
        );

        Runnable refreshSelection = () -> {
            List<ChildOption> selected = selectedProps.entrySet().stream()
                .filter(e -> e.getValue().get())
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(ChildOption::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

            hasSelectedChild.set(!selected.isEmpty());

            childrenSummaryTf.setText(selected.isEmpty()
                ? ""
                : selected.stream().map(ChildOption::getName).collect(Collectors.joining(", ")));

            // Legacy single-child fields are still written on save (primary child = first selected),
            // but we don't show those internal fields in the UI.
        };
        refreshSelection.run();

        TextField searchTf = new TextField();
        searchTf.setPromptText("Search children...");
        searchTf.textProperty().addListener((obs, oldV, newV) -> {
            String q = newV == null ? "" : newV.trim().toLowerCase();
            filteredChildren.setPredicate(c -> {
                if (q.isEmpty()) return true;
                return c.getName().toLowerCase().contains(q) || c.getId().toLowerCase().contains(q);
            });
        });

        ListView<ChildOption> childrenList = new ListView<>(filteredChildren);
        childrenList.setPrefHeight(260);
        childrenList.setCellFactory(lv -> {
            CheckBoxListCell<ChildOption> cell = new CheckBoxListCell<ChildOption>(item -> {
                if (item == null) return new SimpleBooleanProperty(false);
                BooleanProperty p = selectedProps.get(item);
                if (p == null) {
                    p = new SimpleBooleanProperty(false);
                    selectedProps.put(item, p);
                }
                p.addListener((o, ov, nv) -> refreshSelection.run());
                return p;
            }) {
                @Override
                public void updateItem(ChildOption item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.toString());
                    }
                }
            };
            return cell;
        });

        Button applyChildrenBtn = new Button("Apply");
        Button clearChildrenBtn = new Button("Clear");
        HBox popupActions = new HBox(10, applyChildrenBtn, clearChildrenBtn);

        VBox popupContent = new VBox(10,
            new Label("Select Children (multiple):"),
            searchTf,
            childrenList,
            popupActions
        );
        popupContent.setPadding(new Insets(12));

        CustomMenuItem popupItem = new CustomMenuItem(popupContent);
        popupItem.setHideOnClick(false);
        ContextMenu childrenMenu = new ContextMenu(popupItem);
        childrenMenu.setAutoHide(true);

        selectChildrenBtn.setOnAction(e -> {
            if (childrenMenu.isShowing()) {
                childrenMenu.hide();
            } else {
                childrenMenu.show(selectChildrenBtn, Side.BOTTOM, 0, 0);
            }
        });

        childrenSummaryTf.setOnMouseClicked(e -> {
            if (!childrenMenu.isShowing()) {
                childrenMenu.show(childrenSummaryTf, Side.BOTTOM, 0, 0);
            }
        });

        applyChildrenBtn.setOnAction(e -> childrenMenu.hide());
        clearChildrenBtn.setOnAction(e -> {
            selectedProps.values().forEach(p -> p.set(false));
            refreshSelection.run();
        });

        if (!isNew && existing != null) {
            parentNameTf.setText(existing.getParentName());
            phoneTf.setText(existing.getPhone());
        }

        VBox content = new VBox(10,
            new Label("Parent Name:"), parentNameTf,
            new Label("Phone:"), phoneTf,
            new Label("Parent IC:"), parentIcTf,
            parentIcVerifiedCb,
            new Label("Relationship:"), relationshipCb,
            new Label("Custom Relationship (optional):"), relationshipLabelTf,
            new Label("Children (select multiple):"), childrenPickerBox,
            new Label("Notifications:"), new HBox(12, notifActivityCb, notifAttendanceCb, notifEmergencyCb, notifFeesCb)
        );
    content.setPadding(new Insets(20));
    content.setFillWidth(true);

    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    scroll.setPannable(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scroll.setPrefViewportWidth(520);
    scroll.setPrefViewportHeight(650);

    dialog.getDialogPane().setPrefSize(560, 720);
    dialog.getDialogPane().setContent(scroll);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        // Disable Save until basic required fields are present
        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.disableProperty().bind(
            Bindings.createBooleanBinding(
                () -> parentNameTf.getText().trim().isEmpty() || phoneTf.getText().trim().isEmpty() || !hasSelectedChild.get(),
                parentNameTf.textProperty(),
                phoneTf.textProperty(),
                hasSelectedChild
            )
        );

        dialog.setResultConverter(btn -> {
            if (btn == saveType) {
                if (!hasSelectedChild.get()) {
                    new Alert(Alert.AlertType.ERROR, "Please select at least one child.").showAndWait();
                    return null;
                }
                java.time.LocalDate passcodeExpiry = null;
                String familyKey = "";
                int relationshipPriority = 2; // Guardian
                return new ParentsPane.ParentRecord(
                    parentId,
                    parentNameTf.getText().trim(),
                    phoneTf.getText().trim(),
                    "", // relationship (computed in table)
                    "", // childId (computed in table)
                    "", // childName (computed in table)
                    passcodeExpiry,
                    familyKey,
                    relationshipPriority
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(parent -> {
            try {
                Map<String, Object> m = new HashMap<>();
                m.put("parentName", parentNameTf.getText().trim());
                String phoneLocal = PhoneUtil.toLocalMy(phoneTf.getText());
                if (phoneLocal == null || phoneLocal.isBlank()) {
                    new Alert(Alert.AlertType.ERROR, "Phone cannot be empty.").showAndWait();
                    return;
                }
                m.put("phone", phoneLocal);
                m.put("phoneTail", PhoneUtil.myTail(phoneLocal));
                m.put("phoneE164", PhoneUtil.toE164My(phoneLocal));
                m.put("icNo", parentIcTf.getText() == null ? "" : parentIcTf.getText().trim());
                m.put("icVerified", parentIcVerifiedCb.isSelected());
                if (parentIcVerifiedCb.isSelected()) {
                    m.put("icVerifiedAt", new Date());
                }

                // Multi-children arrays
                List<ChildOption> selectedChildren = selectedProps.entrySet().stream()
                    .filter(e -> e.getValue().get())
                    .map(Map.Entry::getKey)
                    .sorted(Comparator.comparing(ChildOption::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());

                List<String> childIds = selectedChildren.stream().map(ChildOption::getId).collect(Collectors.toList());
                List<String> childNames = selectedChildren.stream().map(ChildOption::getName).collect(Collectors.toList());
                List<String> childRefs = selectedChildren.stream().map(ChildOption::getRef).collect(Collectors.toList());

                RelationshipType relationshipType = relationshipCb.getValue() == null ? RelationshipType.GUARDIAN : relationshipCb.getValue();
                String relationshipLabel = relationshipLabelTf.getText() == null ? "" : relationshipLabelTf.getText().trim();

                // Enforce: a child can have only ONE mother and ONE father.
                if (relationshipType == RelationshipType.MOTHER || relationshipType == RelationshipType.FATHER) {
                    List<FsDocument> allParents = client.listDocuments("parents");
                    String desiredType = relationshipType.firestoreValue;

                    Map<String, String> childIdToName = new HashMap<>();
                    for (int i = 0; i < Math.min(childIds.size(), childNames.size()); i++) {
                        childIdToName.put(childIds.get(i), childNames.get(i));
                    }

                    for (String childId : childIds) {
                        if (childId == null || childId.isBlank()) continue;

                        for (FsDocument other : allParents) {
                            if (other == null || other.getId() == null) continue;
                            if (other.getId().equals(parentId)) continue;

                            RelationshipType otherType = RelationshipType.fromFirestore(other.get("relationshipType"));
                            if (!desiredType.equalsIgnoreCase(otherType.firestoreValue)) continue;
                            if (!isParentLinkedToChild(other, childId)) continue;

                            String otherName = safeStr(other.get("parentName")).trim();
                            String otherPhone = safeStr(other.get("phone")).trim();
                            String childName = childIdToName.getOrDefault(childId, childId);

                            new Alert(
                                Alert.AlertType.ERROR,
                                "Cannot save: \"" + childName + "\" already has a " + relationshipType.display.toLowerCase()
                                    + ": " + (otherName.isEmpty() ? other.getId() : otherName)
                                    + (otherPhone.isEmpty() ? "" : (" (" + otherPhone + ")"))
                            ).showAndWait();
                            return;
                        }
                    }
                }

                m.put("childIds", childIds);
                m.put("childNames", childNames);
                m.put("childRefs", childRefs);

                // Relationship fields
                m.put("relationshipType", relationshipType.firestoreValue);
                if (relationshipType == RelationshipType.GUARDIAN) {
                    m.put("relationshipLabel", relationshipLabel.isEmpty() ? null : relationshipLabel);
                } else {
                    m.put("relationshipLabel", null);
                }

                // Legacy single-child fields (primary child = first selected)
                if (!selectedChildren.isEmpty()) {
                    ChildOption first = selectedChildren.get(0);
                    m.put("childId", first.getId());
                    m.put("childName", first.getName());
                    m.put("childRef", first.getRef());
                }

                // settings.notifications.*
                Map<String, Object> notif = new HashMap<>();
                notif.put("activity", notifActivityCb.isSelected());
                notif.put("attendance", notifAttendanceCb.isSelected());
                notif.put("emergency", notifEmergencyCb.isSelected());
                notif.put("fees", notifFeesCb.isSelected());
                Map<String, Object> settings = new HashMap<>();
                settings.put("notifications", notif);
                m.put("settings", settings);

                if (isNew) {
                    // Client timestamp (best-effort)
                    m.put("timestamp", new Date());
                    client.createDocumentWithId("parents", parentId, m);
                } else {
                    client.patchDocumentMerge("parents", parentId, m);
                }

                // Keep children docs updated with cached parent info for fast reads in apps/reports.
                refreshChildParentCacheAsync(client, childIds);

                onSave.run();

            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                new Alert(Alert.AlertType.ERROR, "Firestore REST Error: " + ex.getMessage()).showAndWait();
            }
        });
    }

    private static final class ChildOption {
        private final String id;
        private final String name;

        private ChildOption(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getRef() {
            return "/children/" + id;
        }

        @Override
        public String toString() {
            return name + " (" + id + ")";
        }
    }

    private static String extractChildIdFromRef(String ref) {
        if (ref == null) return null;
        String s = ref.trim();
        if (s.isEmpty()) return null;
        // Accept "/children/<id>" or "children/<id>"
        int idx = s.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < s.length()) {
            return s.substring(idx + 1).trim();
        }
        return s;
    }

    // ------------------ Generic Action Buttons ------------------
    public static <T> Callback<TableColumn<T, Void>, TableCell<T, Void>> createActionCell(
            Consumer<T> onEdit,
            Consumer<T> onDelete
    ) {
        return col -> new TableCell<T, Void>() {
            private final Button editBtn = new Button("Edit");
            private final Button delBtn  = new Button("Delete");

            {
                editBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
                delBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
                editBtn.setOnAction(e -> onEdit.accept(getCurrentItem()));
                delBtn.setOnAction(e -> onDelete.accept(getCurrentItem()));
                setGraphic(new HBox(5, editBtn, delBtn));
            }

            private T getCurrentItem() {
                return getTableView().getItems().get(getIndex());
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : getGraphic());
            }
        };
    }

    // ------------------ Staff Dialog (Firestore) ------------------
    public static void showStaffDialog(StaffManagementView.Admin existing,
                                       boolean isNew,
                                       Runnable onSave) {
        // 🔐 SECURITY CHECK: only allow editing own admin account
        if (!isNew && existing != null) {
            String loggedIn = UserSession.getUsername();
            if (!existing.getUsername().equals(loggedIn)) {
                new Alert(
                    Alert.AlertType.ERROR,
                    "You are not allowed to edit other admin accounts."
                ).showAndWait();
                return;
            }
        }
        Dialog<StaffManagementView.Admin> dlg = new Dialog<>();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle(isNew ? "Add New Staff" : "Edit Staff");

        TextField usernameTf = new TextField();

        PasswordField currentPasswordTf = new PasswordField();
        currentPasswordTf.setPromptText("Enter current password");

        PasswordField newPasswordTf = new PasswordField();
        newPasswordTf.setPromptText("Enter new password");

        TextField profilePictureTf = new TextField();
        profilePictureTf.setEditable(false);
        TextField nameTf = new TextField();

        // Profile picture preview
        ImageView imagePreview = new ImageView();
        imagePreview.setFitWidth(100);
        imagePreview.setFitHeight(120);

        Button uploadBtn = new Button("Upload Profile Picture");
        uploadBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Profile Picture");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
            );
            File file = fileChooser.showOpenDialog(dlg.getOwner());
            if (file != null) {
                try {
                    String destDir = "profile_pics";
                    Files.createDirectories(new File(destDir).toPath());
                    File destFile = new File(destDir, file.getName());
                    Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    profilePictureTf.setText(destFile.getName());
                    imagePreview.setImage(new Image(destFile.toURI().toString()));
                } catch (IOException | SecurityException ex) {
                    new Alert(Alert.AlertType.ERROR, "Failed to copy profile picture: " + ex.getMessage()).showAndWait();
                }
            }
        });

        if (!isNew && existing != null) {
            usernameTf.setText(existing.getUsername());
            usernameTf.setDisable(true);
            profilePictureTf.setText(existing.getProfilePicture());
            nameTf.setText(existing.getName());
            if (existing.getProfilePicture() != null && !existing.getProfilePicture().isEmpty()) {
                File imgFile = new File("profile_pics", existing.getProfilePicture());
                if (imgFile.exists())
                    imagePreview.setImage(new Image(imgFile.toURI().toString()));
            }
        }

        VBox vb = new VBox(10,
            new Label("Username:"), usernameTf,
            new Label("Current Password:"), currentPasswordTf,
            new Label("New Password:"), newPasswordTf,
            new Label("Profile Picture:"), new HBox(10, uploadBtn, profilePictureTf),
            imagePreview,
            new Label("Name:"), nameTf
        );
        vb.setPadding(new Insets(20));
        dlg.getDialogPane().setContent(vb);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt == saveBtn) {

                String username = usernameTf.getText().trim();
                String name = nameTf.getText().trim();

                String currentPwInput = currentPasswordTf.getText().trim();
                String newPwInput = newPasswordTf.getText().trim();

                String finalPassword;

                // 🟢 ADD NEW STAFF → password MUST be provided
                if (isNew) {
                    if (newPwInput.isEmpty()) {
                        new Alert(
                            Alert.AlertType.ERROR,
                            "Password is required for new staff."
                        ).showAndWait();
                        return null;
                    }
                    finalPassword = newPwInput;
                }
                // 🟡 EDIT STAFF
                else {
                    String existingPassword = existing == null ? "" : safeStr(existing.getPassword());
                    // Case 1: both blank → keep old password
                    if (currentPwInput.isEmpty() && newPwInput.isEmpty()) {
                        finalPassword = existingPassword;
                    }
                    // Case 2: one blank → error
                    else if (currentPwInput.isEmpty() || newPwInput.isEmpty()) {
                        new Alert(
                            Alert.AlertType.ERROR,
                            "To change password, please enter BOTH current and new password."
                        ).showAndWait();
                        return null;
                    }
                    // Case 3: wrong current password
                    else if (!currentPwInput.equals(existingPassword)) {
                        new Alert(
                            Alert.AlertType.ERROR,
                            "Current password is incorrect."
                        ).showAndWait();
                        return null;
                    }
                    // Case 4: valid change
                    else {
                        finalPassword = newPwInput;
                    }
                }

                String profilePic = profilePictureTf.getText();

                return new StaffManagementView.Admin(
                    username,
                    finalPassword,
                    profilePic,
                    name
                );
            }
            return null;
        });

        dlg.showAndWait().ifPresent(admin -> {
            if (admin.getUsername() == null || admin.getUsername().isBlank()) {
                new Alert(Alert.AlertType.ERROR, "Username cannot be empty.").showAndWait();
                return;
            }
            try {
                FirestoreRestClient client = rest();

                if (isNew) {
                    // Duplicate check: docId or username field.
                    FsDocument byId = client.getDocument("admins", admin.getUsername());
                    if (byId != null) {
                        new Alert(Alert.AlertType.ERROR,
                            "Admin account with username \"" + admin.getUsername() + "\" already exists.")
                            .showAndWait();
                        return;
                    }
                    for (FsDocument d : client.listDocuments("admins")) {
                        String u = d == null ? null : d.getString("username");
                        if (u != null && u.trim().equalsIgnoreCase(admin.getUsername())) {
                            new Alert(Alert.AlertType.ERROR,
                                "Admin account with username \"" + admin.getUsername() + "\" already exists.")
                                .showAndWait();
                            return;
                        }
                    }

                    Map<String, Object> doc = new HashMap<>();
                    doc.put("username", admin.getUsername());
                    doc.put("password", admin.getPassword());
                    doc.put("profilePicture", admin.getProfilePicture());
                    doc.put("name", admin.getName());
                    client.createDocumentWithId("admins", admin.getUsername(), doc);
                    System.out.println("✅ Added new staff: " + admin.getUsername());
                } else {
                    Map<String, Object> patch = new HashMap<>();
                    patch.put("password", admin.getPassword());
                    patch.put("profilePicture", admin.getProfilePicture());
                    patch.put("name", admin.getName());
                    client.patchDocumentMerge("admins", admin.getUsername(), patch);
                    System.out.println("✏️ Updated staff: " + admin.getUsername());
                }
                onSave.run();
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                new Alert(Alert.AlertType.ERROR, "Firestore REST Error: " + ex.getMessage()).showAndWait();
            }
        });
    }

    public static void showDeleteAdminDialog(
            StaffManagementView.Admin admin,
            Runnable onDeleteSuccess
    ) {
        Dialog<String> dlg = new Dialog<>();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Delete Admin Account");

        PasswordField passwordTf = new PasswordField();
        passwordTf.setPromptText("Enter your password to confirm");

        VBox content = new VBox(10,
            new Label("Enter your password to delete your account:"),
            passwordTf
        );
        content.setPadding(new Insets(20));

        dlg.getDialogPane().setContent(content);

        ButtonType deleteBtn = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(deleteBtn, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt == deleteBtn) {
                return passwordTf.getText().trim();
            }
            return null;
        });

        dlg.showAndWait().ifPresent(enteredPw -> {
            // ❌ Empty password
            if (enteredPw.isEmpty()) {
                new Alert(
                    Alert.AlertType.ERROR,
                    "Password is required to delete your account."
                ).showAndWait();
                return;
            }

            // ❌ Wrong password
            if (!enteredPw.equals(admin.getPassword())) {
                new Alert(
                    Alert.AlertType.ERROR,
                    "Incorrect password. Account not deleted."
                ).showAndWait();
                return;
            }

            try {
                FirestoreRestClient client = rest();
                client.deleteDocument("admins", admin.getUsername());

                new Alert(
                    Alert.AlertType.INFORMATION,
                    "Your admin account has been deleted."
                ).showAndWait();

                System.out.println("🗑 Deleted admin: " + admin.getUsername());

                // 🔥 Optional: force logout / close app
                System.exit(0);

            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                new Alert(
                    Alert.AlertType.ERROR,
                    "Failed to delete account: " + ex.getMessage()
                ).showAndWait();
            }
        });
    }
}