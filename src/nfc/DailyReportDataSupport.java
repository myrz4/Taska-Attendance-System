package nfc;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

@SuppressWarnings("java:S1144")
public final class DailyReportDataSupport {
    private DailyReportDataSupport() {
    }

    public static ObservableList<AttendanceRow> loadAttendance(LocalDate date) {
        ObservableList<AttendanceRow> rows = FXCollections.observableArrayList();

        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            Date startOfDay = Date.from(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
            List<FsDocument> docs = client.queryWhereEqual("attendance", "date", startOfDay);

            for (FsDocument doc : docs) {
                String childId = String.valueOf(doc.getString("childId"));
                String name = String.valueOf(doc.getString("name"));
                String reason = String.valueOf(doc.getString("reason"));
                boolean present = Boolean.TRUE.equals(doc.getBoolean("isPresent"));
                String status = present ? "attend" : "absence";

                Date checkInDate = doc.getDate("check_in_time");
                Date checkOutDate = doc.getDate("check_out_time");

                String checkIn = checkInDate != null ? DailyReportPreviewSupport.formatReportTime(checkInDate) : "-";
                String checkOut = checkOutDate != null ? DailyReportPreviewSupport.formatReportTime(checkOutDate) : "-";

                rows.add(new AttendanceRow(
                    childId,
                    name,
                    status,
                    reason != null ? reason : "",
                    checkIn != null ? checkIn : "",
                    checkOut != null ? checkOut : "",
                    date
                ));
            }

            System.out.println("✅ Loaded " + rows.size() + " records for " + date);
        } catch (IOException | InterruptedException | RuntimeException e) {
            System.err.println("❌ Failed to load attendance: " + e.getMessage());
        }

        return rows;
    }
}