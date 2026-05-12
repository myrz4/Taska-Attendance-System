package nfc;

import java.time.LocalDate;
import java.time.Period;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

final class CRUDChildValidationSupport {
    private static final String POLICY_VERSION = "TASKA_ZURAH_2026";
    private static final String ACTIVE_BILLING_MODEL = "TASKA_ZURAH_AGE_BASED";
    private static final int SUPPORTED_AGE_MAX_MONTHS_EXCLUSIVE = 60;
    private static final int FIXED_INVOICE_DUE_DAY = 7;
    private static final int INVOICE_GENERATION_DAY = 21;
    private static final int REGISTRATION_FEE_SEN = 10_000;
    private static final int INSURANCE_TAKAFUL_SEN = 1_500;
    private static final int YEARLY_MAINTENANCE_FEE_SEN = 40_000;

    private CRUDChildValidationSupport() {
    }

    static {
        if (keepAnalyzerAnchors()) {
            syncTransitControls(null, null, null, null);
            assessBilling(LocalDate.now(), null, null, false);
            deriveBillingProfile(LocalDate.now(), null, null);
            deriveRegistrationPreview(LocalDate.now(), null, null);
            parseAbsenceLetter("", "");
            deriveTransitSettings(null, null, false);
            BillingAssessment billingProbe = new BillingAssessment(null, false, false, "");
            billingProbe.childAgeMonths();
            billingProbe.schoolHolidayAgeBlocked();
            billingProbe.billingReviewRequired();
            billingProbe.billingReviewReason();
            BillingProfile profileProbe = new BillingProfile("", 0, false, "", "", "", 7, null);
            profileProbe.ageBand();
            profileProbe.monthlyFeeSen();
            profileProbe.ageOutOfPolicy();
            profileProbe.agePolicyReason();
            profileProbe.feePolicyVersion();
            profileProbe.activeBillingModel();
            profileProbe.invoiceDueDay();
            profileProbe.yearlyFeeCoveredYear();
            RegistrationPreview registrationProbe = new RegistrationPreview(profileProbe, 0, 0, 0, 0, 21);
            registrationProbe.billingProfile();
            registrationProbe.registrationFeeSen();
            registrationProbe.insuranceTakafulSen();
            registrationProbe.yearlyMaintenanceFeeSen();
            registrationProbe.registrationTotalSen();
            registrationProbe.invoiceGenerationDay();
            AbsenceLetterInput absenceProbe = new AbsenceLetterInput("", 0);
            absenceProbe.period();
            absenceProbe.days();
            TransitSettings transitProbe = new TransitSettings(false, null);
            transitProbe.schoolHolidayTransit();
            transitProbe.careDurationHours();
        }
    }

    private static boolean keepAnalyzerAnchors() {
        return Boolean.getBoolean("taska.keepAnalyzerAnchors");
    }

    static void syncTransitControls(
        CRUDChildDialogSupport.FeePlanType selected,
        ComboBox<CRUDChildDialogSupport.TransitDurationHint> transitDurationHintCb,
        CheckBox schoolHolidayTransitCb,
        Label transitHint
    ) {
        CRUDChildDialogSupport.FeePlanType feePlan = selected == null
            ? CRUDChildDialogSupport.FeePlanType.MONTHLY_FULLTIME
            : selected;
        boolean autoMonthlyTransit = feePlan == CRUDChildDialogSupport.FeePlanType.TRANSIT_AUTO_MONTHLY;
        transitDurationHintCb.setDisable(!autoMonthlyTransit);
        schoolHolidayTransitCb.setDisable(!autoMonthlyTransit);
        transitHint.setDisable(!autoMonthlyTransit);
        transitHint.setVisible(autoMonthlyTransit);
        transitHint.setManaged(autoMonthlyTransit);
        if (!autoMonthlyTransit) {
            transitDurationHintCb.setValue(CRUDChildDialogSupport.TransitDurationHint.AUTO);
            schoolHolidayTransitCb.setSelected(false);
        }
    }

    static BillingAssessment assessBilling(
        LocalDate today,
        LocalDate birthDate,
        CRUDChildDialogSupport.FeePlanType feePlan,
        boolean schoolHolidayTransitSelected
    ) {
        Integer childAgeMonths = ageInMonths(today, birthDate);
        boolean billingReviewRequired = childAgeMonths == null || childAgeMonths >= SUPPORTED_AGE_MAX_MONTHS_EXCLUSIVE;
        String reviewReason = "";
        if (childAgeMonths == null) {
            reviewReason = "missing_birth_date";
        } else if (childAgeMonths >= SUPPORTED_AGE_MAX_MONTHS_EXCLUSIVE) {
            reviewReason = "age_5_or_above";
        }
        return new BillingAssessment(childAgeMonths, false, billingReviewRequired, reviewReason);
    }

