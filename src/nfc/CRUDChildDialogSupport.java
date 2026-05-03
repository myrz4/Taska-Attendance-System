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
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
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
        dialog.setTitle(isNew ? "Add New Child" : "Edit Child");

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
            new Alert(Alert.AlertType.ERROR, "Firestore REST Error: " + e.getMessage()).showAndWait();
            return;
        }

        TextField nameTf = new TextField();
        DatePicker dobPicker = new DatePicker();
        TextField uidTf = new TextField();
        uidTf.setPromptText("Scan/write NFC UID (replaceable)");
        Label uidHint = new Label(UID_HINT_TEXT);
        uidHint.setWrapText(true);
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
        Label billingHint = new Label("Full-time monthly fees are age-based. Daily, weekly, and hourly transit plans are billed from actual attendance records.");
        billingHint.setWrapText(true);

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
            String childIc = safeStr(existingData.get("childIcNo")).trim();
            if (childIc.isEmpty()) {
                childIc = safeStr(existingData.get("icNo")).trim();
            }
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
        }

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

        VBox content = new VBox(10,
            new Label("Child Name:"), nameTf,
            new Label("Birth Date:"), dobPicker,
            new Label("NFC UID:"), uidTf,
            uidHint,
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
                    childIcTf.getText(),
                    birthCertTf.getText(),
                    addressTa.getText(),
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
                new Alert(Alert.AlertType.ERROR, "Firestore REST Error: " + ex.getMessage()).showAndWait();
            }
        });
    }

    private static String safeStr(Object value) {
        return value == null ? "" : String.valueOf(value);
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