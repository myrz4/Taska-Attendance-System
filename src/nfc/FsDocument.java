package nfc;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Minimal Firestore document wrapper for REST responses.
 */
public final class FsDocument {
    private final String id;
    private final Map<String, Object> fields;

    public FsDocument(String id, Map<String, Object> fields) {
        this.id = id;
        this.fields = fields;
    }

    public String getId() {
        return id;
    }

    public Map<String, Object> fields() {
        return fields;
    }

    public Object get(String key) {
        return fields == null ? null : fields.get(key);
    }

    public String getString(String key) {
        Object v = get(key);
        if (v == null) return null;
        String s = String.valueOf(v);
        return s == null ? null : s.trim();
    }

    public Long getLong(String key) {
        Object v = get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception ignored) {
            return null;
        }
    }

    public Boolean getBoolean(String key) {
        Object v = get(key);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim().toLowerCase();
        if ("true".equals(s)) return true;
        if ("false".equals(s)) return false;
        return null;
    }

    public Date getDate(String key) {
        Object v = get(key);
        if (v == null) return null;
        if (v instanceof Date d) return d;
        if (v instanceof Instant i) return Date.from(i);
        return null;
    }
}