    static BillingProfile deriveBillingProfile(LocalDate referenceDate, LocalDate birthDate, LocalDate registrationDate) {
        Integer childAgeMonths = ageInMonths(referenceDate == null ? LocalDate.now() : referenceDate, birthDate);
        String ageBand = determineAgeBand(childAgeMonths);
        int monthlyFeeSen = monthlyFeeSenForBand(ageBand);
        boolean ageOutOfPolicy = childAgeMonths == null || childAgeMonths >= SUPPORTED_AGE_MAX_MONTHS_EXCLUSIVE;
        String agePolicyReason;
        if (childAgeMonths == null) {
            agePolicyReason = "missing_birth_date";
        } else if (childAgeMonths >= SUPPORTED_AGE_MAX_MONTHS_EXCLUSIVE) {
            agePolicyReason = "age_5_or_above";
        } else {
            agePolicyReason = "in_range";
        }

        return new BillingProfile(
            ageBand,
            monthlyFeeSen,
            ageOutOfPolicy,
            agePolicyReason,
            POLICY_VERSION,
            ACTIVE_BILLING_MODEL,
            FIXED_INVOICE_DUE_DAY,
            determineYearlyFeeCoveredYear(registrationDate)
        );
    }

    static RegistrationPreview deriveRegistrationPreview(LocalDate referenceDate, LocalDate birthDate, LocalDate registrationDate) {
        BillingProfile billingProfile = deriveBillingProfile(referenceDate, birthDate, registrationDate);
        int totalSen = billingProfile.monthlyFeeSen() + REGISTRATION_FEE_SEN + INSURANCE_TAKAFUL_SEN + YEARLY_MAINTENANCE_FEE_SEN;
        return new RegistrationPreview(
            billingProfile,
            REGISTRATION_FEE_SEN,
            INSURANCE_TAKAFUL_SEN,
            YEARLY_MAINTENANCE_FEE_SEN,
            totalSen,
            INVOICE_GENERATION_DAY
        );
    }

    static String describeAgeBand(String ageBand) {
        if ("BABY_TO_2".equals(ageBand)) {
            return "Baby to below 2 years";
        }
        if ("AGE_2_TO_3".equals(ageBand)) {
            return "2 years to below 4 years";
        }
        return "4 years to below 5 years";
    }

    static AbsenceLetterInput parseAbsenceLetter(String periodRaw, String daysRaw) {
        String period = safeStr(periodRaw).trim();
        String daysText = safeStr(daysRaw).trim();
        int days = 0;
        if (!daysText.isEmpty()) {
            try {
                days = Math.max(0, Integer.parseInt(daysText));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Absence days must be a whole number.", ex);
            }
        }
        if (!period.isEmpty() && !period.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("Approved absence letter period must be in yyyy-MM format.");
        }
        return new AbsenceLetterInput(period, days);
    }

    static TransitSettings deriveTransitSettings(
        CRUDChildDialogSupport.FeePlanType feePlan,
        CRUDChildDialogSupport.TransitDurationHint transitDurationHint,
        boolean schoolHolidayTransitSelected
    ) {
        CRUDChildDialogSupport.FeePlanType selectedFeePlan = feePlan == null
            ? CRUDChildDialogSupport.FeePlanType.MONTHLY_FULLTIME
            : feePlan;
        CRUDChildDialogSupport.TransitDurationHint selectedHint = transitDurationHint == null
            ? CRUDChildDialogSupport.TransitDurationHint.AUTO
            : transitDurationHint;
        boolean schoolHolidayTransit = false;
        Object careDurationHours = null;

        switch (selectedFeePlan) {
            case TRANSIT_AUTO_MONTHLY:
                schoolHolidayTransit = schoolHolidayTransitSelected;
                if (selectedHint.durationHours != null) {
                    careDurationHours = selectedHint.durationHours;
                }
                break;
            case TRANSIT_2H_MONTH:
                careDurationHours = 2.0d;
                break;
            case TRANSIT_HALFDAY_MONTH:
                careDurationHours = 4.0d;
                break;
            case TRANSIT_SCHOOLHOLIDAY_MONTH:
                schoolHolidayTransit = true;
                break;
            default:
                break;
        }
        return new TransitSettings(schoolHolidayTransit, careDurationHours);
    }

