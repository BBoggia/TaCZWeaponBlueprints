package com.gamergaming.taczweaponblueprints.resource.research;

import java.io.*;
import java.math.BigDecimal;
import java.util.Set;
import com.google.gson.*;
import com.google.gson.stream.*;

/** Reject ambiguous object keys before Gson can silently replace a definition field. */
final class ResearchDataJson {
    private ResearchDataJson() { }
    static JsonElement parse(String text) throws IOException {
        try (var reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            var result = read(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("trailing Research Data JSON");
            return result;
        }
    }
    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 16) throw new IllegalArgumentException("Research Data JSON nesting exceeds 16 at " + reader.getPath());
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var result = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (result.has(key)) throw new IllegalArgumentException("duplicate field " + key + " at " + reader.getPath());
                    result.add(key, read(reader, depth + 1));
                }
                reader.endObject(); yield result;
            }
            case BEGIN_ARRAY -> {
                var result = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) result.add(read(reader, depth + 1));
                reader.endArray(); yield result;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("invalid Research Data JSON at " + reader.getPath());
        };
    }
    /** Old shared target/cost codecs also receive canonical IDs and exact integer validation here. */
    static void validateIds(JsonElement value, String field) {
        if (value.isJsonObject()) {
            value.getAsJsonObject().entrySet().forEach(e -> validateIds(e.getValue(), e.getKey()));
        } else if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(element -> validateIds(element, field));
        } else if (Set.of("item", "tag", "profile", "reconstruction", "target", "blueprints", "tags", "items", "exclude").contains(field)
                && !value.isJsonNull()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                    || ResearchDataPolicy.ID.parse(com.mojang.serialization.JsonOps.INSTANCE, value).error().isPresent())
                throw new IllegalArgumentException("expected canonical ID in " + field);
        }
    }
}
