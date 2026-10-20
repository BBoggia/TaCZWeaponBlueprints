package com.gamergaming.taczweaponblueprints.research.designer;

import com.google.gson.*;
import com.mojang.serialization.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Value-only evidence adapter. Never reflects entity, recipe, registry, config or singleton objects. */
public final class DesignerValues {
    private DesignerValues() {}
    public static <T> JsonObject encode(Codec<T> codec, T value) {
        return codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow(IllegalArgumentException::new).getAsJsonObject();
    }
    public static JsonElement evidence(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof String v) return new JsonPrimitive(v);
        if (value instanceof Boolean v) return new JsonPrimitive(v);
        if (value instanceof Number v) return new JsonPrimitive(v);
        if (value instanceof ResourceLocation v) return new JsonPrimitive(v.toString());
        if (value instanceof Enum<?> v) return new JsonPrimitive(v.name().toLowerCase(Locale.ROOT));
        if (value instanceof Optional<?> v) return v.map(DesignerValues::evidence).orElse(JsonNull.INSTANCE);
        if (value instanceof Map<?, ?> v) {
            var result = new JsonObject(); var sorted = new TreeMap<String, Object>();
            v.forEach((k, item) -> sorted.put(key(k), item));
            sorted.forEach((k, item) -> result.add(k, evidence(item))); return result;
        }
        if (value instanceof Collection<?> v) {
            var values = v.stream().map(DesignerValues::evidence);
            if (value instanceof Set<?>) values = values.sorted(Comparator.comparing(DesignerFingerprint::canonical));
            var result = new JsonArray(); values.forEach(result::add); return result;
        }
        Class<?> type = value.getClass();
        if (type.isRecord() && type.getPackageName().startsWith("com.gamergaming.taczweaponblueprints.")) {
            var result = new JsonObject();
            for (var c : type.getRecordComponents()) {
                try { result.add(c.getName(), evidence(c.getAccessor().invoke(value))); }
                catch (ReflectiveOperationException e) { throw new IllegalArgumentException("Unsupported captured value: " + type.getName(), e); }
            }
            return result;
        }
        throw new IllegalArgumentException("Unsupported captured value: " + type.getName());
    }
    private static String key(Object key) {
        if (key instanceof String || key instanceof ResourceLocation) return key.toString();
        if (key instanceof Enum<?> e) return e.name().toLowerCase(Locale.ROOT);
        throw new IllegalArgumentException("Unsupported evidence key");
    }
}
