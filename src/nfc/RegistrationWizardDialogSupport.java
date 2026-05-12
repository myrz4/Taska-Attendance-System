package nfc;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;

final class RegistrationWizardDialogSupport {
    private static final String UID_HINT_TEXT = "Scan an NFC card while this window is open to fill the UID automatically.";
    private static final String UID_HINT_BRIDGE_ERROR_TEXT = "Scanner bridge unavailable. Check Firestore access for nfcCapture/latest or use the direct USB scanner.";

    private RegistrationWizardDialogSupport() {
    }

    static void showRegistrationWizard(Runnable onSave) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        AppThemeSupport.prepareDialog(
            dialog,
            "New Registration",
            "Follow the Taska Zurah registration form page by page and create the child with linked parent records in one save.",
            AppThemeSupport.Tone.INFO
        );
        dialog.setResizable(true);

        FirestoreRestClient client = FirestoreRest.forCurrentUser();

        String childId = newDocId();

        TextField childNameTf = new TextField();
        ComboBox<String> genderCb = new ComboBox<>();
        genderCb.getItems().setAll("Female", "Male");
        genderCb.setEditable(true);
        genderCb.setMaxWidth(Double.MAX_VALUE);
        DatePicker dobPicker = new DatePicker();
        TextField birthCertTf = new TextField();
        birthCertTf.setPromptText("No. sijil lahir");
        TextField myKidTf = new TextField();
        myKidTf.setPromptText("No. MyKid");
        TextField siblingsTf = new TextField();
        siblingsTf.setPromptText("0");
        TextArea addressTa = new TextArea();
        addressTa.setPromptText("Alamat rumah");
        addressTa.setPrefRowCount(2);
        TextField languageTf = new TextField();
        languageTf.setPromptText("Bahasa pertuturan");
        TextField fatherPhonePage1Tf = new TextField();
        fatherPhonePage1Tf.setPromptText("No. tel bapa");
        TextField motherPhonePage1Tf = new TextField();
        motherPhonePage1Tf.setPromptText("No. tel ibu");
        TextField registrationReceivedByTf = new TextField();
        registrationReceivedByTf.setPromptText("Yuran pendaftaran diterima oleh");
        DatePicker registrationReceivedDatePicker = new DatePicker();
        TextField registrationReceiptNoTf = new TextField();
        registrationReceiptNoTf.setPromptText("No. resit");
        TextField registrationChequeNoTf = new TextField();
        registrationChequeNoTf.setPromptText("No. cek");

        TextField fatherNameTf = new TextField();
        fatherNameTf.setPromptText("Nama bapa");
        ComboBox<ExistingParentOption> fatherExistingParentCb = new ComboBox<>();
        fatherExistingParentCb.setMaxWidth(Double.MAX_VALUE);
        TextField fatherOccupationTf = new TextField();
        fatherOccupationTf.setPromptText("Pekerjaan");
        TextField fatherDepartmentTf = new TextField();
        fatherDepartmentTf.setPromptText("Jabatan");
        TextField fatherPhoneTf = new TextField();
        fatherPhoneTf.setPromptText("No tel");
        TextField fatherNationalityTf = new TextField();
        fatherNationalityTf.setPromptText("Warganegara");

        TextField motherNameTf = new TextField();
        motherNameTf.setPromptText("Nama ibu");
    ComboBox<ExistingParentOption> motherExistingParentCb = new ComboBox<>();
    motherExistingParentCb.setMaxWidth(Double.MAX_VALUE);
        TextField motherOccupationTf = new TextField();
        motherOccupationTf.setPromptText("Pekerjaan");
        TextField motherDepartmentTf = new TextField();
        motherDepartmentTf.setPromptText("Jabatan");
        TextField motherPhoneTf = new TextField();
        motherPhoneTf.setPromptText("No tel");
        TextField motherNationalityTf = new TextField();
        motherNationalityTf.setPromptText("Warganegara");

        Bindings.bindBidirectional(fatherPhonePage1Tf.textProperty(), fatherPhoneTf.textProperty());
        Bindings.bindBidirectional(motherPhonePage1Tf.textProperty(), motherPhoneTf.textProperty());

        TextField emergency1NameTf = new TextField();
        emergency1NameTf.setPromptText("Nama");
        TextArea emergency1AddressTa = new TextArea();
        emergency1AddressTa.setPromptText("Alamat");
        emergency1AddressTa.setPrefRowCount(2);
        TextField emergency1PhoneTf = new TextField();
        emergency1PhoneTf.setPromptText("No tel");
        TextField emergency1OfficePhoneTf = new TextField();
        emergency1OfficePhoneTf.setPromptText("No tel pejabat");

        TextField emergency2NameTf = new TextField();
        emergency2NameTf.setPromptText("Nama");
        TextArea emergency2AddressTa = new TextArea();
        emergency2AddressTa.setPromptText("Alamat");
        emergency2AddressTa.setPrefRowCount(2);
        TextField emergency2PhoneTf = new TextField();
        emergency2PhoneTf.setPromptText("No tel");
        TextField emergency2OfficePhoneTf = new TextField();
        emergency2OfficePhoneTf.setPromptText("No tel pejabat");

