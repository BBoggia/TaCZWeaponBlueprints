package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Legacy combined progression override retained for format-3 datapack
 * compatibility. Runtime research and crafting policies consume its fields
 * independently.
 */
public record BlueprintProgressionRuleOverride(
        Optional<ResearchWorkbenchTier> researchTier,
        Optional<ResearchWorkbenchTier> craftingTier,
        Optional<Integer> fragmentThreshold,
        Optional<ProgressionGateRequirements> gates) {
    public static final BlueprintProgressionRuleOverride EMPTY = new BlueprintProgressionRuleOverride(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    private static final Codec<BlueprintProgressionRuleOverride> RAW_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    new StrictOptionalFieldCodec<>("research_tier", BlueprintProgressionCodecs.WORKBENCH_TIER)
                            .forGetter(BlueprintProgressionRuleOverride::researchTier),
                    new StrictOptionalFieldCodec<>("crafting_tier", BlueprintProgressionCodecs.WORKBENCH_TIER)
                            .forGetter(BlueprintProgressionRuleOverride::craftingTier),
                    new StrictOptionalFieldCodec<>("fragment_threshold", Codec.INT)
                            .forGetter(BlueprintProgressionRuleOverride::fragmentThreshold),
                    new StrictOptionalFieldCodec<>("gates", BlueprintProgressionCodecs.GATE_REQUIREMENTS)
                            .forGetter(BlueprintProgressionRuleOverride::gates))
                    .apply(instance, BlueprintProgressionRuleOverride::new));
    public static final Codec<BlueprintProgressionRuleOverride> CODEC = StrictRecordCodec.wrap(
            "blueprint progression rule override",
            RAW_CODEC,
            "research_tier",
            "crafting_tier",
            "fragment_threshold",
            "gates");

    public BlueprintProgressionRuleOverride {
        researchTier = optional(researchTier);
        craftingTier = optional(craftingTier);
        fragmentThreshold = optional(fragmentThreshold);
        gates = optional(gates);
        fragmentThreshold.ifPresent(value -> {
            if (value < 1 || value > BlueprintFragmentPolicy.MAX_THRESHOLD) {
                throw new IllegalArgumentException("Blueprint Fragment threshold override is out of bounds");
            }
        });
    }

    public boolean isEmpty() {
        return researchTier.isEmpty() && craftingTier.isEmpty()
                && fragmentThreshold.isEmpty() && gates.isEmpty();
    }

    private static <T> Optional<T> optional(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }
}
