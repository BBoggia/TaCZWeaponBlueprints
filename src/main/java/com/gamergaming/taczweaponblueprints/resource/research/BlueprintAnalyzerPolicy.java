package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.*;
import java.util.stream.Stream;
import com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Data-only weapon analysis policy. Nothing in this type enables workstation gameplay. */
public record BlueprintAnalyzerPolicy(Mode mode, Optional<AnalyzerTier> fallbackWeaponTier,
        AnalyzerTier minimumAnalyzerTier, BlueprintReverseEngineeringOverride attempt,
        Set<Origin> allowedOrigins, Optional<Reconstruction> reconstruction,
        Map<AnalyzerTier, Map<AnalyzerTier, Row>> tiers) {
    public enum Mode { LEGACY, TIERED, DISABLED }
    public enum Origin { FOUND, CRAFTED, UNKNOWN }
    public static final BlueprintReverseEngineeringOverride NO_OVERRIDE = new BlueprintReverseEngineeringOverride(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty());
    public static final Set<Origin> ALL_ORIGINS = Set.copyOf(EnumSet.allOf(Origin.class));
    public static final BlueprintAnalyzerPolicy LEGACY = new BlueprintAnalyzerPolicy(Mode.LEGACY,
            Optional.empty(), AnalyzerTier.TIER_1, NO_OVERRIDE, ALL_ORIGINS, Optional.empty(), Map.of());
    static final Codec<Mode> MODE_CODEC = enumCodec(Mode.class);
    static final Codec<Set<Origin>> ORIGINS_CODEC = enumCodec(Origin.class).listOf().flatXmap(values ->
            values.size() == new HashSet<>(values).size() ? DataResult.success(Set.copyOf(values))
                    : DataResult.error(() -> "duplicate Analyzer origin"),
            values -> DataResult.success(values.stream().sorted().toList()));
    static final Codec<Map<AnalyzerTier, Map<AnalyzerTier, Row>>> TIERS_CODEC =
            Codec.unboundedMap(AnalyzerTier.CODEC, Codec.unboundedMap(AnalyzerTier.CODEC, Row.CODEC));
    private static final Codec<BlueprintAnalyzerPolicy> RAW = RecordCodecBuilder.create(i -> i.group(
            MODE_CODEC.fieldOf("mode").forGetter(BlueprintAnalyzerPolicy::mode),
            new AnalyzerOptionalFieldCodec<>("fallback_weapon_tier", AnalyzerTier.CODEC).forGetter(BlueprintAnalyzerPolicy::fallbackWeaponTier),
            new AnalyzerOptionalFieldCodec<>("minimum_analyzer_tier", AnalyzerTier.CODEC)
                    .xmap(v -> v.orElse(AnalyzerTier.TIER_1), Optional::of).forGetter(BlueprintAnalyzerPolicy::minimumAnalyzerTier),
            new AnalyzerOptionalFieldCodec<>("attempt", AnalyzerAttemptCodec.CODEC)
                    .xmap(v -> v.orElse(NO_OVERRIDE), Optional::of).forGetter(BlueprintAnalyzerPolicy::attempt),
            new AnalyzerOptionalFieldCodec<>("allowed_origins", ORIGINS_CODEC)
                    .xmap(v -> v.orElse(ALL_ORIGINS), Optional::of).forGetter(BlueprintAnalyzerPolicy::allowedOrigins),
            new AnalyzerOptionalFieldCodec<>("reconstruction", Reconstruction.CODEC).forGetter(BlueprintAnalyzerPolicy::reconstruction),
            new AnalyzerOptionalFieldCodec<>("tiers", TIERS_CODEC)
                    .xmap(v -> v.orElse(Map.of()), v -> v.isEmpty() ? Optional.empty() : Optional.of(v)).forGetter(BlueprintAnalyzerPolicy::tiers)
    ).apply(i, BlueprintAnalyzerPolicy::new));
    public static final Codec<BlueprintAnalyzerPolicy> CODEC = StrictRecordCodec.wrap("Analyzer policy", RAW,
            "mode", "fallback_weapon_tier", "minimum_analyzer_tier", "attempt", "allowed_origins", "reconstruction", "tiers");

    public BlueprintAnalyzerPolicy {
        Objects.requireNonNull(mode); Objects.requireNonNull(fallbackWeaponTier);
        Objects.requireNonNull(minimumAnalyzerTier); Objects.requireNonNull(attempt);
        Objects.requireNonNull(reconstruction); allowedOrigins = Set.copyOf(allowedOrigins);
        tiers = immutableTiers(tiers);
        if (mode == Mode.TIERED) {
            requireComplete(tiers.keySet());
            tiers.values().forEach(v -> requireComplete(v.keySet()));
        } else if (!tiers.isEmpty() || reconstruction.isPresent()) {
            throw new IllegalArgumentException("only tiered Analyzer policies may declare tiers or reconstruction");
        }
        validateAttempt(attempt);
        for (var rows : tiers.values()) for (var row : rows.values()) {
            if (row.outcomes().fragmentWeight() > 0 && reconstruction.isEmpty()) {
                throw new IllegalArgumentException("partial outcomes require reconstruction thresholds");
            }
        }
    }

    static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.flatXmap(value -> {
            for (var v : type.getEnumConstants()) if (v.name().toLowerCase(Locale.ROOT).equals(value)) return DataResult.success(v);
            return DataResult.error(() -> "unknown " + type.getSimpleName() + ": " + value);
        }, value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));
    }

    static void requireComplete(Set<AnalyzerTier> keys) {
        if (!keys.equals(EnumSet.allOf(AnalyzerTier.class))) throw new IllegalArgumentException("all three analysis tiers must be declared");
    }

    static Map<AnalyzerTier, Map<AnalyzerTier, Row>> immutableTiers(Map<AnalyzerTier, Map<AnalyzerTier, Row>> source) {
        var copy = new EnumMap<AnalyzerTier, Map<AnalyzerTier, Row>>(AnalyzerTier.class);
        source.forEach((key, value) -> {
            var rows = new EnumMap<AnalyzerTier, Row>(AnalyzerTier.class);
            value.forEach((tier, row) -> rows.put(Objects.requireNonNull(tier), Objects.requireNonNull(row)));
            copy.put(Objects.requireNonNull(key), Collections.unmodifiableMap(rows));
        });
        return Collections.unmodifiableMap(copy);
    }

    static void validateAttempt(BlueprintReverseEngineeringOverride value) {
        value.validateForSnapshot();
        value.inputCount().ifPresent(count -> {
            if (count < 1 || count > BlueprintReverseEngineeringPolicy.MAX_INPUT_COUNT) throw new IllegalArgumentException("analysis input count out of bounds");
        });
    }

    /** Profile and row controls overlay legacy costs; new tiered reward defaults are explicit. */
    public BlueprintReverseEngineeringPolicy effectiveAttempt(BlueprintReverseEngineeringPolicy legacy, Row row) {
        var base = new BlueprintReverseEngineeringPolicy(legacy.enabled(), legacy.inputCount(), legacy.cost(),
                false, legacy.allowModified(), PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES, false, false);
        return base.apply(attempt).apply(row.attempt());
    }

    void validateAgainst(BlueprintReverseEngineeringPolicy legacy) {
        for (var rows : tiers.values()) for (var row : rows.values()) {
            var effective = effectiveAttempt(legacy, row);
            effective.validateForSnapshot();
            if (row.outcomes().fragmentWeight() > 0 && !effective.physicalBlueprintLearningMode().learningPermitted()) {
                throw new IllegalArgumentException("partial Analyzer outcomes require permitted blueprint learning");
            }
            if (effective.outputRecyclable() && !effective.expertAllowEconomyLoop()
                    && (allowedOrigins.contains(Origin.CRAFTED) || allowedOrigins.contains(Origin.UNKNOWN))) {
                throw new IllegalArgumentException("recyclable crafted/unknown Analyzer rewards require expert_allow_economy_loop");
            }
        }
    }

    public BlueprintAnalyzerPolicy apply(BlueprintAnalyzerOverride override) {
        if (override == null) return this;
        Mode nextMode = override.mode().orElse(mode);
        if (nextMode != Mode.TIERED && (!override.tiers().isEmpty() || override.reconstruction().isPresent())) {
            throw new IllegalArgumentException("tier rows and reconstruction require an effective tiered Analyzer mode");
        }
        var rows = new EnumMap<AnalyzerTier, Map<AnalyzerTier, Row>>(AnalyzerTier.class);
        tiers.forEach((tier, values) -> rows.put(tier, new EnumMap<>(values)));
        override.tiers().forEach((tier, values) -> rows.computeIfAbsent(tier, ignored -> new EnumMap<>(AnalyzerTier.class)).putAll(values));
        // The selected rule's attempt fields apply after the selected tier row.
        rows.replaceAll((tier, values) -> {
            var result = new EnumMap<AnalyzerTier, Row>(AnalyzerTier.class);
            values.forEach((weapon, row) -> result.put(weapon, new Row(row.enabled(), row.outcomes(), merge(row.attempt(), override.attempt()))));
            return result;
        });
        return new BlueprintAnalyzerPolicy(nextMode, fallbackWeaponTier,
                override.minimumAnalyzerTier().orElse(minimumAnalyzerTier), attempt,
                override.allowedOrigins().orElse(allowedOrigins),
                nextMode == Mode.TIERED ? override.reconstruction().or(() -> reconstruction) : Optional.empty(),
                nextMode == Mode.TIERED ? rows : Map.of());
    }

    static BlueprintReverseEngineeringOverride merge(BlueprintReverseEngineeringOverride base, BlueprintReverseEngineeringOverride next) {
        return new BlueprintReverseEngineeringOverride(next.enabled().or(base::enabled), next.inputCount().or(base::inputCount),
                next.cost().or(base::cost), next.allowKnown().or(base::allowKnown), next.allowModified().or(base::allowModified),
                next.physicalBlueprintLearningMode().or(base::physicalBlueprintLearningMode),
                next.outputRecyclable().or(base::outputRecyclable), next.expertAllowEconomyLoop().or(base::expertAllowEconomyLoop));
    }

    public Stream<BlueprintResearchCost> costs() {
        return Stream.concat(attempt.cost().stream(), tiers.values().stream().flatMap(v -> v.values().stream()).flatMap(row -> row.attempt().cost().stream()));
    }

    public record Row(boolean enabled, AnalyzerOutcomeTable outcomes, BlueprintReverseEngineeringOverride attempt) {
        private static final Codec<Row> RAW = RecordCodecBuilder.create(i -> i.group(
                new AnalyzerOptionalFieldCodec<>("enabled", AnalyzerOptionalFieldCodec.BOOLEAN).xmap(v -> v.orElse(true), Optional::of).forGetter(Row::enabled),
                AnalyzerOutcomeTable.CODEC.fieldOf("outcomes").forGetter(Row::outcomes),
                new AnalyzerOptionalFieldCodec<>("attempt", AnalyzerAttemptCodec.CODEC)
                        .xmap(v -> v.orElse(NO_OVERRIDE), Optional::of).forGetter(Row::attempt)
        ).apply(i, Row::new));
        public static final Codec<Row> CODEC = StrictRecordCodec.wrap("Analyzer tier row", RAW, "enabled", "outcomes", "attempt");
        public Row { Objects.requireNonNull(outcomes); Objects.requireNonNull(attempt); validateAttempt(attempt); }
    }

    public record Reconstruction(Map<AnalyzerTier, Integer> thresholds, int retainedProgressCap) {
        private static final Codec<Reconstruction> RAW = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(AnalyzerTier.CODEC, AnalyzerOutcomeTable.integer(1, BlueprintFragmentPolicy.MAX_THRESHOLD))
                        .fieldOf("thresholds").forGetter(Reconstruction::thresholds),
                AnalyzerOutcomeTable.integer(1, BlueprintFragmentPolicy.MAX_ARCHIVED_FRAGMENTS)
                        .fieldOf("retained_progress_cap").forGetter(Reconstruction::retainedProgressCap)
        ).apply(i, Reconstruction::new));
        public static final Codec<Reconstruction> CODEC = StrictRecordCodec.wrap("Analyzer reconstruction", RAW, "thresholds", "retained_progress_cap");
        public Reconstruction {
            var copy = new EnumMap<AnalyzerTier, Integer>(AnalyzerTier.class); copy.putAll(thresholds);
            requireComplete(copy.keySet());
            if (retainedProgressCap < 1 || retainedProgressCap > BlueprintFragmentPolicy.MAX_ARCHIVED_FRAGMENTS
                    || copy.values().stream().anyMatch(v -> v == null || v < 1 || v > BlueprintFragmentPolicy.MAX_THRESHOLD || v > retainedProgressCap)) {
                throw new IllegalArgumentException("invalid Analyzer reconstruction threshold/cap");
            }
            thresholds = Collections.unmodifiableMap(copy);
        }
    }
}
