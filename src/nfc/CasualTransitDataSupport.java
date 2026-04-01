package nfc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class CasualTransitDataSupport {
    private CasualTransitDataSupport() {}

    static List<CasualTransitView.VisitRow> loadVisits() {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            List<FsDocument> documents = client.listDocuments("casualTransitVisits");
            List<CasualTransitView.VisitRow> loaded = new ArrayList<>();
            for (FsDocument document : documents) {
                loaded.add(CasualTransitRowSupport.visitRowFromDocument(document));
            }
            loaded.sort(Comparator.comparingLong(CasualTransitView.VisitRow::sortTime).reversed());
            return loaded;
        } catch (IOException | InterruptedException error) {
            throw new RuntimeException(error);
        }
    }

    static List<CasualTransitView.AuditEntry> loadAuditEntries(String visitId) {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();
            List<FsDocument> documents = client.queryWhereEqual("casualTransitAudit", "visitId", visitId);
            List<CasualTransitView.AuditEntry> entries = new ArrayList<>();
            for (FsDocument document : documents) {
                entries.add(CasualTransitRowSupport.auditEntryFromDocument(document));
            }
            entries.sort(Comparator.comparingLong(CasualTransitView.AuditEntry::sortTime).reversed());
            return entries;
        } catch (IOException | InterruptedException error) {
            throw new RuntimeException(error);
        }
    }

    static String renderAuditEntries(List<CasualTransitView.AuditEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "No audit entries found for this visit.";
        }
        StringBuilder sb = new StringBuilder();
        for (CasualTransitView.AuditEntry entry : entries) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(entry.render());
        }
        return sb.toString();
    }

    static List<String> auditFilterOptions(List<CasualTransitView.AuditEntry> entries) {
        List<String> items = new ArrayList<>();
        items.add("All Actions");
        for (CasualTransitView.AuditEntry entry : entries) {
            String action = entry.actionLabel();
            if (!action.isBlank() && !items.contains(action)) {
                items.add(action);
            }
        }
        return items;
    }

    static AuditFilterResult applyAuditFilters(
        List<CasualTransitView.AuditEntry> currentAuditEntries,
        String action,
        String dateScope,
        java.util.function.Function<String, String> safe
    ) {
        String normalizedAction = safe.apply(action);
        String normalizedDateScope = safe.apply(dateScope);
        List<CasualTransitView.AuditEntry> filtered = currentAuditEntries.stream()
            .filter(entry -> "All Actions".equalsIgnoreCase(normalizedAction) || entry.actionLabel().equalsIgnoreCase(normalizedAction))
            .filter(entry -> entry.matchesDateScope(normalizedDateScope))
            .toList();
        return new AuditFilterResult(filtered, renderAuditEntries(filtered));
    }

    static final class AuditFilterResult {
        final List<CasualTransitView.AuditEntry> filteredEntries;
        final String renderedText;

        AuditFilterResult(List<CasualTransitView.AuditEntry> filteredEntries, String renderedText) {
            this.filteredEntries = filteredEntries;
            this.renderedText = renderedText;
        }
    }
}