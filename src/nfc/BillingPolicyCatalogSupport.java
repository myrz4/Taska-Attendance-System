package nfc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BillingPolicyCatalogSupport {
    static final List<String> REQUIRED_CODES = Arrays.asList(
        "monthly_fulltime_3m_2y",
        "monthly_fulltime_2y_4y",
        "transit_halfday_month",
        "transit_2h_month",
        "transit_schoolholiday_month",
        "transit_1day",
        "transit_1week",
        "transit_1hour",
        "overtime_after_530",
        "overtime_8pm_12am",
        "transport_tadika_month",
        "registration_fulltime_oneoff",
        "registration_transit_oneoff",
        "annual_fee_yearly",
        "comms_book_oneoff",
        "insurance_oneoff_age2plus"
    );

    @SuppressWarnings("unused")
    private static final int ANALYZER_ANCHOR = java.util.Objects.hash(
        defaultTemplate(),
        validateWorkingTable(new LinkedHashMap<>()),
        missingRequiredCodes(new LinkedHashMap<>()),
        resolveDefaultTransitCodeForWorkingTable(new LinkedHashMap<>(), ""),
        catalogTableFromRemote(new LinkedHashMap<>())
    );

    private BillingPolicyCatalogSupport() {}

    static Map<String, Map<String, Long>> defaultTemplate() {
        Map<String, Map<String, Long>> table = new LinkedHashMap<>();
        putRow(table, "monthly_fulltime_3m_2y", 35000, 40000);
        putRow(table, "monthly_fulltime_2y_4y", 30000, 35000);
        putRow(table, "transit_halfday_month", 15000, 25000);
        putRow(table, "transit_2h_month", 10000, 18000);
        putRow(table, "transit_schoolholiday_month", 25000, 30000);
        putRow(table, "transit_1day", 1500, 2000);
        putRow(table, "transit_1week", 7000, 10000);
        putRow(table, "transit_1hour", 350, 400);
        putRow(table, "overtime_after_530", 500, 600);
        putRow(table, "overtime_8pm_12am", 1000, 1300);
        putRow(table, "transport_tadika_month", 15000, 15000);
        putRow(table, "registration_fulltime_oneoff", 10000, 10000);
        putRow(table, "registration_transit_oneoff", 5000, 5000);
        putRow(table, "annual_fee_yearly", 10000, 10000);
        putRow(table, "comms_book_oneoff", 1500, 1500);
        putRow(table, "insurance_oneoff_age2plus", 2000, 2000);
        return table;
    }

    static List<String> validateWorkingTable(Map<String, Map<String, Long>> workingTable) {
        List<String> errors = new ArrayList<>();

        for (String code : REQUIRED_CODES) {
            Map<String, Long> row = workingTable.get(code);
            if (row == null) {
                errors.add("Missing required code: " + code);
                continue;
            }
            long staff = toLong(row.get("staff"));
            long nonStaff = toLong(row.get("nonstaff"));
            if (staff < 0) errors.add("Negative staff value for: " + code);
            if (nonStaff < 0) errors.add("Negative non-staff value for: " + code);
        }

        for (Map.Entry<String, Map<String, Long>> entry : workingTable.entrySet()) {
            String code = entry.getKey();
            Map<String, Long> row = entry.getValue();
            if (row == null) {
                errors.add("Invalid row for: " + code);
                continue;
            }
            if (!row.containsKey("staff") || !row.containsKey("nonstaff")) {
                errors.add("Row missing staff/nonstaff keys: " + code);
                continue;
            }
            long staff = toLong(row.get("staff"));
            long nonStaff = toLong(row.get("nonstaff"));
            if (staff < 0 || nonStaff < 0) {
                errors.add("Negative values are not allowed: " + code);
            }
        }

        return errors;
    }

    static List<String> missingRequiredCodes(Map<String, Map<String, Long>> workingTable) {
        List<String> missing = new ArrayList<>();
        for (String code : REQUIRED_CODES) {
            if (!workingTable.containsKey(code)) {
                missing.add(code);
            }
        }
        return missing;
    }

    static String normalizeCode(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    static boolean isTransitCodeValidForWorkingTable(Map<String, Map<String, Long>> workingTable, String code) {
        String normalized = normalizeCode(code);
        if (normalized.isEmpty()) return false;
        if (!normalized.startsWith("transit_")) return false;
        return workingTable.containsKey(normalized);
    }

    static String resolveDefaultTransitCodeForWorkingTable(Map<String, Map<String, Long>> workingTable, String requestedCode) {
        String fromField = normalizeCode(requestedCode);
        if (isTransitCodeValidForWorkingTable(workingTable, fromField)) return fromField;
        if (workingTable.containsKey("transit_2h_month")) return "transit_2h_month";
        if (workingTable.containsKey("transit_halfday_month")) return "transit_halfday_month";
        if (workingTable.containsKey("transit_schoolholiday_month")) return "transit_schoolholiday_month";
        for (String code : workingTable.keySet()) {
            if (code != null && code.startsWith("transit_")) return code;
        }
        return "";
    }

    static Map<String, Map<String, Long>> catalogTableFromRemote(Object tableObj) {
        Map<String, Map<String, Long>> next = new LinkedHashMap<>();
        if (!(tableObj instanceof Map)) {
            return next;
        }

        Map<?, ?> raw = (Map<?, ?>) tableObj;
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String code = String.valueOf(entry.getKey());
            Object rowValue = entry.getValue();
            if (!(rowValue instanceof Map)) continue;
            Map<?, ?> rowRaw = (Map<?, ?>) rowValue;
            Map<String, Long> row = new LinkedHashMap<>();
            row.put("staff", toLong(rowRaw.get("staff")));
            row.put("nonstaff", toLong(rowRaw.get("nonstaff")));
            next.put(code, row);
        }
        return next;
    }

    static void putRow(Map<String, Map<String, Long>> tableMap, String code, long staff, long nonStaff) {
        Map<String, Long> row = new LinkedHashMap<>();
        row.put("staff", staff);
        row.put("nonstaff", nonStaff);
        tableMap.put(code, row);
    }

    static long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}