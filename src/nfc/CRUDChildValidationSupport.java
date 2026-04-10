package nfc;

import java.time.LocalDate;
import java.time.Period;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

final class CRUDChildValidationSupport {
    private CRUDChildValidationSupport() {
    }

    static {
        if (keepAnalyzerAnchors()) {
            syncTransitControls(null, null, null, null);
            assessBilling(LocalDate.now(), null, null, false);
            parseAbsenceLetter("", "");
            deriveTransitSettings(null, null, false);
            BillingAssessment billingProbe = new BillingAssessment(null, false, false, "");
            billingProbe.childAgeMonths();
            billingProbe.schoolHolidayAgeBlocked();
            billingProbe.billingReviewRequired();
            billingProbe.billingReviewReason();
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
        CRUDChildDialogSupport.FeePlanType selectedFeePlan = feePlan == null
            ? CRUDChildDialogSupport.FeePlanType.MONTHLY_FULLTIME
            : feePlan;
        boolean requestsSchoolHolidayTransit = selectedFeePlan == CRUDChildDialogSupport.FeePlanType.TRANSIT_SCHOOLHOLIDAY_MONTH
            || (selectedFeePlan == CRUDChildDialogSupport.FeePlanType.TRANSIT_AUTO_MONTHLY && schoolHolidayTransitSelected);
        boolean schoolHolidayAgeBlocked = requestsSchoolHolidayTransit
            && (childAgeMonths == null || childAgeMonths < 48);
        boolean billingReviewRequired = !selectedFeePlan.code.equals("transit")
            && childAgeMonths != null
            && (childAgeMonths < 3 || childAgeMonths >= 48);
        String reviewReason = billingReviewRequired
            ? (childAgeMonths != null && childAgeMonths < 3 ? "under_3_months" : "age_4y_or_above")
            : "";
        return new BillingAssessment(childAgeMonths, schoolHolidayAgeBlocked, billingReviewRequired, reviewReason);
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