        Label healthNameValue = createBoundValueLabel(childNameTf.textProperty());
        Label healthGenderValue = createBoundValueLabel(genderCb.getEditor().textProperty());
        Label healthDobValue = createBoundValueLabel(Bindings.createStringBinding(
            () -> dobPicker.getValue() == null ? "" : dobPicker.getValue().toString(),
            dobPicker.valueProperty()
        ));
        Label healthBirthCertValue = createBoundValueLabel(birthCertTf.textProperty());
        Label healthSiblingsValue = createBoundValueLabel(siblingsTf.textProperty());

        TextField placeOfBirthTf = new TextField();
        placeOfBirthTf.setPromptText("Tempat lahir");
        TextField uidTf = new TextField();
        uidTf.setPromptText("Scan/write NFC UID (replaceable)");
        Label uidHint = new Label(UID_HINT_TEXT);
        uidHint.setWrapText(true);
        uidHint.getStyleClass().add("app-helper-text");
        ComboBox<CRUDChildDialogSupport.FeePlanType> feePlanCb = new ComboBox<>();
        feePlanCb.getItems().setAll(CRUDChildDialogSupport.FeePlanType.values());
        feePlanCb.setMaxWidth(Double.MAX_VALUE);
        feePlanCb.setValue(CRUDChildDialogSupport.FeePlanType.MONTHLY_FULLTIME);
        ComboBox<CRUDChildDialogSupport.TransitDurationHint> transitDurationHintCb = new ComboBox<>();
        transitDurationHintCb.getItems().setAll(CRUDChildDialogSupport.TransitDurationHint.values());
        transitDurationHintCb.setMaxWidth(Double.MAX_VALUE);
        transitDurationHintCb.setValue(CRUDChildDialogSupport.TransitDurationHint.AUTO);
        CheckBox schoolHolidayTransitCb = new CheckBox("Use school-holiday monthly transit rate");
        CheckBox transportFromTadikaCb = new CheckBox("Add transport from tadika (RM150/month)");
        ComboBox<Integer> billingDueDayCb = new ComboBox<>();
        billingDueDayCb.getItems().setAll(5, 7);
        billingDueDayCb.setMaxWidth(Double.MAX_VALUE);
        billingDueDayCb.setValue(7);
        Label transitHint = new Label("For generic monthly transit, backend will use school-holiday override first, then duration hint, then attendance time if no duration is saved.");
        transitHint.setWrapText(true);
        transitHint.getStyleClass().add("app-helper-text");
        Label enrollmentHint = new Label("Keep the NFC UID ready. Billing is now saved automatically using the fixed Taska Zurah age-based policy.");
        enrollmentHint.setWrapText(true);
        enrollmentHint.getStyleClass().add("app-helper-text");
        Label billingModelValue = createReadOnlyValueLabel();
        Label ageBandValue = createReadOnlyValueLabel();
        Label monthlyFeeValue = createReadOnlyValueLabel();
        Label registrationTotalValue = createReadOnlyValueLabel();
        Label yearlyCoverageValue = createReadOnlyValueLabel();
        Label invoiceScheduleValue = createReadOnlyValueLabel();
        Label previewHint = createHelperLabel("Preview updates automatically from the date of birth and registration date. The desktop app saves the fixed Taska Zurah policy values, not a selectable transit plan.");

        AppThemeSupport.styleControls(
            childNameTf,
            genderCb,
            dobPicker,
            birthCertTf,
            myKidTf,
            siblingsTf,
            addressTa,
            languageTf,
            fatherPhonePage1Tf,
            motherPhonePage1Tf,
            registrationReceivedByTf,
            registrationReceivedDatePicker,
            registrationReceiptNoTf,
            registrationChequeNoTf,
            fatherExistingParentCb,
            fatherNameTf,
            fatherOccupationTf,
            fatherDepartmentTf,
            fatherPhoneTf,
            fatherNationalityTf,
            motherExistingParentCb,
            motherNameTf,
            motherOccupationTf,
            motherDepartmentTf,
            motherPhoneTf,
            motherNationalityTf,
            emergency1NameTf,
            emergency1AddressTa,
            emergency1PhoneTf,
            emergency1OfficePhoneTf,
            emergency2NameTf,
            emergency2AddressTa,
            emergency2PhoneTf,
            emergency2OfficePhoneTf,
            placeOfBirthTf,
            uidTf,
            feePlanCb,
            transitDurationHintCb,
            billingDueDayCb
        );

        Runnable syncTransitControls = () -> CRUDChildValidationSupport.syncTransitControls(
            feePlanCb.getValue(),
            transitDurationHintCb,
            schoolHolidayTransitCb,
            transitHint
        );
        feePlanCb.valueProperty().addListener((obs, oldValue, newValue) -> syncTransitControls.run());
        syncTransitControls.run();
        feePlanCb.setValue(CRUDChildDialogSupport.FeePlanType.MONTHLY_FULLTIME);
        transitDurationHintCb.setValue(CRUDChildDialogSupport.TransitDurationHint.AUTO);
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

