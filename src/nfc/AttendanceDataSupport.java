package nfc;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

final class AttendanceDataSupport {
    private AttendanceDataSupport() {}

    static AttendanceDataCache preloadChildren(AttendanceDataCache cache) throws IOException, InterruptedException {
        if (!UserSession.isLoggedIn()) {
            return AttendanceDataCache.empty();
        }
        if (cache != null && cache.hasChildren()) {
            return cache;
        }

        FirestoreRestClient client = FirestoreRest.forCurrentUser();
        List<FsDocument> children = client.listDocuments("children");
        System.out.println("⚡ Attendance preload complete (REST)");
        return (cache == null ? AttendanceDataCache.empty() : cache).withChildren(children);
    }

    static AttendanceLoadResult loadRecords(LocalDate date, AttendanceDataCache cache) throws Exception {
        AttendanceDataCache resolvedCache = preloadChildren(cache);
        AttendanceQueryResult attendanceQuery = getAttendanceDocsForDate(date, resolvedCache);
        List<AttendanceRecord> records = AttendanceRecordDataSupport.buildRecords(attendanceQuery.cache.children(), attendanceQuery.documents);
        System.out.println("✅ Attendance docs loaded for " + date);
        System.out.println("✅ Matching records found: " + attendanceQuery.documents.size());
        return new AttendanceLoadResult(attendanceQuery.cache, records);
    }

    private static AttendanceQueryResult getAttendanceDocsForDate(LocalDate date, AttendanceDataCache cache) throws Exception {
        if (cache != null && cache.hasAttendanceFor(date)) {
            return new AttendanceQueryResult(cache, cache.attendanceForDate());
        }

        FirestoreRestClient client = FirestoreRest.forCurrentUser();
        Date startOfDay = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<FsDocument> docs = client.queryWhereEqual("attendance", "date", startOfDay);
        AttendanceDataCache updatedCache = (cache == null ? AttendanceDataCache.empty() : cache).withAttendance(date, docs);
        return new AttendanceQueryResult(updatedCache, updatedCache.attendanceForDate());
    }

    static final class AttendanceDataCache {
        private final List<FsDocument> children;
        private final LocalDate attendanceDate;
        private final List<FsDocument> attendanceForDate;

        private AttendanceDataCache(List<FsDocument> children, LocalDate attendanceDate, List<FsDocument> attendanceForDate) {
            this.children = children == null ? List.of() : new ArrayList<>(children);
            this.attendanceDate = attendanceDate;
            this.attendanceForDate = attendanceForDate == null ? List.of() : new ArrayList<>(attendanceForDate);
        }

        static AttendanceDataCache empty() {
            return new AttendanceDataCache(List.of(), null, List.of());
        }

        boolean hasChildren() {
            return !children.isEmpty();
        }

        boolean hasAttendanceFor(LocalDate date) {
            return date != null && attendanceDate != null && attendanceDate.equals(date);
        }

        List<FsDocument> children() {
            return children;
        }

        List<FsDocument> attendanceForDate() {
            return attendanceForDate;
        }

        AttendanceDataCache withChildren(List<FsDocument> value) {
            return new AttendanceDataCache(value, attendanceDate, attendanceForDate);
        }

        AttendanceDataCache withAttendance(LocalDate date, List<FsDocument> value) {
            return new AttendanceDataCache(children, date, value);
        }

        AttendanceDataCache clearAttendance() {
            return new AttendanceDataCache(children, null, List.of());
        }
    }

    static final class AttendanceLoadResult {
        final AttendanceDataCache cache;
        final List<AttendanceRecord> records;

        AttendanceLoadResult(AttendanceDataCache cache, List<AttendanceRecord> records) {
            this.cache = cache;
            this.records = records == null ? List.of() : records;
        }
    }

    private static final class AttendanceQueryResult {
        final AttendanceDataCache cache;
        final List<FsDocument> documents;

        AttendanceQueryResult(AttendanceDataCache cache, List<FsDocument> documents) {
            this.cache = cache;
            this.documents = documents == null ? List.of() : documents;
        }
    }
}