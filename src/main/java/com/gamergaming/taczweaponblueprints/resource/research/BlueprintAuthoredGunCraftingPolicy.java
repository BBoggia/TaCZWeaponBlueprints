package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Crafting levels for guns that an authored-only Tech Tree actually includes. */
public record BlueprintAuthoredGunCraftingPolicy(
        Map<Tier, ResearchWorkbenchTier> tierBands,
        BlueprintCraftingAccessPolicy fallback) {
    public static final BlueprintAuthoredGunCraftingPolicy DEFAULT = new BlueprintAuthoredGunCraftingPolicy(
            Map.of(
                    Tier.STARTER, ResearchWorkbenchTier.TIER_1,
                    Tier.BASIC, ResearchWorkbenchTier.TIER_1,
                    Tier.ESTABLISHED, ResearchWorkbenchTier.TIER_2,
                    Tier.ADVANCED, ResearchWorkbenchTier.TIER_2,
                    Tier.ELITE, ResearchWorkbenchTier.TIER_3,
                    Tier.APEX, ResearchWorkbenchTier.TIER_3),
            BlueprintCraftingAccessPolicy.TIER_2);
    public static final BlueprintAuthoredGunCraftingPolicy LEGACY = new BlueprintAuthoredGunCraftingPolicy(
            Map.of(),
            BlueprintCraftingAccessPolicy.UNRESTRICTED);

    private static final Codec<Tier> TIER_CODEC = Codec.STRING.flatXmap(
            BlueprintAuthoredGunCraftingPolicy::parseTier,
            value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));
    private static final Codec<Map<Tier, ResearchWorkbenchTier>> TIER_BANDS_CODEC =
            Codec.unboundedMap(TIER_CODEC, BlueprintProgressionCodecs.WORKBENCH_TIER);
    private static final Codec<BlueprintAuthoredGunCraftingPolicy> RAW_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    new StrictOptionalFieldCodec<>("tier_bands", TIER_BANDS_CODEC)
                            .xmap(value -> value.orElse(Map.of()), value -> value.isEmpty()
                                    ? Optional.empty()
                                    : Optional.of(value))
                            .forGetter(BlueprintAuthoredGunCraftingPolicy::tierBands),
                    BlueprintCraftingAccessPolicy.CODEC.fieldOf("fallback")
                            .forGetter(BlueprintAuthoredGunCraftingPolicy::fallback))
                    .apply(instance, BlueprintAuthoredGunCraftingPolicy::new));
    public static final Codec<BlueprintAuthoredGunCraftingPolicy> CODEC = StrictRecordCodec.wrap(
            "authored-gun crafting policy",
            RAW_CODEC,
            "tier_bands",
            "fallback");

    public BlueprintAuthoredGunCraftingPolicy {
        if (tierBands == null || fallback == null) {
            throw new IllegalArgumentException("authored-gun crafting policy fields cannot be null");
        }
        EnumMap<Tier, ResearchWorkbenchTier> normalized = new EnumMap<>(Tier.class);
        tierBands.forEach((tier, workbenchTier) -> {
            if (tier == null || workbenchTier == null) {
                throw new IllegalArgumentException("authored crafting tier bands cannot contain null");
            }
            normalized.put(tier, workbenchTier);
        });
        tierBands = Collections.unmodifiableMap(normalized);
    }

    public BlueprintCraftingAccessPolicy forTier(Tier tier) {
        if (tier == null) {
            throw new IllegalArgumentException("authored Tech Tree tier cannot be null");
        }
        ResearchWorkbenchTier workbenchTier = tierBands.get(tier);
        return workbenchTier == null ? fallback : BlueprintCraftingAccessPolicy.tiered(workbenchTier);
    }

    private static DataResult<Tier> parseTier(String value) {
        try {
            return DataResult.success(Tier.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "unknown authored Tech Tree tier " + value);
        }
    }
}
