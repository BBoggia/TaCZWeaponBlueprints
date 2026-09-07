package com.gamergaming.taczweaponblueprints.research.designer;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Strict, bounded document IO. Runtime codecs are deliberately not draft parsers. */
public final class DesignerJson {
    public static final int PROJECT_BYTES = 16 * 1024 * 1024;
    public static final int RECOVERY_BYTES = 32 * 1024 * 1024;
    public static final int MAX_DEPTH = 64;
    private DesignerJson() {}

    public static JsonObject parse(byte[] bytes, int limit) {
        return parse(bytes,limit,limit == RECOVERY_BYTES ? MAX_DEPTH + 2 : MAX_DEPTH);
    }
    public static JsonObject parse(byte[] bytes, int limit, int maximumDepth) {
        if (bytes.length > limit) throw new IllegalArgumentException("Document exceeds byte limit");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            try (var reader = new JsonReader(new StringReader(text))) {
                reader.setLenient(false);
                JsonElement value = value(reader, 0, maximumDepth);
                if (reader.peek() != JsonToken.END_DOCUMENT || !value.isJsonObject())
                    throw new IllegalArgumentException("Expected one JSON object");
                return value.getAsJsonObject();
            }
        } catch (IOException | IllegalStateException e) {
            throw new IllegalArgumentException("Invalid UTF-8 JSON document", e);
        }
    }

    private static JsonElement value(JsonReader reader, int depth, int maximum) throws IOException {
        if (depth > maximum) throw new IllegalArgumentException("JSON nesting exceeds limit");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String key = unicode(reader.nextName());
                    if (object.has(key)) throw new IllegalArgumentException("Duplicate JSON key: " + key);
                    object.add(key, value(reader, depth + 1, maximum));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                var array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) array.add(value(reader, depth + 1, maximum));
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(unicode(reader.nextString()));
            case NUMBER -> {
                String token = reader.nextString();
                // Retain the exact decimal value; reject numbers outside finite runtime representation.
                var number = new BigDecimal(token);
                if (!Double.isFinite(number.doubleValue())) throw new IllegalArgumentException("Nonfinite number");
                yield new JsonPrimitive(number);
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("Unexpected JSON token");
        };
    }

    public static byte[] bytes(JsonObject value, int limit) {
        return bytes(value,limit,limit == RECOVERY_BYTES ? MAX_DEPTH + 2 : MAX_DEPTH);
    }
    public static byte[] bytes(JsonObject value, int limit, int maximumDepth) {
        validate(value, 0, maximumDepth);
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limit) throw new IllegalArgumentException("Document exceeds byte limit");
        return bytes;
    }
    private static void validate(JsonElement value, int depth, int maximum) {
        if (depth > maximum) throw new IllegalArgumentException("JSON nesting exceeds limit");
        if (value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(e -> { unicode(e.getKey()); validate(e.getValue(), depth + 1, maximum); });
        else if (value.isJsonArray()) value.getAsJsonArray().forEach(e -> validate(e, depth + 1, maximum));
        else if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isString()) unicode(value.getAsString());
            if (value.getAsJsonPrimitive().isNumber() && !Double.isFinite(value.getAsDouble())) throw new IllegalArgumentException("Nonfinite number");
        }
    }
    private static String unicode(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i))) throw new IllegalArgumentException("Unpaired Unicode surrogate");
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Unpaired Unicode surrogate");
        }
        return value;
    }
    public static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static long integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected an integer");
        try { return value.getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Expected an in-range integer", e); }
    }
    public static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected a string");
        return value.getAsString();
    }

    /** Defensive immutable source container; preserves omission and unsupported nested source verbatim. */
    public static final class ObjectValue {
        private final JsonObject value;
        public ObjectValue(JsonObject value) { this.value = value.deepCopy(); }
        public JsonObject json() { return value.deepCopy(); }
        @Override public boolean equals(Object other) { return other instanceof ObjectValue o && value.equals(o.value); }
        @Override public int hashCode() { return value.hashCode(); }
    }
}
