package nfc;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;

@SuppressWarnings("unused")
final class CRUDChildDialogSupport {
    private static final DateTimeFormatter BILLING_PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String UID_HINT_TEXT = "Scan an NFC card while this window is open to fill the UID automatically.";
    private static final String UID_HINT_BRIDGE_ERROR_TEXT = "Scanner bridge unavailable. Check Firestore access for nfcCapture/latest or use the direct USB scanner.";

    private CRUDChildDialogSupport() {
    }

    static void showChildDialog(ChildrenView.Child existing, boolean isNew, Runnable onSave) {
        Dialog<ChildrenView.Child> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        AppThemeSupport.prepareDialog(
            dialog,
            isNew ? "Add New Child" : "Edit Child",
            "Manage child identity, NFC registration, and billing configuration.",
            AppThemeSupport.Tone.INFO
        );

        FirestoreRestClient client;
        String childId;
        Map<String, Object> existingData = null;
        try {
            client = FirestoreRest.forCurrentUser();
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
            AppThemeSupport.showException(null, "Firestore REST Error", e);
            return;
        }

        TextField nameTf = new TextField();
        ComboBox<String> genderCb = new ComboBox<>();
        genderCb.getItems().setAll("Female", "Male");
        genderCb.setEditable(true);
        genderCb.setMaxWidth(Double.MAX_VALUE);
        genderCb.setPromptText("Female / Male");
        DatePicker dobPicker = new DatePicker();
        TextField placeOfBirthTf = new TextField();
        placeOfBirthTf.setPromptText("Tempat lahir / Place of birth");
        TextField uidTf = new TextField();
        uidTf.setPromptText("Scan/write NFC UID (replaceable)");
        Label uidHint = new Label(UID_HINT_TEXT);
        uidHint.setWrapText(true);
        TextField childIcTf = new TextField();
        childIcTf.setPromptText("No. IC / MyKid");
        TextField birthCertTf = new TextField();
        birthCertTf.setPromptText("No. Sijil Lahir");
        TextField siblingsTf = new TextField();
        siblingsTf.setPromptText("0");
        TextField languageTf = new TextField();
        languageTf.setPromptText("Bahasa pertuturan");
        TextArea addressTa = new TextArea();
        addressTa.setPromptText("Alamat penuh kanak-kanak");
        addressTa.setPrefRowCount(2);
        TextField fatherPhoneTf = new TextField();
        fatherPhoneTf.setPromptText("No. tel bapa");
        TextField motherPhoneTf = new TextField();
        motherPhoneTf.setPromptText("No. tel ibu");

        TextField registrationReceivedByTf = new TextField();
        registrationReceivedByTf.setPromptText("Yuran pendaftaran diterima oleh");
        DatePicker registrationReceivedDatePicker = new DatePicker();
        TextField registrationReceiptNoTf = new TextField();
        registrationReceiptNoTf.setPromptText("No. resit");
        TextField registrationChequeNoTf = new TextField();
        registrationChequeNoTf.setPromptText("No. cek");

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
        Label billingHint = new Label("Full-time monthly fees are age-based. Daily, weekly, and hourly transit plans are billed from actual attendance records.");
        billingHint.setWrapText(true);
        uidHint.getStyleClass().add("app-helper-text");
        transitHint.getStyleClass().add("app-helper-text");
        billingHint.getStyleClass().add("app-helper-text");
        Label billingModelValue = createReadOnlyValueLabel();
        Label ageBandValue = createReadOnlyValueLabel();
        Label monthlyFeeValue = createReadOnlyValueLabel();
        Label registrationTotalValue = createReadOnlyValueLabel();
        Label yearlyCoverageValue = createReadOnlyValueLabel();
        Label invoiceScheduleValue = createReadOnlyValueLabel();
        Label taskaZurahHint = createHelperLabel("Preview updates automatically from the date of birth and registration date. Saving this child now stores the fixed Taska Zurah age-based billing metadata.");
        AppThemeSupport.styleControls(
            nameTf,
            genderCb,
            dobPicker,
            placeOfBirthTf,
            uidTf,
            childIcTf,
            birthCertTf,
            siblingsTf,
            languageTf,
            addressTa,
            fatherPhoneTf,
            motherPhoneTf,
            registrationReceivedByTf,
            registrationReceivedDatePicker,
            registrationReceiptNoTf,
            registrationChequeNoTf,
            feePlanCb,
            transitDurationHintCb,
            billingDueDayCb,
            absenceLetterPeriodTf,
            absenceLetterDaysTf
        );

        Runnable syncTransitControls = () -> CRUDChildValidationSupport.syncTransitControls(
            feePlanCb.getValue(),
            transitDurationHintCb,
            schoolHolidayTransitCb,
            transitHint
        );
        feePlanCb.valueProperty().addListener((obs, oldValue, newValue) -> syncTransitControls.run());

        if (!isNew && existing != null) {
            nameTf.setText(existing.getName());
            if (existing.getBirthDate() != null) {
                dobPicker.setValue(existing.getBirthDate());
            }
            uidTf.setText(existing.getNfcUid());
        }

        if (existingData != null) {
            genderCb.getEditor().setText(safeStr(existingData.get("gender")).trim());
            placeOfBirthTf.setText(safeStr(existingData.get("placeOfBirth")).trim());
            String childIc = safeStr(existingData.get("childIcNo")).trim();
            if (childIc.isEmpty()) {
                childIc = safeStr(existingData.get("icNo")).trim();
            }
            childIcTf.setText(childIc);
            birthCertTf.setText(safeStr(existingData.get("birthCertNo")).trim());
            Object siblingsValue = existingData.get("siblingsCount");
            if (siblingsValue instanceof Number) {
                siblingsTf.setText(String.valueOf(((Number) siblingsValue).intValue()));
            } else {
                siblingsTf.setText(safeStr(siblingsValue).trim());
            }
            languageTf.setText(safeStr(existingData.get("languageSpeaking")).trim());
            addressTa.setText(safeStr(existingData.get("address")).trim());
            fatherPhoneTf.setText(safeStr(existingData.get("fatherPhone")).trim());
            motherPhoneTf.setText(safeStr(existingData.get("motherPhone")).trim());
            registrationReceivedByTf.setText(safeStr(existingData.get("registrationReceivedBy")).trim());
            registrationReceivedDatePicker.setValue(parseLocalDate(existingData.get("registrationReceivedDate")));
            registrationReceiptNoTf.setText(safeStr(existingData.get("registrationReceiptNo")).trim());
            registrationChequeNoTf.setText(safeStr(existingData.get("registrationChequeNo")).trim());

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
        }

        feePlanCb.setValue(FeePlanType.MONTHLY_FULLTIME);
        transitDurationHintCb.setValue(TransitDurationHint.AUTO);
        schoolHolidayTransitCb.setSelected(false);
        transportFromTadikaCb.setSelected(false);
        billingDueDayCb.setValue(7);
        feePlanCb.setDisable(true);
        transitDurationHintCb.setDisable(true);
        schoolHolidayTransitCb.setDisable(true);
        transportFromTadikaCb.setDisable(true);
        billingDueDayCb.setDisable(true);

        Runnable refreshTaskaZurahPreview = () -> updateTaskaZurahPreview(
            dobPicker.getValue(),
            registrationReceivedDatePicker.getValue(),
            billingModelValue,
            ageBandValue,
            monthlyFeeValue,
            registrationTotalValue,
            yearlyCoverageValue,
            invoiceScheduleValue
        );
        dobPicker.valueProperty().addListener((obs, oldValue, newValue) -> refreshTaskaZurahPreview.run());
        registrationReceivedDatePicker.valueProperty().addListener((obs, oldValue, newValue) -> refreshTaskaZurahPreview.run());
        refreshTaskaZurahPreview.run();

        boolean existingTransportDefault = existingData != null && Boolean.TRUE.equals(existingData.get("transportFromTadika"));
        int dueDayDefault = 7;
        if (existingData != null) {
            Object dueObj = existingData.get("billingDueDay");
            if (dueObj instanceof Number) {
                int d = ((Number) dueObj).intValue();
                if (d == 5 || d == 7) {
                    dueDayDefault = d;
                }
            }
        }
        transportFromTadikaCb.setSelected(existingTransportDefault);
        billingDueDayCb.setValue(dueDayDefault);
        syncTransitControls.run();

        GridPane basicGrid = createFormGrid();
        basicGrid.addRow(0, new Label("Child Name"), nameTf);
        basicGrid.addRow(1, new Label("Gender"), genderCb);
        basicGrid.addRow(2, new Label("Birth Date"), dobPicker);
        basicGrid.addRow(3, new Label("Place of Birth"), placeOfBirthTf);
        basicGrid.addRow(4, new Label("NFC UID"), uidTf);
        basicGrid.add(uidHint, 1, 5);

        GridPane identificationGrid = createFormGrid();
        identificationGrid.addRow(0, new Label("MyKid No"), childIcTf);
        identificationGrid.addRow(1, new Label("Birth Certificate No"), birthCertTf);
        identificationGrid.addRow(2, new Label("No. of Siblings"), siblingsTf);
        identificationGrid.addRow(3, new Label("Language Speaking"), languageTf);
        identificationGrid.addRow(4, new Label("Home Address"), addressTa);
        identificationGrid.addRow(5, new Label("Father's Phone No"), fatherPhoneTf);
        identificationGrid.addRow(6, new Label("Mother's Phone No"), motherPhoneTf);

        GridPane officeUseGrid = createFormGrid();
        officeUseGrid.addRow(0, new Label("Registration Fee Received By"), registrationReceivedByTf);
        officeUseGrid.addRow(1, new Label("Date Received"), registrationReceivedDatePicker);
        officeUseGrid.addRow(2, new Label("Receipt No"), registrationReceiptNoTf);
        officeUseGrid.addRow(3, new Label("Cheque No"), registrationChequeNoTf);

        GridPane billingGrid = createFormGrid();
        int billingRow = 0;
        billingGrid.addRow(billingRow++, new Label("Billing Model"), billingModelValue);
        billingGrid.addRow(billingRow++, new Label("Age Band Preview"), ageBandValue);
        billingGrid.addRow(billingRow++, new Label("Monthly Fee Preview"), monthlyFeeValue);
        billingGrid.addRow(billingRow++, new Label("Registration Total Preview"), registrationTotalValue);
        billingGrid.addRow(billingRow++, new Label("Yearly Fee Covers"), yearlyCoverageValue);
        billingGrid.addRow(billingRow++, new Label("Invoice Schedule"), invoiceScheduleValue);
        billingGrid.add(taskaZurahHint, 1, billingRow++);
        billingGrid.add(billingHint, 1, billingRow);

        GridPane optionalGrid = createFormGrid();
        int optionalRow = 0;
        optionalGrid.addRow(optionalRow++, new Label("Approved Absence Letter Period"), absenceLetterPeriodTf);
        optionalGrid.addRow(optionalRow++, new Label("Absence Days With Letter"), absenceLetterDaysTf);
        optionalGrid.add(absenceLetterApprovedCb, 1, optionalRow++);
        optionalGrid.add(staffChildCb, 1, optionalRow++);
        Label optionalNote = new Label("All monthly and yearly totals remain auto-calculated by the backend.");
        optionalNote.getStyleClass().add("app-helper-text");
        optionalNote.setWrapText(true);
        optionalGrid.add(optionalNote, 1, optionalRow);

        VBox content = AppThemeSupport.createDialogContent(
            AppThemeSupport.createFormSection(
                "Basic Info",
                "Capture the child registration profile and keep the NFC UID ready for attendance scanning.",
                basicGrid
            ),
            AppThemeSupport.createFormSection(
                "Identification",
                "Store the exact child details from the registration form for admin review and family contact.",
                identificationGrid
            ),
            AppThemeSupport.createFormSection(
                "Office Use",
                "Track the staff receiver, received date, receipt number, and cheque reference from the paper registration form.",
                officeUseGrid
            ),
            AppThemeSupport.createFormSection(
                "Billing Plan",
                "Preview the fixed Taska Zurah age-based policy that will be saved with this child.",
                billingGrid
            ),
            AppThemeSupport.createFormSection(
                "Optional Billing Controls",
                "Use these toggles for approved absence-letter discounts and staff-child notes only.",
                optionalGrid
            )
        );
        ScrollPane scrollPane = AppThemeSupport.wrapDialogContent(content);

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
        AppThemeSupport.styleDialogButtons(dialog, saveType);

        dialog.setResultConverter(btn -> {
            if (btn == saveType) {
                return new ChildrenView.Child(
                    childId,
                    nameTf.getText().trim(),
                    dobPicker.getValue(),
                    "",
                    "",
                    "",
                    uidTf.getText().trim()
                );
            }
            return null;
        });

        Consumer<String> tagCapture = tagId -> {
            if (!dialog.isShowing()) {
                return;
            }
            String normalizedTagId = tagId == null ? "" : tagId.trim().toUpperCase();
            uidTf.setText(normalizedTagId);
            uidTf.positionCaret(normalizedTagId.length());
        };

        Optional<ChildrenView.Child> dialogResult;
        NFCReader dialogReader = null;
        Thread dialogReaderThread = null;
        String initialBridgeFingerprint = "";
        try {
            initialBridgeFingerprint = latestBridgeFingerprint(client.getDocument("nfcCapture", "latest"));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        AtomicReference<String> lastCapturedBridgeFingerprint = new AtomicReference<>(initialBridgeFingerprint);
        AtomicReference<String> lastBridgeError = new AtomicReference<>("");
        ScheduledExecutorService bridgePoller = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("taska-nfc-bridge-poller");
            thread.setDaemon(true);
            return thread;
        });
        NFCReader.setTagCaptureConsumer(tagCapture);
        try {
            bridgePoller.scheduleWithFixedDelay(() -> {
                try {
                    FsDocument latestScan = client.getDocument("nfcCapture", "latest");
                    if (latestScan == null) {
                        return;
                    }

                    String bridgeFingerprint = latestBridgeFingerprint(latestScan);
                    String uid = safeStr(latestScan.getString("uid")).trim();
                    if (bridgeFingerprint.isEmpty() || uid.isEmpty()) {
                        return;
                    }

                    String previousFingerprint = lastCapturedBridgeFingerprint.getAndSet(bridgeFingerprint);
                    if (bridgeFingerprint.equals(previousFingerprint)) {
                        return;
                    }

                    if (!lastBridgeError.get().isEmpty()) {
                        lastBridgeError.set("");
                        Platform.runLater(() -> uidHint.setText(UID_HINT_TEXT));
                    }
                    Platform.runLater(() -> tagCapture.accept(uid));
                } catch (IOException | InterruptedException ex) {
                    if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    String previousError = lastBridgeError.getAndSet(UID_HINT_BRIDGE_ERROR_TEXT);
                    if (!UID_HINT_BRIDGE_ERROR_TEXT.equals(previousError)) {
                        System.err.println("CRUDChildDialogSupport: NFC bridge read failed - " + ex.getMessage());
                        Platform.runLater(() -> uidHint.setText(UID_HINT_BRIDGE_ERROR_TEXT));
                    }
                } catch (RuntimeException ex) {
                    System.err.println("CRUDChildDialogSupport: NFC bridge poll failed - " + ex.getMessage());
                }
            }, 0L, 700L, TimeUnit.MILLISECONDS);

            if (NFCReader.resolveConfiguredPortName() == null) {
                dialogReader = new NFCReader("auto");
                dialogReaderThread = new Thread(dialogReader);
                dialogReaderThread.setName("taska-nfc-dialog-reader");
                dialogReaderThread.setDaemon(true);
                dialogReaderThread.start();
            }
            dialogResult = dialog.showAndWait();
        } finally {
            NFCReader.clearTagCaptureConsumer(tagCapture);
            bridgePoller.shutdownNow();
            if (dialogReader != null) {
                dialogReader.stopReading();
            }
            if (dialogReaderThread != null && dialogReaderThread.isAlive()) {
                dialogReaderThread.interrupt();
                try {
                    dialogReaderThread.join(1000L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        dialogResult.ifPresent(child -> {
            try {
                Integer dueDay = billingDueDayCb.getValue();
                CRUDChildPersistenceSupport.SaveRequest request = new CRUDChildPersistenceSupport.SaveRequest(
                    client,
                    childId,
                    child,
                    isNew,
                    genderCb.getEditor().getText(),
                    placeOfBirthTf.getText(),
                    childIcTf.getText(),
                    birthCertTf.getText(),
                    siblingsTf.getText(),
                    languageTf.getText(),
                    addressTa.getText(),
                    fatherPhoneTf.getText(),
                    motherPhoneTf.getText(),
                    registrationReceivedByTf.getText(),
                    registrationReceivedDatePicker.getValue(),
                    registrationReceiptNoTf.getText(),
                    registrationChequeNoTf.getText(),
                    staffChildCb.isSelected(),
                    feePlanCb.getValue(),
                    transitDurationHintCb.getValue(),
                    schoolHolidayTransitCb.isSelected(),
                    transportFromTadikaCb.isSelected(),
                    dueDay != null ? dueDay : 7,
                    absenceLetterApprovedCb.isSelected(),
                    absenceLetterPeriodTf.getText(),
                    absenceLetterDaysTf.getText()
                );
                if (!CRUDChildPersistenceSupport.saveChild(request)) {
                    return;
                }

                onSave.run();
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                AppThemeSupport.showException(null, "Firestore REST Error", ex);
            }
        });
    }

    private static GridPane createFormGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.getStyleClass().add("app-form-grid");
        return grid;
    }

    private static Label createHelperLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("app-helper-text");
        label.setWrapText(true);
        return label;
    }

    private static Label createReadOnlyValueLabel() {
        Label label = new Label("-");
        label.setWrapText(true);
        label.setStyle("-fx-background-color: rgba(255,255,255,0.72); -fx-background-radius: 12; -fx-padding: 10 12 10 12; -fx-text-fill: #1f2d3d;");
        return label;
    }

    private static String safeStr(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static LocalDate parseLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof Date) {
            return new java.sql.Date(((Date) value).getTime()).toLocalDate();
        }

        String raw = safeStr(value).trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private static void updateTaskaZurahPreview(
        LocalDate birthDate,
        LocalDate registrationDate,
        Label billingModelValue,
        Label ageBandValue,
        Label monthlyFeeValue,
        Label registrationTotalValue,
        Label yearlyCoverageValue,
        Label invoiceScheduleValue
    ) {
        LocalDate effectiveRegistrationDate = registrationDate == null ? LocalDate.now() : registrationDate;
        CRUDChildValidationSupport.RegistrationPreview preview = CRUDChildValidationSupport.deriveRegistrationPreview(
            effectiveRegistrationDate,
            birthDate,
            effectiveRegistrationDate
        );
        CRUDChildValidationSupport.BillingProfile billingProfile = preview.billingProfile();

        billingModelValue.setText("Taska Zurah registered child age-based billing");
        ageBandValue.setText(resolveAgeBandPreviewText(billingProfile));
        monthlyFeeValue.setText(formatMoneySen(billingProfile.monthlyFeeSen()));
        registrationTotalValue.setText(
            formatMoneySen(preview.registrationTotalSen())
                + " total ("
                + formatMoneySen(preview.registrationFeeSen())
                + " registration + "
                + formatMoneySen(preview.insuranceTakafulSen())
                + " insurance/takaful + "
                + formatMoneySen(preview.yearlyMaintenanceFeeSen())
                + " yearly maintenance + "
                + formatMoneySen(billingProfile.monthlyFeeSen())
                + " monthly fee)"
        );
        yearlyCoverageValue.setText(billingProfile.yearlyFeeCoveredYear() == null
            ? "Follows the next generated January invoice."
            : String.valueOf(billingProfile.yearlyFeeCoveredYear()));
        invoiceScheduleValue.setText("Generated on " + preview.invoiceGenerationDay() + "st each month and due on the 7th.");
    }

    private static String resolveAgeBandPreviewText(CRUDChildValidationSupport.BillingProfile billingProfile) {
        if (billingProfile == null) {
            return "Birth date required for exact preview.";
        }
        if ("missing_birth_date".equals(billingProfile.agePolicyReason())) {
            return "Birth date required for exact preview. The fallback preview uses the 4 years to below 5 years band.";
        }
        String base = CRUDChildValidationSupport.describeAgeBand(billingProfile.ageBand());
        if (billingProfile.ageOutOfPolicy()) {
            return base + " (manual review required)";
        }
        return base;
    }

    private static String formatMoneySen(int amountSen) {
        return String.format("RM%,.2f", amountSen / 100.0d);
    }

    private static String latestBridgeFingerprint(FsDocument latestScan) {
        if (latestScan == null) {
            return "";
        }

        Date scannedAt = latestScan.getDate("scannedAt");
        String uid = safeStr(latestScan.getString("uid")).trim().toUpperCase();
        if (scannedAt == null || uid.isEmpty()) {
            return "";
        }
        return scannedAt.getTime() + ":" + uid;
    }

    private static String newDocId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    public enum FeePlanType {
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

    public enum TransitDurationHint {
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
}