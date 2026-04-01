package nfc;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

final class CasualTransitRowSupport {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter INPUT_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ms-MY"));

    private CasualTransitRowSupport() {}

    static CasualTransitView.VisitRow visitRowFromDocument(FsDocument document) {
        Long amount = document.getLong("amountSen");
        String status = safe(document.getString("status")).isBlank()
            ? "OPEN"
            : safe(document.getString("status")).toUpperCase(Locale.ROOT);
        String paymentStatus = safe(document.getString("paymentStatus")).isBlank()
            ? ("CLOSED".equals(status) ? "PAID" : ("CANCELED".equals(status) ? "VOID" : "PENDING"))
            : safe(document.getString("paymentStatus")).toUpperCase(Locale.ROOT);
        String receiptNo = safe(document.getString("receiptNo"));
        return new CasualTransitView.VisitRow(
            document.getId(),
            status,
            paymentStatus,
            safe(document.getString("childName")),
            safe(document.getString("guardianName")),
            safe(document.getString("guardianPhone")),
            safe(document.getString("guardianRelationship")),
            document.getDate("checkInAt"),
            document.getDate("checkOutAt"),
            amount == null ? 0L : amount,
            receiptNo.isBlank() ? "-" : receiptNo,
            safe(document.getString("notes")),
            safe(document.getString("paymentMethod"))
        );
    }

    static CasualTransitView.AuditEntry auditEntryFromDocument(FsDocument document) {
        Object details = document.get("details");
        String notes = "";
        if (details instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) details;
            Object rawNotes = map.get("notes");
            notes = rawNotes == null ? "" : String.valueOf(rawNotes).trim();
        }
        return new CasualTransitView.AuditEntry(
            safe(document.getString("action")),
            safe(document.getString("actorName")),
            safe(document.getString("actorRole")),
            safe(document.getString("reason")),
            notes,
            document.getDate("createdAt")
        );
    }

    static boolean visitMatchesDateScope(CasualTransitView.VisitRow row, String scope, LocalDate rangeFrom, LocalDate rangeTo) {
        String normalizedScope = safe(scope);
        if (normalizedScope.isBlank() || "All Dates".equalsIgnoreCase(normalizedScope)) {
            return true;
        }
        LocalDate visitDate = visitLocalDate(row.checkInAt(), row.checkOutAt());
        if (visitDate == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        if ("Today".equalsIgnoreCase(normalizedScope)) {
            return visitDate.equals(today);
        }
        if ("Current Month".equalsIgnoreCase(normalizedScope)) {
            return YearMonth.from(visitDate).equals(YearMonth.from(today));
        }
        if ("Custom Range".equalsIgnoreCase(normalizedScope)) {
            boolean afterStart = rangeFrom == null || !visitDate.isBefore(rangeFrom);
            boolean beforeEnd = rangeTo == null || !visitDate.isAfter(rangeTo);
            return afterStart && beforeEnd;
        }
        return true;
    }

    static String visitStatusLabel(String status) {
        if ("CANCELED".equals(status)) {
            return "Canceled";
        }
        return "OPEN".equals(status) ? "Open" : "Closed";
    }

    static String paymentStatusLabel(String paymentStatus) {
        if ("VOID".equals(paymentStatus)) {
            return "Void";
        }
        if ("PAID".equals(paymentStatus)) {
            return "Paid";
        }
        return "Pending";
    }

    static String checkLabel(Date date) {
        return formatDate(date);
    }

    static String amountLabel(long amountSen) {
        return amountSen <= 0 ? "-" : formatMoney(amountSen);
    }

    static String amountInputValue(long amountSen) {
        return amountSen <= 0 ? "" : String.format(Locale.ROOT, "%.2f", amountSen / 100.0d);
    }

    static String inputDateValue(Date date) {
        return date == null ? "" : INPUT_DATE_TIME_FORMAT.format(toLocalDateTime(date));
    }

    static LocalDate visitLocalDate(Date checkInAt, Date checkOutAt) {
        Date reference = checkInAt != null ? checkInAt : checkOutAt;
        if (reference == null) {
            return null;
        }
        return reference.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    static String guardianSummary(String guardianName, String guardianRelationship, String guardianPhone) {
        String relation = guardianRelationship.isBlank() ? "" : " (" + guardianRelationship + ")";
        return guardianName + relation + (guardianPhone.isBlank() ? "" : "\n" + guardianPhone);
    }

    static String searchText(CasualTransitView.VisitRow row) {
        return String.join(" ",
            row.childName(),
            row.guardianName(),
            row.guardianPhone(),
            row.guardianRelationship(),
            row.receiptNo(),
            row.notes(),
            row.paymentMethod(),
            paymentStatusLabel(row.paymentStatus())
        ).toLowerCase(Locale.ROOT);
    }

    static String detailText(CasualTransitView.VisitRow row) {
        return "Visit ID: " + row.visitId() + "\n"
            + "Status: " + visitStatusLabel(row.status()) + "\n"
            + "Payment Status: " + paymentStatusLabel(row.paymentStatus()) + "\n"
            + "Child: " + row.childName() + "\n"
            + "Guardian: " + row.guardianName() + (row.guardianRelationship().isBlank() ? "" : " (" + row.guardianRelationship() + ")") + "\n"
            + "Phone: " + (row.guardianPhone().isBlank() ? "-" : row.guardianPhone()) + "\n"
            + "Check In: " + checkLabel(row.checkInAt()) + "\n"
            + "Check Out: " + checkLabel(row.checkOutAt()) + "\n"
            + "Amount: " + amountLabel(row.amountSen()) + "\n"
            + "Payment Method: " + (row.paymentMethod().isBlank() ? "-" : row.paymentMethod()) + "\n"
            + "Receipt: " + row.receiptNo() + "\n"
            + "Notes: " + (row.notes().isBlank() ? "-" : row.notes());
    }

    static String auditExportTitle(CasualTransitView.AuditEntry entry) {
        return formatDate(entry.createdAt()) + " - " + auditActionLabel(entry.action());
    }

    static boolean auditMatchesDateScope(CasualTransitView.AuditEntry entry, String scope) {
        String normalizedScope = safe(scope);
        if (normalizedScope.isBlank() || "All Dates".equalsIgnoreCase(normalizedScope)) {
            return true;
        }
        if (entry.createdAt() == null) {
            return false;
        }
        LocalDate createdDate = entry.createdAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now();
        if ("Today".equalsIgnoreCase(normalizedScope)) {
            return createdDate.equals(today);
        }
        if ("Current Month".equalsIgnoreCase(normalizedScope)) {
            return YearMonth.from(createdDate).equals(YearMonth.from(today));
        }
        return true;
    }

    static String auditRender(CasualTransitView.AuditEntry entry) {
        return formatDate(entry.createdAt())
            + "\nAction: " + auditActionLabel(entry.action())
            + "\nActor: " + (entry.actorName().isBlank() ? "-" : entry.actorName())
            + (entry.actorRole().isBlank() ? "" : " (" + entry.actorRole() + ")")
            + "\nReason: " + (entry.reason().isBlank() ? "-" : entry.reason())
            + "\nNotes: " + (entry.notes().isBlank() ? "-" : entry.notes());
    }

    static String auditActionLabel(String action) {
        return action.isBlank() ? "-" : action;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String formatMoney(long sen) {
        return MONEY_FORMAT.format(sen / 100.0);
    }

    private static LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static String formatDate(Date date) {
        return date == null ? "-" : DATE_TIME_FORMAT.format(toLocalDateTime(date));
    }
}