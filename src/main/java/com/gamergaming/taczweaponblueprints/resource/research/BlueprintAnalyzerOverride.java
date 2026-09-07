package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.*;
import java.util.stream.Stream;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One winning Analyzer rule; tier row entries replace complete rows, not the entire table. */
public record BlueprintAnalyzerOverride(
        Optional<BlueprintAnalyzerPolicy.Mode> mode,
        Optional<AnalyzerTier> weaponTier,
        Optional<AnalyzerTier> minimumAnalyzerTier,
        Optional<Set<BlueprintAnalyzerPolicy.Origin>> allowedOrigins,
        Optional<BlueprintAnalyzerPolicy.Reconstruction> reconstruction,
        BlueprintReverseEngineeringOverride attempt,
        Map<AnalyzerTier, Map<AnalyzerTier, BlueprintAnalyzerPolicy.Row>> tiers) {
    private static final Codec<BlueprintAnalyzerOverride> RAW = RecordCodecBuilder.create(i -> i.group(
            new AnalyzerOptionalFieldCodec<>("mode", BlueprintAnalyzerPolicy.MODE_CODEC).forGetter(BlueprintAnalyzerOverride::mode),
            new AnalyzerOptionalFieldCodec<>("weapon_tier", AnalyzerTier.CODEC).forGetter(BlueprintAnalyzerOverride::weaponTier),
            new AnalyzerOptionalFieldCodec<>("minimum_analyzer_tier", AnalyzerTier.CODEC).forGetter(BlueprintAnalyzerOverride::minimumAnalyzerTier),
            new AnalyzerOptionalFieldCodec<>("allowed_origins", BlueprintAnalyzerPolicy.ORIGINS_CODEC).forGetter(BlueprintAnalyzerOverride::allowedOrigins),
            new AnalyzerOptionalFieldCodec<>("reconstruction", BlueprintAnalyzerPolicy.Reconstruction.CODEC).forGetter(BlueprintAnalyzerOverride::reconstruction),
            new AnalyzerOptionalFieldCodec<>("attempt", AnalyzerAttemptCodec.CODEC)
                    .xmap(v -> v.orElse(BlueprintAnalyzerPolicy.NO_OVERRIDE), Optional::of).forGetter(BlueprintAnalyzerOverride::attempt),
            new AnalyzerOptionalFieldCodec<>("tiers", BlueprintAnalyzerPolicy.TIERS_CODEC)
                    .xmap(v -> v.orElse(Map.of()), v -> v.isEmpty() ? Optional.empty() : Optional.of(v)).forGetter(BlueprintAnalyzerOverride::tiers)
    ).apply(i, BlueprintAnalyzerOverride::new));
    public static final Codec<BlueprintAnalyzerOverride> CODEC = StrictRecordCodec.wrap("Analyzer rule override", RAW,
            "mode", "weapon_tier", "minimum_analyzer_tier", "allowed_origins", "reconstruction", "attempt", "tiers");
    public BlueprintAnalyzerOverride {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(weaponTier);
        Objects.requireNonNull(minimumAnalyzerTier);
        Objects.requireNonNull(allowedOrigins);
        Objects.requireNonNull(reconstruction);
        allowedOrigins = allowedOrigins.map(Set::copyOf);
        Objects.requireNonNull(attempt); BlueprintAnalyzerPolicy.validateAttempt(attempt);
        tiers = BlueprintAnalyzerPolicy.immutableTiers(tiers);
        if (mode.filter(v -> v != BlueprintAnalyzerPolicy.Mode.TIERED).isPresent()
                && (!tiers.isEmpty() || reconstruction.isPresent())) throw new IllegalArgumentException("non-tiered override cannot author tiers/reconstruction");
    }
    public Stream<BlueprintResearchCost> costs() {
        return Stream.concat(attempt.cost().stream(), tiers.values().stream().flatMap(v -> v.values().stream()).flatMap(row -> row.attempt().cost().stream()));
    }
}
