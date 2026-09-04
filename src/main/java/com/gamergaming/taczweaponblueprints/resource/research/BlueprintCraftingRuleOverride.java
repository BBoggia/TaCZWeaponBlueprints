package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateScope;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Independent crafting-only additions supplied by the selected research rule. */
public record BlueprintCraftingRuleOverride(
        Optional<BlueprintCraftingDisposition> disposition,
        Optional<ResearchWorkbenchTier> workbenchTier,
        Optional<ProgressionGateRequirements> gates) {
    private static final Codec<Fields> FIELDS_CODEC = StrictRecordCodec.wrap(
            "blueprint crafting rule override",
            RecordCodecBuilder.create(instance -> instance.group(
                    new StrictOptionalFieldCodec<>("disposition", BlueprintCraftingDisposition.CODEC)
                            .forGetter(Fields::disposition),
                    new StrictOptionalFieldCodec<>(
                            "workbench_tier",
                            BlueprintProgressionCodecs.WORKBENCH_TIER)
                            .forGetter(Fields::workbenchTier),
                    new StrictOptionalFieldCodec<>("gates", BlueprintProgressionCodecs.GATE_REQUIREMENTS)
                            .forGetter(Fields::gates))
                    .apply(instance, Fields::new)),
            "disposition",
            "workbench_tier",
            "gates");
    public static final Codec<BlueprintCraftingRuleOverride> CODEC = FIELDS_CODEC.flatXmap(
            BlueprintCraftingRuleOverride::fromFields,
            value -> DataResult.success(new Fields(
                    value.disposition(), value.workbenchTier(), value.gates())));

    public BlueprintCraftingRuleOverride {
        disposition = optional(disposition);
        workbenchTier = optional(workbenchTier);
        gates = optional(gates);
        if (disposition.isEmpty() && workbenchTier.isEmpty() && gates.isEmpty()) {
            throw new IllegalArgumentException("crafting rule override cannot be empty");
        }
        if ((disposition.orElse(null) == BlueprintCraftingDisposition.TIERED)
                != workbenchTier.isPresent()) {
            throw new IllegalArgumentException(
                    "workbench_tier is required only when crafting disposition is tiered");
        }
        gates.ifPresent(BlueprintCraftingRuleOverride::validateCraftingGates);
    }

    public Optional<BlueprintCraftingAccessPolicy> accessPolicy() {
        return disposition.map(value -> new BlueprintCraftingAccessPolicy(value, workbenchTier));
    }

    private static void validateCraftingGates(ProgressionGateRequirements requirements) {
        if (requirements.conditionCount() == 0) {
            throw new IllegalArgumentException("crafting rule gates cannot be empty");
        }
        boolean hasResearchOnly = requirements.allOf().stream()
                .flatMap(group -> group.anyOf().stream())
                .anyMatch(condition -> condition.scope() == ProgressionGateScope.RESEARCH);
        if (hasResearchOnly) {
            throw new IllegalArgumentException(
                    "crafting rule gates may use only crafting or both scope");
        }
    }

    private static DataResult<BlueprintCraftingRuleOverride> fromFields(Fields fields) {
        try {
            return DataResult.success(new BlueprintCraftingRuleOverride(
                    fields.disposition(), fields.workbenchTier(), fields.gates()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> exception.getMessage());
        }
    }

    private static <T> Optional<T> optional(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }

    private record Fields(
            Optional<BlueprintCraftingDisposition> disposition,
            Optional<ResearchWorkbenchTier> workbenchTier,
            Optional<ProgressionGateRequirements> gates) {
    }
}
