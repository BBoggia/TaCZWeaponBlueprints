package com.gamergaming.taczweaponblueprints.progression.analyzer;

import java.util.Locale;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Analysis capability, independent of research/crafting workstation requirements. */
public enum AnalyzerTier {
    TIER_1(1), TIER_2(2), TIER_3(3);

    public static final Codec<AnalyzerTier> CODEC = Codec.STRING.flatXmap(value -> {
        for (var tier : values()) if (tier.serializedName().equals(value)) return DataResult.success(tier);
        return DataResult.error(() -> "analysis tier must be tier_1, tier_2, or tier_3");
    }, value -> DataResult.success(value.serializedName()));

    private final int level;
    AnalyzerTier(int level) { this.level = level; }
    public int level() { return level; }
    public String serializedName() { return name().toLowerCase(Locale.ROOT); }
    public static AnalyzerTier fromLevel(int level) {
        for (var tier : values()) if (tier.level == level) return tier;
        throw new IllegalArgumentException("analysis tier must be between 1 and 3");
    }
}
