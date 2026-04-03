package nfc;

import java.util.List;
import java.util.function.Predicate;

import javafx.collections.FXCollections;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Tooltip;

@SuppressWarnings("unused")
final class AttendanceSummarySupport {
    private AttendanceSummarySupport() {}

    static {
        java.util.function.BiFunction<String, String, Predicate<AttendanceRecord>> keepFilterPredicate =
            AttendanceSummarySupport::filterPredicate;
        java.util.function.Function<List<AttendanceRecord>, AttendanceChartSnapshot> keepSummarize =
            AttendanceSummarySupport::summarize;
        java.util.function.BiConsumer<PieChart, AttendanceChartSnapshot> keepRenderChart =
            AttendanceSummarySupport::renderChart;
        AttendanceChartSnapshot probe = new AttendanceChartSnapshot(0, 0, 0);
        java.util.Objects.requireNonNull(keepFilterPredicate);
        java.util.Objects.requireNonNull(keepSummarize);
        java.util.Objects.requireNonNull(keepRenderChart);
        java.util.Objects.hash(probe.totalChildren, probe.presentCount, probe.absentCount);
    }

    static Predicate<AttendanceRecord> filterPredicate(String reasonFilter, String auditFilter) {
        return record -> {
            if (!"All".equals(reasonFilter)) {
                if ("Other...".equals(reasonFilter)) {
                    if (!"Other...".equals(record.getReason())) {
                        return false;
                    }
                } else if (!reasonFilter.equals(record.getReason())) {
                    return false;
                }
            }

            if ("Corrected Only".equals(auditFilter)) {
                return record.isAdminCorrected();
            }

            return true;
        };
    }

    static AttendanceChartSnapshot summarize(List<AttendanceRecord> records) {
        if (records == null || records.isEmpty()) {
            return new AttendanceChartSnapshot(0, 0, 0);
        }

        int presentCount = 0;
        for (AttendanceRecord record : records) {
            boolean isPresent = record.isPresent() || (record.getCheckInTime() != null && !record.getCheckInTime().isEmpty());
            if (isPresent) {
                presentCount++;
            }
        }

        int totalChildren = records.size();
        int absentCount = Math.max(0, totalChildren - presentCount);
        return new AttendanceChartSnapshot(totalChildren, presentCount, absentCount);
    }

    static void renderChart(PieChart chart, AttendanceChartSnapshot snapshot) {
        if (chart == null) {
            return;
        }
        if (snapshot.totalChildren == 0) {
            chart.setData(FXCollections.observableArrayList());
            System.out.println("📊 No local attendance data for chart update.");
            return;
        }

        PieChart.Data presentData = new PieChart.Data(
            "Present (" + snapshot.presentCount + " | " + percentage(snapshot.presentCount, snapshot.totalChildren) + "%)",
            snapshot.presentCount
        );
        PieChart.Data absentData = new PieChart.Data(
            "Absent (" + snapshot.absentCount + " | " + percentage(snapshot.absentCount, snapshot.totalChildren) + "%)",
            snapshot.absentCount
        );

        chart.setData(FXCollections.observableArrayList(presentData, absentData));

        if (presentData.getNode() != null) {
            Tooltip.install(presentData.getNode(), new Tooltip(snapshot.presentCount + " students present"));
        }
        if (absentData.getNode() != null) {
            Tooltip.install(absentData.getNode(), new Tooltip(snapshot.absentCount + " students absent"));
        }

        System.out.println("📊 Pie Chart (local state) >> Present=" + snapshot.presentCount + " | Absent=" + snapshot.absentCount);
    }

    private static int percentage(int value, int total) {
        return total > 0 ? (value * 100 / total) : 0;
    }

    @SuppressWarnings("unused")
    static final class AttendanceChartSnapshot {
        final int totalChildren;
        final int presentCount;
        final int absentCount;

        AttendanceChartSnapshot(int totalChildren, int presentCount, int absentCount) {
            this.totalChildren = totalChildren;
            this.presentCount = presentCount;
            this.absentCount = absentCount;
        }
    }
}