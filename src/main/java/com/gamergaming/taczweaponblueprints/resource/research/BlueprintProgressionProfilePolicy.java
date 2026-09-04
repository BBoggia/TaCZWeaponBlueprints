package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTierRequirement;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Profile-wide workstation, fragment, and Progression Gate defaults. */
public record BlueprintProgressionProfilePolicy(
        ResearchWorkbenchTierRequirement fallbackTiers,
        Map<Tier, ResearchWorkbenchTierRequirement> authoredTierBands,
        BlueprintFragmentProfilePolicy fragments,
        ProgressionGateRequirements gates) {
    public static final BlueprintProgressionProfilePolicy LEGACY = new BlueprintProgressionProfilePolicy(
            ResearchWorkbenchTierRequirement.TIER_1,
            Map.of(),
            BlueprintFragmentProfilePolicy.DISABLED,
            ProgressionGateRequirements.EMPTY);
    public static final BlueprintProgressionProfilePolicy DEFAULT = new BlueprintProgressionProfilePolicy(
            ResearchWorkbenchTierRequirement.sameForBoth(ResearchWorkbenchTier.TIER_2),
            Map.of(
                    Tier.STARTER, ResearchWorkbenchTierRequirement.TIER_1,
                    Tier.BASIC, ResearchWorkbenchTierRequirement.TIER_1,
                    Tier.ESTABLISHED, ResearchWorkbenchTierRequirement.sameForBoth(ResearchWorkbenchTier.TIER_2),
                    Tier.ADVANCED, ResearchWorkbenchTierRequirement.sameForBoth(ResearchWorkbenchTier.TIER_2),
                    Tier.ELITE, ResearchWorkbenchTierRequirement.sameForBoth(ResearchWorkbenchTier.TIER_3),
                    Tier.APEX, ResearchWorkbenchTierRequirement.sameForBoth(ResearchWorkbenchTier.TIER_3)),
            BlueprintFragmentProfilePolicy.DEFAULT,
            ProgressionGateRequirements.EMPTY);

    private static final Codec<Tier> AUTHORED_TIER = Codec.STRING.flatXmap(
            BlueprintProgressionProfilePolicy::parseAuthoredTier,
            value -> DataResult.success(value.name().toLowerCase(java.util.Locale.ROOT)));
    private static final Codec<Map<Tier, ResearchWorkbenchTierRequirement>> AUTHORED_BANDS =
            Codec.unboundedMap(AUTHORED_TIER, BlueprintProgressionCodecs.WORKBENCH_REQUIREMENT);
    private static final Codec<BlueprintProgressionProfilePolicy> RAW_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    BlueprintProgressionCodecs.WORKBENCH_REQUIREMENT.fieldOf("fallback_tiers")
                            .forGetter(BlueprintProgressionProfilePolicy::fallbackTiers),
                    new StrictOptionalFieldCodec<>("authored_tier_bands", AUTHORED_BANDS)
                            .xmap(value -> value.orElse(Map.of()), value -> value.isEmpty()
                                    ? java.util.Optional.empty()
                                    : java.util.Optional.of(value))
                            .forGetter(BlueprintProgressionProfilePolicy::authoredTierBands),
                    new StrictOptionalFieldCodec<>("fragments", BlueprintFragmentProfilePolicy.CODEC)
                            .xmap(value -> value.orElse(BlueprintFragmentProfilePolicy.DISABLED),
                                    java.util.Optional::of)
                            .forGetter(BlueprintProgressionProfilePolicy::fragments),
                    new StrictOptionalFieldCodec<>("gates", BlueprintProgressionCodecs.GATE_REQUIREMENTS)
                            .xmap(value -> value.orElse(ProgressionGateRequirements.EMPTY),
                                    java.util.Optional::of)
                            .forGetter(BlueprintProgressionProfilePolicy::gates))
                    .apply(instance, BlueprintProgressionProfilePolicy::new));
    public static final Codec<BlueprintProgressionProfilePolicy> CODEC = StrictRecordCodec.wrap(
            "blueprint progression profile policy",
            RAW_CODEC,
            "fallback_tiers",
            "authored_tier_bands",
            "fragments",
            "gates");

    public BlueprintProgressionProfilePolicy {
        if (fallbackTiers == null || authoredTierBands == null || fragments == null || gates == null) {
            throw new IllegalArgumentException("blueprint progression profile policy fields cannot be null");
        }
        EnumMap<Tier, ResearchWorkbenchTierRequirement> normalized = new EnumMap<>(Tier.class);
        authoredTierBands.forEach((tier, requirement) -> {
            if (tier == null || requirement == null) {
                throw new IllegalArgumentException("authored tier bands cannot contain null");
            }
            normalized.put(tier, requirement);
        });
        authoredTierBands = Collections.unmodifiableMap(normalized);
    }

    public ResearchWorkbenchTierRequirement forAuthoredTier(Tier tier) {
        if (tier == null) {
            throw new IllegalArgumentException("authored Tech Tree tier cannot be null");
        }
        return authoredTierBands.getOrDefault(tier, fallbackTiers);
    }

    private static DataResult<Tier> parseAuthoredTier(String value) {
        try {
            return DataResult.success(Tier.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "unknown authored Tech Tree tier " + value);
        }
    }
}
