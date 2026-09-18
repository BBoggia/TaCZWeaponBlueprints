package com.gamergaming.taczweaponblueprints.villager.trade;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.resources.ResourceLocation;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.*;

/** Strict version-1 JSON: reject duplicate keys, coercions, typos and oversized input. */
public final class TradeParser {
    private TradeParser() {}
    public record Parsed(TradeDefinition definition, int characters) {}
    public static Parsed read(ResourceLocation id, Reader source) throws IOException {
        StringBuilder text = new StringBuilder(); char[] buffer = new char[4096]; int n;
        while ((n = source.read(buffer, 0, Math.min(buffer.length, TradeLimits.JSON_CHARACTERS + 1 - text.length()))) != -1) {
            text.append(buffer, 0, n);
            if (text.length() > TradeLimits.JSON_CHARACTERS) throw new IllegalArgumentException("trade JSON character limit exceeded");
        }
        try (JsonReader reader = new JsonReader(new StringReader(text.toString()))) {
            reader.setLenient(false);
            JsonObject root = object(json(reader, 0));
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("trailing JSON content");
            fields(root, "version", "profession", "level", "slot", "role", "enabled", "output", "price", "secondary", "uses", "xp", "demand_multiplier", "minimum_basis_points", "required_mods", "fallbacks");
            if (integer(root, "version") != 1) throw new IllegalArgumentException("unsupported trade format version");
            String profession = string(root, "profession");
            Profession owner = switch (profession) {
                case "taczweaponblueprints:gunsmith" -> Profession.GUNSMITH;
                case "taczweaponblueprints:ballistics_researcher" -> Profession.BALLISTICS_RESEARCHER;
                default -> throw new IllegalArgumentException("unknown trade profession: " + profession);
            };
            JsonObject output = object(required(root, "output"));
            fields(output, "kind", "count", "tier", "selection", "candidates", "allow_legacy_exchange");
            List<Choice> choices = new ArrayList<>();
            for (JsonElement e : array(output, "candidates")) {
                JsonObject choice = object(e); fields(choice, "id", "weight");
                choices.add(new Choice(parseId(string(choice, "id")), choice.has("weight") ? number(choice, "weight").doubleValue() : 1));
            }
            var result = new TradeDefinition(id, owner, integer(root, "level"), parseId(string(root, "slot")),
                    enumeration(root, "role", Role.CORE), bool(root, "enabled", true),
                    new Output(enumeration(output, "kind", Kind.class), integer(output, "count"),
                            output.has("tier") ? integer(output, "tier") : 0, enumeration(output, "selection", Selection.NONE), choices, bool(output,"allow_legacy_exchange",false)),
                    input(object(required(root, "price"))), root.has("secondary") ? Optional.of(input(object(root.get("secondary")))) : Optional.empty(),
                    integer(root, "uses"), integer(root, "xp"), demand(root),
                    root.has("minimum_basis_points") ? Optional.of(integer(root, "minimum_basis_points")) : Optional.empty(),
                    strings(root, "required_mods"), strings(root, "fallbacks").stream().map(TradeDefinition::parseId).toList());
            return new Parsed(result, text.length());
        }
    }
    private static float demand(JsonObject o) {
        if (!o.has("demand_multiplier")) return .05f;
        BigDecimal value = number(o, "demand_multiplier");
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0)
            throw new IllegalArgumentException("demand multiplier must be in [0,1]");
        return value.floatValue();
    }
    private static Input input(JsonObject o) {
        fields(o, "item", "count"); String item = string(o, "item");
        return new Input(item.equals("currency") ? Optional.empty() : Optional.of(parseId(item)), integer(o, "count"));
    }
    private static JsonElement json(JsonReader reader, int depth) throws IOException {
        if (depth > TradeLimits.JSON_DEPTH) throw new IllegalArgumentException("JSON nesting limit exceeded");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject(); var o = new JsonObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (o.has(name)) throw new IllegalArgumentException("duplicate JSON key: " + name);
                    o.add(name, json(reader, depth + 1));
                }
                reader.endObject(); yield o;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray(); var a = new JsonArray();
                while (reader.hasNext()) {
                    if (a.size() >= TradeLimits.CHOICES) throw new IllegalArgumentException("JSON array limit exceeded");
                    a.add(json(reader, depth + 1));
                }
                reader.endArray(); yield a;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            default -> throw new IllegalArgumentException("unexpected JSON token: " + reader.peek());
        };
    }
    private static void fields(JsonObject o, String... names) {
        Set<String> allowed = Set.of(names);
        for (String key : o.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("unknown field: " + key);
    }
    private static JsonObject object(JsonElement e) {
        if (e == null || !e.isJsonObject()) throw new IllegalArgumentException("expected JSON object");
        return e.getAsJsonObject();
    }
    private static JsonElement required(JsonObject o, String key) {
        if (!o.has(key)) throw new IllegalArgumentException("missing field: " + key); return o.get(key);
    }
    private static String string(JsonObject o, String key) { return string(required(o, key)); }
    private static String string(JsonElement e) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("expected string");
        return e.getAsString();
    }
    private static BigDecimal number(JsonObject o, String key) {
        JsonElement e = required(o, key);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("expected number: " + key);
        return e.getAsBigDecimal();
    }
    private static int integer(JsonObject o, String key) { return number(o, key).intValueExact(); }
    private static boolean bool(JsonObject o, String key, boolean fallback) {
        if (!o.has(key)) return fallback;
        if (!o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isBoolean()) throw new IllegalArgumentException("expected boolean: " + key);
        return o.get(key).getAsBoolean();
    }
    private static JsonArray array(JsonObject o, String key) {
        if (!o.has(key)) return new JsonArray();
        if (!o.get(key).isJsonArray()) throw new IllegalArgumentException("expected array: " + key);
        return o.getAsJsonArray(key);
    }
    private static List<String> strings(JsonObject o, String key) {
        List<String> result = new ArrayList<>(); array(o, key).forEach(e -> result.add(string(e))); return result;
    }
    private static <T extends Enum<T>> T enumeration(JsonObject o, String key, T fallback) {
        return o.has(key) ? enumeration(o, key, fallback.getDeclaringClass()) : fallback;
    }
    private static <T extends Enum<T>> T enumeration(JsonObject o, String key, Class<T> type) {
        String value = string(o, key);
        T result = Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        if (!result.name().toLowerCase(Locale.ROOT).equals(value)) throw new IllegalArgumentException("expected lowercase " + key);
        return result;
    }
}