        fatherExistingParentCb.getItems().setAll(loadExistingParentOptions(client, CRUDParentDialogSupport.RelationshipType.FATHER));
        fatherExistingParentCb.setValue(fatherExistingParentCb.getItems().get(0));
        motherExistingParentCb.getItems().setAll(loadExistingParentOptions(client, CRUDParentDialogSupport.RelationshipType.MOTHER));
        motherExistingParentCb.setValue(motherExistingParentCb.getItems().get(0));

        Label fatherSelectionHint = createHelperLabel("Choose an existing father to link siblings, or leave Create New selected to register a new father record.");
        Label motherSelectionHint = createHelperLabel("Choose an existing mother to link siblings, or leave Create New selected to register a new mother record.");
        configureParentSelection(
            fatherExistingParentCb,
            fatherNameTf,
            fatherOccupationTf,
            fatherDepartmentTf,
            fatherPhoneTf,
            fatherNationalityTf,
            fatherPhonePage1Tf
        );
        configureParentSelection(
            motherExistingParentCb,
            motherNameTf,
            motherOccupationTf,
            motherDepartmentTf,
            motherPhoneTf,
            motherNationalityTf,
            motherPhonePage1Tf
        );

        GridPane childInfoGrid = createFormGrid();
        childInfoGrid.addRow(0, new Label("Nama / Name"), childNameTf);
        childInfoGrid.addRow(1, new Label("Jantina / Gender"), genderCb);
        childInfoGrid.addRow(2, new Label("Tarikh Lahir / Date of Birth"), dobPicker);
        childInfoGrid.addRow(3, new Label("No Sijil Lahir / Birth Certificate No"), birthCertTf);
        childInfoGrid.addRow(4, new Label("No MyKid / MyKid No"), myKidTf);
        childInfoGrid.addRow(5, new Label("Bilangan Adik Beradik / No of Siblings"), siblingsTf);
        childInfoGrid.addRow(6, new Label("Alamat Rumah / Home Address"), addressTa);
        childInfoGrid.addRow(7, new Label("No Tel Bapa / Father's Phone No"), fatherPhonePage1Tf);
        childInfoGrid.addRow(8, new Label("No Tel Ibu / Mother's Phone No"), motherPhonePage1Tf);
        childInfoGrid.addRow(9, new Label("Pertuturan Bahasa / Language Speaking"), languageTf);

        GridPane officeUseGrid = createFormGrid();
        officeUseGrid.addRow(0, new Label("Yuran pendaftaran diterima oleh"), registrationReceivedByTf);
        officeUseGrid.addRow(1, new Label("Tarikh penerimaan"), registrationReceivedDatePicker);
        officeUseGrid.addRow(2, new Label("No. resit"), registrationReceiptNoTf);
        officeUseGrid.addRow(3, new Label("No. cek"), registrationChequeNoTf);

        VBox page1Content = AppThemeSupport.createDialogContent(
            AppThemeSupport.createFormSection(
                "Child Information",
                "Page 1 of the stakeholder form for the child's core registration details.",
                childInfoGrid
            ),
            AppThemeSupport.createFormSection(
                "For Office Use",
                "Record the staff receiver and receipt references exactly as written on the paper form.",
                officeUseGrid
            )
        );

        GridPane fatherGrid = createFormGrid();
    fatherGrid.addRow(0, new Label("Father Record"), fatherExistingParentCb);
    fatherGrid.add(fatherSelectionHint, 1, 1);
    fatherGrid.addRow(2, new Label("Father's Name"), fatherNameTf);
    fatherGrid.addRow(3, new Label("Occupation"), fatherOccupationTf);
    fatherGrid.addRow(4, new Label("Department"), fatherDepartmentTf);
    fatherGrid.addRow(5, new Label("Contact No"), fatherPhoneTf);
    fatherGrid.addRow(6, new Label("Nationality"), fatherNationalityTf);

        GridPane motherGrid = createFormGrid();
    motherGrid.addRow(0, new Label("Mother Record"), motherExistingParentCb);
    motherGrid.add(motherSelectionHint, 1, 1);
    motherGrid.addRow(2, new Label("Mother's Name"), motherNameTf);
    motherGrid.addRow(3, new Label("Occupation"), motherOccupationTf);
    motherGrid.addRow(4, new Label("Department"), motherDepartmentTf);
    motherGrid.addRow(5, new Label("Contact No"), motherPhoneTf);
    motherGrid.addRow(6, new Label("Nationality"), motherNationalityTf);

        GridPane emergency1Grid = createFormGrid();
        emergency1Grid.addRow(0, new Label("Nama / Name"), emergency1NameTf);
        emergency1Grid.addRow(1, new Label("Alamat / Address"), emergency1AddressTa);
        emergency1Grid.addRow(2, new Label("No tel / Contact no"), emergency1PhoneTf);
        emergency1Grid.addRow(3, new Label("No tel pejabat / Office contact no"), emergency1OfficePhoneTf);

        GridPane emergency2Grid = createFormGrid();
        emergency2Grid.addRow(0, new Label("Nama / Name"), emergency2NameTf);
        emergency2Grid.addRow(1, new Label("Alamat / Address"), emergency2AddressTa);
        emergency2Grid.addRow(2, new Label("No tel / Contact no"), emergency2PhoneTf);
        emergency2Grid.addRow(3, new Label("No tel pejabat / Office contact no"), emergency2OfficePhoneTf);

