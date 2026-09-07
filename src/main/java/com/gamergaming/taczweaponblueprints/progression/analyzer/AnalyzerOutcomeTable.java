package com.gamergaming.taczweaponblueprints.progression.analyzer;

import java.util.Objects;
import java.util.function.LongUnaryOperator;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Pure probability data. Evaluating or exporting it never draws randomness. */
public record AnalyzerOutcomeTable(int blueprintWeight, int fragmentWeight, int noResultWeight,
        int minimumFragments, int maximumFragments) {
    public static final int MAX_WEIGHT = 1_000_000_000;
    public static final AnalyzerOutcomeTable GUARANTEED_BLUEPRINT = new AnalyzerOutcomeTable(1, 0, 0, 0, 0);
    private static final Codec<AnalyzerOutcomeTable> RAW = RecordCodecBuilder.create(i -> i.group(
            integer(0, MAX_WEIGHT).fieldOf("blueprint_weight").forGetter(AnalyzerOutcomeTable::blueprintWeight),
            integer(0, MAX_WEIGHT).fieldOf("fragment_weight").forGetter(AnalyzerOutcomeTable::fragmentWeight),
            integer(0, MAX_WEIGHT).fieldOf("no_result_weight").forGetter(AnalyzerOutcomeTable::noResultWeight),
            integer(0, 64).fieldOf("min_fragments").forGetter(AnalyzerOutcomeTable::minimumFragments),
            integer(0, 64).fieldOf("max_fragments").forGetter(AnalyzerOutcomeTable::maximumFragments)
    ).apply(i, AnalyzerOutcomeTable::new));
    public static final Codec<AnalyzerOutcomeTable> CODEC = StrictRecordCodec.wrap("Analyzer outcomes", RAW,
            "blueprint_weight", "fragment_weight", "no_result_weight", "min_fragments", "max_fragments");

    public AnalyzerOutcomeTable {
        if (blueprintWeight < 0 || blueprintWeight > MAX_WEIGHT || fragmentWeight < 0
                || fragmentWeight > MAX_WEIGHT || noResultWeight < 0 || noResultWeight > MAX_WEIGHT
                || (long) blueprintWeight + fragmentWeight + noResultWeight == 0) {
            throw new IllegalArgumentException("outcome weights must be bounded with a positive total");
        }
        if (fragmentWeight == 0 ? minimumFragments != 0 || maximumFragments != 0
                : minimumFragments < 1 || maximumFragments < minimumFragments || maximumFragments > 64) {
            throw new IllegalArgumentException("positive fragment outcomes require a quantity range from 1 to 64; otherwise use 0/0");
        }
    }

    /** Avoid integer codecs that truncate fractional JSON numbers before validation. */
    public static Codec<Integer> integer(int min, int max) {
        Codec<Integer> delegate = Codec.DOUBLE.flatXmap(value -> Double.isFinite(value) && value == Math.rint(value)
                        && value >= min && value <= max ? DataResult.success(value.intValue())
                        : DataResult.error(() -> "expected an integer from " + min + " to " + max),
                value -> value >= min && value <= max ? DataResult.success(value.doubleValue())
                        : DataResult.error(() -> "integer outside supported bounds"));
        return Codec.of(Codec.intRange(min, max), new com.mojang.serialization.Decoder<>() {
            @Override public <T> DataResult<com.mojang.datafixers.util.Pair<Integer, T>> decode(com.mojang.serialization.DynamicOps<T> ops, T input) {
                if (input instanceof com.google.gson.JsonElement json) {
                    if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) {
                        return DataResult.error(() -> "expected a numeric integer");
                    }
                    try {
                        int exact = json.getAsBigDecimal().intValueExact();
                        if (exact < min || exact > max) return DataResult.error(() -> "integer outside supported bounds");
                        return DataResult.success(com.mojang.datafixers.util.Pair.of(exact, ops.empty()));
                    } catch (ArithmeticException | NumberFormatException invalid) {
                        return DataResult.error(() -> "expected an integer from " + min + " to " + max);
                    }
                }
                return delegate.decode(ops, input);
            }
        });
    }

    public long totalWeight() { return (long) blueprintWeight + fragmentWeight + noResultWeight; }
    public double blueprintProbability() { return (double) blueprintWeight / totalWeight(); }
    public double fragmentProbability() { return (double) fragmentWeight / totalWeight(); }
    public double noResultProbability() { return (double) noResultWeight / totalWeight(); }

    /** Draw only after an authoritative transaction has passed every preflight check. */
    public Result select(LongUnaryOperator boundedRandom) {
        Objects.requireNonNull(boundedRandom, "bounded random source");
        int alternatives = (blueprintWeight > 0 ? 1 : 0) + (fragmentWeight > 0 ? 1 : 0) + (noResultWeight > 0 ? 1 : 0);
        long draw = alternatives == 1 ? 0 : draw(boundedRandom, totalWeight());
        if (draw < blueprintWeight) return new Result(Kind.BLUEPRINT, 1);
        if (draw < (long) blueprintWeight + fragmentWeight) {
            int count = minimumFragments == maximumFragments ? minimumFragments
                    : minimumFragments + (int) draw(boundedRandom, maximumFragments - minimumFragments + 1L);
            return new Result(Kind.FRAGMENTS, count);
        }
        return new Result(Kind.NO_RESULT, 0);
    }

    private static long draw(LongUnaryOperator random, long bound) {
        long value = random.applyAsLong(bound);
        if (value < 0 || value >= bound) throw new IllegalArgumentException("random source returned a value outside its bound");
        return value;
    }

    public enum Kind { BLUEPRINT, FRAGMENTS, NO_RESULT }
    public record Result(Kind kind, int count) {
        public Result {
            if (kind == null || switch (kind) {
                case BLUEPRINT -> count != 1;
                case FRAGMENTS -> count < 1 || count > 64;
                case NO_RESULT -> count != 0;
            }) throw new IllegalArgumentException("invalid Analyzer result");
        }
    }
}
