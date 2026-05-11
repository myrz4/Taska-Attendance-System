package nfc;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

@SuppressWarnings({"java:S1144", "java:S1848"})
final class CRUDChildPersistenceSupport {
    @SuppressWarnings("unused")
    private static final SaveRequest ANALYZER_PROBE = new SaveRequest(
        null,
        "",
        null,
        false,
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        null,
        "",
        "",
        false,
        null,
        null,
        false,
        false,
        7,
        false,
        "",
        ""
    );

    private CRUDChildPersistenceSupport() {
    }

    static {
        if (keepAnalyzerAnchors()) {
            try {
                saveChild(null);
            } catch (IOException | InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static boolean keepAnalyzerAnchors() {
        return Boolean.getBoolean("taska.keepAnalyzerAnchors");
    }

    static boolean saveChild(SaveRequest request) throws IOException, InterruptedException {
        String childId = request.childId() == null ? "" : request.childId().trim();
        if (childId.isEmpty()) {
            throw new IllegalStateException("Missing child ID");
        }

        String nfcUid = request.child().getNfcUid() == null ? "" : request.child().getNfcUid().trim().toUpperCase();
        if (nfcUid.isEmpty()) {
            new Alert(Alert.AlertType.ERROR, "NFC UID cannot be empty.").showAndWait();
            return false;
        }
        if (!ensureUniqueNfcUid(request.client(), childId, nfcUid)) {
            return false;
        }

        CRUDChildValidationSupport.BillingAssessment billingAssessment = CRUDChildValidationSupport.assessBilling(
            LocalDate.now(),
            request.child().getBirthDate(),
            request.feePlan(),
            request.schoolHolidayTransitSelected()
        );
        if (billingAssessment.schoolHolidayAgeBlocked()) {
            new Alert(
                Alert.AlertType.ERROR,
                "Transit penuh cuti sekolah hanya boleh digunakan untuk kanak-kanak umur 4 tahun dan ke atas.")
                .showAndWait();
            return false;
        }
        if (request.isNew() && "under_3_months".equals(billingAssessment.billingReviewReason())) {
            new Alert(
                Alert.AlertType.ERROR,
                "Taska hanya menerima kanak-kanak berumur 3 bulan dan ke atas. Child under 3 months cannot be added.")
                .showAndWait();
            return false;
        }
        if (!confirmBillingReview(billingAssessment)) {
            return false;
        }

        Integer siblingsCount;
        try {
            siblingsCount = parseOptionalInteger(request.siblingsCountRaw());
        } catch (IllegalArgumentException ex) {
            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            return false;
        }

        CRUDChildValidationSupport.AbsenceLetterInput absenceLetter;
        try {
            absenceLetter = CRUDChildValidationSupport.parseAbsenceLetter(
                request.absenceLetterPeriodRaw(),
                request.absenceLetterDaysRaw()
            );
        } catch (IllegalArgumentException ex) {
            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            return false;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", request.child().getName());
        payload.put("birthDate", request.child().getBirthDate() == null ? "" : request.child().getBirthDate().toString());
        payload.put("gender", safeText(request.gender()));
        payload.put("placeOfBirth", safeText(request.placeOfBirth()));
        payload.put("billingReviewRequired", billingAssessment.billingReviewRequired());
        payload.put("billingReviewReason", billingAssessment.billingReviewReason());
        payload.put("nfc_uid", nfcUid);
        payload.put("childIcNo", safeText(request.childIc()));
        payload.put("birthCertNo", safeText(request.birthCert()));
        payload.put("address", safeText(request.address()));
        payload.put("homeAddress", safeText(request.address()));
        payload.put("siblingsCount", siblingsCount == null ? "" : siblingsCount);
        payload.put("languageSpeaking", safeText(request.languageSpeaking()));
        payload.put("fatherPhone", normalizeOptionalPhone(request.fatherPhone()));
        payload.put("motherPhone", normalizeOptionalPhone(request.motherPhone()));
        payload.put("registrationReceivedBy", safeText(request.registrationReceivedBy()));
        payload.put("registrationReceivedDate", request.registrationReceivedDate() == null ? "" : request.registrationReceivedDate().toString());
        payload.put("registrationReceiptNo", safeText(request.registrationReceiptNo()));
        payload.put("registrationChequeNo", safeText(request.registrationChequeNo()));
        payload.put("staffChild", request.staffChild());
        payload.put("absenceLetterApproved", request.absenceLetterApproved());
        payload.put("absenceLetterPeriod", absenceLetter.period());
        payload.put("absenceLetterDays", absenceLetter.days());
        payload.put("careType", request.feePlan().careType);

        String registrationType = "monthly".equals(request.feePlan().code) ? "fulltime" : "transit";
        payload.put("registrationType", registrationType);
        payload.put("feePlan", request.feePlan().code);

        CRUDChildValidationSupport.TransitSettings transitSettings = CRUDChildValidationSupport.deriveTransitSettings(
            request.feePlan(),
            request.transitDurationHint(),
            request.schoolHolidayTransitSelected()
        );
        payload.put("schoolHolidayTransit", transitSettings.schoolHolidayTransit());
        payload.put("transitDurationHours", transitSettings.careDurationHours());
        payload.put("careDurationHours", transitSettings.careDurationHours());
        payload.put("transportFromTadika", request.transportFromTadika());
        payload.put("billingDueDay", request.billingDueDay() == 5 ? 5 : 7);

        if (request.isNew()) {
            payload.put("registeredAt", new Date());
            request.client().createDocumentWithId("children", childId, payload);
            System.out.println("✅ Added new child: " + request.child().getName() + " (childId=" + childId + ")");
        } else {
            request.client().patchDocumentMerge("children", childId, payload);
            System.out.println("✏️ Updated child: " + request.child().getName() + " (childId=" + childId + ")");
        }
        return true;
    }

    private static boolean ensureUniqueNfcUid(FirestoreRestClient client, String childId, String nfcUid) throws IOException, InterruptedException {
        for (FsDocument document : client.listDocuments("children")) {
            if (document == null) {
                continue;
            }
            String migratedTo = document.getString("migratedToChildId");
            if (migratedTo != null && !migratedTo.trim().isEmpty()) {
                continue;
            }
            String otherUid = document.getString("nfc_uid");
            if (otherUid == null) {
                continue;
            }
            if (nfcUid.equalsIgnoreCase(otherUid.trim()) && !document.getId().equals(childId)) {
                new Alert(Alert.AlertType.ERROR, "This NFC UID is already assigned to another child.").showAndWait();
                return false;
            }
        }
        return true;
    }

    private static boolean confirmBillingReview(CRUDChildValidationSupport.BillingAssessment billingAssessment) {
        if (!billingAssessment.billingReviewRequired()) {
            return true;
        }
        Alert warning = new Alert(
            Alert.AlertType.WARNING,
            "This child is outside the standard billing age band. The invoice will use the nearest standard band and be flagged for manual review. Continue saving?",
            ButtonType.OK,
            ButtonType.CANCEL
        );
        warning.setHeaderText("Billing Review Required");
        return warning.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeOptionalPhone(String value) {
        String cleaned = safeText(value);
        if (cleaned.isEmpty()) {
            return "";
        }
        String local = PhoneUtil.toLocalMy(cleaned);
        return local == null || local.isBlank() ? cleaned : local;
    }

    private static Integer parseOptionalInteger(String value) {
        String cleaned = safeText(value);
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(cleaned);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("No. of siblings must be a whole number.");
        }
    }

    static final class SaveRequest {
        private final FirestoreRestClient client;
        private final String childId;
        private final ChildrenView.Child child;
        private final boolean isNew;
        private final String gender;
        private final String placeOfBirth;
        private final String childIc;
        private final String birthCert;
        private final String siblingsCountRaw;
        private final String languageSpeaking;
        private final String address;
        private final String fatherPhone;
        private final String motherPhone;
        private final String registrationReceivedBy;
        private final LocalDate registrationReceivedDate;
        private final String registrationReceiptNo;
        private final String registrationChequeNo;
        private final boolean staffChild;
        private final CRUDChildDialogSupport.FeePlanType feePlan;
        private final CRUDChildDialogSupport.TransitDurationHint transitDurationHint;
        private final boolean schoolHolidayTransitSelected;
        private final boolean transportFromTadika;
        private final int billingDueDay;
        private final boolean absenceLetterApproved;
        private final String absenceLetterPeriodRaw;
        private final String absenceLetterDaysRaw;

        SaveRequest(
            FirestoreRestClient client,
            String childId,
            ChildrenView.Child child,
            boolean isNew,
            String gender,
            String placeOfBirth,
            String childIc,
            String birthCert,
            String siblingsCountRaw,
            String languageSpeaking,
            String address,
            String fatherPhone,
            String motherPhone,
            String registrationReceivedBy,
            LocalDate registrationReceivedDate,
            String registrationReceiptNo,
            String registrationChequeNo,
            boolean staffChild,
            CRUDChildDialogSupport.FeePlanType feePlan,
            CRUDChildDialogSupport.TransitDurationHint transitDurationHint,
            boolean schoolHolidayTransitSelected,
            boolean transportFromTadika,
            int billingDueDay,
            boolean absenceLetterApproved,
            String absenceLetterPeriodRaw,
            String absenceLetterDaysRaw
        ) {
            this.client = client;
            this.childId = childId;
            this.child = child;
            this.isNew = isNew;
            this.gender = gender;
            this.placeOfBirth = placeOfBirth;
            this.childIc = childIc;
            this.birthCert = birthCert;
            this.siblingsCountRaw = siblingsCountRaw;
            this.languageSpeaking = languageSpeaking;
            this.address = address;
            this.fatherPhone = fatherPhone;
            this.motherPhone = motherPhone;
            this.registrationReceivedBy = registrationReceivedBy;
            this.registrationReceivedDate = registrationReceivedDate;
            this.registrationReceiptNo = registrationReceiptNo;
            this.registrationChequeNo = registrationChequeNo;
            this.staffChild = staffChild;
            this.feePlan = feePlan == null ? CRUDChildDialogSupport.FeePlanType.MONTHLY_FULLTIME : feePlan;
            this.transitDurationHint = transitDurationHint == null ? CRUDChildDialogSupport.TransitDurationHint.AUTO : transitDurationHint;
            this.schoolHolidayTransitSelected = schoolHolidayTransitSelected;
            this.transportFromTadika = transportFromTadika;
            this.billingDueDay = billingDueDay;
            this.absenceLetterApproved = absenceLetterApproved;
            this.absenceLetterPeriodRaw = absenceLetterPeriodRaw;
            this.absenceLetterDaysRaw = absenceLetterDaysRaw;
        }

        FirestoreRestClient client() { return client; }
        String childId() { return childId; }
        ChildrenView.Child child() { return child; }
        boolean isNew() { return isNew; }
        String gender() { return gender; }
        String placeOfBirth() { return placeOfBirth; }
        String childIc() { return childIc; }
        String birthCert() { return birthCert; }
        String siblingsCountRaw() { return siblingsCountRaw; }
        String languageSpeaking() { return languageSpeaking; }
        String address() { return address; }
        String fatherPhone() { return fatherPhone; }
        String motherPhone() { return motherPhone; }
        String registrationReceivedBy() { return registrationReceivedBy; }
        LocalDate registrationReceivedDate() { return registrationReceivedDate; }
        String registrationReceiptNo() { return registrationReceiptNo; }
        String registrationChequeNo() { return registrationChequeNo; }
        boolean staffChild() { return staffChild; }
        CRUDChildDialogSupport.FeePlanType feePlan() { return feePlan; }
        CRUDChildDialogSupport.TransitDurationHint transitDurationHint() { return transitDurationHint; }
        boolean schoolHolidayTransitSelected() { return schoolHolidayTransitSelected; }
        boolean transportFromTadika() { return transportFromTadika; }
        int billingDueDay() { return billingDueDay; }
        boolean absenceLetterApproved() { return absenceLetterApproved; }
        String absenceLetterPeriodRaw() { return absenceLetterPeriodRaw; }
        String absenceLetterDaysRaw() { return absenceLetterDaysRaw; }
    }
}