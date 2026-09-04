package com.gamergaming.taczweaponblueprints.progression;

/** Approachable server presets for three-tier Research Bench enforcement. */
public enum ResearchProgressionPreset {
    CLASSIC,
    TIERED_RESEARCH,
    TIERED_RESEARCH_AND_CRAFTING,
    CUSTOM;

    public boolean enforcesResearch(boolean customValue) {
        return switch (this) {
            case CLASSIC -> false;
            case TIERED_RESEARCH, TIERED_RESEARCH_AND_CRAFTING -> true;
            case CUSTOM -> customValue;
        };
    }

    public boolean enforcesCrafting(boolean customValue) {
        return switch (this) {
            case CLASSIC, TIERED_RESEARCH -> false;
            case TIERED_RESEARCH_AND_CRAFTING -> true;
            case CUSTOM -> customValue;
        };
    }
}
