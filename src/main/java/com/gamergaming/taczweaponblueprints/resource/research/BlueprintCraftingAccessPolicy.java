package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One directly enforceable crafting availability authored in a profile or rule. */
public record BlueprintCraftingAccessPolicy(
        BlueprintCraftingDisposition disposition,
        Optional<ResearchWorkbenchTier> workbenchTier) {
    public static final BlueprintCraftingAccessPolicy TIER_1 = tiered(ResearchWorkbenchTier.TIER_1);
    public static final BlueprintCraftingAccessPolicy TIER_2 = tiered(ResearchWorkbenchTier.TIER_2);
    public static final BlueprintCraftingAccessPolicy TIER_3 = tiered(ResearchWorkbenchTier.TIER_3);
    public static final BlueprintCraftingAccessPolicy UNRESTRICTED = new BlueprintCraftingAccessPolicy(
            BlueprintCraftingDisposition.UNRESTRICTED, Optional.empty());
    public static final BlueprintCraftingAccessPolicy DISABLED = new BlueprintCraftingAccessPolicy(
            BlueprintCraftingDisposition.DISABLED, Optional.empty());

    private static final Codec<Fields> FIELDS_CODEC = StrictRecordCodec.wrap(
            "blueprint crafting access policy",
            RecordCodecBuilder.create(instance -> instance.group(
                    BlueprintCraftingDisposition.CODEC.fieldOf("disposition")
                            .forGetter(Fields::disposition),
                    new StrictOptionalFieldCodec<>(
                            "workbench_tier",
                            BlueprintProgressionCodecs.WORKBENCH_TIER)
                            .forGetter(Fields::workbenchTier))
                    .apply(instance, Fields::new)),
            "disposition",
            "workbench_tier");
    public static final Codec<BlueprintCraftingAccessPolicy> CODEC = FIELDS_CODEC.flatXmap(
            BlueprintCraftingAccessPolicy::fromFields,
            value -> DataResult.success(new Fields(value.disposition(), value.workbenchTier())));

    public BlueprintCraftingAccessPolicy {
        workbenchTier = workbenchTier == null ? Optional.empty() : workbenchTier;
        if (disposition == null) {
            throw new IllegalArgumentException("crafting disposition cannot be null");
        }
        if ((disposition == BlueprintCraftingDisposition.TIERED) != workbenchTier.isPresent()) {
            throw new IllegalArgumentException(
                    "workbench_tier is required only when disposition is tiered");
        }
    }

    public static BlueprintCraftingAccessPolicy tiered(ResearchWorkbenchTier tier) {
        if (tier == null) {
            throw new IllegalArgumentException("Crafting Workbench level cannot be null");
        }
        return new BlueprintCraftingAccessPolicy(
                BlueprintCraftingDisposition.TIERED,
                Optional.of(tier));
    }

    private static DataResult<BlueprintCraftingAccessPolicy> fromFields(Fields fields) {
        try {
            return DataResult.success(new BlueprintCraftingAccessPolicy(
                    fields.disposition(),
                    fields.workbenchTier()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> exception.getMessage());
        }
    }

    private record Fields(
            BlueprintCraftingDisposition disposition,
            Optional<ResearchWorkbenchTier> workbenchTier) {
    }
}
