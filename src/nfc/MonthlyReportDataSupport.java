package nfc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("all")
final class MonthlyReportDataSupport {
    private MonthlyReportDataSupport() {
    }

    @SuppressWarnings("unused")
    static List<FsDocument> preloadAttendance() {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            List<FsDocument> attendance = new ArrayList<>(client.listDocuments("attendance"));
            System.out.println("✅ Attendance cached (REST): " + attendance.size());
            return attendance;
        } catch (IOException | InterruptedException | IllegalStateException e) {
            System.err.println("monthlyReport: failed to preload attendance - " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unused")
    static Map<String, StudentInfo> preloadChildren() {
        Map<String, StudentInfo> childCache = new HashMap<>();
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            List<FsDocument> children = client.listDocuments("children");
            for (FsDocument doc : children) {
                childCache.put(
                    doc.getId(),
                    new StudentInfo(
                        doc.getId(),
                        doc.getString("name"),
                        doc.getString("parentName"),
                        doc.getString("parentContact")
                    )
                );
            }
            System.out.println("✅ Children cached (REST): " + childCache.size());
        } catch (IOException | InterruptedException | IllegalStateException e) {
            System.err.println("monthlyReport: failed to preload children - " + e.getMessage());
        }
        return childCache;
    }

    @SuppressWarnings("unused")
    static List<AttendanceRow> getAttendanceRowsForStudentMonth(List<FsDocument> cachedAttendance, String childId, int month, int year) {
        return MonthlyReportAggregationSupport.getAttendanceRowsForStudentMonth(cachedAttendance, childId, month, year);
    }

    @SuppressWarnings("unused")
    static javafx.collections.ObservableList<StudentMonthlyAttendance> buildMonthlyReportData(
        List<FsDocument> cachedAttendance,
        Map<String, StudentInfo> childCache,
        int month,
        int year
    ) {
        return MonthlyReportAggregationSupport.buildMonthlyReportData(cachedAttendance, childCache, month, year);
    }
}