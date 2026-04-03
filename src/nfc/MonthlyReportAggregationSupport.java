package nfc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

@SuppressWarnings("unused")
final class MonthlyReportAggregationSupport {
    private MonthlyReportAggregationSupport() {
    }

    @SuppressWarnings("unused")
    static List<AttendanceRow> getAttendanceRowsForStudentMonth(List<FsDocument> cachedAttendance, String childId, int month, int year) {
        List<AttendanceRow> rows = new ArrayList<>();
        try {
            Map<LocalDate, AttendanceRow> groupedByDate = new LinkedHashMap<>();

            for (FsDocument doc : cachedAttendance) {
                String idStr = String.valueOf(doc.getString("childId"));
                if (!idStr.equals(childId)) {
                    continue;
                }

                Date dateObj = doc.getDate("date");
                if (dateObj == null) {
                    continue;
                }
                LocalDate docDate = dateObj.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                if (docDate.getMonthValue() != month || docDate.getYear() != year) {
                    continue;
                }

                if (!groupedByDate.containsKey(docDate)) {
                    String name = String.valueOf(doc.getString("name"));
                    String reason = String.valueOf(doc.getString("reason"));
                    boolean present = Boolean.TRUE.equals(doc.getBoolean("isPresent"));
                    String status = present ? "attend" : "absence";

                    Date checkInDate = doc.getDate("check_in_time");
                    Date checkOutDate = doc.getDate("check_out_time");

                    String checkIn = checkInDate != null ? monthlyReport.formatReportTime(checkInDate) : "-";
                    String checkOut = checkOutDate != null ? monthlyReport.formatReportTime(checkOutDate) : "-";

                    groupedByDate.put(docDate, new AttendanceRow(
                        idStr,
                        name,
                        status,
                        reason != null ? reason : "",
                        checkIn,
                        checkOut,
                        docDate
                    ));
                }
            }

            rows.addAll(groupedByDate.values());
        } catch (RuntimeException e) {
            System.err.println("❌ Error loading attendance rows: " + e.getMessage());
        }
        return rows;
    }

    @SuppressWarnings("unused")
    static ObservableList<StudentMonthlyAttendance> buildMonthlyReportData(
        List<FsDocument> cachedAttendance,
        Map<String, StudentInfo> childCache,
        int month,
        int year
    ) {
        ObservableList<StudentMonthlyAttendance> data = FXCollections.observableArrayList();
        Map<String, Integer> totalDays = new HashMap<>();
        Map<String, Integer> presentDays = new HashMap<>();

        for (FsDocument doc : cachedAttendance) {
            Date dateObj = doc.getDate("date");
            if (dateObj == null) {
                continue;
            }

            LocalDate docDate = dateObj.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();
            if (docDate.getYear() != year || docDate.getMonthValue() != month) {
                continue;
            }

            String childId = doc.getString("childId");
            boolean present = Boolean.TRUE.equals(doc.getBoolean("isPresent"));

            totalDays.put(childId, totalDays.getOrDefault(childId, 0) + 1);
            if (present) {
                presentDays.put(childId, presentDays.getOrDefault(childId, 0) + 1);
            }
        }

        for (String childId : totalDays.keySet()) {
            int total = totalDays.get(childId);
            int present = presentDays.getOrDefault(childId, 0);
            double percent = total > 0 ? (present * 100.0 / total) : 0;

            StudentInfo info = childCache.get(childId);
            if (info == null) {
                continue;
            }

            StudentMonthlyAttendance record = new StudentMonthlyAttendance(
                childId,
                info.childName,
                String.format("%.2f%%", percent),
                percent >= 80 ? "Good" : "Poor"
            );
            record.setParentName(info.parentName);
            record.setParentContact(info.parentContact);
            data.add(record);
        }

        return data;
    }
}