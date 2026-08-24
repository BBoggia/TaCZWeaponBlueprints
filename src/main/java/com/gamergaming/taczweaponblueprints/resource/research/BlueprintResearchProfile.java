package com.gamergaming.taczweaponblueprints.resource.research;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BlueprintResearchProfile(
        int format,
        boolean journalEnabled,
        JournalVisibility visibility,
        boolean researchEnabled,
        boolean recyclingEnabled,
        boolean allowUnlearnedRecycling,
        int recyclingValue,
        BlueprintResearchCost researchCost,
        boolean requiresDiscovery,
        boolean creativeBypassesCost) {
    public static final int CURRENT_FORMAT = 1;

    private static final Codec<Integer> FORMAT_CODEC = Codec.INT.flatXmap(
            BlueprintResearchProfile::validateFormat,
            BlueprintResearchProfile::validateFormat);

    private static final Codec<BlueprintResearchProfile> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FORMAT_CODEC.fieldOf("format").forGetter(BlueprintResearchProfile::format),
                    Codec.BOOL.fieldOf("journal_enabled").forGetter(BlueprintResearchProfile::journalEnabled),
                    JournalVisibility.CODEC.fieldOf("visibility").forGetter(BlueprintResearchProfile::visibility),
                    Codec.BOOL.fieldOf("research_enabled").forGetter(BlueprintResearchProfile::researchEnabled),
                    Codec.BOOL.fieldOf("recycling_enabled").forGetter(BlueprintResearchProfile::recyclingEnabled),
                    Codec.BOOL.fieldOf("allow_unlearned_recycling")
                            .forGetter(BlueprintResearchProfile::allowUnlearnedRecycling),
                    BlueprintResearchCodecs.POINTS.fieldOf("recycling_value")
                            .forGetter(BlueprintResearchProfile::recyclingValue),
                    BlueprintResearchCost.CODEC.fieldOf("research_cost")
                            .forGetter(BlueprintResearchProfile::researchCost),
                    Codec.BOOL.fieldOf("requires_discovery")
                            .forGetter(BlueprintResearchProfile::requiresDiscovery),
                    Codec.BOOL.fieldOf("creative_bypasses_cost")
                            .forGetter(BlueprintResearchProfile::creativeBypassesCost))
                    .apply(instance, BlueprintResearchProfile::new));

    public static final Codec<BlueprintResearchProfile> CODEC = StrictRecordCodec.wrap(
            "blueprint research profile",
            RAW_CODEC.flatXmap(BlueprintResearchProfile::validateProfile, BlueprintResearchProfile::validateProfile),
            "format",
            "journal_enabled",
            "visibility",
            "research_enabled",
            "recycling_enabled",
            "allow_unlearned_recycling",
            "recycling_value",
            "research_cost",
            "requires_discovery",
            "creative_bypasses_cost");

    public BlueprintResearchProfile {
        if (format != CURRENT_FORMAT) {
            throw new IllegalArgumentException("unsupported blueprint research-profile format " + format);
        }
        if (visibility == null || researchCost == null) {
            throw new IllegalArgumentException("profile visibility and research cost cannot be null");
        }
        if (recyclingValue < 0 || recyclingValue > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            throw new IllegalArgumentException("profile recycling value is outside the supported range");
        }
    }

    private static DataResult<Integer> validateFormat(int value) {
        return value == CURRENT_FORMAT
                ? DataResult.success(value)
                : DataResult.error(() -> "unsupported blueprint research-profile format " + value);
    }

    private static DataResult<BlueprintResearchProfile> validateProfile(BlueprintResearchProfile profile) {
        return DataResult.success(profile);
    }
}
