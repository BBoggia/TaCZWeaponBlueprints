package com.gamergaming.taczweaponblueprints.research.designer;

import java.lang.reflect.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import com.gamergaming.taczweaponblueprints.progression.*;

/** Reads only the fixed immutable settings types written by the capture adapter. No config instances are created. */
public final class DesignerCapturedSettings {
    private DesignerCapturedSettings() {}
    public static BlueprintProgressionConfigSnapshot progression(ResearchDesignerCapture capture, ResourceLocation profile) {
        var value = settings(capture, "progression"); value.addProperty("activeProfileId", profile.toString());
        return (BlueprintProgressionConfigSnapshot) read(BlueprintProgressionConfigSnapshot.class, value);
    }
    public static ResearchFeatureConfigSnapshot features(ResearchDesignerCapture capture) {
        return (ResearchFeatureConfigSnapshot) read(ResearchFeatureConfigSnapshot.class, settings(capture, "features"));
    }
    private static JsonObject settings(ResearchDesignerCapture capture, String key) {
        return capture.json().getAsJsonObject("settings").getAsJsonObject("effective").getAsJsonObject(key).deepCopy();
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object read(Type type, JsonElement value) {
        if (type instanceof ParameterizedType p) {
            var arguments = p.getActualTypeArguments();
            if (p.getRawType() == Optional.class) return value.isJsonNull() ? Optional.empty() : Optional.of(read(arguments[0], value));
            if (p.getRawType() == List.class || p.getRawType() == Set.class) {
                var values = value.getAsJsonArray().asList().stream().map(v -> read(arguments[0], v)).toList();
                return p.getRawType() == Set.class ? Set.copyOf(values) : values;
            }
            if (p.getRawType() == Map.class) {
                var values = new LinkedHashMap<>(); value.getAsJsonObject().entrySet().forEach(e ->
                        values.put(read(arguments[0], new JsonPrimitive(e.getKey())), read(arguments[1], e.getValue())));
                return Map.copyOf(values);
            }
            throw new IllegalArgumentException("Unsupported captured settings collection");
        }
        var cls = (Class<?>) type;
        // Nullable boxed measurements are part of captured weapon evidence; record constructors validate required fields.
        if (value.isJsonNull()) {
            if (cls.isPrimitive()) throw new IllegalArgumentException("Required captured primitive is null");
            return null;
        }
        if (cls == String.class) return value.getAsString();
        if (cls == ResourceLocation.class) return ResourceLocation.parse(value.getAsString());
        if (cls == boolean.class || cls == Boolean.class) { if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Expected boolean setting"); return value.getAsBoolean(); }
        if (cls == int.class || cls == Integer.class) return value.getAsBigDecimal().intValueExact();
        if (cls == long.class || cls == Long.class) return value.getAsBigDecimal().longValueExact();
        if (cls == double.class || cls == Double.class) return value.getAsDouble();
        if (cls == float.class || cls == Float.class) return value.getAsFloat();
        if (cls.isEnum()) return Enum.valueOf((Class) cls, value.getAsString().toUpperCase(Locale.ROOT));
        if (!cls.isRecord() || !cls.getPackageName().startsWith("com.gamergaming.taczweaponblueprints."))
            throw new IllegalArgumentException("Unsupported captured settings type " + cls.getName());
        var components = cls.getRecordComponents(); var object = value.getAsJsonObject().deepCopy();
        if (cls == BlueprintProgressionConfigSnapshot.class && !object.has("domainSettings")) {
            var domains = new JsonObject(); domains.addProperty("ammo", "PROFILE");
            domains.addProperty("attachments", "PROFILE"); object.add("domainSettings", domains);
        }
        if (cls == ResearchFeatureConfigSnapshot.class && !object.has("stations")) {
            var stations = new JsonObject(); stations.addProperty("analyzer", "TIERED");
            stations.addProperty("research", "PRESET"); stations.addProperty("dataRewardTier", "TIER_1");
            object.add("stations", stations);
        }
        if (!object.keySet().equals(new HashSet<>(Arrays.stream(components).map(RecordComponent::getName).toList())))
            throw new IllegalArgumentException("Incomplete or unsupported captured settings: " + cls.getSimpleName());
        Object[] values = new Object[components.length]; Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType(); values[i] = read(components[i].getGenericType(), object.get(components[i].getName()));
        }
        try { return cls.getConstructor(types).newInstance(values); }
        catch (ReflectiveOperationException e) { throw new IllegalArgumentException("Invalid captured settings: " + cls.getSimpleName(), e); }
    }
}
