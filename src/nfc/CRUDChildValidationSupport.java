package nfc;

import java.time.LocalDate;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

final class CRUDChildValidationSupport {
    private CRUDChildValidationSupport() {
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
            && (childAgeMonths < 3 || childAgeMonths >= 60);
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

    static UniformChargeInput parseUniformCharge(boolean enabled, String feeRaw, String periodRaw) {
        if (!enabled) {
            return new UniformChargeInput(0, "");
        }

        int uniformFeeSen;
        try {
            uniformFeeSen = parseMoneyToSen(feeRaw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Uniform fee must be a valid amount.", ex);
        }
        if (uniformFeeSen <= 0) {
            throw new IllegalArgumentException("Uniform fee must be greater than zero when uniform billing is enabled.");
        }

        String chargePeriod = safeStr(periodRaw).trim();
        if (!chargePeriod.isEmpty() && !chargePeriod.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("Uniform charge period must be in yyyy-MM format.");
        }
        return new UniformChargeInput(uniformFeeSen, chargePeriod);
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

    private static int parseMoneyToSen(String raw) {
        String normalized = safeStr(raw).trim().replace("RM", "").replace(",", "");
        if (normalized.isEmpty()) {
            return 0;
        }
        double value = Double.parseDouble(normalized);
        return (int) Math.round(Math.max(0d, value) * 100d);
    }

    private static Integer ageInMonths(LocalDate at, LocalDate birthDate) {
        if (at == null || birthDate == null) {
            return null;
        }
        return (at.getYear() - birthDate.getYear()) * 12 + (at.getMonthValue() - birthDate.getMonthValue());
    }

    static final class BillingAssessment {
        final Integer childAgeMonths;
        final boolean schoolHolidayAgeBlocked;
        final boolean billingReviewRequired;
        final String billingReviewReason;

        BillingAssessment(Integer childAgeMonths, boolean schoolHolidayAgeBlocked, boolean billingReviewRequired, String billingReviewReason) {
            this.childAgeMonths = childAgeMonths;
            this.schoolHolidayAgeBlocked = schoolHolidayAgeBlocked;
            this.billingReviewRequired = billingReviewRequired;
            this.billingReviewReason = billingReviewReason == null ? "" : billingReviewReason;
        }
    }

    static final class AbsenceLetterInput {
        final String period;
        final int days;

        AbsenceLetterInput(String period, int days) {
            this.period = period == null ? "" : period;
            this.days = days;
        }
    }

    static final class UniformChargeInput {
        final int feeSen;
        final String chargePeriod;

        UniformChargeInput(int feeSen, String chargePeriod) {
            this.feeSen = feeSen;
            this.chargePeriod = chargePeriod == null ? "" : chargePeriod;
        }
    }

    static final class TransitSettings {
        final boolean schoolHolidayTransit;
        final Object careDurationHours;

        TransitSettings(boolean schoolHolidayTransit, Object careDurationHours) {
            this.schoolHolidayTransit = schoolHolidayTransit;
            this.careDurationHours = careDurationHours;
        }
    }
}