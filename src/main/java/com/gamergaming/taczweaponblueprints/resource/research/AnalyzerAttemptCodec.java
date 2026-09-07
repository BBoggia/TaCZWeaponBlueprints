package com.gamergaming.taczweaponblueprints.resource.research;

import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;

/** Reuses reverse-engineering fields without accepting fractional counts or explicit nulls. */
final class AnalyzerAttemptCodec {
    private AnalyzerAttemptCodec() { }
    static final Codec<BlueprintReverseEngineeringOverride> CODEC = Codec.of(BlueprintReverseEngineeringOverride.CODEC,
            new Decoder<>() {
                @Override public <T> DataResult<Pair<BlueprintReverseEngineeringOverride, T>> decode(DynamicOps<T> ops, T value) {
                    return inspect(ops, value, 0).flatMap(ignored -> BlueprintReverseEngineeringOverride.CODEC.decode(ops, value));
                }
            });
    static <T> DataResult<Boolean> inspect(DynamicOps<T> ops, T value, int depth) {
        if (depth > 8) return DataResult.error(() -> "Analyzer attempt nesting exceeds the supported structure");
        if (value == null || value.equals(ops.empty())) return DataResult.error(() -> "Analyzer attempt fields cannot be null");
        var map = ops.getMap(value).result();
        if (map.isPresent()) {
            for (var entry : map.orElseThrow().entries().toList()) {
                String key = ops.getStringValue(entry.getFirst()).result().orElse("");
                var result = inspect(ops, entry.getSecond(), depth + 1);
                if (result.error().isPresent()) return result;
                if (key.equals("input_count") || key.equals("points") || key.equals("count")) {
                    var number = AnalyzerOutcomeTable.integer(0, Integer.MAX_VALUE).parse(ops, entry.getSecond());
                    if (number.error().isPresent()) return DataResult.error(() -> "Analyzer " + key + " must be a bounded integer");
                }
                if (key.equals("enabled") || key.equals("allow_known") || key.equals("allow_modified")
                        || key.equals("output_recyclable") || key.equals("expert_allow_economy_loop")) {
                    if (AnalyzerOptionalFieldCodec.BOOLEAN.parse(ops, entry.getSecond()).error().isPresent()) {
                        return DataResult.error(() -> "Analyzer " + key + " must be a boolean");
                    }
                }
            }
        } else {
            var list = ops.getStream(value).result();
            if (list.isPresent()) for (var element : list.orElseThrow().toList()) {
                var result = inspect(ops, element, depth + 1); if (result.error().isPresent()) return result;
            }
        }
        return DataResult.success(true);
    }
}
