package nfc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

@SuppressWarnings("unused")
final class TeacherPayrollRemoteSupport {
    private static final Gson GSON = new Gson();

    private TeacherPayrollRemoteSupport() {
    }

    static PayrollSummary loadSummary(String period, boolean generateIfMissing) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("period", period);
        if (generateIfMissing) {
            payload.put("generateIfMissing", true);
        }
        return callSummary("getTeacherPayrollSummary", payload, RemoteAction.LOAD_SUMMARY);
    }

    static PayrollSummary generateMonthlyPayroll(String period) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("period", period);
        return callSummary("generateTeacherMonthlyPayroll", payload, RemoteAction.GENERATE);
    }

    static List<PayrollRecord> loadTeacherHistory(String teacherId, int limit) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("teacherId", teacherId);
        if (limit > 0) {
            payload.put("limit", limit);
        }
        try {
            FirebaseFunctionsClient.CallResult result = callable("getTeacherPayrollForTeacher", payload);
            Map<?, ?> parsed = BillingPolicyCallableSupport.parseCallableResultMap(result.rawBody);
            ensureOk(result, parsed);
            return payrollList(parsed);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static PayrollRecord markReviewed(String payrollId, String reviewNote) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payrollId", payrollId);
        if (reviewNote != null && !reviewNote.isBlank()) {
            payload.put("reviewNote", reviewNote);
        }
        return callSinglePayroll("markTeacherPayrollReviewed", payload);
    }

    static PayrollRecord markPaid(String payrollId, String paymentReference, String paymentNote) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payrollId", payrollId);
        if (paymentReference != null && !paymentReference.isBlank()) {
            payload.put("paymentReference", paymentReference);
        }
        if (paymentNote != null && !paymentNote.isBlank()) {
            payload.put("paymentNote", paymentNote);
        }
        return callSinglePayroll("markTeacherPayrollPaid", payload);
    }

    private static PayrollSummary callSummary(String functionName, Map<String, Object> payload, RemoteAction action) {
        try {
            FirebaseFunctionsClient.CallResult result = callable(functionName, payload);
            Map<?, ?> parsed = BillingPolicyCallableSupport.parseCallableResultMap(result.rawBody);
            ensureOk(result, parsed);
            return PayrollSummary.fromMap(parsed, action);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static PayrollRecord callSinglePayroll(String functionName, Map<String, Object> payload) {
        try {
            FirebaseFunctionsClient.CallResult result = callable(functionName, payload);
            Map<?, ?> parsed = BillingPolicyCallableSupport.parseCallableResultMap(result.rawBody);
            ensureOk(result, parsed);
            Object payrollObj = parsed.get("payroll");
            if (!(payrollObj instanceof Map<?, ?>)) {
                throw new IllegalStateException("Payroll callable did not return a payroll record.");
            }
            return PayrollRecord.fromMap((Map<?, ?>) payrollObj);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static FirebaseFunctionsClient.CallResult callable(String functionName, Map<String, Object> payload) throws IOException {
        String projectId = FirestoreRest.projectId();
        String idToken = UserSession.getIdToken();
        String payloadJson = GSON.toJson(payload == null ? Collections.emptyMap() : payload);
        switch (functionName) {
            case "getTeacherPayrollSummary":
                return FirebaseFunctionsClient.callGetTeacherPayrollSummary(projectId, idToken, payloadJson);
            case "generateTeacherMonthlyPayroll":
                return FirebaseFunctionsClient.callGenerateTeacherMonthlyPayroll(projectId, idToken, payloadJson);
            case "markTeacherPayrollReviewed":
                return FirebaseFunctionsClient.callMarkTeacherPayrollReviewed(projectId, idToken, payloadJson);
            case "markTeacherPayrollPaid":
                return FirebaseFunctionsClient.callMarkTeacherPayrollPaid(projectId, idToken, payloadJson);
            case "getTeacherPayrollForTeacher":
                return FirebaseFunctionsClient.callGetTeacherPayrollForTeacher(projectId, idToken, payloadJson);
            default:
                throw new IllegalArgumentException("Unsupported payroll callable: " + functionName);
        }
    }

    private static void ensureOk(FirebaseFunctionsClient.CallResult result, Map<?, ?> parsed) {
        Object ok = parsed.get("ok");
        if (ok instanceof Boolean && ((Boolean) ok)) {
            return;
        }
        throw BillingPolicyCallableSupport.callableFailure(result.reason, parsed);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static long moneyValue(Object value) {
        return value instanceof Number ? Math.round(((Number) value).doubleValue()) : 0L;
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static boolean boolValue(Object value) {
        return value instanceof Boolean ? (Boolean) value : false;
    }

    private static List<?> listValue(Object value) {
        return value instanceof List<?> ? (List<?>) value : Collections.emptyList();
    }

    private static Map<?, ?> mapValue(Object value) {
        return value instanceof Map<?, ?> ? (Map<?, ?>) value : Collections.emptyMap();
    }

    private static List<PayrollRecord> payrollList(Map<?, ?> result) {
        List<PayrollRecord> payrolls = new ArrayList<>();
        for (Object rawPayroll : listValue(result.get("payrolls"))) {
            if (rawPayroll instanceof Map<?, ?>) {
                payrolls.add(PayrollRecord.fromMap((Map<?, ?>) rawPayroll));
            }
        }

        if (payrolls.isEmpty()) {
            Object rawPayroll = result.get("payroll");
            if (rawPayroll instanceof Map<?, ?>) {
                payrolls.add(PayrollRecord.fromMap((Map<?, ?>) rawPayroll));
            }
        }
        return payrolls;
    }

    enum RemoteAction {
        LOAD_SUMMARY,
        GENERATE,
    }

    static final class PayrollSummary {
        final String period;
        final String periodLabel;
        final int teacherCount;
        final int reviewedCount;
        final int paidCount;
        final int unresolvedDailyCount;
        final long totalBaseSen;
        final long totalOvertimeSen;
        final long totalPaySen;
        final Policy policy;
        final List<PayrollRecord> payrolls;
        final String message;

        private PayrollSummary(
            String period,
            String periodLabel,
            int teacherCount,
            int reviewedCount,
            int paidCount,
            int unresolvedDailyCount,
            long totalBaseSen,
            long totalOvertimeSen,
            long totalPaySen,
            Policy policy,
            List<PayrollRecord> payrolls,
            String message
        ) {
            this.period = period;
            this.periodLabel = periodLabel;
            this.teacherCount = teacherCount;
            this.reviewedCount = reviewedCount;
            this.paidCount = paidCount;
            this.unresolvedDailyCount = unresolvedDailyCount;
            this.totalBaseSen = totalBaseSen;
            this.totalOvertimeSen = totalOvertimeSen;
            this.totalPaySen = totalPaySen;
            this.policy = policy;
            this.payrolls = payrolls;
            this.message = message;
        }

        static PayrollSummary fromMap(Map<?, ?> result, RemoteAction action) {
            List<PayrollRecord> payrolls = payrollList(result);

            String actionMessage;
            if (action == RemoteAction.GENERATE) {
                actionMessage = String.format(
                    java.util.Locale.ROOT,
                    "Generated %d payroll record(s) for %s. Updated %d existing record(s).",
                    intValue(result.get("createdCount")),
                    stringValue(result.get("periodLabel")),
                    intValue(result.get("updatedCount"))
                );
            } else {
                actionMessage = String.format(
                    java.util.Locale.ROOT,
                    "%d payroll record(s) loaded for %s.",
                    payrolls.size(),
                    stringValue(result.get("periodLabel"))
                );
            }

            return new PayrollSummary(
                stringValue(result.get("period")),
                stringValue(result.get("periodLabel")),
                intValue(result.get("teacherCount")),
                intValue(result.get("reviewedCount")),
                intValue(result.get("paidCount")),
                intValue(result.get("unresolvedDailyCount")),
                moneyValue(result.get("totalBaseSen")),
                moneyValue(result.get("totalOvertimeSen")),
                moneyValue(result.get("totalPaySen")),
                Policy.fromMap(mapValue(result.get("policy"))),
                payrolls,
                actionMessage
            );
        }
    }

    static final class PayrollRecord {
        final String payrollId;
        final String period;
        final String periodLabel;
        final String teacherId;
        final String teacherName;
        final String teacherEmail;
        final String teacherStatus;
        final String status;
        final boolean salaryActive;
        final String joinedDate;
        final long baseSalarySen;
        final long overtimeTotalSen;
        final long totalPaySen;
        final String currency;
        final int overtimeDayCount;
        final int weekdayBlocks;
        final int saturdayBlocks;
        final int totalBlocks;
        final String reviewedAt;
        final String paidAt;
        final String paymentReference;
        final String paymentNote;
        final List<DailyEntry> overtimeEntries;

        private PayrollRecord(
            String payrollId,
            String period,
            String periodLabel,
            String teacherId,
            String teacherName,
            String teacherEmail,
            String teacherStatus,
            String status,
            boolean salaryActive,
            String joinedDate,
            long baseSalarySen,
            long overtimeTotalSen,
            long totalPaySen,
            String currency,
            int overtimeDayCount,
            int weekdayBlocks,
            int saturdayBlocks,
            int totalBlocks,
            String reviewedAt,
            String paidAt,
            String paymentReference,
            String paymentNote,
            List<DailyEntry> overtimeEntries
        ) {
            this.payrollId = payrollId;
            this.period = period;
            this.periodLabel = periodLabel;
            this.teacherId = teacherId;
            this.teacherName = teacherName;
            this.teacherEmail = teacherEmail;
            this.teacherStatus = teacherStatus;
            this.status = status;
            this.salaryActive = salaryActive;
            this.joinedDate = joinedDate;
            this.baseSalarySen = baseSalarySen;
            this.overtimeTotalSen = overtimeTotalSen;
            this.totalPaySen = totalPaySen;
            this.currency = currency;
            this.overtimeDayCount = overtimeDayCount;
            this.weekdayBlocks = weekdayBlocks;
            this.saturdayBlocks = saturdayBlocks;
            this.totalBlocks = totalBlocks;
            this.reviewedAt = reviewedAt;
            this.paidAt = paidAt;
            this.paymentReference = paymentReference;
            this.paymentNote = paymentNote;
            this.overtimeEntries = overtimeEntries;
        }

        static PayrollRecord fromMap(Map<?, ?> map) {
            List<DailyEntry> overtimeEntries = new ArrayList<>();
            for (Object rawEntry : listValue(map.get("overtimeEntries"))) {
                if (rawEntry instanceof Map<?, ?>) {
                    overtimeEntries.add(DailyEntry.fromMap((Map<?, ?>) rawEntry));
                }
            }

            return new PayrollRecord(
                stringValue(map.get("payrollId")),
                stringValue(map.get("period")),
                stringValue(map.get("periodLabel")),
                stringValue(map.get("teacherId")),
                stringValue(map.get("teacherName")),
                stringValue(map.get("teacherEmail")),
                stringValue(map.get("teacherStatus")),
                stringValue(map.get("status")),
                boolValue(map.get("salaryActive")),
                stringValue(map.get("joinedDate")),
                moneyValue(map.get("baseSalarySen")),
                moneyValue(map.get("overtimeTotalSen")),
                moneyValue(map.get("totalPaySen")),
                stringValue(map.get("currency")),
                intValue(map.get("overtimeDayCount")),
                intValue(map.get("weekdayBlocks")),
                intValue(map.get("saturdayBlocks")),
                intValue(map.get("totalBlocks")),
                stringValue(map.get("reviewedAt")),
                stringValue(map.get("paidAt")),
                stringValue(map.get("paymentReference")),
                stringValue(map.get("paymentNote")),
                overtimeEntries
            );
        }
    }

    static final class DailyEntry {
        final String dailyId;
        final String dateKey;
        final String dayType;
        final int blocks;
        final long totalSen;
        final long rateSen;
        final String latestCheckoutAt;
        final String latestChildName;
        final int attendanceCount;
        final String closingTimeLabel;

        private DailyEntry(
            String dailyId,
            String dateKey,
            String dayType,
            int blocks,
            long totalSen,
            long rateSen,
            String latestCheckoutAt,
            String latestChildName,
            int attendanceCount,
            String closingTimeLabel
        ) {
            this.dailyId = dailyId;
            this.dateKey = dateKey;
            this.dayType = dayType;
            this.blocks = blocks;
            this.totalSen = totalSen;
            this.rateSen = rateSen;
            this.latestCheckoutAt = latestCheckoutAt;
            this.latestChildName = latestChildName;
            this.attendanceCount = attendanceCount;
            this.closingTimeLabel = closingTimeLabel;
        }

        static DailyEntry fromMap(Map<?, ?> map) {
            return new DailyEntry(
                stringValue(map.get("dailyId")),
                stringValue(map.get("dateKey")),
                stringValue(map.get("dayType")),
                intValue(map.get("blocks")),
                moneyValue(map.get("totalSen")),
                moneyValue(map.get("rateSen")),
                stringValue(map.get("latestCheckoutAt")),
                stringValue(map.get("latestChildName")),
                intValue(map.get("attendanceCount")),
                stringValue(map.get("closingTimeLabel"))
            );
        }
    }

    static final class Policy {
        final String policyVersion;
        final String currency;
        final long weekdayHalfHourRateSen;
        final long saturdayHalfHourRateSen;
        final String weekdayClosingTimeLabel;
        final String saturdayClosingTimeLabel;

        private Policy(
            String policyVersion,
            String currency,
            long weekdayHalfHourRateSen,
            long saturdayHalfHourRateSen,
            String weekdayClosingTimeLabel,
            String saturdayClosingTimeLabel
        ) {
            this.policyVersion = policyVersion;
            this.currency = currency;
            this.weekdayHalfHourRateSen = weekdayHalfHourRateSen;
            this.saturdayHalfHourRateSen = saturdayHalfHourRateSen;
            this.weekdayClosingTimeLabel = weekdayClosingTimeLabel;
            this.saturdayClosingTimeLabel = saturdayClosingTimeLabel;
        }

        static Policy fromMap(Map<?, ?> map) {
            return new Policy(
                stringValue(map.get("policyVersion")),
                stringValue(map.get("currency")),
                moneyValue(map.get("weekdayHalfHourRateSen")),
                moneyValue(map.get("saturdayHalfHourRateSen")),
                stringValue(map.get("weekdayClosingTimeLabel")),
                stringValue(map.get("saturdayClosingTimeLabel"))
            );
        }
    }
}