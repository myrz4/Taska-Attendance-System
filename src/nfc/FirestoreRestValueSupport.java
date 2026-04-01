package nfc;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@SuppressWarnings("unused")
final class FirestoreRestValueSupport {
    private FirestoreRestValueSupport() {}

    static JsonObject toFields(Map<String, Object> fields) {
        JsonObject out = new JsonObject();
        if (fields == null) return out;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            out.add(entry.getKey(), toValue(entry.getValue()));
        }
        return out;
    }

    static JsonObject toValue(Object value) {
        JsonObject v = new JsonObject();

        if (value == null) {
            v.addProperty("nullValue", "NULL_VALUE");
            return v;
        }
        if (value instanceof String) {
            v.addProperty("stringValue", (String) value);
            return v;
        }
        if (value instanceof Boolean) {
            v.addProperty("booleanValue", (Boolean) value);
            return v;
        }
        if (value instanceof Integer) {
            Integer integerValue = (Integer) value;
            v.addProperty("integerValue", String.valueOf(integerValue.longValue()));
            return v;
        }
        if (value instanceof Long) {
            v.addProperty("integerValue", String.valueOf((Long) value));
            return v;
        }
        if (value instanceof Double) {
            v.addProperty("doubleValue", (Double) value);
            return v;
        }
        if (value instanceof Float) {
            Float floatValue = (Float) value;
            v.addProperty("doubleValue", floatValue.doubleValue());
            return v;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            JsonArray values = new JsonArray();
            for (Object item : list) {
                values.add(toValue(item));
            }

            JsonObject arrayValue = new JsonObject();
            arrayValue.add("values", values);
            v.add("arrayValue", arrayValue);
            return v;
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            JsonObject fields = new JsonObject();
            for (Object key : map.keySet()) {
                if (key == null) continue;
                fields.add(String.valueOf(key), toValue(map.get(key)));
            }

            JsonObject mapValue = new JsonObject();
            mapValue.add("fields", fields);
            v.add("mapValue", mapValue);
            return v;
        }
        if (value instanceof Date) {
            Date dateValue = (Date) value;
            v.addProperty("timestampValue", DateTimeFormatter.ISO_INSTANT.format(dateValue.toInstant()));
            return v;
        }
        if (value instanceof Instant) {
            v.addProperty("timestampValue", DateTimeFormatter.ISO_INSTANT.format((Instant) value));
            return v;
        }
        if (value instanceof FirestoreRestClient.ReferenceValue) {
            FirestoreRestClient.ReferenceValue referenceValue = (FirestoreRestClient.ReferenceValue) value;
            v.addProperty("referenceValue", referenceValue.value());
            return v;
        }

        v.addProperty("stringValue", String.valueOf(value));
        return v;
    }

    static Object fromValue(JsonObject v) {
        if (v == null) return null;

        if (v.has("nullValue")) return null;
        if (v.has("stringValue")) return v.get("stringValue").getAsString();
        if (v.has("booleanValue")) return v.get("booleanValue").getAsBoolean();

        if (v.has("integerValue")) {
            try {
                return v.get("integerValue").getAsLong();
            } catch (NumberFormatException | UnsupportedOperationException | ClassCastException | IllegalStateException ignored) {
                return null;
            }
        }

        if (v.has("doubleValue")) {
            try {
                return v.get("doubleValue").getAsDouble();
            } catch (NumberFormatException | UnsupportedOperationException | ClassCastException | IllegalStateException ignored) {
                return null;
            }
        }

        if (v.has("timestampValue")) {
            try {
                Instant instant = Instant.parse(v.get("timestampValue").getAsString());
                return Date.from(instant);
            } catch (java.time.format.DateTimeParseException | UnsupportedOperationException | ClassCastException | IllegalStateException ignored) {
                return null;
            }
        }

        if (v.has("referenceValue")) {
            return v.get("referenceValue").getAsString();
        }

        if (v.has("arrayValue") && v.get("arrayValue").isJsonObject()) {
            JsonObject av = v.getAsJsonObject("arrayValue");
            if (!av.has("values") || !av.get("values").isJsonArray()) return List.of();
            List<Object> out = new ArrayList<>();
            for (JsonElement element : av.getAsJsonArray("values")) {
                if (!element.isJsonObject()) continue;
                out.add(fromValue(element.getAsJsonObject()));
            }
            return out;
        }

        if (v.has("mapValue") && v.get("mapValue").isJsonObject()) {
            JsonObject mv = v.getAsJsonObject("mapValue");
            if (!mv.has("fields") || !mv.get("fields").isJsonObject()) return Map.of();

            Map<String, Object> out = new LinkedHashMap<>();
            JsonObject fs = mv.getAsJsonObject("fields");
            for (Map.Entry<String, JsonElement> entry : fs.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                out.put(entry.getKey(), fromValue(entry.getValue().getAsJsonObject()));
            }
            return out;
        }

        return null;
    }
}