        VBox page2Content = AppThemeSupport.createDialogContent(
            AppThemeSupport.createFormSection(
                "Father Particulars",
                "Page 2 father details from the registration form.",
                fatherGrid
            ),
            AppThemeSupport.createFormSection(
                "Mother Particulars",
                "Page 2 mother details from the registration form.",
                motherGrid
            ),
            AppThemeSupport.createFormSection(
                "Emergency Contact 1",
                "Who can be contacted during an emergency besides the parents.",
                emergency1Grid
            ),
            AppThemeSupport.createFormSection(
                "Emergency Contact 2",
                "Secondary emergency contact besides the parents.",
                emergency2Grid
            )
        );

        GridPane healthGrid = createFormGrid();
        healthGrid.addRow(0, new Label("Nama / Name"), healthNameValue);
        healthGrid.addRow(1, new Label("Jantina / Gender"), healthGenderValue);
        healthGrid.addRow(2, new Label("Tarikh Lahir / Date of Birth"), healthDobValue);
        healthGrid.addRow(3, new Label("Tempat Lahir / Place of Birth"), placeOfBirthTf);
        healthGrid.addRow(4, new Label("No sijil kelahiran / Birth Certificate"), healthBirthCertValue);
        healthGrid.addRow(5, new Label("Bil. Adik Beradik / Siblings No"), healthSiblingsValue);

        GridPane systemGrid = createFormGrid();
        systemGrid.addRow(0, new Label("NFC UID"), uidTf);
        systemGrid.add(uidHint, 1, 1);
        systemGrid.addRow(2, new Label("Billing Model"), billingModelValue);
        systemGrid.addRow(3, new Label("Age Band Preview"), ageBandValue);
        systemGrid.addRow(4, new Label("Monthly Fee Preview"), monthlyFeeValue);
        systemGrid.addRow(5, new Label("Registration Total Preview"), registrationTotalValue);
        systemGrid.addRow(6, new Label("Yearly Fee Covers"), yearlyCoverageValue);
        systemGrid.addRow(7, new Label("Invoice Schedule"), invoiceScheduleValue);
        systemGrid.add(previewHint, 1, 8);
        systemGrid.add(enrollmentHint, 1, 9);

        VBox page3Content = AppThemeSupport.createDialogContent(
            AppThemeSupport.createFormSection(
                "Children Health Record",
                "Page 3 health-record identity details from the paper form.",
                healthGrid
            ),
            AppThemeSupport.createFormSection(
                "System Enrollment",
                "Administrative fields required by the desktop system, with billing saved automatically from the fixed Taska Zurah policy.",
                systemGrid
            )
        );

        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(
            createTab("Page 1", page1Content),
            createTab("Page 2", page2Content),
            createTab("Page 3", page3Content)
        );

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefWidth(Math.min(visualBounds.getWidth() * 0.82, 920));
        dialog.getDialogPane().setPrefHeight(Math.min(visualBounds.getHeight() * 0.9, 900));
        dialog.getDialogPane().setContent(tabs);

