package com.gamergaming.taczweaponblueprints.progression.workbench;

import java.util.Locale;
import java.util.Optional;

/** Ordered workstation capability; higher tiers inherit every lower-tier action. */
public enum ResearchWorkbenchTier {
    TIER_1(1),
    TIER_2(2),
    TIER_3(3);

    private final int level;

    ResearchWorkbenchTier(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean satisfies(ResearchWorkbenchTier required) {
        if (required == null) {
            throw new IllegalArgumentException("required workbench tier cannot be null");
        }
        return level >= required.level;
    }

    public ResearchWorkbenchTier higherOf(ResearchWorkbenchTier other) {
        if (other == null) {
            throw new IllegalArgumentException("workbench tier cannot be null");
        }
        return level >= other.level ? this : other;
    }

    public Optional<ResearchWorkbenchTier> next() {
        return level == values().length ? Optional.empty() : Optional.of(fromLevel(level + 1));
    }

    public static ResearchWorkbenchTier fromLevel(int level) {
        for (ResearchWorkbenchTier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        throw new IllegalArgumentException("workbench tier level must be between 1 and 3");
    }

    public static ResearchWorkbenchTier parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("workbench tier cannot be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "1", "tier1", "tier_1" -> TIER_1;
            case "2", "tier2", "tier_2" -> TIER_2;
            case "3", "tier3", "tier_3" -> TIER_3;
            default -> throw new IllegalArgumentException("unknown workbench tier: " + value);
        };
    }
}