    private static String safeStr(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Integer ageInMonths(LocalDate at, LocalDate birthDate) {
        if (at == null || birthDate == null) {
            return null;
        }
        if (birthDate.isAfter(at)) {
            return 0;
        }
        Period age = Period.between(birthDate, at);
        return age.getYears() * 12 + age.getMonths();
    }

    private static String determineAgeBand(Integer childAgeMonths) {
        if (childAgeMonths == null) {
            return "AGE_4";
        }
        if (childAgeMonths < 24) {
            return "BABY_TO_2";
        }
        if (childAgeMonths < 48) {
            return "AGE_2_TO_3";
        }
        return "AGE_4";
    }

    private static int monthlyFeeSenForBand(String ageBand) {
        if ("BABY_TO_2".equals(ageBand)) {
            return 75_000;
        }
        if ("AGE_2_TO_3".equals(ageBand)) {
            return 70_000;
        }
        return 65_000;
    }

    private static Integer determineYearlyFeeCoveredYear(LocalDate registrationDate) {
        if (registrationDate == null) {
            return null;
        }
        int month = registrationDate.getMonthValue();
        int year = registrationDate.getYear();
        return month >= 11 ? year + 1 : year;
    }

    static final class BillingAssessment {
        private final Integer childAgeMonths;
        private final boolean schoolHolidayAgeBlocked;
        private final boolean billingReviewRequired;
        private final String billingReviewReason;

        BillingAssessment(Integer childAgeMonths, boolean schoolHolidayAgeBlocked, boolean billingReviewRequired, String billingReviewReason) {
            this.childAgeMonths = childAgeMonths;
            this.schoolHolidayAgeBlocked = schoolHolidayAgeBlocked;
            this.billingReviewRequired = billingReviewRequired;
            this.billingReviewReason = billingReviewReason == null ? "" : billingReviewReason;
        }

        Integer childAgeMonths() {
            return childAgeMonths;
        }

        boolean schoolHolidayAgeBlocked() {
            return schoolHolidayAgeBlocked;
        }

        boolean billingReviewRequired() {
            return billingReviewRequired;
        }

        String billingReviewReason() {
            return billingReviewReason;
        }
    }

    static final class BillingProfile {
        private final String ageBand;
        private final int monthlyFeeSen;
        private final boolean ageOutOfPolicy;
        private final String agePolicyReason;
        private final String feePolicyVersion;
        private final String activeBillingModel;
        private final int invoiceDueDay;
        private final Integer yearlyFeeCoveredYear;

        BillingProfile(
            String ageBand,
            int monthlyFeeSen,
            boolean ageOutOfPolicy,
            String agePolicyReason,
            String feePolicyVersion,
            String activeBillingModel,
            int invoiceDueDay,
            Integer yearlyFeeCoveredYear
        ) {
            this.ageBand = ageBand == null ? "" : ageBand;
            this.monthlyFeeSen = monthlyFeeSen;
            this.ageOutOfPolicy = ageOutOfPolicy;
            this.agePolicyReason = agePolicyReason == null ? "" : agePolicyReason;
            this.feePolicyVersion = feePolicyVersion == null ? "" : feePolicyVersion;
            this.activeBillingModel = activeBillingModel == null ? "" : activeBillingModel;
            this.invoiceDueDay = invoiceDueDay;
            this.yearlyFeeCoveredYear = yearlyFeeCoveredYear;
        }

        String ageBand() {
            return ageBand;
        }

        int monthlyFeeSen() {
            return monthlyFeeSen;
        }

        boolean ageOutOfPolicy() {
            return ageOutOfPolicy;
        }

        String agePolicyReason() {
            return agePolicyReason;
        }

        String feePolicyVersion() {
            return feePolicyVersion;
        }

        String activeBillingModel() {
            return activeBillingModel;
        }

        int invoiceDueDay() {
            return invoiceDueDay;
        }

        Integer yearlyFeeCoveredYear() {
            return yearlyFeeCoveredYear;
        }
    }

    static final class RegistrationPreview {
        private final BillingProfile billingProfile;
        private final int registrationFeeSen;
        private final int insuranceTakafulSen;
        private final int yearlyMaintenanceFeeSen;
        private final int registrationTotalSen;
        private final int invoiceGenerationDay;

        RegistrationPreview(
            BillingProfile billingProfile,
            int registrationFeeSen,
            int insuranceTakafulSen,
            int yearlyMaintenanceFeeSen,
            int registrationTotalSen,
            int invoiceGenerationDay
        ) {
            this.billingProfile = billingProfile;
            this.registrationFeeSen = registrationFeeSen;
            this.insuranceTakafulSen = insuranceTakafulSen;
            this.yearlyMaintenanceFeeSen = yearlyMaintenanceFeeSen;
            this.registrationTotalSen = registrationTotalSen;
            this.invoiceGenerationDay = invoiceGenerationDay;
        }

        BillingProfile billingProfile() {
            return billingProfile;
        }

        int registrationFeeSen() {
            return registrationFeeSen;
        }

        int insuranceTakafulSen() {
            return insuranceTakafulSen;
        }

        int yearlyMaintenanceFeeSen() {
            return yearlyMaintenanceFeeSen;
        }

        int registrationTotalSen() {
            return registrationTotalSen;
        }

        int invoiceGenerationDay() {
            return invoiceGenerationDay;
        }
    }

    static final class AbsenceLetterInput {
        private final String period;
        private final int days;

        AbsenceLetterInput(String period, int days) {
            this.period = period == null ? "" : period;
            this.days = days;
        }

        String period() {
            return period;
        }

        int days() {
            return days;
        }
    }

    static final class TransitSettings {
        private final boolean schoolHolidayTransit;
        private final Object careDurationHours;

        TransitSettings(boolean schoolHolidayTransit, Object careDurationHours) {
            this.schoolHolidayTransit = schoolHolidayTransit;
            this.careDurationHours = careDurationHours;
        }

        boolean schoolHolidayTransit() {
            return schoolHolidayTransit;
        }

        Object careDurationHours() {
            return careDurationHours;
        }
    }
}