        ButtonType saveType = new ButtonType("Save Registration", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        AppThemeSupport.styleDialogButtons(dialog, saveType);
        dialog.setResultConverter(btn -> btn == saveType ? Boolean.TRUE : null);

        Consumer<String> tagCapture = tagId -> {
            if (!dialog.isShowing()) {
                return;
            }
            String normalizedTagId = tagId == null ? "" : tagId.trim().toUpperCase();
            uidTf.setText(normalizedTagId);
            uidTf.positionCaret(normalizedTagId.length());
        };

        Optional<Boolean> result;
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
            thread.setName("taska-nfc-registration-bridge-poller");
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
                    String uid = safeText(latestScan.getString("uid"));
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
                        System.err.println("RegistrationWizardDialogSupport: NFC bridge read failed - " + ex.getMessage());
                        Platform.runLater(() -> uidHint.setText(UID_HINT_BRIDGE_ERROR_TEXT));
                    }
                } catch (RuntimeException ex) {
                    System.err.println("RegistrationWizardDialogSupport: NFC bridge poll failed - " + ex.getMessage());
                }
            }, 0L, 700L, TimeUnit.MILLISECONDS);

            if (NFCReader.resolveConfiguredPortName() == null) {
                dialogReader = new NFCReader("auto");
                dialogReaderThread = new Thread(dialogReader);
                dialogReaderThread.setName("taska-nfc-registration-reader");
                dialogReaderThread.setDaemon(true);
                dialogReaderThread.start();
            }
            result = dialog.showAndWait();
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

        if (result.isEmpty()) {
            return;
        }

        try {
            ParentDraft fatherDraft = ParentDraft.fromSelection(
                CRUDParentDialogSupport.RelationshipType.FATHER,
                fatherExistingParentCb.getValue(),
                fatherNameTf.getText(),
                fatherPhoneTf.getText(),
                fatherOccupationTf.getText(),
                fatherDepartmentTf.getText(),
                fatherNationalityTf.getText()
            );
            ParentDraft motherDraft = ParentDraft.fromSelection(
                CRUDParentDialogSupport.RelationshipType.MOTHER,
                motherExistingParentCb.getValue(),
                motherNameTf.getText(),
                motherPhoneTf.getText(),
                motherOccupationTf.getText(),
                motherDepartmentTf.getText(),
                motherNationalityTf.getText()
            );
            if (fatherDraft == null && motherDraft == null) {
                AppThemeSupport.showError(null, "Parent Details Required", "Fill in at least one parent particulars section before saving registration.");
                return;
            }

            ChildrenView.Child child = new ChildrenView.Child(
                childId,
                safeText(childNameTf.getText()),
                dobPicker.getValue(),
                "",
                "",
                "",
                safeText(uidTf.getText())
            );

            Integer dueDay = billingDueDayCb.getValue();
            CRUDChildPersistenceSupport.SaveRequest request = new CRUDChildPersistenceSupport.SaveRequest(
                client,
                childId,
                child,
                true,
                genderCb.getEditor().getText(),
                placeOfBirthTf.getText(),
                myKidTf.getText(),
                birthCertTf.getText(),
                siblingsTf.getText(),
                languageTf.getText(),
                addressTa.getText(),
                fatherPhonePage1Tf.getText(),
                motherPhonePage1Tf.getText(),
                registrationReceivedByTf.getText(),
                registrationReceivedDatePicker.getValue(),
                registrationReceiptNoTf.getText(),
                registrationChequeNoTf.getText(),
                false,
                feePlanCb.getValue(),
                transitDurationHintCb.getValue(),
                schoolHolidayTransitCb.isSelected(),
                transportFromTadikaCb.isSelected(),
                dueDay != null ? dueDay : 7,
                false,
                "",
                ""
            );
            if (!CRUDChildPersistenceSupport.saveChild(request)) {
                return;
            }

            List<Map<String, Object>> emergencyContacts = buildEmergencyContacts(
                emergency1NameTf,
                emergency1AddressTa,
                emergency1PhoneTf,
                emergency1OfficePhoneTf,
                emergency2NameTf,
                emergency2AddressTa,
                emergency2PhoneTf,
                emergency2OfficePhoneTf
            );

            List<String> invoiceParentIds = new ArrayList<>();
            addUniqueParentId(invoiceParentIds, saveParentIfPresent(client, childId, child.getName(), fatherDraft, emergencyContacts));
            addUniqueParentId(invoiceParentIds, saveParentIfPresent(client, childId, child.getName(), motherDraft, emergencyContacts));
            CRUDParentDialogSupport.refreshChildParentCacheAsync(client, List.of(childId));
            issueRegistrationInvoices(invoiceParentIds, registrationReceivedDatePicker.getValue());

            if (onSave != null) {
                onSave.run();
            }
        } catch (IllegalArgumentException ex) {
            AppThemeSupport.showError(null, "Invalid Registration", ex.getMessage());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppThemeSupport.showException(null, "Firestore REST Error", ex);
        }
    }

    private static Tab createTab(String title, VBox content) {
        ScrollPane scroll = AppThemeSupport.wrapDialogContent(content);
        scroll.setFitToWidth(true);
        Tab tab = new Tab(title, scroll);
        tab.setClosable(false);
        return tab;
    }

    private static Label createBoundValueLabel(javafx.beans.value.ObservableValue<String> observable) {
        Label label = new Label();
        label.textProperty().bind(Bindings.createStringBinding(
            () -> {
                String value = observable.getValue();
                return value == null || value.isBlank() ? "-" : value;
            },
            observable
        ));
        label.setWrapText(true);
        label.setStyle("-fx-background-color: rgba(255,255,255,0.72); -fx-background-radius: 12; -fx-padding: 10 12 10 12; -fx-text-fill: #1f2d3d;");
        return label;
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

    private static List<ExistingParentOption> loadExistingParentOptions(
        FirestoreRestClient client,
        CRUDParentDialogSupport.RelationshipType relationshipType
    ) {
        List<ExistingParentOption> options = new ArrayList<>();
        options.add(ExistingParentOption.createNew(relationshipType));

        try {
            for (FsDocument parentDoc : client.listDocuments("parents")) {
                if (parentDoc == null || parentDoc.getId() == null) {
                    continue;
                }

                CRUDParentDialogSupport.RelationshipType type = CRUDParentDialogSupport.RelationshipType.fromFirestore(parentDoc.get("relationshipType"));
                if (type != relationshipType) {
                    continue;
                }
                options.add(ExistingParentOption.fromDocument(parentDoc, relationshipType));
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("RegistrationWizardDialogSupport: failed to load existing parents - " + ex.getMessage());
        }

        options.sort((left, right) -> {
            if (left.isCreateNew()) return -1;
            if (right.isCreateNew()) return 1;
            return left.displayLabel().compareToIgnoreCase(right.displayLabel());
        });
        return options;
    }

    private static void configureParentSelection(
        ComboBox<ExistingParentOption> selector,
        TextField nameTf,
        TextField occupationTf,
        TextField departmentTf,
        TextField phoneTf,
        TextField nationalityTf,
        TextField pagePhoneTf
    ) {
        selector.valueProperty().addListener((obs, oldValue, newValue) -> applyParentSelection(newValue, nameTf, occupationTf, departmentTf, phoneTf, nationalityTf, pagePhoneTf));
        applyParentSelection(selector.getValue(), nameTf, occupationTf, departmentTf, phoneTf, nationalityTf, pagePhoneTf);
    }

    private static void applyParentSelection(
        ExistingParentOption option,
        TextField nameTf,
        TextField occupationTf,
        TextField departmentTf,
        TextField phoneTf,
        TextField nationalityTf,
        TextField pagePhoneTf
    ) {
        if (option == null || option.isCreateNew()) {
            nameTf.clear();
            occupationTf.clear();
            departmentTf.clear();
            phoneTf.clear();
            nationalityTf.clear();

            nameTf.setDisable(false);
            occupationTf.setDisable(false);
            departmentTf.setDisable(false);
            phoneTf.setDisable(false);
            nationalityTf.setDisable(false);
            pagePhoneTf.setDisable(false);
            return;
        }

        nameTf.setText(option.name());
        occupationTf.setText(option.occupation());
        departmentTf.setText(option.department());
        phoneTf.setText(option.phone());
        nationalityTf.setText(option.nationality());

        nameTf.setDisable(true);
        occupationTf.setDisable(true);
        departmentTf.setDisable(true);
        phoneTf.setDisable(true);
        nationalityTf.setDisable(true);
        pagePhoneTf.setDisable(true);
    }

    private static GridPane createFormGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.getStyleClass().add("app-form-grid");
        return grid;
    }

    private static String saveParentIfPresent(
        FirestoreRestClient client,
        String childId,
        String childName,
        ParentDraft draft,
        List<Map<String, Object>> emergencyContacts
    ) throws IOException, InterruptedException {
        if (draft == null) {
            return "";
        }

        if (draft.existingParentId() != null && !draft.existingParentId().isBlank()) {
            return linkExistingParentToChild(client, childId, childName, draft, emergencyContacts);
        }

        String phoneLocal = normalizeRequiredPhone(draft.phone());
        String parentId = newDocId();
        Map<String, Object> document = new HashMap<>();
        document.put("parentName", draft.name());
        document.put("phone", phoneLocal);
        document.put("phoneTail", PhoneUtil.myTail(phoneLocal));
        document.put("phoneE164", PhoneUtil.toE164My(phoneLocal));
        document.put("icNo", "");
        document.put("icVerified", false);
        document.put("occupation", draft.occupation());
        document.put("department", draft.department());
        document.put("nationality", draft.nationality());
        document.put("childIds", List.of(childId));
        document.put("childNames", List.of(childName));
        document.put("childRefs", List.of("/children/" + childId));
        document.put("childId", childId);
        document.put("childName", childName);
        document.put("childRef", "/children/" + childId);
        document.put("relationshipType", draft.relationshipType().firestoreValue);
        document.put("relationshipLabel", null);
        document.put("emergencyContacts", emergencyContacts);
        applyEmergencyContactFlatFields(document, emergencyContacts);

        Map<String, Object> notif = new HashMap<>();
        notif.put("activity", false);
        notif.put("attendance", false);
        notif.put("emergency", false);
        notif.put("fees", true);
        Map<String, Object> settings = new HashMap<>();
        settings.put("notifications", notif);
        document.put("settings", settings);
        document.put("timestamp", new Date());

        client.createDocumentWithId("parents", parentId, document);
        return parentId;
    }

    private static String linkExistingParentToChild(
        FirestoreRestClient client,
        String childId,
        String childName,
        ParentDraft draft,
        List<Map<String, Object>> emergencyContacts
    ) throws IOException, InterruptedException {
        FsDocument existingDoc = client.getDocument("parents", draft.existingParentId());
        if (existingDoc == null) {
            throw new IOException("Selected parent record is no longer available.");
        }

        LinkedHashMap<String, String> childMap = extractLinkedChildren(existingDoc);
        childMap.put(childId, childName);
        List<String> childIds = new ArrayList<>(childMap.keySet());
        List<String> childNames = new ArrayList<>(childMap.values());
        List<String> childRefs = new ArrayList<>();
        for (String linkedChildId : childIds) {
            childRefs.add("/children/" + linkedChildId);
        }

        String phoneLocal = normalizeRequiredPhone(draft.phone());
        Map<String, Object> patch = new HashMap<>();
        patch.put("parentName", draft.name());
        patch.put("phone", phoneLocal);
        patch.put("phoneTail", PhoneUtil.myTail(phoneLocal));
        patch.put("phoneE164", PhoneUtil.toE164My(phoneLocal));
        patch.put("occupation", draft.occupation());
        patch.put("department", draft.department());
        patch.put("nationality", draft.nationality());
        patch.put("childIds", childIds);
        patch.put("childNames", childNames);
        patch.put("childRefs", childRefs);
        if (!childIds.isEmpty()) {
            patch.put("childId", childIds.get(0));
            patch.put("childName", childNames.get(0));
            patch.put("childRef", childRefs.get(0));
        }
        if (emergencyContacts != null && !emergencyContacts.isEmpty()) {
            patch.put("emergencyContacts", emergencyContacts);
            applyEmergencyContactFlatFields(patch, emergencyContacts);
        }

        client.patchDocumentMerge("parents", draft.existingParentId(), patch);
        return draft.existingParentId();
    }

    private static void addUniqueParentId(List<String> parentIds, String parentId) {
        if (parentIds == null) {
            return;
        }
        String normalized = safeText(parentId);
        if (normalized.isBlank() || parentIds.contains(normalized)) {
            return;
        }
        parentIds.add(normalized);
    }

    private static void issueRegistrationInvoices(List<String> parentIds, LocalDate registrationDate) {
        if (parentIds == null || parentIds.isEmpty()) {
            return;
        }

        LocalDate effectiveDate = registrationDate == null ? LocalDate.now() : registrationDate;
        String period = String.format("%04d-%02d", effectiveDate.getYear(), effectiveDate.getMonthValue());

        try {
            Map<?, ?> result = BillingLedgerRemoteSupport.generateInvoicesForPeriod(period, parentIds);
            BillingLedgerRemoteSupport.assertInvoiceBatchSucceeded(result);
        } catch (RuntimeException ex) {
            AppThemeSupport.showWarning(
                null,
                "Invoice Not Generated Yet",
                "The child and parent records were saved, but the registration invoice could not be issued immediately.\n\n"
                    + BillingLedgerMessageSupport.rootMessage(ex)
            );
        }
    }

    private static LinkedHashMap<String, String> extractLinkedChildren(FsDocument parentDoc) {
        LinkedHashMap<String, String> childMap = new LinkedHashMap<>();
        List<String> childIds = extractStringList(parentDoc.get("childIds"));
        List<String> childNames = extractStringList(parentDoc.get("childNames"));
        for (int index = 0; index < childIds.size(); index++) {
            String childId = childIds.get(index);
            if (childId.isBlank()) {
                continue;
            }
            String name = index < childNames.size() && !childNames.get(index).isBlank() ? childNames.get(index) : childId;
            childMap.put(childId, name);
        }

        if (childMap.isEmpty()) {
            String legacyChildId = safeText(parentDoc.getString("childId"));
            String legacyChildName = safeText(parentDoc.getString("childName"));
            if (!legacyChildId.isBlank()) {
                childMap.put(legacyChildId, legacyChildName.isBlank() ? legacyChildId : legacyChildName);
            }
        }
        return childMap;
    }

    private static List<String> extractStringList(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List<?>) {
            for (Object value : (List<?>) raw) {
                String text = safeText(value);
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
        }
        return values;
    }

    private static List<Map<String, Object>> buildEmergencyContacts(
        TextField emergency1NameTf,
        TextArea emergency1AddressTa,
        TextField emergency1PhoneTf,
        TextField emergency1OfficePhoneTf,
        TextField emergency2NameTf,
        TextArea emergency2AddressTa,
        TextField emergency2PhoneTf,
        TextField emergency2OfficePhoneTf
    ) {
        List<Map<String, Object>> contacts = new ArrayList<>();
        Map<String, Object> first = buildEmergencyContact(emergency1NameTf, emergency1AddressTa, emergency1PhoneTf, emergency1OfficePhoneTf);
        if (first != null) {
            first.put("slot", 1);
            contacts.add(first);
        }
        Map<String, Object> second = buildEmergencyContact(emergency2NameTf, emergency2AddressTa, emergency2PhoneTf, emergency2OfficePhoneTf);
        if (second != null) {
            second.put("slot", 2);
            contacts.add(second);
        }
        return contacts;
    }

    private static Map<String, Object> buildEmergencyContact(
        TextField nameTf,
        TextArea addressTa,
        TextField phoneTf,
        TextField officePhoneTf
    ) {
        String name = safeText(nameTf.getText());
        String address = safeText(addressTa.getText());
        String phone = normalizeOptionalPhone(phoneTf.getText());
        String officePhone = normalizeOptionalPhone(officePhoneTf.getText());
        if (name.isEmpty() && address.isEmpty() && phone.isEmpty() && officePhone.isEmpty()) {
            return null;
        }

        Map<String, Object> contact = new HashMap<>();
        contact.put("name", name);
        contact.put("address", address);
        contact.put("phone", phone);
        contact.put("officePhone", officePhone);
        return contact;
    }

    private static void applyEmergencyContactFlatFields(Map<String, Object> document, List<Map<String, Object>> emergencyContacts) {
        for (int index = 0; index < 2; index++) {
            int slot = index + 1;
            Map<String, Object> contact = index < emergencyContacts.size() ? emergencyContacts.get(index) : null;
            document.put("emergencyContact" + slot + "Name", safeText(contact == null ? "" : contact.get("name")));
            document.put("emergencyContact" + slot + "Address", safeText(contact == null ? "" : contact.get("address")));
            document.put("emergencyContact" + slot + "Phone", safeText(contact == null ? "" : contact.get("phone")));
            document.put("emergencyContact" + slot + "OfficePhone", safeText(contact == null ? "" : contact.get("officePhone")));
        }
    }

    private static String normalizeRequiredPhone(String value) {
        String cleaned = normalizeOptionalPhone(value);
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Each parent section that is used must include a contact number.");
        }
        return cleaned;
    }

    private static String normalizeOptionalPhone(String value) {
        String cleaned = safeText(value);
        if (cleaned.isEmpty()) {
            return "";
        }
        String local = PhoneUtil.toLocalMy(cleaned);
        return local == null || local.isBlank() ? cleaned : local;
    }

    private static String safeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String newDocId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private static String latestBridgeFingerprint(FsDocument latestScan) {
        if (latestScan == null) {
            return "";
        }

        Date scannedAt = latestScan.getDate("scannedAt");
        String uid = safeText(latestScan.getString("uid")).toUpperCase();
        if (scannedAt == null || uid.isEmpty()) {
            return "";
        }
        return scannedAt.getTime() + ":" + uid;
    }

    private static final class ParentDraft {
        private final CRUDParentDialogSupport.RelationshipType relationshipType;
        private final String existingParentId;
        private final String name;
        private final String phone;
        private final String occupation;
        private final String department;
        private final String nationality;

        private ParentDraft(
            CRUDParentDialogSupport.RelationshipType relationshipType,
            String existingParentId,
            String name,
            String phone,
            String occupation,
            String department,
            String nationality
        ) {
            this.relationshipType = relationshipType;
            this.existingParentId = existingParentId;
            this.name = name;
            this.phone = phone;
            this.occupation = occupation;
            this.department = department;
            this.nationality = nationality;
        }

        static ParentDraft fromSelection(
            CRUDParentDialogSupport.RelationshipType relationshipType,
            ExistingParentOption option,
            String name,
            String phone,
            String occupation,
            String department,
            String nationality
        ) {
            if (option != null && !option.isCreateNew()) {
                return new ParentDraft(
                    relationshipType,
                    option.parentId(),
                    option.name(),
                    option.phone(),
                    option.occupation(),
                    option.department(),
                    option.nationality()
                );
            }

            String cleanName = safeText(name);
            String cleanPhone = safeText(phone);
            String cleanOccupation = safeText(occupation);
            String cleanDepartment = safeText(department);
            String cleanNationality = safeText(nationality);
            if (cleanName.isEmpty() && cleanPhone.isEmpty() && cleanOccupation.isEmpty() && cleanDepartment.isEmpty() && cleanNationality.isEmpty()) {
                return null;
            }
            if (cleanName.isEmpty()) {
                throw new IllegalArgumentException("Each parent section that is used must include a name.");
            }
            if (cleanPhone.isEmpty()) {
                throw new IllegalArgumentException("Each parent section that is used must include a contact number.");
            }
            return new ParentDraft(relationshipType, "", cleanName, cleanPhone, cleanOccupation, cleanDepartment, cleanNationality);
        }

        CRUDParentDialogSupport.RelationshipType relationshipType() { return relationshipType; }
        String existingParentId() { return existingParentId; }
        String name() { return name; }
        String phone() { return phone; }
        String occupation() { return occupation; }
        String department() { return department; }
        String nationality() { return nationality; }
    }

    private static final class ExistingParentOption {
        private final String parentId;
        private final CRUDParentDialogSupport.RelationshipType relationshipType;
        private final String name;
        private final String phone;
        private final String occupation;
        private final String department;
        private final String nationality;
        private final boolean createNew;

        private ExistingParentOption(
            String parentId,
            CRUDParentDialogSupport.RelationshipType relationshipType,
            String name,
            String phone,
            String occupation,
            String department,
            String nationality,
            boolean createNew
        ) {
            this.parentId = parentId;
            this.relationshipType = relationshipType;
            this.name = name;
            this.phone = phone;
            this.occupation = occupation;
            this.department = department;
            this.nationality = nationality;
            this.createNew = createNew;
        }

        static ExistingParentOption createNew(CRUDParentDialogSupport.RelationshipType relationshipType) {
            return new ExistingParentOption("", relationshipType, "", "", "", "", "", true);
        }

        static ExistingParentOption fromDocument(FsDocument parentDoc, CRUDParentDialogSupport.RelationshipType relationshipType) {
            return new ExistingParentOption(
                safeText(parentDoc.getId()),
                relationshipType,
                safeText(parentDoc.get("parentName")),
                safeText(parentDoc.get("phone")),
                safeText(parentDoc.get("occupation")),
                safeText(parentDoc.get("department")),
                safeText(parentDoc.get("nationality")),
                false
            );
        }

        boolean isCreateNew() { return createNew; }
        String parentId() { return parentId; }
        String name() { return name; }
        String phone() { return phone; }
        String occupation() { return occupation; }
        String department() { return department; }
        String nationality() { return nationality; }
        String displayLabel() {
            if (createNew) {
                return "Create New " + relationshipType.display;
            }
            return name.isBlank()
                ? parentId + (phone.isBlank() ? "" : " (" + phone + ")")
                : name + (phone.isBlank() ? "" : " (" + phone + ")");
        }

        @Override
        public String toString() {
            return displayLabel();
        }
    }
}