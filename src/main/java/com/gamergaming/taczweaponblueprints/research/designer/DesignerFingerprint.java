package com.gamergaming.taczweaponblueprints.research.designer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;

/** Semantic JSON hash: objects are keyed, arrays retain their declared order, decimals normalize. */
public final class DesignerFingerprint {
    private DesignerFingerprint() {}
    public static String of(JsonElement value) {
        return DesignerJson.digest(canonical(value).getBytes(StandardCharsets.UTF_8));
    }
    public static String canonical(JsonElement value) {
        if (value.isJsonObject()) {
            var fields = new TreeMap<String, JsonElement>();
            value.getAsJsonObject().entrySet().forEach(e -> fields.put(e.getKey(), e.getValue()));
            return "{" + fields.entrySet().stream().map(e -> new JsonPrimitive(e.getKey()) + ":" + canonical(e.getValue()))
                    .collect(java.util.stream.Collectors.joining(",")) + "}";
        }
        if (value.isJsonArray()) {
            var values = new java.util.ArrayList<String>();
            value.getAsJsonArray().forEach(e -> values.add(canonical(e)));
            return "[" + String.join(",", values) + "]";
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            var number = value.getAsBigDecimal().stripTrailingZeros();
            if (!Double.isFinite(number.doubleValue())) throw new IllegalArgumentException("Nonfinite number");
            return number.signum() == 0 ? "0" : number.toString();
        }
        return value.toString();
    }
}
