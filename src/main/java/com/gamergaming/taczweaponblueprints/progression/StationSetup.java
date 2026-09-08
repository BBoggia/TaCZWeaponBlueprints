package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

/** Immutable station choices, shared by runtime policy and offline authoring. */
public record StationSetup(AnalyzerMode analyzer, ResearchAccess research, AnalyzerTier dataRewardTier) {
    public static final StationSetup COMPATIBILITY = new StationSetup(AnalyzerMode.TIERED, ResearchAccess.PRESET, AnalyzerTier.TIER_1);
    public StationSetup {
        if (analyzer == null || research == null || dataRewardTier == null)
            throw new IllegalArgumentException("Station setup is incomplete");
    }
    public AnalyzerTier weaponRewardTier(AnalyzerTier station, AnalyzerTier weapon) {
        return analyzer == AnalyzerMode.SINGLE ? weapon : station;
    }
    public AnalyzerTier dataRewardTier(AnalyzerTier station) {
        return analyzer == AnalyzerMode.SINGLE ? dataRewardTier : station;
    }
    public ResearchWorkbenchTier researchTier(ResearchWorkbenchTier physical) {
        return research == ResearchAccess.SINGLE || research == ResearchAccess.PORTABLE ? ResearchWorkbenchTier.TIER_3 : physical;
    }
    public boolean enforceResearchTiers(boolean presetValue) {
        return switch (research) { case PRESET -> presetValue; case TIERED -> true; case SINGLE, PORTABLE -> false; };
    }
    public boolean recipeEnabled(boolean analyzerRecipe, int tier) {
        if (analyzerRecipe) return analyzer == AnalyzerMode.TIERED || tier == 1;
        return switch (research) { case PORTABLE -> false; case SINGLE -> tier == 1; case TIERED, PRESET -> true; };
    }
    public enum AnalyzerMode implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
        TIERED, SINGLE;
        @Override public String prefix() { return "config.taczweaponblueprints.blueprint.analyzerSetup"; }
    }
    public enum ResearchAccess implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
        TIERED, SINGLE, PORTABLE, PRESET;
        @Override public String prefix() { return "config.taczweaponblueprints.blueprint.researchAccess"; }
    }
}
