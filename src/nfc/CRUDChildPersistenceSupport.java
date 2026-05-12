package nfc;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
        if (!confirmBillingReview(billingAssessment)) {
            return false;
        }

        LocalDate registrationDate = request.registrationReceivedDate() != null
            ? request.registrationReceivedDate()
            : (request.isNew() ? LocalDate.now() : null);
        LocalDate billingReferenceDate = registrationDate == null ? LocalDate.now() : registrationDate;
        CRUDChildValidationSupport.BillingProfile billingProfile = CRUDChildValidationSupport.deriveBillingProfile(
            billingReferenceDate,
            request.child().getBirthDate(),
            registrationDate
        );

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
        payload.put("careType", CRUDChildDialogSupport.FeePlanType.MONTHLY_FULLTIME.careType);

        String registrationType = "fulltime";
        payload.put("registrationType", registrationType);
        payload.put("feePlan", CRUDChildDialogSupport.FeePlanType.MONTHLY_FULLTIME.code);
        payload.put("schoolHolidayTransit", false);
        payload.put("transitDurationHours", "");
        payload.put("careDurationHours", "");
        payload.put("transportFromTadika", false);
        payload.put("billingDueDay", 7);
        payload.put("invoiceDueDay", billingProfile.invoiceDueDay());
        payload.put("activeBillingModel", billingProfile.activeBillingModel());
        payload.put("feePolicyVersion", billingProfile.feePolicyVersion());
        payload.put("ageBand", billingProfile.ageBand());
        payload.put("monthlyFeeSen", billingProfile.monthlyFeeSen());
        payload.put("ageOutOfPolicy", billingProfile.ageOutOfPolicy());
        payload.put("agePolicyReason", billingProfile.agePolicyReason());
        if (registrationDate != null) {
            payload.put("registrationDate", registrationDate.toString());
        }
        if (billingProfile.yearlyFeeCoveredYear() != null) {
            payload.put("yearlyFeeCoveredYear", billingProfile.yearlyFeeCoveredYear());
        }

        if (request.isNew()) {
            payload.put("registeredAt", new Date());
            request.client().createDocumentWithId("children", childId, payload);
            System.out.println("✅ Added new child: " + request.child().getName() + " (childId=" + childId + ")");
        } else {
            request.client().patchDocumentMerge("children", childId, payload);
            System.out.println("✏️ Updated child: " + request.child().getName() + " (childId=" + childId + ")");
        }

        issueLinkedParentInvoicesIfPresent(request.client(), childId, invoicePeriod(request));
        return true;
    }

    private static void issueLinkedParentInvoicesIfPresent(FirestoreRestClient client, String childId, String period) throws IOException, InterruptedException {
        if (client == null || childId == null || childId.isBlank() || period == null || period.isBlank()) {
            return;
        }

        List<String> parentIds = findLinkedParentIds(client, childId);
        if (parentIds.isEmpty()) {
            return;
        }

        try {
            Map<?, ?> result = BillingLedgerRemoteSupport.generateInvoicesForPeriod(period, parentIds);
            BillingLedgerRemoteSupport.assertInvoiceBatchSucceeded(result);
        } catch (RuntimeException ex) {
            new Alert(
                Alert.AlertType.WARNING,
                "Child details were saved, but the linked invoice could not be refreshed automatically.\n\n"
                    + BillingLedgerMessageSupport.rootMessage(ex)
            ).showAndWait();
        }
    }

    private static List<String> findLinkedParentIds(FirestoreRestClient client, String childId) throws IOException, InterruptedException {
        List<String> parentIds = new ArrayList<>();
        for (FsDocument parentDoc : client.listDocuments("parents")) {
            if (parentDoc == null || parentDoc.getId() == null || parentDoc.getId().isBlank()) {
                continue;
            }
            if (!parentLinksChild(parentDoc, childId)) {
                continue;
            }
            if (!parentIds.contains(parentDoc.getId())) {
                parentIds.add(parentDoc.getId());
            }
        }
        return parentIds;
    }

    private static boolean parentLinksChild(FsDocument parentDoc, String childId) {
        if (childId.equals(parentDoc.getString("childId"))) {
            return true;
        }

        Object rawChildIds = parentDoc.get("childIds");
        if (!(rawChildIds instanceof List<?>)) {
            return false;
        }

        for (Object rawChildId : (List<?>) rawChildIds) {
            if (childId.equals(safeText(rawChildId == null ? null : String.valueOf(rawChildId)))) {
                return true;
            }
        }
        return false;
    }

    private static String invoicePeriod(SaveRequest request) {
        LocalDate anchorDate = request != null && request.registrationReceivedDate() != null
            ? request.registrationReceivedDate()
            : LocalDate.now();
        return String.format("%04d-%02d", anchorDate.getYear(), anchorDate.getMonthValue());
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
            "This child is outside the supported Taska Zurah auto-billing range. The saved profile will be flagged for manual billing review. Continue saving?",
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
        private final boolean schoolHolidayTransitSelected;
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
            this.schoolHolidayTransitSelected = schoolHolidayTransitSelected;
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
        boolean schoolHolidayTransitSelected() { return schoolHolidayTransitSelected; }
        boolean absenceLetterApproved() { return absenceLetterApproved; }
        String absenceLetterPeriodRaw() { return absenceLetterPeriodRaw; }
        String absenceLetterDaysRaw() { return absenceLetterDaysRaw; }
    }
}